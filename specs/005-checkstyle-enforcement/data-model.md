# Phase 1 Data Model: Code Style Enforcement (Checkstyle + Spotless)

**Feature**: 005-checkstyle-enforcement | **Date**: 2026-10-17

This feature has no persisted data. The "entities" here are the **build-time configuration
artifacts** and their relationships. Each is described with its structure, the rules it carries, and
who owns it.

---

## 1. Artifact relationship overview

```text
build.gradle.kts
├── applies plugin: checkstyle ─────────────► reads  config/checkstyle/checkstyle.xml
│       tasks: checkstyleMain, checkstyleTest ─┐
│                                              ├─► wired into task `check`, therefore `build`,
└── applies plugin: com.diffplug.spotless     │   therefore .githooks/pre-push and CI
        task: spotlessCheck  ─────────────────┘
        task: spotlessApply  (fix command)
        reads (inline Kotlin DSL) the Java format config

.git-blame-ignore-revs ──(hash of)──► the isolated reformat commit
```

Both tools run inside the standard `build`. There is exactly one enforcement path.

---

## 2. Lint ruleset — `config/checkstyle/checkstyle.xml`

**Owner**: Checkstyle (Gradle `checkstyle` plugin, `toolVersion = "14.3.0"`).
**Scope**: `src/main/java` and `src/test/java`. Generated `com.minidoodle.jooq` excluded.
**Severity**: `error` (violations fail the build).

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
  <property name="severity" value="error"/>
  <property name="charset" value="UTF-8"/>
  <!-- Formatting is owned by Spotless: no line-length, whitespace, import-order,
       newline-at-EOF, or tab checks here. -->

  <module name="TreeWalker">
    <!-- Imports / file shape -->
    <module name="OuterTypeFilename"/>
    <module name="AvoidStarImport"/>
    <module name="RedundantImport"/>
    <!-- Block conventions (google-java-format does not add braces) -->
    <module name="NeedBraces"/>
    <module name="EmptyBlock">
      <property name="option" value="TEXT"/>
      <property name="tokens" value="LITERAL_CATCH,LITERAL_TRY,LITERAL_IF,LITERAL_ELSE"/>
    </module>
    <module name="OneTopLevelClass"/>
    <module name="NoFinalizer"/>
    <!-- Bug patterns -->
    <module name="EmptyStatement"/>
    <module name="MissingSwitchDefault"/>
    <module name="FallThrough"/>
    <module name="DefaultComesLast"/>
    <module name="EqualsHashCode"/>
    <module name="StringLiteralEquality"/>
    <module name="SimplifyBooleanExpression"/>
    <module name="SimplifyBooleanReturn"/>
    <module name="InnerAssignment"/>
    <!-- Naming -->
    <module name="PackageName"/>
    <module name="TypeName"/>
    <module name="MethodName"/>
    <!-- The default pattern rejects the conventional lowercase `log` logger name used by
         SLF4J; allow it explicitly so the check still catches genuinely misnamed constants. -->
    <module name="ConstantName">
      <property name="format" value="^(log|[A-Z][A-Z0-9]*(_[A-Z0-9]+)*)$"/>
    </module>
    <module name="StaticVariableName"/>
    <module name="MemberName"/>
    <module name="ParameterName"/>
    <module name="LocalVariableName"/>
    <!-- Checkstyle 12+ replaced the old TypeParameterName check with per-declaration
         variants; all four are included so every type parameter is still covered. -->
    <module name="ClassTypeParameterName"/>
    <module name="InterfaceTypeParameterName"/>
    <module name="RecordTypeParameterName"/>
    <module name="MethodTypeParameterName"/>
    <module name="CatchParameterName"/>
  </module>
