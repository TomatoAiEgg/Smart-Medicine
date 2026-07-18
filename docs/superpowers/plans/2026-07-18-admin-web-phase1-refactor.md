# admin-web Phase 1 Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first-round `admin-web` application shell and move order, workflow, report, and ops views out of the current 3000-line `App.vue`.

**Architecture:** Keep the existing Vue 3 + TypeScript + Vite stack and current API clients. Introduce a small app shell, route-like local view registry, feature modules, and shared formatting helpers without adding a new router or UI library in this phase.

**Tech Stack:** Vue 3 Composition API, TypeScript, Vite, existing CSS, existing API clients, `vue-tsc` build verification.

---

## Scope

This plan implements phase one from `docs/03_架构设计/admin-web前端重构方案.md`:

- Create an admin application shell and business menu.
- Split `App.vue` so it no longer owns order, workflow, reports, and ops business logic.
- Preserve currently working APIs and user workflows.
- Keep decoction, logistics, portal, and integration in `App.vue` for the next phase to reduce blast radius.

Out of scope:

- Adding Vue Router.
- Adding a component library.
- Adding Vitest or test files.
- Reworking backend APIs.
- Migrating decoction, logistics, portal, and integration pages.

## Files

Create:

- `frontend/admin-web/src/app/views.ts` - central view keys, menu items, title/subtitle metadata.
- `frontend/admin-web/src/app/AppLayout.vue` - sidebar, topbar, notice display, and content slot.
- `frontend/admin-web/src/domain/formatters.ts` - shared date/number/default-date helpers.
- `frontend/admin-web/src/domain/status.ts` - shared status tone helper.
- `frontend/admin-web/src/features/orders/OrderCenter.vue` - order query and progress detail page.
- `frontend/admin-web/src/features/workflow/WorkflowTasks.vue` - review, dispense, and recheck task page.
- `frontend/admin-web/src/features/reports/ReportOverview.vue` - report overview and CSV export page.
- `frontend/admin-web/src/features/ops/OpsConsole.vue` - health overview and ops datasets page.

Modify:

- `frontend/admin-web/src/App.vue` - keep global state, remaining phase-two pages, and mount new feature components through `AppLayout`.
- `frontend/admin-web/src/styles/base.css` - add layout compatibility rules only if new components need classes not already covered.
- `docs/00_项目总览/上下文管理.md` - record the phase-one frontend refactor once implemented.
- `docs/99_项目记录/项目记录.md` - record changed frontend structure and verification result.

Verify:

- `cd frontend/admin-web; pnpm build`
- `git diff --check`
- `git status --short`

## Task 1: App View Registry And Layout

**Files:**

- Create: `frontend/admin-web/src/app/views.ts`
- Create: `frontend/admin-web/src/app/AppLayout.vue`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Create view metadata**

Create `frontend/admin-web/src/app/views.ts`:

```ts
export type ViewKey =
  | 'dashboard'
  | 'orders'
  | 'reviews'
  | 'dispenses'
  | 'rechecks'
  | 'decoction'
  | 'logistics'
  | 'portal'
  | 'reports'
  | 'integration'
  | 'ops';

export interface MenuItem {
  key: ViewKey;
  label: string;
  group: string;
}

export const menuItems: MenuItem[] = [
  { key: 'dashboard', label: '工作台', group: '总览' },
  { key: 'orders', label: '订单中心', group: '核心业务' },
  { key: 'reviews', label: '审核任务', group: '药房作业' },
  { key: 'dispenses', label: '调剂任务', group: '药房作业' },
  { key: 'rechecks', label: '复核任务', group: '药房作业' },
  { key: 'decoction', label: '煎煮作业', group: '履约作业' },
  { key: 'logistics', label: '物流发货', group: '履约作业' },
  { key: 'portal', label: '门户查单', group: '门户集成' },
  { key: 'integration', label: '集成任务', group: '门户集成' },
  { key: 'reports', label: '报表统计', group: '统计运维' },
  { key: 'ops', label: '运维审计', group: '统计运维' },
];

export const viewTitles: Record<ViewKey, { title: string; subtitle: string }> = {
  dashboard: { title: '工作台', subtitle: '查看核心待处理事项和系统健康概览' },
  orders: { title: '订单中心', subtitle: '查询订单详情、处方和履约进度' },
  reviews: { title: '审核任务', subtitle: '处理待审核订单' },
  dispenses: { title: '调剂任务', subtitle: '处理待调剂处方任务' },
  rechecks: { title: '复核任务', subtitle: '处理待复核处方任务' },
  decoction: { title: '煎煮作业', subtitle: '处理处方绑定、煎煮状态和设备作业记录' },
  logistics: { title: '物流发货', subtitle: '处理打包、发货、签收和回调补偿' },
  portal: { title: '门户查单', subtitle: '医院查单和地址补录申请' },
  integration: { title: '集成任务', subtitle: '外围消息、地址回推和重试任务' },
  reports: { title: '报表统计', subtitle: '查看核心指标、状态分布和趋势导出' },
  ops: { title: '运维审计', subtitle: '查询事件、消费、校验、访问和失败任务' },
};
```

