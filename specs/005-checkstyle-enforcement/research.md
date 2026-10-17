# Phase 0 Research: Code Style Enforcement (Checkstyle + Spotless)

**Feature**: 005-checkstyle-enforcement | **Date**: 2026-10-17

This document resolves the technical unknowns behind the plan. Decisions are recorded as
**Decision / Rationale / Alternatives considered**, matching the Spec Kit convention.

---

## Decision: Formatter = Spotless with google-java-format (AOSP)

**Rationale**: The spec requires deterministic, repository-versioned formatting that is applied
identically locally and in CI (FR-003). Spotless expresses this as "format ↔ `spotlessApply`" and
"verify ↔ `spotlessCheck`", with the formatter version pinned in `build.gradle.kts`, so results are
reproducible (FR-013) and IDE-independent (US3). The **AOSP** variant of google-java-format uses
4-space indentation and a 100-column limit, which is the closest match to the existing codebase
(104 of ~120 files use 4-space indent), keeping the one-time diff as small as the formatter allows.
Spotless also automatically opens the JDK-internal packages google-java-format needs
(`ModuleHelper.doOpenInternalPackagesIfRequired()` in `GoogleJavaFormatStep`), so no manual JVM
`--add-exports` flags are required on JDK 21.

**Alternatives considered**:
- **Gradle `checkstyle` only** — Checkstyle is a linter, not a fixer; it cannot reformat whitespace,
  wrapping, or imports. Rejected because the drift the project actually has (trailing whitespace,
  long lines) needs a formatter (FR-010, "single fix command").
- **Palantir Java Format** — attractive 120-col default, but the user explicitly chose
  google-java-format.
- **Google style (2-space)** — canonical, but would convert 104 four-space files and is unusual for
  Java; AOSP minimizes surprise (user decision).
- **Eclipse JDT / IntelliJ `idea()` formatters** — heavier configuration and IDE-coupled; Spotless's
  google-java-format is the pinned, deterministic choice.

---

## Decision: Linter = Gradle's built-in `checkstyle` plugin

**Rationale**: Spotless is "primarily a formatter, not a linter" (its own docs) and ships no Checkstyle
step, so the linter is the standard Gradle `checkstyle` plugin. Applying it auto-creates
`checkstyleMain`/`checkstyleTest` and wires them into the `check` lifecycle, so `./gradlew build`
(what the pre-push hook and CI already run) fails on violations — satisfying FR-006 with no new hook
or CI wiring. The ruleset lives in the conventional `config/checkstyle/checkstyle.xml` and the tool
version is pinned via `checkstyle { toolVersion = "14.3.0" }`.

**Alternatives considered**:
- **Spotless `checkstyle` step** — does not exist in Spotless; would require a custom step.
- **PMD / Error Prone / Sonar** — different tool classes; the request names Checkstyle, and Error
  Prone would require compiler-plugin surgery (higher risk, out of scope).

---

## Decision: Single-owner split of duties (Spotless formats, Checkstyle lints)

**Rationale**: If both tools police line length, import order, or whitespace, they can disagree and
produce contradictory failures. The clean split (FR-004) is:

| Concern | Owner |
|---|---|
| Indentation, line wrapping/limit, blank lines, trailing whitespace, final newline | Spotless (google-java-format AOSP) |
| Import order and unused-import removal | Spotless (`importOrder()` + `removeUnusedImports()`) |
| Naming conventions | Checkstyle |
| Brace/block and bug-pattern conventions | Checkstyle |
| Redundant/star imports (not handled by the formatter) | Checkstyle |

