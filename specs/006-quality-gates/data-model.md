# Phase 1 Data Model: Multi-Dimensional Quality Gates

**Feature**: 006-quality-gates | **Date**: 2026-10-17

This feature stores no runtime data. The "entities" here are the *configuration and enforcement
objects* the gates are made of: what is measured, what is enforced, and where the values came from.
Each entity lists its fields, its validation rules (the invariants the implementation must
preserve), and — where relevant — its lifecycle.

---

## 1. Coverage Dimension

One independent measure of test thoroughness. Derived from JaCoCo; nothing is hand-maintained.

| Field | Values | Notes |
|---|---|---|
| `counter` | `INSTRUCTION`, `BRANCH`, `LINE`, `METHOD`, `CLASS`, `COMPLEXITY` | JaCoCo's six counters |
| `measured` | ratio | From `jacocoTestReport`; always reported, whatever the enforcement |
| `enforced` | boolean | Whether a bundle-level floor exists for this counter |
| `baseline` | ratio at time of writing | Recorded in `research.md` and below |
| `floor` | ratio or absent | Present only when `enforced` |

| Counter | As built | Enforced | Floor | Failure it detects |
|---|---|---|---|---|
| LINE | 99.16% (949/957) | yes | 0.95 | behaviour that is not executed at all |
| BRANCH | 97.22% (175/180) | yes | 0.95 | a decision whose outcome is never taken |
| METHOD | 99.19% (245/247) | yes | 0.95 | an entire method never invoked |
| COMPLEXITY | 97.92% (330/337) | yes | 0.95 | decision points never exercised |
| INSTRUCTION | 99.41% | no | — | (correlated with LINE; still reported) |
| CLASS | 100% | no | — | (near-binary; the real concern is §2) |

The "as built" column is the finished tree, after the gap-closing tests of `research.md` D5–D8. The
pre-implementation baseline (LINE 94.1%, BRANCH 85.1%, METHOD 95.1%, COMPLEXITY 90.3%) is kept in
`research.md`; floors are derived from the as-built ratios, not from the gap-ridden ones.

**Validation rules**

