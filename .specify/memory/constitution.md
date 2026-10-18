<!--
Sync Impact Report
==================
Version change: 1.0.0 (initial ratification, 2026-10-15) → 1.1.0
Bump rationale: MINOR — the locked Technology Stack table gains the load/performance
  testing tooling that feature 007 (k6 load test) introduces, and the workflow section
  states explicitly that load testing is deliberate and off the critical path. No
  existing principle, decision, or gate is removed or redefined.

Modified principles: none

Added sections: none

Amended sections:
- Technology Stack (Locked): two rows added — "Load testing" (k6, run locally through a
  docker-compose profile, results exported to Prometheus/Grafana) and "DB-side metrics"
  (postgres_exporter sidecar → Prometheus).
- Development Workflow & Quality Gates: one bullet added — load/performance testing is
  manual, opt-in, and MUST NOT be wired into check, build, CI, or the pre-push hook.

Removed sections: none

Templates requiring updates:
- .specify/templates/plan-template.md  ✅ no change (Constitution Check is derived at runtime)
- .specify/templates/spec-template.md   ✅ no change
- .specify/templates/tasks-template.md  ✅ no change (the off-critical-path rule is stated here
  and in AGENTS.md, and is enforced by not wiring anything into check/build)
- .opencode/commands/speckit.*.md       ✅ no change (constitution loaded at runtime)

Runtime guidance updated:
- AGENTS.md / README.md gain the load-test run procedure and the "never on the critical path"
  rule in the same feature (007) that adds the tooling.

Follow-up TODOs: none
-->

# Mini Doodle Constitution

## Core Principles

### I. Domain-Centric Hexagonal Architecture (NON-NEGOTIABLE)

The domain model is the center of the system and the single owner of business rules.

- The domain layer (aggregates, entities, value objects, domain services) MUST NOT depend
  on frameworks or I/O. No Spring, Jakarta, or jOOQ types in domain code.
- Dependencies point inward only: inbound adapters and outbound adapters depend on the
  application/domain; the domain depends on nothing.
- Application services define ports (interfaces) for required side effects; adapters
  implement them (inbound: REST controllers; outbound: jOOQ repositories).
- Business invariants (e.g., a slot cannot be booked twice, status transitions) MUST be
  enforced by aggregates, not spread across controllers or SQL.
- Package root is `com.minidoodle`, organized by bounded context and then by
  `domain` / `application` / `adapter`.

Rationale: The task is judged on design and tech-decision quality. An explicit domain
core keeps rules testable in isolation and prevents persistence concerns from leaking into
business logic.

### II. Database as Source of Truth (Constraints First)

Correctness is enforced by the database first; the application is defense-in-depth.

- All schema changes MUST be versioned Flyway migrations. Hibernate/jOOQ schema
  auto-generation is forbidden; schema is validated against migrations, never mutated by code.
- Data integrity MUST be expressed as database constraints wherever possible: NOT NULL,
  foreign keys, CHECK constraints, unique/partial indexes.
- Slot overlap MUST be prevented at the database level via a PostgreSQL exclusion
  constraint over a `tstzrange` (GIST). Application-level checks are additional, not primary.
- Any business-critical race (e.g., booking the same free slot concurrently) MUST be
  resolved by the database constraint, not trusting a read-then-write in application code.

Rationale: Under hundreds of users and thousands of slots, application-only checks are
race-prone and slow. DB-level constraints make double-booking structurally impossible and
let the database optimize range queries.

### III. Test Discipline with Real Dependencies

Behavior is verified against realistic dependencies, and green tests are a merge gate.

- Domain logic MUST be covered by fast unit tests (JUnit 5 + AssertJ).
- Persistence and constraint behavior MUST be verified with integration tests against a
  real PostgreSQL via Testcontainers — not an in-memory substitute.
- HTTP behavior MUST be verified with MockMvc contract tests for each endpoint.
- Tests MUST pass in CI before merge; skipped, ignored, or flaky tests are not acceptable.
- Test-first is encouraged for domain rules, where the invariant is known up front.

Rationale: The overlap/exclusion design only proves itself against a real PostgreSQL.
Testcontainers removes the "works on H2" class of false confidence.

### IV. Simplicity & YAGNI at Honest Scale

Use the simplest architecture that meets the real scale — and say so.

