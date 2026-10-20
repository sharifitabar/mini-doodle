# Feature Specification: Multi-Dimensional Quality Gates

**Feature Branch**: `006-quality-gates`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "Add multi-dimensional quality gates: coverage thresholds beyond line/branch, error-path and architecture conformance tests, complexity limits, and mutation testing"

## Context

Feature 005 added a required style gate for formatting and lint through the existing `./gradlew build`.
It also added line and branch coverage measurements that are *report-only*.
This feature closes two gaps: coverage is not enforced, and only two dimensions are measured.

1. **Nothing fails when coverage regresses.** The suite currently sits at 94.1% line / 85.1%
   branch. That is a fact, not a guarantee.
2. **Line and branch are not the only meaningful dimensions.** Method, class, instruction and
   cyclomatic-complexity coverage each expose different blind spots, and none of them reveal
   whether tests would *detect* a defect.

A baseline audit of the current suite established the starting position and the specific gaps
this feature must close:

- Method coverage 95.1% — 11 methods never invoked.
- Exception/error-path coverage: only 10 of 15 documented error responses have ever been
  produced by a test. 4 Of 5 `catch` blocks have never been entered.
- The database conflict-recovery path (retry, backoff, eventual give-up) is entirely not tested.
- One domain method (`TimeRange.overlaps`) is never called by any test.
- Only 1.6% of covered lines hide a never-taken branch, so the existing coverage is genuine
  rather than padded — which means a regression floor is meaningful rather than aspirational.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Coverage floors enforced on multiple dimensions (Priority: P1)

A maintainer changes production code and runs the standard build.
The build fails if coverage falls below an agreed floor on any enforced dimension.
The failure identifies the dimension, measured value, and floor.
Read the failure. Add or restore coverage. Run the build again.

**Why this priority**: This is the core of the request — turning a report into a guarantee. It is
the only story that protects the *existing* quality level against future regression, and it is a
prerequisite for trusting every other gate.

**Independent Test**: Run the standard build on the unmodified tree (it passes), then remove or
disable a test that covers a meaningful amount of code and run again — the build fails and the
message identifies which dimension fell below its floor.

**Acceptance Scenarios**:

1. **Given** the repository at its agreed floors, **When** the standard build runs, **Then** it
   succeeds and coverage is reported on multiple dimensions.
2. **Given** a change that removes covered behavior from the test suite, **When** the standard
   build runs, **Then** it fails, naming the dimension that fell below its floor.
3. **Given** a maintainer wants detail, **When** they open the local coverage report, **Then**
   they can see per-package, per-class, and per-line coverage for every dimension.
4. **Given** machine-generated code exists in the tree, **When** coverage is measured, **Then**
   generated code is excluded from the measurement and from the floors.

---

### User Story 2 - Error and recovery paths are actually covered (Priority: P1)

A maintainer needs confidence in the API's documented error responses and database conflict-recovery logic.
These paths run only when an operation fails.
An automated test produces each documented error response.
Tests exercise the recovery logic, including retry, backoff, and failure after the final attempt.

**Why this priority**: These paths are the highest-risk code in the system and are currently the
least tested. They are also what makes the floors in User Story 1 meaningful rather than a number
that only reflects the happy path. Reproducing a real database conflict is genuinely hard, so
this story carries the most technical risk of the set and must be proven early.

**Independent Test**: Run the test suite and inspect the coverage report — every documented error
response appears as a produced outcome, and every instruction of the recovery path is marked
executed.

**Acceptance Scenarios**:

1. **Given** the documented API error responses, **When** the test suite runs, **Then** every
   documented error response is produced by at least one test.
2. **Given** a database write that hits a transient conflict, **When** the conflict-recovery path
   runs, **Then** a test exercises the retry, the backoff wait, and the recovery outcome.
3. **Given** a transient conflict that persists beyond the allowed attempts, **When** the
   recovery path runs, **Then** a test exercises the give-up outcome.
4. **Given** a domain boundary condition that was previously untested, **When** the suite runs,
   **Then** the boundary is exercised in both directions.

---

### User Story 3 - Architectural layering is enforced, not just documented (Priority: P2)

A maintainer or new contributor adds a dependency between packages.
The standard build fails if this dependency violates the hexagonal architecture's layer rules.
For example, an inner layer must not import a framework or persistence library.
The failure message identifies the affected type and violated rule.

**Why this priority**: The repository documents "domain and application layers are framework-free"
as a design decision, but nothing enforces it. Documentation-only invariants decay silently. This
turns a claim into a check. It is P2 rather than P1 because the current layering is already
correct — the value is preventing future decay, not fixing present defects.

**Independent Test**: Introduce a dependency that breaks the layering, run the standard build,
observe the failure naming the rule and the affected type, then revert.

**Acceptance Scenarios**:

1. **Given** the layering rules, **When** the standard build runs, **Then** the existing code
   satisfies every rule.
2. **Given** an inner layer that imports a framework, persistence, or web type, **When** the
   standard build runs, **Then** it fails and identifies the rule and the affected type.
