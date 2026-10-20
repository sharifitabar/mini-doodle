# Phase 0 Research: Multi-Dimensional Quality Gates

**Feature**: 006-quality-gates | **Date**: 2026-10-17

This document resolves the technical unknowns behind the plan. Decisions are recorded as
**Decision / Reason / Alternatives considered**, matching the Spec Kit convention.

Every number quoted here was **measured on the current tree**, not estimated. The measurements were
taken with the tooling that will enforce them (see "How the measurements were taken" at the end).

---

## Baseline of the current suite (the input to every floor)

| Dimension (JaCoCo counter) | Measured | Covered / total |
|---|---|---|
| Instruction | 94.7% | 3364 / 3551 |
| Branch | 85.1% | 143 / 168 |
| Line | 94.1% | 801 / 851 |
| Method | 95.1% | 215 / 226 |
| Class | 98.6% | 72 / 73 |
| Cyclomatic complexity | 90.3% | 280 / 310 |

Known gaps, all confirmed by measurement:

| Location | Line | Branch | Method | Complexity | Cause |
|---|---|---|---|---|---|
| `GlobalExceptionHandler` | 80.0% | n/a | 73.7% | 73.7% | 5 of 15 error mappings never invoked |
| `JooqSlotRepository` | 81.6% | **46.2%** | 93.3% | 60.7% | retry/backoff/give-up path never entered |
| `CurrentUserArgumentResolver` | 100% | 62.5% | 100% | 57.1% | blank/malformed header branches untested |
| `JooqMeetingRepository` | 96.4% | 75.0% | 100% | 84.6% | one transient-failure branch untested |
| `TimeRange` | 90.0% | 50.0% | 66.7% | 57.1% | `overlaps()` is **dead code** (see D8) |
| `DevDataSeeder` | **0%** | n/a | 0% | 0% | disabled by property. Manually written, so not excludable |

---

## Decision D1: Enforce coverage with `jacocoTestCoverageVerification`, wired into `check` explicitly

**Reason**: The Gradle JaCoCo plugin creates the `jacocoTestCoverageVerification` task but does
**not** attach it to `check` in this Gradle version. Verified empirically:

```text
$ ./gradlew check --dry-run --console=plain | grep jacoco
  :jacocoTestReport SKIPPED          # present only because test.finalizedBy(jacocoTestReport)
$ ./gradlew check --dry-run --console=plain | grep -c jacocoTestCoverageVerification
  0
```

The plugin's own documentation ("Code coverage metrics can be enforced via ... a
`JacocoCoverageVerification` task ... The `jacocoTestCoverageVerification` task is **not** wired to
`check`") matches the observed behavior. Enforcement thus requires one explicit
`dependsOn`, which keeps the gate on the existing standard build (pre-push hook + CI) with no new
hook or CI step (FR-005).

Two wiring details are load-bearing:

1. **The generated-source exclusion must be repeated.** `JacocoReport` and
   `JacocoCoverageVerification` each own an independent `classDirectories`. The jOOQ exclusion
   already applied to the report (`classDirectories.setFrom(files(... fileTree { exclude(...) }))`)
   must be applied to the verification task as well, or the generated code re-enters the
   denominator and moves every ratio (FR-004). The plan factors the filtered collection into a
   single shared value used by both tasks.
2. **`minimum` is a `BigDecimal`, not a `String`, in this Gradle version.** The limit is expressed
   as `minimum = "0.90".toBigDecimal()`.

**Alternatives considered**:
- **A custom `doLast` that parses the JaCoCo XML** — rejected: reimplements a supported API, and
  loses the task-graph integration (`--dry-run` visibility, up-to-date checks, report linkage).
- **Third-party Gradle plugins (`kover`, `jacoco-report-aggregation`)** — rejected: Kover is the
  Kotlin-oriented alternative and would replace working JaCoCo wiring for no benefit at this scale
  (constitution IV).
- **Relying on the plugin to wire `check`** — rejected because it demonstrably does not happen.

---

## Decision D2: Enforced counters and floors are a deliberate subset of the six measured dimensions