- Every counter in the "reported" set MUST appear in the generated report (FR-001).
- Each floor MUST be the as-built ratio rounded **down** to the nearest 0.05, so the standard build
  is green on an unmodified tree with no suppression (SC-009, FR-018's spirit).
- Generated code (jOOQ) MUST NOT contribute to any ratio (FR-004).

---

## 2. Violation Rule

A single enforceable coverage constraint. Two scopes exist, and they answer different questions.

| Field | Values | Notes |
|---|---|---|
| `scope` | `BUNDLE`, `CLASS` | Element the rule is evaluated against |
| `includes` | class-name patterns | Present only for `CLASS` scope |
| `counter` | as §1 | |
| `value` | `COVEREDRATIO`, `COVEREDCOUNT`, `MISSEDCOUNT`, … | Only `COVEREDRATIO` is used here |
| `minimum` | decimal | BigDecimal in the build script |
| `rationale` | text | Why this scope and this number |

**Bundle-scoped rules** — one per enforced dimension in §1.

**Class-scoped rules** — the implementation of the "error-path dimension" (FR-006):

| Target class | Counter | Floor | As built |
|---|---|---|---|
| `GlobalExceptionHandler` | LINE | 1.00 | 40/40 = 100% |
| `GlobalExceptionHandler` | METHOD | 1.00 | 19/19 = 100% |
| `JooqSlotRepository` | LINE | 0.95 | 93/94 = 98.94% |
| `JooqSlotRepository` | BRANCH | 0.90 | 11/12 = 91.67% |
| `JooqMeetingRepository` | LINE | 0.95 | 84/85 = 98.82% |
| `JooqMeetingRepository` | BRANCH | 0.90 | 9/10 = 90.00% |
| `CurrentUserArgumentResolver` | BRANCH | 1.00 | 8/8 = 100% |

`TimeRange` and `DevDataSeeder` — the other two classes the audit flagged — are held by the bundle
floor instead: the dead `overlaps()` method is deleted (so `TimeRange` reaches 100%) and the seeder
is enabled in a test (so it reaches 100%). A class with no residue does not need a rule of its own.
`JooqSlotRepository` and `JooqMeetingRepository` are the classes with accepted residue: the
`throw ex;` for a delete failure that is not a foreign-key violation, and the analogous rethrow for
a failure that is not a unique violation, reachable only on an infrastructure fault. Both are
documented next to their rules in `build.gradle.kts`.

**Validation rules**

- Class-scoped rules MUST name their targets explicitly; there is no package-wide rule (a package
  rule mixes error mapping with unrelated code — see research D3).
- A class-scoped floor of 1.00 is only permitted where the gap-closing tests make it achievable;
  it is a deliberate "all of this behaviour must be tested" statement.
- The same `classDirectories` filter (jOOQ excluded) MUST be applied to the verification task as to
  the report task; otherwise a `CLASS` rule can be satisfied by generated code.

**State transition**: each class-scoped floor is expected to move in one direction only. When the
gap-closing tests land, the floor is *set* at the post-test value and never lowered afterwards.

---

## 3. Gap-Closing Test

A test added specifically because the baseline audit found behaviour unexercised. Distinguished
from ordinary tests because success is defined by a named code location becoming executed.

| Field | Description |
|---|---|
| `target` | the code location(s) the test must execute |
| `trigger` | how the behaviour is provoked |
| `asserts` | the specific outcome asserted (not merely "no exception") |
| `reachable` | whether the trigger exists in production, or a seam is required |

| # | Target | Trigger | Asserts | Reachable |
|---|---|---|---|---|
| 1 | `handleInvalidSlot` | `PATCH /api/v1/slots/{id}` with an empty body object | 400, type `invalid-slot` | yes |
| 2 | `handleInvalidMeeting` | book a meeting with a participant having neither name nor email | 400, type `invalid-meeting` | yes |
| 3 | `handleTypeMismatch` | `GET /api/v1/slots/not-a-uuid` | 400, type `validation` | yes |
| 4 | `handleUnreadable` | malformed JSON body | 400, type `validation` | yes |
| 5 | `handleMissingHeader` | test-only controller declaring a required header, with the real advice | 400, type `validation` | **no** — test-only seam |
| 6 | retry/backoff/give-up | injected seam driven with synthetic `SQLState`s | return / rethrow / `SlotOverlapException` / interrupt restoration | **no** — test-only seam |
| 7 | `DevDataSeeder` | Spring context with `app.seed-demo-data=true` | demo user + calendar exist; second run is a no-op | yes |
| 8 | `CurrentUserArgumentResolver` blank/malformed header | request with absent / non-UUID `X-User-Id` | 400, type `invalid-caller` | yes |

**Validation rules**

- Every entry MUST assert the specific outcome (FR-011): status *and* problem type where it is an
  error response; the recovery effect where it is a retry.
- No entry may rely on a flaky trigger (constitution III).
- A test that cannot name the code location it closes MUST NOT be added (this is the anti-goal).

---

## 4. Architecture Rule

An enforceable statement about which parts of the system may depend on which.

| Field | Description |
|---|---|
| `id` | A1–A5 |
| `statement` | the rule in one sentence |
| `scope` | packages the rule covers |
| `failure message` | must name the rule and the offending type (FR-015) |
| `negative control` | a fixture that must make the rule fail (FR-016) |
| `status today` | PASS/FAIL on the unmodified tree |

| id | Statement | Scope | Status today |
|---|---|---|---|
| R1 | The domain does not depend on frameworks (Spring, Jakarta, jOOQ, Hibernate) | `..domain..` | PASS |
| R2 | The domain depends on nothing but the JDK and the domain itself | `..domain..` | PASS |
| R3 | The application layer does not depend on adapters | `..application..` → `..adapter..` | PASS |
| R4 | Inbound and outbound adapters do not depend on each other | `..adapter.in..` ↔ `..adapter.out..` | PASS |
| R5 | Shared infrastructure does not depend on the application or the adapters | `..shared..` | PASS |
| R6 | The context's packages form no dependency cycle | leaf packages under `com.minidoodle.calendar` | PASS |

**Validation rules**

- Production rules MUST import only production classes (`ImportOption.DoNotIncludeTests`), or the
  negative-control fixtures in §5 would make them fail.
- Each rule MUST have a negative control that fails and is asserted to fail.
- R1/R2 MUST NOT be extended to the application layer: measurement showed the application uses Spring
  (`@Service`, `@Component`, `@Transactional`), and the constitution's framework-free claim covers
  the domain only. The application is constrained by direction (R3) instead.
- R6 MUST slice **leaf packages** from the context root, not top-level packages: `calendar` and
  `shared` reference each other legitimately (inbound adapters use `shared.web.CurrentUser`; the
  shared exception advice maps `calendar.domain.exception` types), so a top-level slice rule is red
  on a correct tree. `shared` is outside the pattern because R5 plus R2 already make a cycle through
  it impossible. See research D4.

---

## 5. Negative-Control Fixture

A deliberately non-conforming class living in the test sources, used to prove a rule is not
vacuously satisfied.

| Field | Description |
|---|---|
| `violates` | the rule id it breaks |
| `shape` | e.g. an inner type in the domain shape that references a Spring type |
| `assertion` | the rule, evaluated over the fixture, reports a violation |

Because the fixture is in the test sources, the production rules are blind to it, and the
fixture-scoped evaluation is blind to production. The two cannot interfere.

---

## 6. Complexity Ceiling

| Field | Values |
|---|---|
| `check` | Checkstyle module name |
| `ceiling` | integer |
| `meaning` | what the metric counts |
| `measured max` | current worst value |
| `where` | the worst offender |

| check | ceiling | measured max | where | meaning |
|---|---|---|---|---|
| `CyclomaticComplexity` | 10 | 7 | `Participant:16` | independent paths through a method |
| `NPathComplexity` | 30 | 27 | `Participant:16` | acyclic execution paths |
| `MethodLength` | 70 | 65 | `SlotRepositoryConcurrencyIT:63` | lines in a method |
| `ParameterNumber` | 7 | 7 | `Meeting:29`, `Meeting:61` | parameters of a method/constructor |

**Validation rules**

- `ceiling` MUST be greater than or equal to `measured max`, with no `SuppressionFilter`, no
  baseline file, and no `// CHECKSTYLE:OFF` (FR-018).
- The ceilings MUST live in the existing ruleset so they run in the tasks already in `check`.
- Evaluated-and-excluded checks are recorded in `research.md` D10 with their measured maxima; they
  are not silently omitted.

---

## 7. Mutation Scope

| Field | Value | Rationale |
|---|---|---|
| `tool` | PIT via `info.solidsoft.pitest` 1.19.0 | see research D9 |
| `testPlugin` | JUnit 5 (`junit5PluginVersion = 1.2.3`) | the suite is JUnit 5 |
| `targetClasses` | `com.minidoodle.calendar.domain.*` | the only layer with fast unit tests |
| `targetTests` | `com.minidoodle.calendar.domain.*` | avoid container startup per mutant |
| `outputFormats` | HTML, XML, CSV | per-package and overall score (FR-019) |
| `threshold` | none | equivalent mutants make a threshold meaningless (FR-021 is triage) |
| `in check` | **no** | FR-020: must not affect the standard build's duration |

**Validation rules**

- The `pitest` task MUST NOT be reachable from `check` or `build`.
- Mutation output MUST be browsable per package (FR-019) and MUST identify survivors by location
  (FR-021).
- Report output MUST be non-timestamped so paths are stable across runs.

---

## 8. Exclusion

| Field | Description |
|---|---|
| `what` | the code or rule being kept out of a measurement |
| `why` | the justification |
| `scoped to` | report, verification, both, or a specific check |

| what | why | scoped to |
|---|---|---|
| `com.minidoodle.jooq.**` | machine-generated jOOQ output, not ours to cover (FR-004) | report **and** verification |
| `ClassFanOutComplexity`, `ClassDataAbstractionCoupling`, `ExecutableStatementCount`, `JavaNCSS` | measured maxima make them either red today or redundant (research D10) | Checkstyle |
| application-layer framework imports | the application legitimately uses Spring; the constitution's framework-free rule covers the domain | ArchUnit (A1 scope) |
| application/adapter packages in mutation | container startup cost per mutant | `pitest` scope |

**Validation rule**: every exclusion MUST be listed here with a reason. The set of exclusions is a
reviewable artifact, not an incidental detail — "no suppression file" is a success criterion, and
this table is what replaces it.

---

## Relationships

```text
CoverageDimension ──enforced by──> ViolationRule (BUNDLE scope)
                                          │
                                          └── CLASS scope ──> names ──> error-handling types
                                                                             ▲
GapClosingTest ──executes──> target code ────────────────────────────────────┘
GapClosingTest ──raises──> coverage ──> allows CLASS floor to be set

ArchitectureRule ──proved by──> NegativeControlFixture
ComplexityCeiling ──lives in──> existing Checkstyle ruleset ──runs in──> `check`
MutationScope ──deliberately outside──> `check`
Exclusion ──narrows──> every measurement above
```

## Lifecycle

1. **Baseline** (done): measure the six counters and the complexity maxima.
2. **Close gaps**: add the eight `GapClosingTest` entries; re-measure.
3. **Set floors**: bundle floors from §1, class floors from §2 (post-gap values).
4. **Enforce**: wire verification into `check`; add architecture rules and complexity ceilings.
5. **Ratchet**: floors may be raised when coverage rises, never lowered to accommodate a regression.
