# Core Business Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the core admin-facing business loop without adding the postponed community-hospital adapter work.

**Architecture:** Keep the existing service boundaries. `order-service` owns order detail and fulfillment progress, `report-service` owns report summary and export, `ops-service` owns operational health aggregation, and `admin-web` displays these capabilities through existing proxy APIs.

**Tech Stack:** Spring Boot, JdbcTemplate, PostgreSQL, JUnit/Mockito, Vue 3, TypeScript, Vite.

---

### Task 1: Order Fulfillment Progress

**Files:**
- Modify: `backend/order-service/src/main/java/com/zhyf/order/api/InstitutionOrderController.java`
- Modify: `backend/order-service/src/main/java/com/zhyf/order/application/OrderService.java`
- Modify: `backend/order-service/src/main/java/com/zhyf/order/infrastructure/OrderRepository.java`
- Modify: `backend/order-service/src/test/java/com/zhyf/order/infrastructure/OrderRepositoryTest.java`
- Modify: `frontend/admin-web/src/api/types.ts`
- Modify: `frontend/admin-web/src/api/order.ts`
- Modify: `frontend/admin-web/src/App.vue`

- [x] Add failing repository test for a joined order progress snapshot.
- [x] Implement repository records and SQL for order, prescriptions, workflow tasks, dispense records, decoction tasks, shipment, callback, and status logs.
- [x] Add service/controller API `GET /api/admin/orders/{orderNo}/progress`.
- [x] Add frontend API types and render progress under order lookup.
- [x] Run `mvn -pl order-service -am test -q` and `pnpm build`.

### Task 2: Report CSV Export

**Files:**
- Modify: `backend/report-service/src/main/java/com/zhyf/report/api/ReportController.java`
- Modify: `backend/report-service/src/main/java/com/zhyf/report/application/ReportQueryService.java`
- Modify: `backend/report-service/src/test/java/com/zhyf/report/application/ReportQueryServiceTest.java`
- Modify: `frontend/admin-web/src/api/report.ts`
- Modify: `frontend/admin-web/src/App.vue`

- [x] Add failing service test for CSV export content.
- [x] Implement UTF-8 CSV export for overview metrics, status distribution, callback distribution, and daily trend.
- [x] Add `GET /api/admin/reports/overview.csv`.
- [x] Add frontend download button in report view.
- [x] Run `mvn -pl report-service -am test -q` and `pnpm build`.

### Task 3: Ops Health Overview

**Files:**
- Modify: `backend/ops-service/src/main/java/com/zhyf/ops/api/OpsQueryController.java`
- Modify: `backend/ops-service/src/main/java/com/zhyf/ops/application/OpsQueryService.java`
- Modify: `backend/ops-service/src/main/java/com/zhyf/ops/application/OpsRecords.java`
- Modify: `backend/ops-service/src/main/java/com/zhyf/ops/infrastructure/OpsQueryRepository.java`
- Modify: `backend/ops-service/src/test/java/com/zhyf/ops/application/OpsQueryServiceTest.java`
- Modify: `backend/ops-service/src/test/java/com/zhyf/ops/infrastructure/OpsQueryRepositoryTest.java`
- Modify: `frontend/admin-web/src/api/types.ts`
- Modify: `frontend/admin-web/src/api/ops.ts`
- Modify: `frontend/admin-web/src/App.vue`

- [x] Add failing service/repository tests for health overview limit normalization and SQL aggregation.
- [x] Implement `GET /api/admin/ops/health-overview`.
- [x] Aggregate outbox, consume failures, validation failures, callback failures, integration retry failures, and recent access volume.
- [x] Add frontend health panel to ops view.
- [x] Run `mvn -pl ops-service -am test -q` and `pnpm build`.

### Task 4: Documentation And Final Verification

**Files:**
- Modify: `docs/00_项目总览/上下文管理.md`
- Modify: `docs/00_项目总览/modules.md`
- Modify: `docs/99_项目记录/项目记录.md`

- [x] Update current completed link and API list.
- [x] Record postponed community-hospital adapter scope.
- [x] Run full `mvn test -q`, `pnpm build`, `git diff --check`, and sensitive scan.
- [x] Commit with a Chinese message.