3. **Given** a dependency cycle between packages, **When** the standard build runs, **Then** it
   fails and identifies the cycle.
4. **Given** a maintainer reading a failure, **When** they look at the message, **Then** it states
   which layer may depend on which.

---

### User Story 4 - Method complexity has a ceiling (Priority: P3)

A maintainer writes a method whose branching grows beyond an agreed ceiling. The standard build
fails and identifies the method, its measured complexity, and the ceiling, prompting the method
to be split.

**Why this priority**: Complexity is the best available *predictor* of where coverage will be
weak — the branch-coverage gap found in the baseline sits precisely in the most branchy code.
It is P3 because it is a preventive guard rather than a fix for a present defect, and because the
existing codebase is expected to pass it with little or no change.

**Independent Test**: Add a deliberately convoluted method, run the standard build, observe the
failure naming the method and its measured complexity, then revert.

**Acceptance Scenarios**:

1. **Given** the agreed ceilings, **When** the standard build runs, **Then** the existing code
   passes and no suppression or baseline file is required.
2. **Given** a method whose branching exceeds the ceiling, **When** the standard build runs,
   **Then** it fails and reports the method and its measured value.

---

### User Story 5 - Test *strength* is measurable off the critical path (Priority: P3)

A maintainer needs to know whether tests *detect* defects in the code they execute.
An on-demand analysis introduces deliberate faults and reports the proportion that tests detect.
It reports results for each package and overall.
Surviving faults identify possible weak or missing assertions.

**Why this priority**: Coverage answers "was this executed?". This answers "would a test fail if
this were wrong?". It is the only dimension that exposes tests which assert nothing meaningful.
It is P3 and off the critical path because it runs the suite many times and would make every
push unacceptably slow.

**Independent Test**: Run the on-demand analysis on the unmodified tree and make sure that it produces a
score per package and overall, without affecting the duration of the standard build.

**Acceptance Scenarios**:

1. **Given** a maintainer runs the on-demand analysis, **When** it completes, **Then** it reports
   a score overall and per package.
2. **Given** the standard build, **When** it runs, **Then** it does not perform mutation analysis
   and its duration is unaffected.
3. **Given** the analysis reports survivors, **When** the maintainer inspects the report, **Then**
   each survivor identifies the code location and the test that failed to detect it.

---

### Edge Cases

- **A refactor legitimately deletes covered code.** Absolute coverage percentages can rise and
  fall for reasons unrelated to quality. The floors must be low enough to tolerate this without
  becoming meaningless.
- **New code arrives with no tests.** The floors apply to the whole codebase, so a new untested
  module dilutes them rather than being individually rejected.
- **A database conflict cannot be provoked reliably.** Inducing a genuine conflict may be
  nondeterministic. The resulting tests must not become flaky, and a test that passes only
  intermittently is worse than no test.
- **Generated code grows or shrinks.** Excluding it from measurement is what keeps the floor
  stable. A change in generation must not move the numbers.
- **Mutation analysis is slow or noisy.** Equivalent mutants (for example in logging or `toString`) can
  never be killed and will depress the score. The score is a signal for investigation, not a
  build-breaking threshold.
- **A complexity ceiling catches pre-existing code.** Introducing a ceiling that the current tree
  already violates would block all work. The ceilings must be validated against the tree first.
- **An architecture rule is vacuously true.** A rule that no type could ever violate provides
  false confidence. Each rule must be demonstrated to fail when deliberately broken.
- **Coverage floors conflict with an in-flight refactor.** Floors are enforced on the standard
  build, so a branch that temporarily reduces coverage will fail until it is restored.

## Requirements *(mandatory)*

### Functional Requirements

**Measurement and enforcement**

- **FR-001**: Coverage MUST be measured on multiple independent dimensions — at minimum line,
  branch, method, class, instruction, and cyclomatic complexity — not on line and branch alone.
- **FR-002**: The standard build MUST fail when an enforced dimension falls below its configured
  floor, and the failure MUST identify the dimension, the measured value, and the floor.
- **FR-003**: Floors MUST be derived from the measured baseline of the existing suite and recorded
  in the repository with the reasoning for their values.
- **FR-004**: Machine-generated code MUST be excluded from coverage measurement and MUST NOT
  influence the floors, consistently with the exclusions already applied to style checks.
- **FR-005**: The existing standard build MUST measure and enforce coverage through its existing enforcement points.
  This feature MUST NOT add a hook or CI step.
- **FR-006**: Reports MUST show exception handling and error-response paths as a separate coverage dimension.
  This MUST make error-handling gaps visible independently of overall coverage.

**Closing the identified gaps**

- **FR-007**: Every API error response documented in the project's API contract MUST be produced by
  at least one automated test.
- **FR-008**: The database conflict-recovery path MUST be exercised by automated tests covering
  retry, backoff, and recovery.
- **FR-009**: The give-up outcome of the conflict-recovery path (conflict persisting beyond the
  allowed attempts) MUST be exercised by an automated test.