</module>
```

Notes:
- **Every module above sits under `TreeWalker`** — all of these are AST-based checks. `Checker` only
  carries the shared `severity`/`charset` properties. (File-level checks such as
  `FileTabCharacter`/`NewlineAtEndOfFile` are deliberately absent, since Spotless owns them.)
- `severity` is set on `Checker` so it is inherited by every rule (violations are `error` → the
  Gradle `checkstyle` task fails with `maxErrors = 0`).
- `IllegalCatch`/`IllegalThrows` are intentionally absent (two intentional `catch (Throwable)` blocks
  in the concurrency integration tests).
- `UnusedImports` is intentionally absent (Spotless removes unused imports).

---

## 3. Formatter configuration — `build.gradle.kts` (inline)

**Owner**: Spotless (`com.diffplug.spotless` `8.10.4`) + google-java-format `1.36.0` (AOSP).
**Scope**: `src/main/java/**/*.java`, `src/test/java/**/*.java` (explicit targets → generated dir
excluded).

Intent (exact Kotlin DSL finalized in implementation, then verified by running):

```kotlin
plugins {
    java
    checkstyle
    id("com.diffplug.spotless") version "8.10.4"
    // ... existing plugins
}

checkstyle {
    toolVersion = "14.3.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/com/minidoodle/jooq/**")
}

spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        googleJavaFormat("1.36.0").aosp()
        removeUnusedImports()
        importOrder("com.minidoodle", "java", "javax", "org", "com", "", "\\#")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

**Formatting ownership map** (what Spotless defines):

| Formatting concern | Mechanism |
|---|---|
| Indentation (4 spaces, AOSP) | `googleJavaFormat(...).aosp()` |
| Line wrapping / 100-col limit | `googleJavaFormat(...)` |
| Blank lines, spacing, trailing whitespace, final newline | `googleJavaFormat(...)` + `trimTrailingWhitespace()` + `endWithNewline()` |
| Unused imports removed | `removeUnusedImports()` |
| Import grouping/order | `importOrder(...)` (group list above) |

`spotlessCheck` is created and automatically added to `check`. `spotlessApply` is the documented fix
command (FR-010).

---

## 4. Exclusion record

**Owner**: build configuration (no separate file).
**Content**: the generated jOOQ source tree only — `build/generated-sources/jooq` /
package `com.minidoodle.jooq`. Excluded through explicit Spotless targets and the Checkstyle `exclude`
pattern. No entry is added for human-written code (FR-011: no baseline).

---

## 5. Ignore-revisions record — `.git-blame-ignore-revs`

**Owner**: git configuration (opt-in per clone).
**Content**: a comment header plus the 40-char SHA of the isolated reformat commit. Format:

```text
# Reformat existing Java sources with Spotless + google-java-format (AOSP).
# Enable locally:  git config blame.ignoreRevsFile .git-blame-ignore-revs
<reformat-commit-sha>
```

**State transition**: written after the reformat commit exists (its hash is knowable only then), so
it lands in a follow-up commit. Documented in README/AGENTS.

---

## 6. Build quality gate (the enforcement path)

**Owner**: Gradle task graph.

| Task | Provided by | In `check`? | Fails build on |
|---|---|---|---|
| `checkstyleMain` | Gradle `checkstyle` plugin | yes (auto) | lint violation in `src/main/java` |
| `checkstyleTest` | Gradle `checkstyle` plugin | yes (auto) | lint violation in `src/test/java` |
| `spotlessCheck` | Spotless plugin | yes (auto, `enforceCheck`) | file not matching the standard format |
| `spotlessApply` | Spotless plugin | no | — (writes fixes) |

`check` → `build` → `.githooks/pre-push` (`./gradlew build`) and CI
(`./gradlew build --no-daemon --console=plain`). No new hook or workflow file.

---

## 7. Documentation entities

- **README.md**: how to run `./gradlew spotlessCheck checkstyleMain checkstyleTest`, how to fix with
  `./gradlew spotlessApply`, where the ruleset (`config/checkstyle/checkstyle.xml`) and formatter
  config (`build.gradle.kts`) live, and the `blame.ignoreRevsFile` setup.
- **AGENTS.md**: a short contributor note that the style gate is part of `build` and the fix command.

---

## Validation rules (from the spec)

- A lint violation in either source set → nonzero build result (FR-007, SC-001).
- A formatting mismatch → nonzero build result naming the file(s) (FR-007, FR-009, SC-002).
- Generated jOOQ sources never appear in either report (FR-005).
- On the cleaned tree, `spotlessCheck` + `checkstyleMain` + `checkstyleTest` pass with zero findings
  (FR-011, SC-005).
- `spotlessApply` is idempotent: a second run yields no diff (SC-006).
- Two environments (local JDK 21, CI) agree using only committed configuration (SC-007).
