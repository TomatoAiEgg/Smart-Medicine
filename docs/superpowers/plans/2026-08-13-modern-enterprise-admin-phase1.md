# 现代企业后台阶段 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以老项目真实布局为业务基线，交付现代企业风格的后台壳层、公共页面组件，以及工号管理、机构列表、处方列表三个可验收代表页。

**Architecture:** 从 `main` 创建隔离工作树，只移植旧分支中与隐私保护有关的已部署修正，不继承旧 C1 壳层和追加式全局覆盖。视觉令牌、应用壳层、公共页面组件和业务页面分层实现；代表页通过后，其余页面才进入后续阶段。

**Tech Stack:** Vue 3、TypeScript、Vue Router、TDesign Vue Next、Vite、CSS Custom Properties、Edge 桌面/移动视口验收。

---

## 范围与非目标

本阶段交付：

- 11 个父级、69 个新路由的稳定菜单壳层。
- 4px 网格、单一医疗蓝、平面层级和高密度后台令牌。
- 筛选带、内容分区、表格、分页、状态和抽屉表单公共组件。
- 工号管理、机构列表、处方列表三个代表页。
- 桌面、平板和移动端截图对比以及 Impeccable 静态审查。

本阶段不做：

- 不批量修改剩余 66 个页面。
- 不修改 API、数据库、权限模型、状态机和服务端部署。
- 不发布云服务器；代表页经用户确认后再决定是否进入第二阶段和部署。
- 不新增测试文件。按仓库个人规则使用类型检查、生产构建、路由契约脚本和浏览器验收代替。

## 文件边界

**新增：**

- `frontend/admin-web/src/styles/admin-tokens.css`：现代企业后台颜色、字体、间距、尺寸和 TDesign 变量。
- `frontend/admin-web/src/styles/admin-shell.css`：顶栏、侧栏、页签、内容区和响应式壳层。
- `frontend/admin-web/src/components/admin/AdminDrawerForm.vue`：统一新增/编辑抽屉的标题、正文和底部操作。

**修改：**

- `frontend/admin-web/src/main.ts`：加载 TDesign 样式和阶段 1 所需组件，按顺序加载新样式。
- `frontend/admin-web/src/App.vue`：显示全部有权限的 69 个业务入口。
- `frontend/admin-web/src/app/views.ts`：固定 11 个父级顺序和菜单契约。
- `frontend/admin-web/src/app/router.ts`：统一浏览器标题。
- `frontend/admin-web/src/app/AppLayout.vue`：重建高密度工作台壳层。
- `frontend/admin-web/src/components/admin/AdminToolbar.vue`：连续工作面的筛选带。
- `frontend/admin-web/src/components/admin/AdminPanel.vue`：无卡片化分区标题。
- `frontend/admin-web/src/components/admin/AdminTableShell.vue`：高密度、内部滚动表格容器。
- `frontend/admin-web/src/components/admin/AdminPagination.vue`：兼容现有页面的紧凑分页。
- `frontend/admin-web/src/components/admin/AdminPageState.vue`：稳定的加载、空、错误、无权限和只读状态。
- `frontend/admin-web/src/components/admin/AdminStatusTag.vue`：低饱和语义状态。
- `frontend/admin-web/src/domain/formatters.ts`：复用姓名和手机号脱敏函数。
- `frontend/admin-web/src/features/settings/OperatorManage.vue`：简单管理列表代表页。
- `frontend/admin-web/src/features/institution/InstitutionList.vue`：主数据列表代表页。
- `frontend/admin-web/src/features/orders/OrderCenter.vue`：复杂宽表和详情代表页。
- `docs/99_项目记录/项目记录.md`：记录阶段 1 范围、验证和遗留项。

`frontend/admin-web/src/styles/base.css` 暂不继续追加壳层覆盖。本阶段只在确有公共遗留样式冲突时做局部删除；剩余页面仍依赖的 `legacy-*` 规则必须保留到对应页面迁移完成。

### Task 1: 创建隔离工作树并建立可构建基线

**Files:**
- Worktree: `.worktrees/admin-modern-phase1`
- Reference commit: `f2bf621`

- [ ] **Step 1: 确认主工作区中的用户改动**

Run:

```powershell
git status --short
git log --oneline -3
```

