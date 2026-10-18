# AGENTS.md

## Approval before committing or pushing

- NEVER run `git commit` or `git push` unless the user has explicitly approved it for the current work.
- Implement and verify first, then STOP and ask for approval (e.g. "shall I commit?"). Wait for an explicit yes.
- Approval is per-change; a previous approval does not carry over to later changes. Do not push without approval even if a commit was approved.
- When approval is given, commit and push exactly the approved changes — do not bundle unrelated work.
- Before pushing, run the full CI build locally (`./gradlew build --no-daemon --console=plain`) and confirm it passes. Never push with a red build.
- A versioned `.githooks/pre-push` hook runs that build and blocks a failing push. Enable it once per clone with `git config core.hooksPath .githooks`.

## Style gate

Linting and formatting are wired into the standard `check` lifecycle, so `./gradlew build`
(the same command the pre-push hook and CI run) already enforces them. Do not add a separate
hook or CI step for style.

- **Before asking to commit**, run `./gradlew spotlessApply` to auto-fix formatting, then
  `./gradlew check` to confirm the gate is green. A red gate is never pushed.
- Spotless owns formatting (indentation, wrapping, whitespace, import order, unused imports);
  Checkstyle owns naming/brace/block/bug-pattern rules in `config/checkstyle/checkstyle.xml`.
  Never "fix" a formatting complaint by hand-editing for style — use `spotlessApply`.
- Generated jOOQ sources under `build/generated-sources` are excluded from both tools; never add
  manual formatting there or lift the exclusion.
- Versions are pinned (`build.gradle.kts`). Do not bump Spotless, google-java-format, or
  Checkstyle casually: google-java-format is capped at `1.36.0` because 1.37.0 breaks Spotless's
  reflective glue (see `specs/005-checkstyle-enforcement/research.md`).

## Quality gates

Four gates ride the same `check` lifecycle as style, so `./gradlew build` is the single enforcement
point. Do not add a separate hook or CI step for any of them.

- **Coverage floors** (JaCoCo, verified by `jacocoTestCoverageVerification`). Bundle floors are
  line/branch/method/complexity ≥ 0.95 over production classes; `GlobalExceptionHandler`,
  `JooqSlotRepository`, and `CurrentUserArgumentResolver` carry stricter class-scoped rules because
  a bundle average hides the error/recovery paths. **Floors are never lowered to make a change
  pass.** Raise coverage instead, or change a floor deliberately in the same commit, quoting the
  fresh measurement. A floor is the achieved ratio rounded down to 0.05 — re-measure with
  `./gradlew test jacocoTestReport` before touching one.
- **Architecture rules** (ArchUnit, rules R1–R6 in `src/test/java/com/minidoodle/architecture/`).
  They run as ordinary tests; a layering violation fails `./gradlew test`. Every rule is also
  asserted to fail against a deliberately violating fixture — keep that property when adding a rule.
  The fixture tree is `com.minidoodle.architecture.fixture` (plus the package-cycle pair under
  `com.minidoodle.calendar.fixture.cyclic`). Test-only controllers must stay **outside**
  `com.minidoodle`, or the application's component scan will pick them up.
- **Complexity ceilings** (Checkstyle). Four metric checks in
  `config/checkstyle/checkstyle.xml`. There is no suppression file and no baseline: a ceiling that
  fails is fixed in the code, not excused.
- **Mutation testing** (`./gradlew pitest`) is **on demand only** and must never be wired into
  `check` or `build` — it must not slow or block a push. It has no threshold; survivors are a
  triage signal.

Notes that save time:

- ArchUnit is pinned to `1.4.1`. 1.5.x is compiled against `junit-platform-engine` 1.14.4 and breaks
  the Gradle test worker under Spring Boot's managed 1.12.2 (`NoClassDefFoundError:
  org/junit/platform/engine/OutputDirectoryCreator`). Do not bump it without re-checking the
  resolved `junit-platform-*` versions.
- The accepted coverage residue — `JooqSlotRepository`'s rethrow for a non-foreign-key delete
  failure — is documented beside its class-scoped rule in `build.gradle.kts`. Do not "fix" it by
  adding a mocking framework; the project is deliberately integration-first.
- Full measurements, the rounding rule, and the decision record are in
  `specs/006-quality-gates/research.md` (see its as-built addendum).

## Commit Conventions

All commits MUST follow [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

See [docs/CONVENTIONAL_COMMITS.md](docs/CONVENTIONAL_COMMITS.md) for full rules, types, and examples.

### Quick rules for agents

- Use format: `<type>[scope]: <imperative description>`
- Valid types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
- Mark breaking changes with `!` or `BREAKING CHANGE:` footer
- Keep description in imperative mood, no trailing period
- Add body/footer only when helpful (explain context, link issues)
- Infer type/scope from changed files when drafting
- Validate before proposing: type valid, format correct, imperative mood

### When asked to commit

1. Inspect staged/unstaged changes (`git diff --staged --stat`, `git diff --stat`)
2. Draft 1–3 candidate messages
3. Explain reasoning
4. Propose final message only after user confirmation (or as requested)
## Load testing (k6)

- The load test harness lives in `loadtest/` (SQL, k6 scripts, run scripts, results). It is off the
  critical path: never add references to `loadtest/` in `build.gradle.kts`, `.github/workflows/*`,
  `.githooks/*`, or other build/CI paths. `./gradlew build` must not be made to depend on it.
- Run locally only: `./loadtest/run.sh --smoke` for wiring, `./loadtest/run.sh` for a full run.
  Never run the load test in CI or on a schedule.
- Before committing changes that touch the harness or compose, the standard gate applies:
  `./gradlew spotlessApply` then `./gradlew check`. The gate must be green; no production schema
  or Gradle changes are expected from this feature.
- Tuning the service/schema/connection pool to hit targets is explicitly out of scope for this
  feature; report a red run as a finding (measurement, not optimisation).
- If the app's metrics are missing `http_server_requests_seconds_bucket`, enable histogram buckets
  via `management.metrics.distribution.percentiles-histogram.http.server.requests: true` in
  `src/main/resources/application.yml` (the only production-file change contemplated by the feature).
