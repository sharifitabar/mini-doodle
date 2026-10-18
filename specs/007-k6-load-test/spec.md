# Feature Specification: k6 Load Test for Booking-Scale Performance

**Feature Branch**: `007-k6-load-test`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "grill me i need to add k6 load test for simulating 1000 usres each might have up to 800 slot and max converted to 100 meetings to check performance of db and service and latency main scenario like add slot and change to meeting would be covered from 100 percent of simulated request in a duration 5 percent is manipulation and rest is checking availabilities load test should be run locally using docker compose and result should be reflected in grafana dashboard"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run the load test locally and watch it live in Grafana (Priority: P1)

A developer wants to answer "how does the booking service behave at the scale we expect?" without cloud infrastructure. They follow one documented procedure, the stack comes up, the target dataset is provisioned, the load scenario starts, and request rate, latency, and failures appear in Grafana while the run is in progress. When the run ends they get a summary that states whether it met its performance targets.

**Why this priority**: This is the entire point of the feature — a repeatable local performance exercise with a visible, trustworthy result. Every other story only adds fidelity to this loop.

**Independent Test**: From a clean checkout, follow the documented bring-up and run procedure, then confirm that (a) the stack reaches a ready state, (b) load starts only after the dataset exists, (c) Grafana shows the run's traffic and latency during the run with no manual dashboard editing, and (d) an end-of-run summary is printed.

**Acceptance Scenarios**:

1. **Given** a clean checkout with no containers running, **When** the developer follows the documented procedure, **Then** the service, its database, and the dashboards reach a healthy state and the provisioned dataset is reported ready before any load starts.
2. **Given** a running stack and a provisioned dataset, **When** the load scenario starts, **Then** the run's request rate and latency become visible in Grafana within the first minute and remain queryable after the run ends.
3. **Given** a completed run, **When** the developer reads the summary, **Then** it reports throughput, latency percentiles per flow, error rate, and a single pass/fail verdict.
4. **Given** a run has finished, **When** the developer re-opens the dashboards, **Then** the previous run's data is still there and is distinguishable from a later run.

---

### User Story 2 - A workload that mirrors real usage (95% checks, 5% manipulations) (Priority: P1)

Simulated users mostly look at availability; a small minority of their actions change data by adding a free slot and converting that slot into a booked meeting.

**Why this priority**: A performance number is only meaningful for the mix that produced it. The 95/5 split is the stated production shape; a test that drifts to another mix measures the wrong system.

**Independent Test**: Run the scenario for a short duration and inspect the recorded request mix by flow; manipulations must be 5% (±1 percentage point) and everything else must be availability checks.

**Acceptance Scenarios**:

1. **Given** a running load scenario, **When** the request mix is measured, **Then** approximately 5% of requests are manipulations and approximately 95% are availability checks.
2. **Given** a manipulation, **When** it executes, **Then** it adds a new slot and then converts that same slot into a meeting, and both steps are counted as part of that one manipulation.
3. **Given** a manipulation that is refused because another request already occupies the slot, **When** the run is summarised, **Then** the refusal is reported as an expected business outcome and does not count toward the error rate.
4. **Given** any simulated user, **When** it issues a request, **Then** it acts as itself, against its own calendar only.

---

### User Story 3 - A dataset at the stated scale (1000 users, up to 800 slots each, up to 100 meetings each) (Priority: P1)

Before load starts, the environment holds 1000 users, each owning a calendar, each carrying a substantial history of slots (up to 800), a minority of which are already booked as meetings (up to 100).

**Why this priority**: Latency against a near-empty table tells you nothing. The point is to read and write against large per-user working sets with real index and I/O pressure.

**Independent Test**: After provisioning completes, count users, slots per user, and meetings per user in the database; the counts match the target shape, and the provisioning step reports what it created and is safe to re-run.

**Acceptance Scenarios**:

1. **Given** an empty database, **When** provisioning runs, **Then** 1000 distinct users exist, each with its own calendar.
2. **Given** a provisioned database, **When** per-user data is counted, **Then** no user exceeds 800 slots, no user has more than 100 meetings, and the busiest users are close to those ceilings.
3. **Given** a provisioned database, **When** availability is checked for any user, **Then** the answer is computed from that user's real history rather than an empty calendar.
4. **Given** a provisioned database, **When** provisioning runs again, **Then** it does not duplicate or grow the dataset and the resulting shape is the same.
5. **Given** a completed provisioning step, **When** the developer reads its report, **Then** it states how many users, slots, and meetings exist and how long provisioning took.

---

