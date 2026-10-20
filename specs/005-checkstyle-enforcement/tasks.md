---

description: "Task list for feature 005-checkstyle-enforcement"
---

# Tasks: Code Style Enforcement (Checkstyle + Spotless)

**Input**: Design documents from `/specs/005-checkstyle-enforcement/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/quality-gate.md, quickstart.md

**Tests**: No JUnit/TestKit test tasks are generated — the spec's Independent Tests and Success
Criteria (SC-001, SC-002) are satisfied by *running the gate and demonstrating it fails on a
deliberate violation*, which is exactly what a style gate's own verification is. Automated TestKit
tests for the gate would be YAGNI (constitution IV). The demonstration tasks (T008, T009) are
thus mandatory, not optional.

**Organization**: Tasks are grouped by user story so each story can be implemented and verified
independently. US1 (local pre-push gate) is the MVP. US2 rides existing wiring. US3 is documentation
and history hygiene.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (for example, US1, US2, US3)
- Include exact file paths in descriptions

---

## ⚠️ CRITICAL SEQUENCING NOTE (read before executing)

FR-012 requires the reformat to be an **isolated commit separate from the tooling/config change**,
but FR-011 requires the gate to be **green when the feature lands**. These are reconciled by
ordering the work like this — and this ordering is why the tasks below are not strictly
"config first, commit immediately":

1. Add the plugins + config to the **working tree** (Phase 1, T001–T004) — **do not commit yet.**
2. Run `spotlessApply` so the tree conforms (Phase 2, T005).
3. **Commit only the `src/` reformat** (T010) → this is the isolated, blame-ignored commit.
4. **Then commit the tooling + documentation** (T020) → its diff is config-only, and the gate it adds is
   already satisfied by step 2, so the build passes at that point.
5. Push only after step 4, so the build never fails at push time (AGENTS.md pre-push rule).

Between steps 1 and 3 the build **intentionally fails** (unformatted code + a new gate). That is
local-only: **do not commit or push in that window.**

**Approval**: Per `AGENTS.md`, T010 and T020 create commits and thus **must stop and await
explicit user approval.** Do not commit or push without it.

---

## Phase 1: Foundational (Shared Tooling Configuration)

**Purpose**: Pin the tool versions, apply the plugins, write the selected ruleset, and configure the
formatter plus both generated-source exclusions. Blocks every user story.

- [X] T001 Add the `com.diffplug.spotless` plugin (version `8.10.4`) and the built-in `checkstyle` plugin to `build.gradle.kts`. Pin Checkstyle `toolVersion = "14.3.0"` and google-java-format `"1.36.0"` in the Spotless step so results are reproducible (1.36.0 is the ceiling — see the version-ceiling decision in research.md) (FR-001, FR-004, FR-013)
- [X] T002 [P] Create the selected non-formatting ruleset at `config/checkstyle/checkstyle.xml`: 29 AST checks, all nested under `TreeWalker`, `Checker` carrying only `severity="error"` and `charset="UTF-8"`, exactly as specified in `data-model.md` §2 (FR-001, FR-004)
- [X] T003 Configure the Spotless `java` block in `build.gradle.kts`: explicit `target("src/main/java/**/*.java", "src/test/java/**/*.java")`, `googleJavaFormat("1.36.0").aosp()`, `removeUnusedImports()`, `importOrder("com.minidoodle", "java", "javax", "org", "com", "", "\\#")`, `trimTrailingWhitespace()`, `endWithNewline()` (FR-003, FR-004, FR-005)
- [X] T004 Configure the Checkstyle wiring in `build.gradle.kts`: `configFile = file("config/checkstyle/checkstyle.xml")`, `isIgnoreFailures = false`, `maxErrors = 0`, `maxWarnings = 0`, and `tasks.withType<Checkstyle>().configureEach { exclude("**/com/minidoodle/jooq/**") }` (FR-005, FR-007)

**Checkpoint**: Configuration is present but **uncommitted**. The build fails until Phase 2
reformats. Proceed directly to Phase 2.

---

## Phase 2: User Story 1 - Nonconforming code is blocked before a push (Priority: P1) 🎯 MVP

**Goal**: The existing `./gradlew build` — the exact command the pre-push hook runs — fails on any
style or formatting violation and passes on a clean tree.

**Independent Test**: Introduce a deliberate lint violation and an unformatted edit, run the build
command, and make sure that it fails naming file/line/rule and file respectively. Then fix/format and
confirm it passes.

- [X] T005 [US1] Run `./gradlew spotlessApply` to reformat all Java sources to AOSP and remove unused imports, then inspect the diff (`git diff --stat`) to confirm it is mechanical only — the reformat, the two unused imports (`Participant.java`, `MeetingBookingConcurrencyIT.java`), and trailing whitespace (FR-011, FR-012)
- [X] T006 [US1] Run `./gradlew spotlessCheck checkstyleMain checkstyleTest --console=plain` and confirm zero findings on the whole tree (FR-011, FR-002, SC-005)
- [X] T007 [US1] If any curated-lint finding survives that the formatter cannot fix (naming, brace, or bug-pattern rule), correct it by hand in the affected file under `src/main/java` or `src/test/java` and run again T006 — the audit predicts none, so treat any hit as new information (FR-011)
- [X] T008 [US1] Demonstrate enforcement: introduce a deliberate lint violation into a file under `src/main/java`, run `./gradlew checkstyleMain --console=plain`, confirm a nonzero exit naming file, line, and rule, then restore the file with `git checkout -- <file>` (SC-001, FR-007, FR-008)
- [X] T009 [US1] Demonstrate enforcement: introduce an unformatted edit into a file under `src/main/java`, run `./gradlew spotlessCheck --console=plain`, confirm a nonzero exit naming the file, then restore it with `git checkout -- <file>` (SC-002, FR-007, FR-009)
- [X] T010 [US1] **Commit ONLY the `src/` reformat** as an isolated conventional commit (for example `style: reformat Java sources with google-java-format AOSP`), leaving `build.gradle.kts`, `config/`, and documentation unstaged for now — **AWAIT user approval before committing**, then record the resulting SHA for `.git-blame-ignore-revs` (FR-012)

**Checkpoint**: US1 is independently verifiable — a violating working tree now fails the build, which
is what the pre-push hook and CI both run (SC-003).

---

## Phase 3: User Story 2 - The same gate runs in CI (Priority: P2)

**Goal**: CI enforces the identical gate with **no new wiring**, so a contributor who bypasses the
local hook is still caught.

**Independent Test**: Confirm the CI job and the pre-push hook run the same build command that now
carries the gate, and make sure that the task graph proves the gate tasks run inside `check`/`build`.

- [X] T011 [P] [US2] Confirm `.github/workflows/ci.yml` runs `./gradlew build --no-daemon --console=plain` and make **no change** to it — the gate rides the existing step (FR-006, SC-004)
- [X] T012 [P] [US2] Confirm `.githooks/pre-push` runs the same build command and that `git config core.hooksPath .githooks` is set, making **no change** to the hook logic (FR-006, SC-003)
- [X] T013 [US2] Record evidence that the gate is inside the standard build: run `./gradlew check --dry-run --console=plain` and confirm `checkstyleMain`, `checkstyleTest`, and `spotlessCheck` appear in the task graph (FR-006, FR-007)

**Checkpoint**: US1 and US2 both hold — the same gate blocks locally before push and in CI, from one
configuration (SC-007).

---

## Phase 4: User Story 3 - One shared, documented style contract (Priority: P3)

**Goal**: The ruleset and format live in version control with short documentation, so machines and IDEs agree,
a contributor can converge with one command, and blame stays readable after the reformat.

**Independent Test**: Use the repository documentation from a clean tree to run the check and the
fix command, and reproduce the same pass result using only committed configuration.

- [X] T014 [P] [US3] Create `.git-blame-ignore-revs` containing a comment header explaining the change and how to enable it, plus the reformat commit SHA from T010 (FR-012, SC-009)
- [X] T015 [P] [US3] Update `README.md`: how to run the checks (`./gradlew spotlessCheck checkstyleMain checkstyleTest`), the single fix command (`./gradlew spotlessApply`), where the ruleset (`config/checkstyle/checkstyle.xml`) and the format (`build.gradle.kts`) live, and the `git config blame.ignoreRevsFile .git-blame-ignore-revs` setup (FR-010, FR-014)
- [X] T016 [P] [US3] Add a short style-gate note to `AGENTS.md`: the gate runs as part of `./gradlew build`, and the fix command is `./gradlew spotlessApply` (FR-010, FR-014)
- [X] T017 [US3] Execute `specs/005-checkstyle-enforcement/quickstart.md` from a clean state and make sure that the documented commands reproduce the passing result in under 5 minutes (SC-008, FR-014)

**Checkpoint**: All three user stories are independently functional. The contract is documented and
history stays readable.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and the landing commit.

- [X] T018 Run the full gate exactly as the pre-push hook and CI do — `./gradlew build --no-daemon --console=plain` — and confirm `BUILD SUCCESSFUL` with zero style findings (SC-003, SC-004, SC-005)
- [X] T019 Run `./gradlew spotlessApply` a second time and confirm `git diff` is empty, proving the formatter is idempotent (SC-006)
- [X] T020 **Commit the tooling and documentation** (`build.gradle.kts`, `config/checkstyle/checkstyle.xml`, `.git-blame-ignore-revs`, `README.md`, `AGENTS.md`) as an isolated conventional commit (for example `build: enforce code style via Checkstyle and Spotless`) — **AWAIT user approval before committing**. Do not bundle the `src/` reformat into it (FR-012)
- [X] T021 Do **not** push until the user approves. When approved, confirm `./gradlew build --no-daemon --console=plain` passes locally immediately before pushing (AGENTS.md pre-push rule)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — starts immediately. Adds config to the working tree only. **Nothing committed here.**
- **User Story 1 (Phase 2)**: Depends on Phase 1. The MVP.
- **User Story 2 (Phase 3)**: Depends on Phase 1 (the gate must exist). Independent of Phase 2's commit ordering. It only *confirms* wiring. Can be done in parallel with the rest of Phase 2.
- **User Story 3 (Phase 4)**: Depends on T010 (needs the reformat SHA) for T014, and on Phase 1 for the documentation' content. T015–T017 can start once Phase 2 is verified.
- **Polish (Phase 5)**: Depends on all of the above.

### Commit ordering (must be followed exactly)

`Phase 1 config (uncommitted) → T005 reformat → T010 commit reformat → T020 commit tooling+docs → T021 push`.
The build fails only between the config landing and T005. It is never red at a commit that gets pushed.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 1. No dependencies on other stories.
- **US2 (P2)**: Starts after Phase 1. Shares configuration with US1 but is verifiable independently (read-only confirmation of CI/hook wiring).
- **US3 (P3)**: Starts after Phase 1. T014 needs T010's SHA. T015–T017 are independent of US2.

### Parallel Opportunities

- Within Phase 1: only T002 (writes `config/checkstyle/checkstyle.xml`, a different file from `build.gradle.kts`) is `[P]`. T003 and T004 both edit `build.gradle.kts` and are sequential.
- Within Phase 3: T011 and T012 inspect different files and are both `[P]`.
- Within Phase 4: T014, T015, and T016 write three different files (`.git-blame-ignore-revs`, `README.md`, `AGENTS.md`) and are all `[P]`.
- US2 and US3 can proceed concurrently with each other once their prerequisites are met.

---

## Parallel Example: User Story 3

```bash
# Launch all three documentation tasks together (different files):
Task T014: "Create .git-blame-ignore-revs with the reformat SHA"
Task T015: "Update README.md with run/fix/config locations"
Task T016: "Add style-gate note to AGENTS.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1 (config into the working tree, nothing committed).
2. Complete Phase 2 (reformat, verify, demonstrate, commit the reformat).
3. **STOP and VALIDATE**: a violating tree fails `./gradlew build`. A clean tree passes.
4. That alone delivers the explicit ask ("forced … at least before push") — the hook already runs that build.

### Incremental Delivery

1. Phase 1 + 2 → local pre-push gate is live (MVP).
2. Phase 3 → CI confirms the identical gate. Bypass is caught.
3. Phase 4 → documented contract + readable blame.
4. Phase 5 → final build that passes, idempotency proof, and the landing commit.

### Notes

- Every task is specific and names its file so an LLM can execute without extra context.
- Stop at any checkpoint to validate a story independently.
- Avoid: committing config and reformat together (breaks FR-012), adding a suppression/baseline file (breaks FR-011), or wiring new hooks/CI (breaks the "standard build only" decision).