- [ ] **Step 2: Create the app layout component**

Create `frontend/admin-web/src/app/AppLayout.vue`:

```vue
<script setup lang="ts">
import type { MenuItem, ViewKey } from './views';

defineProps<{
  activeView: ViewKey;
  title: string;
  subtitle: string;
  menuItems: MenuItem[];
  counts: Partial<Record<ViewKey, number>>;
  notice: { tone: 'info' | 'success' | 'error'; text: string } | null;
}>();

defineEmits<{
  switchView: [view: ViewKey];
}>();
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">药</div>
        <div>
          <strong>智能药房</strong>
          <span>SaaS 管理后台</span>
        </div>
      </div>

      <nav class="nav">
        <button
          v-for="item in menuItems"
          :key="item.key"
          type="button"
          :class="{ active: activeView === item.key }"
          @click="$emit('switchView', item.key)"
        >
          <span>{{ item.label }}</span>
          <b v-if="counts[item.key]">{{ counts[item.key] }}</b>
        </button>
      </nav>

      <div class="service-panel">
        <span>本地代理</span>
        <code>/order-api /workflow-api /ops-api</code>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <p>{{ subtitle }}</p>
          <h1>{{ title }}</h1>
        </div>
      </header>

      <div v-if="notice" class="notice" :class="notice.tone">
        {{ notice.text }}
      </div>

      <slot />
    </main>
  </div>
</template>
```

- [ ] **Step 3: Wire layout into `App.vue` without moving pages yet**

Modify the imports and layout wrapper in `frontend/admin-web/src/App.vue`:

```ts
import AppLayout from './app/AppLayout.vue';
import { menuItems, viewTitles, type ViewKey } from './app/views';
```

Remove the local `type ViewKey = ...` definition. Add:

```ts
const currentViewTitle = computed(() => viewTitles[activeView.value]);
const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => ({
  reviews: reviewTasks.value.length,
  dispenses: dispenseTasks.value.length,
  rechecks: recheckTasks.value.length,
  decoction: activeDecoctionCount.value,
  logistics: activeLogisticsCount.value,
  reports: reportOverview.value ? 1 : 0,
  integration: activeIntegrationCount.value,
  ops: activeOpsCount.value,
}));
```

Wrap the existing template content inside:

```vue
<AppLayout
  :active-view="activeView"
  :title="currentViewTitle.title"
  :subtitle="currentViewTitle.subtitle"
  :menu-items="menuItems"
  :counts="menuCounts"
  :notice="notice"
  @switch-view="switchView"
>
  <!-- existing section rendering stays here for this task -->
</AppLayout>
```

Remove the old inline `<aside class="sidebar">`, `<nav class="nav">`, `.brand`, `.topbar`, and notice markup from `App.vue`.

- [ ] **Step 4: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds. If Vue reports missing props or duplicate `ViewKey`, fix before continuing.

## Task 2: Shared Formatting And Status Helpers

**Files:**

- Create: `frontend/admin-web/src/domain/formatters.ts`
- Create: `frontend/admin-web/src/domain/status.ts`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Extract formatters**

Create `frontend/admin-web/src/domain/formatters.ts`:

```ts
export function formatDate(value: string | null | undefined) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

export function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined) return '-';
  return new Intl.NumberFormat('zh-CN').format(value);
}

export function defaultDate(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export function dateInputToIso(value: string, endExclusive = false) {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  if (endExclusive) {
    date.setDate(date.getDate() + 1);
  }
  return date.toISOString();
}
```

- [ ] **Step 2: Extract status tone helper**

Create `frontend/admin-web/src/domain/status.ts`:

```ts
export function statusTone(status: string | null | undefined) {
  if (!status) return 'neutral';
  if (['PASSED', 'SUCCESS', 'SENT', 'SIGNED', 'DECOCTED', 'RECHECKED'].some((item) => status.includes(item))) {
    return 'success';
  }
  if (['FAILED', 'REJECTED', 'DEAD', 'CANCELLED', 'TERMINATED'].some((item) => status.includes(item))) {
    return 'error';
  }
  if (['PENDING', 'NEW', 'PROCESSING', 'DECOCTING', 'IN_TRANSIT'].some((item) => status.includes(item))) {
    return 'warning';
  }
  return 'neutral';
}
```

- [ ] **Step 3: Replace local helpers in `App.vue`**

Import:

```ts
import { dateInputToIso, defaultDate, formatDate, formatNumber } from './domain/formatters';
import { statusTone } from './domain/status';
```

Delete the local `statusTone`, `formatDate`, `formatNumber`, `defaultDate`, and `dateInputToIso` functions from `App.vue`.

- [ ] **Step 4: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds and no duplicate function declarations remain.

## Task 3: Move Order Center

**Files:**

- Create: `frontend/admin-web/src/features/orders/OrderCenter.vue`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Create `OrderCenter.vue`**

Move the order state and `queryOrder` logic from `App.vue` into `frontend/admin-web/src/features/orders/OrderCenter.vue`. The component must:

- Own `orderNo`, `order`, `orderProgress`, `orderLoading`, and `orderError`.
- Import `getOrder` and `getOrderProgress`.
- Emit notices through `defineEmits<{ notice: [tone: NoticeTone, text: string] }>()`.
- Render the current `activeView === 'orders'` section markup from `App.vue`.

Use this script shape:

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { ApiError } from '../../api/client';
import { getOrder, getOrderProgress } from '../../api/order';
import type { OrderCreateResult, OrderProgressSnapshot } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const orderNo = ref('');
const order = ref<OrderCreateResult | null>(null);
const orderProgress = ref<OrderProgressSnapshot | null>(null);
const orderLoading = ref(false);
const orderError = ref('');

function errorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return '请求失败';
}

async function queryOrder() {
  if (!orderNo.value.trim()) {
    orderError.value = '请输入订单号';
    return;
  }
  orderLoading.value = true;
  orderError.value = '';
  try {
    const trimmedOrderNo = orderNo.value.trim();
    const [orderResult, progressResult] = await Promise.all([
      getOrder(trimmedOrderNo),
      getOrderProgress(trimmedOrderNo),
    ]);
    order.value = orderResult;
    orderProgress.value = progressResult;
    emit('notice', 'success', `已查询到订单 ${order.value.orderNo}`);
  } catch (error) {
    order.value = null;
    orderProgress.value = null;
    orderError.value = errorMessage(error);
    emit('notice', 'error', orderError.value);
  } finally {
    orderLoading.value = false;
  }
}
</script>
```

- [ ] **Step 2: Render the component from `App.vue`**

Import:

```ts
import OrderCenter from './features/orders/OrderCenter.vue';
```

Replace the old orders section with:

```vue
<OrderCenter v-else-if="activeView === 'orders'" @notice="showNotice" />
```

Delete order-specific state and `queryOrder` from `App.vue`.

- [ ] **Step 3: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds and the order center still compiles with existing API types.

## Task 4: Move Workflow Tasks

**Files:**

- Create: `frontend/admin-web/src/features/workflow/WorkflowTasks.vue`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Create `WorkflowTasks.vue`**

Move review, dispense, and recheck task state and handlers from `App.vue` into `frontend/admin-web/src/features/workflow/WorkflowTasks.vue`.

Props:

```ts
import type { ViewKey } from '../../app/views';

const props = defineProps<{
  activeView: Extract<ViewKey, 'reviews' | 'dispenses' | 'rechecks'>;
}>();
```

Expose counts and refresh to parent:

```ts
defineExpose({
  refreshAllWorkflowTasks,
  counts: {
    reviews: reviewTasks,
    dispenses: dispenseTasks,
    rechecks: recheckTasks,
  },
});
```

The component must own:

- `reviewTasks`, `dispenseTasks`, `recheckTasks`
- loading and error state
- `operator`, `comment`, `handlingTaskId`
- `refreshReviewTasks`, `refreshDispenseTasks`, `refreshRecheckTasks`, `refreshCurrentTasks`, `refreshAllWorkflowTasks`
- `handleReview`, `handleDispense`, `handleRecheck`

- [ ] **Step 2: Render workflow component from `App.vue`**

Import:

```ts
import WorkflowTasks from './features/workflow/WorkflowTasks.vue';
```

Replace the old workflow section with:

```vue
<WorkflowTasks
  v-if="activeView === 'reviews' || activeView === 'dispenses' || activeView === 'rechecks'"
  :active-view="activeView"
  @notice="showNotice"