**Decision**: the bundle-level violation rules enforce **line ≥ 0.95, branch ≥ 0.95,
method ≥ 0.95, cyclomatic complexity ≥ 0.95**. Instruction and class coverage remain *measured and
reported* but are not given bundle-level floors.

**Reason**:
- Instruction coverage is a strictly finer-grained view of line coverage (99.41% vs 99.16% today)
  and moves almost identically. A separate floor adds a second, correlated failure mode with no
  new signal.
- Class coverage is 100% today only because of the gap-closing work in D5–D8, and it is a
  near-binary metric (one class flips it by ~1.4 points). A floor on it is effectively a floor on
  one class. The class-level concern that *does* matter is expressed precisely, by per-class rules
  (D3).
- The four enforced counters each fail for a genuinely different reason: line = "not executed",
  branch = "one decision path never taken", method = "whole behaviour uninvoked",
  complexity = "decision points never exercised". Together they cover the axes the baseline audit
  showed matter.
- **Floor policy (one rule, applied everywhere)**: each floor is the ratio the suite *actually
  achieves*, rounded **down** to the nearest 0.05. Measured after the gap-closing tests landed:
  line 99.16%, branch 97.22%, method 99.19%, complexity 97.92% → all four floors 0.95. A floor set
  this way always passes when `test` passes, and it can only be tripped by a real regression
  (at least one newly uncovered line/branch pair), which is the spec's
  "regression guard, not a target" assumption made mechanical.

**Alternatives considered**:
- Floors pinned exactly at the measured ratio (for example 0.9907) — rejected: they would fail on any
  renaming/refactor that adds a single uncovered instruction, turning the gate into a change tax
  rather than a regression guard.
- Floors at round "targets" (for example line 100%) — rejected: unreachable, so the gate would fail on
  day one, which contradicts SC-009.
- Enforcing all six counters — rejected as over-constraining and correlated (above).
- Keeping the pre-implementation floors (line 0.90 / branch 0.80) — rejected: those were derived
  from a baseline whose *gaps* this feature closes. A floor derived from the un-fixed state would
  let every one of those gaps reopen silently.

---

## Decision D3: The error-path dimension is expressed as per-class rules, not a new metric

**Decision**: `GlobalExceptionHandler` and the slot write adapter carry **class-scoped** violation
rules, in addition to the bundle-level rules. These are the as-built floors, each the class's
measured ratio rounded down to the nearest 0.05 (D2):

| Class | Counter | Floor | Measured after gap-closing |
|---|---|---|---|
| `GlobalExceptionHandler` | LINE, METHOD | 1.00 | 40/40 lines, 19/19 methods |
| `JooqSlotRepository` | LINE | 0.95 | 93/94 = 98.94% |
| `JooqSlotRepository` | BRANCH | 0.90 | 11/12 = 91.67% |
| `JooqMeetingRepository` | LINE | 0.95 | 84/85 = 98.82% |
| `JooqMeetingRepository` | BRANCH | 0.90 | 9/10 = 90.00% |
| `CurrentUserArgumentResolver` | BRANCH | 1.00 | 8/8 = 100% |

`TimeRange` and `DevDataSeeder` — the two other classes the baseline identified — are *not* given
class-scoped rules: `TimeRange` reaches 100% line/branch once its dead `overlaps()` method is
deleted (D8), and `DevDataSeeder` reaches 100% line/method once a test enables the seeding property
(D7). They are held by the bundle floor, which is the honest place for a class with no residue.
`JooqSlotRepository` and `JooqMeetingRepository` are the classes where a residual uncovered line is
accepted. Both are documented in `build.gradle.kts` next to their rules and in the as-built
addendum below.

**Reason**: FR-006 requires exception/error paths to be *reported as a distinct dimension so
gaps are visible independently of overall coverage*. JaCoCo has no "error path" counter — the only
honest way to express that dimension with the tool that already exists is to name the
error-handling types explicitly and hold them to a stricter standard than the codebase average.
This is genuinely different from the bundle floor: `GlobalExceptionHandler` was 80% while the
bundle was 94%, and only a class-scoped rule can see it. It also makes FR-011 (error tests must
assert the specific outcome) enforceable in effect: a handler whose body is not executed fails the
build.

