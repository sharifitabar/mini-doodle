---
description: "Task list for Time Slot Management (001)"
---

# Tasks: Time Slot Management

**Input**: Design documents from `/specs/001-time-slot-management/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. Constitution Principle III (Test Discipline with Real Dependencies) makes tests a merge gate, so each user story carries its own tests.

**Organization**: Tasks are grouped by user story so each can be implemented and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Every task includes an exact file path

## Path Conventions

Single Gradle module. Production code under `src/main/java/com/minidoodle/`, tests under `src/test/java/com/minidoodle/`, resources under `src/main/resources/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and build tooling

- [X] T001 Create Gradle project files in `settings.gradle.kts` and `build.gradle.kts` (Java 21 toolchain, Spring Boot 3.5.x, Spring MVC + Validation, Actuator, Micrometer-Prometheus, jOOQ, Flyway, springdoc-openapi, Testcontainers, add the Gradle wrapper)
- [X] T002 [P] Configure jOOQ code generation in `build.gradle.kts` (jOOQ plugin reading `src/main/resources/db/migration`, generated sources under `build/generated-sources`)
- [X] T003 [P] Create `docker-compose.yml` with the app service and a PostgreSQL 16 service (healthcheck, env, ports)
- [X] T004 [P] Create the multi-stage `Dockerfile` (Gradle build stage → JRE 21 runtime stage)
- [X] T005 [P] Create `src/main/resources/application.yml` (datasource, Flyway, jOOQ, actuator exposure, springdoc, logging) and a `docker` profile
- [X] T006 Create the application entry point `src/main/java/com/minidoodle/MiniDoodleApplication.java` and the package skeleton (`shared/config`, `shared/web`, `calendar/domain`, `calendar/application`, `calendar/adapter`)
- [X] T007 [P] Add a `src/main/resources/application-test.yml` for test wiring (Testcontainers JDBC url placeholder)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain primitives, ports, adapters, and cross-cutting web that ALL stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T008 Create Flyway migration `src/main/resources/db/migration/V1__init.sql` (`btree_gist` extension, `users`, `calendars`, `time_slots` with generated `slot_range`, `ck_*` checks, `ex_slot_no_overlap` exclusion constraint, indexes) per data-model.md
- [X] T009 Run jOOQ code generation against `src/main/resources/db/migration` and verify generated classes build under `build/generated-sources/jooq`. Configure the generated `slot_range` column to be excluded from generated insert/update statements (depends on T002, T008)
- [X] T010 [P] Implement value objects `UserId`, `CalendarId`, `SlotId`, `TimeRange`, `SlotStatus` in `src/main/java/com/minidoodle/calendar/domain/model/`
- [X] T011 Implement domain entities `Calendar` and `TimeSlot` (aggregate root with invariants) in `src/main/java/com/minidoodle/calendar/domain/model/` (depends on T010)
- [X] T012 [P] Define domain exceptions (`SlotOverlapException`, `InvalidSlotException`, `SlotNotFoundException`, `ForbiddenAccessException`) in `src/main/java/com/minidoodle/calendar/domain/exception/`
- [X] T013 [P] Define outbound ports `CalendarRepository` and `SlotRepository` in `src/main/java/com/minidoodle/calendar/application/port/out/`
- [X] T014 Implement persistence adapters `JooqCalendarRepository` and `JooqSlotRepository` (+ row mappers) in `src/main/java/com/minidoodle/calendar/adapter/out/persistence/`. Ensure the generated `slot_range` column is never written (let PostgreSQL maintain it) (depends on T009, T013)
- [X] T015 [P] Implement caller identity: `CurrentUserArgumentResolver` / filter resolving `X-User-Id` into a `UserId` and provisioning lookup, in `src/main/java/com/minidoodle/shared/web/`
- [X] T016 [P] Implement global error handling `ApiExceptionHandler` mapping domain exceptions to RFC 7807 `ProblemDetail` (400/403/404/409) in `src/main/java/com/minidoodle/shared/web/` (depends on T012)
- [X] T017 [P] Add `OpenApiConfig` and structured logging configuration in `src/main/java/com/minidoodle/shared/config/`
- [X] T018 Create Testcontainers base test `src/test/java/com/minidoodle/support/PostgresIntegrationTest.java` (spins up PostgreSQL 16, applies Flyway) 
- [X] T019 Add dev seed data for a demo user + calendar through `src/main/resources/db/migration/V2__seed_dev.sql` or a dev-profile `ApplicationRunner`

**Checkpoint**: Foundation ready — user stories can begin

---

## Phase 3: User Story 1 - Create and read time slots (Priority: P1) 🎯 MVP

**Goal**: A caller can create a slot (default FREE) in their calendar, and retrieve/list their slots.

**Independent Test**: POST a valid slot, then GET it back and make sure that it exists with `status=FREE`. POST an overlapping slot and confirm `409`.

### Tests for User Story 1 (write first, must fail before implementation) ⚠️