Because google-java-format removes unused imports and (via Spotless's `importOrder`) sorts them, the
Checkstyle ruleset **omits** `UnusedImports`, `ImportOrder`, `CustomImportOrder`, `LineLength`,
`FileTabCharacter`, `NewlineAtEndOfFile`, and whitespace checks.

**Alternatives considered**: letting Checkstyle also check formatting (redundant, conflict-prone);
letting Spotless own naming via a custom step (not its job).

---

## Decision: Pinned versions (as of 2026-10-17)

**Rationale**: FR-013 requires reproducible versions. Verified current releases:

| Component | Version | Source |
|---|---|---|
| Spotless Gradle plugin | `8.10.4` | Gradle Plugin Portal (8.10.4, created 2026-10-08) |
| google-java-format | `1.36.0` | Latest whose `Style` API still matches Spotless 8.10.4's reflective glue — see the version-ceiling decision below |
| Checkstyle | `14.3.0` | Maven Central `maven-metadata.xml` (latest, 2026-09-27) |
| Gradle | `8.14.3` (existing) | `gradle/wrapper/gradle-wrapper.properties` |
| Java toolchain | `21` (existing) | `build.gradle.kts` |

Compatibility: Spotless 8.x requires **JRE 17+ and Gradle 7.3+** (we have JDK 21 / Gradle 8.14.3).
Spotless's google-java-format support requires ≥ `1.17.0` on JDK 21 (and ≥ `1.30.0` on JDK 25);
`1.36.0` is fine on JDK 21 (see the version-ceiling decision below for why the very latest is
not usable). Checkstyle 14.x runs on JDK 17+; tool version is set explicitly rather
than relying on Gradle's bundled default.

**Alternatives considered**: relying on Spotless's default google-java-format version
(`BuildConfig.VERSION_GJF`, whatever ships with 8.10.4) — rejected because it is implicit and could
shift between Spotless upgrades (violates FR-013). Using "latest" dynamically — rejected (not
reproducible).

---

## Decision: google-java-format is capped at `1.36.0` (not the latest)

**Rationale**: Verified during implementation, this is the one place where "pin the newest version"
is actively wrong. Spotless does not call google-java-format's API directly; it loads the formatter
into an isolated classloader and drives it through a reflective glue class
(`com.diffplug.spotless.glue.java.GoogleJavaFormatFormatterFunc`) that does
`Style.valueOf(style)` in its constructor. **google-java-format 1.37.0 turned
`JavaFormatterOptions.Style` from an enum into a record**, and records have no `valueOf(String)`.
The resulting `NoSuchMethodException` is thrown from the glue constructor, which is instantiated via
`Constructor.newInstance(...)`, so it is wrapped as `java.lang.reflect.InvocationTargetException` —
and every single source file fails with that same opaque error. The formatter jar's manifest
`Add-Exports` and Spotless's `ModuleHelper` both work correctly (verified: the `jdk.compiler`
packages *are* opened inside the Gradle daemon), so the failure is purely the API break, not a
JPMS problem.

Confirmed by inspecting the jars: `Style` has `valueOf` in `1.32.0`–`1.36.0` and does not in
`1.37.0`. `1.36.0` is therefore the highest version compatible with Spotless 8.10.4.

**Alternatives considered**: passing the exports via `org.gradle.jvmargs` — rejected because the
exports were never the problem. Upgrading/downgrading Spotless — rejected, 8.10.4 is current.
Forking the glue — rejected, far beyond the scope of a build-quality gate.

---

## Decision: Checkstyle naming rules adapted to 14.3.0's check set

**Rationale**: Two adjustments were required to run the curated ruleset on Checkstyle `14.3.0`,
both discovered by running it against the real codebase:

1. **`TypeParameterName` no longer exists** — 14.x removed it and replaced it with per-declaration
   variants. The ruleset uses all four (`ClassTypeParameterName`, `InterfaceTypeParameterName`,
   `RecordTypeParameterName`, `MethodTypeParameterName`) so type parameters stay covered.
2. **`ConstantName` must allow the conventional lowercase `log`** — four classes declare
   `private static final Logger log`, which the default pattern `^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`
   rejects. Rather than rename them to `LOG` (against SLF4J convention) or drop the check, the
   pattern is widened to `^(log|[A-Z][A-Z0-9]*(_[A-Z0-9]+)*)$`, which keeps the check's value for
   genuinely misnamed constants.

**Alternatives considered**: dropping `ConstantName` entirely (loses real signal); renaming `log` →
`LOG` in 4 files (fights the ecosystem convention and is the kind of churn the curated ruleset was
chosen to avoid).

---

## Decision: Enforce the whole tree now; generated jOOQ sources excluded

**Rationale**: The user chose "fix all now" (FR-011), so there is no ratchet and no suppression
baseline. The only generated Java in the build is the jOOQ output added to the `main` source set
(`build/generated-sources/jooq`, package `com.minidoodle.jooq`). It must be excluded from **both**
tools or the gate would demand formatting changes to machine-generated files. Exclusion approach:

- **Spotless**: set the Java target explicitly to `src/main/java/**/*.java` and
  `src/test/java/**/*.java` (which inherently excludes `build/`), rather than relying on source-set
  inference (which would include the generated dir).
- **Checkstyle**: `exclude("**/com/minidoodle/jooq/**")` on the `Checkstyle` tasks (the generated
  root is a separate source root, so the package-relative pattern is used).

**Alternatives considered**: `targetExclude("build/generated-sources/**")` for Spotless (works, but
explicit targets are clearer); excluding the generated dir from compilation (breaks the build —
rejected); `// CHECKSTYLE:OFF` suppressions inside generated files (impossible — regenerated).

---

## Decision: Curated, non-formatting Checkstyle ruleset (severity = error)

