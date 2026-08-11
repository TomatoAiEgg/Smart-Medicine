# Admin Web Phase 3 Institution Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将机构管理 5 个页面迁移到现有管理端公共页面骨架，移除页面内全部 `legacy-*` 依赖，并保持真实查询、编辑、启停、导出和安全字段边界不变。

**Architecture:** 页面继续拥有自己的 API 请求、表单和路由激活状态，仅复用 `AdminToolbar`、`AdminPanel`、`AdminTableShell`、`AdminPagination`、`AdminPageState` 和 `AdminStatusTag`。每页将列表请求与操作错误分离，使用请求序号忽略过期响应，并使用现有 `loading`/`saving` 状态阻止查询和写操作重入；不修改 API、权限、路由或数据模型。

**Tech Stack:** Vue 3、TypeScript、Vite、TDesign Vue Next、scoped CSS

---

## Design Specification

### Product Goal

机构管理员需要快速维护机构档案、接入应用、IP 白名单、接口定义和机构授权。主视图优先显示机构名称、业务编码、接入状态和可执行动作；密钥明文、UUID 和内部系统字段不扩大展示范围。

### Information Architecture

```text
InstitutionPage
  AdminToolbar
    BusinessFilters
    QueryAndExportActions
  CurrentPageStats
  AdminPanel (CreateOrEditForm)
    ActionError
    BusinessFields
    SaveAndResetActions
  AdminPanel (ResultList)
    AdminPageState
    AdminTableShell
      SemanticTable
        AdminStatusTag
        RowActions
    AdminPagination
```

应用、白名单和接口授权页面继续加载机构/接口选项。选项加载失败显示为可恢复的列表错误，不把空选项误当成正常数据；编辑已有应用时 `AppSecret` 仍为空，只有用户重新填写后才触发密钥重置语义。

### Visual Direction And Layout

- 延续第二阶段样板页的中性白色工作区、细边框、6px 圆角和紧凑 12px 间距。
- 统计只表达总数、本页启用、本页停用，不增加与后端无关的指标。
- 桌面端表单使用 3 至 4 列网格；小于 980px 改为单列，工具栏自然换行。
- 宽表格保留业务比较价值，水平滚动限制在 `AdminTableShell` 内，页面本身不得横向溢出。
- 行操作使用 TDesign 小尺寸描边按钮，状态用文字加语义色，不只依赖颜色。

### State Coverage

- 初次加载、空结果、列表错误分别使用 `AdminPageState`。
- 查询成功后保留已有内容刷新时不闪成空态；过期响应不得覆盖新筛选结果。
- 表单校验和保存/启停错误进入 `actionError`，列表错误进入 `listError`。
- `loading` 时禁用筛选提交、分页和导出；`saving` 时禁用保存、清空和行操作。
- 机构或接口选项加载期间禁用依赖选项的表单控件。
- 长路径、回调地址和备注允许换行；编码、请求方法和 IP 段保持可扫描。

### Critical Self-review

| Risk | Correction |
| --- | --- |
| 五页变成机械复制的卡片模板 | 只用面板表达编辑和列表边界，筛选与统计保持无嵌套结构 |
| 安全字段被视觉重构扩大暴露 | 应用列表只显示密钥是否配置，编辑时不回填密钥 |
| 快速筛选出现旧结果覆盖 | 每页使用单调递增请求序号，只提交最新响应 |
| 一个错误状态同时清空列表和表单 | 分离 `listError` 与 `actionError`，列表刷新失败才控制列表状态 |
| 移动端把桌面表格压缩成不可读列 | 表格保持稳定最小宽度并在容器内滚动，筛选与表单改为单列 |

### Scope And Rollback

仅修改 5 个机构页面和项目记录；不新增公共抽象、不修改共享组件接口。每个页面组独立提交，出现阻断时可按提交回滚；旧兼容层继续服务其他未迁移页面。

---

### Task 1: 建立隔离工作区与验证基线

**Files:**
- Inspect: `frontend/admin-web/src/features/institution/*.vue`
- Inspect: `frontend/admin-web/src/components/admin/*.vue`

- [ ] **Step 1: 创建隔离工作树**

