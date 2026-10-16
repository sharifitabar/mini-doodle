---

description: "Task list template for feature implementation"
---

# Tasks: Metrics Dashboards

**Input**: Design documents from `/specs/004-metrics-dashboards/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below assume single project - adjust based on plan.md structure

<!--
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.

  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/

  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment

  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare directory structure and files for Prometheus/Grafana

- [ ] T001 Create `prometheus/` directory and `prometheus/prometheus.yml` skeleton per implementation plan
- [ ] T002 Create `grafana/provisioning/datasources/` and `grafana/provisioning/dashboards/` directories and provisioning files skeleton

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 [P] Write `prometheus/prometheus.yml` to scrape `app:8080/actuator/prometheus` every 15s with job `minidoodle`
- [ ] T004 [P] Write `grafana/provisioning/datasources/prometheus.yml` to configure Prometheus datasource
- [ ] T005 [P] Write `grafana/provisioning/dashboards/dashboards.yml` to provision dashboards from `./dashboards`
- [ ] T006 Create `grafana/dashboards/mini-doodle-baseline.json` with core panels (slot ops, meeting ops, availability queries, http.server.requests, JVM/process)
- [ ] T007 Extend `docker-compose.yml` to add `prometheus` and `grafana` services with named volumes and health/dependency ordering
- [ ] T008 Update `README.md` with metrics stack instructions, URLs (9090/3000), and demo credentials

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Run metrics stack with a single command (Priority: P1) 🎯 MVP

**Goal**: Prometheus + Grafana start healthy via `docker compose up` with provisioning-as-code and no manual setup.

**Independent Test**: After `docker compose up`, Prometheus and Grafana are healthy, Prometheus has a target for `app:8080/actuator/prometheus` UP, and Grafana has a provisioned Prometheus datasource.

### Implementation for User Story 1

- [ ] T009 [US1] Start services with `docker compose up -d` and verify Prometheus targets and Grafana readiness (depends on T003–T007)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Browse and inspect key metrics (Priority: P2)

**Goal**: Provisioned baseline dashboard displays existing core metrics (slot/meeting ops, availability queries, http.server.requests, JVM/process).

**Independent Test**: Baseline dashboard renders live panels for the core metric families without manual configuration.

### Implementation for User Story 2

- [ ] T010 [US2] Verify dashboard JSON renders core metric families and is loaded by Grafana provisioning (depends on T006)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Data and dashboards persist across restarts (Priority: P3)

**Goal**: Named volumes ensure Prometheus and Grafana data/dashboards persist across `docker compose down`/`up`.

**Independent Test**: After restart, datasource and dashboards remain present and Prometheus data volume is mounted.

### Implementation for User Story 3

- [ ] T011 [US3] Verify named volumes are mounted and persist data across restart (depends on T007,T009)

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in docs/
- [ ] TXXX Code cleanup and refactoring
- [ ] TXXX Performance optimization across all stories
- [ ] TXXX [P] Additional unit tests (if requested) in tests/unit/
- [ ] TXXX Security hardening
- [ ] TXXX Run quickstart.md validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (if tests requested):
Task: "Contract test for [endpoint] in tests/contract/test_[name].py"
Task: "Integration test for [user journey] in tests/integration/test_[name].py"

# Launch all models for User Story 1 together:
Task: "Create [Entity1] model in src/models/[entity1].py"
Task: "Create [Entity2] model in src/models/[entity2].py"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
