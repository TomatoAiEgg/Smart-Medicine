import { request } from './client';
import type { CallbackRecord } from './types';

interface CallbackQueryParams {
  status?: string;
  callbackType?: string;
  orderId?: string;
  limit?: number;
}

function buildQuery(params: CallbackQueryParams) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

export function listCallbackRecords(params: CallbackQueryParams = {}) {
  const query = buildQuery(params);
  return request<CallbackRecord[]>(`/callback-api/api/admin/callback-records${query ? `?${query}` : ''}`);
}

export function markCallbackSuccess(id: string, responseBody = 'manual success') {
  return request<CallbackRecord>(`/callback-api/api/admin/callback-records/${id}/mark-success`, {
    method: 'PATCH',
    body: JSON.stringify({ responseBody }),
  });
}

export function markCallbackFailed(id: string, responseBody = 'manual failed') {
  return request<CallbackRecord>(`/callback-api/api/admin/callback-records/${id}/mark-failed`, {
    method: 'PATCH',
    body: JSON.stringify({ responseBody }),
  });
}

export function replayCallback(id: string) {
  return request<CallbackRecord>(`/callback-api/api/admin/callback-records/${id}/replay`, {
    method: 'PATCH',
  });
}
