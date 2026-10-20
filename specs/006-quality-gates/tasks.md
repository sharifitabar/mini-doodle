---

description: "Task list for feature 006-quality-gates"
---

# Tasks: Multi-Dimensional Quality Gates

**Input**: Design documents from `/specs/006-quality-gates/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/quality-gate.md, quickstart.md

**Tests**: Test tasks ARE generated here, unlike feature 005 — this feature's deliverable *is*
enforcement, so most of the work is tests or measurement of tests. Two kinds appear:

- **Gap-closing tests** (User Story 2) — ordinary tests added because the baseline audit named
  behavior nothing executes.
- **Gate-verification tasks** — running a gate and demonstrating it fails on a deliberate violation.
  These are mandatory, not optional: a gate nobody has seen fail is not evidence.

**Organization**: Tasks are grouped by user story. US1 (enforced coverage floors) and US2 (closing the
named gaps) are both P1 and are **ordered deliberately**: US2's tests land before US1's class-scoped
floors, because a floor above the achievable value would leave the build unable to pass during implementation. US3
(architecture), US4 (complexity), and US5 (mutation) are independent of each other.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (for example, US1, US2, US3)
- Include exact file paths in descriptions

---

## ⚠️ SEQUENCING NOTE (read before executing)

The spec requires the build to **pass when the feature is added** (SC-009, FR-023), but three tasks
below would make it fail if done in the wrong order:

1. **The coverage floors (T007) must not be enabled before the gap-closing tests (T012–T016)** — the
   `GlobalExceptionHandler` floor of `1.00` is only reachable once its five handlers are exercised.
   Bundle floors (T005) are safe immediately, because they sit below today's measured values.
2. **The retry seam (T011) must be extracted before its test (T012) is written**, and the extraction
   must preserve behavior — the concurrency integration tests are the guard.
3. **The complexity ceilings (T021) must be validated against the tree before being trusted** — the
   values in `data-model.md` §6 were measured, but a re-measure is cheap and is the whole point of
   FR-018 (no baseline file).

Between T007 and T013 the build **intentionally fails**. That is local-only: **do not commit or push
in that window.**

**Approval**: Per `AGENTS.md`, the commit task (T032) **must stop and await explicit user approval.**
Do not commit or push without it. T010 deletes production code and is identified for explicit sign-off.

---

## As-built status

All tasks T001–T031 are implemented and verified on this tree. T032 (commit) is pending explicit
approval. Where the build differs from the original task text, the task carries an inline
**as built** note. The substantive deviations, in one place:

1. **ArchUnit 1.4.1, not 1.5.1** — 1.5.x is built against `junit-platform-engine` 1.14.4 and breaks
   the Gradle 8.14.3 test worker against Spring Boot's managed 1.12.2 (`NoClassDefFoundError:
   org/junit/platform/engine/OutputDirectoryCreator`). 1.4.1 matches the managed version exactly.
2. **Six architecture rules (R1–R6), not four** — the cycle rule was kept, but sliced by *leaf*
   package from the context root: a top-level `com.minidoodle` slice rule fails on a correct tree
   because `calendar` and `shared` legitimately reference each other.
3. **Bundle floors are 0.95 × 4, not 0.90/0.80/0.90/0.85** — floors are the *achieved* ratios
   rounded down to the nearest 0.05, measured after the gap-closing tests landed. Deriving them from
   the pre-fix baseline would have let every closed gap reopen.
4. **`CurrentUserArgumentResolver` branch floor is 1.00, not 0.85** — its one request-unreachable
   guard is unit-tested directly, so the class reaches 100% branch coverage.
5. **Two of the five "untriggered" handlers are actually HTTP-unreachable** — `InvalidSlotException`
   and `InvalidMeetingException` are pre-empted by request bean validation (`@AssertTrue` on
   `UpdateSlotRequest`/`ParticipantRequest`). They are covered through the real advice over a
   test-only controller, along with `MissingRequestHeaderException`. The test class and fixture
   controller were renamed accordingly (`WebErrorContractIT`, `ExceptionHandlerAdviceTest`).
6. **One accepted coverage residue** — `JooqSlotRepository:101` (`throw ex;` for a non-foreign-key
   delete failure), documented next to its class-scoped rule in `build.gradle.kts`.

Final measurements, floors and the PIT run are recorded in `research.md`'s as-built addendum.

---

## Phase 1: Setup (Shared Tooling)

**Purpose**: Add the two new tools and refactor the coverage configuration so both coverage tasks
share one generated-source exclusion. Nothing here enforces anything yet — the build continues to pass.

- [x] T001 Add the ArchUnit test dependency to `build.gradle.kts`: `testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")` — **as built: 1.4.1, not 1.5.1**. 1.5.X is compiled against `junit-platform-engine` 1.14.4 and breaks the Gradle 8.14.3 test worker against Spring Boot's managed 1.12.2 (research D4) (FR-012, research D4)
- [x] T002 [P] Add the mutation-analysis plugin to `build.gradle.kts`: `id("info.solidsoft.pitest") version "1.19.0"` — the plugin adds a `pitest` task and does **not** attach it to `check`, which is the required on-demand semantics (FR-020, research D9)
- [x] T003 Refactor the JaCoCo block in `build.gradle.kts`: extract the jOOQ-filtered `classDirectories` (currently inline on `jacocoTestReport`) into one shared value and use it there, so T004 can reuse it (FR-004)
- [x] T004 Run `./gradlew check --console=plain` and make sure that the build continues to pass after T001–T003 (checkpoint: no behavior changed yet)

