# Feature Specification: Metrics Dashboards (Prometheus + Grafana)

**Feature Branch**: `004-metrics-dashboards`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "Add a Prometheus and Grafana metrics collection and dashboard stack to docker-compose, provisioned as code with persistent volumes, scraping the application's existing /actuator/prometheus endpoint."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run metrics stack with a single command (Priority: P1)

An operator starts the full stack (`app + db + Prometheus + Grafana`) with one command.
Metrics and dashboards are available without manual UI configuration.

**Why this priority**: The stack must be reproducible and easy to run.
Automatic provisioning is the main purpose of this feature.

**Independent Test**: `docker compose up` brings Prometheus and Grafana up in a healthy state, and Grafana loads a provisioned Prometheus datasource and at least one provisioned dashboard.

**Acceptance Scenarios**:

1. **Given** the codebase is clean, **When** `docker compose up` completes, **Then** Prometheus, Grafana, app, and db are running and report healthy status.
2. **Given** Grafana is started, **When** an operator opens Grafana, **Then** a Prometheus datasource is already configured (no manual setup).
3. **Given** Grafana is started, **When** an operator opens the provisioned dashboard, **Then** panels render live metrics from the app without manual configuration.

---

### User Story 2 - Browse and inspect key metrics (Priority: P2)

An operator wants to browse key metrics already emitted by the application (request rates/latencies, slot/meeting operations, availability queries) on a baseline dashboard.

**Why this priority**: Surfacing the existing metrics is the primary reason for adding the stack.

**Independent Test**: The provisioned baseline dashboard displays time-series for at least the existing core metric families and allows filtering by time range.

**Acceptance Scenarios**:

1. **Given** the stack is running, **When** viewing the baseline dashboard, **Then** the dashboard shows slot operation counts, meeting operation counts, availability queries, HTTP server request metrics, and basic JVM/process metrics.
2. **Given** traffic has been generated, **When** refreshing the dashboard, **Then** series reflect recent activity.
3. **Given** the dashboard is provisioned, **When** Grafana restarts, **Then** the same dashboard and datasource are present.

---

### User Story 3 - Data and dashboards persist across restarts (Priority: P3)

An operator wants data and dashboards to survive container restarts so they are not lost on `docker compose down/up`.

**Why this priority**: A local demo must be reproducible and preserve its data.
Without volumes, dashboards and short-term metrics history are temporary.

**Independent Test**: After `docker compose down`, then `docker compose up`, the provisioned datasource and dashboards remain and Prometheus starts with its retained data volume mounted.

**Acceptance Scenarios**:

1. **Given** the stack has been running and collected metrics, **When** `docker compose down` and `docker compose up`, **Then** the Grafana provisioned datasource and dashboards remain intact.
2. **Given** Prometheus has been running for a short period, **When** the stack is restarted, **Then** Prometheus uses a persistent volume so that short-term time-series history remains accessible.

---

### Edge Cases

- **App unavailable at scrape time**: Prometheus marks the target DOWN. Grafana panels show gaps or no data without crashing.
  Manual intervention is not required.
- **First boot ordering**: Grafana starts after Prometheus becomes healthy (or after scrape target is reachable). Provisioning succeeds without races.
- **Port conflicts**: Default ports (Prometheus 9090, Grafana 3000) may conflict on the host. The compose file must be explicit about published ports so conflicts are visible.
- **Restart resilience**: `docker compose down` followed by `up` yields the same provisioned configuration.
- **Existing metrics endpoint**: Scraping the already-exposed `/actuator/prometheus` works without any application code changes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `docker-compose.yml` MUST add a Prometheus service configured to scrape the application's Prometheus endpoint (`/actuator/prometheus`) at a fixed interval.
- **FR-002**: The `docker-compose.yml` MUST add a Grafana service with Prometheus configured as a provisioned datasource (no manual UI setup required).
- **FR-003**: Grafana datasource and at least one baseline dashboard MUST be provisioned from version-controlled files (provisioning-as-code).
- **FR-004**: Prometheus and Grafana data MUST persist across container restarts through named volumes.
- **FR-005**: The stack MUST reuse the application's existing metrics endpoint (`/actuator/prometheus`) with no application code changes required for metrics exposition.
- **FR-006**: Prometheus and Grafana UIs MUST be reachable from the host on their published ports.
- **FR-007**: The stack MUST start with a single `docker compose up` and reach a healthy state without manual configuration steps.
- **FR-008**: The provisioned baseline dashboard MUST surface the core metrics already emitted by the application: slot operations, meeting operations, availability queries, HTTP server request metrics, and JVM/process metrics.
- **FR-009**: `README.md` MUST document how to run the stack, the service URLs, Grafana credentials (demo), and how to add/modify provisioned dashboards.
- **FR-010**: Provisioning files MUST live under the repository and be mounted into the containers (version-controlled and reproducible).
- **FR-011**: Baseline dashboards MUST cover Golden Signals (Traffic, Latency, Errors, Saturation) using existing Actuator/Micrometer metrics.
- **FR-012**: Baseline dashboards MUST include basic SLI/SLO indicators (request rate, p95 latency, 5xx error rate).

### Key Entities *(include if feature involves data)*

- **Prometheus scrape job**: Defines the target (`app:8080` / `/actuator/prometheus`) and scrape interval for pulling metrics from the application.
- **Grafana datasource provisioning**: Version-controlled configuration that registers Prometheus as a datasource so Grafana can query it without manual setup.
- **Grafana dashboard provisioning**: Version-controlled configuration that loads dashboard JSON files at startup, ensuring dashboards exist reproducibly.
- **Dashboard JSON**: A baseline dashboard definition that renders the existing metric families in panels (time series) with sensible defaults.
- **Named volumes**: Persisted storage for Prometheus TSDB data and Grafana configuration/data so dashboards and history survive restarts.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After `docker compose up`, the operator can access Prometheus targets (target UP) and the provisioned Grafana dashboard within one minute, with no manual configuration steps.
- **SC-002**: After `docker compose down` and `docker compose up`, the provisioned datasource and dashboards remain present, and the stack reaches a healthy state.
- **SC-003**: The provisioned baseline dashboard displays at least the five core metric families (slot ops, meeting ops, availability queries, HTTP server requests, JVM/process) when data is available.
- **SC-004**: The metrics shown are sourced exclusively from the existing `/actuator/prometheus` endpoint (no new application instrumentation added as part of this feature).
- **SC-005**: A fresh checkout can run `docker compose up` and obtain a working metrics stack using only version-controlled files.

## Assumptions

- **Scope is the stack only**: This feature adds Prometheus + Grafana to docker-compose with provisioning-as-code and volumes. Adding new application metrics or altering percentile/histogram configuration is out of scope.
- **Single-instance demo**: Local docker-compose with a single `app` instance. No service discovery beyond static targets.
- **Demo credentials**: Grafana uses default/demo credentials documented in README (not production-hardened).
- **Default ports**: Prometheus on 9090, Grafana on 3000 (explicitly published). Conflicts are assumed to be handled by the operator if they occur.
- **Retention**: Prometheus uses reasonable default retention for a local demo (documented in provisioning). No long-term archival.
- **Observability of HTTP and JVM**: The application already exposes `http.server.requests` and JVM/process metrics through Actuator/Micrometer. They are included in the baseline dashboard.
- **Alerting and tracing are out of scope**: No Alertmanager, no OpenTelemetry/tracing integration, no exemplars in this feature.
- **Persistence is local**: Named volumes are sufficient for the local demo environment.

(End of file)
