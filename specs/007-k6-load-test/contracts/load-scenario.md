# Contract: Load Scenario, Metrics and Verdict

**Feature**: `007-k6-load-test` | **Date**: 2026-10-18

The observable contract of `loadtest/k6/booking.js`: what load it applies, what it measures, when it
passes, and how a caller learns the verdict.

## 1. Scenario

| Item | Value |
| --- | --- |
| Scenario name | `booking` |
| Executor | `ramping-vus` |
| Stages | `2m → 1000 VUs`, `10m → 1000 VUs`, `30s → 0 VUs` |
| `gracefulRampDown` | `30s` (in-flight iterations finish, so the tail is not truncated) |
| Concurrent users | `LOAD_VUS` (default **1000**) resident for the whole steady state |
| VU → user mapping | `user(u) = "10000000-0000-4000-8000-" + pad(__VU, 12)` — one VU *is* one dataset user, so all 1000 identities are exercised (FR-017) |
| Think time | `sleep(random(8, 12))` seconds between actions (`LOAD_THINK_TIME_SECONDS`, default 10) |
| Measured window | the 10-minute steady state. Ramp-up and ramp-down are excluded (FR-005) |
| Expected demand | ≈ 90–100 requests/s → ≈ 55,000–60,000 requests and ≈ 2,800–3,000 manipulation sequences per run |

**Closed-loop caveat (must be read with the numbers):** `ramping-vus` holds *users*, not a fixed
request rate, so if the service slows down the achieved request rate falls with it. Latency
thresholds are unaffected and remain the primary signal. Throughput is reported as **achieved**, not
offered. This is the deliberate trade-off for having 1000 genuinely resident users, and it is
documented in `quickstart.md` so nobody reads a low request rate as "the service is fast".

## 2. Flow selection (the 5% / 95% mix)

```
manipulation  ⇔  (__ITER % 20) === (__VU % 20)
```

| Property | Why it matters |
| --- | --- |
| Exactly 5% per VU per 20-iteration cycle | The mix is a property of the script, not of a random draw, so two runs are comparable (FR-014, FR-019) |
| Offset by `__VU` | Without the offset every VU's *first* iteration would be a manipulation, firing 1000 simultaneous writes at ramp start |
| One manipulation = one simulated request | A manipulation is the create-slot-then-book sequence (FR-015). It counts once in the mix even though it issues two HTTP calls |
| Machine-checked | The `manipulation_share` Rate metric is thresholded to `0.04…0.06`, so a drifting mix fails the run (SC-003) |

Raw HTTP volume thus sits near 95 read calls to 10 manipulation calls — the dashboard labels
those panels "HTTP calls", not "simulated requests", so the 5/95 statement stays honest.

## 3. Request behavior

Standard status handling is defined once in `contracts/api-usage.md`. Two behaviors are worth
restating here because they shape the measurements:

- **Expected refusals are not failures.** `http.setResponseCallback(http.expectedStatuses({min:200,max:204}, 409, 503))`
  makes `409` and `503` expected statuses, so `http_req_failed` measures real faults only (FR-020).
  `409` is counted in `business_refusals{step}` and `503` in `slot_contention{step}`, so the
  contention level is visible instead of hidden in the error rate.
- **Response-body sampling on the read path.** Availability responses carry ≈1,200–1,600 intervals
  (~150–250 KB). Every 10th read (`LOAD_BODY_SAMPLE_EVERY`, default 10) is fully parsed and checked
  for non-triviality. The rest are issued with `responseType: 'none'`. This keeps the generator from
  becoming the bottleneck on a laptop (risk R2) while leaving the server-side work — query,
  aggregation, serialization, transfer — completely unaffected. FR-016 is still verified, just on a
  sample: a check failure rate of 0 on the sampled reads proves the calendar is not empty.

## 3.5 Assertions (`check()`), and what they protect against

| Check | Passes when | Protects against |
| --- | --- | --- |
| `availability ok` | status `200` | measuring a service that answers errors |
| `availability not trivial` (sampled) | body parsed, `intervals` non-empty, ≥1 `BUSY` interval | measuring an empty or missing calendar (FR-016) |
| `slot created` | status `201` | a run whose writes are silently refused — for example a stale future left by a previous run without a reset (D13) |
| `booked` | status `201` | the booking half of the sequence quietly not happening (`409` here is a real refusal, but a *systemic* one must be loud) |
| `sequence complete` | both steps reached a terminal, classified outcome | a sequence being abandoned mid-way |