**Alternatives considered**:
- A hand-rolled "annotate `@ExceptionHandler` methods and count them" script — rejected: it would
  be a bespoke metric maintained outside the build, and this feature's whole point is to use
  enforced, standard measurement.
- Per-package floors (for example `com.minidoodle.shared.web`) — rejected: too coarse. The package also
  contains code unrelated to error mapping.
- A single bundle floor raised to 100% — rejected: would fail on day one and says nothing about
  *which* behavior is missing.

---

## Decision D4: Architecture conformance through ArchUnit 1.4.1 as ordinary tests

**Decision**: add `testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")` and write the
layering rules as JUnit 5 tests, so they run inside the existing `test` task and thus inside
`check`/`build` with no wiring at all.

**Version pinned to 1.4.1, and why it is not the newest.** The first attempt used 1.5.1 (the current
release), which broke the test run outright:

```text
java.lang.NoClassDefFoundError: org/junit/platform/engine/OutputDirectoryCreator
  at JUnitPlatformTestClassProcessor.createLauncherDiscoveryRequest(...)
```

ArchUnit 1.5.x is built against `junit-platform-engine:1.14.4`, which pulls `junit-bom:5.14.4` into
the graph and resolves `junit-platform-launcher` to **1.14.4** while Spring Boot's dependency
management keeps `junit-platform-engine` at **1.12.2**. The Gradle 8.14.3 test worker needs an
engine class that 1.12.2 does not have. ArchUnit 1.4.1 declares
`junit-platform-engine:1.12.2` — exactly the Boot-managed version — so the graph stays consistent.
Verified with `./gradlew dependencies --configuration testRuntimeClasspath`: every
`junit-platform-*` artifact resolves to 1.12.2 and `junit-bom` to 5.12.2.

**Rules as built and their measured status on the current tree**:

| Rule | Statement | Today |
|---|---|---|
| R1 | The domain does not depend on Spring, Jakarta, jOOQ or Hibernate | **PASS** — the domain's complete import set is `java.*` plus its own `domain.*` packages |
| R2 | The domain depends on nothing but the JDK and the domain itself | **PASS** — same measurement, stated as closure rather than a deny-list |
| R3 | The application layer must not depend on adapters (inbound or outbound) | **PASS** — zero matches for `com.minidoodle.*adapter` in `application` |
| R4 | Inbound adapters and outbound adapters must not depend on each other | **PASS** — `adapter.in` never imports `adapter.out` and vice versa |
| R5 | Shared infrastructure must not depend on the application or the adapters | **PASS** — `shared` imports only domain types and the JDK |
| R6 | The context's packages form no dependency cycle | **PASS** on the tree, **FAIL** on the cycle fixture (that is the point) |

**The cycle rule needed rescuing from a false positive.** The obvious form — "no cycles between
the top-level packages of `com.minidoodle`" — is **fails on a correct tree**: `calendar` and `shared`
reference each other legitimately (inbound web adapters use `shared.web.CurrentUser`,
`shared.web.GlobalExceptionHandler` maps `calendar.domain.exception` types). Once `shared.web` is
read as another adapter, every dependency in that pair points inward and there is no cycle to fix.

Two changes made the rule honest:

1. **Slice by leaf package, from the context root**: `slices().matching("com.minidoodle.calendar.(**)..")`
   makes each leaf package its own slice (`domain`, `application`, `adapter.in`, `adapter.out`, ...)
   rather than lumping everything into two coarse slices. The graph is then acyclic, as it should be.
2. **Keep it falsifiable**: the rule is evaluated against a real two-package cycle
   (`com.minidoodle.calendar.fixture.cyclic.a` ↔ `...b`) and asserted to report it. Without that,
   the pattern change could have made the rule match nothing at all and continue to pass forever — which
   is precisely the failure mode FR-016 exists to prevent.

`shared` is outside the R6 pattern because it needs no cycle rule of its own: R5 forbids it from
depending on the application or any adapter, and R2 forbids the domain from depending on it, so no
cycle through `shared` can exist. R2–R5 and R6 together are the acyclic guarantee.

