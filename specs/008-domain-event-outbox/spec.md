# Feature Specification: Domain Event Outbox

<!--
  Write generated prose in ASD-STE100 Simplified Technical English (STE).
  Read AGENTS.md and docs/WRITING.md before you write.
  Preserve recorded input, requirements, identifiers, commands, and code.
-->

**Feature Branch**: `008-domain-event-outbox`

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "Add events on important flows. Some component captures the events asynchronously inside the application. For now, the component only writes the events to the log. Use the transaction outbox pattern."

## Terms

- **Event**: a record of one change to the calendar data. The record has a type, a time, and the changed facts.
- **Capture**: the action that writes an event record with the state change.
- **Changed facts**: the new values of the fields that the operation changed.
- **Outbox**: the saved event records that wait for delivery.
- **Delivery**: the action that sends a captured event to the delivery target.
- **Delivery target**: the component that receives the events. In this feature, it is the application log.
- **Delivery cycle**: one run of the delivery action.
- **Background action**: an action that does not run in the request.
- **Published event**: an event that the delivery target received.
- **Pending event**: an event that the delivery target did not receive.
- **Failed event**: a pending event with 5 failed delivery attempts. The system does not try again.
- **Retention interval**: the time during which the system keeps published events. The interval is 7 days.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture in the same unit as the state change (Priority: P1)

A caller books a meeting. The service saves the meeting and changes the time slot to BUSY. In the same unit, the service writes one `meeting.booked` event. A later failure removes the state change and the event together. The event is available after a restart of the service.

**Why this priority**: This is the value of the feature. The event record exists only when the state change succeeds. The state change never exists without its event.

**Independent Test**: Book a meeting and read the event records. Exactly one `meeting.booked` record MUST exist with the slot identifier. Then cause a failed booking and read the records again. No new record MUST exist.

**Acceptance Scenarios**:

1. **Given** a free time slot, **When** the caller books a meeting, **Then** one `meeting.booked` record exists. It has the slot identifier, the meeting identifier, the title, and the number of participants.
2. **Given** a booking that fails, **When** the operation stops, **Then** no event record exists and no log entry exists.
3. **Given** a time slot, **When** the caller sends an update that changes no value, **Then** no event record exists.
4. **Given** a time slot with a meeting, **When** the caller cancels the meeting, **Then** one `meeting.cancelled` record exists. It has the slot identifier and the meeting identifier.

---

### User Story 2 - Delivery to the log in a background action (Priority: P2)

A background action sends each captured event to the application log. The request does not wait for the write to the log. The action starts after the request completes.

**Why this priority**: The record is useful only when a component receives it. This story proves the delivery path. The log gives the first visible output of the feature.

**Independent Test**: Capture one event and wait for one delivery cycle. One log entry MUST exist at the information level, and the record MUST show the published state. Then cause 5 failed deliveries and check that the record shows the failed state.

**Acceptance Scenarios**:

1. **Given** one pending event, **When** the delivery cycle runs, **Then** the log has one entry. The entry has the event identifier, the event type, the schema version, the aggregate identifiers, and the time of the change.
2. **Given** one pending event and a failed delivery, **When** the delivery cycle runs again, **Then** the attempt count increases and the record stays pending.
3. **Given** 5 failed delivery attempts, **When** the delivery cycle runs again, **Then** the record has the failed state and the system does not try again.
4. **Given** two events of one time slot, **When** the delivery cycle runs, **Then** the log shows the events in capture order.
5. **Given** a stop of the service with pending events, **When** the service starts again, **Then** the system sends the pending events.

---

### User Story 3 - Operations visibility and retention (Priority: P3)

An operator reads the state of the outbox in the metrics. The system removes old published records after the retention interval.

**Why this priority**: The operator cannot see a failed event without a metric. The operator needs the count of pending events and the count of failed events. The retention rule keeps the event table small.

**Independent Test**: Publish events and read the metrics. The metrics MUST show 3 published events and 0 failed events. Then create 1 pending event and read the metrics again. The metrics MUST show 1 pending event. Then create published records older than 7 days and run the cleanup action. No old record MUST remain.

**Acceptance Scenarios**:

1. **Given** 3 published events, **When** the operator reads the metrics, **Then** the metrics show the count of published events as 3.
2. **Given** 1 pending event, **When** the operator reads the metrics, **Then** the metrics show the count of pending events as 1.
3. **Given** 1 failed event, **When** the operator reads the metrics, **Then** the metrics show the count of failed events as 1.
4. **Given** a published record older than 7 days, **When** the cleanup action runs, **Then** the record is absent.

---

### Edge Cases