- The service is a single deployable Gradle module using standard Spring MVC (blocking,
  servlet). Reactive stacks (WebFlux) MUST NOT be introduced without measured, documented need.
- No microservices, message brokers, or distributed infrastructure for this scope.
- A new abstraction, layer, or dependency MUST be justified by a current concrete need.
- Any deviation from these principles MUST be recorded in the plan's Complexity Tracking
  table with the simpler alternative that was rejected.

Rationale: The brief values honest engineering judgment. Claiming reactive/microservice
complexity at this scale is a negative signal; a clean monolith with a strong domain is the
credible choice.

### V. Observable & Documented Service

The running service exposes its health, metrics, and API contract.

- Spring Boot Actuator MUST expose health and Prometheus metrics (Micrometer).
- Domain operations and failures MUST emit structured logs suitable for debugging.
- The HTTP API MUST be described with springdoc-openapi, with Swagger UI enabled, and kept
  in sync with implemented behavior.
- README.md MUST document how the service is consumed (build, run, endpoints, examples).
  A change to the API surface MUST update documentation in the same change.

Rationale: The brief lists metrics and documentation as a plus, and a reviewer must be able
to run and exercise the service without reading source.

## Technology Stack (Locked)

These choices are fixed for this project. Changing any of them requires a constitution
amendment (see Governance).

| Concern | Decision |
| --- | --- |
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.5.x |
| Build | Gradle with Kotlin DSL + wrapper (`./gradlew`) |
| Architecture | Hexagonal (ports & adapters) with DDD tactical patterns |
| Persistence | jOOQ over PostgreSQL (SQL-level control) |
| Migrations | Flyway (versioned SQL) |
| Overlap/concurrency | PostgreSQL `tstzrange` + GIST exclusion constraint |
| API | Spring MVC, JSON REST |
| Auth | Demo only: `X-User-Id` header identifies the caller; no JWT/OAuth |
| Testing | JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL) |
| Load testing | k6, run on demand through a `loadtest` docker-compose profile; results exported to the existing Prometheus/Grafana via remote write |
| DB-side metrics | postgres_exporter sidecar → Prometheus (connections, sessions, locks, transactions) |
| Observability | Actuator + Micrometer → Prometheus; structured logging |
| API docs | springdoc-openapi + Swagger UI |
| Packaging | Multi-stage Dockerfile (build → JRE) |
| Local runtime | docker-compose (app + PostgreSQL) |
| CI | GitHub Actions: build + tests on pull requests |
| Time handling | `OffsetDateTime` in UTC; calendar/slot intervals as `tstzrange` |
| Package root | `com.minidoodle` |

## Development Workflow & Quality Gates

- Commits MUST follow Conventional Commits (see `docs/CONVENTIONAL_COMMITS.md` and
  `AGENTS.md`). Types are validated; history stays meaningful for reviewer assessment.
- Work is spec-driven: features flow through `/speckit.specify` → `/speckit.plan` →
  `/speckit.tasks` → `/speckit.implement`. The plan MUST include a Constitution Check and
  pass it (or justify violations) before implementation.
- Local full-stack run MUST work via `docker-compose up` with all dependencies declared.
- CI MUST build and run the test suite on pull requests; merge requires green CI.
- Load and performance testing is deliberate and manual: it runs via the `loadtest`
  docker-compose profile and MUST NOT be wired into `check`, `build`, CI, or the pre-push
  hook, so it can never slow or block a push.
- Quality gates: compiles, unit tests pass, integration tests pass (Testcontainers),
  API documented, README consumption steps accurate.

## Governance

- This constitution supersedes other development practices. Where a practice conflicts,
  the constitution wins; conflicts MUST be raised and resolved, not silently ignored.
- Amendments are made by proposing a change to `.specify/memory/constitution.md` with
  documented rationale and a version bump; the change is reviewed like any other.
- Versioning follows semantic versioning of governance:
  - MAJOR: backward-incompatible removal or redefinition of a principle.
  - MINOR: a new principle or section, or materially expanded guidance.
  - PATCH: clarifications, wording, or non-semantic refinements.
- Every plan and task list MUST verify compliance with these principles. Deviations are
  permitted only when recorded in the plan's Complexity Tracking table with justification.
- Runtime development guidance lives in `AGENTS.md`, `README.md`, and `docs/`.

**Version**: 1.1.0 | **Ratified**: 2026-10-15 | **Last Amended**: 2026-10-18