Expected: `main` 包含设计规格提交 `3460faf`；现有 `A14-开发进度总览.png` 删除、`_analysis/`、`completion-status.png` 和 Stitch 目录保持原样，不暂存、不恢复。

- [ ] **Step 2: 创建隔离工作树**

Run:

```powershell
git worktree add .worktrees/admin-modern-phase1 -b feature/admin-modern-phase1 main
```

Expected: 新工作树分支为 `feature/admin-modern-phase1`，主工作区未跟踪内容不会进入工作树。

- [ ] **Step 3: 安装依赖并验证原始构建**

Run:

```powershell
pnpm --dir frontend/admin-web install --frozen-lockfile
pnpm --dir frontend/admin-web build
```

Expected: `vue-tsc -b` 和 Vite 均退出 `0`。

- [ ] **Step 4: 只移植已部署的隐私和异常信息修正**

Run:

```powershell
git cherry-pick f2bf621
```

Expected: 仅移植姓名/手机号脱敏、物流打印脱敏、异常日志摘要、机构应用密钥保护和登录视觉基础；不引入 `85635d3`、`317b794`、`f37988b` 的旧壳层与覆盖样式。

- [ ] **Step 5: 验证移植结果**

Run:

```powershell
pnpm --dir frontend/admin-web build
git diff --check HEAD~1 HEAD
```

Expected: 构建退出 `0`，差异检查无输出。Cherry-pick 已形成独立提交，无需再创建重复提交。

### Task 2: 建立令牌与 TDesign 运行时

**Files:**
- Create: `frontend/admin-web/src/styles/admin-tokens.css`
- Create: `frontend/admin-web/src/styles/admin-shell.css`
- Modify: `frontend/admin-web/src/main.ts`

- [ ] **Step 1: 新增后台令牌文件**

Create `admin-tokens.css` with this token surface:

```css
:root {
  --admin-primary: #0052d9;
  --admin-primary-hover: #003cab;
  --admin-header: #1f2d3d;
  --admin-sidebar: #1f2d3d;
  --admin-sidebar-hover: #26384a;
  --admin-sidebar-active: #0b5cab;
  --admin-workspace: #f5f6f7;
  --admin-surface: #ffffff;
  --admin-surface-subtle: #f2f3f5;
  --admin-text: #181818;
  --admin-text-secondary: #5e5e5e;
  --admin-text-placeholder: #8b8b8b;
  --admin-border: #dcdcdc;
  --admin-success: #2ba471;
  --admin-warning: #e37318;
  --admin-danger: #d54941;
  --admin-control-height: 32px;
  --admin-header-height: 48px;
  --admin-sidebar-width: 208px;
  --admin-tab-height: 36px;
  --admin-radius: 3px;
  --admin-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
}

html,
body,
#app {
  min-width: 0;
  min-height: 100%;
  margin: 0;
  font-family: var(--admin-font-family);
  color: var(--admin-text);
  background: var(--admin-workspace);
  letter-spacing: 0;
}

button,
input,
select,
textarea {
  font: inherit;
  letter-spacing: 0;
}

:focus-visible {
  outline: 2px solid var(--admin-primary);
  outline-offset: 1px;
}
```

Add TDesign variable overrides for 32px desktop controls, 3px radius, primary blue, gray borders, 13px–14px text, and 40px table rows. Do not override component internals with global descendant selectors when an official TDesign variable exists.

```css
:root {
  --td-brand-color: var(--admin-primary);
  --td-brand-color-hover: var(--admin-primary-hover);
  --td-brand-color-active: #002a7c;
  --td-brand-color-focus: rgba(0, 82, 217, 0.18);
  --td-bg-color-page: var(--admin-workspace);
  --td-bg-color-container: var(--admin-surface);
  --td-bg-color-component: var(--admin-surface-subtle);
  --td-border-level-1-color: var(--admin-border);
  --td-border-level-2-color: #c8c8c8;
  --td-font-gray-1: var(--admin-text);
  --td-font-gray-2: var(--admin-text-secondary);
  --td-font-gray-3: var(--admin-text-placeholder);
  --td-success-color: var(--admin-success);
  --td-warning-color: var(--admin-warning);
  --td-error-color: var(--admin-danger);
  --td-radius-small: 2px;
  --td-radius-default: 3px;
  --td-radius-medium: 4px;
  --td-comp-size-s: var(--admin-control-height);
  --td-font-family: var(--admin-font-family);
}
```

