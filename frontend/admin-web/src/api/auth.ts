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
  tokenType: 'Bearer';
  expiresAt: string;
  user: Omit<AdminUserSession, 'expiresAt'>;
}

export async function loginAdmin(command: AdminLoginCommand): Promise<AdminUserSession> {
  const result = await request<AdminLoginResult>('/auth-api/api/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify(command),
  });
  const user = { ...result.user, expiresAt: result.expiresAt };
  storeAdminSession(result.accessToken, user);
  return user;
}

export async function restoreAdminSession(): Promise<AdminUserSession | null> {
  if (!readAdminSession()) return null;
  try {
    const user = await request<AdminUserSession>('/auth-api/api/admin/auth/me');
    const stored = readAdminSession();
    if (!stored) return null;
    storeAdminSession(stored.accessToken, user);
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
