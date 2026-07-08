import { request } from './client';
import type {
  ApiAccessLogRecord,
  EventOutboxRecord,
  LogisticsCallbackIssueRecord,
  MessageConsumeRecord,
  OrderValidationRecord,
} from './types';

interface OpsQueryParams {
  limit?: number;
  status?: string;
  eventType?: string;
  consumerGroup?: string;
  eventId?: string;
  orderId?: string;
  validationStatus?: string;
  appKey?: string;
  resultCode?: string;
  callbackStatus?: string;
  callbackType?: string;
  businessId?: string;
  orderNo?: string;
}

function buildQuery(params: OpsQueryParams) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

function opsUrl(path: string, params: OpsQueryParams) {
  const query = buildQuery(params);
  return query ? `/ops-api/api/admin/ops/${path}?${query}` : `/ops-api/api/admin/ops/${path}`;
}

export function listOutbox(params: Pick<OpsQueryParams, 'status' | 'eventType' | 'limit'> = {}) {
  return request<EventOutboxRecord[]>(opsUrl('outbox', params));
}

export function listMessageConsumeLogs(
  params: Pick<OpsQueryParams, 'status' | 'consumerGroup' | 'eventId' | 'limit'> = {},
) {
  return request<MessageConsumeRecord[]>(opsUrl('message-consume-logs', params));
}

export function listOrderValidationRecords(
  params: Pick<OpsQueryParams, 'orderId' | 'validationStatus' | 'limit'> = {},
) {
  return request<OrderValidationRecord[]>(opsUrl('order-validation-records', params));
}

export function listApiAccessLogs(params: Pick<OpsQueryParams, 'appKey' | 'resultCode' | 'limit'> = {}) {
  return request<ApiAccessLogRecord[]>(opsUrl('api-access-logs', params));
}

export function listLogisticsCallbackIssues(
  params: Pick<OpsQueryParams, 'callbackStatus' | 'callbackType' | 'businessId' | 'orderNo' | 'limit'> = {},
) {
  return request<LogisticsCallbackIssueRecord[]>(opsUrl('logistics-callback-issues', params));
}
