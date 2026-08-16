import { request } from './client';
import type { PageResult } from './management.types';

export type OperationValue = string | number | boolean | null | undefined;

export interface OperationRecord {
  id: string;
  [key: string]: OperationValue | OperationValue[] | Record<string, OperationValue> | Record<string, OperationValue>[] | unknown;
}

export interface OperationListQuery {
  [key: string]: OperationValue;
  page?: number;
  pageSize?: number;
  keyword?: string;
  status?: string;
}

function buildQuery(params: OperationListQuery) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    const text = String(value).trim();
    if (text) query.set(key, text);
  });
  return query.toString();
}

function listUrl(path: string, params: OperationListQuery) {
  const query = buildQuery(params);
  return query ? `${path}?${query}` : path;
}

export async function listOperationPage(path: string, params: OperationListQuery = {}) {
  const payload = await request<unknown>(listUrl(path, params));
  return normalizePage(payload, params);
}

export async function createOperationRecord<TCommand extends object>(path: string, command: TCommand) {
  return request<OperationRecord>(path, { method: 'POST', body: JSON.stringify(command) });
}

export async function updateOperationRecord<TCommand extends object>(
  path: string,
  id: string,
  command: TCommand,
  method: 'PATCH' | 'PUT' = 'PATCH',
) {
  return request<OperationRecord>(`${path}/${encodeURIComponent(id)}`, { method, body: JSON.stringify(command) });
}

function normalizePage(payload: unknown, params: OperationListQuery): PageResult<OperationRecord> {
  if (Array.isArray(payload)) {
    const records = payload.map((item, index) => normalizeRecord(item, index));
    return {
      records,
      total: records.length,
      page: params.page ?? 1,
      pageSize: params.pageSize ?? records.length,
    };
  }

  if (isRecord(payload)) {
    const source = firstArray(payload.records, payload.list, payload.items, payload.data);
    const records = source.map((item, index) => normalizeRecord(item, index));
    return {
      records,
      total: readNumber(payload.total) ?? records.length,
      page: readNumber(payload.page) ?? readNumber(payload.pageNo) ?? params.page ?? 1,
      pageSize: readNumber(payload.pageSize) ?? params.pageSize ?? records.length,
    };
  }

  return {
    records: [],
    total: 0,
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  };
}

function normalizeRecord(value: unknown, index: number): OperationRecord {
  if (!isRecord(value)) return { id: `row-${index}`, value: String(value ?? '') };

  const id = readFirstString(
    value.id,
    value.orderId,
    value.orderNo,
    value.ruleId,
    value.costId,
    value.mergeId,
    value.taskId,
    value.shipmentId,
    value.eventId,
    value.messageId,
    value.registrationId,
  ) ?? `row-${index}`;

  return { id, ...value };
}

function firstArray(...values: unknown[]) {
  return values.find((value): value is unknown[] => Array.isArray(value)) ?? [];
}

function readNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

function readFirstString(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value;
    if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
