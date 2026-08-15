# Admin React Parallel Rebuild Phase 2 Plan

## Goal

Continue the parallel React admin rebuild in `frontend/admin-react` by migrating more legacy menu entries into real React pages while keeping the Vue `frontend/admin-web` entry as the rollback path.

## Scope

- Keep the existing 11 parent menus and 68 child routes unchanged.
- Preserve old project page structure from `docs/00_项目总览/老项目UI截图基线/`.
- Read `frontend/admin-react/DESIGN.md` before every UI change.
- Do not change backend APIs, database schema, gateway routes, or server deployment.
- Do not create test files unless explicitly requested; verify with type check, build, Impeccable detect, and screenshots.

## Phase 2 Priority

1. **System And Permission Pages**
   - `系统管理 / 角色管理`
   - `系统管理 / 菜单管理`
   - Goal: restore permission-oriented table, tree, drawer, and disabled-action states.

2. **Parameter Pages**
   - `参数管理 / 字典列表`
   - `参数管理 / 参数配置`
   - `参数管理 / 煎煮中心配置`
   - `参数管理 / 工号管理`
   - Goal: migrate high-frequency configuration lists and connect only stable read endpoints.

3. **Institution Pages**
   - `机构管理 / 机构列表`
   - `机构管理 / 机构 IP 白名单列表`
   - `机构管理 / 接口列表`
   - `机构管理 / 机构接口权限列表`
   - Goal: preserve old structure for institution access and API permission workflows.

4. **Order Workbench Expansion**
   - `订单管理 / 订单审核`
   - `订单管理 / 调剂打印`
   - `订单管理 / 复核管理`
   - Goal: extend from the existing prescription list and recheck shell without wiring unsafe write actions.

## Acceptance

- `pnpm exec tsc -p tsconfig.json --noEmit --incremental false --pretty false` passes.
- `pnpm run build` passes without Vite chunk-size or circular chunk warnings.
- Impeccable detect returns no static findings.
- Representative desktop screenshots show no header, sidebar, tab, filter, or table overlap at 1366px.
- Unmigrated write actions remain disabled or clearly marked until their backend contract is confirmed.
