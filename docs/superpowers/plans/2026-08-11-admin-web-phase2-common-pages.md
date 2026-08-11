# Admin Web Phase 2 Common Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立不依赖 `legacy-*` 的管理端公共页面骨架，并迁移工作台、工号、角色、菜单、字典、系统参数和煎煮中心配置七个样板页面。

**Architecture:** 保留现有 Vue Router、API、领域格式化和页面状态逻辑，在 `src/components/admin` 增加小型布局组件。页面继续拥有自己的业务请求和表单状态，只把工具栏、面板、表格容器、分页、状态反馈和状态标签收敛为稳定接口；未迁移页面继续使用旧兼容层。

**Tech Stack:** Vue 3、TypeScript、Vite、TDesign Vue Next、scoped CSS

---

### Task 1: 建立隔离工作区与基线

**Files:**
- Inspect: `frontend/admin-web/src/styles/base.css`
- Inspect: `frontend/admin-web/src/features/**/*.vue`

- [ ] **Step 1: 创建隔离工作树**

从 `main` 当前提交创建 `ai3-admin-web-phase2` 工作树，保留主工作区已有的图片删除和未跟踪目录，不复制或清理这些用户改动。

- [ ] **Step 2: 记录兼容类基线**

Run:

```powershell
rg -l --glob '*.vue' 'legacy-' frontend/admin-web/src | Measure-Object
rg -o --glob '*.vue' 'legacy-[A-Za-z0-9_-]+' frontend/admin-web/src | Measure-Object
```

Expected: 输出迁移前依赖文件数和引用总数，作为本批次验收基线。

- [ ] **Step 3: 验证现有构建**

Run:

```powershell
pnpm --dir frontend/admin-web build
```

Expected: `vue-tsc -b` 与 `vite build` 均成功。

### Task 2: 建立公共页面骨架

**Files:**
- Create: `frontend/admin-web/src/components/admin/AdminToolbar.vue`
- Create: `frontend/admin-web/src/components/admin/AdminPanel.vue`
- Create: `frontend/admin-web/src/components/admin/AdminTableShell.vue`
- Create: `frontend/admin-web/src/components/admin/AdminPagination.vue`
- Create: `frontend/admin-web/src/components/admin/AdminPageState.vue`
- Create: `frontend/admin-web/src/components/admin/AdminStatusTag.vue`

- [ ] **Step 1: 实现工具栏与面板容器**

`AdminToolbar` 使用默认插槽承载真实筛选控件，`actions` 插槽承载查询、导出等命令；窄屏时筛选区换行，命令区保持可见。`AdminPanel` 提供可选 `title`、`description`、`actions` 插槽和内容插槽，不形成卡片嵌套。

- [ ] **Step 2: 实现表格容器和页面状态**

`AdminTableShell` 只负责水平滚动、边框和表格密度，页面继续输出语义化 `<table>`。`AdminPageState` 接收 `state: 'loading' | 'empty' | 'error' | 'forbidden'` 与 `message`，错误态使用 `t-alert`，其余状态保持固定最小高度。

- [ ] **Step 3: 实现分页和状态标签**

`AdminPagination` 的类型接口固定为：

```ts
interface Props {
  page: number;
  pageSize: number;
  total: number;
  loading?: boolean;
}

interface Emits {
  previous: [];
  next: [];
}
```

组件内部计算上一页、下一页禁用状态并显示“第 N 页 / 共 N 条”。`AdminStatusTag` 接收 `enabled: boolean`，统一输出“启用/停用”及语义色。

- [ ] **Step 4: 运行类型与构建检查**

Run:

```powershell
pnpm --dir frontend/admin-web build
```

Expected: 新组件无 TypeScript、模板或样式编译错误。

- [ ] **Step 5: 提交公共骨架**

```powershell
git add frontend/admin-web/src/components/admin
git commit -m "建立管理端公共页面骨架"
```

### Task 3: 迁移工作台与工号管理

**Files:**
- Modify: `frontend/admin-web/src/features/dashboard/DashboardHome.vue`
- Modify: `frontend/admin-web/src/features/settings/OperatorManage.vue`

- [ ] **Step 1: 迁移工作台**

保留健康概览请求、刷新、导出和无权限行为。使用 `AdminToolbar`、`AdminPageState` 和页面自有指标网格替换 `legacy-page`、`legacy-search-panel`、`legacy-detail-grid`；指标区采用白色工作区、细边框、紧凑数字层级，不新增装饰数据。

- [ ] **Step 2: 迁移工号筛选和编辑区**

保留关键字、状态、查询、导出、新增、编辑、启停和强制下线。使用 `AdminToolbar` 和 `AdminPanel` 分离筛选、编辑和列表，移动端编辑表单改为单列；角色主显示角色名称，角色编码仅作为次级文本。

- [ ] **Step 3: 迁移工号表格与分页**

使用 `AdminTableShell`、`AdminPageState`、`AdminPagination`、`AdminStatusTag`；保持表格最小宽度和内部横向滚动，行操作保持靠近当前记录，当前账号继续禁用“强制下线”。

- [ ] **Step 4: 静态回归检查**

Run:

```powershell
rg -n 'legacy-' frontend/admin-web/src/features/dashboard/DashboardHome.vue frontend/admin-web/src/features/settings/OperatorManage.vue
rg -n '\bany\b|console\.log|debugger' frontend/admin-web/src/features/dashboard/DashboardHome.vue frontend/admin-web/src/features/settings/OperatorManage.vue frontend/admin-web/src/components/admin
```

Expected: 两条命令均无匹配。

