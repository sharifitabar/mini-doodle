# Implementation Plan: Meeting Scheduling

**Branch**: `002-meeting-scheduling` | **Date**: 2026-10-16 | **Spec**: `specs/002-meeting-scheduling/spec.md`

**Input**: Feature specification from `/specs/002-meeting-scheduling/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Implement meeting scheduling as the P2 slice of mini-doodle: a user converts one of their
available (free) time slots into a meeting with a title, an optional description, and one or
more participants; views the meeting for a slot; and cancels it, which frees the slot again.
The technical approach extends the existing `calendar` bounded context: a framework-free domain
aggregate (`Meeting` + `Participant` value objects), a new `meetings` table linked one-to-one to
`time_slots` with a `UNIQUE (slot_id)` constraint that makes double-booking structurally
impossible, and a `meeting_participants` child table (free-form name/email, no user account
required). Booking is a single transaction that creates the meeting and marks its slot BUSY;
slot modify/delete/status-change operations are rejected with a conflict while a meeting exists
(deletion additionally protected by a `RESTRICT` foreign key). The HTTP surface adds three
slot-scoped endpoints (book, view, cancel), consistent with the existing `X-User-Id` demo auth
and RFC 7807 error model.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.5.x (Spring MVC, Bean Validation), jOOQ, Flyway,
springdoc-openapi, Micrometer + Spring Boot Actuator

**Storage**: PostgreSQL 16 (existing `btree_gist` extension; no new extension needed)

**Testing**: JUnit 5, AssertJ, MockMvc, Testcontainers (PostgreSQL)

**Target Platform**: Linux container (JVM) for local `docker-compose`; developer machines via
`./gradlew bootRun`

**Project Type**: web-service (single deployable Gradle module)

**Performance Goals**: Booking a meeting completes with p99 ≤ 200 ms (SC-001); retrieving a
meeting completes with p99 ≤ 300 ms even with thousands of slots per user (SC-004).

**Constraints**: At most one meeting per slot, enforced by a database `UNIQUE (slot_id)`
constraint so concurrent bookings cannot double-book (SC-002); a slot's meeting cannot be
orphaned by slot mutation (FR-010); all schema changes via Flyway; timestamps stored in UTC and
exchanged with explicit offset (FR-015).

**Scale/Scope**: Hundreds of users, thousands of slots per user. Adds one aggregate (`Meeting`)
and three endpoints; availability querying remains out of scope (separate feature).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Domain-Centric Hexagonal Architecture | New `Meeting`/`Participant` domain types are framework-free; booking rules live in the application service + aggregate; jOOQ only in the persistence adapter | PASS |
| II. Database as Source of Truth | One-meeting-per-slot is a DB `UNIQUE (slot_id)` constraint; slot deletion with a meeting is blocked by a `RESTRICT` FK; schema via Flyway `V2` | PASS |
| III. Test Discipline with Real Dependencies | Domain unit tests for meeting/participant invariants; Testcontainers ITs for the unique/FK constraints and concurrent booking; MockMvc contract tests per endpoint | PASS |
| IV. Simplicity & YAGNI at Honest Scale | Reuse the existing single module and bounded context; no new infrastructure or abstraction beyond one outbound port (`MeetingRepository`) | PASS |
| V. Observable & Documented Service | Add meeting metrics/structured logs; extend the OpenAPI contract, Swagger UI, and README in the same change | PASS |

No violations. `Complexity Tracking` is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-meeting-scheduling/
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

New and changed files (existing structure from `specs/001-time-slot-management/plan.md` is
reused; `+` = new, `~` = changed):

```text
src/main/java/com/minidoodle/calendar/
├── domain/
│   ├── model/
│   │   ├── Meeting.java              (+) aggregate root: title, description, participants
│   │   ├── MeetingId.java            (+) UUID value object
│   │   └── Participant.java          (+) value object: name and/or email (at least one)
│   └── exception/
│       ├── MeetingNotFoundException.java   (+) 404 when a slot has no meeting
│       └── MeetingConflictException.java   (+) 409: slot unavailable / holds a meeting
├── application/
│   ├── port/in/
│   │   ├── BookMeetingUseCase.java   (+) command + use case (slotId, title, description, participants)
│   │   ├── GetMeetingUseCase.java    (+) use case (by slot)
│   │   └── CancelMeetingUseCase.java (+) use case (by slot)
│   ├── port/out/
│   │   └── MeetingRepository.java    (+) save, findBySlotId, existsBySlotId, deleteBySlotId
│   └── service/
│       ├── MeetingApplicationService.java (+) @Transactional: book, get, and cancel; sets slot BUSY/FREE
│       ├── SlotApplicationService.java    (~) reject slot update/delete when the slot holds a meeting (409)
│       └── MeetingMetrics.java            (+) booked/viewed/cancelled counters + timers
└── adapter/
    ├── in/web/
    │   ├── MeetingController.java    (+) POST/GET/DELETE /api/v1/slots/{slotId}/meeting
    │   ├── BookMeetingRequest.java   (+) validation (title, description, participants)
    │   ├── ParticipantRequest.java   (+) name/email fields
    │   ├── MeetingResponse.java      (+)
    │   └── MeetingWebMapper.java     (+)
    └── out/persistence/
        ├── JooqMeetingRepository.java (+) implements MeetingRepository
        └── MeetingPersistenceMapper.java (+)

src/main/resources/db/migration/
└── V2__meetings.sql                  (+) meetings + meeting_participants + constraints

src/test/java/com/minidoodle/calendar/
├── domain/                           (+) MeetingTest, ParticipantTest
├── adapter/out/persistence/          (+) MeetingRepositoryIT (unique/FK), MeetingBookingConcurrencyIT
└── adapter/in/web/                   (+) MeetingControllerBookIT, MeetingControllerReadIT,
                                          MeetingControllerCancelIT, SlotMeetingGuardIT
```

**Structure Decision**: Single Gradle module; extend the existing `calendar` bounded context
rather than introducing a new one, because meetings are tightly coupled to slots (one meeting
per slot, owned through the same calendar). This keeps hexagonal layering (constitution I) and
avoids a speculative second context (constitution IV).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations were identified; this section is intentionally empty.

## Phase 0 / Phase 1 Artifacts

- [research.md](./research.md) — resolved technical decisions and rejected alternatives.
- [data-model.md](./data-model.md) — entities, schema, constraints, validation rules.
- [contracts/openapi.yaml](./contracts/openapi.yaml) — HTTP contract for the meeting API.
- [quickstart.md](./quickstart.md) — build, run, and verify steps.

## Post-Design Constitution Check

Re-evaluated after Phase 1 design: the schema (data-model.md) encodes one-meeting-per-slot and
slot-deletion protection as database constraints (II); contracts add only the three required
slot-scoped operations and stay within the single module (I, IV); observability and docs remain
in scope (V). No new violations. Gate: PASS.

## Open Items

- None blocking. Spec clarifications resolved on 2026-10-16 (participants are free-form, not
  validated as users; cancellation in scope; slot operations on a meeting's slot are rejected
  as conflicts). The agent-context update step is a no-op here: this repository has no
  `update-agent-context.sh` and manages `AGENTS.md` manually.