### User Story 4 - A defensible performance verdict, with database pressure visible (Priority: P1)

The run answers "is this fast enough, and if not, where does it hurt?" — latency per flow, error rate, and database-level pressure (connections, activity, contention) on the same timeline as the traffic.

**Why this priority**: The request is to check "performance of db and service and latency". Without an explicit pass/fail and database visibility, the result is a wall of graphs rather than an answer.

**Independent Test**: Run the scenario with a deliberately tight target and confirm the run reports failure; repeat with a loose target and confirm it reports success; then confirm database pressure is visible in the dashboards for the same time window.

**Acceptance Scenarios**:

1. **Given** a completed run, **When** the summary is read, **Then** it reports a latency percentile for availability checks and for manipulations, plus an overall error rate.
2. **Given** a run whose measured latency or error rate breaches the agreed target, **When** the run finishes, **Then** the run reports failure, and a breach is detectable by an automated caller rather than only by a human reading a chart.
3. **Given** a run in progress, **When** the developer opens the dashboards, **Then** database pressure for the same window (open connections, active queries or transactions, contention or slow statements) is visible next to the request metrics.
4. **Given** a run that saturates the database, **When** the developer investigates, **Then** the evidence distinguishes "the database was the bottleneck" from "the service was the bottleneck".

---

### User Story 5 - Repeatable runs without wrecking the local environment (Priority: P2)

The load test is not a one-off. It is re-run on demand, leaves the environment in a known state, and never affects the normal build.

**Why this priority**: Necessary for the test to stay useful over time, but it adds no new measurement by itself.

**Independent Test**: Run the scenario twice back-to-back and confirm both runs are comparable with no manual database cleanup between them; then run the normal build and confirm its outcome and duration are unchanged.

**Acceptance Scenarios**:

1. **Given** a previously completed run, **When** the developer starts another run, **Then** the procedure resets or reuses data so the second run is comparable to the first.
2. **Given** the load-test assets are present in the repository, **When** the normal build and test suite run, **Then** the load test does not execute and neither the build's result nor its duration is affected.
3. **Given** a developer joining the project, **When** they read the project documentation, **Then** they find the load test's purpose, the exact run procedure, the metric and target definitions, and how to interpret the dashboards.

---

### Edge Cases

- Two simulated users convert the same free slot at the same time: one is refused. The refusal must be recorded as an expected business outcome, not as a failure, and must not trigger indefinite retries.
- A newly added slot would overlap an existing slot of that user: the write is refused by the overlap rule. Provisioned and generated slots must be placed so this cannot happen by accident, and any residual refusal must be classified as expected.
- A slot already holds a meeting: converting it again is refused. The workload must only convert the slot it just added.
- A request carries no caller identity, or one that is not a provisioned user: it is refused. The load test must therefore never rely on anonymous access, and a misconfigured identity must surface as a clear failure rather than a slow accumulation of refusals counted as errors.
- Slot creation with a past start time or an out-of-range duration: refused by input validation. All generated data must be valid by construction.
- Availability checked over an inverted or unbounded frame: refused. Reads must use valid, bounded windows.
- Provisioning up to ~800,000 slots is itself the slowest single step: it must be bounded, reported, and excluded from the measured window, and must not double as the load.
- A run is interrupted midway: the run is incomplete and must be reported as incomplete, and the next run must start from a reset state rather than from a half-mutated one.
- The host is the bottleneck (laptop CPU, container resource limits, or the generator itself): the result must make it possible to separate "the service was slow" from "the machine could not drive the load", so a run is not misread as a service regression.
- The metrics store samples on an interval that is coarse relative to the run: even a short run must still produce a complete, readable picture rather than sparse gaps.
- Several runs happen in one session: load-generator measurements and service/database measurements must stay correlated per run and distinguishable from one another.
- The service's database connection budget is far smaller than the number of simultaneous users: saturation must be an observable, explainable outcome rather than a confusing spray of timeouts.

## Requirements *(mandatory)*

### Functional Requirements

**Environment and execution**

- **FR-001**: The load test MUST run entirely on the local machine through `docker compose`, requiring no external or cloud service.
- **FR-002**: A single documented procedure MUST bring up the whole stack (service, database, metrics store, dashboards, load generator), and load MUST NOT start until the service is ready and the dataset is provisioned.
- **FR-003**: The load MUST be driven by 1000 simulated users, each acting as a distinct, pre-existing identity that owns a calendar.
- **FR-004**: The load generator's own measurements (client-observed latency, request counts, failures per flow) MUST be visible in the dashboards alongside the service-side measurements, for the same run.
- **FR-005**: The measured window MUST exclude provisioning, warm-up, and shutdown, so reported numbers describe steady state only.

