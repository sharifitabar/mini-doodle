---
description: "Task list for Domain Event Outbox (008)"
---

# Tasks: Domain Event Outbox

**Input**: Design documents from `/specs/008-domain-event-outbox/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. Constitution principle III makes tests a merge gate, so each user story carries its own
tests. The test split follows research D11: one transactional test for atomicity, and non-transactional
tests for delivery.

**Organization**: Tasks are grouped by user story. The feature extends the `calendar` context. There is no
greenfield setup.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task includes an exact file path

## Path Conventions

Single Gradle module. Production code is under `src/main/java/com/minidoodle/calendar/`. Tests are under
`src/test/java/com/minidoodle/calendar/`. Resources are under `src/main/resources/`. The shared
configuration is under `src/main/java/com/minidoodle/shared/config/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Schema, configuration properties, and scheduling.

- [x] T001 Create the Flyway migration `src/main/resources/db/migration/V4__outbox_events.sql` with the table, the 5 check constraints, and the 3 indexes from data-model.md
- [x] T002 Run `./gradlew build` and confirm that the generated `OutboxEvents` table and record classes exist under `build/generated-sources/jooq`, and edit no generated file (depends on T001)
- [x] T003 [P] Add `src/main/java/com/minidoodle/shared/config/OutboxProperties.java` with the keys `poll-interval`, `batch-size`, `max-attempts`, `cleanup-interval`, and `retention`, bound to `app.outbox`, in the style of `SlotProperties`
- [x] T004 [P] Add the `app.outbox` defaults to `src/main/resources/application.yml`: `poll-interval: 500ms`, `batch-size: 50`, `max-attempts: 5`, `cleanup-interval: 1h`, `retention: 7d`
- [x] T005 [P] Add `@EnableScheduling` to `src/main/java/com/minidoodle/MiniDoodleApplication.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain events, ports, and the transaction ownership change. All 3 stories need this phase.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T006 Create the sealed interface `src/main/java/com/minidoodle/calendar/domain/event/CalendarEvent.java` with `eventId`, `type()`, `schemaVersion()`, `aggregateType()`, `aggregateId()`, `calendarId`, and `occurredAt`, per data-model.md
- [x] T007 Create the 5 records in `src/main/java/com/minidoodle/calendar/domain/event/`: `SlotCreated.java`, `SlotUpdated.java`, `SlotDeleted.java`, `MeetingBooked.java`, and `MeetingCancelled.java`, with the payload fields from data-model.md (depends on T006)
- [x] T008 Modify `src/main/java/com/minidoodle/calendar/domain/model/TimeSlot.java`: add the event list, `drainEvents()`, an event in `book`, `cancelMeeting`, `changeTime`, `changeStatus`, `create`, and `recordDeletion`, and an equality guard so that an equal status or an equal time range adds no event (depends on T007)
- [x] T009 [P] Create the 4 ports and the message record in `src/main/java/com/minidoodle/calendar/application/port/out/`: `EventOutbox.java` (`append`), `EventPublisher.java` (`publish`), `OutboxMessage.java`, and `OutboxMetrics.java` (`published`, `failed`, `pending`)
- [x] T010 Move transaction ownership: add `@Transactional` to `createSlot` in `src/main/java/com/minidoodle/calendar/application/service/SlotApplicationService.java`, and remove the `dsl.transaction(...)` call and its comment from `src/main/java/com/minidoodle/calendar/adapter/out/persistence/JooqSlotRepository.java`

---

## Phase 3: User Story 1 - Capture in the same unit as the state change (Priority: P1) 🎯 MVP

**Goal**: A successful change writes one event record in the same transaction. A failed change writes none.

**Independent Test**: Book a meeting, then query `outbox_events`. Exactly one `meeting.booked` row exists
with the slot identifier. Then fail a booking. No new row exists.

### Tests for User Story 1

- [x] T011 [P] [US1] Write `src/test/java/com/minidoodle/calendar/domain/model/TimeSlotEventsTest.java`: one event per real transition, no event for an equal status, no event for an equal time range, and `drainEvents()` clears the list
- [x] T012 [P] [US1] Write `src/test/java/com/minidoodle/calendar/adapter/out/outbox/OutboxPayloadSerializationTest.java`: the 5 payload documents have the fields and the values of `contracts/outbox-event.schema.json`, and no participant name or email

### Implementation for User Story 1

- [x] T013 [US1] Implement `src/main/java/com/minidoodle/calendar/adapter/out/outbox/JooqEventOutbox.java` with `append(List<CalendarEvent>)`: serialize the payload with Jackson, insert the row with `state='PENDING'` and `payload = JSONB.valueOf(json)`, and include the envelope columns (depends on T002, T009)
- [x] T014 [US1] Call `outbox.append(slot.drainEvents())` after the write in `createSlot`, `updateSlot`, and `deleteSlot` in `src/main/java/com/minidoodle/calendar/application/service/SlotApplicationService.java`, and in `bookMeeting` and `cancelMeeting` in `src/main/java/com/minidoodle/calendar/application/service/MeetingApplicationService.java` (depends on T010, T013)
- [x] T015 [US1] Write `src/test/java/com/minidoodle/calendar/adapter/out/outbox/OutboxCaptureIT.java` on `PostgresIntegrationTest`: a booking writes one row, a failed booking writes none, a no-op `PATCH` writes none, a delete with a meeting writes none, and a read writes none (depends on T014)

**Checkpoint**: US1 alone is the MVP. The record is durable and testable with SQL.

---

## Phase 4: User Story 2 - Delivery to the log in a background action (Priority: P2)

**Goal**: A background action sends each pending row to the log. The request does not wait. A failure retries,
and 5 failures end the attempts.

**Independent Test**: Insert one pending row, call `pollOnce()`. The row is `PUBLISHED` and the log has one
entry. Force 5 failures. The row is `FAILED`.

### Tests for User Story 2

- [x] T016 [US2] Write `src/test/java/com/minidoodle/calendar/adapter/out/outbox/OutboxDeliveryIT.java` without `@Transactional`: `pollOnce()` publishes and marks, keeps `id` order for one slot, increments `attempts`, sets `FAILED` at 5, and resumes from committed rows (depends on T019)
- [x] T017 [P] [US2] Write `src/test/java/com/minidoodle/calendar/adapter/out/outbox/OutboxLogContractTest.java` with a logback `ListAppender`: one `event=event.published` entry for each event, the envelope fields, no participant email, and no second mutation entry from the metrics adapters (depends on T018)

### Implementation for User Story 2

- [x] T018 [P] [US2] Implement `src/main/java/com/minidoodle/calendar/adapter/out/outbox/LoggingEventPublisher.java` per `contracts/delivery-log.md`: the envelope at the information level, the payload at the debug level, and the logger `com.minidoodle.outbox`
- [x] T019 [US2] Add the delivery queries to `src/main/java/com/minidoodle/calendar/adapter/out/outbox/JooqEventOutbox.java`: `nextPending(limit)` with `ORDER BY id FOR UPDATE SKIP LOCKED`, `markPublished`, and `markFailed` (depends on T013)
- [x] T020 [US2] Implement `src/main/java/com/minidoodle/calendar/adapter/out/outbox/OutboxRelay.java` with `pollOnce()`: read one batch, publish each row, mark it, catch a failure, write the warning entry, and set `FAILED` at `max-attempts` (depends on T018, T019)
- [x] T021 [US2] Add the scheduled entry point to `OutboxRelay.java`: `@Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")` calls `pollOnce()` (depends on T020, T005)
- [x] T022 [P] [US2] Remove the 5 mutation-event `log.info("event=...")` lines from `src/main/java/com/minidoodle/calendar/adapter/out/observability/MicrometerSlotMetrics.java` and `MicrometerMeetingMetrics.java`, and keep the `meeting.viewed` and `availability.query` lines (FR-019)

