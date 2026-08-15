# Admin React Parallel Rebuild Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a parallel React admin frontend in `frontend/admin-react` that restores the legacy admin menu structure, modernizes the app shell, and delivers representative working pages without modifying the existing Vue frontend.

**Architecture:** Create a standalone React/Vite/TypeScript app next to `frontend/admin-web`. Reuse the current backend API contract and auth/session semantics, but rebuild UI with Ant Design, ProComponents, React Router, and TanStack Query. First restore all 11 legacy parent menus and 68 child entries as routes, then implement a small set of representative pages and migration notice pages for unfinished routes.

**Tech Stack:** React, TypeScript, Vite, Ant Design, ProComponents, React Router, TanStack Query, pnpm.

---

## Scope Notes

- Do not delete or rewrite `frontend/admin-web`.
- Do not change backend APIs, database schema, gateway routes, permissions, or deployment files in this phase.
- Do not create test files unless the user explicitly asks. Verification uses `tsc`, `vite build`, manual route checks, and Impeccable audit.
- Preserve unrelated current worktree changes:
  - Deleted `docs/99_项目记录/A14-开发进度总览.png`
  - Untracked `_analysis/`
  - Untracked `completion-status.png`

## Source References

- Spec: `docs/superpowers/specs/2026-08-15-admin-ui-baseline-redesign.md`
- Legacy screenshots: `docs/00_项目总览/老项目UI截图基线/`
- Legacy menu report: `docs/00_项目总览/老项目审查报告.md`
- Existing Vue API contract: `frontend/admin-web/src/api/`
- Existing Vite proxy: `frontend/admin-web/vite.config.ts`
- Existing design policy: `frontend/admin-web/DESIGN.md`

## File Structure

Create:

```text
frontend/admin-react/
├─ DESIGN.md
├─ PRODUCT.md
├─ index.html
├─ package.json
├─ tsconfig.json
├─ tsconfig.node.json
├─ vite.config.ts
└─ src/
   ├─ main.tsx
   ├─ app/
   │  ├─ App.tsx
   │  └─ providers.tsx
   ├─ api/
   │  ├─ adminSession.ts
   │  ├─ auth.ts
   │  ├─ client.ts
   │  └─ types.ts
   ├─ components/
   │  ├─ MigrationNoticePage.tsx
   │  ├─ PageHeader.tsx
   │  ├─ QueryTableShell.tsx
   │  └─ StatusTag.tsx
   ├─ features/
   │  ├─ auth/LoginPage.tsx
   │  ├─ system/UserManagementPage.tsx
   │  ├─ orders/PrescriptionListPage.tsx
   │  ├─ orders/PrescriptionRecheckPage.tsx
   │  ├─ decoction/EquipmentListPage.tsx
   │  └─ reports/PrescriptionCountReportPage.tsx
   ├─ routes/
   │  ├─ menu.ts
   │  └─ router.tsx
   ├─ shell/
   │  ├─ AdminShell.tsx
   │  └─ useRouteTabs.ts
   ├─ styles/
   │  ├─ global.css
   │  └─ shell.css
   └─ utils/
      ├─ formatters.ts
      └─ masking.ts
```

Modify:

```text
.gitignore
```

Only modify `.gitignore` if new React build artifacts need ignoring and are not already covered.

## Task 1: Scaffold `frontend/admin-react`

**Files:**

- Create: `frontend/admin-react/package.json`
- Create: `frontend/admin-react/tsconfig.json`
- Create: `frontend/admin-react/tsconfig.node.json`
- Create: `frontend/admin-react/vite.config.ts`
- Create: `frontend/admin-react/index.html`
- Create: `frontend/admin-react/src/main.tsx`
- Create: `frontend/admin-react/src/app/App.tsx`
- Create: `frontend/admin-react/src/app/providers.tsx`
- Create: `frontend/admin-react/src/styles/global.css`
- Create: `frontend/admin-react/DESIGN.md`
- Create: `frontend/admin-react/PRODUCT.md`

- [ ] **Step 1: Create package metadata**

Create `frontend/admin-react/package.json`:

```json
{
  "name": "zhyf-admin-react",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 0.0.0.0 --port 5175",
    "build": "tsc -b && vite build",
    "preview": "vite preview --host 0.0.0.0 --port 4175"
  },
  "dependencies": {
    "@ant-design/icons": "^5.5.0",
    "@ant-design/pro-components": "^2.8.0",
    "@tanstack/react-query": "^5.80.0",
    "antd": "^5.27.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^7.0.0"
  },
  "devDependencies": {
    "@types/node": "^24.0.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^5.0.0",
    "typescript": "^5.8.0",
    "vite": "^7.0.0"
  }
}
```

- [ ] **Step 2: Create TypeScript config**

Create `frontend/admin-react/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["DOM", "DOM.Iterable", "ES2022"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

Create `frontend/admin-react/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "composite": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 3: Create Vite config with existing gateway proxies**

Create `frontend/admin-react/vite.config.ts`:

```ts
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

const proxyPrefixes = [
  '/order-api',
  '/workflow-api',
  '/message-api',
  '/decoction-api',
  '/ops-api',
  '/logistics-api',
  '/callback-api',
  '/portal-api',
  '/report-api',
  '/integration-api',
  '/auth-api',
] as const;

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_GATEWAY_URL || 'http://127.0.0.1:18080';

  return {
    plugins: [react()],
    server: {
      proxy: Object.fromEntries(
        proxyPrefixes.map((prefix) => [
          prefix,
          {
            target,
            changeOrigin: true,
          },
        ]),
      ),
    },
  };
});
```

- [ ] **Step 4: Create HTML and React entry**

Create `frontend/admin-react/index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>智能药房 SaaS</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Create `frontend/admin-react/src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { AppProviders } from './app/providers';
import { App } from './app/App';
import './styles/global.css';
import './styles/shell.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <AppProviders>
      <App />
    </AppProviders>
  </React.StrictMode>,
);
```

Create `frontend/admin-react/src/app/App.tsx`:

```tsx
import { RouterProvider } from 'react-router-dom';
import { router } from '../routes/router';

export function App() {
  return <RouterProvider router={router} />;
}
```

Create `frontend/admin-react/src/app/providers.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { PropsWithChildren } from 'react';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#0052D9',
          borderRadius: 6,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, Noto Sans CJK SC, Arial, sans-serif',
        },
        components: {
          Table: {
            headerBg: '#F8FAFC',
            rowHoverBg: '#F8FAFC',
          },
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
```

- [ ] **Step 5: Create global CSS and design docs**

Create `frontend/admin-react/src/styles/global.css`:

```css
:root {
  color: #1f2937;
  background: #f3f6fb;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC", Arial, sans-serif;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  --zhyf-primary: #0052d9;
  --zhyf-bg: #f3f6fb;
  --zhyf-surface: #ffffff;
  --zhyf-border: #e5e7eb;
  --zhyf-text: #1f2937;
  --zhyf-muted: #667085;
}

* {
  box-sizing: border-box;
}

html,
body,
#root {
  width: 100%;
  min-width: 0;
  min-height: 100%;
  margin: 0;
}

body {
  overflow: hidden;
}

button,
input,
textarea,
select {
  font: inherit;
  letter-spacing: 0;
}
```

Create `frontend/admin-react/DESIGN.md` by adapting `frontend/admin-web/DESIGN.md`. Required differences:

```markdown
# Smart Pharmacy SaaS React Admin Design

This React admin is a high-density medical operations console. Preserve the legacy system's information architecture and workflows, but do not copy JSP, iframe, jQuery, EasyUI, Bootstrap, fixed-pixel styling, or old visual skin.

Use Ant Design and ProComponents as the implementation layer. Standard pages must prioritize page title, compact filters, data tables, pagination, drawers/dialogs, status tags, and stable route tabs.
```

Create `frontend/admin-react/PRODUCT.md` by adapting `frontend/admin-web/PRODUCT.md`. Required addition:

```markdown
# Smart Pharmacy SaaS React Admin Product Notes