- [ ] **Step 2: 新增壳层样式文件**

Create `admin-shell.css` with these stable layout constraints:

```css
.admin-shell {
  display: grid;
  grid-template:
    "header header" var(--admin-header-height)
    "sidebar main" calc(100vh - var(--admin-header-height))
    / var(--admin-sidebar-width) minmax(0, 1fr);
  min-width: 0;
  min-height: 100vh;
  background: var(--admin-workspace);
}

.admin-shell__header { grid-area: header; }
.admin-shell__sidebar { grid-area: sidebar; min-height: 0; overflow-y: auto; }
.admin-shell__main { grid-area: main; min-width: 0; min-height: 0; overflow: hidden; }
.admin-shell__workspace { min-width: 0; height: calc(100% - var(--admin-tab-height)); overflow: auto; padding: 12px 16px 20px; }

@media (max-width: 1023px) {
  .admin-shell { grid-template: "header" var(--admin-header-height) "main" calc(100vh - var(--admin-header-height)) / minmax(0, 1fr); }
  .admin-shell__sidebar { position: fixed; inset: var(--admin-header-height) auto 0 0; width: min(320px, 86vw); transform: translateX(-100%); }
  .admin-shell--nav-open .admin-shell__sidebar { transform: translateX(0); }
}
```

Add selectors for `.admin-shell__header`, `__brand`, `__account`, `__sidebar`, `__home`, `__menu-group`, `__menu-title`, `__menu-items`, `__menu-item`, `__tabs`, `__tab`, `__backdrop`, `.admin-page-heading`, `.admin-page-notice`, and `.admin-page-content`. The required dimensions are: 48px header, 208px sidebar, 40px parent rows, 36px child rows, 36px tabs, and a 3px selected indicator. Use only solid surfaces and 1px borders; no gradient, decorative card, section shadow, or viewport-scaled font declaration is allowed.

- [ ] **Step 3: 注册实际使用的 TDesign 组件并固定样式顺序**

Update `main.ts` to follow this order:

```ts
import { createApp } from 'vue';
import { Alert } from 'tdesign-vue-next/es/alert';
import { Button } from 'tdesign-vue-next/es/button';
import { Drawer } from 'tdesign-vue-next/es/drawer';
import { Icon } from 'tdesign-vue-next/es/icon';
import { Input } from 'tdesign-vue-next/es/input';
import { Select } from 'tdesign-vue-next/es/select';
import { Switch } from 'tdesign-vue-next/es/switch';
import { Tag } from 'tdesign-vue-next/es/tag';
import { Tooltip } from 'tdesign-vue-next/es/tooltip';
import 'tdesign-vue-next/es/style/index.css';
import App from './App.vue';
import { router } from './app/router';
import './styles/base.css';
import './styles/admin-tokens.css';
import './styles/admin-shell.css';

createApp(App)
  .use(Alert)
  .use(Button)
  .use(Drawer)
  .use(Icon)
  .use(Input)
  .use(Select)
  .use(Switch)
  .use(Tag)
  .use(Tooltip)
  .use(router)
  .mount('#app');
```

- [ ] **Step 4: 验证令牌不会破坏现有页面**

Run:

```powershell
pnpm --dir frontend/admin-web build
git diff --check
```

Expected: 构建退出 `0`，无未知 TDesign 组件类型错误。

- [ ] **Step 5: 提交令牌与运行时**

```powershell
git add frontend/admin-web/src/main.ts frontend/admin-web/src/styles/admin-tokens.css frontend/admin-web/src/styles/admin-shell.css
git commit -m "建立现代企业后台视觉令牌"
```

### Task 3: 重建菜单契约与应用壳层

**Files:**
- Modify: `frontend/admin-web/src/App.vue:100-110`
- Modify: `frontend/admin-web/src/app/views.ts:90-110`
- Modify: `frontend/admin-web/src/app/router.ts`
- Modify: `frontend/admin-web/src/app/AppLayout.vue`

- [ ] **Step 1: 固定父级顺序**

Add to `views.ts`:

