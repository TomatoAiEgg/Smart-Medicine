import { request } from './client';

type QueryValue = string | number | boolean | null | undefined;

export interface DecoctionDeviceQueryParams {
  [key: string]: QueryValue;
  deviceId?: string;
  deviceCode?: string;
  deviceName?: string;
  deviceType?: string;
  deviceGroup?: string;
  ipAddress?: string;
  decoctionCenter?: string;
  enabled?: string | boolean;
}

export interface DeviceRecord {
  deviceId: string | null;
  deviceCode: string;
  deviceName: string;
  deviceType: string;
  deviceGroup: string | null;
  ipAddress?: string | null;
  decoctionCenter: string | null;
  deviceStatus: string;
  enabled: boolean;
  remark: string | null;
  activeTaskNo: string | null;
  activePrescriptionNo: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface DecoctionDevicePage {
  records: DeviceRecord[];
  total: number;
}

export async function listAdminDecoctionDevices(params: DecoctionDeviceQueryParams = {}) {
  const query = buildQuery(params);
  const payload = await request<unknown>(`/decoction-api/admin/decoction/devices${query ? `?${query}` : ''}`);
  return normalizeDevicePage(payload);
}

function buildQuery(params: Record<string, QueryValue>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    const normalizedValue = typeof value === 'string' ? value.trim() : String(value);
    if (normalizedValue === '') return;
    query.set(key, normalizedValue);
  });
  return query.toString();
}

function normalizeDevicePage(payload: unknown): DecoctionDevicePage {
  if (Array.isArray(payload)) {
    const records = payload.filter(isDeviceRecord);
    return { records, total: records.length };
  }

  if (isRecordObject(payload)) {
    const recordsPayload = firstArray(payload.records, payload.list, payload.items);
    const records = recordsPayload.filter(isDeviceRecord);
    return { records, total: readNumber(payload.total) ?? records.length };
  }

  return { records: [], total: 0 };
}

function isDeviceRecord(value: unknown): value is DeviceRecord {
  if (!isRecordObject(value)) return false;
  return (
    (typeof value.deviceId === 'string' || value.deviceId === null || value.deviceId === undefined) &&
    typeof value.deviceCode === 'string' &&
    typeof value.deviceName === 'string' &&
    typeof value.deviceType === 'string' &&
    typeof value.deviceStatus === 'string' &&
    typeof value.enabled === 'boolean'
  );
}

function firstArray(...values: unknown[]) {
  return values.find((value): value is unknown[] => Array.isArray(value)) ?? [];
}

function readNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsedValue = Number(value);
    return Number.isFinite(parsedValue) ? parsedValue : undefined;
  }
  return undefined;
}

function isRecordObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
