# Feature Specification: Code Style Enforcement (Checkstyle + Spotless)

**Feature Branch**: `005-checkstyle-enforcement`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "The checkstyle should be add to project and forrced on some phases, at least before push if possible, grill me"

## User Scenarios & Testing *(mandatory)*

<!--
  Users here are contributors and maintainers of the service. Each story is a
  self-contained slice of the developer/CI workflow that can be delivered and
  verified on its own.
-->

### User Story 1 - Non-conforming code is blocked before a push (Priority: P1)

A contributor writes or edits Java code that breaks the project's agreed style or
formatting rules. When they attempt to push, the push is rejected locally with a report
that names each offending file, line, and rule, so they fix the issues (or run the
one documented fix command) and retry — without consuming a reviewer's time or a CI run.

**Why this priority**: This is the explicit ask ("forced ... at least before push").
It delivers the core value — non-conforming code cannot reach the shared branch — and it
works even when a contributor forgets to run anything manually.

**Independent Test**: Introduce a deliberate style violation and an unformatted edit,
attempt to push, and confirm the push is blocked and the problem is reported with file,
line, and rule; then fix/format and confirm the push is allowed.

**Acceptance Scenarios**:

1. **Given** a working tree with a style violation, **When** the contributor pushes, **Then** the push is rejected and the violation is reported with file, line, and rule.
2. **Given** a working tree containing code that does not match the canonical format, **When** the contributor pushes, **Then** the push is rejected and the file(s) that need formatting are named.
3. **Given** a working tree that conforms to the ruleset and the canonical format, **When** the contributor pushes, **Then** the push proceeds.
4. **Given** a violation, **When** the check runs, **Then** the failure is a build failure (not a silent warning) whose offending location is identifiable without rerunning with extra flags.

---

### User Story 2 - The same gate runs in CI (Priority: P2)

A contributor bypasses local hooks (or a machine lacks them) and opens a pull request
containing a style or formatting problem. CI runs the identical check and fails the build,
so the rule is enforced at the point where changes are merged regardless of local setup.

**Why this priority**: Local hooks are advisory; a human can pass `--no-verify`, and the
hook is opt-in per clone. CI is the authoritative gate. It is P2 because the P1 local gate
already blocks the common path.

**Independent Test**: Create a branch with a style violation, push it past the local hook
(e.g., with the hook bypassed), and confirm the CI job that runs the build fails on the
style check and reports the problem.

**Acceptance Scenarios**:

1. **Given** a pull request whose changes include a style or formatting violation, **When** CI runs the build, **Then** the job fails and the problem is visible in the CI output.
2. **Given** a pull request with no style or formatting violations, **When** CI runs the build, **Then** the build is not blocked by the style gate.

---

### User Story 3 - One shared, documented style contract (Priority: P3)

A contributor (especially a new one) can find the project's style rules and canonical
format in version control, reproduce the exact same result locally as in CI, auto-fix what
is mechanically fixable, and know where to look when the check reports something it cannot
fix automatically.

**Why this priority**: Without a single versioned source of truth and short docs, "enforced"
drifts between machines and IDEs, and the gate becomes a nuisance rather than a shared
contract. It is P3 because it improves adoption rather than blocking bad code.

**Independent Test**: Follow the repository documentation on a fresh clone to run the style
check and the fix command, and produce the same pass/fail result as CI using only committed
configuration.

**Acceptance Scenarios**:

1. **Given** a fresh clone, **When** a contributor follows the documented steps, **Then** they can run the style check locally and get results consistent with CI.
2. **Given** code that violates only mechanically fixable rules, **When** the contributor runs the documented fix command, **Then** the code conforms and the check passes without manual editing.
3. **Given** two different machines (or an IDE and the command line), **When** both run the check on the same code, **Then** they agree on pass/fail because the rules live in the repository, not in personal settings.
4. **Given** the ruleset, **When** a contributor reads the docs, **Then** they can identify which rules apply and where the ruleset and canonical format are defined.

---

### Edge Cases

