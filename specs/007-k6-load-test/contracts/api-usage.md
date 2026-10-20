# Contract: HTTP API Usage by the Load Generator

**Feature**: `007-k6-load-test` | **Date**: 2026-10-18

The load test adds **no endpoint**. It is a consumer of the API that already exists, so this
contract records exactly what the generator depends on — the surface that must not silently change
under it.

## Base and identity

| Item | Value |
| --- | --- |
| Base URL (in compose) | `http://app:8080` (`BASE_URL`) |
| Identity header | `X-User-Id: <uuid>` — required on every request |
| Identity value | the VU's own seeded user, `10000000-0000-4000-8000-` + `lpad(to_hex(n), 12, '0')` |
| Content type (writes) | `application/json` |

A missing or malformed header yields `400`; a well-formed header for an unknown user yields `403`.
Both are **unexpected failures** for this harness: they mean the generator is misconfigured or the
dataset is missing, not that the service is under load. They must therefore show up as errors rather
than as silently skipped work.

## Request 1 — availability check (the 95%)

```http
GET /api/v1/availability?from={WINDOW_FROM}&to={WINDOW_TO}
X-User-Id: 10000000-0000-4000-8000-000000000042
```

| Aspect | Value |
| --- | --- |
| Params | `from`, `to` — ISO-8601 `OffsetDateTime`, both required, `to > from`, span ≤ 31 days |
| Window used | `[min(start_at), max(start_at) + 1 hour]` of the seeded dataset (≈25 days) |
| Success | `200`, body `AvailabilityResponse` |
| Response fields the script depends on | `from`, `to`, `intervals[]` with `startAt`, `endAt`, `kind` (`FREE` \| `BUSY` \| `UNALLOCATED`) |
| Non-triviality check | `intervals` must be non-empty and must contain at least one `BUSY` interval (the user has ≥ 600 slots in the window) — guards against measuring an empty calendar (FR-016) |
| Other statuses | `400` invalid frame/missing caller → unexpected failure; `403` unknown caller → unexpected failure |

Deliberately *not* included: `limit`/`offset` don't exist here, and the window is not varied per
iteration — a stable, worst-case-cost window is what makes two runs comparable (FR-019).

## Request 2a — add a slot (first half of the 5%)

```http
POST /api/v1/slots
X-User-Id: 10000000-0000-4000-8000-000000000042
Content-Type: application/json

{"startAt":"2027-01-04T09:00:00Z","durationMinutes":30}
```

| Aspect | Value |
| --- | --- |
| Validation the script must satisfy | `startAt` not null and strictly in the future (`@Future`); `durationMinutes` 1…1440 |
| Placement | a per-(VU, iteration) exclusive hour slot in the future, so one user's manipulations can never collide with each other or with the seeded (past) history |
| Success | `201`, `Location: /api/v1/slots/{id}`, body `SlotResponse` — the script reads `id` from the body |
| Expected refusal | `409` overlap (lost a race or a duplicated index) — **not** an error; recorded as a business refusal and the sequence stops after this step |
| Expected contention | `503` deadlock with a concurrent write — **not** an error; recorded as `slot_contention` and the sequence stops after this step, as with a `409` |
| Unexpected | `400` malformed (a bug in the generator), `403` unknown caller, other `5xx`, timeout |

## Request 2b — convert the slot to a meeting (second half of the 5%)

```http
POST /api/v1/slots/{slotId}/meeting
X-User-Id: 10000000-0000-4000-8000-000000000042
Content-Type: application/json

{"title":"Load meeting","description":"load-test manipulation","participants":[{"name":"P0","email":"p0@loadtest.local"}]}
```

| Aspect | Value |
| --- | --- |
| Validation the script must satisfy | `title` non-blank ≤ 200; `participants` non-empty; each participant has a name or an email |
| Success | `201`, body `MeetingResponse` |
| Expected refusal | `409` slot not free / already holds a meeting — **not** an error; recorded as a business refusal |
| Expected contention | `503` deadlock with a concurrent write — **not** an error; recorded as `slot_contention` |
| Unexpected | as Request 2a |

A manipulation is one logical unit spanning 2a then 2b (FR-015). It is measured as a sequence and it
is counted as **one simulated request** for the 5/95 mix (FR-014, as clarified). Consequences the
dashboard must therefore show honestly: the manipulation flow issues twice as many HTTP calls as
iterations, so raw HTTP request counts sit at roughly 95:10 for reads:manipulation steps.

## Status classification (single source of truth)

| Class | Statuses | Counted as |
| --- | --- | --- |
| Success | `200`, `201`, `204` | the iteration's success |
| **Expected refusal** | `409` on 2a or 2b | `business_refusals` counter (tagged by step); **excluded** from `http_req_failed` |
| **Expected contention** | `503` on 2a or 2b | `slot_contention` counter (tagged by step); **excluded** from `http_req_failed` |
| Unexpected failure | `400`, `403`, `404`, other `5xx`, timeouts | `http_req_failed` → counted against the `< 1%` error-rate target |

`409` is the only business refusal the API can return for these two flows, and it is a legitimate
concurrent-booking outcome at 1000 users and a 5% write rate (spec edge case 1). `503` is not a
refusal but a transient contention outcome: the database aborted the write as a deadlock victim, the
service does not retry it, and it answers `Retry-After` (see
`docs/adr/0001-slot-insert-contention.md`). It is counted separately so that it stays visible
instead of disappearing into the error rate.

## Endpoints deliberately not exercised

`GET /api/v1/slots`, `GET /api/v1/slots/{id}`, `PATCH /api/v1/slots/{id}`,
`DELETE /api/v1/slots/{id}`, `GET`/`DELETE /api/v1/slots/{id}/meeting`, and the OpenAPI/Actuator
surface (except `GET /actuator/health`, which the *orchestration script* — not the load generator —
polls for readiness). No performance claim is made about them.

## Client behaviour

| Aspect | Value |
| --- | --- |
| Request timeout | k6 default 60 s, kept deliberately (a hung request must fail the run, not be retried forever) |
| Retries | none. A refusal or failure is recorded, never retried; retrying would double the write rate and hide the contention the run exists to expose |
| Redirects | not followed (the API returns a `Location` header, not a `3xx`) |
| Connections | k6's per-VU connection pool; no explicit `connection: close` |