**Checkpoint**: Both tools are on the classpath. The build passes and unchanged for consumers.

---

## Phase 2: Foundational (Wire the Verification Task)

**Purpose**: Make `jacocoTestCoverageVerification` run inside `check` with the shared exclusion but
**no rules** — it passes trivially, proving the wiring before any floor can fail.

- [x] T005 Wire coverage verification into the standard build in `build.gradle.kts`: set the shared filtered `classDirectories` on `jacocoTestCoverageVerification` and add `tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }` (FR-004, FR-005)
- [x] T006 Confirm the wiring is real, not assumed: run `./gradlew check --dry-run --console=plain` and confirm `jacocoTestCoverageVerification` now appears in the task graph (it does **not** by default — verified in research D1), then run `./gradlew check --console=plain` and make sure that it passes with no rules configured

**Checkpoint**: The gate is on the standard build and provably exercised — but enforces nothing yet.

---

## Phase 3: User Story 1 - Coverage floors enforced (Priority: P1) 🎯 MVP

**Goal**: A change that lowers coverage below an agreed floor on any enforced dimension fails
`./gradlew build` and names the dimension, the measured value, and the floor.

**Independent Test**: Run the standard build on the unmodified tree (passes), then raise one floor
above the measured value and run again — it fails and names that dimension.

- [x] T007 [US1] Add the bundle-level `violationRules` to `jacocoTestCoverageVerification` in `build.gradle.kts`: `LINE` ≥ 0.95, `BRANCH` ≥ 0.95, `METHOD` ≥ 0.95, `COMPLEXITY` ≥ 0.95 (as built — the floors are the achieved ratios rounded down to the nearest 0.05, not the pre-implementation 0.90/0.80/0.90/0.85, see research D2), using `"…".toBigDecimal()` (`minimum` is a `BigDecimal` in this Gradle version — research D1) (FR-001, FR-002, FR-003, data-model §1)
- [x] T008 [US1] Verify enforcement end-to-end: run `./gradlew build --console=plain` and confirm passes on the unmodified tree. Then temporarily raise the `BRANCH` floor above the measured 85.1%, run again `./gradlew jacocoTestCoverageVerification --console=plain`, confirm a nonzero exit naming the counter, the measured ratio, and the minimum, and revert the floor (FR-002, SC-001)
- [x] T009 [US1] Verify the generated-source exclusion holds for the verification task specifically (it owns a separate `classDirectories` from the report): temporarily remove the filter and make sure that the reported ratios move, then restore it — this is the check that proves FR-004 is not satisfied for the report only (FR-004, SC-009)

**Checkpoint**: US1 is independently verifiable — coverage regression now fails the standard build.

---

## Phase 4: User Story 2 - Error and recovery paths are actually covered (Priority: P1)

**Goal**: Every documented error response is produced by a test, the slot-write recovery path is
exercised including its give-up outcome, and the class-scoped floors that encode this dimension are
enabled.

**Independent Test**: Run the suite and re-measure — every handler appears executed, every branch of
the recovery path is exercised, and the class-scoped rules pass.

