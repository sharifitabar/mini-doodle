# Contract: Style Quality Gate

**Feature**: 005-checkstyle-enforcement | **Date**: 2026-10-17

There is no HTTP API in this feature. The observable contract is the **build/command surface**
that contributors, the pre-push hook, and CI rely on. Anything that consumes this feature (a
developer, a hook, a CI job, an IDE) depends only on the commands, exit behavior, and report
locations described here.

---

## 1. Commands and expected behavior

| Command | Contract |
|---------|----------|
| `./gradlew build` | Runs the style gate as part of `check`. Fails (nonzero exit) if any lint violation or formatting mismatch exists. This is the single command the pre-push hook and CI run. |
| `./gradlew check` | Same gate without packaging. Fails on any style finding. |
| `./gradlew spotlessCheck` | Fails if any Java source does not match the standard format. The message names the affected file(s) and points at `spotlessApply`. Passes when the tree is formatted. |
| `./gradlew checkstyleMain` / `checkstyleTest` | Fails if any lint rule in the selected ruleset is violated. The report names file, line, and rule. Passes on the cleaned tree. |
| `./gradlew spotlessApply` | Rewrites sources to the standard format (formatting, import order, unused-import removal). Exits 0. Running it on an already-formatted tree makes **no** changes (idempotent). |
| `./gradlew spotlessCheck checkstyleMain checkstyleTest` | The documented "verify locally" combination. Runs the whole gate without tests. |

## 2. Exit / result semantics

- **Success**: exit code 0. No files reported by `spotlessCheck`. No violations from
  `checkstyleMain`/`checkstyleTest`.
- **Failure**: nonzero exit. The affected file and location are present in the task output
  (Checkstyle) or the list of unformatted files (Spotless). A finding is always a **build failure**,
  never a silent warning (FR-007).
- **Generated sources**: `build/generated-sources/jooq` / package `com.minidoodle.jooq` never appear
  in either report (FR-005).

## 3. Failure message contract

- Checkstyle identifies each violation as `path:line:col: message [RuleName]` (Checkstyle's standard
  console format). A browsable report is also written under `build/reports/checkstyle/`.
- Spotless lists each unformatted file and instructs the user to run `./gradlew spotlessApply`
  (Spotless's standard output).

## 4. Report locations

| Artifact | Path |
|---|---|
| Checkstyle main report | `build/reports/checkstyle/main.html` / `main.xml` |
| Checkstyle test report | `build/reports/checkstyle/test.html` / `test.xml` |
| Spotless diagnostics (optional) | `./gradlew spotlessDiagnose` |

## 5. Integration points (must remain true)

- `.githooks/pre-push` runs `./gradlew build --no-daemon --console=plain`. The gate is enforced by
  that existing command, with **no change** to the hook logic (FR-006).
- `.github/workflows/ci.yml` runs `./gradlew build --no-daemon --console=plain`. The gate is enforced
  by that existing step, with **no change** to the workflow (FR-006).
- A push whose changes violate the gate is rejected locally. Bypassing with `--no-verify` is still
  caught by CI (US1/US2).

## 6. Configuration surface (where things live)

| Concern | Location |
|---|---|
| Lint ruleset | `config/checkstyle/checkstyle.xml` (severity `error`) |
| Checkstyle tool version | `build.gradle.kts` → `checkstyle { toolVersion = "14.3.0" }` |
| Formatter + version | `build.gradle.kts` → `spotless { java { googleJavaFormat("1.36.0").aosp() ... } }` |
| Generated-source exclusion | `build.gradle.kts` (Spotless explicit targets, Checkstyle `exclude`) |
| Blame ignore file | `.git-blame-ignore-revs` |

## 7. Non-goals (explicit)

- No formatting of `.kts`, `.yml`, `.md`, or other non-Java files.
- No pre-commit hook, no `--no-verify` interception, no ratcheting, no remote cache/Build Scan.
- No runtime dependency and no change to application behavior or the HTTP API.
