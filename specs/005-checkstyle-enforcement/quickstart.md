# Quickstart: Code Style Enforcement (Checkstyle + Spotless)

**Feature**: 005-checkstyle-enforcement | **Date**: 2026-10-17

## Prerequisites

- JDK 21 and the Gradle wrapper (already provided: Gradle 8.14.3).
- No new installs: the Checkstyle tool and the google-java-format formatter are resolved by Gradle
  from the pinned versions in `build.gradle.kts`.
- Docker is only needed for the existing integration tests, not for the style gate.

## Verify the gate locally (what CI and the pre-push hook run)

```bash
# Whole gate (lint + format) without running tests — fast feedback
./gradlew spotlessCheck checkstyleMain checkstyleTest --console=plain

# The full build, exactly as the pre-push hook and CI run it
./gradlew build --no-daemon --console=plain
```

Expected on a clean tree: `BUILD SUCCESSFUL`, no unformatted files, no lint violations.

## Fix style problems automatically

```bash
# Apply canonical formatting (indentation, wrapping, whitespace, imports, unused imports)
./gradlew spotlessApply

# Then review and re-run the gate
./gradlew spotlessCheck checkstyleMain checkstyleTest --console=plain
```

`spotlessApply` fixes everything the formatter owns. Anything Checkstyle still reports (for example a
missing brace or a naming issue) must be fixed by hand — the report names the file, line, and rule.

## See what Checkstyle reported

- Console: lines of the form `src/.../File.java:12:5: <message> [RuleName]`.
- Browsable HTML: `build/reports/checkstyle/main.html` and `build/reports/checkstyle/test.html`.
- If Spotless fails, the output lists the unformatted files and tells you to run `spotlessApply`.

## Deliberately demonstrate enforcement (optional)

```bash
# 1. Add a style violation (e.g. an unused import or a wrapped line) to any Java file.
# 2. Confirm the gate fails:
./gradlew spotlessCheck checkstyleMain checkstyleTest --console=plain   # expected: FAILED
# 3. Revert the change.
git checkout -- <file>
```

## Make `git blame` ignore the one-time reformat

The initial reformat is recorded as a single mechanical commit. To skip it in blame (per clone):

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

GitHub honors the same file automatically.

## Where things are configured

| What | Where |
|---|---|
| Lint ruleset (selected, `severity=error`) | `config/checkstyle/checkstyle.xml` |
| Checkstyle version + tasks | `build.gradle.kts` → `checkstyle { ... }` |
| Formatter (google-java-format AOSP) + version | `build.gradle.kts` → `spotless { java { ... } }` |
| Generated jOOQ sources excluded from both tools | `build.gradle.kts` |
| Reformat-commit SHA for blame | `.git-blame-ignore-revs` |

## Troubleshooting

- **Spotless reports a file I didn't edit** — the formatter output is standard. Run `spotlessApply`
  and commit the result.
- **`spotlessCheck` passes but `checkstyleMain` fails** — formatting and linting are separate owners.
  Fix the lint finding named in the report.
- **A generated `com.minidoodle.jooq` file appears in a report** — the exclusion regressed. Check the
  Spotless `target(...)` list and the Checkstyle `exclude("**/com/minidoodle/jooq/**")`.
- **The gate feels slow** — `spotlessCheck` and Checkstyle are incremental and cacheable. A warm
  second run should be a few seconds.
