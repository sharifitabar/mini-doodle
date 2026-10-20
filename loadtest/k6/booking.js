// loadtest/k6/booking.js
// k6 scenario: 95% availability checks, 5% manipulation (add slot → book meeting on same slot)
// Flows are tagged `flow=availability` and `flow=manipulation`. Two outcomes are expected and are
// excluded from http_req_failed: `409` refusals (business_refusals) and `503` contention
// (slot_contention). Either outcome stops the sequence after the step that produced it.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { config, userUuid } from './lib/identity.js';
import { buildSummary } from './lib/report.js';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 204 }, 409, 503));

// Custom metrics (exported by k6; used in thresholds and in the dashboard/report)
const manipulationShare = new Rate('manipulation_share');
const manipulationSequenceDuration = new Trend('manipulation_sequence_duration', true);
const businessRefusals = new Counter('business_refusals');
const slotContention = new Counter('slot_contention');

// Compute RUN_START_HOUR_MS = start of current hour (or next hour, but module init; toISOString
// produces a concrete time; using Date.now() at module load is fine in k6)
const RUN_START_HOUR_MS = Math.floor(Date.now() / 3600000) * 3600000;

// Deterministic future slot start time per iteration across the whole scenario. Each
// manipulation iteration gets a distinct hour far in the future, so it cannot overlap the
// past-dated seeded history, and two manipulations never collide.
function nextManipulationSlotStart() {
  const offsetHours = 1 + exec.scenario.iterationInTest;
  return new Date(RUN_START_HOUR_MS + offsetHours * 3600000).toISOString();
}

// Thresholds: overridable via env; defaults from FR-026. Smoke mode cannot guarantee the mix (few
// iterations), so the mix threshold is omitted when SMOKE=1 — checks > 0.99 still protect wiring.
const thresholds = {
  'http_req_duration{flow:availability}': [
    `p(95)<${config.targets.availP95Ms}`,
    `p(99)<${config.targets.availP99Ms}`,
  ],
  'manipulation_sequence_duration': [`p(95)<${config.targets.manipP95Ms}`],
  'http_req_failed': [`rate<${config.targets.errorRate}`],
  'checks': ['rate>0.99'],
};

if (!config.smoke) {
  const lower = 0.05 - config.targets.mixTolerance;
  const upper = 0.05 + config.targets.mixTolerance;
  thresholds['manipulation_share'] = [
    `rate>=${lower}`,
    `rate<=${upper}`,
  ];
}

