# Implementation Plan: Time Slot Management

**Branch**: `001-time-slot-management` | **Date**: 2026-10-15 | **Spec**: `specs/001-time-slot-management/spec.md`

**Input**: Feature specification from `/specs/001-time-slot-management/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Implement time slot management as the P1 slice of mini-doodle: users create, list,
inspect, modify, delete, and toggle the free/busy status of time slots in their personal
calendar. The technical approach follows the ratified constitution: a framework-free
domain core (hexagonal architecture) implemented with Spring Boot 3.5.x + Java 21, jOOQ
over PostgreSQL, Flyway migrations, and a database-level `tstzrange` exclusion constraint
that makes overlapping slots impossible — including under concurrent requests. The HTTP
surface is a small JSON REST API consumed by an external client that identifies itself via
an `X-User-Id` header (demo auth).

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.5.x (Spring MVC, Bean Validation), jOOQ,
Flyway, springdoc-openapi, Micrometer + Spring Boot Actuator

**Storage**: PostgreSQL 16 (requires the `btree_gist` extension for the exclusion
constraint)

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL)

**Target Platform**: Linux container (JVM) for local `docker-compose`; developer machines
via `./gradlew bootRun`

**Project Type**: web-service (single deployable Gradle module)

**Performance Goals**: Slot create completes with p99 ≤ 200 ms (SC-001). Listing a user's
slots completes with p99 ≤ 300 ms even with thousands of slots per user (SC-004; see
Deferred items).

**Constraints**: Overlaps MUST be rejected at the database level via a GIST exclusion
constraint over a `tstzrange`; all schema changes via Flyway; timestamps stored in UTC and
exchanged with explicit offset.

**Scale/Scope**: Hundreds of users, thousands of slots per user. Single user-facing
resource (`time slot`); no meeting/availability features in this slice.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Domain-Centric Hexagonal Architecture | Domain layer has no Spring/jOOQ/Jakarta types; dependencies point inward; `com.minidoodle` layout by context then `domain`/`application`/`adapter` | PASS |
| II. Database as Source of Truth | Flyway-only schema; overlap prevented by `tstzrange` + GIST exclusion constraint; no schema auto-generation | PASS |
| III. Test Discipline with Real Dependencies | Domain unit tests (JUnit 5/AssertJ), persistence + constraint tests against real PostgreSQL via Testcontainers, MockMvc contract tests; green CI gate | PASS |
| IV. Simplicity & YAGNI at Honest Scale | Spring MVC (blocking), single module, no broker/cache/microservices; abstractions limited to ports with a current need | PASS |
| V. Observable & Documented Service | Actuator health + Prometheus metrics, structured logs, springdoc OpenAPI UI, README consumption steps | PASS |

No violations — Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-time-slot-management/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
build.gradle.kts
settings.gradle.kts
gradlew, gradlew.bat, gradle/wrapper/
docker-compose.yml
Dockerfile

src/main/java/com/minidoodle/
├── MiniDoodleApplication.java
├── shared/
│   ├── config/                 # jOOQ, OpenAPI, Jackson, web config
│   └── web/                    # error handling (ProblemDetail), current-user resolution
└── calendar/                   # bounded context
    ├── domain/                 # framework-free core
    │   ├── model/              # Calendar, TimeSlot, SlotId, TimeRange, SlotStatus, UserId
    │   ├── exception/          # domain exceptions (overlap, invalid duration, not found, forbidden)
    │   └── service/            # domain rules (slot creation/modification invariants)
    ├── application/
    │   ├── port/in/            # use-case interfaces (CreateSlot, ListSlots, GetSlot, UpdateSlot, DeleteSlot)
    │   ├── port/out/           # SlotRepository, CalendarRepository
    │   └── service/            # use-case implementations (transaction boundary)
    └── adapter/
        ├── in/web/             # SlotController, request/response DTOs, mappers
        └── out/persistence/    # JooqSlotRepository, JooqCalendarRepository, row mappers

src/main/resources/
├── application.yml
└── db/migration/               # Flyway: V1__init.sql (btree_gist, users, calendars, time_slots)

src/test/java/com/minidoodle/
├── calendar/domain/            # pure unit tests
├── calendar/adapter/persistence/  # Testcontainers integration tests (constraint behavior)
└── calendar/adapter/in/web/    # MockMvc contract tests
```

**Structure Decision**: Single Gradle module (constitution IV). A single bounded context
`calendar` holds domain, application ports/services, and inbound/outbound adapters, with a
small `shared` package for cross-cutting web/config. This satisfies hexagonal layering
(constitution I) without the cost of multi-module builds.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations were identified; this section is intentionally empty.

## Phase 0 / Phase 1 Artifacts

- [research.md](./research.md) — resolved technical decisions and rejected alternatives.
- [data-model.md](./data-model.md) — entities, schema, constraints, validation rules.
- [contracts/openapi.yaml](./contracts/openapi.yaml) — HTTP contract for the slot API.
- [quickstart.md](./quickstart.md) — build, run, and verify steps.

## Post-Design Constitution Check

Re-evaluated after Phase 1 design: the schema (data-model.md) encodes the overlap rule as
a database exclusion constraint (II); contracts expose only the required operations and
stay within the single module (I, IV); observability and docs remain in scope (V). No new
violations. Gate: PASS.

## Open Items

- None. SC-004/SC-005 were resolved in the spec clarifications (p99 ≤ 300 ms read target;
  5xx rate < 0.1%). FR-016 (unknown caller rejection) is covered by T022; the added
  concurrency, persistence-restart, and DST tests (T054–T056) close the coverage gaps
  flagged by the consistency analysis.
