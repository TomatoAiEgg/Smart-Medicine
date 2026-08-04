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
  expiresAt: string;
}

interface StoredAdminSession {
  accessToken: string;
  user: AdminUserSession;
}

const STORAGE_KEY = 'zhyf.admin.session';

export function readAdminSession(): StoredAdminSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StoredAdminSession>;
    if (!parsed.accessToken || !parsed.user?.userId || !parsed.user.expiresAt) {
      clearAdminSession();
      return null;
    }
    if (Date.parse(parsed.user.expiresAt) <= Date.now()) {
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

export function storeAdminSession(accessToken: string, user: AdminUserSession) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken, user } satisfies StoredAdminSession));
}

export function clearAdminSession() {
  sessionStorage.removeItem(STORAGE_KEY);
}
