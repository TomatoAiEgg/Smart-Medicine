# Admin UI C1 Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the runnable Vue administration frontend around one C1 medical operations shell, one 69-entry navigation registry, and consistent dense business-page components without copying Stitch HTML.

**Architecture:** Keep `views.ts` as the single route and menu source, normalize its user-facing labels, and render it through one responsive `AppLayout`. Consolidate visual behavior in the existing admin primitives and the final C1 section of `base.css`; preserve feature APIs and component ownership. Use the reviewed Stitch screens only as layout references for representative workflows.

**Tech Stack:** Vue 3, TypeScript, Vue Router, TDesign Vue Next, Vite, CSS.

---

### Task 1: Lock the 69-entry navigation contract

**Files:**
- Modify: `frontend/admin-web/src/app/views.ts`
- Modify: `frontend/admin-web/src/app/AppLayout.vue`
- Verify: `frontend/admin-web/src/app/router.ts`

- [ ] **Step 1: Normalize the 69 menu labels**

Use the approved business names exactly, including `系统参数配置`, `机构 IP 白名单`, `订单拦截`, `订单流程`, `异常日志`, `MQ 消息`, `短信记录`, and the summary/detail report names. Do not change route keys, paths, permissions, component keys, or API dependencies.

- [ ] **Step 2: Make group order explicit**

Add a typed constant containing the fixed order:

```ts
export const menuGroupOrder = [
  '系统管理',
  '参数管理',
  '机构管理',
  '药品管理',
  '订单管理',
  '煎煮管理',
  '物流管理',
  '标签管理',
  '短信管理',
  '维护管理',
  '报表管理',
] as const;
```

Have `AppLayout.vue` sort grouped menu data by this registry rather than first occurrence in `views.ts`.

- [ ] **Step 3: Enforce one expanded group**

Replace the set-based expansion state with one `string | null`. The active route group takes precedence; manually opening a group closes the previous group.

- [ ] **Step 4: Verify route registration**

Run `pnpm build` from `frontend/admin-web`.

Expected: Vue type checking and Vite build both exit with code 0.

- [ ] **Step 5: Commit**

Stage `views.ts` and `AppLayout.vue`, then commit with message `统一后台菜单名称与导航顺序`.

### Task 2: Rebuild the shared application shell

**Files:**
- Modify: `frontend/admin-web/src/app/AppLayout.vue`
- Modify: `frontend/admin-web/src/styles/base.css`

- [ ] **Step 1: Implement the fixed shell hierarchy**

Use a 56px white top bar, 220px dark sidebar, 36px task tab bar, and `#F4F7FB` workspace. Display only `智能药房 SaaS` as the product name. Keep tenant, signed-in user, refresh, logout, and mobile navigation behavior.

- [ ] **Step 2: Add semantic navigation icons**

Use TDesign icons already available through the installed component library. Icons identify parent groups and actions; numeric menu indexes and text-only pseudo-icons are removed.

- [ ] **Step 3: Add breadcrumb and stable page header**

Derive `父级菜单 / 子菜单` from the active route. Render the page title at 20px and supporting subtitle at 13px. Keep the refresh action compact and prevent the header from changing height for long content.

- [ ] **Step 4: Implement responsive behavior**

At widths below 1024px, move the sidebar into an overlay drawer. At widths below 640px, stack page-header actions and keep task tabs horizontally scrollable. Remove the current `min-width: 1180px` constraint.

- [ ] **Step 5: Verify keyboard and overflow behavior**

Check focus-visible outlines for the menu trigger, parent menu buttons, child routes, tabs, refresh, and logout. Confirm long tenant and menu labels truncate without changing layout width.

- [ ] **Step 6: Commit**

Stage `AppLayout.vue` and `base.css`, then commit with message `重构智能药房后台统一应用壳层`.

### Task 3: Consolidate admin primitives and page states

**Files:**
- Modify: `frontend/admin-web/src/components/admin/AdminPanel.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminToolbar.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminTableShell.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminPageState.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminStatusTag.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminPagination.vue`
- Modify: `frontend/admin-web/src/styles/base.css`