**Checkpoint**: US2 makes the record visible in the log. US1 and US2 are independently testable.

---

## Phase 5: User Story 3 - Operations visibility and retention (Priority: P3)

**Goal**: 3 meters show the state, and a cleanup removes old published rows.

**Independent Test**: Read `/actuator/prometheus`. The 3 meters show the 3 counts. Insert a published row older
than 7 days, call `cleanupOnce()`. The row is absent. A failed row stays.

### Tests for User Story 3

- [x] T023 [US3] Write `src/test/java/com/minidoodle/calendar/adapter/out/outbox/OutboxMetricsIT.java` without `@Transactional`: the published, failed, and pending counts match the table (depends on T025)
- [x] T024 [US3] Write `src/test/java/com/minidoodle/calendar/adapter/out/outbox/OutboxCleanupIT.java` without `@Transactional`: an old published row goes, a failed row stays, and a recent published row stays (depends on T026)

### Implementation for User Story 3

- [x] T025 [P] [US3] Implement `src/main/java/com/minidoodle/calendar/adapter/out/observability/MicrometerOutboxMetrics.java`: the counters `minidoodle.outbox.published` and `minidoodle.outbox.failed`, and the gauge `minidoodle.outbox.pending` over an `AtomicInteger`
- [x] T026 [US3] Update the published counter and the failed counter from `OutboxRelay.java` after each delivery (depends on T020, T025)
- [x] T027 [US3] Add `cleanupOnce()` and its scheduled entry point to `OutboxRelay.java`: delete published rows older than `retention` in a bounded statement, keep failed rows, and write the debug entry (depends on T019, T021)
- [x] T028 [US3] Add `countPending()` to `JooqEventOutbox.java`, call it after each poll in `OutboxRelay.java`, and set the first gauge value at start (depends on T019, T020, T025)