All checks roll up into `checks`, thresholded at `> 0.99` (T6) in `contracts/load-scenario.md` §5. A
small number of genuine `409` refusals is normal and does not threaten the threshold. A systemic
refusal (every manipulation refused) pushes it far below.

## 4. Metrics contract

### Built-in, used by the dashboards

| Prometheus series | Source | Used for |
| --- | --- | --- |
| `k6_vus` | executor | resident users |
| `k6_http_reqs_total` | built-in counter | achieved request rate by `flow` |
| `k6_http_req_failed_rate` | built-in rate | error rate (refusals excluded) |
| `k6_http_req_duration_p95` / `_p99` / `_avg` / `_min` / `_max` | built-in trend + `K6_PROMETHEUS_RW_TREND_STATS` | client-side latency by `flow` |
| `k6_checks_rate` | built-in rate | assertion health |
| `k6_iterations_total`, `k6_iteration_duration_*` | built-in | throughput and per-iteration cost |
| `k6_dropped_iterations_total` | built-in | not expected with `ramping-vus`. A nonzero value means something is wrong |

`K6_PROMETHEUS_RW_TREND_STATS=p(90),p(95),p(99),avg,min,max` is required — k6's default is `p(99)`
only, and without the extra stats the `_p95` series the dashboard queries would not exist.

### Custom

| Metric | Type | Tags | Purpose |
| --- | --- | --- | --- |
| `manipulation_share` | Rate | — | 1 per manipulation iteration, 0 per read → the measured mix |
| `manipulation_sequence_duration` | Trend (ms) | — | create-slot **plus** book-meeting, measured as one unit (FR-015) |
| `business_refusals` | Counter | `step=create_slot\|book_meeting` | expected `409`s, reported but never thresholded |

### Tags

| Tag | Set on | Purpose |
| --- | --- | --- |
| `testid` (wide tag) | all metrics, from `__ENV.TEST_ID` | separates runs of the same session (FR-024, SC-006) |
| `flow=availability\|manipulation` | request metrics | per-flow latency, rate and error panels |
| `name`, `method`, `status`, `expected_response` | k6 built-ins | request-level breakdowns |

## 5. Thresholds — the verdict

| # | Threshold | Target | Spec origin |
| --- | --- | --- | --- |
| T1 | `http_req_duration{flow:availability}` | `p(95) < 300` ms | FR-026 |
| T2 | `http_req_duration{flow:availability}` | `p(99) < 1000` ms | FR-026 |
| T3 | `manipulation_sequence_duration` | `p(95) < 500` ms | FR-026 |
| T4 | `http_req_failed` | `rate < 0.01` | FR-026 (refusals excluded by design) |
| T5 | `manipulation_share` | `rate >= 0.04` and `rate <= 0.06` | FR-014, SC-003 |
| T6 | `checks` | `rate > 0.99` | FR-016, and "the requests did what the run thinks they did" |

Each target value is read from an environment variable whose **default is exactly the FR-026 value**
(`TARGET_AVAIL_P95_MS=300`, `TARGET_AVAIL_P99_MS=1000`, `TARGET_MANIP_P95_MS=500`,
`TARGET_ERROR_RATE=0.01`). The override exists so the verdict mechanism can itself be verified —
a run with `TARGET_AVAIL_P95_MS=1` must fail — and for exploratory runs at other hypothetical
targets. **Defaults are never raised to make a failed run pass**. A breach is the finding, and changing
a target deliberately is a spec change, not a test change.

Deliberately **not** thresholded: throughput (a closed-loop model cannot promise a rate), the
manipulation refusal rate (contention is an observation), and service-side percentiles (the k6
thresholds are the contract, service-side panels are diagnostic context).

## 6. Summary artifact and exit codes

`handleSummary` writes `loadtest/results/summary-{TEST_ID}.json` (k6's full JSON) and prints to
stdout: VUs, requests and sequences issued, achieved request rate, measured mix, per-flow p95/p99,
error rate, refusal counts by step, and a per-threshold **PASS/FAIL** line ending in the verdict.

| Exit code | Meaning |
| --- | --- |
| `0` | every threshold held |
| `99` | at least one threshold breached — the automated "fail" signal (SC-005) |
| any other nonzero | k6 could not run the script (bad config, unreachable endpoint, script error) |

`loadtest/run.sh` propagates the code unchanged and still prints the Grafana URL, so a failed run is
still inspectable.
