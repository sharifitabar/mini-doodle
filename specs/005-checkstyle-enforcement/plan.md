# Implementation Plan: Code Style Enforcement (Checkstyle + Spotless)

**Branch**: `005-checkstyle-enforcement` | **Date**: 2026-10-17 | **Spec**: `specs/005-checkstyle-enforcement/spec.md`

**Input**: Feature specification from `/specs/005-checkstyle-enforcement/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Add a curated **Checkstyle** lint gate and a deterministic **Spotless + google-java-format (AOSP)**
formatter to the single Gradle module, and wire both into the standard `build` lifecycle so the
existing pre-push hook and CI enforce them with **no new hook or CI wiring**. Responsibility is
split: Spotless owns formatting (indentation, line wrapping/length, whitespace, import order, and
removing unused imports); Checkstyle owns non-formatting rules (naming, brace/block and bug-pattern
conventions, redundant imports). Generated jOOQ sources are excluded from both tools. The one-time
codebase-wide reformat lands as an isolated, blame-ignored commit. Versions are pinned:
Spotless `8.10.4`, google-java-format `1.36.0`, Checkstyle `14.3.0` (Gradle's built-in `checkstyle`
plugin). The codebase audit found the curated lint set is effectively clean today (no underscore
test methods, no star imports, no `System.out`, no braceless control statements, no empty catches);
the only mechanical changes are the formatter's own reformat plus two unused imports and trailing
whitespace.

## Technical Context

**Language/Version**: Java 21 (LTS); Gradle 8.14.3 (Kotlin DSL)

**Primary Dependencies**: `com.diffplug.spotless` Gradle plugin `8.10.4`; google-java-format `1.36.0`; Checkstyle `14.3.0` via Gradle's built-in `checkstyle` plugin (no new runtime dependency)

**Storage**: N/A (build-tooling feature; no persisted data)

**Testing**: The gate is verified by (a) `./gradlew spotlessCheck checkstyleMain checkstyleTest` passing on the cleaned tree; (b) `./gradlew spotlessApply` being idempotent (no diff on a second run); (c) a manual negative demonstration (introduce a violation → `./gradlew build` fails, naming file/line/rule). The existing JUnit/Testcontainers/MockMvc suites are unchanged and must stay green.

**Target Platform**: Contributor machines (macOS/Linux, JDK 21) and CI (`ubuntu-latest`, `./gradlew build`)

**Project Type**: build quality tooling within a single Gradle module

**Performance Goals**: `spotlessCheck` + `checkstyleMain`/`checkstyleTest` add only a few seconds to the build; both are incremental/cacheable and must not materially change build wall-time.

**Constraints**: No runtime behaviour change and no new runtime dependency; generated jOOQ sources excluded from both tools; enforcement via the standard build only (existing pre-push hook + CI, no new hooks); all pre-existing violations fixed with no baseline/suppression file; the initial reformat is an isolated commit recorded in `.git-blame-ignore-revs`.

**Scale/Scope**: ~86 production + 34 test Java sources (~6,100 LOC); one-time reformat of most files (104 use 4-space indent); a curated set of 29 non-formatting Checkstyle checks (all under `TreeWalker`). Out of scope: formatting of non-Java files (`.kts`, `.yml`, `.md`), pre-commit hooks, ratcheting, and any API/domain change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Domain-Centric Hexagonal Architecture | No domain/application/adapter code changes; the feature only adds build configuration and reformats existing sources without altering behaviour or dependencies-inward rules. Spotless/Checkstyle operate on all sources but introduce no framework types into the domain. | PASS |
| II. Database as Source of Truth | No schema or migration change. | PASS |
| III. Test Discipline with Real Dependencies | No change to the test strategy; the existing Testcontainers/MockMvc suites remain the gate. The style gate itself is verified by running it (pass on clean tree, fail on a deliberate violation). | PASS |
| IV. Simplicity & YAGNI at Honest Scale | Two standard, well-known build plugins with minimal pinned configuration, no new runtime dependency, no new services. The new build-time dependencies are justified by an explicit requirement (force a style gate) and review-quality value; the split of duties avoids two tools policing the same concern. | PASS |
| V. Observable & Documented Service | Documentation is updated (README and/or contributor guidance) with how to run the checks and the fix command; no runtime observability impact. | PASS |

No violations. `Complexity Tracking` is intentionally empty. Adding build tooling does not alter the
constitution's locked technology stack (it is a build-quality concern, not a runtime stack item), so
no amendment is required.

## Project Structure

### Documentation (this feature)

```text
specs/005-checkstyle-enforcement/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── quality-gate.md  # Phase 1 output: observable build/command contract
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