- Generated sources (the jOOQ classes produced during the build) must be excluded from **both** the lint check and the formatter, even though they live under the build output and are added to the compile sources.
- Test sources affect the gate exactly like production sources: a violation or unformatted test file must fail the build.
- The codebase adopted before these tools existed already violates some rules; the gate must be green on the initial adoption so it does not immediately block all work (an explicit, one-time cleanup is part of this feature).
- A contributor who deliberately bypasses the local hook (`--no-verify`) must still be caught by CI.
- Files must be evaluated consistently regardless of the contributor's operating system or line-ending settings, so a Windows checkout does not produce different results.
- The formatter and the linter must not contradict each other: a file that the formatter considers correct must never be reported by the linter for a formatting concern the formatter owns.
- The two tools must not require network access beyond normal dependency resolution already needed to build.
- A wholesale reformat (from adopting the formatter) must not obscure authorship in history — line-level blame should still resolve to the original author for untouched lines.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST keep a single, curated lint ruleset in version control that is applied identically on contributor machines and in CI. The ruleset prioritizes high-signal checks that the existing code can satisfy cheaply; it does not adopt a full published style as-is.
- **FR-002**: The lint check MUST cover both production and test Java sources.
- **FR-003**: The project MUST keep a canonical code format in version control that is deterministic (same input always yields the same output) and applied identically locally and in CI.
- **FR-004**: Responsibility MUST be divided so the formatter owns formatting (indentation, line wrapping/length, whitespace, import order, and removing unused imports) and the linter owns non-formatting rules (naming, brace/block and bug-pattern conventions, and other concerns the formatter cannot express). The two MUST NOT both enforce the same formatting concern.
- **FR-005**: The lint and format checks MUST automatically exclude generated sources (jOOQ output).
- **FR-006**: The lint and format checks MUST run as part of the standard build, so the existing CI build and the existing pre-push build both enforce them without relying on contributors to remember a separate command. No new hook or CI wiring is introduced for this.
- **FR-007**: A lint violation or a formatting mismatch MUST fail the build (a non-zero result), not merely emit a warning.
- **FR-008**: The lint failure report MUST identify each violation by file, line, and rule/check name, with a human-readable message.
- **FR-009**: The formatting check MUST name the file(s) that do not match the canonical format so the problem is actionable.
- **FR-010**: The project MUST document a single command that applies all mechanically fixable changes (formatting and import cleanups) and re-runs the checks, so a contributor can converge without hand-editing.
- **FR-011**: The gate MUST be green on the whole existing codebase when this feature lands. All pre-existing lint violations MUST be fixed; no baseline/suppression file is used to hide them.
- **FR-012**: The initial codebase-wide reformat MUST land as an isolated change, separate from the tooling/configuration change, and MUST be recorded in a version-controlled ignore-revisions file so history tooling can skip it.
- **FR-013**: The chosen tools and their versions MUST be pinned in the build configuration so results are reproducible over time.
- **FR-014**: Documentation MUST explain how to run the checks, how to run the fix command, and where the ruleset and canonical format live (README and/or contributor guidance).

### Key Entities *(include if feature involves data)*

- **Lint ruleset**: the versioned Checkstyle configuration defining which non-formatting rules apply, their severity, and exclusions, applied uniformly locally and in CI.
- **Canonical format**: the versioned formatter configuration (deterministic style) that defines how code is laid out; the single source of truth for formatting.
- **Exclusion record**: the explicit, versioned list of what is exempt — in practice, the generated jOOQ sources.
- **Build quality gate**: the wiring that makes a lint violation or a formatting mismatch fail the build, and therefore the pre-push check and CI.
- **Ignore-revisions record**: the version-controlled file listing the initial reformat commit so history tooling skips it.
- **Contributor documentation**: the short guide describing how to run the checks, interpret output, and auto-fix.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A deliberately introduced lint violation causes the standard build command to fail, with the offending file and line shown in the output (verified by a repeatable demonstration).
- **SC-002**: A deliberately introduced formatting mismatch causes the standard build command to fail and names the affected file(s).
- **SC-003**: An attempted push whose changes contain a violation or formatting mismatch is rejected locally by the existing pre-push gate 100% of the time, without the contributor running any special command.
- **SC-004**: A pull request containing a violation or formatting mismatch fails CI on the build job; a pull request without them does not.
- **SC-005**: On the initial adoption, the full build passes on the shared branch with zero unaccounted violations (0 remaining reported violations and 0 files needing formatting, with no baseline entries).
- **SC-006**: A contributor can take code that violates only mechanically fixable rules and make the gate pass by running the single documented fix command, with no manual edits.
- **SC-007**: Two independent environments (a developer machine and CI) produce identical pass/fail for the same commit, using only committed configuration.
- **SC-008**: A new contributor can go from a fresh clone to successfully running the checks by following the documentation in under 5 minutes.
- **SC-009**: After the initial reformat, line-history/blame tooling resolves untouched lines to their original authors rather than to the reformat change.

## Assumptions

- The build is the existing Gradle Kotlin DSL, single-module project (Java 21); both tools integrate as build tasks rather than a separate scripted pipeline.
- The existing pre-push hook runs the full `./gradlew build`; wiring the checks into the standard build lifecycle therefore covers "before push" with no new hook logic.
- CI already builds the project on push/PR; the checks run as part of that same build step.
- The linter is Checkstyle (per the request). The formatter uses google-java-format in its AOSP variant (4-space indentation, 100-column limit) to stay close to the codebase's existing 4-space style.
- Generated jOOQ sources are the only significant generated Java code and the primary exclusion for both tools.
- Enforcing style is a developer-productivity and review-quality concern, not a runtime behavior change; no API or data behavior changes.
- The existing code style is broadly consistent, so the curated ruleset is satisfiable after a one-time, mostly mechanical cleanup (trailing whitespace, a small number of unused imports, and long lines), plus the one-time formatter reformat.
- Formatting is enforced but not auto-applied on save or in hooks beyond the standard build; contributors apply it via the documented command.