**Rationale**: The user chose a curated baseline, not a full published style, to keep signal high and
churn near zero. Auditing the current tree shows the curated concerns are already satisfied:
**0** underscore test methods, **0** star imports, **0** `System.out`/`printStackTrace`, **0**
braceless control statements, **0** empty catch blocks, **0** acronym-heavy type names. The only
flagged patterns are two intentional `catch (Throwable error)` blocks in concurrency tests, so
`IllegalCatch` is deliberately **excluded**. Rules are set to `severity="error"` and the tasks keep
Gradle's default `ignoreFailures = false` / `maxErrors = 0`, so violations fail the build (FR-007).

Selected checks (grouped): **Naming** (`PackageName`, `TypeName`, `MethodName`, `ConstantName`
with a widened pattern allowing `log`, `StaticVariableName`, `MemberName`, `ParameterName`,
`LocalVariableName`, `ClassTypeParameterName`, `InterfaceTypeParameterName`,
`RecordTypeParameterName`, `MethodTypeParameterName`, `CatchParameterName`); **Imports**
(`AvoidStarImport`, `RedundantImport`); **File**
(`OuterTypeFilename`); **Coding** (`EmptyStatement`, `MissingSwitchDefault`, `FallThrough`,
`EqualsHashCode`, `StringLiteralEquality`, `SimplifyBooleanExpression`, `SimplifyBooleanReturn`,
`DefaultComesLast`, `InnerAssignment`, `NoFinalizer`, `OneTopLevelClass`); **Blocks** (`NeedBraces`,
`EmptyBlock` with an explanatory-text rule). The exact XML is specified in
[data-model.md](./data-model.md) §2 and [contracts/quality-gate.md](./contracts/quality-gate.md).

**Alternatives considered**: Google Checks / Sun Checks as-is (large churn: Javadoc on 57 public
types, `final` parameters, 100-col re-wrapping, import reorder — rejected per the user's "curated"
choice); `IllegalCatch`/`IllegalThrows` (rejected: two intentional `catch (Throwable)` in concurrency
tests, and changing them would weaken the tests); `UnusedLocalVariable`/`HiddenField`/`ModifierOrder`
(noisy; deferred rather than shipped).

---

## Decision: Isolated reformat commit + `.git-blame-ignore-revs`

**Rationale**: FR-012. The formatter reformats nearly every Java file, so the reformat must be a
separate, mechanical commit for reviewability, and its hash recorded in `.git-blame-ignore-revs` so
`git blame` (with `blame.ignoreRevsFile` set) skips it and attributes untouched lines to their real
authors (SC-009). Because `.git-blame-ignore-revs` must contain the reformat commit's hash, the
sequence is: (1) land the reformat, (2) record its hash in `.git-blame-ignore-revs`. The tooling
wiring and the reformat are committed separately; the push happens only once the tree is green, so
the build is never red at push time (AGENTS.md pre-push rule).

**Alternatives considered**: a single combined commit (rejected by the user — hard to review, pollutes
blame); ratcheting via `spotless { ratchetFrom 'origin/main' }` (rejected — the user chose "fix all
now", and ratcheting hides rather than fixes); no blame-ignore file (rejected — makes history
misleading).

---

## Decision: Scope limited to Java sources

**Rationale**: The request is Checkstyle (Java) plus a Java formatter. Formatting `.kts`, `.yml`, or
`.md` would add a large, unrelated diff and new concerns; it is out of scope (recorded in the plan).
Spotless is configured only in its `java { }` block.

**Alternatives considered**: also formatting build scripts (`kotlinGradle`) and docs — deferred as
YAGNI (constitution IV).

---

## Resolved unknowns

| Unknown | Resolution |
|---|---|
| Which formatter/variant | Spotless + google-java-format `1.36.0`, AOSP (4-space) |
| Which linter and how run | Gradle built-in `checkstyle`, toolVersion `14.3.0`, `checkstyleMain`/`checkstyleTest` |
| Do we need manual JVM flags on JDK 21? | No — Spotless opens the required JDK packages automatically |
| Does Spotless auto-wire into `check`? | Yes — `spotlessCheck` is added to `check` (`enforceCheck` default true) |
| Does the Gradle `checkstyle` plugin auto-wire into `check`? | Yes — `checkstyleMain`/`checkstyleTest` are in the `check` lifecycle |
| How to exclude generated sources | Explicit Spotless Java targets; `**/com/minidoodle/jooq/**` excluded from Checkstyle |
| How to preserve blame | Isolated reformat commit + `.git-blame-ignore-revs` |
| Existing-violation footprint | 0 curated-check violations; only formatter reformat + 2 unused imports + trailing whitespace |