```ts
export const menuGroupOrder = [
  '系统管理',
  '参数管理',
  '机构管理',
  '物流管理',
  '订单管理',
  '维护管理',
  '标签管理',
  '短信管理',
  '药品管理',
  '报表管理',
  '煎煮管理',
] as const;

export type MenuGroupName = (typeof menuGroupOrder)[number];
```

Keep all 69 route definitions and their API/component mappings unchanged.

- [ ] **Step 2: 恢复机构应用配置入口**

Replace the `navigationItems` filter in `App.vue` with:

```ts
const navigationItems = computed<readonly AppRouteItem[]>(() => (
  menuItems.filter((item) => canAccessRoute(item, sessionPermissions.value))
));
```

Expected: users with `institution:read` can see the new “机构应用配置” route in addition to the 64 old-layout routes.

- [ ] **Step 3: 统一浏览器标题**

Add to `router.ts` after router creation:

```ts
router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '工作台';
  document.title = `${title} - 智能药房 SaaS`;
});
```

- [ ] **Step 4: 重写 AppLayout 的状态逻辑**

Use one expanded group instead of a `Set<string>`:

```ts
const activeMenuItem = computed(() => props.menuItems.find((item) => item.key === props.activeView));
const activeGroupName = computed(() => activeMenuItem.value?.group ?? null);
const expandedGroupName = ref<string | null>(activeGroupName.value);

watch(activeGroupName, (groupName) => {
  if (groupName) expandedGroupName.value = groupName;
}, { immediate: true });

function toggleGroup(groupName: string) {
  expandedGroupName.value = expandedGroupName.value === groupName ? null : groupName;
}

function isGroupOpen(groupName: string) {
  return expandedGroupName.value === groupName;
}
```

Build `groupedMenuItems` from `menuGroupOrder`, ignoring groups with no permitted items. Remove numerical menu prefixes, “展开/收起” text, development-environment badge, “按业务分组进入页面” copy, and `YF` letter tiles.

- [ ] **Step 5: 重写 AppLayout 模板**

The DOM must use these landmarks:

```vue
<div class="admin-shell" :class="{ 'admin-shell--nav-open': mobileNavigationOpen }">
  <header class="admin-shell__header">
    <button class="admin-shell__menu-trigger" aria-label="打开导航" @click="openMobileNavigation">
      <t-icon name="view-list" />
    </button>
    <strong class="admin-shell__brand">智能药房 SaaS</strong>
    <div class="admin-shell__account">
      <span>{{ adminUser.tenantName }}</span>
      <strong>{{ adminUser.displayName || adminUser.username }}</strong>
      <t-button theme="default" variant="text" size="small" @click="$emit('logout')">
        <template #icon><t-icon name="logout" /></template>退出
      </t-button>
    </div>
  </header>

  <aside class="admin-shell__sidebar" aria-label="业务导航">
    <!-- 首页 + 11 个父级 + permitted children -->
  </aside>

  <main class="admin-shell__main">
    <nav class="admin-shell__tabs" aria-label="已打开页面"><!-- existing tabs --></nav>
    <section class="admin-shell__workspace">
      <header class="admin-page-heading">
        <div><p>{{ activeMenuItem?.group ?? '工作台' }}</p><h1>{{ title }}</h1><span>{{ subtitle }}</span></div>
        <t-button variant="outline" size="small" @click="$emit('refresh')">
          <template #icon><t-icon name="refresh" /></template>刷新
        </t-button>
      </header>
      <t-alert v-if="notice" class="admin-page-notice" :theme="noticeTheme" :message="notice.text" close />
      <section class="admin-page-content"><slot /></section>
    </section>
  </main>
</div>
```

Use a familiar close icon for tabs and an icon-only close button with `title`/`aria-label`; do not render the letter `x` as an icon.

- [ ] **Step 6: 验证导航契约**

Run this PowerShell check from the worktree root:

