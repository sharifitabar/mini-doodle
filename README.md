# mini-doodle

A high-performance backend simulation of a meeting scheduling platform (a "mini Doodle"). Users manage personal calendars of time slots, mark them busy/free, and convert free slots into meetings with a title, description, and participants.

> **Status:** First draft. Sections marked _TBD_ are still being finalized.

---

## Overview

The service exposes an HTTP API that lets users:

- **Manage time slots** — create available time slots with a configurable duration inside a personal calendar, modify or delete existing slots, and toggle them between `FREE` and `BUSY`.
- **Schedule meetings** — convert available (free) slots into meetings, attaching details such as title, description, and participants.
- **Query availability** — ask for free or busy slots and get an aggregated view for a selected time frame.

The platform is designed to be used by hundreds of users with thousands of slots, so the design favours efficient storage, indexed range queries, and bounded result sets.

## Tech stack

- **Java 21** (LTS) with **Spring Boot 3.5.x**
- **Gradle** (Kotlin DSL) with wrapper — `./gradlew`
- **Hexagonal architecture** (ports & adapters) with DDD tactical patterns
- **jOOQ** over **PostgreSQL** for persistence, **Flyway** for versioned migrations
- Slot overlap prevented by a PostgreSQL `tstzrange` + GIST exclusion constraint
- **Docker** & **docker-compose** for local, one-command startup

## Domain concepts

| Concept | Description |
| --- | --- |
| **Calendar** | A user's personal container for their time. Internal domain concept; not exposed as a first-class API resource. |
| **Time slot** | A time interval with a configurable duration inside a calendar. Has a status of `FREE` or `BUSY`. |
| **Meeting** | A booked slot with a title, description, and one or more participants. |

A slot can be booked as a meeting. Each user has a personal calendar that manages their time.

## Getting started

### Prerequisites

- Docker and Docker Compose

### Run locally

_TBD: docker-compose service definitions are not committed yet._

```bash
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

_TBD: add health check endpoint and default seeded data details._

## API

_TBD: endpoints, request/response examples, and error model._

Planned resource groups:

- `POST /calendars/{calendarId}/slots` — create a time slot
- `PATCH /slots/{slotId}` — modify a slot (time, duration, status)
- `DELETE /slots/{slotId}` — delete a slot
- `POST /slots/{slotId}/meeting` — convert a free slot into a meeting
- `GET /availability?from=...&to=...&status=free|busy` — aggregated availability for a time frame

## Data model

_TBD: entity fields, indexes, and constraints (particularly range/index strategy for slot queries)._

## Configuration

_TBD: environment variables (database URL, credentials, pool sizing, pagination limits)._

## Testing

The suite uses JUnit 5, AssertJ, MockMvc, and Testcontainers (real PostgreSQL).

```bash
./gradlew test
```

## Project structure

```
src/
  main/java/...   # application code
  main/resources/ # configuration
  test/java/...   # tests
docs/             # challenge brief and project docs
docker-compose.yml
```

_TBD: confirm layout once the build tool is chosen._

## Design decisions

_TBD: document the key trade-offs — storage of intervals, overlapping-slot rules, concurrency when booking a slot, query pagination, and indexing for large-scale availability lookups._

## Notes

- The challenge brief references a "composer file"; this is interpreted as the `docker-compose.yml` that defines all service dependencies, and it will contain all required dependencies for the service.
- Data is persisted so it can be properly managed and queried.

## Roadmap

- [ ] Project skeleton (Spring Boot + build tool)
- [ ] Docker Compose environment (app + PostgreSQL)
- [ ] Time slot CRUD and status management
- [ ] Meeting scheduling from slots
- [ ] Availability query with aggregated view
- [ ] Tests
- [ ] Metrics and observability
- [ ] API documentation (OpenAPI)
