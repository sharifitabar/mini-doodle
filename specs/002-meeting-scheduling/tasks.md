---
description: "Task list for Meeting Scheduling (002)"
---

# Tasks: Meeting Scheduling

**Input**: Design documents from `/specs/002-meeting-scheduling/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. Constitution Principle III (Test Discipline with Real Dependencies) makes tests a merge gate, so each user story carries its own tests.

**Organization**: Tasks are grouped by user story so each can be implemented and tested independently. This feature extends the existing `calendar` bounded context; there is no greenfield setup.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task includes an exact file path

## Path Conventions

Single Gradle module (established by 001). Production code under `src/main/java/com/minidoodle/calendar/`, tests under `src/test/java/com/minidoodle/calendar/`, resources under `src/main/resources/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing project already provides everything this feature needs

- [x] T001 Verify no new third-party dependencies are required (Spring MVC, Bean Validation, jOOQ, Flyway, springdoc, Micrometer are already present), record the decision in `build.gradle.kts` if needed, and run `./gradlew build` to confirm a green baseline

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain model, persistence port, and error mapping shared by all three stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Create Flyway migration `src/main/resources/db/migration/V2__meetings.sql` (`meetings` table with `uq_meetings_slot`, `ON DELETE RESTRICT` FK to `time_slots`, title `ck_*` checks; `meeting_participants` table with `(meeting_id, position)` PK, participant `ck_*` checks, `ON DELETE CASCADE`) per data-model.md
- [x] T003 Run jOOQ code generation and verify the `Meetings`/`MeetingParticipants` generated classes build under `build/generated-sources/jooq`; confirm no generated column is written by the app (depends on T002)
- [x] T004 [P] Implement value objects `MeetingId` and `Participant` (name and/or email, at least one present) in `src/main/java/com/minidoodle/calendar/domain/model/`
- [x] T005 Implement the `Meeting` aggregate root (framework-free; title and participant invariants) in `src/main/java/com/minidoodle/calendar/domain/model/` (depends on T004)
- [x] T006 [P] Define domain exceptions `MeetingNotFoundException` and `MeetingConflictException`, extending the existing `DomainException`, in `src/main/java/com/minidoodle/calendar/domain/exception/`
- [x] T007 [P] Define outbound port `MeetingRepository` (`save`, `findBySlotId`, `existsBySlotId`, `deleteBySlotId`) in `src/main/java/com/minidoodle/calendar/application/port/out/`
- [x] T008 Implement `JooqMeetingRepository` and `MeetingPersistenceMapper` in `src/main/java/com/minidoodle/calendar/adapter/out/persistence/`; translate SQLSTATE `23505` (unique slot) and `23503` (restricted delete) into `MeetingConflictException` (depends on T003, T007)
- [x] T009 [P] Expose/confirm a status-update operation on the existing `SlotRepository` (`src/main/java/com/minidoodle/calendar/application/port/out/SlotRepository.java` + `JooqSlotRepository`) used by booking (→ BUSY) and cancellation (→ FREE)
- [x] T010 [P] Extend `src/main/java/com/minidoodle/shared/web/GlobalExceptionHandler.java` to map `MeetingNotFoundException` → 404 and `MeetingConflictException` → 409 (RFC 7807) (depends on T006)
- [x] T011 Extend the Testcontainers base so `V2__meetings.sql` is applied in tests and add meeting tables/constraints to `src/test/java/com/minidoodle/SchemaSmokeTest.java`

**Checkpoint**: Foundation ready — user stories can begin

---

## Phase 3: User Story 1 - Book a meeting from an available slot (Priority: P1) 🎯 MVP

**Goal**: A caller converts a free slot into a meeting with a title, optional description, and participants; the slot becomes BUSY and cannot be double-booked.

**Independent Test**: Create a free slot, `POST /api/v1/slots/{slotId}/meeting`, confirm `201` and that the slot is now `BUSY`; a second booking of the same slot returns `409`.

### Tests for User Story 1 (write first, must fail before implementation) ⚠️