**Important scope correction discovered by measurement**: the application layer **does** use Spring
(`@Service` ×3, `@Component` ×4, `@Transactional` ×2), so the intuitive "domain *and* application are
framework-free" rule would be **unable to pass today**. The constitution only makes that claim for the domain
("No Spring, Jakarta, or jOOQ types in domain code"), so A1/A2 are scoped to the domain and the
application is constrained only by direction (A3), not by framework use. Getting this wrong is how
an architecture gate fails when added.

**FR-016 (every rule must be shown to fail)**: production rules import the production classpath and
exclude tests (`ImportOption.DoNotIncludeTests`). A companion test then evaluates each rule against
deliberately violating fixture classes that live **in the test sources**, and asserts the rule
reports them. Without this, a typo'd package pattern yields a rule that is able to pass indefinitely without a meaningful check.
The rules are thus declared once, as data, in `ArchitectureRules` and consumed twice: enforced
in `ArchitectureTest`, proven effective in `ArchitectureRuleEffectivenessTest` (one fixture per
rule, five in total, under `com.minidoodle.architecture.fixture`).

**Alternatives considered**:
- **jQAssistant / Sonargraph / Structurizr** — rejected: heavier, separate tooling and storage, and
  out of proportion to five rules (constitution IV).
- **A `layeredArchitecture()` declaration** — rejected in favour of explicit `noClasses()...` /
  `classes()...` rules: the layered DSL requires a layer name for every package and fails
  confusingly on unspecified ones, whereas explicit rules produce the "rule + offending type"
  message FR-015 demands.
- **Failing the build from a separate `archTest` task** — rejected: would need new `check` wiring
  for no benefit. These are tests.
- **Keeping ArchUnit 1.5.1 and pinning the JUnit launcher back to 1.12.2 by force** — rejected: the
  1.5.x engine is compiled against 1.14.4 APIs and would risk `NoSuchMethodError` at runtime. Using
  the version whose declared baseline matches the managed one is the non-invasive fix.

---

## Decision D5: Close the error-response gap with real HTTP triggers where they exist, and one test-only controller where they do not

**Decision**: cover the five untriggered `@ExceptionHandler` mappings as follows. The third column
is the *as-built* finding, which corrected the plan: two of the five turned out not to be reachable
either.

| Handler | Trigger | Reachable in production? | Covered by |
|---|---|---|---|
| `handleTypeMismatch` | `GET /api/v1/slots/not-a-uuid` | **Yes** | `WebErrorContractIT` (HTTP) |
| `handleUnreadable` | any endpoint with a syntactically invalid JSON body | **Yes** | `WebErrorContractIT` (HTTP) |
| `handleMissingHeader` | `MissingRequestHeaderException` | **No** — the application declares **no `@RequestHeader`** anywhere. The custom `CurrentUserArgumentResolver` throws its own `MissingCallerIdException` instead | `ExceptionHandlerAdviceTest` (standalone MockMvc, real advice) |
| `handleInvalidSlot` | planned as `PATCH /api/v1/slots/{id}` with an empty-ish body | **No** — `UpdateSlotRequest` carries `@AssertTrue` on `anyFieldPresent()`, so bean validation raises `MethodArgumentNotValidException` **before** `SlotController` can throw `InvalidSlotException` | `ExceptionHandlerAdviceTest` |
| `handleInvalidMeeting` | planned as a participant with neither `name` nor `email` | **No** — `ParticipantRequest` carries `@AssertTrue` on `isNameOrEmailPresent()`, so the same pre-emption applies to `InvalidMeetingException` | `ExceptionHandlerAdviceTest` |