The legacy screenshot baseline in `docs/00_项目总览/老项目UI截图基线/` is the source for page structure and menu coverage. The React implementation must keep business workflows and permission semantics unchanged.
```

- [ ] **Step 6: Install dependencies and verify empty app builds**

Run:

```powershell
pnpm install
pnpm run build
```

Working directory: `frontend/admin-react`

Expected:

```text
tsc -b && vite build
✓ built
```

- [ ] **Step 7: Commit scaffold**

Run:

```powershell
git status --short
git add frontend/admin-react
git commit -m "搭建React后台并行工程"
```

Expected: commit includes only `frontend/admin-react` scaffold files.

## Task 2: Implement API Client And Session Layer

**Files:**

- Create: `frontend/admin-react/src/api/types.ts`
- Create: `frontend/admin-react/src/api/adminSession.ts`
- Create: `frontend/admin-react/src/api/client.ts`
- Create: `frontend/admin-react/src/api/auth.ts`
- Create: `frontend/admin-react/src/features/auth/LoginPage.tsx`

- [ ] **Step 1: Create API response types**

Create `frontend/admin-react/src/api/types.ts`:

```ts
export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}
```

- [ ] **Step 2: Create session storage helper**

Create `frontend/admin-react/src/api/adminSession.ts`:

```ts
export interface AdminUserSession {
  userId: string;
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  username: string;
  displayName: string;
  roleCodes: string[];
  institutionIds: string[];
  permissions: string[];
  tenantWide: boolean;
  expiresAt: string;
}

export interface StoredAdminSession {
  accessToken: string;
  refreshToken: string;
  refreshExpiresAt: string;
  user: AdminUserSession;
}

const STORAGE_KEY = 'zhyf.admin.session';

export function readAdminSession(): StoredAdminSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StoredAdminSession>;
    if (!parsed.accessToken || !parsed.refreshToken || !parsed.refreshExpiresAt || !parsed.user?.userId || !parsed.user.expiresAt) {
      clearAdminSession();
      return null;
    }
    if (Date.parse(parsed.refreshExpiresAt) <= Date.now()) {
      clearAdminSession();
      return null;
    }
    return parsed as StoredAdminSession;
  } catch {
    clearAdminSession();
    return null;
  }
}

export function adminAccessToken() {
  return readAdminSession()?.accessToken ?? null;
}

export function storeAdminSession(
  accessToken: string,
  refreshToken: string,
  refreshExpiresAt: string,
  user: AdminUserSession,
) {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ accessToken, refreshToken, refreshExpiresAt, user } satisfies StoredAdminSession),
  );
}

export function clearAdminSession() {
  sessionStorage.removeItem(STORAGE_KEY);
}
```

- [ ] **Step 3: Create request client**

Create `frontend/admin-react/src/api/client.ts`:

```ts
import type { ApiResponse } from './types';
import {
  adminAccessToken,
  clearAdminSession,
  readAdminSession,
  storeAdminSession,
  type AdminUserSession,
} from './adminSession';

interface AdminRefreshResult {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  refreshExpiresAt: string;
  user: Omit<AdminUserSession, 'expiresAt'>;
}

let refreshPromise: Promise<boolean> | null = null;

export class ApiError extends Error {
  readonly code: string;
  readonly status?: number;