从当前 `main` 创建 `ai3-admin-web-phase3-institution` 工作树。主工作区已有图片删除、`_analysis/` 和 `completion-status.png` 保持原状。

- [ ] **Step 2: 记录本批兼容类基线**

```powershell
$files = Get-ChildItem frontend/admin-web/src/features/institution -Filter *.vue
foreach ($file in $files) {
  $count = (rg -o 'legacy-[A-Za-z0-9_-]+' -- $file.FullName | Measure-Object).Count
  "$($file.Name): $count"
}
```

Expected: 5 个页面合计 157 处 `legacy-*` 引用。

- [ ] **Step 3: 验证现有构建**

```powershell
pnpm --dir frontend/admin-web build
```

Expected: `vue-tsc -b` 与 `vite build` 均成功。

### Task 2: 迁移机构列表与接口定义

**Files:**
- Modify: `frontend/admin-web/src/features/institution/InstitutionList.vue`
- Modify: `frontend/admin-web/src/features/institution/ApiInfoList.vue`

- [ ] **Step 1: 收敛异步状态**

两个页面分别增加 `listError`、`actionError`、`refreshRequestSequence` 和 `activeRefreshRequest`。列表刷新只接受当前请求序号；查询、分页和保存入口在对应状态已占用时直接返回。

```ts
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);

async function refreshPage() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  // 仅当 requestId === activeRefreshRequest.value 时提交结果或错误。
}
```

- [ ] **Step 2: 迁移机构列表视图**

保留编码/名称/煎煮中心、状态和类型筛选，以及新增、编辑、启停、分页和当前页导出。机构名称为主信息，编码、类型、仓储标识和时间保持可比较列。

- [ ] **Step 3: 迁移接口定义视图**

保留关键字、状态、接口编码/名称、HTTP 方法、路径、描述和启停动作。路径允许换行，接口编码继续只在新增时可编辑。

- [ ] **Step 4: 静态检查、构建并提交**

```powershell
rg -n 'legacy-' frontend/admin-web/src/features/institution/InstitutionList.vue frontend/admin-web/src/features/institution/ApiInfoList.vue
rg -n '\bany\b|console\.log|debugger' frontend/admin-web/src/features/institution/InstitutionList.vue frontend/admin-web/src/features/institution/ApiInfoList.vue
pnpm --dir frontend/admin-web build
git add frontend/admin-web/src/features/institution/InstitutionList.vue frontend/admin-web/src/features/institution/ApiInfoList.vue
git commit -m "迁移机构与接口定义页面骨架"
```

Expected: 两次静态扫描无匹配，构建成功。

### Task 3: 迁移机构应用与 IP 白名单

**Files:**
- Modify: `frontend/admin-web/src/features/institution/InstitutionApps.vue`
- Modify: `frontend/admin-web/src/features/institution/IpWhitelist.vue`

- [ ] **Step 1: 收敛列表和选项请求状态**

按 Task 2 的请求序号模式保护列表结果。机构选项加载继续最多 100 条且只缓存成功结果；加载失败保留明确错误，后续刷新可以重试。

- [ ] **Step 2: 迁移机构应用视图**

保留机构、关键字、状态筛选和新增、编辑、启停、导出。列表只显示密钥“已配置/未配置”；编辑时不回填密钥，填写新值时保存按钮继续提示“保存并重置密钥”。

- [ ] **Step 3: 迁移 IP 白名单视图**

保留关键字、机构、IP 段、状态筛选和新增、编辑、启停、导出。机构名称和编码组合显示，IP/CIDR 是主扫描字段。

- [ ] **Step 4: 静态检查、构建并提交**

```powershell
rg -n 'legacy-' frontend/admin-web/src/features/institution/InstitutionApps.vue frontend/admin-web/src/features/institution/IpWhitelist.vue
rg -n '\bany\b|console\.log|debugger' frontend/admin-web/src/features/institution/InstitutionApps.vue frontend/admin-web/src/features/institution/IpWhitelist.vue
pnpm --dir frontend/admin-web build
git add frontend/admin-web/src/features/institution/InstitutionApps.vue frontend/admin-web/src/features/institution/IpWhitelist.vue
git commit -m "迁移机构接入安全配置页面骨架"
```

