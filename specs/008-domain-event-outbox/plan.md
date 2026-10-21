# Implementation Plan: Domain Event Outbox

<!--
  Write generated prose in ASD-STE100 Simplified Technical English (STE).
  Read AGENTS.md and docs/WRITING.md before you write.
  Preserve recorded input, requirements, identifiers, commands, and code.
-->

**Branch**: `008-domain-event-outbox` | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-domain-event-outbox/spec.md`

**Note**: The `/speckit.plan` command fills in this template. Its definition describes the execution workflow.

## Summary

The service writes one event record for each successful change in 5 flows: time slot creation,
time slot update, time slot deletion, meeting booking, and meeting cancellation. The record and the
state change commit as one unit. A background action sends each record to the application log, and
a later adapter can replace the log. The record is visible after a restart, so a future consumer
can use it.

The technical approach: one `outbox_events` table, one sealed domain event type, one outbound port
for the append, one poller with a fixed delay, one publisher port with a log adapter, and three
Micrometer meters. The feature adds no HTTP surface and no external broker.

## Technical Context

**Language/Version**: Java 21 (the Gradle toolchain pins 21)

**Primary Dependencies**: Spring Boot 3.5.x (web, validation, actuator, jOOQ starter, JDBC),
jOOQ 3.19.35, Flyway, PostgreSQL 16, Micrometer with the Prometheus registry, Jackson (transitive
through `spring-boot-starter-web`), Spring scheduling (in `spring-context`, already present),
JUnit 5, AssertJ, MockMvc, Testcontainers

**Storage**: PostgreSQL 16. One new table, `outbox_events`, with a `jsonb` payload column. One
Flyway migration, `V4__outbox_events.sql`. No change to an existing table.

**Testing**: `./gradlew test` for the unit tests and the integration tests with Testcontainers.
`./gradlew check` for the coverage, architecture, style, and complexity gates. All `*IT` classes
run in the `test` task by name convention.

**Target Platform**: Linux container with a JRE 21, started by `docker compose up --build`

**Project Type**: single-module hexagonal web service

**Performance Goals**: one `INSERT` for each mutation in the existing transaction, and no wait for
a delivery. The poll has a fixed delay of 500 ms and a batch of 50. SC-004 requires 0 pending
events within 60 s after a start.

**Constraints**: bundle coverage floors of 0.95 for line, branch, method, and complexity. The class
rules for `GlobalExceptionHandler` (1.00) and `JooqSlotRepository` (0.95) stay. The ArchUnit rules
stay, and the new packages obey them. No HTTP endpoint. No broker. All documentation in STE.

**Scale/Scope**: the load-test data set is 1 000 users, approximately 700 000 slots, and 50 000
meetings. The feature adds 5 event types, 1 table, 2 scheduled methods, and 3 meters. The retention
interval is 7 days.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
| --- | --- | --- |
| I. Domain-centric hexagonal architecture | The domain owns the event records. The application owns the ports and the transaction. The adapters own the table, the serialization, the schedule, and the log. The domain uses `java..` only. | PASS |
| II. Database as source of truth | The event row is in the same transaction as the state change. The delivery state is a column with a check constraint. The capture order is the primary key. | PASS |
| III. Test discipline with real dependencies | The integration tests use PostgreSQL through Testcontainers. There is no mocking framework. The relay test uses the real table and the real publisher. | PASS |
| IV. Simplicity and YAGNI at honest scale | One table, one poller, one publisher port. No broker, no second deployable, no dead-letter table. The outbox is necessary because the specification requires a record that survives a restart. Research D1, D5, and D6 give the reason and the rejected simpler options. | PASS |
| V. Observable and documented service | Three meters, one log entry for each event, an ADR, an architecture section, and the plan artifacts. | PASS |

No violation needs an entry in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/008-domain-event-outbox/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── outbox-event.schema.json
│   └── delivery-log.md
├── checklists/
│   └── requirements.md
└── spec.md
```

### Source Code (repository root)