```powershell
$lines=Get-Content 'frontend/admin-web/src/app/views.ts'
$start=(Select-String -Path 'frontend/admin-web/src/app/views.ts' -Pattern '^export const menuItems').LineNumber
$end=(Select-String -Path 'frontend/admin-web/src/app/views.ts' -Pattern '^\] as const satisfies readonly MenuItem\[\];').LineNumber
$menu=$lines[($start-1)..($end-1)]
$keys=$menu | Where-Object { $_ -match '^\s+key:' } | ForEach-Object { ($_ -replace '^\s*key:\s*','').Trim(" ',") }
$paths=$menu | Where-Object { $_ -match '^\s+path:' } | ForEach-Object { ($_ -replace '^\s*path:\s*','').Trim(" ',") }
$groups=$menu | Where-Object { $_ -match '^\s+group:' } | ForEach-Object { ($_ -replace '^\s*group:\s*','').Trim(" ',") }
"menu=$($keys.Count) groups=$(($groups | Sort-Object -Unique).Count) duplicateKeys=$(($keys | Group-Object | Where-Object Count -gt 1).Count) duplicatePaths=$(($paths | Group-Object | Where-Object Count -gt 1).Count)"
```

Expected: `menu=69 groups=11 duplicateKeys=0 duplicatePaths=0`.

- [ ] **Step 7: 构建并提交壳层**

```powershell
pnpm --dir frontend/admin-web build
git diff --check
git add frontend/admin-web/src/App.vue frontend/admin-web/src/app/AppLayout.vue frontend/admin-web/src/app/router.ts frontend/admin-web/src/app/views.ts
git commit -m "重建高密度后台应用壳层"
```

### Task 4: 统一公共页面组件

**Files:**
- Create: `frontend/admin-web/src/components/admin/AdminDrawerForm.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminToolbar.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminPanel.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminTableShell.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminPagination.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminPageState.vue`
- Modify: `frontend/admin-web/src/components/admin/AdminStatusTag.vue`

- [ ] **Step 1: 将筛选工具栏改为浅灰连续筛选带**

Keep the existing slots and replace card styling with:

```css
.admin-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 8px 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius) var(--admin-radius) 0 0;
  background: var(--admin-surface-subtle);
}
```

At `max-width: 1023px`, actions wrap below filters; at `max-width: 639px`, each labeled filter becomes full width. Do not create a floating card.

- [ ] **Step 2: 将 AdminPanel 改为无卡片分区**

Keep slots unchanged. Use a 40px heading row, a 1px bottom divider, 15px/600 title, 12px description, and no outer background, shadow, border radius, or nested frame.

- [ ] **Step 3: 将 AdminTableShell 改为稳定宽表容器**

Preserve plain-table compatibility and apply:

```css
.admin-table-shell {
  min-width: 0;
  overflow: auto;
  border: 1px solid var(--admin-border);
  border-top: 0;
  border-radius: 0 0 var(--admin-radius) var(--admin-radius);
  background: var(--admin-surface);
}

.admin-table-shell :deep(th) {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 40px;
  padding: 0 10px;
  color: var(--admin-text);
  background: var(--admin-surface-subtle);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.admin-table-shell :deep(td) {
  min-height: 40px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--admin-border);
  font-size: 13px;
  vertical-align: middle;
}
```

Add `[data-align="number"] { text-align: right; }`, `[data-align="status"] { text-align: center; }`, row hover, and `font-variant-numeric: tabular-nums` for numeric/time cells.

- [ ] **Step 4: 保持分页 API 兼容并改为紧凑样式**

Keep `previous` and `next` emits so unmigrated pages do not break. Change summary to:

```vue
<span class="admin-pagination__summary">
  共 {{ total }} 条，第 {{ page }} / {{ totalPages || 1 }} 页
</span>
```

Buttons remain 32px TDesign outline buttons. Do not introduce a new page-size contract in phase 1.

- [ ] **Step 5: 补齐页面状态**

Use the type:

```ts
type AdminPageStateType = 'loading' | 'empty' | 'error' | 'forbidden' | 'readonly';
```

Render fixed-height skeleton rows for `loading`, clear icon/title/message for terminal states, and an optional action slot. Avoid illustrations and oversized empty-state graphics.

- [ ] **Step 6: 创建统一抽屉表单**

Create `AdminDrawerForm.vue` with this public contract:

```ts
interface Props {
  open: boolean;
  title: string;
  description?: string;
  submitting?: boolean;
  saveLabel?: string;
  width?: string;
}

const emit = defineEmits<{
  'update:open': [value: boolean];
  save: [];
}>();
```

Render a `t-drawer` with body slot and footer buttons “取消 / 保存”. Disable close/save during submission, set default width to `520px`, and use `width: min(520px, 100vw)` on mobile.