**Why this is not "testing dead code".** The mappings are not dead: they are the RFC 7807 shape the
API promises for those exception types, and they are reachable from *other* code paths (the service
layer throws them, nothing in this feature's scope changed that). What is true is that no HTTP
request in the current API can reach them, because request-level bean validation pre-empts the
domain invariant. Three options were available: delete the mappings, cover them through the real
advice over a throwaway controller, or leave them uncovered and weaken FR-007. Deleting would
remove the promised error contract for exception types the service can still raise. The throwaway
controller exercises the real `GlobalExceptionHandler` through the real `DispatcherServlet` and
asserts the status, problem type, title and detail for each.

**The throwaway controller lives outside `com.minidoodle`** (`qualitygate.advice`). Spring Boot
component scans from the application class's package, so a `@RestController` in the test sources
under `com.minidoodle` would be a scan candidate and would publish `/unreachable/**` into every
integration test that boots the real context. It also has to carry a stereotype, because the
standalone MockMvc setup detects handlers by controller annotation: without one the request finds no
handler, answers 404, and never reaches the advice at all. (Both facts were established by
experiment, not assumption.)

**Also closed here**: the blank-header branch of the caller resolver
(`raw.isBlank()` with a non-null header) is triggered over HTTP by `X-User-Id: "   "` in
`WebErrorContractIT`, and the resolver's "annotated but not a `UserId`" guard is unit-tested
directly in `CurrentUserArgumentResolverTest` — the one branch that no request can produce.

**Reason**: FR-007 asks that every documented error response be produced by a test. Two of the
five are reachable through the public API and are covered that way — the strongest form, since they
also assert the status and problem body the contract promises. The other three cannot be produced by
any request in the current API. Covering them through the real advice over a test-only controller
keeps those mappings honest and tested, whereas deleting them would remove the RFC 7807 shape for
exception types the service layer still raises, and leaving them uncovered would contradict the
requirement.

**Alternatives considered**:
- Triggering `MissingRequestHeaderException` by adding a `@RequestHeader` parameter to a real
  endpoint — rejected: adding production surface only to test a handler is worse than testing the
  handler directly.
- For `InvalidSlotException`/`InvalidMeetingException`, relaxing the request-level `@AssertTrue`
  guards so the domain invariants become reachable over HTTP — rejected: that would trade a
  meaningful, early, field-level 400 for a later domain 400 to make testing convenient. The
  duplicate-guard situation is deliberate. The mapping is tested for its own sake instead.

---

## Decision D6: The retry/backoff path is covered through an injectable seam, not a real deadlock

**Decision**: extract the retry loop currently inside `JooqSlotRepository.execute(Runnable)`
(lines 177–216) into a small package-private collaborator with an injected sleep strategy, and drive
it from a fast unit test with synthetic `SQLException`s.

The behavior to cover, exactly as it exists today:

| Case | Expected |
|---|---|
| Action succeeds first attempt | returns, no retry |
| `SQLState 40P01` (deadlock) then success | retries once, backs off, returns |
| `SQLState 40001` (serialization failure) then success | retries, backs off, returns |
| Transient conflict, attempts exhausted (`MAX_ATTEMPTS`) | rethrows the original exception |
| `SQLState 23P01` (exclusion violation) | throws `SlotOverlapException` immediately, no retry |
| Any other `SQLState` | rethrows immediately, no retry |
| Sleep interrupted | restores the interrupt flag and throws `IllegalStateException` |

**Reason**: The retry path keys off SQLState values produced by PostgreSQL under GiST exclusion
constraint contention. Provoking that deterministically requires orchestrating lock contention, and
a test that only *usually* hits the deadlock is precisely the flaky test the constitution forbids
("skipped, ignored, or flaky tests are not acceptable"). Injecting the sleep also removes all real
sleeping from the test, and makes the `InterruptedException` branch — currently the only
never-entered `catch` block in the codebase — directly testable. This is the fallback the spec's
Assumptions anticipated ("exercise the recovery logic through a fault-injection seam rather than a
real lock conflict").

**Alternatives considered**:
- **Real contention through `pg_advisory_lock` / overlapping inserts** — rejected: nondeterministic.
  It is exactly how the current gap arose (a concurrency IT exists but never provokes the transient
  SQLState).
- **Mocking `DSLContext` to throw** — rejected: the retry loop is private and driven by internal
  calls, so a mock cannot reach it without reflection or a seam anyway. The seam is the cleaner of
  the two.
- **Excluding the class from coverage** — rejected: it is the most consequential untested logic in
  the codebase.

---

## Decision D7: `DevDataSeeder` is covered by enabling its property, not excluded

**Decision**: add an integration test with `app.seed-demo-data=true` against the existing
Testcontainers PostgreSQL that runs the seeder and asserts the demo user and calendar exist and the
operation is idempotent (running twice leaves one row each).

**Reason**: It is manually written production code that runs at startup in the docker-compose and
local profiles. Excluding it from verification would be a suppression — the very thing the spec's
"no suppression file" success criterion rules out. The existing `PostgresIntegrationTest` base and
the `jdbc:tc:` datasource already make this cheap.

**Alternatives considered**: excluding the class from `jacocoTestCoverageVerification` (rejected:
suppression). Deleting the seeder (rejected: it is a documented local/docker-compose feature).

---

## Decision D8: `TimeRange.overlaps()` is dead code and should be removed

**Decision**: delete `TimeRange.overlaps(TimeRange)` rather than write a test for it. Confirmed by
search: the method has **zero call sites** in `src/main` and `src/test`.

**Reason**: The spec forbids chasing a coverage number ("chasing a coverage number is explicitly
not a goal"). Writing a test whose only purpose is to raise a ratio for code nothing calls is the
anti-pattern this feature exists to prevent, and a speculative public method on a domain record
contradicts the constitution's YAGNI principle. Removing it makes the coverage floor honest: the
branch-coverage gap in `TimeRange` disappears because the branch disappears.

FR-010's *intent* — that interval boundary conditions are genuinely exercised — is still met: the
interval types production actually uses (`TimeFrame`, `SlotOccupancy`, `AvailabilityInterval`) carry
the overlap/frame rules and are exercised by the domain tests and the overlap integration tests.

**This is a production code deletion, and the only scope exception in this feature.** It is called
out again in the plan's Open Items so it is visible for approval rather than buried.

**Alternatives considered**: writing the test (rejected above). Leaving it uncovered (keeps ~7
missed instructions and a class branch rule that always fails).

---

## Decision D9: Mutation testing through `info.solidsoft.pitest` 1.19.0, on-demand and domain-scoped

**Decision**: add the `info.solidsoft.pitest` Gradle plugin at `1.19.0`, configure
`junit5PluginVersion = "1.2.3"`, scope mutation to the domain package, and leave it **out of
`check`** entirely. Invoked as `./gradlew pitest`.

**Reason / compatibility** (verified from the plugin's release notes and Maven Central):
- `1.19.0` is the current release (2026-03-29) and the first line compatible with modern Gradle:
  minimum Gradle 8.4 and Java 17 (we have 8.14.3 / 21), with explicit support for running on
  JDK 24 and Gradle 8.14. It ships PIT `1.22.1`.
- The older widely-cited `1.15.0` predates that compatibility work, and `1.9.x` requires the
  obsolete plugin API. `1.19.0` is the correct pin.
- `pitest-junit5-plugin` `1.2.3` requires PIT ≥ 1.19.4, satisfied by the bundled 1.22.1.
- The plugin adds a `pitest` task and does **not** attach it to `check`, which is exactly the
  on-demand semantics FR-020 requires.

**Scope reason**: mutation analysis runs tests again for each mutant. The only tests that cover the
domain layer quickly are the domain unit tests. The application and adapter layers are covered by
MockMvc/Testcontainers integration tests whose container startup cost would be multiplied by
hundreds of mutants. Mutation is thus scoped to:
`targetClasses = ["com.minidoodle.calendar.domain.*"]`,
`targetTests = ["com.minidoodle.calendar.domain.*"]`.

**No mutation threshold is configured**: equivalent mutants (inherent in `toString`, logging, and
defensive branches) can never be killed, so a threshold would fail for reasons unrelated to test
quality. The score is a signal for triage (FR-021), which matches the spec's on-demand framing.

**Alternatives considered**:
- PITest Maven/CLI driving — rejected: no Maven here, and the Gradle plugin is the maintained path.
- A `mutationThreshold` gate — rejected above.
- Mutating the whole codebase — rejected: hours per run, dominated by container startup.
- Scoping with `mutationThreshold` per package — deferred. No evidence yet that the score is stable
  enough to gate on.

---

## Decision D10: Complexity ceilings go into the existing selected Checkstyle ruleset

**Decision**: add four `TreeWalker` checks to `config/checkstyle/checkstyle.xml`, chosen so the
current tree passes with **no suppression and no baseline file** (FR-018):

| Check | Ceiling | Measured max today | Where the max is |
|---|---|---|---|
| `CyclomaticComplexity` | 10 (Checkstyle default) | 7 | `Participant:16`, `SlotApplicationService:110`, `JooqSlotRepository:177` |
| `NPathComplexity` | 30 | 27 | `Participant:16` |
| `MethodLength` | 70 | 65 | `SlotRepositoryConcurrencyIT:63` |
| `ParameterNumber` | 7 (Checkstyle default) | 7 | `Meeting:29`, `Meeting:61` |

**Reason**: These are the four metrics that map onto the branch-coverage blind spot the baseline
audit found: the code with the worst branch coverage is the code with the most decisions per
method. They are also the cheapest possible gate — they ride the Checkstyle configuration that
already exists, run in the `checkstyleMain`/`checkstyleTest` tasks already wired into `check`, add
no dependency, and take no measurable build time. `NPathComplexity`'s ceiling is deliberately
tightened from its default of 200, which would otherwise never fire. 30 Sits just above the current
maximum, so it is a real (if forgiving) ceiling rather than decoration.

**Checks deliberately excluded, with the measured reason** (this is the interesting half):

| Check | Why excluded |
|---|---|
| `ClassFanOutComplexity` | Current max is **40** (`SlotController`), against a default of 20 — the initial build would fail. Raising the ceiling to 41 would enforce nothing. Coupling/direction is better expressed by the ArchUnit rules (D4), which say *which* dependency is wrong instead of counting types. |
| `ClassDataAbstractionCoupling` | Max is 5 against a default of 7 — it passes, but the signal duplicates `ClassFanOutComplexity` and ArchUnit. |
| `ExecutableStatementCount` | Max is 26 against a default of 30, and it is strongly correlated with `MethodLength`. Two checks for one concern is noise. |
| `JavaNCSS` | Max is 35 against a default method maximum of 50. Correlated with `MethodLength`. |

**Alternatives considered**: enabling published "Google Checks"/"Sun Checks" wholesale (rejected in
feature 005 for churn). A separate metrics tool. `SuppressionFilter` baselines (rejected: FR-018
forbids them).

---

## Resolved unknowns

| Unknown | Resolution |
|---|---|
| Is `jacocoTestCoverageVerification` already in `check`? | **No** (measured). Requires one explicit `dependsOn`. |
| Which counters get bundle floors? | Line 0.95, branch 0.95, method 0.95, complexity 0.95 (each the as-built ratio rounded down to 0.05). Instruction/class measured only. |
| How is the "error-path dimension" expressed? | Per-class rules on `GlobalExceptionHandler`, `JooqSlotRepository`, `CurrentUserArgumentResolver`. |
| ArchUnit version and scope | `archunit-junit5` **1.4.1** (1.5.1 breaks the Gradle test worker through a launcher/engine mismatch). Domain is framework-free, **application is not** (uses Spring). |
| Do all architecture rules pass today? | Yes — all five passed verification, and each proven to fail against a violating fixture. |
| Are all five untriggered handlers reachable over HTTP? | Two are (`handleTypeMismatch`, `handleUnreadable`). The other three are not — `MissingRequestHeaderException` (no `@RequestHeader` in the app) and the two domain-exception mappings (request bean validation pre-empts them). |
| How is the retry path made deterministic? | Injectable-seam extraction + synthetic SQLStates. No real locks. |
| Is `TimeRange.overlaps` used? | No — zero call sites. Deleted in this feature. |
| Is `DevDataSeeder` excludable? | No — manually written. Cover it with a property-enabled IT. |
| PITest plugin/version compatibility | `info.solidsoft.pitest` 1.19.0 (Gradle 8.4+, Java 17+, PIT 1.22.1) + `junit5PluginVersion` 1.2.3. |
| Complexity defaults vs reality | `ClassFanOutComplexity` would fail today (40 vs 20) and is excluded. The other three defaults pass. |

---

## How the measurements were taken

So the numbers can be reproduced rather than trusted:

1. **Coverage figures** — `./gradlew test jacocoTestReport`, then parsed
   `build/reports/jacoco/test/jacocoTestReport.xml` per counter, per class, and per source line.
2. **Complexity maxima** — a throwaway `config/checkstyle/checkstyle.xml` was written with the
   eight candidate metric checks all set to `max=1` (so every method above the trivial minimum is
   reported with its measured value), `./gradlew checkstyleMain checkstyleTest` was run, the
   violations were parsed for their reported values, and the original configuration was restored
   (`git diff` clean). This both proves the eight module names exist in Checkstyle 14.3.0 (an
   unknown module aborts configuration) and yields the real distribution behind the ceilings in
   D10. `MethodLength` needed a corrected parse: its message is
   "Method *name* length is N lines", not "Method length is N".
3. **Layer violations** — direct import analysis of `src/main/java` per layer.
4. **Handler reachability** — direct search for `throw new …Exception` sites and for `@RequestHeader`
   / `@RequestParam` declarations in the controllers.
5. **Build wiring** — `./gradlew check --dry-run --console=plain`.
6. **Tool versions** — project release pages and Maven Central for ArchUnit 1.4.1, the Gradle Plugin
   Portal for `info.solidsoft.pitest` 1.19.0, and the `pitest-junit5-plugin` README for 1.2.3.

---

## As-built addendum

Measured on the finished tree, from `build/reports/jacoco/test/jacocoTestReport.xml` after
`./gradlew test jacocoTestReport`. This supersedes the earlier "baseline" table at the top of this
document, which describes the tree *before* the gap-closing tests landed.

**Bundle (jOOQ generated sources excluded):**

| Counter | As built | Covered / total | Floor |
|---|---|---|---|
| Instruction | 99.41% | 3856 / 3879 | measured only |
| Branch | 97.22% | 175 / 180 | 0.95 |
| Line | 99.16% | 949 / 957 | 0.95 |
| Cyclomatic complexity | 97.92% | 330 / 337 | 0.95 |
| Method | 99.19% | 245 / 247 | 0.95 |
| Class | 100% | 78 / 78 | measured only |

**The classes the audit identified:**

| Class | Before | After | Class-scoped floor |
|---|---|---|---|
| `GlobalExceptionHandler` | 80.0% line, 73.7% method | 40/40 line, 19/19 method | 1.00 line, 1.00 method |
| `JooqSlotRepository` | 81.6% line, 46.2% branch | 98.94% line, 91.67% branch | 0.95 line, 0.90 branch |
| `JooqMeetingRepository` | 96.4% line, 75.0% branch | 98.82% line, 90.00% branch | 0.95 line, 0.90 branch |
| `CurrentUserArgumentResolver` | 62.5% branch | 100% branch | 1.00 branch |
| `TimeRange` | 90.0% line, 50.0% branch | 100% / 100% | none needed (dead method deleted) |
| `DevDataSeeder` | 0% | 100% line, 100% method | none needed |

**Accepted residue** (documented rather than excluded): the `throw ex;` that rethrows a delete
failure which is not a foreign-key violation in `JooqSlotRepository`, and the analogous rethrow for
a failure that is not a unique violation in `JooqMeetingRepository` — plus their branches. Both are
reachable only on an infrastructure fault, and this project has no mocking framework. Fabricating
one for a defensive rethrow would buy less than the floors already do. The floors are set so this
residue is visible (neither class can drop below 0.90 branch) rather than hidden.

**Verification of the whole gate** (`./gradlew check`) — every task present, in order:
`jooqCodegen`, `compileJava`, `checkstyleMain`, `compileTestJava`, `checkstyleTest`,
`spotlessCheck`, `test`, `jacocoTestCoverageVerification`, `jacocoTestReport`. `pitest` is
**absent** from that list, which is the design: mutation analysis is on demand.

**Mutation analysis** (`./gradlew pitest`, domain scope, run once during implementation):

```text
>> Line Coverage (for mutated classes only): 159/178 (89%)
>> Generated 71 mutations Killed 61 (86%)
>> Mutations with no coverage 7. Test strength 95%
> run mutation analysis : 2 seconds
```

PIT ran standalone, so the plugin wiring, the JUnit 5 plugin and the scope all work. No threshold is
set, and the task is not in `check`, so a mutation score can never block a push.
