# Implementation Plan: Availability Query

**Branch**: `003-availability-query` | **Date**: 2026-10-16 | **Spec**: `specs/003-availability-query/spec.md`

**Input**: Feature specification from `/specs/003-availability-query/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Implement the aggregated availability query as the P3 slice of mini-doodle: a user requests
their free/busy availability over a bounded time frame and receives a single, chronologically
ordered list of labeled intervals — FREE (declared availability), BUSY (slots explicitly marked
busy plus slots occupied by a meeting), and UNALLOCATED (frame time covered by no slot) — that
together tile the frame exactly. The technical approach extends the existing `calendar` bounded
context with **no new persisted data**: a framework-free domain value type (`TimeFrame`,
`SlotOccupancy`, `AvailabilityInterval`, `AvailabilityKind`) and a pure `AvailabilityCalculator`
domain service merge the caller's slots into coalesced intervals; a new read-only outbound port
(`AvailabilityRepository`) loads slot occupancies for the caller's calendar overlapping the frame
in a single `time_slots LEFT JOIN meetings` range query; and a new `GET /api/v1/availability`
endpoint returns the result. The `X-User-Id` demo auth and RFC 9457 error model are reused.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.5.x (Spring MVC, Bean Validation), jOOQ, Flyway, springdoc-openapi, Micrometer + Spring Boot Actuator

**Storage**: PostgreSQL 16 (existing `time_slots` and `meetings` tables; read-only, **no new migration**)

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL)

**Target Platform**: Linux container (JVM) for local `docker-compose`; developer machines via `./gradlew bootRun`

**Project Type**: web-service (single deployable Gradle module)

**Performance Goals**: Aggregated availability completes with p99 ≤ 300 ms (SC-001) even with thousands of slots per user (SC-005).

**Constraints**: The requested frame is required and bounded by a maximum span (FR-002); the query is strictly read-only (FR-010) and scoped to the caller's own calendar (FR-008); intervals are half-open `[start, end)`, coalesced, disjoint, and cover the frame exactly (FR-006); times are UTC, microsecond precision (FR-013).

**Scale/Scope**: Hundreds of users, thousands of slots per user. Adds one read endpoint, one read port, and a framework-free availability calculator (free/busy/unallocated); no schema change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Domain-Centric Hexagonal Architecture | Interval coalescing, clipping, and free/busy classification live in a framework-free domain calculator (`domain/service`) and value types; the controller and jOOQ adapter stay thin | PASS |
| II. Database as Source of Truth | No schema change; the read uses existing constraints/indexes (`ix_time_slots_range`, `ix_time_slots_calendar_start`) and joins the authoritative `meetings` rows to decide occupancy | PASS |
| III. Test Discipline with Real Dependencies | Domain unit tests for merging/clipping/gaps; a Testcontainers IT for the range + meeting join and ownership isolation; a MockMvc contract test for the endpoint and error codes | PASS |
| IV. Simplicity & YAGNI at Honest Scale | Reuse the single module and `calendar` context; one new read port (justified by the slot↔meeting join); no new infrastructure, migration, or dependency | PASS |
| V. Observable & Documented Service | Add an availability counter/timer and structured log; extend the OpenAPI contract, Swagger UI, and README in the same change | PASS |

No violations. `Complexity Tracking` is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-availability-query/
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

New and changed files (the existing structure from `specs/001-time-slot-management/plan.md` is
reused; `+` = new, `~` = changed):

```text
src/main/java/com/minidoodle/calendar/
├── domain/
│   ├── model/
│   │   ├── TimeFrame.java             (+) bounded half-open [start, end) window + max-span rule
│   │   ├── SlotOccupancy.java         (+) slot range + status + holdsMeeting; isBusy() rule
│   │   ├── AvailabilityKind.java      (+) enum FREE | BUSY | UNALLOCATED
│   │   ├── AvailabilityInterval.java  (+) labeled half-open interval [start, end)
│   │   └── Availability.java          (+) result: frame + ordered disjoint intervals
│   ├── service/
│   │   └── AvailabilityCalculator.java (+) coalesce/clip/classify → free/busy/unallocated
│   └── exception/
│       └── InvalidTimeFrameException.java (+) 400: missing/empty/oversized/inverted frame
├── application/
│   ├── port/in/
│   │   ├── AvailabilityQuery.java     (+) from/to request
│   │   └── GetAvailabilityUseCase.java (+) getAvailability(caller, query) → Availability
│   ├── port/out/
│   │   └── AvailabilityRepository.java (+) findOccupancies(calendarId, frame) → List<SlotOccupancy>
│   └── service/
│       ├── AvailabilityApplicationService.java (+) resolve caller → load → calculate, timed
│       └── AvailabilityMetrics.java   (+) queries counter + query timer + structured log
├── adapter/
    ├── in/web/
    │   ├── AvailabilityController.java        (+) GET /api/v1/availability
    │   ├── AvailabilityResponse.java          (+)
    │   ├── AvailabilityIntervalResponse.java  (+)
    │   └── AvailabilityWebMapper.java         (+)
    └── out/persistence/
        └── JooqAvailabilityRepository.java    (+) LEFT JOIN time_slots × meetings over the frame

src/main/java/com/minidoodle/shared/web/
└── GlobalExceptionHandler.java        (~) InvalidTimeFrameException → 400; missing query param → 400

src/test/java/com/minidoodle/calendar/
├── domain/                            (+) AvailabilityCalculatorTest, TimeFrameTest, AvailabilityIntervalTest, SlotOccupancyTest
├── adapter/out/persistence/           (+) AvailabilityRepositoryIT (range + meeting join + isolation)
└── adapter/in/web/                    (+) AvailabilityControllerIT (contract, validation, auth)
```

**Structure Decision**: Single Gradle module; extend the existing `calendar` bounded context
rather than adding a new one, because availability is a projection over the same slots and meetings
the context already owns. This keeps hexagonal layering (constitution I) and avoids a speculative
second context (constitution IV).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations were identified; this section is intentionally empty.

## Phase 0 / Phase 1 Artifacts

- [research.md](./research.md) — resolved technical decisions and rejected alternatives.
- [data-model.md](./data-model.md) — domain types, classification rules, and the read query.
- [contracts/openapi.yaml](./contracts/openapi.yaml) — HTTP contract for the availability endpoint.
- [quickstart.md](./quickstart.md) — build, run, and verify steps.

## Post-Design Constitution Check

Re-evaluated after Phase 1 design: the query is read-only with no schema change (II); coalescing,
clipping, and classification are pure domain logic with unit tests (I, III); a single justified read
port serves the slot↔meeting join (IV); observability and docs remain in scope (V). No new
violations. Gate: PASS.

## Open Items

- None blocking. The response-shape and maximum-frame-span interpretations are recorded as decisions
  in [research.md](./research.md) and [data-model.md](./data-model.md) (aggregation = merged
  free/busy/unallocated intervals; max frame span = 31 days, a domain constant). The agent-context
  update step is a no-op here: this repository has no `update-agent-context.sh` and manages
  `AGENTS.md` manually.