Expected: 两次静态扫描无匹配，构建成功。

### Task 4: 迁移机构接口授权

**Files:**
- Modify: `frontend/admin-web/src/features/institution/ApiPermissionList.vue`

- [ ] **Step 1: 保护机构与接口选项加载**

机构和接口选项继续并行获取，只有两者都成功时更新选项；失败后允许下一次刷新重试。列表请求使用请求序号保护，保存和启停使用 `saving` 锁。

- [ ] **Step 2: 迁移授权筛选、编辑和表格**

保留关键字、机构、接口、状态筛选，以及授权新增、编辑、启停和导出。主表优先显示机构名称和接口名称，编码作为次级文本；方法、路径、备注和状态保持独立列。

- [ ] **Step 3: 静态检查、构建并提交**

```powershell
rg -n 'legacy-' frontend/admin-web/src/features/institution/ApiPermissionList.vue
rg -n '\bany\b|console\.log|debugger' frontend/admin-web/src/features/institution/ApiPermissionList.vue
pnpm --dir frontend/admin-web build
git add frontend/admin-web/src/features/institution/ApiPermissionList.vue
git commit -m "迁移机构接口授权页面骨架"
```

Expected: 静态扫描无匹配，构建成功。

### Task 5: 浏览器验收与缺陷修正

**Files:**
- Modify if needed: `frontend/admin-web/src/features/institution/*.vue`

- [ ] **Step 1: 启动独立开发服务**

```powershell
pnpm --dir frontend/admin-web dev -- --port 5176
```

Expected: `http://127.0.0.1:5176/` 可访问，不占用当前 5174/5175 服务。

- [ ] **Step 2: 验收 5 个路由**

检查 `/institutions`、`/institutions/apps`、`/institutions/ip-whitelist`、`/institutions/apis`、`/institutions/api-permissions`。视口覆盖 1440x900、768x1024、390x844；记录控制台错误、页面错误、正文重叠和页面级水平溢出。

- [ ] **Step 3: 验收交互状态**

验证查询、分页、表单校验、保存中禁用、行操作禁用、空态、错误态、长路径/备注和选项加载失败。后端未运行时使用本地脱敏会话检查视觉与错误态，并明确记录未执行真实写接口。

- [ ] **Step 4: 修复验收发现的问题并复验**

只修复本批页面内问题；每次修正后重新运行构建和对应视口检查。

### Task 6: 记录结果、最终验证和集成

**Files:**
- Modify: `docs/99_项目记录/项目记录.md`

- [ ] **Step 1: 统计迁移结果**

```powershell
rg -l --glob '*.vue' 'legacy-' frontend/admin-web/src | Measure-Object
rg -o --glob '*.vue' 'legacy-[A-Za-z0-9_-]+' frontend/admin-web/src | Measure-Object
rg -n 'legacy-' frontend/admin-web/src/features/institution
```

Expected: 机构 5 页无匹配；全项目依赖文件由 61 降至 56，引用数由 2140 降至 1983。

- [ ] **Step 2: 更新项目记录**

在项目记录顶部增加本批范围、保留行为、异步保护、兼容类减少量、构建与浏览器验收结果，以及真实后端动作是否覆盖。

- [ ] **Step 3: 运行最终验证**

```powershell
pnpm --dir frontend/admin-web build
git diff --check main...HEAD
rg -n 'legacy-' frontend/admin-web/src/features/institution
rg -n '\bany\b|console\.log|debugger' frontend/admin-web/src/features/institution
```

Expected: 构建与差异检查成功，两次静态扫描无匹配。

- [ ] **Step 4: 提交记录并审查差异**

```powershell
git add docs/99_项目记录/项目记录.md
git commit -m "记录机构管理页面迁移结果"
git diff --stat main...HEAD
git log --oneline main..HEAD
```

- [ ] **Step 5: 集成到主分支**

确认主工作区仅保留原有无关改动后，将功能分支快进合并到 `main`，再次运行前端构建和静态扫描，然后删除隔离工作树与临时分支。