- [ ] **Step 5: 构建并提交**

```powershell
pnpm --dir frontend/admin-web build
git add frontend/admin-web/src/features/dashboard/DashboardHome.vue frontend/admin-web/src/features/settings/OperatorManage.vue
git commit -m "迁移工作台与工号管理页面骨架"
```

### Task 4: 迁移角色与菜单管理

**Files:**
- Modify: `frontend/admin-web/src/features/system/RoleManage.vue`
- Modify: `frontend/admin-web/src/features/system/MenuRegistry.vue`

- [ ] **Step 1: 迁移角色列表和编辑结构**

保留角色筛选、成员查询、权限目录、机构范围、保存、删除和导出。桌面端使用“角色列表 + 独立编辑面板”结构，权限按业务域分组并允许组内换行；窄屏按列表、基本信息、权限、机构范围顺序纵向排列，消除狭窄右栏造成的超长页面。

- [ ] **Step 2: 迁移菜单注册表**

保留菜单筛选、分组、路由核对、接入状态和导出。主表显示业务名称、分组、路由和接入状态，内部组件键和旧路由降为次级信息，移动端保持表格内部滚动。

- [ ] **Step 3: 验证关键状态**

检查加载、空数据、接口错误、无管理权限、内置角色不可删除、保存中和导出中状态；所有按钮继续绑定原有方法，不新增静态占位操作。

- [ ] **Step 4: 静态检查、构建并提交**

```powershell
rg -n 'legacy-' frontend/admin-web/src/features/system/RoleManage.vue frontend/admin-web/src/features/system/MenuRegistry.vue
pnpm --dir frontend/admin-web build
git add frontend/admin-web/src/features/system/RoleManage.vue frontend/admin-web/src/features/system/MenuRegistry.vue
git commit -m "迁移角色与菜单管理页面骨架"
```

Expected: `legacy-*` 无匹配，构建成功。

### Task 5: 迁移参数管理样板页

**Files:**
- Modify: `frontend/admin-web/src/features/settings/DictList.vue`
- Modify: `frontend/admin-web/src/features/settings/SystemConfig.vue`
- Modify: `frontend/admin-web/src/features/settings/DecoctCenterConfig.vue`

- [ ] **Step 1: 迁移字典双栏工作区**

保留字典类型与字典项联动、筛选、新增、编辑、启停、分页和导出。桌面保持双栏，宽度不足时改为上下布局；选中类型使用明确行选中态，避免嵌套卡片。

- [ ] **Step 2: 迁移系统参数页**

保留参数筛选、值类型、启停、新增、编辑和导出。敏感值不扩大展示范围；表单和列表使用公共组件，长值允许换行或截断后通过标题查看完整内容。

- [ ] **Step 3: 迁移煎煮中心配置页**

保留查询、新增、编辑、启停和导出。主信息优先显示中心名称和业务状态，系统 ID 仅保留在必要的编辑值或次级信息中。

- [ ] **Step 4: 静态检查、构建并提交**

```powershell
rg -n 'legacy-' frontend/admin-web/src/features/settings/DictList.vue frontend/admin-web/src/features/settings/SystemConfig.vue frontend/admin-web/src/features/settings/DecoctCenterConfig.vue
pnpm --dir frontend/admin-web build
git add frontend/admin-web/src/features/settings/DictList.vue frontend/admin-web/src/features/settings/SystemConfig.vue frontend/admin-web/src/features/settings/DecoctCenterConfig.vue
git commit -m "迁移参数管理样板页面骨架"
```

Expected: 三个页面无 `legacy-*`，构建成功。

### Task 6: 验收、记录和集成

**Files:**
- Modify: `docs/99_项目记录/项目记录.md`

- [ ] **Step 1: 统计迁移结果**

```powershell
rg -l --glob '*.vue' 'legacy-' frontend/admin-web/src | Measure-Object
rg -o --glob '*.vue' 'legacy-[A-Za-z0-9_-]+' frontend/admin-web/src | Measure-Object
```

Expected: 依赖文件数至少减少 7 个；剩余兼容类只属于未迁移页面。

- [ ] **Step 2: 启动预览并做视觉验收**

Run:

```powershell
pnpm --dir frontend/admin-web dev
```

在 1440x900、768x1024、390x844 检查登录后工作台、工号、角色、菜单、字典、参数和煎煮中心页面；确认无正文重叠、无页面级水平滚动、表格滚动局限于容器，菜单和主要命令可达。开发环境缺少有效会话时，至少完成登录页、构建产物和组件级 DOM/CSS 检查，并如实记录未覆盖项。

- [ ] **Step 3: 运行最终验证**

```powershell
pnpm --dir frontend/admin-web build
git diff --check
rg -n '\bany\b|console\.log|debugger' frontend/admin-web/src/components/admin frontend/admin-web/src/features/dashboard/DashboardHome.vue frontend/admin-web/src/features/settings frontend/admin-web/src/features/system
```

Expected: 构建成功，`git diff --check` 无错误，禁止项无匹配。

- [ ] **Step 4: 更新项目事实记录**

在现有项目记录顶部追加本批次迁移页面、公共组件、兼容类减少量、构建结果、视觉验收覆盖范围和未覆盖项；不记录密码、令牌或患者数据。

- [ ] **Step 5: 提交验收记录并合并**

```powershell
git add docs/99_项目记录/项目记录.md
git commit -m "记录管理端样板页面迁移结果"
```

所有检查通过后，使用 fast-forward 方式合并到 `main`；若任一关键页面回归失败，保留主分支不变并回退该工作树内对应批次提交。