export const options = {
  discardResponseBodies: false,
  scenarios: {
    booking: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${config.rampSeconds}s`, target: config.vus },
        { duration: `${config.steadySeconds}s`, target: config.vus },
        { duration: `${config.rampDownSeconds}s`, target: 0 },
      ],
      gracefulRampDown: '30s',
      tags: { testid: config.testId },
    },
  },
  thresholds,
  // summaryTrendStats: declare which percentiles exist; k6 2.3.0 also honours this for exports
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function checkAvailability(userId) {
  const iter = exec.vu.iterationInInstance;
  const sampled = (iter % config.bodySampleEvery) === 0;
  const url = `${config.baseUrl}/api/v1/availability?from=${encodeURIComponent(config.window.from)}&to=${encodeURIComponent(config.window.to)}`;
  const res = http.get(url, {
    headers: { 'X-User-Id': userId },
    tags: { flow: 'availability' },
    // Don't parse the body for every read (response bodies can be large); sample a few to assert
    // the shape (intervals present, at least one BUSY). The contract's responseType guidance in
    // the spec is honoured: parse only the sampled reads.
    responseType: sampled ? 'text' : 'none',
  });
  check(res, { 'availability ok': (r) => r.status === 200 });

  if (sampled) {
    let parsed = null;
    try {
      // responseType 'text' → use res.body; k6 can JSON.parse text or use res.json if we didn't
      // set 'text', but 'text' is explicit here. res.json() requires JSON content-type and
      // default parsing; setting responseType:'text' returns body string.
      if (res.body) {
        parsed = JSON.parse(res.body);
      } else {
        parsed = null;
      }
    } catch (e) {
      parsed = null;
    }
    check(parsed, {
      'availability not trivial': (p) => {
        if (!p || !Array.isArray(p.intervals)) return false;
        if (p.intervals.length === 0) return false;
        return p.intervals.some((i) => i && i.kind === 'BUSY');
      },
    });
  }
}

function manipulate(userId, vuId, iter) {
  const startMs = Date.now();
  const startAt = nextManipulationSlotStart();

  // Step 1: create a FREE slot at an exclusive future time.
  const createBody = JSON.stringify({
    startAt,
    durationMinutes: 30,
  });
  const create = http.post(`${config.baseUrl}/api/v1/slots`, createBody, {
    headers: {
      'X-User-Id': userId,
      'Content-Type': 'application/json',
    },
    tags: { flow: 'manipulation' },
  });

  if (create.status === 409) {
    businessRefusals.add(1, { step: 'create_slot' });
    manipulationSequenceDuration.add(Date.now() - startMs);
    manipulationShare.add(1);
    return;
  }

  // 503 is retryable contention: the service answers with Retry-After and never retries the write
  // itself. The harness records it and stops here, like a 409; the client-side retry policy is not
  // what this run measures.
  if (create.status === 503) {
    slotContention.add(1, { step: 'create_slot' });
    manipulationSequenceDuration.add(Date.now() - startMs);
    manipulationShare.add(1);
    return;
  }

  check(create, { 'slot created': (r) => r.status === 201 });
  let slotId = null;
  try {
    slotId = create.json ? create.json('id') : JSON.parse(create.body).id;
  } catch (e) {
    slotId = null;
  }
  check(slotId, { 'slot id present': (v) => !!v });

  // Step 2: convert that slot into a meeting (one logical unit).
  const bookBody = JSON.stringify({
    title: `Load meeting ${vuId}-${iter}`,
    description: 'load-test manipulation sequence',
    participants: [
      { name: 'P0', email: 'p0@loadtest.local' },
    ],
  });
  const book = http.post(`${config.baseUrl}/api/v1/slots/${slotId}/meeting`, bookBody, {
    headers: {
      'X-User-Id': userId,
      'Content-Type': 'application/json',
    },
    tags: { flow: 'manipulation' },
  });

  if (book.status === 409) {
    businessRefusals.add(1, { step: 'book_meeting' });
    manipulationSequenceDuration.add(Date.now() - startMs);
    manipulationShare.add(1);
    return;
  }

  if (book.status === 503) {
    slotContention.add(1, { step: 'book_meeting' });
    manipulationSequenceDuration.add(Date.now() - startMs);
    manipulationShare.add(1);
    return;
  }

  check(book, { 'booked': (r) => r.status === 201 });
  manipulationSequenceDuration.add(Date.now() - startMs);
  manipulationShare.add(1);
}

export default function () {
  const vuId = exec.vu.idInInstance;
  const iter = exec.vu.iterationInInstance;
  const userIndex = ((vuId - 1) % config.seedUsers) + 1;
  const userId = userUuid(userIndex);

  // 5/95 mix: manipulation when (iter % 20) === (vuId % 20). Deterministic and spreads across VUs.
  const isManipulation = (iter % 20) === (vuId % 20);

  if (isManipulation) {
    manipulate(userId, vuId, iter);
  } else {
    checkAvailability(userId);
    manipulationShare.add(0);
  }

  // Jittered think time: 10s ± 2s (8..12s) to avoid a pathological sync across all VUs.
  const base = config.thinkTimeSeconds;
  const sleepSec = base * 0.8 + Math.random() * (base * 0.4);
  sleep(sleepSec);
}

export function handleSummary(data) {
  return buildSummary(data, config.testId, config.smoke);
}
