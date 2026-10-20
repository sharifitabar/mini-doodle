# AGENTS.md

## Documentation language

- Write all project documentation in ASD-STE100 Simplified Technical English (STE), Issue 9.
  This rule applies to README files, architecture decision records (ADRs), specifications,
  plans, research, task lists, procedures, and agent instructions.
- Before you write or change documentation, read [the writing rules](docs/WRITING.md).
  Examine each changed passage against those rules before you finish.
  Use the rules and dictionary in [the local Issue 9 PDF](docs/ASD-STE100_ISSUE9.pdf).
- Preserve the meaning, requirement strength, scope, decisions, and technical facts.
  Keep commands, code, identifiers, links, requirement IDs, and recorded input unchanged.
- Use the STE dictionary and the project's technical terms. Use one term for each concept.
  Make sure that word meanings and parts of speech agree with the PDF.
  If you cannot resolve a word or rule, report the limitation. Do not claim full STE compliance
  from a readability score or an automated word check.

## Building, testing, running

- This service uses Java 21 and Spring Boot. The Gradle toolchain specifies Java 21.
  It uses hexagonal architecture in `com.minidoodle.calendar.{domain,application,adapter}`
  and `com.minidoodle.shared`. The build generates jOOQ sources in `build/generated-sources`.
  Do not edit generated sources.
- Start Docker before you run `./gradlew build`. Integration tests use Testcontainers.
  The jOOQ generator starts a `postgres:16` container unless `JOOQ_CODEGEN_URL` identifies
  an existing database.
- Run `./gradlew test` for the test suite. Run `./gradlew build` for code generation,
  tests, the boot jar, and all gates below.
- Run `docker compose up --build` to start the local stack.
  The app uses `http://localhost:8080`. Health is at `/actuator/health`.
  OpenAPI is at `/swagger-ui.html`.

## Approval before committing or pushing

- Run `git commit` only after the user explicitly approves a commit for the current change.
  Run `git push` only after the user explicitly approves a push for the current change.
- First, make the change and do the necessary checks. Then stop and ask for approval.
  Wait for an explicit yes.
- Approval applies only to the current change. Commit approval does not include push approval.
- Commit or push only the approved changes. Keep unrelated work separate.
- Before a push, run `./gradlew build --no-daemon --console=plain` locally.
  Push only if the full CI build passes.
- The versioned `.githooks/pre-push` hook runs this build and blocks a push if it fails.
  Enable it once per clone with `git config core.hooksPath .githooks`.

## Style gate

The standard `check` lifecycle includes lint and format checks.
The pre-push hook and CI enforce these checks through `./gradlew build`.
Do not add a separate hook or CI step for style.

- **Before you ask to commit**, run `./gradlew spotlessApply`.
  Then run `./gradlew check` and make sure that it passes. Do not push if the gate fails.
- Spotless controls formatting. It uses google-java-format's **AOSP** variant:
  4-space indentation, a 100-column limit, import order, and removal of unused imports.
  Checkstyle controls naming, braces, blocks, and bug patterns in
  `config/checkstyle/checkstyle.xml`. Use `spotlessApply` to correct formatting errors.
- Both tools exclude generated jOOQ sources in `build/generated-sources`.
  Keep this exclusion. Do not format these sources manually.
- `build.gradle.kts` specifies the tool versions. Change these versions only for a specific reason.
  The maximum google-java-format version is `1.36.0`.
  Version 1.37.0 breaks Spotless's reflection code.
  See `specs/005-checkstyle-enforcement/research.md`.

## Quality gates

The four quality gates use the same `check` lifecycle as style.
`./gradlew build` is the single enforcement point.
Do not add a separate hook or CI step for these gates.

- **Coverage floors**: JaCoCo checks coverage through `jacocoTestCoverageVerification`.
  Production classes have bundle floors of 0.95 for line, branch, method, and complexity coverage.
  `GlobalExceptionHandler`, `JooqSlotRepository`, and `CurrentUserArgumentResolver`
  also have class rules. A bundle average can hide their error and recovery paths.
  **Do not lower a floor to make a change pass.** Add coverage instead.
  A deliberate floor change must include the new measurement in the same commit.
  Round the measured ratio down to the nearest multiple of 0.05 to calculate a floor.
  Run `./gradlew test jacocoTestReport` before you change a floor.
- **Architecture rules**: ArchUnit rules are in
  `src/test/java/com/minidoodle/architecture/ArchitectureRules.java`.
  They run as tests. A layer violation fails `./gradlew test`.
  Each rule also has a test that proves it fails against a fixture with a deliberate violation.
  Keep this test when you add a rule.
  Fixtures are in `com.minidoodle.architecture.fixture`.
  The package-cycle pair is in `com.minidoodle.calendar.fixture.cyclic`.
  Keep test-only controllers **outside** `com.minidoodle`.
  Otherwise, the application's component scan finds them.
- **Complexity ceilings**: Checkstyle has four metric checks in
  `config/checkstyle/checkstyle.xml`. There is no suppression file or baseline.
  Change the code if it exceeds a ceiling.
- **Mutation testing**: Run `./gradlew pitest` **only on demand**.
  Never add it to `check` or `build`. It must not slow or block a push.
  It has no threshold. Review surviving mutations to find possible test gaps.

Technical constraints:

- Keep ArchUnit at `1.4.1` unless you examine the resolved `junit-platform-*` versions again.
  ArchUnit 1.5.x uses `junit-platform-engine` 1.14.4.
  It breaks the Gradle test worker with Spring Boot's managed version 1.12.2.
  The error is `NoClassDefFoundError: org/junit/platform/engine/OutputDirectoryCreator`.
- `build.gradle.kts` documents the accepted coverage gap beside the class rule for
  `JooqSlotRepository`. The gap is its rethrow after a delete failure that is not a foreign-key error.
  Do not add a mocking framework to cover it. This project tests integrations first.
- See `specs/006-quality-gates/research.md` for measurements, rounding, and the decision record.
  The as-built addendum records the implemented results.

## Commit Conventions

All commits MUST obey [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

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

- The load test harness is in `loadtest/`. It contains SQL, k6 scripts, run scripts, and results.
  Keep it separate from build and CI paths. Never reference it in `build.gradle.kts`,
  `.github/workflows/*`, `.githooks/*`, or other build and CI paths.
  `./gradlew build` must not depend on it.
- Run load tests only locally. Use `./loadtest/run.sh --smoke` for a check of component connections.
  Use `./loadtest/run.sh` for a full run. Never run load tests in CI or on a schedule.
- Before you commit harness or Compose changes, run `./gradlew spotlessApply`, then `./gradlew check`.
  The checks must pass. This feature is not expected to change the production schema or Gradle.
- Service, schema, and connection-pool adjustments to meet targets are outside this feature's scope.
  Report a failed run as a measurement. Do not optimize the service as part of this feature.
- If `http_server_requests_seconds_bucket` is missing, enable histogram buckets with
  `management.metrics.distribution.percentiles-histogram.http.server.requests: true`
  in `src/main/resources/application.yml`.
  This is the only production-file change considered for this feature.