- [ ] T012 [P] [US1] Domain unit tests for `Meeting`/`Participant` invariants (blank/over-length title, no participants, participant with neither name nor email) in `src/test/java/com/minidoodle/calendar/domain/MeetingTest.java` and `src/test/java/com/minidoodle/calendar/domain/ParticipantTest.java`
- [ ] T013 [P] [US1] MockMvc contract test for `POST /api/v1/slots/{slotId}/meeting` (201, 400, 403, 404 foreign/missing slot, 409 on busy/already-booked slot) in `src/test/java/com/minidoodle/calendar/adapter/in/web/MeetingControllerBookIT.java`
- [ ] T014 [P] [US1] Persistence integration test: a second `save` for the same slot fails on `uq_meetings_slot`, and two parallel bookings yield exactly one success and one conflict in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/MeetingBookingConcurrencyIT.java` (covers FR-002, SC-002)
- [ ] T015 [P] [US1] Persistence integration test: booking marks the slot `BUSY`, participants persist in order, and deleting a booked slot is blocked by the `RESTRICT` FK in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/MeetingRepositoryIT.java` (covers FR-006, FR-010)

### Implementation for User Story 1

- [ ] T016 [P] [US1] Define inbound port `BookMeetingUseCase` and `BookMeetingCommand` (slotId, title, description, participants) in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [ ] T017 [US1] Implement `BookMeetingService` (resolve caller calendar, load slot, require FREE and no existing meeting, build aggregate, persist meeting, set slot BUSY in one transaction, translate constraint violations) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T005, T007, T008, T009, T016)
- [ ] T018 [P] [US1] Add web DTOs `BookMeetingRequest`, `ParticipantRequest`, `MeetingResponse`, and `MeetingWebMapper` in `src/main/java/com/minidoodle/calendar/adapter/in/web/`
- [ ] T019 [US1] Implement `MeetingController` `POST /api/v1/slots/{slotId}/meeting` wiring the use case in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T017, T018)
- [ ] T020 [US1] Enforce bean validation on `BookMeetingRequest`/`ParticipantRequest` (title bounds, `@NotEmpty` participants, at least one of name/email) and confirm the 400 surface in `src/main/java/com/minidoodle/calendar/adapter/in/web/`

**Checkpoint**: US1 fully functional and independently testable (MVP)

---

## Phase 4: User Story 2 - View meeting details (Priority: P2)

**Goal**: A caller retrieves the meeting for a slot they own, including title, description, participants, and the slot's time window.

**Independent Test**: Book a meeting, `GET /api/v1/slots/{slotId}/meeting`, and confirm the returned fields match what was booked; a slot without a meeting returns `404`.

### Tests for User Story 2

- [ ] T021 [P] [US2] MockMvc contract test for `GET /api/v1/slots/{slotId}/meeting` (200 body fields, 403 unknown caller, 404 for missing/foreign slot and for a slot with no meeting) in `src/test/java/com/minidoodle/calendar/adapter/in/web/MeetingControllerReadIT.java`
- [ ] T022 [P] [US2] Persistence integration test: `findBySlotId` returns the meeting with its participants and returns empty for a slot without one in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/MeetingRepositoryReadIT.java`

### Implementation for User Story 2

- [ ] T023 [P] [US2] Define inbound port `GetMeetingUseCase` in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [ ] T024 [US2] Implement `GetMeetingService` (ownership via caller calendar, map to response, throw `MeetingNotFoundException` when absent) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T007)
- [ ] T025 [US2] Add the `GET` endpoint to `MeetingController` and a response mapper for the slot time window in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T019, T024)

**Checkpoint**: US1 and US2 both work independently

---

## Phase 5: User Story 3 - Cancel a meeting (Priority: P3)

**Goal**: A caller cancels a meeting, which removes it and frees its slot; slot modify/delete/status operations are rejected while a meeting exists.

**Independent Test**: Book then `DELETE /api/v1/slots/{slotId}/meeting`; confirm `204`, the meeting is gone (`404` on GET), the slot is `FREE`, and `PATCH`/`DELETE` on a booked slot are rejected with `409`.

### Tests for User Story 3

- [ ] T026 [P] [US3] MockMvc contract test for `DELETE /api/v1/slots/{slotId}/meeting` (204, 403, 404) in `src/test/java/com/minidoodle/calendar/adapter/in/web/MeetingControllerCancelIT.java`
- [ ] T027 [P] [US3] MockMvc contract test for the FR-010 guards: `PATCH` status, `PATCH` time/duration, and `DELETE` on a booked slot each return `409` in `src/test/java/com/minidoodle/calendar/adapter/in/web/SlotMeetingGuardIT.java`
- [ ] T028 [P] [US3] Persistence integration test: cancelling deletes the meeting, cascades participants, and frees the slot in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/MeetingRepositoryCancelIT.java`