  constructor(message: string, code = 'REQUEST_FAILED', status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const { response, token } = await fetchWithAdminSession(url, init, true);
  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json')
    ? ((await response.json()) as ApiResponse<T>)
    : null;

  if (!response.ok) {
    expireSessionWhenUnauthorized(response.status, token);
    throw new ApiError(payload?.message || `HTTP ${response.status}`, payload?.code, response.status);
  }

  if (!payload) throw new ApiError('服务返回不是 JSON');
  if (payload.code !== 'SUCCESS' && payload.code !== '0') {
    throw new ApiError(payload.message || '业务处理失败', payload.code);
  }
  return payload.data;
}

export async function downloadBlob(url: string, init?: RequestInit): Promise<Blob> {
  const { response, token } = await fetchWithAdminSession(url, init, false);
  if (response.ok) return response.blob();
  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json')
    ? ((await response.json()) as ApiResponse<unknown>)
    : null;
  expireSessionWhenUnauthorized(response.status, token);
  throw new ApiError(payload?.message || `导出失败：HTTP ${response.status}`, payload?.code, response.status);
}

async function fetchWithAdminSession(url: string, init: RequestInit | undefined, jsonBody: boolean) {
  let token = adminAccessToken();
  let response = await fetchRequest(url, init, token, jsonBody);
  if (response.status === 401 && token && shouldRefresh(url) && await refreshAdminSession()) {
    token = adminAccessToken();
    response = await fetchRequest(url, init, token, jsonBody);
  }
  return { response, token };
}

async function fetchRequest(
  url: string,
  init: RequestInit | undefined,
  token: string | null,
  jsonBody: boolean,
) {
  try {
    return await fetch(url, {
      ...init,
      headers: {
        ...(jsonBody ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    });
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : '服务连接失败');
  }
}

function shouldRefresh(url: string) {
  return url !== '/auth-api/api/admin/auth/login' && url !== '/auth-api/api/admin/auth/refresh';
}

async function refreshAdminSession() {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function performRefresh() {
  const stored = readAdminSession();
  if (!stored) return false;
  try {
    const response = await fetch('/auth-api/api/admin/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: stored.refreshToken }),
    });
    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('application/json')
      ? ((await response.json()) as ApiResponse<AdminRefreshResult>)
      : null;
    if (!response.ok || !payload || (payload.code !== 'SUCCESS' && payload.code !== '0')) {
      expireAdminSession();
      return false;
    }
    const result = payload.data;
    const user = { ...result.user, expiresAt: result.expiresAt };
    storeAdminSession(result.accessToken, result.refreshToken, result.refreshExpiresAt, user);
    window.dispatchEvent(new CustomEvent<AdminUserSession>('admin-auth-refreshed', { detail: user }));
    return true;
  } catch {
    expireAdminSession();
    return false;
  }
}

function expireSessionWhenUnauthorized(status: number, token: string | null) {
  if (status !== 401 || !token) return;
  expireAdminSession();
}

function expireAdminSession() {
  clearAdminSession();
  window.dispatchEvent(new CustomEvent('admin-auth-expired'));
}
```

- [ ] **Step 4: Create auth API**

Create `frontend/admin-react/src/api/auth.ts`:

```ts
import { request } from './client';
import {
  clearAdminSession,
  readAdminSession,
  storeAdminSession,
  type AdminUserSession,
} from './adminSession';

export interface AdminLoginCommand {
  tenantCode: string;
  username: string;
  password: string;
}

interface AdminLoginResult {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  refreshExpiresAt: string;
  user: Omit<AdminUserSession, 'expiresAt'>;
}

export async function loginAdmin(command: AdminLoginCommand): Promise<AdminUserSession> {
  const result = await request<AdminLoginResult>('/auth-api/api/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify(command),
  });
  const user = { ...result.user, expiresAt: result.expiresAt };
  storeAdminSession(result.accessToken, result.refreshToken, result.refreshExpiresAt, user);
  return user;
}

export async function restoreAdminSession(): Promise<AdminUserSession | null> {
  if (!readAdminSession()) return null;
  try {
    const user = await request<AdminUserSession>('/auth-api/api/admin/auth/me');
    const stored = readAdminSession();
    if (!stored) return null;
    storeAdminSession(stored.accessToken, stored.refreshToken, stored.refreshExpiresAt, user);
    return user;
  } catch {
    clearAdminSession();
    return null;
  }
}

export async function logoutAdmin() {
  try {
    await request<void>('/auth-api/api/admin/auth/logout', { method: 'POST' });
  } finally {
    clearAdminSession();
  }
}
```

- [ ] **Step 5: Create login page**

Create `frontend/admin-react/src/features/auth/LoginPage.tsx`:

```tsx
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { loginAdmin, type AdminLoginCommand } from '../../api/auth';

const { Title, Text } = Typography;

export function LoginPage() {
  const navigate = useNavigate();
  const mutation = useMutation({
    mutationFn: (command: AdminLoginCommand) => loginAdmin(command),
    onSuccess: () => navigate('/system/users', { replace: true }),
  });

  return (
    <main className="login-page">
      <Card className="login-card">
        <Title level={3}>智能药房 SaaS</Title>
        <Text type="secondary">管理后台</Text>
        <Form
          layout="vertical"
          initialValues={{ tenantCode: 'default' }}
          onFinish={(values) => mutation.mutate(values as AdminLoginCommand)}
        >
          <Form.Item name="tenantCode" label="租户编码" rules={[{ required: true, message: '请输入租户编码' }]}>
            <Input autoComplete="organization" />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          {mutation.isError ? <Alert type="error" showIcon message={mutation.error.message} /> : null}
          <Button type="primary" htmlType="submit" block loading={mutation.isPending}>
            登录
          </Button>
        </Form>
      </Card>
    </main>
  );
}
```

- [ ] **Step 6: Build and commit**

Run:

```powershell
pnpm run build
git add frontend/admin-react/src/api frontend/admin-react/src/features/auth
git commit -m "接入React后台登录会话"
```

Working directory for build: `frontend/admin-react`

Expected: build passes and commit contains only API/session/login files.

## Task 3: Register Legacy Menu Map And Router

**Files:**

- Create: `frontend/admin-react/src/routes/menu.ts`
- Create: `frontend/admin-react/src/routes/router.tsx`
- Create: `frontend/admin-react/src/components/MigrationNoticePage.tsx`

- [ ] **Step 1: Create full legacy menu map**

Create `frontend/admin-react/src/routes/menu.ts` with all 11 parent menus and 68 legacy entries:

```ts
export interface AdminMenuItem {
  key: string;
  label: string;
  path: string;
  legacyRoute: string;
  title: string;
  parentKey: string;
  parentLabel: string;
  implemented: boolean;
}

export interface AdminMenuGroup {
  key: string;
  label: string;
  children: AdminMenuItem[];
}

export const menuGroups: AdminMenuGroup[] = [
  {
    key: 'system',
    label: '系统管理',
    children: [
      { key: 'system-users', label: '用户管理', path: '/system/users', legacyRoute: 'user.html', title: '用户管理', parentKey: 'system', parentLabel: '系统管理', implemented: true },
      { key: 'system-roles', label: '角色管理', path: '/system/roles', legacyRoute: 'role.html', title: '角色管理', parentKey: 'system', parentLabel: '系统管理', implemented: false },
      { key: 'system-menus', label: '菜单管理', path: '/system/menus', legacyRoute: 'menu.html', title: '菜单管理', parentKey: 'system', parentLabel: '系统管理', implemented: false },
    ],
  },
  {
    key: 'settings',
    label: '参数管理',
    children: [
      { key: 'settings-dicts', label: '字典列表', path: '/settings/dicts', legacyRoute: 'systemSetting/listDict.html', title: '字典列表', parentKey: 'settings', parentLabel: '参数管理', implemented: false },
      { key: 'settings-configs', label: '参数配置', path: '/settings/system-configs', legacyRoute: 'systemSetting/listSystemConfig.html', title: '参数配置', parentKey: 'settings', parentLabel: '参数管理', implemented: false },
      { key: 'settings-decoct-centers', label: '煎煮中心配置', path: '/settings/decoct-centers', legacyRoute: 'systemSetting/listDecoctCenter.html', title: '煎煮中心配置', parentKey: 'settings', parentLabel: '参数管理', implemented: false },
      { key: 'settings-operators', label: '工号管理', path: '/settings/operators', legacyRoute: 'systemSetting/listOperUser.html', title: '工号管理', parentKey: 'settings', parentLabel: '参数管理', implemented: false },
    ],
  },
  {
    key: 'institutions',
    label: '机构管理',
    children: [
      { key: 'institutions-list', label: '机构列表', path: '/institutions', legacyRoute: 'mechanism/listInstitutions.html', title: '机构列表', parentKey: 'institutions', parentLabel: '机构管理', implemented: false },
      { key: 'institutions-ip', label: '机构 IP 白名单列表', path: '/institutions/ip-whitelist', legacyRoute: 'mechanism/listWhiteIp.html', title: '机构 IP 白名单列表', parentKey: 'institutions', parentLabel: '机构管理', implemented: false },
      { key: 'institutions-apis', label: '接口列表', path: '/institutions/apis', legacyRoute: 'mechanism/listApiInfo.html', title: '接口列表', parentKey: 'institutions', parentLabel: '机构管理', implemented: false },
      { key: 'institutions-api-permissions', label: '机构接口权限列表', path: '/institutions/api-permissions', legacyRoute: 'mechanism/listApiPermission.html', title: '机构接口权限列表', parentKey: 'institutions', parentLabel: '机构管理', implemented: false },
    ],
  },
  {
    key: 'logistics',
    label: '物流管理',
    children: [
      { key: 'logistics-special-rules', label: '机构物流费规则配置', path: '/logistics/special-rules', legacyRoute: 'logistics/listLogisSpecial.html', title: '机构物流费规则配置', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
      { key: 'logistics-address-costs', label: '机构地址物流费配置', path: '/logistics/address-costs', legacyRoute: 'logistics/listLogisCompanycostAddr.html', title: '机构地址物流费配置', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
      { key: 'logistics-delivery', label: '订单发货查询', path: '/logistics/deliveries', legacyRoute: 'logistics/listLogisDelivery.html', title: '订单发货查询', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
      { key: 'logistics-info', label: '物流信息查询', path: '/logistics/infos', legacyRoute: 'logistics/listLogisInfo.html', title: '物流信息查询', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
      { key: 'logistics-print', label: '物流打单', path: '/logistics/print', legacyRoute: 'logistics/logisPrint.html', title: '物流打单', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
      { key: 'logistics-merges', label: '物流合并列表', path: '/logistics/merges', legacyRoute: 'logistics/listOrderMerge.html', title: '物流合并列表', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
      { key: 'logistics-unreceived', label: '未签收跟进', path: '/logistics/unreceived-followups', legacyRoute: 'logistics/listUnreceiptedOrderInfo.html', title: '未签收跟进', parentKey: 'logistics', parentLabel: '物流管理', implemented: false },
    ],
  },
  {
    key: 'orders',
    label: '订单管理',
    children: [
      { key: 'orders-prescriptions', label: '处方列表', path: '/orders/prescriptions', legacyRoute: 'order/listOrder.html', title: '处方列表', parentKey: 'orders', parentLabel: '订单管理', implemented: true },
      { key: 'orders-audit', label: '订单审核', path: '/orders/audit', legacyRoute: 'order/listExamineOrder.html', title: '订单审核', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-dispense', label: '调剂打印', path: '/orders/dispense', legacyRoute: 'order/listAdjustOrder.html', title: '调剂打印', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-recheck', label: '处方复核', path: '/orders/recheck', legacyRoute: 'order/review.html', title: '处方复核', parentKey: 'orders', parentLabel: '订单管理', implemented: true },
      { key: 'orders-recheck-multi', label: '处方复核（多桶）', path: '/orders/recheck-multi', legacyRoute: 'order/reviews.html', title: '处方复核（多桶）', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-recheck-records', label: '复核管理', path: '/orders/recheck-records', legacyRoute: 'order/listReview.html', title: '复核管理', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-address', label: '订单地址修改', path: '/orders/address-modifications', legacyRoute: 'order/listModifyAddress.html', title: '订单地址修改', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-prescription-modify', label: '处方修改', path: '/orders/prescription-modifications', legacyRoute: 'order/listModifyPrescri.html', title: '处方修改', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-actions', label: '订单操作', path: '/orders/manage-actions', legacyRoute: 'order/listManageOrder.html', title: '订单操作', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-reprint', label: '处方重打', path: '/orders/prescription-reprints', legacyRoute: 'order/listPrescriptionReprint.html', title: '处方重打', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-warehouse', label: '订单仓库', path: '/orders/warehouse', legacyRoute: 'order/listOrderWarehouse.html', title: '订单仓库', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-intercept', label: '订单拦截配置', path: '/orders/intercept-rules', legacyRoute: 'order/listFillpushPrescriptionsIntercept.html', title: '订单拦截配置', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-manual-process', label: '订单走流程', path: '/orders/manual-process', legacyRoute: 'order/listProcessOrder.html', title: '订单走流程', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
      { key: 'orders-receipts', label: '订单签收', path: '/orders/receipts', legacyRoute: 'order/listOrderReceipt.html', title: '订单签收', parentKey: 'orders', parentLabel: '订单管理', implemented: false },
    ],
  },
  {
    key: 'maintenance',
    label: '维护管理',
    children: [
      { key: 'maintenance-order-processes', label: '订单流程查询', path: '/maintenance/order-processes', legacyRoute: 'mainten/listOrderProcess.html', title: '订单流程查询', parentKey: 'maintenance', parentLabel: '维护管理', implemented: false },
      { key: 'maintenance-exception-logs', label: '异常日志信息查询', path: '/maintenance/exception-logs', legacyRoute: 'mainten/listLogException.html', title: '异常日志信息查询', parentKey: 'maintenance', parentLabel: '维护管理', implemented: false },
      { key: 'maintenance-mq', label: 'MQ 消息查询列表', path: '/maintenance/mq-messages', legacyRoute: 'mainten/listMqMessage.html', title: 'MQ 消息查询列表', parentKey: 'maintenance', parentLabel: '维护管理', implemented: false },
      { key: 'maintenance-problems', label: '问题件登记', path: '/maintenance/problem-registrations', legacyRoute: 'mainten/listOrderProblemRegistration.html', title: '问题件登记', parentKey: 'maintenance', parentLabel: '维护管理', implemented: false },
    ],
  },
  {
    key: 'labels',
    label: '标签管理',
    children: [
      { key: 'labels-template', label: '处方标签设置', path: '/labels/templates', legacyRoute: 'lable/listDecotingLableset.html', title: '处方标签设置', parentKey: 'labels', parentLabel: '标签管理', implemented: false },
      { key: 'labels-print', label: '处方标签打印', path: '/labels/prints', legacyRoute: 'lable/labelPrint.html?viewName=decotingLablePrint', title: '处方标签打印', parentKey: 'labels', parentLabel: '标签管理', implemented: false },
    ],
  },
  {
    key: 'sms',
    label: '短信管理',
    children: [
      { key: 'sms-templates', label: '短信模板管理', path: '/sms/templates', legacyRoute: 'sms/listSmsMoudle.html', title: '短信模板管理', parentKey: 'sms', parentLabel: '短信管理', implemented: false },
      { key: 'sms-send-single', label: '单发短信', path: '/sms/send-single', legacyRoute: 'sms/sendUnitSmsInit.html', title: '单发短信', parentKey: 'sms', parentLabel: '短信管理', implemented: false },
      { key: 'sms-records', label: '短信列表查询', path: '/sms/records', legacyRoute: 'sms/listSms.html', title: '短信列表查询', parentKey: 'sms', parentLabel: '短信管理', implemented: false },
    ],
  },
  {
    key: 'drugs',
    label: '药品管理',
    children: [
      { key: 'drugs-herbs', label: '药品目录列表', path: '/drugs/herbs', legacyRoute: 'drugManage/listHerbs.html', title: '药品目录列表', parentKey: 'drugs', parentLabel: '药品管理', implemented: false },
      { key: 'drugs-index-logs', label: '药品索引操作日志', path: '/drugs/index-operation-logs', legacyRoute: 'drugManage/listImportantOperLog.html', title: '药品索引操作日志', parentKey: 'drugs', parentLabel: '药品管理', implemented: false },
      { key: 'drugs-indexes', label: '药品索引列表', path: '/drugs/herb-indexes', legacyRoute: 'drugManage/listHerbsIndex.html', title: '药品索引列表', parentKey: 'drugs', parentLabel: '药品管理', implemented: false },
      { key: 'drugs-herbs-import', label: '药品目录导入', path: '/drugs/herbs/imports', legacyRoute: 'drugManage/importHerbsInit.html', title: '药品目录导入', parentKey: 'drugs', parentLabel: '药品管理', implemented: false },
      { key: 'drugs-index-import', label: '药品索引导入', path: '/drugs/herb-indexes/imports', legacyRoute: 'drugManage/importHerbsIndexInit.html', title: '药品索引导入', parentKey: 'drugs', parentLabel: '药品管理', implemented: false },
      { key: 'drugs-areas', label: '药材区域管理', path: '/drugs/herb-areas', legacyRoute: 'drugManage/listHerbsArea.html', title: '药材区域管理', parentKey: 'drugs', parentLabel: '药品管理', implemented: false },
    ],
  },
  {
    key: 'reports',
    label: '报表管理',
    children: [
      { key: 'reports-prescription-counts', label: '处方数量统计', path: '/reports/prescription-counts', legacyRoute: 'exportQuery/prescriptionNumStatistics.html', title: '处方数量统计', parentKey: 'reports', parentLabel: '报表管理', implemented: true },
      { key: 'reports-institution-counts', label: '机构处方数量统计', path: '/reports/institution-prescription-counts', legacyRoute: 'exportQuery/institutionsPrescriptionNumStatistics.html', title: '机构处方数量统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-audit-performance', label: '审核员业绩统计', path: '/reports/audit-performance', legacyRoute: 'exportQuery/auditPerformanceStatistics.html', title: '审核员业绩统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-audit-details', label: '审核员业绩明细', path: '/reports/audit-performance-details', legacyRoute: 'exportQuery/auditPerformanceDetail.html', title: '审核员业绩明细', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-dispense-performance', label: '调剂员业绩统计', path: '/reports/dispense-performance', legacyRoute: 'exportQuery/dispensePerformanceStatistics.html', title: '调剂员业绩统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-dispense-details', label: '调剂员业绩明细', path: '/reports/dispense-performance-details', legacyRoute: 'exportQuery/dispensePerformanceDetail.html', title: '调剂员业绩明细', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-recheck-performance', label: '复核员业绩统计', path: '/reports/recheck-performance', legacyRoute: 'exportQuery/recheckPerformanceStatistics.html', title: '复核员业绩统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-recheck-details', label: '复核员业绩明细', path: '/reports/recheck-performance-details', legacyRoute: 'exportQuery/recheckPerformanceDetail.html', title: '复核员业绩明细', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-decoction-performance', label: '煎煮员业绩统计', path: '/reports/decoction-performance', legacyRoute: 'exportQuery/boilPerformanceStatistics.html', title: '煎煮员业绩统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-decoction-details', label: '煎煮员业绩明细', path: '/reports/decoction-performance-details', legacyRoute: 'exportQuery/boilPerformanceDetail.html', title: '煎煮员业绩明细', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-logistics-performance', label: '物流员业绩统计', path: '/reports/logistics-performance', legacyRoute: 'exportQuery/packagePerformanceStatistics.html', title: '物流员业绩统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-logistics-details', label: '物流员业绩明细', path: '/reports/logistics-performance-details', legacyRoute: 'exportQuery/packagePerformanceDetail.html', title: '物流员业绩明细', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-org-herbs', label: '机构药材统计（对账）', path: '/reports/institution-herb-reconciliation', legacyRoute: 'exportQuery/orgHerbsStatistics.html', title: '机构药材统计（对账）', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-prescription-reconciliation', label: '处方对账列表（对账）', path: '/reports/prescription-reconciliation', legacyRoute: 'exportQuery/prescriAccount.html', title: '处方对账列表（对账）', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-herb-details', label: '药材明细列表（对账）', path: '/reports/prescription-herb-details', legacyRoute: 'exportQuery/prescriHerbsDetail.html', title: '药材明细列表（对账）', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
      { key: 'reports-herb-dosage', label: '药材用量统计', path: '/reports/herb-dosage', legacyRoute: 'exportQuery/medicinalDoseStatistics.html', title: '药材用量统计', parentKey: 'reports', parentLabel: '报表管理', implemented: false },
    ],
  },
  {
    key: 'decoction',
    label: '煎煮管理',
    children: [
      { key: 'decoction-equipment', label: '设备列表查询', path: '/decoction/equipment', legacyRoute: 'decoct/listEquipmentManage.html', title: '设备列表查询', parentKey: 'decoction', parentLabel: '煎煮管理', implemented: true },
      { key: 'decoction-bindings', label: '处方设备绑定列表', path: '/decoction/prescription-bindings', legacyRoute: 'decoct/listPrescriptionBindInfo.html', title: '处方设备绑定列表', parentKey: 'decoction', parentLabel: '煎煮管理', implemented: false },
      { key: 'decoction-printers', label: '打码机打印配置', path: '/decoction/pda-printer-relations', legacyRoute: 'decoct/listPdaDmjRelation.html', title: '打码机打印配置', parentKey: 'decoction', parentLabel: '煎煮管理', implemented: false },
      { key: 'decoction-water-pails', label: '加水桶管理', path: '/decoction/water-pails', legacyRoute: 'decoct/listWaterpailInfo.html', title: '加水桶管理', parentKey: 'decoction', parentLabel: '煎煮管理', implemented: false },
      { key: 'decoction-cloud-prints', label: '云打印记录列表', path: '/decoction/cloud-print-records', legacyRoute: 'decoct/listBqPrintRecord.html', title: '云打印记录列表', parentKey: 'decoction', parentLabel: '煎煮管理', implemented: false },
    ],
  },
];

export const menuItems = menuGroups.flatMap((group) => group.children);

export function findMenuItemByPath(pathname: string) {
  return menuItems.find((item) => item.path === pathname) ?? menuItems[0];
}
```

- [ ] **Step 2: Create migration notice page**

Create `frontend/admin-react/src/components/MigrationNoticePage.tsx`:

```tsx
import { Card, Descriptions, Result, Tag } from 'antd';
import type { AdminMenuItem } from '../routes/menu';

interface MigrationNoticePageProps {
  item: AdminMenuItem;
}

export function MigrationNoticePage({ item }: MigrationNoticePageProps) {
  return (
    <Card>
      <Result
        status="info"
        title={`${item.title}正在迁移到 React`}
        subTitle="该入口已按老项目菜单恢复。完整业务页面会在后续阶段按截图和接口逐页补齐。"
      />
      <Descriptions bordered size="small" column={1}>
        <Descriptions.Item label="父级菜单">{item.parentLabel}</Descriptions.Item>
        <Descriptions.Item label="业务入口">{item.label}</Descriptions.Item>
        <Descriptions.Item label="老路由">{item.legacyRoute}</Descriptions.Item>
        <Descriptions.Item label="迁移状态">
          <Tag color={item.implemented ? 'green' : 'blue'}>
            {item.implemented ? '代表页面已实现' : '入口已恢复，页面待迁移'}
          </Tag>
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}
```

- [ ] **Step 3: Create router**

Create `frontend/admin-react/src/routes/router.tsx`:

```tsx
import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { MigrationNoticePage } from '../components/MigrationNoticePage';
import { LoginPage } from '../features/auth/LoginPage';
import { AdminShell } from '../shell/AdminShell';
import { menuItems } from './menu';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <AdminShell />,
    children: [
      { index: true, element: <Navigate to="/system/users" replace /> },
      ...menuItems.map((item) => ({
        path: item.path.slice(1),
        element: <MigrationNoticePage item={item} />,
      })),
    ],
  },
  {
    path: '*',
    element: <Navigate to="/system/users" replace />,
  },
]);
```

- [ ] **Step 4: Build and commit**

Run:

```powershell
pnpm run build
git add frontend/admin-react/src/routes frontend/admin-react/src/components/MigrationNoticePage.tsx
git commit -m "恢复React后台老项目菜单路由"
```

Working directory for build: `frontend/admin-react`

Expected: TypeScript compiles because all legacy routes initially render `MigrationNoticePage`.

## Task 4: Build Admin Shell, Sidebar, And Route Tabs

**Files:**

- Create: `frontend/admin-react/src/shell/AdminShell.tsx`
- Create: `frontend/admin-react/src/shell/useRouteTabs.ts`
- Create: `frontend/admin-react/src/styles/shell.css`
- Modify: `frontend/admin-react/src/routes/router.tsx`

- [ ] **Step 1: Create route tabs hook**

Create `frontend/admin-react/src/shell/useRouteTabs.ts`:

```ts
import { useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { findMenuItemByPath } from '../routes/menu';

export interface RouteTab {
  key: string;
  title: string;
  path: string;
  closable: boolean;
}

export function useRouteTabs() {
  const location = useLocation();
  const navigate = useNavigate();
  const current = findMenuItemByPath(location.pathname);
  const [tabs, setTabs] = useState<RouteTab[]>([
    { key: 'system-users', title: '用户管理', path: '/system/users', closable: false },
  ]);

  const activeKey = current.key;

  useMemo(() => {
    setTabs((existing) => {
      if (existing.some((tab) => tab.key === current.key)) return existing;
      return [...existing, { key: current.key, title: current.title, path: current.path, closable: true }];
    });
  }, [current.key, current.path, current.title]);

  function closeTab(targetKey: string) {
    setTabs((existing) => {
      const next = existing.filter((tab) => tab.key !== targetKey || !tab.closable);
      if (targetKey === activeKey) {
        const fallback = next[next.length - 1] ?? next[0];
        if (fallback) navigate(fallback.path);
      }
      return next;
    });
  }

  return { tabs, activeKey, current, navigate, closeTab };
}
```

- [ ] **Step 2: Create admin shell**

Create `frontend/admin-react/src/shell/AdminShell.tsx`:

```tsx
import {
  ApartmentOutlined,
  BarChartOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  MessageOutlined,
  SettingOutlined,
  ShopOutlined,
  ToolOutlined,
  TruckOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Layout, Menu, Tabs, Typography } from 'antd';
import { Outlet, useNavigate } from 'react-router-dom';
import { menuGroups } from '../routes/menu';
import { useRouteTabs } from './useRouteTabs';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const parentIcons: Record<string, React.ReactNode> = {
  system: <UserOutlined />,
  settings: <SettingOutlined />,
  institutions: <ApartmentOutlined />,
  logistics: <TruckOutlined />,
  orders: <FileTextOutlined />,
  maintenance: <ToolOutlined />,
  labels: <DatabaseOutlined />,
  sms: <MessageOutlined />,
  drugs: <ShopOutlined />,
  reports: <BarChartOutlined />,
  decoction: <ExperimentOutlined />,
};

export function AdminShell() {
  const navigate = useNavigate();
  const { tabs, activeKey, current, closeTab } = useRouteTabs();

  return (
    <Layout className="admin-shell">
      <Sider width={224} className="admin-shell__sider">
        <div className="admin-shell__brand">
          <span className="admin-shell__brand-mark">药</span>
          <span>智能药房 SaaS</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[current.key]}
          defaultOpenKeys={[current.parentKey]}
          items={menuGroups.map((group) => ({
            key: group.key,
            icon: parentIcons[group.key],
            label: group.label,
            children: group.children.map((item) => ({
              key: item.key,
              label: item.label,
              onClick: () => navigate(item.path),
            })),
          }))}
        />
      </Sider>
      <Layout>
        <Header className="admin-shell__header">
          <div>
            <Text strong>{current.parentLabel}</Text>
            <Text type="secondary"> / {current.title}</Text>
          </div>
          <div className="admin-shell__user">
            <Text type="secondary">平台运营中心</Text>
            <span className="admin-shell__avatar">管</span>
          </div>
        </Header>
        <div className="admin-shell__tabs">
          <Tabs
            type="editable-card"
            size="small"
            activeKey={activeKey}
            hideAdd
            items={tabs.map((tab) => ({ key: tab.key, label: tab.title, closable: tab.closable }))}
            onChange={(key) => {
              const target = tabs.find((tab) => tab.key === key);
              if (target) navigate(target.path);
            }}
            onEdit={(targetKey, action) => {
              if (action === 'remove') closeTab(String(targetKey));
            }}
          />
        </div>
        <Content className="admin-shell__content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
```

- [ ] **Step 3: Create shell CSS**

Create `frontend/admin-react/src/styles/shell.css`:

```css
.admin-shell {
  min-height: 100vh;
  background: var(--zhyf-bg);
}

.admin-shell__sider {
  min-height: 100vh;
  overflow-y: auto;
}

.admin-shell__brand {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.admin-shell__brand-mark {
  width: 26px;
  height: 26px;
  display: inline-grid;
  place-items: center;
  border-radius: 6px;
  background: #fff;
  color: var(--zhyf-primary);
}

.admin-shell__header {
  height: 52px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--zhyf-surface);
  border-bottom: 1px solid var(--zhyf-border);
}

.admin-shell__user {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.admin-shell__avatar {
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  background: #e8f1ff;
  color: var(--zhyf-primary);
  font-weight: 700;
}

.admin-shell__tabs {
  height: 38px;
  padding: 4px 12px 0;
  background: #fff;
  border-bottom: 1px solid var(--zhyf-border);
}

.admin-shell__tabs .ant-tabs-nav {
  margin: 0;
}

.admin-shell__content {
  height: calc(100vh - 90px);
  overflow: auto;
  padding: 16px 18px 20px;
}

.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f3f6fb;
}

.login-card {
  width: min(420px, 100%);
}

.login-card .ant-form {
  margin-top: 20px;
}
```

- [ ] **Step 4: Build and commit**

Run:

```powershell
pnpm run build
git add frontend/admin-react/src/shell frontend/admin-react/src/styles/shell.css
git commit -m "实现React后台主壳层和页签"
```

Working directory for build: `frontend/admin-react`

Expected: build passes; `/system/users` displays shell with sidebar, header, tabs, and content.

## Task 5: Build Shared Page Components And Formatters

**Files:**

- Create: `frontend/admin-react/src/components/PageHeader.tsx`
- Create: `frontend/admin-react/src/components/QueryTableShell.tsx`
- Create: `frontend/admin-react/src/components/StatusTag.tsx`
- Create: `frontend/admin-react/src/utils/formatters.ts`
- Create: `frontend/admin-react/src/utils/masking.ts`

- [ ] **Step 1: Create page header**

Create `frontend/admin-react/src/components/PageHeader.tsx`:

```tsx
import { Space, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Title, Text } = Typography;

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="query-page__header">
      <div>
        <Title level={3}>{title}</Title>
        {subtitle ? <Text type="secondary">{subtitle}</Text> : null}
      </div>
      {actions ? <Space wrap>{actions}</Space> : null}
    </div>
  );
}
```

- [ ] **Step 2: Create query table shell**

Create `frontend/admin-react/src/components/QueryTableShell.tsx`:

```tsx
import { Card } from 'antd';
import type { ReactNode } from 'react';
import { PageHeader } from './PageHeader';

interface QueryTableShellProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  filters: ReactNode;
  table: ReactNode;
}

export function QueryTableShell({ title, subtitle, actions, filters, table }: QueryTableShellProps) {
  return (
    <section className="query-page">
      <PageHeader title={title} subtitle={subtitle} actions={actions} />
      <Card className="query-page__filters" size="small">
        {filters}
      </Card>
      <Card className="query-page__table" size="small">
        {table}
      </Card>
    </section>
  );
}
```

Append to `frontend/admin-react/src/styles/global.css`:

```css
.query-page {
  display: grid;
  gap: 12px;
}

.query-page__header {
  min-height: 42px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.query-page__header .ant-typography {
  margin: 0;
}

.query-page__filters,
.query-page__table {
  border-color: var(--zhyf-border);
}

.query-page__filters .ant-card-body {
  padding: 12px;
}

.query-page__table .ant-card-body {
  padding: 0;
}
```

- [ ] **Step 3: Create status tag and formatting utilities**

Create `frontend/admin-react/src/components/StatusTag.tsx`:

```tsx
import { Tag } from 'antd';

const statusColorMap: Record<string, string> = {
  ENABLED: 'blue',
  DISABLED: 'default',
  ACTIVE: 'green',
  INACTIVE: 'default',
  PENDING: 'gold',
  SUCCESS: 'green',
  FAILED: 'red',
};

interface StatusTagProps {
  value: string | boolean | null | undefined;
  labels?: Record<string, string>;
}

export function StatusTag({ value, labels }: StatusTagProps) {
  const normalized = typeof value === 'boolean' ? (value ? 'ENABLED' : 'DISABLED') : String(value || '');
  const label = labels?.[normalized] ?? defaultStatusLabel(normalized);
  return <Tag color={statusColorMap[normalized] ?? 'default'}>{label}</Tag>;
}

function defaultStatusLabel(value: string) {
  const labels: Record<string, string> = {
    ENABLED: '已启用',
    DISABLED: '已停用',
    ACTIVE: '正常',
    INACTIVE: '停用',
    PENDING: '待处理',
    SUCCESS: '成功',
    FAILED: '失败',
  };
  return labels[value] ?? (value || '-');
}
```

Create `frontend/admin-react/src/utils/formatters.ts`:

```ts
export function displayValue(value: unknown, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback;
  return String(value);
}

export function formatDate(value: string | null | undefined) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatNumber(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) return '-';
  return new Intl.NumberFormat('zh-CN').format(value);
}
```

Create `frontend/admin-react/src/utils/masking.ts`:

```ts
export function maskPhone(value: string | null | undefined) {
  if (!value) return '-';
  return value.replace(/^(\d{3})\d{4}(\d+)/, '$1****$2');
}

export function maskName(value: string | null | undefined) {
  if (!value) return '-';
  if (value.length <= 1) return '*';
  return `${value.slice(0, 1)}${'*'.repeat(Math.max(1, value.length - 1))}`;
}
```

- [ ] **Step 4: Build and commit**

Run:

```powershell
pnpm run build
git add frontend/admin-react/src/components frontend/admin-react/src/utils frontend/admin-react/src/styles/global.css
git commit -m "沉淀React后台通用页面组件"
```

Working directory for build: `frontend/admin-react`

Expected: build passes and common components compile.

## Task 6: Implement Representative Pages With Real Structure

**Files:**

- Create: `frontend/admin-react/src/features/system/UserManagementPage.tsx`
- Create: `frontend/admin-react/src/features/orders/PrescriptionListPage.tsx`
- Create: `frontend/admin-react/src/features/orders/PrescriptionRecheckPage.tsx`
- Create: `frontend/admin-react/src/features/decoction/EquipmentListPage.tsx`
- Create: `frontend/admin-react/src/features/reports/PrescriptionCountReportPage.tsx`
- Modify: `frontend/admin-react/src/routes/router.tsx`

- [ ] **Step 1: Create user management page**

Create `frontend/admin-react/src/features/system/UserManagementPage.tsx`:

```tsx
import { PlusOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button } from 'antd';
import { QueryTableShell } from '../../components/QueryTableShell';
import { StatusTag } from '../../components/StatusTag';
import { formatDate } from '../../utils/formatters';

interface UserRow {
  userId: string;
  username: string;
  displayName: string;
  roleCodes: string[];
  tenantName: string;
  mobile?: string;
  enabled: boolean;
  createdAt?: string;
  lastLoginAt?: string;
}

const columns: ProColumns<UserRow>[] = [
  { title: '用户ID', dataIndex: 'userId', width: 120, search: false },
  { title: '用户名', dataIndex: 'username', width: 140 },
  { title: '姓名', dataIndex: 'displayName', width: 120 },
  { title: '角色', dataIndex: 'roleCodes', width: 160, search: false, renderText: (value: string[]) => value.join('、') || '-' },
  { title: '租户', dataIndex: 'tenantName', width: 180, search: false },
  { title: '手机号', dataIndex: 'mobile', width: 140, search: false },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, search: false, renderText: formatDate },
  { title: '最近登录时间', dataIndex: 'lastLoginAt', width: 180, search: false, renderText: formatDate },
  { title: '状态', dataIndex: 'enabled', width: 100, search: false, render: (_, row) => <StatusTag value={row.enabled} /> },
  {
    title: '操作',
    valueType: 'option',
    width: 180,
    render: () => [<a key="edit">修改</a>, <a key="permission">查看权限</a>],
  },
];

export function UserManagementPage() {
  return (
    <QueryTableShell
      title="用户管理"
      subtitle="后台操作人员账号、角色标识和启停状态维护"
      actions={<><Button type="primary" icon={<PlusOutlined />}>新增</Button><Button>导出</Button></>}
      filters={null}
      table={
        <ProTable<UserRow>
          rowKey="userId"
          columns={columns}
          search={{ labelWidth: 72, span: 6 }}
          options={false}
          scroll={{ x: 1320 }}
          request={async () => ({
            data: [],
            success: true,
            total: 0,
          })}
          locale={{ emptyText: '用户接口迁移中，页面结构已按老项目恢复' }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
```

- [ ] **Step 2: Create prescription list page**

Create `frontend/admin-react/src/features/orders/PrescriptionListPage.tsx`:

```tsx
import { ExportOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button } from 'antd';
import { QueryTableShell } from '../../components/QueryTableShell';
import { StatusTag } from '../../components/StatusTag';
import { formatDate } from '../../utils/formatters';
import { maskName, maskPhone } from '../../utils/masking';

interface PrescriptionRow {
  orderNo: string;
  prescriptionNo: string;
  platformOrderTime?: string;
  decoctCenterName?: string;
  institutionName?: string;
  patientName?: string;
  patientPhone?: string;
  prescriptionType?: string;
  doseCount?: number;
  amount?: number;
  logisticsType?: string;
  receiverInfo?: string;
  deliveryTime?: string;
  status?: string;
  remark?: string;
}

const columns: ProColumns<PrescriptionRow>[] = [
  { title: '平台处方号', dataIndex: 'prescriptionNo', width: 170 },
  { title: '平台订单时间', dataIndex: 'platformOrderTime', width: 180, search: false, renderText: formatDate },
  { title: '煎煮中心', dataIndex: 'decoctCenterName', width: 140 },
  { title: '机构名称', dataIndex: 'institutionName', width: 180 },
  { title: '病人姓名', dataIndex: 'patientName', width: 110, renderText: maskName },
  { title: '收货电话', dataIndex: 'patientPhone', width: 140, renderText: maskPhone },
  { title: '处方类型', dataIndex: 'prescriptionType', width: 120 },
  { title: '剂数', dataIndex: 'doseCount', width: 80, search: false },
  { title: '处方金额', dataIndex: 'amount', width: 110, search: false },
  { title: '送货方式', dataIndex: 'logisticsType', width: 120 },
  { title: '收货信息', dataIndex: 'receiverInfo', width: 260, search: false, ellipsis: true },
  { title: '送货时间', dataIndex: 'deliveryTime', width: 180, search: false, renderText: formatDate },
  { title: '状态', dataIndex: 'status', width: 110, render: (_, row) => <StatusTag value={row.status} /> },
  { title: '订单备注', dataIndex: 'remark', width: 160, search: false, ellipsis: true },
  { title: '操作', valueType: 'option', width: 100, fixed: 'right', render: () => [<a key="view">查看</a>] },
];

export function PrescriptionListPage() {
  return (
    <QueryTableShell
      title="处方列表"
      subtitle="查询订单详情、处方和履约进度"
      actions={<Button icon={<ExportOutlined />}>导出</Button>}
      filters={null}
      table={
        <ProTable<PrescriptionRow>
          rowKey="prescriptionNo"
          columns={columns}
          options={false}
          search={{ labelWidth: 86, span: 6 }}
          scroll={{ x: 2200 }}
          request={async () => ({ data: [], success: true, total: 0 })}
          locale={{ emptyText: '处方列表接口迁移中，页面结构已按老项目截图恢复' }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
```

- [ ] **Step 3: Create prescription recheck page**

Create `frontend/admin-react/src/features/orders/PrescriptionRecheckPage.tsx`:

```tsx
import { Button, Card, Form, Input, Space } from 'antd';
import { PageHeader } from '../../components/PageHeader';

export function PrescriptionRecheckPage() {
  return (
    <section className="query-page">
      <PageHeader title="处方复核" subtitle="保留老项目按处方号、调剂员、复核员、加水桶号录入的作业结构" />
      <Card>
        <Form layout="vertical" style={{ maxWidth: 720 }}>
          <Form.Item label="处方号" name="prescriptionNo" rules={[{ required: true, message: '请输入处方号' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="调剂员" name="dispenseOperator">
            <Input />
          </Form.Item>
          <Form.Item label="复核员" name="recheckOperator">
            <Input />
          </Form.Item>
          <Form.Item label="加水桶号" name="waterPailNo">
            <Input />
          </Form.Item>
          <Space>
            <Button type="primary">提交复核</Button>
            <Button>重置</Button>
          </Space>
        </Form>
      </Card>
    </section>
  );
}
```

- [ ] **Step 4: Create equipment list page**

Create `frontend/admin-react/src/features/decoction/EquipmentListPage.tsx`:

```tsx
import { PlusOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button } from 'antd';
import { QueryTableShell } from '../../components/QueryTableShell';
import { StatusTag } from '../../components/StatusTag';
import { formatDate } from '../../utils/formatters';

interface EquipmentRow {
  id: string;
  equipmentType: string;
  equipmentNo: string;
  equipmentName: string;
  serialNo?: string;
  ip?: string;
  groupName?: string;
  enabled: boolean;
  used: boolean;
  decoctCenterName?: string;
  operatorName?: string;
  createdAt?: string;
  updatedAt?: string;
}

const columns: ProColumns<EquipmentRow>[] = [
  { title: 'ID', dataIndex: 'id', width: 90, search: false },
  { title: '设备类型', dataIndex: 'equipmentType', width: 130 },
  { title: '设备编号', dataIndex: 'equipmentNo', width: 140 },
  { title: '设备名称', dataIndex: 'equipmentName', width: 160 },
  { title: '设备序列号', dataIndex: 'serialNo', width: 160, search: false },
  { title: '设备IP', dataIndex: 'ip', width: 140 },
  { title: '设备组别', dataIndex: 'groupName', width: 120, search: false },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (_, row) => <StatusTag value={row.enabled} /> },
  { title: '使用状态', dataIndex: 'used', width: 110, render: (_, row) => <StatusTag value={row.used ? 'ACTIVE' : 'INACTIVE'} /> },
  { title: '煎煮中心', dataIndex: 'decoctCenterName', width: 160 },
  { title: '操作人', dataIndex: 'operatorName', width: 120, search: false },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, search: false, renderText: formatDate },
  { title: '修改时间', dataIndex: 'updatedAt', width: 180, search: false, renderText: formatDate },
  { title: '操作', valueType: 'option', width: 90, fixed: 'right', render: () => [<a key="edit">修改</a>] },
];

export function EquipmentListPage() {
  return (
    <QueryTableShell
      title="设备列表查询"
      subtitle="煎煮设备档案、状态和绑定关系维护"
      actions={<Button type="primary" icon={<PlusOutlined />}>添加设备</Button>}
      filters={null}
      table={
        <ProTable<EquipmentRow>
          rowKey="id"
          columns={columns}
          options={false}
          search={{ labelWidth: 76, span: 6 }}
          scroll={{ x: 1700 }}
          request={async () => ({ data: [], success: true, total: 0 })}
          locale={{ emptyText: '设备接口迁移中，页面结构已按老项目截图恢复' }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
```

- [ ] **Step 5: Create prescription count report page**

Create `frontend/admin-react/src/features/reports/PrescriptionCountReportPage.tsx`:

```tsx
import { ExportOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button } from 'antd';
import { QueryTableShell } from '../../components/QueryTableShell';

interface PrescriptionCountRow {
  date: string;
  prescriptionTotal: number;
  decoctionPieces: number;
  proxyDecoction: number;
  paste: number;
  pill: number;
  powder: number;
  other: number;
  doseTotal: number;
  decoctionDose: number;
  proxyDecoctionDose: number;
  pasteDose: number;
  pillDose: number;
  powderDose: number;
  otherDose: number;
}

const columns: ProColumns<PrescriptionCountRow>[] = [
  { title: '日期', dataIndex: 'date', width: 130 },
  { title: '处方合计', dataIndex: 'prescriptionTotal', width: 110, search: false },
  { title: '饮片', dataIndex: 'decoctionPieces', width: 90, search: false },
  { title: '代煎', dataIndex: 'proxyDecoction', width: 90, search: false },
  { title: '膏方', dataIndex: 'paste', width: 90, search: false },
  { title: '丸剂', dataIndex: 'pill', width: 90, search: false },
  { title: '散剂', dataIndex: 'powder', width: 90, search: false },
  { title: '其他', dataIndex: 'other', width: 90, search: false },
  { title: '剂数合计', dataIndex: 'doseTotal', width: 110, search: false },
  { title: '饮片剂数', dataIndex: 'decoctionDose', width: 110, search: false },
  { title: '代煎剂数', dataIndex: 'proxyDecoctionDose', width: 110, search: false },
  { title: '膏方剂数', dataIndex: 'pasteDose', width: 110, search: false },
  { title: '丸剂剂数', dataIndex: 'pillDose', width: 110, search: false },
  { title: '散剂剂数', dataIndex: 'powderDose', width: 110, search: false },
  { title: '其他剂数', dataIndex: 'otherDose', width: 110, search: false },
];

export function PrescriptionCountReportPage() {
  return (
    <QueryTableShell
      title="处方数量统计"
      subtitle="按时间、机构和煎煮中心统计处方数量与剂数"
      actions={<Button icon={<ExportOutlined />}>导出报表</Button>}
      filters={null}
      table={
        <ProTable<PrescriptionCountRow>
          rowKey="date"
          columns={columns}
          options={false}
          search={{ labelWidth: 76, span: 6 }}
          scroll={{ x: 1600 }}
          request={async () => ({ data: [], success: true, total: 0 })}
          locale={{ emptyText: '报表接口迁移中，页面结构已按老项目截图恢复' }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
```

- [ ] **Step 6: Build and commit**

Modify `frontend/admin-react/src/routes/router.tsx` so implemented routes render real pages:

```tsx
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { MigrationNoticePage } from '../components/MigrationNoticePage';
import { LoginPage } from '../features/auth/LoginPage';
import { EquipmentListPage } from '../features/decoction/EquipmentListPage';
import { PrescriptionListPage } from '../features/orders/PrescriptionListPage';
import { PrescriptionRecheckPage } from '../features/orders/PrescriptionRecheckPage';
import { PrescriptionCountReportPage } from '../features/reports/PrescriptionCountReportPage';
import { UserManagementPage } from '../features/system/UserManagementPage';
import { AdminShell } from '../shell/AdminShell';
import { menuItems } from './menu';

const implementedPages: Record<string, ReactNode> = {
  'system-users': <UserManagementPage />,
  'orders-prescriptions': <PrescriptionListPage />,
  'orders-recheck': <PrescriptionRecheckPage />,
  'decoction-equipment': <EquipmentListPage />,
  'reports-prescription-counts': <PrescriptionCountReportPage />,
};

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <AdminShell />,
    children: [
      { index: true, element: <Navigate to="/system/users" replace /> },
      ...menuItems.map((item) => ({
        path: item.path.slice(1),
        element: implementedPages[item.key] ?? <MigrationNoticePage item={item} />,
      })),
    ],
  },
  {
    path: '*',
    element: <Navigate to="/system/users" replace />,
  },
]);
```

Run:

```powershell
pnpm run build
git add frontend/admin-react/src/features frontend/admin-react/src/routes/router.tsx
git commit -m "实现React后台代表页面样板"
```

Working directory for build: `frontend/admin-react`

Expected: build passes and five representative routes render.

## Task 7: Connect Real API Endpoints For Representative Read Paths

**Files:**

- Create: `frontend/admin-react/src/api/order.ts`
- Create: `frontend/admin-react/src/api/report.ts`
- Create: `frontend/admin-react/src/api/decoction.ts`
- Modify: representative pages from Task 6

- [ ] **Step 1: Create minimal order API wrappers**

Create `frontend/admin-react/src/api/order.ts`:

```ts
import { request } from './client';

export interface PageResult<T> {
  records?: T[];
  list?: T[];
  total?: number;
}

export interface AdminOrderRecord {
  orderNo: string;
  prescriptionNo?: string;
  platformOrderTime?: string;
  decoctCenterName?: string;
  institutionName?: string;
  patientName?: string;
  patientPhone?: string;
  prescriptionType?: string;
  doseCount?: number;
  amount?: number;
  logisticsType?: string;
  receiverInfo?: string;
  deliveryTime?: string;
  status?: string;
  remark?: string;
}

export function listAdminOrders(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  return request<PageResult<AdminOrderRecord>>(`/order-api/api/admin/orders?${query}`);
}
```

- [ ] **Step 2: Create report and decoction API wrappers**

Create `frontend/admin-react/src/api/report.ts`:

```ts
import { request } from './client';
import type { PageResult } from './order';

export interface PrescriptionCountRecord {
  date: string;
  prescriptionTotal: number;
  decoctionPieces: number;
  proxyDecoction: number;
  paste: number;
  pill: number;
  powder: number;
  other: number;
  doseTotal: number;
  decoctionDose: number;
  proxyDecoctionDose: number;
  pasteDose: number;
  pillDose: number;
  powderDose: number;
  otherDose: number;
}

export function listPrescriptionCounts(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  return request<PageResult<PrescriptionCountRecord>>(`/report-api/api/admin/reports/overview?${query}`);
}
```

Create `frontend/admin-react/src/api/decoction.ts`:

```ts
import { request } from './client';
import type { PageResult } from './order';

export interface DecoctionEquipmentRecord {
  id: string;
  equipmentType: string;
  equipmentNo: string;
  equipmentName: string;
  serialNo?: string;
  ip?: string;
  groupName?: string;
  enabled: boolean;
  used: boolean;
  decoctCenterName?: string;
  operatorName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export function listDecoctionEquipment(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  return request<PageResult<DecoctionEquipmentRecord>>(`/decoction-api/api/admin/decoction/equipment?${query}`);
}
```

- [ ] **Step 3: Wire pages to API wrappers**

In each representative `ProTable`, replace empty `request` with:

```tsx
request={async (params) => {
  const result = await listAdminOrders({
    pageNo: params.current,
    pageSize: params.pageSize,
    keyword: params.keyword as string | undefined,
  });
  return {
    data: result.records ?? result.list ?? [],
    success: true,
    total: result.total ?? 0,
  };
}}
```

Use the matching function for each page:

- `PrescriptionListPage.tsx`: `listAdminOrders`
- `EquipmentListPage.tsx`: `listDecoctionEquipment`
- `PrescriptionCountReportPage.tsx`: `listPrescriptionCounts`

Leave `UserManagementPage.tsx` and `PrescriptionRecheckPage.tsx` as structure-only if no stable endpoint is confirmed during implementation.

- [ ] **Step 4: Build and commit**

Run:

```powershell
pnpm run build
git add frontend/admin-react/src/api frontend/admin-react/src/features
git commit -m "接入React代表页面查询接口"
```

Working directory for build: `frontend/admin-react`

Expected: TypeScript compile succeeds. Runtime data may show backend-specific field gaps; record any mismatch before implementing write actions.

## Task 8: Run Impeccable Review And Local Visual Verification

**Files:**

- Modify only files under `frontend/admin-react` if audit finds scoped UI issues.

- [ ] **Step 1: Run build verification**

Run:

```powershell
pnpm run build
```

Working directory: `frontend/admin-react`

Expected:

```text
tsc -b && vite build
✓ built
```

- [ ] **Step 2: Start local dev server**

Run:

```powershell
pnpm run dev
```

Working directory: `frontend/admin-react`

Expected:

```text
Local: http://localhost:5175/
```

Keep the server running until visual checks finish.

- [ ] **Step 3: Manual route verification**

In browser, verify these routes:

```text
http://localhost:5175/system/users
http://localhost:5175/orders/prescriptions
http://localhost:5175/orders/recheck
http://localhost:5175/decoction/equipment
http://localhost:5175/reports/prescription-counts
http://localhost:5175/sms/templates
```

Expected:

- Shell displays left 11 parent menus.
- Implemented representative routes render real page shells.
- Unfinished route `/sms/templates` renders migration notice page with parent menu, old route, and status.
- Header, sidebar, route tabs, and table cards do not overlap at 1366px width.

- [ ] **Step 4: Run Impeccable context and audit**

Run from repository root:

```powershell
node .agents/skills/impeccable/scripts/context.mjs --target frontend/admin-react
```

Then run the project-level Impeccable audit command available after installation. If the CLI command is not recognized, record that Impeccable files exist but CLI invocation failed, then continue with build and browser verification.

Expected:

- Audit is scoped to `frontend/admin-react`.
- Any polish changes are limited to `frontend/admin-react`.

- [ ] **Step 5: Commit visual polish**

If changes were made:

```powershell
git status --short
git add frontend/admin-react
git commit -m "打磨React后台首阶段界面"
```

If no changes were made, do not create an empty commit.

## Task 9: Prepare Deployment Switch Notes Without Deploying

**Files:**

- Create or modify only if the repository already has a deployment note convention for frontend switch. Prefer existing docs under `docs/06_部署运维/`.

- [ ] **Step 1: Inspect deployment docs**

Run:

```powershell
rg -n "admin-web|frontend|dist|nginx|5174|4173|部署" docs/06_部署运维 frontend -S
```

Expected:

- Identify current deploy path for Vue frontend.
- Do not change server deployment in this task.

- [ ] **Step 2: Record switch requirements if a suitable existing doc exists**

If an existing deployment doc covers frontend deployment, add a short section with:

```markdown
## React 管理后台切换准备

- React 工程路径：`frontend/admin-react`
- 构建命令：`pnpm install && pnpm run build`
- 构建产物：`frontend/admin-react/dist`
- 当前阶段：并行验证，不替换线上入口
- 切换条件：登录、菜单、代表页面、构建、Impeccable 审查和用户验收均通过
- 回退方式：继续使用 `frontend/admin-web/dist`
```

If no suitable existing doc exists, skip doc creation and report the deployment switch notes in the final delivery instead.

- [ ] **Step 3: Final status check**

Run:

```powershell
git status --short --branch
```

Expected:

- Only current task files are modified or staged.
- Existing unrelated dirty work remains untouched.

## Self-Review Checklist

- Spec coverage:
  - `frontend/admin-react` standalone app: Task 1
  - Existing API/session semantics: Task 2 and Task 7
  - 11 parent / 68 child menu structure: Task 3
  - Legacy shell habit without iframe: Task 4
  - Common page patterns: Task 5
  - Representative pages: Task 6
  - Build, visual, Impeccable verification: Task 8
  - Deployment switch not performed in this phase: Task 9

- Placeholder scan:
  - No unfinished-marker instructions should remain.
  - "Migration notice page" is an intentional product state for unfinished routes, not a placeholder.

- Type consistency:
  - Route keys in `menu.ts` must match `implementedPages` in `router.tsx`.
  - API wrapper result shape uses `PageResult<T>` consistently.
  - Page imports must match created file paths exactly.

## Execution Choice

After this plan is accepted, execute with one of:

1. Subagent-Driven, recommended: use `superpowers:subagent-driven-development`, one fresh subagent per task, review between tasks.
2. Inline Execution: use `superpowers:executing-plans`, execute tasks in this session with checkpoints.