**Dataset**

- **FR-006**: The environment MUST be provisioned with 1000 users before load begins, each with its own calendar.
- **FR-007**: Each provisioned user MUST hold a substantial slot history that never exceeds 800 slots for that user, with the busiest users approaching that ceiling (counts vary between users; see Assumptions).
- **FR-008**: No user may have more than 100 slots in the booked state, and no user may have more meetings than slots.
- **FR-009**: Provisioned slots MUST be individually valid under the service's own rules (non-overlapping within a user, valid placement), so reads exercise realistic data rather than a body of rejects.
- **FR-010**: Provisioning MUST be idempotent and MUST produce a comparable shape on every run. The dataset MUST be fully established before the measured window opens; the measured run MUST NOT build it up through the service's own write path, so that every request in the measured window is answered against a database that is already at its target size.
- **FR-011**: Provisioning MUST report what it created (users, slots, meetings) and how long it took.
- **FR-012**: Provisioning MUST NOT be the measured load: it is setup, and its cost is reported separately.

**Workload**

- **FR-013**: The workload MUST consist of exactly two flows: (a) checking availability, and (b) manipulating data — adding a slot and then converting that slot into a meeting.
- **FR-014**: 5% of all simulated requests MUST be manipulations (tolerance ±1 percentage point) and the remaining requests MUST be availability checks.
- **FR-015**: Every manipulation MUST perform both steps — add the slot, then convert that same slot — as one logical unit of work.
- **FR-016**: Availability checks MUST run against a bounded, realistic window over the caller's own history, returning non-trivial results rather than querying an empty calendar.
- **FR-017**: All 1000 simulated users MUST be active concurrently and MUST each act as themselves, so per-user data volume, indexing, and ownership rules are genuinely exercised.
- **FR-018**: The workload MUST run 1000 simulated users concurrently at peak: a ramp-up of about 2 minutes, a steady state of at least 10 minutes, then a ramp-down. Only the steady state is measured.
- **FR-019**: The workload MUST be deterministic in shape, so that two runs with the same inputs are comparable and run-to-run randomness does not move the measured mix beyond the stated tolerance.
- **FR-020**: The workload MUST classify expected business refusals (slot overlap, slot already booked, losing a race for the same slot) separately from unexpected failures (server errors, timeouts, connection errors), and MUST NOT count expected refusals as errors.
- **FR-021**: The workload MUST leave the environment in a resettable state, and the documented procedure MUST reset data so consecutive runs remain comparable.

**Observability and verdict**