### Implementation for User Story 3

- [ ] T029 [P] [US3] Define inbound port `CancelMeetingUseCase` in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [ ] T030 [US3] Implement `CancelMeetingService` (ownership, delete meeting, set slot FREE in one transaction) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T007, T009)
- [ ] T031 [US3] Add the `DELETE` endpoint to `MeetingController` in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T019, T030)
- [ ] T032 [US3] Add the meeting-exists guard (reject with `409`) to `ModifySlotService`, `ChangeSlotStatusService`, and `DeleteSlotService` in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T007, T008, T010)

**Checkpoint**: All three stories independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, observability, and performance verification

- [ ] T033 [P] Annotate `MeetingController` endpoints with springdoc `@Operation`/`@ApiResponse` and the required `X-User-Id` header, and verify Swagger UI matches `specs/002-meeting-scheduling/contracts/openapi.yaml` in `src/main/java/com/minidoodle/calendar/adapter/in/web/MeetingController.java`
- [ ] T034 [P] Add `MeetingMetrics` (booked/viewed/cancelled counters and timers) and structured logging, wired into the meeting services, in `src/main/java/com/minidoodle/calendar/application/service/`
- [ ] T035 [P] Update `README.md` with the meeting endpoints, examples, and the "one meeting per slot" rule
- [ ] T036 Run `specs/002-meeting-scheduling/quickstart.md` end-to-end against `docker-compose up` and fix discrepancies
- [ ] T037 Performance check for `specs/002-meeting-scheduling/quickstart.md`: verify booking p99 ≤ 200 ms (SC-001) and meeting retrieval p99 ≤ 300 ms (SC-004) under a seeded dataset
- [ ] T038 Code cleanup and confirm no framework types leaked into `src/main/java/com/minidoodle/calendar/domain/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–5)**: depend on Foundational
- **Polish (Phase 6)**: depends on the desired stories being complete

### User Story Dependencies

- **US1 (P1)**: after Foundational — no dependency on other stories
- **US2 (P2)**: after Foundational — reuses the `MeetingResponse`/mapper introduced in US1 (T018), otherwise independent
- **US3 (P3)**: after Foundational — reuses the controller (T019) and adds the FR-010 guards in the slot services; independent of US2

> NOTE: US1, US2, and US3 all add methods to the same `MeetingController`, so implement them sequentially (US1 → US2 → US3) to avoid file conflicts, even though the underlying use cases are independent.

### Within Each User Story

- Tests MUST be written and fail before implementation
- Value objects → aggregate → port → adapter → service → controller
- Story complete before the next priority

### Parallel Opportunities

- Foundational: T004, T006, T007, T009, T010 in parallel (mind T005 → T004; T008 → T003/T007; T010 → T006)
- US1: test tasks T012–T015 in parallel; DTOs T018 in parallel with the service T017
- US2: test tasks T021–T022 in parallel; port T023 in parallel with tests
- US3: test tasks T026–T028 in parallel; port T029 in parallel with tests
- Polish: T033, T034, T035 in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests together (write first, expect failure):
Task: "Domain tests MeetingTest/ParticipantTest in src/test/java/com/minidoodle/calendar/domain/"
Task: "MockMvc POST book test in src/test/java/com/minidoodle/calendar/adapter/in/web/MeetingControllerBookIT.java"
Task: "Persistence unique/concurrency IT in src/test/java/com/minidoodle/calendar/adapter/out/persistence/MeetingBookingConcurrencyIT.java"
Task: "Persistence booking/slot-busy/RESTRICT IT in src/test/java/com/minidoodle/calendar/adapter/out/persistence/MeetingRepositoryIT.java"

# Then in parallel with the service:
Task: "Web DTOs + mapper in src/main/java/com/minidoodle/calendar/adapter/in/web/"
Task: "BookMeetingService in src/main/java/com/minidoodle/calendar/application/service/"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: US1 (T012–T020)
4. STOP and VALIDATE: book a slot → slot becomes BUSY → second booking → 409
5. Demo

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate → demo (MVP)
3. US2 → validate → demo
4. US3 → validate → demo
5. Polish

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps each task to its user story for traceability
- Tests follow the constitution (Testcontainers for the unique/FK constraints and concurrency, MockMvc for HTTP)
- Commit after each task or logical group (Conventional Commits)
- Suggested MVP scope: Phase 1 + Phase 2 + US1 (T001–T020)
