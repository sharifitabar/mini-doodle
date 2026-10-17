---
description: "Task list for Availability Query (003)"
---

# Tasks: Availability Query

**Input**: Design documents from `/specs/003-availability-query/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. Constitution Principle III (Test Discipline with Real Dependencies) makes tests a merge gate, so each user story carries its own tests.

**Organization**: Tasks are grouped by user story so each can be implemented and tested independently. This feature extends the existing `calendar` bounded context and adds **no migration** — availability is derived from the existing `time_slots` and `meetings` tables.

> **Note on shared surface**: all three stories serve a single endpoint (`GET /api/v1/availability`) and a single response shape, so they are three facets of one computation delivered as increments: US1 = free windows, US2 = busy intervals, US3 = unallocated gaps that make the union cover the frame exactly. Each story extends the same `AvailabilityCalculator`/`JooqAvailabilityRepository`, so implement US1 → US2 → US3 sequentially to avoid file conflicts.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task includes an exact file path

## Path Conventions

Single Gradle module (established by 001). Production code under `src/main/java/com/minidoodle/calendar/`, tests under `src/test/java/com/minidoodle/calendar/`, shared code under `src/main/java/com/minidoodle/shared/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing project already provides everything this feature needs

- [x] T001 Verify no new third-party dependencies are required (Spring MVC, Bean Validation, jOOQ, Flyway, springdoc, Micrometer already present) and no new Flyway migration is needed; run `./gradlew build --no-daemon --console=plain` to confirm a green baseline

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain value types, the read port, and error mapping shared by all three stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 [P] Implement `AvailabilityKind` enum (`FREE`, `BUSY`, `UNALLOCATED`) in `src/main/java/com/minidoodle/calendar/domain/model/`
- [x] T003 [P] Implement `TimeFrame` value object (both bounds required, `end` strictly after `start`, span ≤ 31 days, normalized to UTC and truncated to microseconds) in `src/main/java/com/minidoodle/calendar/domain/model/`
- [x] T004 [P] Implement `AvailabilityInterval` value object (half-open `[start, end)`, kind, `end` strictly after `start`) in `src/main/java/com/minidoodle/calendar/domain/model/`
- [x] T005 Implement `Availability` result record (frame + ordered intervals) in `src/main/java/com/minidoodle/calendar/domain/model/` (depends on T003, T004)
- [x] T006 [P] Implement `SlotOccupancy` domain type (`TimeRange range`, `SlotStatus status`, `boolean holdsMeeting`, with derived `isBusy()`) in `src/main/java/com/minidoodle/calendar/domain/model/`
- [x] T007 [P] Define domain exception `InvalidTimeFrameException` extending `DomainException` in `src/main/java/com/minidoodle/calendar/domain/exception/`
- [x] T008 [P] Define outbound port `AvailabilityRepository` (`List<SlotOccupancy> findOccupancies(CalendarId calendarId, TimeFrame frame)`) in `src/main/java/com/minidoodle/calendar/application/port/out/`
- [x] T009 Implement `JooqAvailabilityRepository` reading `time_slots` scoped to the calendar and overlapping the frame (`slot_range && tstzrange(from, to, '[)')`, ordered by `start_at`), mapping rows to `SlotOccupancy`, in `src/main/java/com/minidoodle/calendar/adapter/out/persistence/` (depends on T006, T008)
- [x] T010 [P] Extend `src/main/java/com/minidoodle/shared/web/GlobalExceptionHandler.java` to map `InvalidTimeFrameException` → 400 (`invalid-time-frame`) and `MissingServletRequestParameterException` → 400 (`validation`) (depends on T007)
- [x] T011 Extend the Testcontainers base to help this feature's tests: add a slot-creation helper (and a meeting-booking helper) to `src/test/java/com/minidoodle/support/PostgresIntegrationTest.java` (depends on T008)

**Checkpoint**: Foundation ready — user stories can begin