```vue
<template>
  <t-drawer
    :visible="open"
    :header="title"
    :size="width"
    :close-btn="!submitting"
    :close-on-overlay-click="!submitting"
    @close="emit('update:open', false)"
  >
    <p v-if="description" class="admin-drawer-form__description">{{ description }}</p>
    <div class="admin-drawer-form__body"><slot /></div>
    <template #footer>
      <t-button theme="default" variant="outline" :disabled="submitting" @click="emit('update:open', false)">取消</t-button>
      <t-button theme="primary" :loading="submitting" @click="emit('save')">{{ saveLabel }}</t-button>
    </template>
  </t-drawer>
</template>
```

- [ ] **Step 7: 收敛状态标签**

Keep existing tone mapping. Use 24px height, 2px radius, 12px text and light semantic backgrounds. Do not use fully rounded pills or blue for every state.

- [ ] **Step 8: 构建并提交公共组件**

```powershell
pnpm --dir frontend/admin-web build
git diff --check
git add frontend/admin-web/src/components/admin
git commit -m "统一后台筛选表格与抽屉组件"
```

### Task 5: 改造工号管理与机构列表代表页

**Files:**
- Modify: `frontend/admin-web/src/features/settings/OperatorManage.vue`
- Modify: `frontend/admin-web/src/features/institution/InstitutionList.vue`
- Reference: `docs/00_项目总览/老项目UI截图基线/01-系统管理/01-用户管理.png`
- Reference: `docs/00_项目总览/老项目UI截图基线/03-机构管理/01-机构列表.png`

- [ ] **Step 1: 工号管理移除虚构统计卡和常驻编辑表单**

Delete `OperatorStat`, `enabledCount`, `disabledCount`, `stats`, `.operator-stats`, `.operator-stat`, and the always-visible editor panel. Add:

```ts
const editorOpen = ref(false);

function openCreateForm() {
  resetForm();
  editorOpen.value = true;
}

function openEditForm(row: AdminOperatorRecord) {
  editOperator(row);
  editorOpen.value = true;
}

function closeEditor() {
  if (saving.value) return;
  resetForm();
  editorOpen.value = false;
}
```

After successful save, call `closeEditor()` before refresh. Keep create/update/toggle/force-logout APIs, permission checks, request sequence, export and pagination unchanged.

- [ ] **Step 2: 工号管理使用老布局顺序**

The first viewport must be:

```vue
<section class="admin-list-page operator-page">
  <AdminToolbar>
    <!-- 关键词、状态 -->
    <template #actions>
      <t-button theme="primary" size="small" :disabled="!canManage" @click="openCreateForm">新增工号</t-button>
      <t-button variant="outline" size="small" :disabled="!canExport" @click="downloadOperatorCsv">导出</t-button>
      <t-button variant="outline" size="small" :loading="loading" @click="searchFirstPage">查询</t-button>
    </template>
  </AdminToolbar>
  <AdminPanel>
    <template #title>工号列表</template>
    <AdminTableShell><!-- current columns and actions --></AdminTableShell>
    <AdminPagination ... />
  </AdminPanel>
  <AdminDrawerForm v-model:open="editorOpen" :title="editing ? '编辑工号' : '新增工号'" :submitting="saving" @save="saveOperator">
    <!-- username, displayName, roleCode, enabled -->
  </AdminDrawerForm>
</section>
```

Use TDesign input/select/switch in the drawer. Username remains disabled while editing. Current-user force logout remains disabled.

- [ ] **Step 3: 机构列表移除统计卡和常驻编辑表单**

Delete `InstitutionStat`, enabled/disabled page statistics and their CSS. Add the same `editorOpen`, `openCreateForm`, `closeEditor` flow. Keep institution code immutable while editing and preserve list query, stale-request protection, export, page correction, enable/disable and save APIs.

- [ ] **Step 4: 机构列表使用连续筛选表格布局**

Order filters as screenshot-informed business flow: keyword → status → type → actions. Put “新增机构” before query/export actions. Use `AdminDrawerForm` for code, name, type, status and decoction-center/storage field.

- [ ] **Step 5: 验证代表页不残留卡片结构**

Run:

```powershell
rg -n "operator-stats|operator-stat|institution-stats|institution-stat|stats = computed" frontend/admin-web/src/features/settings/OperatorManage.vue frontend/admin-web/src/features/institution/InstitutionList.vue
```