- [ ] **Step 1: Standardize compact dimensions**

Set controls to 32px, table headers to 40px, rows to 44px, panel radii to at most 8px, and page gaps to 16px. Use borders and tonal layers instead of decorative shadows.

- [ ] **Step 2: Standardize semantic colors**

Use blue for primary actions and links, green for success/enabled, cyan for processing/connectivity, amber for pending/warning, and red only for errors or dangerous actions. Pair every color with text.

- [ ] **Step 3: Complete shared states**

Make `AdminPageState` render stable loading, empty, error/retry, permission, and read-only presentations. Preserve layout dimensions during loading and expose accessible action labels.

- [ ] **Step 4: Fix small-screen tables and toolbars**

Allow toolbars to wrap below 1024px. Keep comparison tables in a horizontally scrollable container with a sticky primary column where existing markup supports it.

- [ ] **Step 5: Build verification**

Run `pnpm build` from `frontend/admin-web`; expect exit code 0 with no TypeScript errors.

- [ ] **Step 6: Commit**

Stage the admin components and `base.css`, then commit with message `统一后台表格筛选与页面状态组件`.

### Task 4: Align representative business workflows

**Files:**
- Modify: `frontend/admin-web/src/features/institution/InstitutionList.vue`
- Modify: `frontend/admin-web/src/features/institution/InstitutionApps.vue`
- Modify: `frontend/admin-web/src/features/orders/OrderReviewRecords.vue`
- Modify: `frontend/admin-web/src/features/logistics/LogisPrint.vue`
- Modify: `frontend/admin-web/src/features/ops/ExceptionLogList.vue`
- Modify: `frontend/admin-web/src/features/reports/AuditPerformance.vue`
- Modify: `frontend/admin-web/src/features/reports/AuditPerformanceDetails.vue`
- Modify: `frontend/admin-web/src/styles/base.css`

- [ ] **Step 1: Align list-first pages**

Keep filters and primary actions in one compact toolbar, place business names before codes, limit visible row actions to two, and move secondary actions into existing menus or drawers.

- [ ] **Step 2: Align task workspaces**

For review and print workspaces, keep the task list, business details or fixed-format preview, and sticky action area in stable columns. Do not expose complete patient phone numbers, addresses, tokens, or secrets.

- [ ] **Step 3: Align diagnostic and report pages**

Keep raw technical payloads in diagnostic detail surfaces. Reports must show filters, period, data-update context, summary-to-detail navigation, and right-aligned numeric columns without invented trend claims.

- [ ] **Step 4: Verify functional paths**

Exercise filtering, reset, pagination, drawer/dialog open-close, export, print preview, retry, and route navigation where the existing API permits. Do not replace real actions with decorative controls.

- [ ] **Step 5: Commit**

Stage the representative feature files and `base.css`, then commit with message `校正机构订单物流与报表代表页面`.

### Task 5: Complete visual and route acceptance

**Files:**
- Modify only when verification exposes a defect: `frontend/admin-web/src/**`

- [ ] **Step 1: Verify the menu registry**

Use PowerShell extraction to assert exactly 69 `menuItems`, 11 groups, approved group order, and no duplicate path or key.

- [ ] **Step 2: Run production build**

Run `pnpm build` from `frontend/admin-web`; expect exit code 0.

- [ ] **Step 3: Start the development server**

Run `pnpm dev` from `frontend/admin-web`; expect Vite to serve the application on an available local URL.

- [ ] **Step 4: Capture visual evidence**

Inspect desktop at 1440x900 and mobile at 390x844. Capture the shell plus institution list, order review, logistics print, exception log, and audit performance. Check nonblank rendering, no overlap, no page-level horizontal overflow, and readable long content.

- [ ] **Step 5: Verify clean scope**

Run `git status --short` and `git diff --check`. Expect only task-related files and no whitespace errors.

- [ ] **Step 6: Commit final corrections**

Stage only final frontend corrections and commit with message `完成智能药房后台C1界面验收修正`.
