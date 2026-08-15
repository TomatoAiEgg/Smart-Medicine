import { request } from './client';

type QueryValue = string | number | boolean | null | undefined;

export interface AdminOrderQueryParams {
  [key: string]: QueryValue;
  pageNo?: number;
  pageSize?: number;
  prescriptionNo?: string;
  decoctionCenter?: string;
  institutionName?: string;
  patientName?: string;
  receiverPhone?: string;
  prescriptionType?: string;
  deliveryMethod?: string;
  deliveryTime?: string;
  status?: string;
}

export interface AdminOrderRecord {
  orderId: string;
  orderNo: string;
  institutionName: string;
  orderStatus: string;
  patientName: string | null;
  patientPhone: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  prescriptionId: string;
  prescriptionStatus: string;
  prescriptionNos: string;
  prescriptionTypes: string;
  doseCount: number | null;
  totalAmount: number | string | null;
  deliveryTime: string | null;
  orderRemark: string | null;
  createdAt: string;
  updatedAt: string;
  decoctionCenter?: string | null;
  deliveryMethod?: string | null;
}

export interface AdminOrderPage {
  records: AdminOrderRecord[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export async function listAdminOrders(params: AdminOrderQueryParams = {}) {
  const query = buildQuery(params);
  const payload = await request<unknown>(`/order-api/api/admin/orders${query ? `?${query}` : ''}`);
  return normalizeAdminOrderPage(payload, params);
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

function normalizeAdminOrderPage(payload: unknown, params: AdminOrderQueryParams): AdminOrderPage {
  if (Array.isArray(payload)) {
    const records = payload.filter(isAdminOrderRecord);
    return {
      records,
      total: records.length,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? records.length,
    };
  }

  if (isRecordObject(payload)) {
    const recordsPayload = firstArray(payload.records, payload.list, payload.items);
    const records = recordsPayload.filter(isAdminOrderRecord);
    return {
      records,
      total: readNumber(payload.total) ?? records.length,
      pageNo: readNumber(payload.pageNo) ?? readNumber(payload.page) ?? params.pageNo ?? 1,
      pageSize: readNumber(payload.pageSize) ?? params.pageSize ?? records.length,
    };
  }

  return {
    records: [],
    total: 0,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 10,
  };
}

function isAdminOrderRecord(value: unknown): value is AdminOrderRecord {
  if (!isRecordObject(value)) return false;
  return (
    typeof value.orderId === 'string' &&
    typeof value.orderNo === 'string' &&
    typeof value.institutionName === 'string' &&
    typeof value.prescriptionId === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.updatedAt === 'string'
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