- [X] T020 [P] [US1] Domain unit tests for slot creation invariants (positive duration, ≤24h, future start, default FREE) in `src/test/java/com/minidoodle/calendar/domain/TimeSlotTest.java`
- [X] T021 [P] [US1] Persistence integration test: insert succeeds and a second overlapping insert fails on `ex_slot_no_overlap` in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryOverlapIT.java`
- [X] T022 [P] [US1] MockMvc contract test for `POST /api/v1/slots` (201, 400, 403, 404, 409, including missing/unknown caller identity) in `src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerCreateIT.java` (covers FR-016)
- [X] T023 [P] [US1] MockMvc contract test for `GET /api/v1/slots` and `GET /api/v1/slots/{slotId}` (200, filters, pagination, 403, 404) in `src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerReadIT.java`
- [X] T054 [P] [US1] Persistence integration test proving slots survive a restart (fresh application context / reconnected DataSource against the same database) in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryPersistenceIT.java` (covers FR-013)
- [X] T055 [P] [US1] Domain test for timezone/offset normalization across a DST boundary (for example, a slot crossing a spring-forward/fall-back transition) in `src/test/java/com/minidoodle/calendar/domain/TimeRangeTimezoneTest.java` (covers FR-014 edge case)
- [X] T056 [P] [US1] Concurrency integration test: N parallel inserts of mutually overlapping slots yield exactly one success and N-1 overlap rejections through `ex_slot_no_overlap` in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryConcurrencyIT.java` (covers FR-010, SC-002)

### Implementation for User Story 1

- [X] T024 [P] [US1] Define inbound ports `CreateSlotUseCase`, `ListSlotsUseCase`, `GetSlotUseCase` in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [X] T025 [P] [US1] Add web DTOs `CreateSlotRequest`, `SlotResponse`, `SlotPageResponse` and mapper in `src/main/java/com/minidoodle/calendar/adapter/in/web/`
- [X] T026 [US1] Implement the create operation in `SlotApplicationService` (resolve calendar, build aggregate, persist, translate exclusion violation → `SlotOverlapException`) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T024)
- [X] T027 [US1] Implement the list/get operations in `SlotApplicationService` (range/status filters, limit/offset, ownership scoping) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T024, T014)
- [X] T028 [US1] Implement `SlotController` POST and GET/GET-one endpoints wiring the use cases in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T025, T026, T027)
- [X] T029 [US1] Map PostgreSQL `exclusion_violation` (23P01) to `SlotOverlapException` in `src/main/java/com/minidoodle/calendar/adapter/out/persistence/` (depends on T014)
- [X] T030 [US1] Enforce bean validation on `CreateSlotRequest` in `src/main/java/com/minidoodle/calendar/adapter/in/web/CreateSlotRequest.java` (duration bounds, future start, offset date-time) and confirm 400 surface (depends on T016)

**Checkpoint**: US1 fully functional and independently testable (MVP)

---

## Phase 4: User Story 2 - Mark a slot busy or free (Priority: P2)

**Goal**: A caller toggles an existing slot between `FREE` and `BUSY`.

**Independent Test**: PATCH a slot's status to `BUSY`, GET it, confirm `BUSY`. PATCH to `FREE`, confirm `FREE`.

### Tests for User Story 2

- [X] T031 [P] [US2] Domain unit test for idempotent status transitions in `src/test/java/com/minidoodle/calendar/domain/TimeSlotStatusTest.java`
- [X] T032 [P] [US2] MockMvc contract test for `PATCH /api/v1/slots/{slotId}` with `status` (200, 403, 404) in `src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerStatusIT.java`

### Implementation for User Story 2

- [X] T033 [P] [US2] Define inbound port `UpdateSlotUseCase` with `UpdateSlotCommand` (status, startAt, durationMinutes) in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [X] T034 [US2] Implement the status update in `SlotApplicationService` (ownership check, idempotent set) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T033)
- [X] T035 [US2] Extend `SlotController` PATCH to handle `status` field in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T034, T028)

**Checkpoint**: US1 and US2 both work independently

---

## Phase 5: User Story 3 - Modify an existing slot (Priority: P3)

**Goal**: A caller changes a slot's start time and/or duration. Overlap is still rejected.

**Independent Test**: PATCH the duration of an existing slot and confirm end time updates. PATCH into an occupied range and confirm `409`.

### Tests for User Story 3

- [X] T036 [P] [US3] Domain unit test for modifying time/duration invariants and overlap check again in `src/test/java/com/minidoodle/calendar/domain/TimeSlotModifyTest.java`
- [X] T037 [P] [US3] Persistence integration test: updating a slot into an overlapping range fails on the constraint in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryModifyIT.java`
- [X] T038 [P] [US3] MockMvc contract test for `PATCH /api/v1/slots/{slotId}` with `startAt`/`durationMinutes` (200, 400, 404, 409) in `src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerModifyIT.java`

### Implementation for User Story 3

- [X] T039 [P] [US3] Reuse the inbound port `UpdateSlotUseCase` for time changes (`startAt`/`durationMinutes`) in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [X] T040 [US3] Implement the time modification in `SlotApplicationService` (re-validate invariants, persist, translate overlap → 409) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T039)
- [X] T041 [US3] Add `UpdateSlotRequest` DTO and extend `SlotController` PATCH for `startAt`/`durationMinutes` in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T040, T035)