**Checkpoint**: All 3 stories work. The operator can see a stuck outbox.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T029 [P] Write `docs/adr/0002-event-outbox.md` in STE: the context, the decision, the consequences, and the rejected alternatives from research D1, D5, D6, and D7
- [x] T030 [P] Add the event section to `docs/ARCHITECTURE.md` in STE: the capture unit, the delivery action, the ordering limit, and the retention rule
- [x] T031 [P] Add the 3 meters and the outbox behavior to the Metrics Stack section of `README.md` in STE
- [x] T032 Run `./gradlew spotlessApply`, then `./gradlew check`. Correct every failure in the code. Do not lower a coverage floor.
- [x] T033 Run the procedure in `specs/008-domain-event-outbox/quickstart.md` against the local stack. Record the result, or record the reason why the run is not possible.
- [x] T034 Confirm that the ArchUnit rules R1–R6 in `src/test/java/com/minidoodle/architecture/ArchitectureRules.java` pass with the new packages, and add no suppression and no new rule.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (T001–T005) precedes Foundational. T002 depends on T001.
- Foundational (T006–T010) blocks every story. T007 depends on T006. T008 depends on T007.
- US1 (T011–T015) is the MVP. T013 depends on T002 and T009. T014 depends on T010 and T013. T015 depends on T014.
- US2 (T016–T022) depends on US1 for the rows. T019 depends on T013. T020 depends on T018 and T019. T021 depends on T020 and T005.
- US3 (T023–T028) depends on US2 for the relay. T026 depends on T020 and T025. T027 depends on T019 and T021.
- Polish (T029–T034) follows US3.

### Within Each Story

- Write the test before the implementation where the story lists a test.
- The adapter precedes the wiring. The wiring precedes the integration test.

### Parallel Opportunities

- T003, T004, and T005 touch different files.
- T009 touches 4 new port files and no existing file.
- T011 and T012 are 2 different test files.
- T018 and T022 touch different files.
- T029, T030, and T031 are 3 different documents.

---

## Parallel Example: Setup and Foundational

```text
Task T003: OutboxProperties.java
Task T004: application.yml
Task T005: MiniDoodleApplication.java
```

## Parallel Example: US1 tests

```text
Task T011: TimeSlotEventsTest.java
Task T012: OutboxPayloadSerializationTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Setup and Foundational.
2. Complete US1.
3. Stop and test: book a meeting, read the row, fail a booking, confirm no row.

US1 alone is a valuable increment: the durable record exists, and a later consumer can use it.
The log entry is not available in the MVP.

### Incremental Delivery

1. Add US2. Each event appears in the log. The request does not wait.
2. Add US3. The operator sees the counts and the cleanup keeps the table small.
3. Add the Polish tasks. Run the gates and the quickstart.

### Stop Points

- After US1: confirm FR-001 to FR-007 with `OutboxCaptureIT`.
- After US2: confirm FR-008 to FR-015 with `OutboxDeliveryIT` and `OutboxLogContractTest`.
- After US3: confirm FR-016 and FR-017 with `OutboxMetricsIT` and `OutboxCleanupIT`.
- FR-018 stays true in every phase: no endpoint changes.
