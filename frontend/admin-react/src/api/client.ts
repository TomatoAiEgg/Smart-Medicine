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
  const payload = await readJsonPayload<T>(response);

  if (!response.ok) {
    expireSessionWhenUnauthorized(response.status, token);
    throw new ApiError(payload?.message || `HTTP ${response.status}`, payload?.code, response.status);
  }

  if (!payload) {
    throw new ApiError('服务返回不是 JSON');
  }

  if (payload.code !== 'SUCCESS' && payload.code !== '0') {
    throw new ApiError(payload.message || '业务处理失败', payload.code);
  }

  return payload.data;
}

export async function downloadBlob(url: string, init?: RequestInit): Promise<Blob> {
  const { response, token } = await fetchWithAdminSession(url, init, false);

  if (response.ok) return response.blob();

  const payload = await readJsonPayload<unknown>(response);
  expireSessionWhenUnauthorized(response.status, token);
  throw new ApiError(payload?.message || `导出失败：HTTP ${response.status}`, payload?.code, response.status);
}

function expireSessionWhenUnauthorized(status: number, token: string | null) {
  if (status !== 401 || !token) return;
  expireAdminSession();
}

async function fetchWithAdminSession(url: string, init: RequestInit | undefined, jsonBody: boolean) {
  let token = adminAccessToken();
  let response = await fetchRequest(url, init, token, jsonBody);
  if (response.status === 401 && token && shouldRefresh(url) && (await refreshAdminSession())) {
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

async function readJsonPayload<T>(response: Response): Promise<ApiResponse<T> | null> {
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) return null;
  return (await response.json()) as ApiResponse<T>;
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
    const payload = await readJsonPayload<AdminRefreshResult>(response);
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

function expireAdminSession() {
  clearAdminSession();
  window.dispatchEvent(new CustomEvent('admin-auth-expired'));
}
