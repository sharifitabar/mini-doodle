# Implementation Plan: Metrics Dashboards

**Branch**: `004-metrics-dashboards` | **Date**: 2026-10-16 | **Spec**: specs/004-metrics-dashboards/spec.md

**Input**: Feature specification from `/specs/004-metrics-dashboards/spec.md`

**Note**: The `/speckit.plan` command fills in this template.
Its definition describes the execution workflow.

## Summary

Add Prometheus and Grafana to docker-compose.
Use version-controlled provisioning and named volumes.
Collect metrics from the existing `/actuator/prometheus` endpoint.
This feature does not change application code.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21

**Primary Dependencies**: Prometheus, Grafana, Micrometer Prometheus registry (already present), Spring Boot Actuator

**Storage**: Docker named volumes (Prometheus TSDB, Grafana data/config)

**Testing**: Docker Compose smoke test (stack starts healthy, endpoints respond). No new application unit tests required (no app code changes).

**Target Platform**: Linux (Docker Compose local)

**Project Type**: Observability/infrastructure stack (Docker Compose + provisioning)

**Performance Goals**: Prometheus scrapes app /actuator/prometheus every 15s. Grafana dashboard loads < 2s. Stack healthy within ~60s of docker compose up

**Constraints**: No application code changes (reuse existing /actuator/prometheus). Provisioning as code. Persistent volumes. Demo credentials only

**Scale/Scope**: Single app instance. Local demo. Baseline dashboards only (no alerting/tracing)

## Constitution Check

*GATE: Must pass before Phase 0 research. Check again after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Domain-Centric Hexagonal Architecture | No application/domain code changes. Only infrastructure/provisioning (prometheus, grafana, docker-compose). | PASS |
| II. Database as Source of Truth | No schema/migration changes. | PASS |
| III. Test Discipline with Real Dependencies | Stack smoke test through docker-compose. No new app unit tests required. | PASS |
| IV. Simplicity & YAGNI at Honest Scale | Minimal stack (Prometheus + Grafana), version-controlled provisioning, reuse existing /actuator/prometheus. No Alertmanager/tracing. | PASS |
| V. Observable & Documented Service | Adds Prometheus scrape and Grafana dashboards. Updates README with URLs/credentials/usage. | PASS |

No violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
.
├── docker-compose.yml           # extend with prometheus and grafana
├── prometheus/
│   └── prometheus.yml           # scrape job for /actuator/prometheus
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml
│   │   └── dashboards/
│   │       └── dashboards.yml
│   └── dashboards/
│       └── mini-doodle-baseline.json
└── README.md                    # update with metrics stack docs
```
**Structure Decision**: The above tree reflects the new files and modifications. No application code changes required.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

_No violations identified. Section is empty._