Expected: 无输出。

- [ ] **Step 6: 构建并提交两个代表页**

```powershell
pnpm --dir frontend/admin-web build
git diff --check
git add frontend/admin-web/src/features/settings/OperatorManage.vue frontend/admin-web/src/features/institution/InstitutionList.vue
git commit -m "重构工号与机构高密度列表页面"
```

### Task 6: 改造处方列表复杂代表页

**Files:**
- Modify: `frontend/admin-web/src/features/orders/OrderCenter.vue:1062-2375`
- Modify: `frontend/admin-web/src/domain/formatters.ts`
- Reference: `docs/00_项目总览/老项目UI截图基线/05-订单管理/01-处方列表.png`

- [ ] **Step 1: 补齐复杂列表所需脱敏函数**

If Task 1 cherry-pick has already added these functions, reuse them without duplication:

```ts
export function maskPersonName(value: unknown) {
  const text = displayValue(value);
  if (text === EMPTY_VALUE || text.length <= 1) return text;
  return `${text.slice(0, 1)}${'*'.repeat(Math.max(1, text.length - 1))}`;
}

export function maskPhone(value: unknown) {
  const text = displayValue(value);
  if (text === EMPTY_VALUE || text.length < 7) return text;
  return `${text.slice(0, 3)}****${text.slice(-4)}`;
}
```

Add the following reset function beside `searchFirstPage()`:

```ts
function resetOrderFilters() {
  startTime.value = '';
  endTime.value = '';
  institution.value = '';
  prescriptionType.value = '';
  hospitalType.value = '';
  orderStatus.value = '';
  decoctionCenter.value = '';
  deliveryType.value = '';
  logisticsCompany.value = '';
  province.value = '';
  orderNo.value = '';
  hospitalPrescriptionNo.value = '';
  patientName.value = '';
  receiverPhone.value = '';
  page.value = 1;
}
```

Use them for patient/receiver names and phone values in primary tables and details unless a privileged reveal flow already exists.

- [ ] **Step 2: 保留脚本行为，只替换页面结构**

Do not change API calls, order workflow methods, modal state, export behavior or status rules. Replace the template root and filter list:

```vue
<section class="admin-list-page order-center-page">
  <AdminToolbar class="order-filter-bar">
    <!-- 时间范围、机构、处方类型、门诊住院、状态、配送、物流、省份、订单号、机构处方号、病人姓名、收货电话 -->
    <template #actions>
      <t-button theme="primary" size="small" :loading="orderLoading" @click="searchFirstPage">查询</t-button>
      <t-button variant="outline" size="small" @click="resetOrderFilters">重置</t-button>
      <t-button variant="outline" size="small" :loading="exportLoading" @click="exportOrders">导出</t-button>
    </template>
  </AdminToolbar>
  <AdminTableShell class="order-list-table"><!-- existing business columns --></AdminTableShell>
  <AdminPagination ... />
  <!-- existing details and operation dialogs, restyled as flat sections -->
</section>
```

Use the existing `searchFirstPage()` and `exportOrders()` functions and the new `resetOrderFilters()` function exactly once; do not add aliases for the same behavior.

- [ ] **Step 3: 保持老页面字段密度**

The primary table must retain current new-system equivalents of: platform prescription number, platform order time, decoction center, institution, outpatient/inpatient, institution prescription number, patient, prescription type, dose count, amount, delivery method, recipient information, delivery time, status, payment and operations. Hide UUIDs and internal enums from the first visual line.

Use `min-width` by business column, sticky operation column, internal horizontal scroll, and two-line recipient information. Do not collapse the page to five generic columns.

- [ ] **Step 4: 收敛详情和操作区域**

Replace card stacks with named sections separated by header rows and hairlines. Keep order, prescription, drugs, amounts, shipment and progress information. Keep address edit, prescription edit, cancel, initialize, sign and advance-flow actions behind existing permission/status checks and confirmations.

- [ ] **Step 5: 删除该页面的 legacy 样式依赖**

Run:

```powershell
rg -n "legacy-|cloud-console|gradient|box-shadow" frontend/admin-web/src/features/orders/OrderCenter.vue
```

Expected: no `legacy-*` or `cloud-console` class remains; only the single allowed modal/drawer elevation may contain `box-shadow`, and no gradient remains.