---

## Phase 3: User Story 1 - View your free time over a period (Priority: P1) 🎯 MVP

**Goal**: A caller requests availability for a frame and receives their merged free windows (clipped to the frame), in chronological order.

**Independent Test**: Create two contiguous free slots plus a separated one, `GET /api/v1/availability?from=&to=`, and confirm the contiguous pair is returned as one merged `FREE` window and the frame is echoed.

### Tests for User Story 1 (write first, must fail before implementation) ⚠️

- [x] T012 [P] [US1] Domain unit tests for `TimeFrame` (missing/inverted/oversized bounds rejected; bounds normalized to UTC and truncated) and `AvailabilityInterval` invariants in `src/test/java/com/minidoodle/calendar/domain/TimeFrameTest.java` and `src/test/java/com/minidoodle/calendar/domain/AvailabilityIntervalTest.java`
- [x] T013 [P] [US1] Domain unit tests for the FREE path of `AvailabilityCalculator` (contiguous free slots merge; non-adjacent free slots stay separate; a slot extending past a frame bound is clipped; a slot entirely outside the frame is dropped) in `src/test/java/com/minidoodle/calendar/domain/AvailabilityCalculatorTest.java`
- [x] T014 [P] [US1] Persistence integration test for `JooqAvailabilityRepository`: only slots overlapping the frame for the caller's calendar are returned, ordered by `start_at`; another calendar's slots are excluded in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/AvailabilityRepositoryIT.java`
- [x] T015 [P] [US1] MockMvc contract test for `GET /api/v1/availability` (200 with merged free windows for the frame; 400 missing/invalid/malformed `from`/`to`; 403 unknown caller; frame echoed) in `src/test/java/com/minidoodle/calendar/adapter/in/web/AvailabilityControllerIT.java`

### Implementation for User Story 1

- [x] T016 [P] [US1] Define inbound port `GetAvailabilityUseCase` and query `AvailabilityQuery` (`from`, `to`) in `src/main/java/com/minidoodle/calendar/application/port/in/`
- [x] T017 [US1] Implement `AvailabilityCalculator` FREE path (map free occupancies, clip to the frame, coalesce contiguous free blocks, keep chronological order) in `src/main/java/com/minidoodle/calendar/domain/service/` (depends on T003, T004, T006)
- [x] T018 [US1] Implement `AvailabilityApplicationService` (resolve caller calendar, build `TimeFrame`, load occupancies, invoke the calculator) in `src/main/java/com/minidoodle/calendar/application/service/` (depends on T005, T008, T009, T016, T017)
- [x] T019 [P] [US1] Add web DTOs `AvailabilityResponse` and `AvailabilityIntervalResponse` with `AvailabilityWebMapper` in `src/main/java/com/minidoodle/calendar/adapter/in/web/`
- [x] T020 [US1] Implement `AvailabilityController` `GET /api/v1/availability` wiring the use case, with the required `X-User-Id` header, in `src/main/java/com/minidoodle/calendar/adapter/in/web/` (depends on T018, T019)
- [x] T021 [US1] Enforce the required `from`/`to` query parameters and ISO-8601-with-offset binding, and confirm the 400/403 surface, in `src/main/java/com/minidoodle/calendar/adapter/in/web/AvailabilityController.java` (depends on T010, T020)

**Checkpoint**: US1 fully functional and independently testable (MVP) — returns merged free windows

---

## Phase 4: User Story 2 - See busy time, including meetings (Priority: P2)

**Goal**: Busy time within the frame is distinguished from free time: slots explicitly marked `BUSY` and slots that hold a meeting are reported as `BUSY` intervals.

**Independent Test**: With a frame containing a busy slot and a slot that holds a meeting, `GET /api/v1/availability` and confirm both are reported as `BUSY`; a free slot touching a busy slot stays a separate `FREE` interval.

### Tests for User Story 2

- [x] T022 [P] [US2] Domain tests for busy classification (`SlotOccupancy.isBusy()`; `SlotStatus.BUSY` → BUSY; `holdsMeeting` → BUSY; a free block touching a busy block is not merged) in `src/test/java/com/minidoodle/calendar/domain/SlotOccupancyTest.java` and `src/test/java/com/minidoodle/calendar/domain/AvailabilityCalculatorTest.java`
- [x] T023 [P] [US2] Persistence integration test: a slot that holds a meeting is returned with `holdsMeeting = true` via the `LEFT JOIN meetings` in `src/test/java/com/minidoodle/calendar/adapter/out/persistence/AvailabilityRepositoryIT.java`
- [x] T024 [P] [US2] MockMvc contract test: after booking a meeting on a free slot, availability reports that period as `BUSY`, not `FREE`, in `src/test/java/com/minidoodle/calendar/adapter/in/web/AvailabilityControllerIT.java`

### Implementation for User Story 2

- [x] T025 [US2] Extend `JooqAvailabilityRepository` with a `LEFT JOIN` to `meetings` (`m.slot_id = ts.id`) so each `SlotOccupancy` carries `holdsMeeting`, in `src/main/java/com/minidoodle/calendar/adapter/out/persistence/JooqAvailabilityRepository.java` (depends on T009)
- [x] T026 [US2] Extend `AvailabilityCalculator` to emit `BUSY` intervals using `SlotOccupancy.isBusy()` (status `BUSY` or holds a meeting), keeping same-kind coalescing and never merging across kinds, in `src/main/java/com/minidoodle/calendar/domain/service/AvailabilityCalculator.java` (depends on T017, T025)

**Checkpoint**: US1 and US2 both work independently — free and busy intervals are distinguished

---

## Phase 5: User Story 3 - See unallocated time in the frame (Priority: P3)

**Goal**: Time inside the frame covered by no slot is reported as `UNALLOCATED`, so the returned intervals are disjoint and together cover the frame exactly.

**Independent Test**: Request availability for a partly covered frame and confirm gaps appear as `UNALLOCATED` intervals, with the union matching `[from, to)` exactly (no overlaps, no holes); a fully covered frame yields no `UNALLOCATED` intervals.

### Tests for User Story 3

- [x] T027 [P] [US3] Domain tests for gap filling and exact coverage: empty frame → single `UNALLOCATED` interval spanning the frame; holes between intervals → `UNALLOCATED`; a frame fully covered by free+busy slots → no gaps; boundaries align at microseconds, in `src/test/java/com/minidoodle/calendar/domain/AvailabilityCalculatorTest.java`
- [x] T028 [P] [US3] MockMvc contract test: response intervals are ordered, disjoint, and cover `[from, to)` exactly (first `startAt` == `from`, last `endAt` == `to`, adjacent boundaries equal), in `src/test/java/com/minidoodle/calendar/adapter/in/web/AvailabilityControllerIT.java`

### Implementation for User Story 3

- [x] T029 [US3] Extend `AvailabilityCalculator` to fill the gaps before the first, between, and after the last emitted interval with `UNALLOCATED` intervals so the union equals the frame exactly, in `src/main/java/com/minidoodle/calendar/domain/service/AvailabilityCalculator.java` (depends on T026)

**Checkpoint**: All three stories work — the response is a complete free/busy/unallocated cover of the frame

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, observability, and end-to-end verification

- [x] T030 [P] Add springdoc `@Operation`/`@ApiResponses` annotations and the required `X-User-Id` header parameter to `AvailabilityController`, and verify Swagger UI matches `specs/003-availability-query/contracts/openapi.yaml`, in `src/main/java/com/minidoodle/calendar/adapter/in/web/AvailabilityController.java`
- [x] T031 [P] Add `AvailabilityMetrics` (`minidoodle.availability.queries` counter, `minidoodle.availability.query` timer, structured log) wired into `AvailabilityApplicationService`, in `src/main/java/com/minidoodle/calendar/application/service/`
- [x] T032 [P] Update `README.md` with the availability endpoint, an example, the free/busy/unallocated semantics, and the metric names
- [x] T033 Run `specs/003-availability-query/quickstart.md` end-to-end against `docker-compose up` and fix any discrepancies
- [x] T034 Performance check for `specs/003-availability-query/quickstart.md`: verify availability p99 ≤ 300 ms (SC-001, SC-005) against a seeded dataset of thousands of slots
- [x] T035 Code cleanup: confirm no framework types leaked into `src/main/java/com/minidoodle/calendar/domain/` and re-run `./gradlew build --no-daemon --console=plain`
- [x] T036 [FR-010] Add a read-only assertion to `src/test/java/com/minidoodle/calendar/adapter/in/web/AvailabilityControllerIT.java` verifying no `time_slots` or `meetings` rows change after an availability query

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–5)**: depend on Foundational
- **Polish (Phase 6)**: depends on the desired stories being complete

### User Story Dependencies

- **US1 (P1)**: after Foundational — no dependency on other stories (MVP)
- **US2 (P2)**: after Foundational — extends the calculator (T017) and the repository (T009) introduced in US1
- **US3 (P3)**: after Foundational — depends on US2's calculator output (T026) to fill gaps

> NOTE: all three stories touch the same `AvailabilityCalculator` (and `AvailabilityCalculatorTest`/`AvailabilityControllerIT`), so implement them sequentially (US1 → US2 → US3) to avoid file conflicts.

### Within Each User Story

- Tests MUST be written and fail before implementation
- Value objects → calculator → repository → service → controller
- Story complete before the next priority

### Parallel Opportunities

- Foundational: T002, T003, T004, T006, T007, T008 in parallel (mind T005 → T003/T004; T009 → T006/T008; T010 → T007)
- US1: test tasks T012–T015 in parallel; DTOs T019 in parallel with the calculator T017
- US2: test tasks T022–T024 in parallel
- US3: test tasks T027–T028 in parallel
- Polish: T030, T031, T032 in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests together (write first, expect failure):
Task: "TimeFrame/AvailabilityInterval unit tests in src/test/java/com/minidoodle/calendar/domain/"
Task: "AvailabilityCalculator free-path tests in src/test/java/com/minidoodle/calendar/domain/AvailabilityCalculatorTest.java"
Task: "JooqAvailabilityRepository IT in src/test/java/com/minidoodle/calendar/adapter/out/persistence/AvailabilityRepositoryIT.java"
Task: "AvailabilityController MockMvc IT in src/test/java/com/minidoodle/calendar/adapter/in/web/AvailabilityControllerIT.java"

# Then in parallel with the calculator:
Task: "Web DTOs + mapper in src/main/java/com/minidoodle/calendar/adapter/in/web/"
Task: "AvailabilityCalculator (FREE path) in src/main/java/com/minidoodle/calendar/domain/service/"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1 (T012–T021)
4. `GET /api/v1/availability` returns merged free windows for the caller
5. Stop and validate independently before proceeding

### Incremental Delivery

1. US1 → free windows (MVP)
2. US2 → busy intervals (status/markings + meetings)
3. US3 → unallocated gaps complete the exact cover
4. Polish → OpenAPI, metrics, README, quickstart, performance

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps the task to its user story for traceability
- No schema change: availability is computed from existing `time_slots` and `meetings`
- Merging/clipping/classification live in the framework-free `AvailabilityCalculator` (constitution I); the read uses the existing GiST range index (constitution II)
- Run the full build with the Gradle daemon disabled: `./gradlew build --no-daemon --console=plain`
- Commit after each task or logical group (Conventional Commits)
- Suggested MVP scope: Phase 1 + Phase 2 + US1 (T001–T021)