- **FR-022**: During and after a run, the dashboards MUST show, for that run: request rate, latency distribution (at least the 95th and 99th percentiles) per flow, failure and error rate, and the number of active simulated users.
- **FR-023**: The dashboards MUST show database-level pressure for the same window: open connections, active queries or transactions, and contention or slow-statement evidence.
- **FR-024**: Load-generator measurements and service/database measurements MUST both be visible for the same run, and multiple runs in one session MUST remain distinguishable.
- **FR-025**: A completed run MUST produce a summary stating, per flow, request count, throughput, latency percentiles, and error rate, plus one overall pass/fail verdict.
- **FR-026**: A run MUST be judged against explicit targets: availability checks p95 under 300 ms and p99 under 1 s; manipulation sequences p95 under 500 ms; error rate under 1% (expected business refusals excluded from that rate). A breach MUST fail the run, and the outcome MUST be exposed to an automated caller (for example through the run's exit status), not only to a human reading a chart.
- **FR-027**: Interrupting a run MUST leave the environment recoverable by the documented reset, and a partial run MUST be reported as incomplete rather than as a result.
- **FR-028**: The load test MUST NOT be part of the standard build or continuous-integration gate; it runs only when deliberately invoked.
- **FR-029**: Every asset needed to run the load test MUST live in the repository and be documented (purpose, procedure, metric and target definitions, interpretation guidance).

### Key Entities

- **Simulated user**: one of 1000 identities driving load; owns exactly one calendar and its own data.
- **Provisioned slot**: a time range owned by a simulated user's calendar; up to 800 per user, non-overlapping within a user.
- **Provisioned meeting**: a slot that is already booked; at most 100 per user.
- **Flow**: one of the two request kinds — availability check, or manipulation (add slot, then convert to meeting).
- **Load scenario**: the flow mix and its tolerance, the duration, the ramp, and the 1000 simulated users.
- **Run**: one execution of a scenario against one provisioned dataset; has a measured window, a summary, and a verdict.
- **Generator measurement**: client-observed latency, counts, and failures, attributed to a flow and a run.
- **Service measurement**: server-side request/latency/latency-outcome data for the same window.
- **Database pressure measurement**: connections in use, active work, contention, and slow-statement evidence for the same window.
- **Performance target**: the latency percentile and error-rate bounds that decide pass/fail for a run.

## Clarifications

### Session 2026-10-18

- Q: How long should the run last, and is 1000 a peak concurrency or a total population? → A: 1000 users concurrent at peak; ~2 minute ramp-up, at least 10 minutes steady state, then ramp-down; only the steady state is measured.
- Q: Must the dataset exist before the measured run, or may the run build it through the service's own API? → A: It must be fully pre-seeded before the measured window opens; the measured run must not build the dataset through the write path.
- Q: What defines pass/fail, and should a breach fail the run? → A: Availability checks p95 < 300 ms and p99 < 1 s; manipulation sequences p95 < 500 ms; error rate < 1% (expected business refusals excluded). A breach fails the run and must be visible to an automated caller.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can go from a clean checkout to a completed, summarised load run by following one documented procedure, with no manual dashboard editing and no manual data cleanup on re-run.
- **SC-002**: A completed run exercises 1000 simultaneously active users, and the provisioned dataset reaches the stated per-user ceilings (800 slots, 100 meetings) for the busiest users.
- **SC-003**: The recorded request mix of a run is within 1 percentage point of 95% availability checks and 5% manipulations.
- **SC-004**: For a run, the summary reports, over the steady-state window, the request count, throughput, latency percentiles for availability checks and for manipulations, and an overall error rate, each compared against the targets in FR-026.
- **SC-005**: A run that breaches a target (availability checks at or above 300 ms p95 or 1 s p99, manipulation sequences at or above 500 ms p95, or an error rate at or above 1%) reports failure visibly and is detectable by an automated caller; a compliant run reports success.
- **SC-006**: For every run, the load generator's view and the service/database view of the same time window are both visible in the dashboards, and two runs in one session can be told apart.
- **SC-007**: When the database limits a run, the cause is identifiable from the dashboards alone: which resource, when, and which flow suffered.
- **SC-008**: Provisioning, running, and resetting can be repeated back-to-back with comparable results, with no manual database surgery.
- **SC-009**: The normal build's outcome and duration are unchanged by the presence of the load-test assets.
- **SC-010**: After reading project documentation alone, a newcomer can state the load test's purpose, the exact command sequence, the metric definitions, and the pass criteria.

## Assumptions

- **Generator**: k6 is used because the user asked for it; the metrics store and dashboard stack that already exist in the project are reused rather than duplicated. The load generator runs as a container in the same compose stack so that "run it locally with docker compose" holds end to end.
- **Scale interpretation**: "1000 users" means 1000 simultaneously active simulated users at peak, not a total population over time (confirmed in Clarifications).
- **Dataset timing**: the dataset is established by a separate provisioning step before the measured window opens, so the dataset is a precondition of a run rather than an outcome of it.
- **Per-user data**: "up to 800 slots" is read as a per-user ceiling that the busiest users approach with variation between users, rather than exactly 800 for everyone. Provisioning is therefore shaped, not uniform.
- **Measurement, not optimisation**: the goal is to observe and report latency, throughput, and database behaviour. Tuning the service, schema, or connection pool to hit a target is out of scope; any shortfall is reported as a finding and addressed separately.
- **Unknown outcome at this scale**: whether the current service and its database configuration can satisfy the targets at 1000 simultaneous users is precisely what this feature measures. A run that breaches them and exits non-zero is a valid, useful result of this feature, not a sign that the feature is broken.
- **Expected refusals are normal**: at a 5% manipulation rate, concurrent contention on the same slot is expected; those refusals are part of the workload's realistic behaviour and are excluded from the error rate.
- **Excluded flows**: retrieving or listing slots, cancelling a meeting, and deleting slots are not exercised, so no performance claims are made about them.
- **Measured window**: ramp-up, provisioning, and teardown are excluded from the reported numbers.
- **Host realism**: developer-laptop capacity (CPU, memory, container limits) is the binding constraint, so absolute numbers are indicative of relative behaviour rather than a production capacity certification.
- **Environment reuse**: the existing local service, database, metrics store, and dashboard provisioning are extended, not replaced.
- **Isolation from delivery**: in line with how the project already treats expensive, non-deterministic checks, the load test stays off the build's critical path and never runs as part of the normal verification suite.