/>
```

Delete workflow-specific state and handlers from `App.vue`.

- [ ] **Step 3: Adjust menu counts**

If counts cannot be lifted cleanly in this step, set workflow menu counts to `0` temporarily and keep the counts visible inside the workflow page. Do not add complex global state just for badges.

- [ ] **Step 4: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds; review, dispense, and recheck views compile through one component.

## Task 5: Move Reports

**Files:**

- Create: `frontend/admin-web/src/features/reports/ReportOverview.vue`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Create `ReportOverview.vue`**

Move report state and handlers from `App.vue` into `frontend/admin-web/src/features/reports/ReportOverview.vue`.

The component must own:

- `reportFrom`, `reportTo`, `reportTrendDays`
- `reportLoading`, `reportExporting`, `reportError`
- `reportOverview`
- `refreshReports`
- `exportReports`

It must import:

```ts
import { downloadReportOverviewCsv, getReportOverview } from '../../api/report';
import type { ReportOverview } from '../../api/types';
import { dateInputToIso, defaultDate, formatNumber } from '../../domain/formatters';
```

- [ ] **Step 2: Render report component from `App.vue`**

Import:

```ts
import ReportOverview from './features/reports/ReportOverview.vue';
```

Replace the old reports section with:

```vue
<ReportOverview v-else-if="activeView === 'reports'" @notice="showNotice" />
```

Delete report-specific state and handlers from `App.vue`.

- [ ] **Step 3: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds and CSV export code remains in the report feature.

## Task 6: Move Ops Console

**Files:**

- Create: `frontend/admin-web/src/features/ops/OpsConsole.vue`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Create `OpsConsole.vue`**

Move ops state and handlers from `App.vue` into `frontend/admin-web/src/features/ops/OpsConsole.vue`.

The component must own:

- `activeOpsDataset`
- all ops filters
- all ops record arrays
- `opsHealth`
- `refreshOpsHealth`, `refreshOpsRecords`, `switchOpsDataset`

It must import:

```ts
import {
  getOpsHealthOverview,
  listApiAccessLogs,
  listIntegrationRetryIssues,
  listLogisticsCallbackIssues,
  listMessageConsumeLogs,
  listOrderValidationRecords,
  listOutbox,
} from '../../api/ops';
```

It must reuse `formatDate`, `formatNumber`, and `statusTone` from `domain`.

- [ ] **Step 2: Render ops component from `App.vue`**

Import:

```ts
import OpsConsole from './features/ops/OpsConsole.vue';
```

Replace the old ops section with:

```vue
<OpsConsole v-else-if="activeView === 'ops'" @notice="showNotice" />
```

Delete ops-specific state and handlers from `App.vue`.

- [ ] **Step 3: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds and ops query tabs compile in the new feature component.

## Task 7: Dashboard Placeholder With Real Health Data

**Files:**

- Create: `frontend/admin-web/src/features/dashboard/DashboardHome.vue`
- Modify: `frontend/admin-web/src/App.vue`

- [ ] **Step 1: Create dashboard component**

Create `frontend/admin-web/src/features/dashboard/DashboardHome.vue` using `getOpsHealthOverview`. The page must show real health overview data only. Do not add fake pending task numbers.

Required cards:

- Outbox 待发/失败
- 消费失败
- 订单校验拒绝
- 回调失败/死信
- 集成失败/死信
- 最近访问量

- [ ] **Step 2: Render dashboard first**

Change the default active view in `App.vue`:

```ts
const activeView = ref<ViewKey>('dashboard');
```

Render:

```vue
<DashboardHome v-if="activeView === 'dashboard'" @notice="showNotice" />
```

- [ ] **Step 3: Verify build**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: build succeeds and first screen is a real backend-backed dashboard.

## Task 8: Documentation And Final Verification

**Files:**

- Modify: `docs/00_项目总览/上下文管理.md`
- Modify: `docs/99_项目记录/项目记录.md`

- [ ] **Step 1: Update project docs**

Add one 2026-07-18 entry stating:

- `admin-web` phase-one refactor created an app shell and business menu.
- Order center, workflow tasks, report overview, and ops console were moved out of `App.vue`.
- `App.vue` now keeps remaining phase-two pages: decoction, logistics, portal, integration.
- Verification command and result.

- [ ] **Step 2: Run final frontend verification**

Run:

```powershell
cd frontend/admin-web
pnpm build
```

Expected: exit code 0.

- [ ] **Step 3: Run repository diff checks**

Run:

```powershell
git diff --check
git status --short
```

Expected: no diff-check errors; status contains only files related to this frontend refactor and docs update before commit.

- [ ] **Step 4: Commit**

Run:

```powershell
git add frontend/admin-web/src docs/00_项目总览/上下文管理.md docs/99_项目记录/项目记录.md
git commit -m "拆分后台前端核心页面"
```

Expected: one commit containing the phase-one frontend refactor.

## Self-Review

- Spec coverage: Covers first-round app shell, menu, `App.vue` split, order, workflow, reports, ops, docs, and build verification.
- Placeholder scan: No `TBD` or open-ended implementation placeholder is left; phase-two modules are explicitly out of scope.
- Type consistency: `ViewKey`, `NoticeTone`, and API types are defined or imported before use.
- Test strategy: The project currently has no frontend test runner. This plan does not add test files because repository rules prefer existing verification unless the user asks for new tests. Each task uses `pnpm build` as the regression gate.
