# Contract: Multi-Dimensional Quality Gates

**Feature**: 006-quality-gates | **Date**: 2026-10-17

There is no HTTP API in this feature. The observable contract is the **build/command surface** that
contributors, the pre-push hook, and CI rely on. Anything consuming this feature (a developer, the
pre-push hook, a CI job, an IDE) depends only on the commands, exit behavior, and report locations
described here.

Every gate must be reachable from the one command the enforcement points already run:
`./gradlew build --no-daemon --console=plain`.

---

## 1. Commands and expected behavior

| Command | Contract |
|---|---|
| `./gradlew build` | Runs **every fast gate**: style (005), ArchUnit conformance, Checkstyle complexity ceilings, and coverage verification. Fails (nonzero exit) if any of them fails. **Does not** run mutation analysis. |
| `./gradlew check` | The same gate set without packaging. |
| `./gradlew jacocoTestReport` | Writes the coverage report. Never fails the build on coverage values — it only reports. |
| `./gradlew jacocoTestCoverageVerification` | Fails if any dimension or class floor is violated. Names the rule, the measured value, and the minimum. Passes on a tree at or above the floors. |
| `./gradlew checkstyleMain` / `checkstyleTest` | Now also enforces the four complexity ceilings. A violation message carries the measured value and the ceiling. |
| `./gradlew test` | Runs the suite **including** the ArchUnit conformance tests and the rule-effectiveness tests. |
| `./gradlew pitest` | Runs mutation analysis for the domain package **on demand**. Not part of `build`, `check`, or `test`. |

## 2. Exit / result semantics

- **Success**: exit 0. No coverage rule violated. No complexity ceiling exceeded. Every architecture
  rule satisfied.
- **Failure**: nonzero exit, with the affected rule and its measured value named in the output.
  A finding is always a build failure, never a silent warning.
- **Generated sources**: package `com.minidoodle.jooq` never contributes to a coverage ratio, in
  either the report **or** the verification (FR-004). This must hold for both tasks independently —
  they own separate `classDirectories` collections.
- **Mutation analysis is never part of the standard build's result**: `./gradlew build` must not
  start PIT and its duration must be unaffected (FR-020).

## 3. Failure message contract

| Gate | Message must contain |
|---|---|
| Coverage (bundle) | the counter (for example `LINE`), the measured ratio, and the minimum |
| Coverage (class-scoped) | the target class, the counter, the measured ratio, and the minimum |
| Complexity | the check name, the measured value, and the ceiling (Checkstyle's standard format: `path:line:col: message [RuleName]`) |
| Architecture | the rule id/description and the affected type (FR-015) |

## 4. Report locations

| Artifact | Path |
|---|---|
| Coverage — browsable | `build/reports/jacoco/test/html/index.html` |
| Coverage — machine-readable | `build/reports/jacoco/test/jacocoTestReport.xml` |
| Checkstyle | `build/reports/checkstyle/main.html` / `main.xml` (and `test.*`) |
| ArchUnit (on failure) | the JUnit test failure text, naming rule and affected type |
| Mutation — browsable | `build/reports/pitest/index.html` |
| Mutation — per package | `build/reports/pitest/<package>/index.html` |
| Mutation — machine-readable | `build/reports/pitest/mutations.xml` |

## 5. Floors (the enforced contract)

Bundle scope, evaluated over production classes with generated code excluded. Each floor is the
measured ratio rounded down to the nearest 0.05 (see `research.md` D2 and its as-built addendum):

| Counter | Floor | As built |
|---|---|---|
| `LINE` | 0.95 | 99.16% |
| `BRANCH` | 0.95 | 97.22% |
| `METHOD` | 0.95 | 99.19% |
| `COMPLEXITY` | 0.95 | 97.92% |

Class scope (the error-path dimension):

| Class | Counter | Floor | As built |
|---|---|---|---|
| `GlobalExceptionHandler` | `LINE` | 1.00 | 100% |
| `GlobalExceptionHandler` | `METHOD` | 1.00 | 100% |
| `JooqSlotRepository` | `LINE` | 0.95 | 98.94% |
| `JooqSlotRepository` | `BRANCH` | 0.90 | 91.67% |
| `JooqMeetingRepository` | `LINE` | 0.95 | 98.82% |
| `JooqMeetingRepository` | `BRANCH` | 0.90 | 90.00% |
| `CurrentUserArgumentResolver` | `BRANCH` | 1.00 | 100% |

Complexity ceilings (Checkstyle, `severity=error`, no suppression file):

| Check | Ceiling |
|---|---|
| `CyclomaticComplexity` | 10 |
| `NPathComplexity` | 30 |
| `MethodLength` | 70 |
| `ParameterNumber` | 7 |

## 6. Integration points (must remain true)

- `.githooks/pre-push` runs `./gradlew build --no-daemon --console=plain`. Every fast gate is
  enforced by that existing command, with **no change** to the hook logic (FR-005).
- `.github/workflows/ci.yml` runs `./gradlew build --no-daemon --console=plain`. Likewise unchanged.
- `.github/workflows/ci.yml` may gain an **optional, non-blocking** mutation job. If it is added it
  must not gate the merge (FR-020).

## 7. Configuration surface (where things live)

| Concern | Location |
|---|---|
| Coverage tool + report task | `build.gradle.kts` → `jacoco { toolVersion = "0.8.15" }`, `jacocoTestReport` |
| Coverage floors | `build.gradle.kts` → `jacocoTestCoverageVerification { violationRules { ... } }` |
| Verification wiring | `build.gradle.kts` → `tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }` |
| Architecture rules | `src/test/java/.../ArchitectureTest.java` + rule-effectiveness test |
| Complexity ceilings | `config/checkstyle/checkstyle.xml` (added to the existing selected ruleset) |
| Mutation tool + scope | `build.gradle.kts` → `plugins { id("info.solidsoft.pitest") version "1.19.0" }`, `pitest { ... }` |
| Generated-source exclusion | `build.gradle.kts` (applied to report **and** verification) |

## 8. Non-goals (explicit)

- No mutation threshold that fails the build. The score is for triage.
- No `SuppressionFilter`, no Checkstyle baseline file, no `// CHECKSTYLE:OFF`.
- No exclusions beyond machine-generated jOOQ code. `DevDataSeeder` is covered rather than excluded.
- No dependency vulnerability scanning, dependency locking, or container scanning.
- No change to the HTTP API or to any documented response.
