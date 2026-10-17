# Implementation Plan: Multi-Dimensional Quality Gates

**Branch**: `006-quality-gates` | **Date**: 2026-10-17 | **Spec**: `specs/006-quality-gates/spec.md`

**Input**: Feature specification from `/specs/006-quality-gates/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Turn the existing *report-only* coverage measurement into enforced, multi-dimensional quality gates,
and close the specific gaps that make those gates honest.

Five gates, deliberately split by cost:

1. **Coverage floors** — `jacocoTestCoverageVerification` wired into `check` with bundle-level floors
   on **line, branch, method, and cyclomatic complexity** (instruction and class stay measured-only),
   plus **class-scoped** rules on the three error/recovery types. Floors sit measurably below the
   measured baseline so the tree is green on day one.
2. **Error/recovery coverage** — eight tests that close the audit's named gaps: five untriggered
   `@ExceptionHandler` mappings, the slot write retry/backoff/give-up path (through an injectable
   seam, not a flaky real deadlock), blank/malformed caller-header branches, and the
   property-gated `DevDataSeeder`.
3. **Architecture conformance** — ArchUnit `1.4.1` rules as ordinary JUnit 5 tests (domain is
   framework-free; application must not reach into adapters; adapters must not cross; shared is a
   leaf; no package cycles within the context), each with a negative-control fixture proving it is
   not vacuous.
4. **Complexity ceilings** — four Checkstyle metric checks added to the existing curated ruleset
   (Cyclomatic 10, NPath 30, MethodLength 70, ParameterNumber 7), satisfied today with **no
   suppression and no baseline file**.
5. **Mutation analysis** — PIT via `info.solidsoft.pitest` `1.19.0`, **on demand only**, scoped to
   the domain package, with no threshold; never reachable from `build`.

One production-code change is required and is the only scope exception: deleting `TimeRange.overlaps`
(zero call sites — dead code) rather than writing a test whose only purpose is to move a number.

See [research.md](./research.md) for the measurements behind every number, and
[contracts/quality-gate.md](./contracts/quality-gate.md) for the observable build contract.

## Technical Context

**Language/Version**: Java 21 (LTS); Gradle 8.14.3 (Kotlin DSL)

**Primary Dependencies**: JaCoCo `0.8.15` (already applied, report-only today; verification task
already provided by the Gradle plugin — only enabled and wired); ArchUnit `com.tngtech.archunit:archunit-junit5:1.4.1` (new, test scope; 1.5.x breaks the Gradle test worker — see research D4); `info.solidsoft.pitest` Gradle plugin `1.19.0` (new, build-time only) with `junit5PluginVersion = "1.2.3"` (bundles PIT `1.22.1`); Checkstyle `14.3.0` (already pinned; four metric checks added to the existing ruleset). No new runtime dependency.

**Storage**: N/A (build-quality feature; no persisted data). The gates read build outputs
(`build/reports/jacoco/**`, compiled classes) and the existing Testcontainers PostgreSQL for the
gap-closing integration tests.

**Testing**: The gates are verified by running them and by making them fail on purpose:
(a) `./gradlew build --no-daemon --console=plain` green on the unmodified tree;
(b) `./gradlew jacocoTestCoverageVerification` fails when a floor is raised above the measured value;
(c) each architecture rule is proven to fail by a negative-control fixture (FR-016);
(d) a lowered complexity ceiling fails and names the method and measured value;
(e) `./gradlew pitest` produces a score per package without being reachable from `build`.
The existing JUnit/Testcontainers/MockMvc suites remain the substantive gate and must stay green.

**Target Platform**: Contributor machines (macOS/Linux, JDK 21, Docker for Testcontainers) and CI
(`ubuntu-latest`, `./gradlew build`)

**Project Type**: build quality tooling within a single Gradle module (no new module)

**Performance Goals**: the added fast gates must not materially change `./gradlew build` wall-time.
Coverage verification adds analysis of existing class files (sub-second); the Checkstyle metric
checks are AST-only and ride existing tasks; the ArchUnit rules are a handful of tests over an
already-loaded classpath. Mutation analysis is explicitly outside the build and its duration is
unconstrained (it *is* expected to be slow, which is why it is on demand).

**Constraints**: no runtime behaviour change and no new runtime dependency; generated jOOQ sources
excluded from **both** the report and the verification (they own separate `classDirectories`);
enforcement through the existing standard build only (the pre-push hook and CI are untouched); no
`SuppressionFilter`, no Checkstyle baseline file, no `// CHECKSTYLE:OFF`; the complexity ceilings must
be satisfied by the current tree as-is; the gates must not weaken feature 005's style gate; mutation
analysis must not be reachable from `check`/`build`.

**Scale/Scope**: ~86 production + 34 test Java sources; 4 counters given bundle floors, 3 classes
given class-scoped floors, 4 architecture rules, 4 complexity ceilings, 8 gap-closing tests, 1
production deletion (7 lines). Out of scope: bug-pattern analysis (SpotBugs), dependency
vulnerability scanning, dependency locking, container/image scanning, mutation thresholds, and any
change to the HTTP API.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Domain-Centric Hexagonal Architecture | The feature *enforces* this principle rather than bending it: rule R1 forbids framework types in the domain, R2 keeps the domain closed, R3 forbids application→adapter dependencies, R4 forbids adapter cross-dependencies, R5 keeps shared a leaf, R6 forbids package cycles within the context. Measured today: all six pass. No domain/application/adapter code is added except the extracted retry collaborator (adapter-out, behaviour-preserving) and the `TimeRange.overlaps` deletion (dead domain code). | PASS |
| II. Database as Source of Truth | No schema or migration change. The retry/backoff logic under test is *existing* defence-in-depth around the PostgreSQL exclusion constraint; the seam extraction preserves its behaviour exactly (retry on `40P01`/`40001`, map `23P01` to a domain overlap error, bounded attempts). | PASS |
| III. Test Discipline with Real Dependencies | This is the principle the feature serves, and it is why two seams are used instead of pretending otherwise: the architecture rule-effectiveness fixtures and the retry seam are unit-level, while the error-response and seeder tests use the real MockMvc pipeline and real PostgreSQL. The spec's "no flaky tests" rule is exactly why a real deadlock is *not* provoked. | PASS |
| IV. Simplicity & YAGNI at Honest Scale | No new module, no new runtime dependency, and the four complexity checks reuse the Checkstyle configuration that already exists. Removing `TimeRange.overlaps` is YAGNI applied. Two new build-time/test dependencies (ArchUnit, PITest) are each justified by an explicit requirement that no existing tool satisfies (coverage cannot express directionality; JaCoCo cannot express test *strength*). | PASS |
| V. Observable & Documented Service | README and AGENTS.md gain the gate inventory, the run/fix commands, and the floor table; each gate's failure message names the rule and value so it is actionable without reading source. No runtime observability change. | PASS |

No violations. `Complexity Tracking` is intentionally empty. Adding test-scope and build-time
tooling does not alter the constitution's locked technology stack (a build-quality concern, not a
runtime stack item), so no amendment is required.

## Project Structure

### Documentation (this feature)

```text
specs/006-quality-gates/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── quality-gate.md  # Phase 1 output: observable build/command contract
├── checklists/
│   └── requirements.md  # Specify-phase validation artifact
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

New and changed files (`+` = new, `~` = changed, `-` = deleted). The application/domain tree from
`specs/001-time-slot-management/plan.md` is otherwise unchanged; this feature touches build
configuration, one Checkstyle ruleset, one production adapter (behaviour-preserving extraction), one
dead domain method (deleted), new tests, and docs:

```text
.
├── build.gradle.kts                      (~) add the pitest plugin + `pitest { }` block (on-demand,
│                                             domain-scoped); add `violationRules` to
│                                             `jacocoTestCoverageVerification`; wire that task into
│                                             `check`; factor the jOOQ-excluded class directories into
│                                             one shared value used by report AND verification
├── config/
│   └── checkstyle/
│       └── checkstyle.xml                (~) add CyclomaticComplexity, NPathComplexity,
│                                             MethodLength, ParameterNumber to the curated ruleset
├── README.md                             (~) document the gate inventory, run/fix commands, floors
├── AGENTS.md                             (~) contributor note: what runs in `build`, what is on demand
└── src/
    ├── main/java/com/minidoodle/
    │   ├── calendar/domain/model/
    │   │   └── TimeRange.java            (-) remove `overlaps(...)`: zero call sites
    │   └── calendar/adapter/out/persistence/
    │       ├── SlotWriteRetry.java       (+) extracted retry/backoff collaborator with an injectable
    │       │                                 sleep strategy: bounded retries on transient SQLStates,
    │       │                                 exclusion violation → SlotOverlapException, interrupt →
    │       │                                 IllegalStateException, behaviour identical
    │       └── JooqSlotRepository.java   (~) `execute` delegates to SlotWriteRetry
    └── test/java/com/minidoodle/
        ├── architecture/
        │   ├── ArchitectureTest.java             (+) rules R1–R6 over production classes
        │   └── ArchitectureRuleEffectivenessTest.java (+) each rule must fail on its fixture
        ├── architecture/fixture/                  (+) one violating fixture per rule (test scope)
        ├── calendar/adapter/out/persistence/
        │   └── SlotWriteRetryTest.java            (+) retry/backoff/give-up/interrupt cases, no container
        ├── calendar/adapter/in/web/
        │   ├── ErrorResponseContractIT.java       (+) the 5 untriggered handlers, end-to-end
        │   └── MissingHeaderAdviceTest.java       (+) test-only controller + real advice (standalone)
        └── shared/config/
            └── DevDataSeederIT.java               (+) property-enabled, idempotent seeding
```

**Structure Decision**: Keep the existing single Gradle module and add build-quality tooling only.
Coverage floors live inline in `build.gradle.kts` (there is no external coverage-config file to
create); the complexity ceilings belong in the existing `config/checkstyle/checkstyle.xml` rather
than a second ruleset, because they are Checkstyle rules and must run in the tasks already wired into
`check`; the architecture rules are ordinary test classes under a new `architecture` test package.
The jOOQ exclusion is a single shared filtered `classDirectories` value applied to both
`jacocoTestReport` and `jacocoTestCoverageVerification`. No application structure changes.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations were identified; this section is intentionally empty.

## Phase 0 / Phase 1 Artifacts

- [research.md](./research.md) — measured baseline, the ten decisions (D1–D10) with rationale and
  rejected alternatives, resolved unknowns, and how each measurement was taken.
- [data-model.md](./data-model.md) — the gate "entities": coverage dimensions and floors, violation
  rules, gap-closing tests, architecture rules and negative controls, complexity ceilings, mutation
  scope, and the exclusion table that replaces a suppression file.
- [contracts/quality-gate.md](./contracts/quality-gate.md) — the observable build/command contract
  (commands, exit semantics, failure-message contents, report locations, floors).
- [quickstart.md](./quickstart.md) — how to run each gate, read its output, and fix a failure,
  plus how to prove each gate actually bites.

## Post-Design Constitution Check

Re-evaluated after Phase 1 design: the feature enforces principle I rather than bending it (R1–R6
pass today); no schema change and the retry extraction is behaviour-preserving (II); the test
strategy is strengthened with real dependencies where reachable and explicit seams where not, with
no flaky triggers (III); no new module, no runtime dependency, ceilings reuse existing Checkstyle
configuration (IV); README/AGENTS updated and failures are self-describing (V). No new violations.
Gate: PASS.

## Open Items

- **`TimeRange.overlaps` deletion needs explicit sign-off.** It is dead code (zero call sites in
  `src/main` and `src/test`), and deleting it is the only production change in this feature. The
  alternative — a test for unreachable code — is the anti-pattern the spec forbids. If the method is
  intended as public domain API, it should instead be exercised by a test and the deletion dropped.
- **`MissingRequestHeaderException` has no production trigger** (there is no `@RequestHeader`
  anywhere in the application). FR-007 is satisfied for it by a test-only binding test against the
  real advice. If a future endpoint declares a required header, that test should be replaced by an
  end-to-end one.
- **Class-scoped floors are set to post-gap values.** The `1.00` floors on `GlobalExceptionHandler`
  are only reachable once the five gap-closing tests land; the ordering therefore matters — tests
  first, then floors. Implementation must not enable the floors ahead of the tests, or the build
  goes red mid-feature.
- **`ClassFanOutComplexity` is excluded, not solved.** It would fail today at 40 vs a default of 20
  (`SlotController`). The underlying coupling is real; the ArchUnit rules constrain its *direction*
  but do not reduce it. Reducing `SlotController`'s fan-out is a legitimate follow-up, out of scope
  here.
- **Mutation score is unmeasured.** No threshold is set because equivalent mutants will depress the
  score, and there is no evidence yet about its stability. The first `./gradlew pitest` run should
  inform whether a floor is ever worth adding.
- The agent-context update step is a no-op here: this repository has no
  `update-agent-context.sh` under `.specify/scripts/bash/` and manages `AGENTS.md` manually (as
  recorded in the feature 005 plan).
