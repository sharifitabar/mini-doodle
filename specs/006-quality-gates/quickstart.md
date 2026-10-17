# Quickstart: Multi-Dimensional Quality Gates

**Feature**: 006-quality-gates | **Date**: 2026-10-17

How to run each gate, read its output, and fix a failure. Nothing here is a separate step for
everyday work: all fast gates run inside `./gradlew build`.

---

## The one command that matters

```bash
./gradlew build --no-daemon --console=plain
```

This is what the pre-push hook and CI run. If it is green, every fast gate passes. It now includes:

- style (formatting + lint) — feature 005,
- **architecture conformance** (`ArchitectureTest`, via `test`),
- **complexity ceilings** (inside `checkstyleMain`/`checkstyleTest`),
- **coverage verification** (`jacocoTestCoverageVerification`, via `check`).

It does **not** include mutation analysis.

---

## Coverage

```bash
./gradlew test jacocoTestReport          # measure and report
open build/reports/jacoco/test/html/index.html
```

Browsable report: `build/reports/jacoco/test/html/index.html` — per package, per class, per line,
with branch detail (green/red/yellow annotations on source lines).

Machine-readable: `build/reports/jacoco/test/jacocoTestReport.xml`.

### Reading the six dimensions

`./gradlew jacocoTestReport` reports **instruction, branch, line, method, class, and cyclomatic
complexity**. Four of them have enforced floors; all six are always reported.

### If coverage verification fails

```bash
./gradlew jacocoTestCoverageVerification
```

The message names the rule (counter and scope), the measured value, and the minimum, for example a
`BRANCH` rule that came in at 0.94 against 0.95. To fix it:

1. Open the HTML report and find the red/yellow branches — a **yellow** line means the line ran but
   one path never did, which is the usual cause of a branch-floor failure.
2. Add the missing assertion or case. If the gap is in error handling, look for an
   `@ExceptionHandler` with no test.
3. Re-run. Do **not** lower a floor to accommodate a regression; floors only move up.

### Why some classes have their own (stricter) rule

`GlobalExceptionHandler`, `JooqSlotRepository`, and `CurrentUserArgumentResolver` carry class-scoped
rules because they hold the error and recovery behaviour. They can be far below the class average
while the bundle looks healthy — that is exactly what a bundle floor cannot see.

---

## Complexity ceilings

They ride the existing lint gate:

```bash
./gradlew checkstyleMain checkstyleTest
```

A failure looks like:

```text
.../AvailabilityCalculator.java:49:5: Cyclomatic complexity is 11 (max allowed is 10). [CyclomaticComplexity]
```

Fix it by extracting a method or simplifying the branching. There is **no suppression file and no
`CHECKSTYLE:OFF`** — if a ceiling is genuinely wrong, the ceiling is the thing to change, in
`config/checkstyle/checkstyle.xml`, deliberately and visibly.

| Check | Ceiling | Counts |
|---|---|---|
| `CyclomaticComplexity` | 10 | independent paths through a method |
| `NPathComplexity` | 30 | acyclic execution paths through a method |
| `MethodLength` | 70 | lines in a method |
| `ParameterNumber` | 7 | parameters of a method or constructor |

---

## Architecture conformance

The rules are ordinary tests, so they run with the suite:

```bash
./gradlew test --tests '*Architecture*'
```

A failure names the rule and the offending type, for example a domain type referencing Spring, or
`adapter.in` reaching into `adapter.out`. Fix it by depending on the port/interface instead of the
concrete type — dependencies point inward.

The rules are in `src/test/java/**/ArchitectureTest.java`. A companion test proves each rule
actually fails when violated, so a rule can never be silently vacuous.

---

## Mutation analysis (on demand)

```bash
./gradlew pitest
open build/reports/pitest/index.html
```

Answers a different question from coverage: not "was this line executed?" but "would a test fail if
this line were wrong?". Scoped to the domain package, because that is the only layer with fast unit
tests — running it over the container-backed integration layers would multiply container startup by
hundreds of mutants.

Survivors are the interesting output: each one is a change to the code that no test noticed. Some
are equivalent mutants (inherently undetectable, common in logging and `toString`) and can be
ignored; the rest point at a missing or too-weak assertion.

There is no mutation threshold. It is never part of `./gradlew build`.

---

## Verifying the gates actually bite

Each gate has a deliberate-failure check, worth running when changing the configuration:

| Gate | How to prove it fails |
|---|---|
| Coverage | temporarily add a rule demanding a ratio above today's measured value |
| Complexity | temporarily lower a ceiling below a known method's measured value |
| Architecture | temporarily add a dependency that breaks a rule (e.g. import a Spring type into a domain class) |
| Error-path class rules | the rule-effectiveness test covers this; run `./gradlew test --tests '*Architecture*'` |
| Mutation | `./gradlew pitest` and confirm survivors are listed per package |

Revert immediately after.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| `jacocoTestCoverageVerification` reports metrics including generated code | `classDirectories` filtering was not applied to the verification task (it owns a separate collection from the report task) |
| `:jacocoTestCoverageVerification SKIPPED` | no `.exec` data — the task needs `test` to have run; use `./gradlew test jacocoTestCoverageVerification` |
| A complexity failure points at a *test* method | ceilings apply to test sources too; long concurrency tests are the usual cause |
| Architecture rule passes with an obviously wrong dependency | the rule's package pattern does not match the type; the rule-effectiveness test is the guard for this |
| `pitest` takes a very long time | `targetTests` is not scoped, so container-backed integration tests are running per mutant |
