// loadtest/k6/lib/report.js
// Builds a human-readable summary for stdout and writes a machine-readable summary to /results
// (the filename includes TEST_ID so runs remain identifiable).

const pct = (v) => {
  if (v === undefined || v === null) return 'n/a';
  return `${Math.round(v * 1000) / 1000}`;
};

const ms = (v) => {
  if (v === undefined || v === null) return 'n/a';
  return `${Math.round(v)}`;
};

export function buildSummary(data, testId, isSmoke) {
  const metrics = data.metrics || {};
  const httpDuration = metrics.http_req_duration?.values || {};
  const httpDurationAvail = metrics['http_req_duration{flow:availability}']?.values || {};
  const manipulationSequence = metrics.manipulation_sequence_duration?.values || {};
  const httpReqs = metrics.http_reqs?.values || {};
  const httpFailed = metrics.http_req_failed?.values || {};
  const manipulationShare = metrics.manipulation_share?.values || {};
  const checks = metrics.checks?.values || {};
  const vusMax = metrics.vus_max?.values || {};
  const iterations = metrics.iterations?.values || {};
  const businessRefusals = metrics.business_refusals?.values || {};
  const businessRefusalsCreate = metrics['business_refusals{step:create_slot}']?.values || {};
  const businessRefusalsBook = metrics['business_refusals{step:book_meeting}']?.values || {};
  const slotContention = metrics.slot_contention?.values || {};
  const slotContentionCreate = metrics['slot_contention{step:create_slot}']?.values || {};
  const slotContentionBook = metrics['slot_contention{step:book_meeting}']?.values || {};

  const thresholdFailures = [];
  for (const [name, m] of Object.entries(metrics)) {
    if (m.thresholds) {
      for (const [expr, res] of Object.entries(m.thresholds)) {
        if (!res.ok) {
          thresholdFailures.push(`${name}: ${expr}`);
        }
      }
    }
  }

  const lines = [];
  lines.push('=== k6 Load Test Summary ===');
  lines.push(`testid: ${testId}`);
  lines.push(`smoke: ${isSmoke ? 'true' : 'false'}`);
  lines.push(`iterations: ${iterations.count ?? 0}`);
  lines.push(`max VUs: ${vusMax.max ?? 0}`);
  lines.push(`http_reqs: ${httpReqs.count ?? 0} (rate ${pct(httpReqs.rate)}/s)`);
  lines.push(`http_req_failed: ${pct(httpFailed.rate)} (target < ${pct(metrics.http_req_failed?.thresholds ? undefined : undefined)} — see thresholds)`);
  lines.push('availability http_req_duration:');
  lines.push(`  p95: ${ms(httpDurationAvail['p(95)'])} ms, p99: ${ms(httpDurationAvail['p(99)'])} ms, avg: ${ms(httpDurationAvail.avg)} ms`);
  lines.push('overall http_req_duration:');
  lines.push(`  p95: ${ms(httpDuration['p(95)'])} ms, p99: ${ms(httpDuration['p(99)'])} ms, avg: ${ms(httpDuration.avg)} ms`);
  lines.push('manipulation_sequence_duration:');
  lines.push(`  p95: ${ms(manipulationSequence['p(95)'])} ms, avg: ${ms(manipulationSequence.avg)} ms`);
  lines.push(`manipulation_share (rate): ${pct(manipulationShare.rate)} (target 0.04..0.06 in non-smoke)`);
  lines.push(`checks rate: ${pct(checks.rate)}`);
  lines.push(`business_refusals total: ${businessRefusals.count ?? 0} (create_slot ${businessRefusalsCreate.count ?? 0}, book_meeting ${businessRefusalsBook.count ?? 0})`);
  lines.push(`slot_contention total: ${slotContention.count ?? 0} (create_slot ${slotContentionCreate.count ?? 0}, book_meeting ${slotContentionBook.count ?? 0})`);
  if (thresholdFailures.length === 0) {
    lines.push('VERDICT: PASS');
  } else {
    lines.push('VERDICT: FAIL');
    for (const f of thresholdFailures) {
      lines.push(`  - ${f}`);
    }
  }
  lines.push('============================');

  const artifact = {
    [`/results/summary-${testId}.json`]: JSON.stringify(data, null, 2),
    stdout: lines.join('\n') + '\n',
  };

  return artifact;
}

export function summary(data, testId, isSmoke) {
  return buildSummary(data, testId, isSmoke);
}