- [ ] **Step 6: 构建并提交复杂代表页**

```powershell
pnpm --dir frontend/admin-web build
git diff --check
git add frontend/admin-web/src/domain/formatters.ts frontend/admin-web/src/features/orders/OrderCenter.vue
git commit -m "重构处方列表与订单详情工作面"
```

### Task 7: 浏览器验收、Impeccable 审查与记录

**Files:**
- Modify: `docs/99_项目记录/项目记录.md`
- Evidence only, do not commit: temporary screenshots under `$env:TEMP/zhyf-admin-phase1-acceptance`

- [ ] **Step 1: 运行完整静态验证**

```powershell
pnpm --dir frontend/admin-web build
git diff --check
npx --yes impeccable detect frontend/admin-web/src --json
```

Expected: build exits `0`; diff check has no output; Impeccable report is reviewed. Fix relevant findings for card nesting, over-rounding, hierarchy, focus, overflow and alignment. Reject findings that would remove business fields or reduce required density.

- [ ] **Step 2: 启动本地服务并使用真实会话**

```powershell
$env:VITE_GATEWAY_URL='http://47.120.55.53'
pnpm --dir frontend/admin-web dev --host 127.0.0.1 --port 5174
```

Use the server-side admin credential only in the browser login flow. Do not print it, save it to files, add a preview bypass, or commit session storage. Confirm `/auth-api/api/admin/auth/me` succeeds through the Vite proxy.

- [ ] **Step 3: 验证核心交互**

Check all of the following manually or through browser automation:

1. Only one parent group is expanded; route change opens the active parent.
2. All permitted routes, including “机构应用配置”, appear.
3. Mobile menu opens, traps the visual layer, closes by backdrop and closes after navigation.
4. Tabs navigate and close without resizing the shell.
5. 工号/机构新增与编辑抽屉 can open, cancel and preserve submission disabling.
6. 处方列表 query/reset/export controls remain wired; table scroll does not move the whole page horizontally.
7. Loading, empty, 401/403-style error and disabled action states remain coherent.

- [ ] **Step 4: 采集视口截图**

Capture these routes at `1440×900`, `1366×768`, `1024×768`, and `390×844`:

- `/system/users`
- `/institutions`
- `/orders/prescriptions`

Compare desktop screenshots side-by-side with:

- `docs/00_项目总览/老项目UI截图基线/01-系统管理/01-用户管理.png`
- `docs/00_项目总览/老项目UI截图基线/03-机构管理/01-机构列表.png`
- `docs/00_项目总览/老项目UI截图基线/05-订单管理/01-处方列表.png`

Acceptance: business controls and density are recognizable from the old layout; visual styling uses the new tokens; no overlap, outer horizontal overflow, clipped command, card nesting, marketing heading or unexplained blank region appears.

- [ ] **Step 5: 关闭本地服务并清理会话证据**

Stop the Vite process, verify port `5174` is closed, and remove temporary screenshots only after review. Never place credentials, cookies, tokens or session dumps under the repository.

- [ ] **Step 6: 更新项目记录**

Append a dated entry to `docs/99_项目记录/项目记录.md` recording:

- phase 1 files and three migrated representative pages;
- 69/11 route contract result;
- build and Impeccable result;
- screenshot viewports and observed acceptance result;
- no cloud deployment in this phase;
- remaining pages are intentionally deferred to phases 2–4.

- [ ] **Step 7: 提交验收记录**

```powershell
git add docs/99_项目记录/项目记录.md
git commit -m "记录现代企业后台阶段一验收"
git status --short
```

Expected: feature worktree clean. Main workspace's pre-existing unrelated changes remain untouched.

## 阶段 1 完成门槛

阶段 1 只有同时满足以下条件才算完成：

- 生产构建通过。
- 菜单契约为 69 个子入口、11 个父级、无重复 key/path。
- 三个代表页均有四种视口截图并完成老布局对比。
- 工号和机构页面不再展示统计卡或常驻编辑表单。
- 处方列表不再依赖 `legacy-*` 样式，且没有减少业务字段。
- 无预览登录旁路、硬编码凭据或会话数据进入 Git。
- 用户确认阶段 1 的桌面截图后，才编写和执行基础管理、核心履约、运营支撑三个后续计划。
