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
    if (
      !isNonEmptyString(parsed.accessToken) ||
      !isNonEmptyString(parsed.refreshToken) ||
      !isParseableDateString(parsed.refreshExpiresAt) ||
      !isAdminUserSession(parsed.user)
    ) {
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

function isAdminUserSession(value: unknown): value is AdminUserSession {
  if (!isRecord(value)) return false;
  return (
    isNonEmptyString(value.userId) &&
    isString(value.tenantId) &&
    isString(value.tenantCode) &&
    isString(value.tenantName) &&
    isString(value.username) &&
    isString(value.displayName) &&
    isStringArray(value.roleCodes) &&
    isStringArray(value.institutionIds) &&
    isStringArray(value.permissions) &&
    typeof value.tenantWide === 'boolean' &&
    isParseableDateString(value.expiresAt)
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isNonEmptyString(value: unknown): value is string {
  return isString(value) && value.length > 0;
}

function isParseableDateString(value: unknown): value is string {
  return isNonEmptyString(value) && Number.isFinite(Date.parse(value));
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(isString);
}