- [x] T010 [P] [US2] Delete the unused `overlaps(TimeRange)` method from `src/main/java/com/minidoodle/calendar/domain/model/TimeRange.java` — zero call sites in `src/main` and `src/test`. A test for it would exist only to move a number, which the spec forbids (research D8). **⚠️ Requires explicit user sign-off — the only production deletion in this feature. As built: deleted, tests re-verified.**
- [x] T011 [US2] Extract the retry loop from `JooqSlotRepository.execute(Runnable)` (lines 177–216) into a new package-private collaborator `src/main/java/com/minidoodle/calendar/adapter/out/persistence/SlotWriteRetry.java` with an injected sleep strategy, and delegate from `JooqSlotRepository` — behavior must be identical: retry on SQLState `40P01`/`40001`, map `23P01` to `SlotOverlapException`, rethrow others, bounded attempts, restore the interrupt flag on `InterruptedException` (FR-008, research D6)
- [x] T012 [US2] Add `src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotWriteRetryTest.java` covering the cases in `data-model.md` §3 row 6 — first-try success, deadlock-then-success, serialization-then-success, attempts exhausted, exclusion violation mapped, other SQLState rethrown, interrupted sleep restoring the flag — with no container and no real sleeping (FR-008, FR-009). **As built: 9 cases**, adding the a-deep-cause-chain lookup and the non-SQL failure path
- [x] T013 [P] [US2] Add `src/test/java/com/minidoodle/calendar/adapter/in/web/WebErrorContractIT.java` (as built, the plan named it `ErrorResponseContractIT`) covering the **two** reachable untriggered handlers end-to-end over MockMvc, asserting status **and** the problem `type`: non-UUID slot id and malformed JSON body — both `validation` (FR-007, FR-011, data-model §3 rows 1–4, 8). **As built, `invalid-slot` and `invalid-meeting` are *not* reachable over HTTP** — request bean validation pre-empts both — so they moved to T014. The blank/malformed `X-User-Id` branches are covered here.
- [x] T014 [P] [US2] Add `src/test/java/com/minidoodle/shared/web/ExceptionHandlerAdviceTest.java` (as built, the plan named it `MissingHeaderAdviceTest`): a MockMvc standalone test registering the **real** `GlobalExceptionHandler` advice over a test-only controller, asserting the 400 problem-detail body for the three mappings no HTTP request can reach — `MissingRequestHeaderException` (no `@RequestHeader` in the app) plus `InvalidSlotException` and `InvalidMeetingException` (pre-empted by `@AssertTrue` field guards). The controller lives in `qualitygate.advice`, deliberately outside the `com.minidoodle` component-scan root (FR-007, research D5)
- [x] T015 [P] [US2] Add `src/test/java/com/minidoodle/shared/config/DevDataSeederIT.java` running the seeder with `app.seed-demo-data=true` against the existing Testcontainers PostgreSQL, asserting the demo user and calendar exist and that a second run leaves exactly one row each — covering rather than excluding the only manually written 0% class (research D7)
- [x] T016 [US2] Add the class-scoped `violationRules` to `jacocoTestCoverageVerification` in `build.gradle.kts` (`GlobalExceptionHandler` LINE and METHOD = 1.00, `JooqSlotRepository` LINE = 0.95 and BRANCH = 0.90, `CurrentUserArgumentResolver` BRANCH = 1.00 — as built, each the class's achieved ratio rounded down to 0.05, see research D3 and its as-built addendum), **after** T012–T015 have landed (FR-006, data-model §2)
- [x] T017 [US2] Re-measure and make sure that the gap is closed: run `./gradlew test jacocoTestReport --console=plain`, confirm every `@ExceptionHandler` method is executed, confirm the retry path and `DevDataSeeder` are no longer at 0%, and confirm `./gradlew build --console=plain` is green with the class-scoped floors active (FR-007, SC-003, SC-004)

**Checkpoint**: The error/recovery dimension is real: it is measured, enforced per class, and no
longer the place where coverage silently lags.

---

## Phase 5: User Story 3 - Architectural layering enforced (Priority: P2)

**Goal**: A dependency that violates the hexagonal layering fails the standard build, naming the rule
and the affected type.

**Independent Test**: Introduce a violating dependency, run the standard build, observe the failure
naming the rule and the type, then revert.

- [x] T018 [P] [US3] Add the negative-control fixtures under `src/test/java/com/minidoodle/architecture/fixture/` — one deliberately nonconforming class per rule **R1–R5** (as built, five fixtures, for example a domain-shaped type referencing a Spring type, an application-shaped type referencing an adapter, an inbound adapter referencing an outbound one), **plus** a real two-package cycle under `src/test/java/com/minidoodle/calendar/fixture/cyclic/{a,b}/` for R6 (FR-016, data-model §5)
- [x] T019 [US3] Add `src/test/java/com/minidoodle/architecture/ArchitectureTest.java` implementing rules **R1–R6** (as built, the rule set grew a sixth rule — see T020) over production classes only, using `ImportOption.DoNotIncludeTests`, with each rule's failure message naming the rule and the affected type (FR-012, FR-013, FR-014, FR-015, research D4)
- [x] T020 [US3] Add `src/test/java/com/minidoodle/architecture/ArchitectureRuleEffectivenessTest.java` asserting that each rule **fails** when evaluated against its fixture — this is what stops a typo'd package pattern from producing a vacuously green rule forever (FR-016). **As built, all five fixtures live in one import except the cycle fixtures**, which cannot be reached by a pattern anchored at `com.minidoodle.calendar.(**)..` from `com.minidoodle.architecture.fixture`. That rule is thus evaluated against a second import instead
- [x] T021 [US3] Verify enforcement: introduce a deliberate layering violation into a production class (import a Spring type into a domain class), run `./gradlew test --console=plain`, confirm the failure names the rule and the affected type, then revert with `git checkout -- <file>` (SC-005, FR-015)

**Checkpoint**: The layering claimed in the README is now checked rather than asserted.

---

## Phase 6: User Story 4 - Complexity ceilings (Priority: P3)

**Goal**: A method whose branching exceeds the agreed ceiling fails the standard build, naming the
method and its measured complexity.

**Independent Test**: Add a deliberately convoluted method, run the standard build, observe the
failure naming the method and its measured value, then revert.

- [x] T022 [US4] Add the four metric checks to `config/checkstyle/checkstyle.xml` inside the existing `TreeWalker`: `CyclomaticComplexity` max 10, `NPathComplexity` max 30, `MethodLength` max 70, `ParameterNumber` max 7 — **no** `SuppressionFilter`, no baseline file, no `// CHECKSTYLE:OFF` (FR-017, FR-018, data-model §6)
- [x] T023 [US4] Verify the ceilings pass on the unmodified tree: run `./gradlew checkstyleMain checkstyleTest --console=plain` and confirm zero findings — if any check fires, the ceiling is wrong and must be re-measured rather than suppressed (FR-018, SC-009)
- [x] T024 [US4] Verify enforcement: temporarily lower the `CyclomaticComplexity` ceiling below a known method's measured value, run `./gradlew checkstyleMain --console=plain`, confirm a nonzero exit naming the method, its measured value, and the ceiling, then restore the correct ceiling (SC-007)

**Checkpoint**: Complexity is bounded by a gate that the tree satisfies honestly, with no exceptions.

---

## Phase 7: User Story 5 - Mutation analysis off the critical path (Priority: P3)

**Goal**: A mutation score can be produced per package on demand, without the standard build
performing mutation analysis or taking any longer.

**Independent Test**: Run the on-demand analysis and make sure that it reports a score per package. Confirm
the standard build does not run it.

- [x] T025 [US5] Finalise the `pitest { }` block in `build.gradle.kts`: `junit5PluginVersion = "1.2.3"`, `targetClasses`/`targetTests` scoped to `com.minidoodle.calendar.domain.*`, `outputFormats` HTML+XML+CSV, and `timestampedReports = false` for stable paths (FR-019, FR-021, research D9)
- [x] T026 [US5] Verify the split: run `./gradlew pitest --console=plain` and confirm a per-package and overall score is produced under `build/reports/pitest/`. Then run `./gradlew check --dry-run --console=plain` and confirm `pitest` does **not** appear in the graph (FR-019, FR-020, SC-008)

**Checkpoint**: Test *strength* is measurable, and the standard build is measurably unaffected.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, final verification, and the landing commit.

- [x] T027 [P] Update `README.md`: the gate inventory (what runs in `./gradlew build`, what is on
      demand), the floor table, the complexity ceilings, and the run/fix commands (FR-022)
- [x] T028 [P] Update `AGENTS.md`: the contributor rule that all fast gates ride `./gradlew build`
      (no new hooks), that mutation analysis is on demand, that floors only ratchet upward, and that
      there is no suppression file (FR-022)
- [x] T029 Run `./gradlew spotlessApply`, then the full CI command `./gradlew build --no-daemon
      --console=plain`, and confirm `BUILD SUCCESSFUL` with every gate active (SC-009, SC-010)
- [x] T030 Confirm the enforcement points are untouched: diff `.githooks/pre-push` and
      `.github/workflows/ci.yml` against the feature-005 state and confirm **no change** (FR-005,
      FR-023)
- [x] T031 Verify the documented quickstart is accurate by executing the commands in
      `specs/006-quality-gates/quickstart.md`, including the deliberate-failure checks (FR-022)
- [ ] T032 **Commit** the feature as one or more conventional commits (suggested: the gap-closing
      tests and the retry seam separately from the gate configuration) — **AWAIT explicit user
      approval before committing**, and do not push without separate approval (AGENTS.md)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately. Adds tools only. The build continues to pass.
- **Foundational (Phase 2)**: Depends on Setup (T003 provides the shared exclusion). Blocks US1.
- **US1 (Phase 3)**: Depends on Phase 2. Bundle floors are safe immediately.
- **US2 (Phase 4)**: Depends on Phase 2 for T016 only. T010–T015 are independent of Phase 3 and can run
  in parallel with it. **T016 depends on T012–T015** (floors must not precede the tests).
- **US3 (Phase 5)**: Depends on Setup (T001 provides ArchUnit). Independent of US1/US2/US4/US5.
- **US4 (Phase 6)**: Depends on nothing beyond the existing Checkstyle configuration. Fully independent.
- **US5 (Phase 7)**: Depends on Setup (T002). Fully independent.
- **Polish (Phase 8)**: Depends on all of the above.

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories. The MVP.
- **US2 (P1)**: Independent of US1 except for the shared build file — T007 and T016 both edit
  `jacocoTestCoverageVerification`'s `violationRules`, so they are **sequential, not parallel**.
- **US3, US4, US5 (P2/P3)**: Mutually independent. Each is independently testable.

### Parallel Opportunities

- **Phase 1**: T001 and T002 both edit `build.gradle.kts` (sequential). T003 edits the same file
  (sequential). Only T002's plugin id is `[P]`-labelled for its *content* being independent, not for
  concurrent file access.
- **Phase 4**: T010, T013, T014, and T015 touch four different files and are `[P]`. T011/T012 are a
  pair (T012 depends on T011).
- **Phase 5**: T018 (fixtures) is `[P]` with respect to nothing else in the phase. T019 and T020 are
  sequential (T020 asserts T019's rules).
- **Phases 5, 6, 7**: Independent of each other and of Phases 3–4 apart from `build.gradle.kts`
  contention (Phase 5 needs no build-file edit beyond T001, Phase 6 edits the Checkstyle config,
  Phase 7 edits the build file).

### File-contention note

`build.gradle.kts` is edited by T001, T002, T003, T005, T007, T016, and T025. Those tasks are
**sequential** with respect to that file even when their stories are independent.

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phases 1–2 (tools + wiring, build passes).
2. Complete Phase 3 (bundle floors + failure demonstration).
3. **STOP and VALIDATE**: raising a floor above the measured value fails `./gradlew build`. The
   unmodified tree passes.
4. That alone delivers the core ask — coverage is now a guarantee rather than a report.

### Incremental Delivery

1. Phases 1–3 → enforced coverage floors (MVP).
2. Phase 4 → the floors become meaningful, because the error/recovery gaps they exposed are closed
   and directly enforced per class.
3. Phase 5 → layering enforced.
4. Phase 6 → complexity bounded.
5. Phase 7 → test strength measurable on demand.
6. Phase 8 → documented, verified, committed.

### Notes

- Every task is specific and names its file so an LLM can execute it without extra context.
- Stop at any checkpoint to validate a story independently.
- Avoid: enabling class-scoped floors before the gap-closing tests, a `SuppressionFilter` or baseline
  file, wiring `pitest` into `check`, or any new pre-push/CI step.