**Checkpoint**: US1–US3 independently functional

---

## Phase 6: User Story 4 - Delete a slot (Priority: P4)

**Goal**: A caller removes a slot from their calendar.

**Independent Test**: Create a slot, DELETE it, confirm it is gone (`404` on later GET).

### Tests for User Story 4

- [X] T042 [P] [US4] Persistence integration test for delete scoped to calendar in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryDeleteIT.java`
- [X] T043 [P] [US4] MockMvc contract test for `DELETE /api/v1/slots/{slotId}` (204, 403, 404) in `src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerDeleteIT.java`

### Implementation for User Story 4

- [X] T044 [P] [US4] Define inbound port `DeleteSlotUseCase` in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [X] T045 [US4] Implement the delete operation in `SlotApplicationService` (ownership check, delete) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T044)
- [X] T046 [US4] Add DELETE endpoint to `SlotController` in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T045)

**Checkpoint**: All four stories independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, observability, CI, and performance verification

- [X] T047 [P] Annotate endpoints with springdoc `@Operation`/`@ApiResponse` and verify Swagger UI matches `contracts/openapi.yaml` in `src/main/java/com/minidoodle/calendar/adapter/in/web/SlotController.java`
- [X] T048 [P] Add Micrometer counters/timers for slot operations and a structured-log convention in `src/main/java/com/minidoodle/calendar/application/service/`
- [X] T049 [P] Add GitHub Actions workflow `.github/workflows/ci.yml` running `./gradlew build` (with Testcontainers) on pull requests
- [X] T050 Update `README.md` with concrete run steps, endpoint overview, and link to the OpenAPI UI
- [X] T051 Run `quickstart.md` end-to-end against `docker-compose up` and fix discrepancies
- [X] T052 Performance check for `specs/001-time-slot-management/quickstart.md`: verify slot create p99 ≤ 200 ms and bounded list latency under a seeded dataset (thousands of slots)
- [X] T053 Code cleanup and confirm no framework types leaked into `src/main/java/com/minidoodle/calendar/domain/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–6)**: depend on Foundational
- **Polish (Phase 7)**: depends on the desired stories being complete

### User Story Dependencies

- **US1 (P1)**: after Foundational — no dependency on other stories
- **US2 (P2)**: after Foundational — extends the PATCH endpoint introduced conceptually in US1's controller (T028). Otherwise independent
- **US3 (P3)**: after Foundational — extends PATCH (T041 on top of T035)
- **US4 (P4)**: after Foundational — fully independent

> NOTE: US2 and US3 both add fields to the same `PATCH /slots/{id}` handler, so they touch `SlotController`. Implement sequentially (US2 → US3) to avoid file conflicts. US4 is independent.

### Within Each User Story

- Tests MUST be written and fail before implementation
- Ports → services → controller wiring
- Story complete before the next priority

### Parallel Opportunities

- Setup: T002, T003, T004, T005, T007 in parallel
- Foundational: T010, T012, T013, T015, T016, T017 in parallel (mind T011 → T010, T014 → T009/T013)
- Each story's test tasks (for example, T020–T023) run in parallel
- Different stories can be worked in parallel by different people once Foundational completes (except the shared PATCH handler in US2/US3)

---

## Parallel Example: User Story 1

```bash
# Tests (all parallel):
Task: "Domain unit tests for slot creation in src/test/java/com/minidoodle/calendar/domain/TimeSlotTest.java"
Task: "Persistence overlap IT in src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryOverlapIT.java"
Task: "MockMvc create test in src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerCreateIT.java"
Task: "MockMvc read test in src/test/java/com/minidoodle/calendar/adapter/in/web/SlotControllerReadIT.java"
Task: "Persistence restart IT in src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryPersistenceIT.java"
Task: "DST timezone test in src/test/java/com/minidoodle/calendar/domain/TimeRangeTimezoneTest.java"
Task: "Concurrency overlap IT in src/test/java/com/minidoodle/calendar/adapter/out/persistence/SlotRepositoryConcurrencyIT.java"

# Ports + DTOs (parallel):
Task: "Define CreateSlotUseCase/ListSlotsUseCase/GetSlotUseCase in src/main/java/com/minidoodle/calendar/application/port/in/"
Task: "Add CreateSlotRequest/SlotResponse/SlotPageResponse + mapper in src/main/java/com/minidoodle/calendar/adapter/in/web/"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: US1
4. STOP and VALIDATE: create → read → overlap-rejected
5. Demo

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate → demo (MVP)
3. US2 → validate → demo
4. US3 → validate → demo
5. US4 → validate → demo
6. Polish

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps each task to its user story for traceability
- Tests follow the constitution (Testcontainers for persistence, MockMvc for HTTP)
- Commit after each task or logical group (Conventional Commits)
- Suggested MVP scope: Phase 1 + Phase 2 + US1 (T001–T030 plus the US1 remediation tests T054–T056)