- **FR-010**: Domain boundary conditions identified as untested in the baseline MUST be exercised
  by automated tests, including the interval-overlap boundary in both directions.
- **FR-011**: Tests that exercise error and recovery paths MUST assert the specific outcome
  (error identifier and status, or recovery effect), not only that no exception occurred.

**Architecture conformance**

- **FR-012**: Architectural layering rules MUST be expressed as automated checks that fail the
  standard build when violated.
- **FR-013**: The rules MUST prevent inner layers (domain, application) from depending on
  framework, persistence, or web libraries.
- **FR-014**: The rules MUST prevent dependency cycles between packages.
- **FR-015**: A layering violation MUST produce a failure message that names the rule and the
  affected type.
- **FR-016**: Every architecture rule MUST be demonstrated to fail when deliberately violated.

**Complexity**

- **FR-017**: Method and type complexity ceilings MUST be enforced by automated checks that fail
  the standard build when exceeded.
- **FR-018**: Complexity ceilings MUST be satisfied by the existing codebase with no suppression
  or baseline file.

**Test strength**

- **FR-019**: Mutation analysis MUST be runnable on demand and MUST report a score overall and per
  package.
- **FR-020**: Mutation analysis MUST NOT run as part of the standard build, and MUST NOT increase
  the standard build's duration.
- **FR-021**: Mutation survivors MUST be reported with their code location so they can be triaged.

**Documentation and compatibility**

- **FR-022**: Each enforced gate MUST be documented for maintainers, stating what it checks, how to
  run it, and how to fix a failure.
- **FR-023**: The new gates MUST NOT weaken or conflict with the existing style gate.
  The standard build MUST pass when this feature is added.

### Key Entities

- **Coverage dimension**: an independent measure of test thoroughness (line, branch, method, class,
  instruction, complexity, error-path). Each is reported separately and may carry its own floor.
- **Coverage floor**: the minimum acceptable value for one coverage dimension, derived from the
  measured baseline and recorded with its reason.
- **Conformance rule**: an enforceable statement about which parts of the system may depend on
  which, with a failure message that names the rule and the affected type.
- **Complexity ceiling**: the maximum acceptable branching measure for a method or type.
- **Error-response matrix**: the mapping from documented API error responses to the tests that
  produce them. Its purpose is to make an uncovered response a visible defect.
- **Mutation score**: the proportion of deliberately introduced faults the suite detects, reported
  overall and per package.
- **Gap-closing test**: a test added specifically because the baseline audit found the behavior
  not tested.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A regression that lowers any enforced coverage dimension below its floor fails the
  standard build without any additional command being run.
- **SC-002**: Coverage is reported on at least six independent dimensions, up from two.
- **SC-003**: 100% of documented API error responses are produced by at least one automated test,
  up from 10 of 15.
- **SC-004**: Every recoverable-outcome branch of the database conflict-recovery path is exercised
  by automated tests, up from zero.
- **SC-005**: A deliberate layering violation fails the standard build, naming the rule and the
  affected type.
- **SC-006**: A deliberate dependency cycle fails the standard build.
- **SC-007**: A method exceeding the complexity ceiling fails the standard build, naming the method
  and its measured value.
- **SC-008**: A mutation score overall and per package can be produced on demand without changing
  the standard build's duration.
- **SC-009**: The standard build continues to pass with all new gates active and no suppression file.
- **SC-010**: The existing style gate continues to pass unchanged.

## Assumptions

- **Baseline for floors**: the floors are derived from the measured baseline of the current suite
  (94.1% line, 85.1% branch, 95.1% method, 90.3% complexity) with a deliberate margin below each,
  so that ordinary refactoring does not trip them. Exact values are settled during planning.
- **Enforcement split by cost**: cheap gates run inside the standard build. The expensive analysis
  (User Story 5) is on-demand only. This matches the pattern already used for coverage
  measurement, which is report-only today.
- **The floors are a regression guard, not a target**: chasing a coverage number is explicitly not
  a goal. A floor that is raised only when coverage rises is in scope, while any target above the
  achievable baseline is not.
- **Error-response coverage is driven by the existing API contract**, which already enumerates the
  documented responses, rather than by a manually written list.
- **Tests may use the existing containerized database** and the fixture/fault-injection techniques
  available in the current test infrastructure. Docker remains available in the build environment.
- **Considered and deferred, out of scope for this feature**: bytecode bug-pattern analysis
  (overlaps substantially with the existing lint ruleset), dependency vulnerability scanning
  (better served by the repository host's dependency automation than by build wiring), dependency
  locking for reproducibility, and container/image scanning. These are recorded as deliberate
  exclusions rather than oversights.
- **User Story 2 carries the primary technical risk**: provoking a genuine database conflict
  deterministically may prove difficult. If it cannot be made reliable, the fallback is to
  exercise the recovery logic through a fault-injection seam rather than a real lock conflict.
  The requirement is that the recovery behavior is tested, not how it is provoked.
- **The existing style gate (feature 005) is untouched** by this feature except where documentation
  must reference the new gates.