New and changed files (`+` = new, `~` = changed). The application/domain tree from
`specs/001-time-slot-management/plan.md` is unchanged; this feature only touches build files,
one config directory, docs, and reformatted sources:

```text
.
├── build.gradle.kts                     (~) apply `checkstyle` + `com.diffplug.spotless`
│                                            plugins; pin tool versions; configure the Java
│                                            format block (AOSP) and the checkstyle tasks;
│                                            exclude generated jOOQ sources from both
├── config/
│   └── checkstyle/
│       └── checkstyle.xml               (+) curated non-formatting ruleset (severity=error)
├── .git-blame-ignore-revs               (+) hash of the isolated reformat commit
├── .githooks/pre-push                   (~) unchanged build command; style runs within it
│                                            (only a comment/README note if useful)
├── README.md                            (~) document the gate: run checks, fix command,
│                                            ruleset/format locations, blame-ignore setup
├── AGENTS.md                            (~) contributor note: style gate + fix command
└── src/
    ├── main/java/**                     (~) reformatted (AOSP) in the isolated style commit
    └── test/java/**                     (~) reformatted (AOSP) in the isolated style commit
```

**Structure Decision**: Keep the existing single Gradle module and add build-quality tooling only.
The ruleset lives at Gradle's conventional `config/checkstyle/checkstyle.xml`; the formatter is
configured inline in `build.gradle.kts`. Generated jOOQ sources (`build/generated-sources/jooq/**`)
are excluded by setting Spotless's Java target explicitly to `src/main/java` + `src/test/java` and by
excluding the generated package (`**/com/minidoodle/jooq/**`) from the Checkstyle tasks. No
application structure changes.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations were identified; this section is intentionally empty.

## Phase 0 / Phase 1 Artifacts

- [research.md](./research.md) — resolved technical decisions (tool/version/ownership choices, JDK-21 handling, generated-source exclusion) and rejected alternatives.
- [data-model.md](./data-model.md) — the build/config "entities": ruleset structure, formatter configuration, exclusions, and the ignore-revisions record.
- [contracts/quality-gate.md](./contracts/quality-gate.md) — the observable build/command contract (tasks, commands, exit behaviour, report locations).
- [quickstart.md](./quickstart.md) — how to run the checks, auto-fix, and verify locally.

## Post-Design Constitution Check

Re-evaluated after Phase 1 design: no domain/application/adapter changes (I); no schema change
(II); tests and the style gate are verified by running them (III); two standard plugins with pinned
versions, no new runtime dependency, and a single-owner split of duties (IV); README/AGENTS updated
(V). No new violations. Gate: PASS.

## Open Items

- None blocking. The exact curated Checkstyle rule list is recorded in
  [data-model.md](./data-model.md) and [contracts/quality-gate.md](./contracts/quality-gate.md) and
  was validated against the current tree (no underscore test methods, no star imports, no
  `System.out`, no braceless control statements). The two intentional `catch (Throwable)` blocks in
  the concurrency tests are the reason `IllegalCatch` is deliberately **not** in the curated set.
- The agent-context update step is a no-op here: this repository has no
  `update-agent-context.sh` under `.specify/scripts/bash/` and manages `AGENTS.md` manually.