```text
src/main/resources/db/migration/
└── V4__outbox_events.sql                                   (+)

src/main/java/com/minidoodle/calendar/
├── domain/event/
│   ├── CalendarEvent.java                                  (+ sealed interface)
│   ├── SlotCreated.java                                    (+)
│   ├── SlotUpdated.java                                    (+)
│   ├── SlotDeleted.java                                    (+)
│   ├── MeetingBooked.java                                  (+)
│   └── MeetingCancelled.java                               (+)
├── domain/model/TimeSlot.java                              (~ event list, drainEvents)
├── application/port/out/
│   ├── EventOutbox.java                                    (+ append)
│   ├── EventPublisher.java                                 (+ publish)
│   ├── OutboxMessage.java                                  (+ record)
│   └── OutboxMetrics.java                                  (+ 3 meters)
├── application/service/SlotApplicationService.java         (~ @Transactional, append)
├── application/service/MeetingApplicationService.java      (~ append)
├── adapter/out/outbox/
│   ├── JooqEventOutbox.java                                (+ port adapter, queries)
│   ├── OutboxRelay.java                                    (+ schedule, pollOnce, cleanupOnce)
│   └── LoggingEventPublisher.java                          (+ publisher adapter)
├── adapter/out/observability/
│   ├── MicrometerOutboxMetrics.java                        (+)
│   ├── MicrometerSlotMetrics.java                          (~ remove mutation log lines)
│   └── MicrometerMeetingMetrics.java                       (~ remove mutation log lines)
└── adapter/out/persistence/JooqSlotRepository.java         (~ remove own transaction)

src/main/java/com/minidoodle/shared/config/
└── OutboxProperties.java                                   (+ poll, batch, retention, attempts)

src/main/java/com/minidoodle/
└── MiniDoodleApplication.java                              (~ @EnableScheduling)
```

The aggregate raises the events. The application service drains them into the `EventOutbox` port in
the same transaction.

```text
src/test/java/com/minidoodle/
├── calendar/domain/model/TimeSlotEventsTest.java           (+) unit
├── calendar/adapter/out/outbox/OutboxPayloadSerializationTest.java (+) unit
├── calendar/adapter/out/outbox/OutboxCaptureIT.java        (+) transactional
├── calendar/adapter/out/outbox/OutboxDeliveryIT.java       (+) non-transactional
├── calendar/adapter/out/outbox/OutboxCleanupIT.java        (+) non-transactional
├── calendar/adapter/out/outbox/OutboxMetricsIT.java        (+) non-transactional
└── calendar/adapter/in/web/                                (~ no new endpoint)

docs/
├── ARCHITECTURE.md                                         (~ event section)
└── adr/0002-event-outbox.md                                (+)
```

**Structure Decision**: the existing single-module hexagon stays. The package `domain.event` holds
the records. The application ports hold the 4 interfaces and the message record. The package
`adapter.out.outbox` holds the table access, the schedule, and the log publisher. Every new
dependency points inward (R1, R2, R3, R6).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| — | No violation. | — |

## Phase 0 / Phase 1 Artifacts

- [research.md](research.md) — 13 decisions (D1–D13) with reasons and rejected alternatives.
- [data-model.md](data-model.md) — the table, the delivery state, the 5 events, the payloads, and the validation rules.
- [contracts/outbox-event.schema.json](contracts/outbox-event.schema.json) — the JSON Schema of a delivered event.
- [contracts/delivery-log.md](contracts/delivery-log.md) — the log format at each level.
- [quickstart.md](quickstart.md) — the local run and the checks.

## Post-Design Constitution Check

- The design adds no framework type to the domain. R1 and R2 stay green.
- The design adds no adapter type to the application. R3 and the closed-dependency rule stay green.
- The design adds no dependency between an inbound adapter and an outbound adapter. R4 stays green.
- The design adds no cycle between the context packages. R6 stays green.
- The design adds no suppression, no lowered floor, and no new check task.
- The one judgement call is the extra table and poller. Principle IV permits it because the
  specification requires a record that survives a restart. Research D1, D5, and D6 record the
  simpler options and why each one fails a requirement.

## Open Items

- **Ordering across instances**: the order guarantee is for one instance (spec Assumption). A
  partition by `aggregate_id` is the future fix. This plan does not implement it.
- **Schema version policy**: version 1 is fixed. A later change adds fields and increments the
  version. A consumer must ignore an unknown field.
- **Payload retention**: the meeting title stays in the table for 7 days. The information-level log
  entry never contains it.
- **ADR 0001 correction**: the slot wait-cycle text in
  [ADR 0001](../../docs/adr/0001-slot-insert-contention.md) is a separate change. This feature does
  not touch it.