- A caller sends the same booking request two times. The second request fails with a conflict. The system writes one event record only.
- The delivery cycle starts before the state change is saved. The delivery cycle MUST NOT see the event record.
- The service stops during a delivery. After a new start, the system sends the event again. A future consumer can receive the same event two times.
- The count of pending events is large. The delivery cycle MUST limit the batch size.
- A caller reads a time slot, a page of time slots, the availability, or a meeting. The system writes no event record.
- A caller deletes a time slot that holds a meeting. The operation fails. The system writes no event record.
- A delivery target fails for all events. The system MUST NOT block the request path.
- The event table holds a failed event. The cleanup action MUST NOT remove it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST write exactly one event record for each successful change in these flows:
  - time slot creation
  - time slot update
  - time slot deletion
  - meeting booking
  - meeting cancellation
- **FR-002**: The system MUST NOT write an event record for a read operation.
- **FR-003**: The system MUST NOT write an event record when an operation changes no value.
- **FR-004**: The system MUST save the event record and the state change as one unit. A failure of one MUST remove the other. The delivery cycle MUST NOT see an event record before its save completes.
- **FR-005**: Each event record MUST contain these values:
  - a unique event identifier
  - an event type
  - a schema version
  - an aggregate type
  - an aggregate identifier
  - a calendar identifier
  - the time of the change
  - the changed facts
- **FR-006**: The 5 event types MUST use the time slot as the aggregate. A meeting event MUST contain the meeting identifier in the changed facts.
- **FR-007**: The changed facts MUST NOT contain the name or the email address of a participant. The changed facts MUST contain the number of participants.
- **FR-008**: The system MUST send the events in a background action. A request MUST NOT wait for a delivery.
- **FR-009**: The delivery target MUST be the application log. The system MUST write one log entry for each delivered event.
- **FR-010**: The log entry MUST contain the event identifier, the event type, the schema version, the aggregate identifiers, and the time of the change. The changed facts MUST be available at the debug level only.
- **FR-011**: The system MUST record the delivery state of each event as pending, published, or failed.
- **FR-012**: When a delivery fails, the system MUST increase the attempt count and MUST try again in a later delivery cycle.
- **FR-013**: After 5 failed attempts, the system MUST set the failed state and MUST NOT try the delivery of that event again.
- **FR-014**: The system MUST keep the capture order of the events of one time slot when one application instance runs.
- **FR-015**: The system MUST send every pending event after a restart of the service.
- **FR-016**: The system MUST publish these metrics: the count of published events, the count of failed events, and the count of pending events.
- **FR-017**: The system MUST remove published events after the retention interval. The system MUST keep failed events.
- **FR-018**: The system MUST NOT add an HTTP endpoint for the events. The system MUST NOT change the response of an existing endpoint.
- **FR-019**: The system MUST write one mutation-event log entry for each event. The existing event log entries in the metrics components MUST be absent for the 5 mutation flows. The read-path log entries MUST remain.

### Key Entities *(include if feature involves data)*

- **Event record**: the record of one change that the system saves. It has these values:
  - an event identifier
  - an event type
  - a schema version
  - an aggregate type
  - an aggregate identifier
  - a calendar identifier
  - the time of the change
  - the changed facts
  - the delivery state
  - the attempt count
  - the last error
  - the published time
- **Time slot**: the aggregate for the 5 event types. Its identifier groups the events of one slot.
- **Meeting**: the subject of a booking event and a cancellation event. The changed facts contain its identifier and its title.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Each successful operation in the 5 flows gives exactly one event record. The test suite shows 0 exceptions.
- **SC-002**: Each failed operation gives 0 event records and 0 log entries.
- **SC-003**: A request completes before its delivery starts. The test suite shows 0 requests that wait for a delivery.
- **SC-004**: When the delivery target works, the count of pending events is 0 within 60 seconds after the service starts.
- **SC-005**: One log entry exists for each delivered event. The test suite shows 0 duplicate entries.
- **SC-006**: An operator sees the counts of published, pending, and failed events in the metrics within 60 seconds after a change of the state.
- **SC-007**: After the cleanup action, 0 published records older than 7 days remain.

## Assumptions

- One application instance runs. The order guarantee in FR-014 applies to one instance only.
- The application log is the only delivery target in this feature. A message broker is out of scope.
- The feature adds no HTTP endpoint and no consumer service.
- The 5 flows are the complete set of important flows. A read operation does not change the state.
- Delivery is at-least-once. A future consumer MUST discard a duplicate event.
- The retention interval of 7 days is sufficient for this stage.
- The title of a meeting is not personal data, so the changed facts can contain it.
- The existing metrics stay. The new metrics extend them.
