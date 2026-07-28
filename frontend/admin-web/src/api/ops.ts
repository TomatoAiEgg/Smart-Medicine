import { request } from './client';
import type {
  ApiAccessLogRecord,
  DeadLetterOperationResult,
  DeadLetterRecord,
  EventOutboxRecord,
  IntegrationRetryIssueRecord,
  LogisticsCallbackIssueRecord,
  MessageConsumeRecord,
  OrderObservabilityBundle,
  OrderValidationRecord,
  OpsHealthOverview,
  ProblemRegistrationActionRecord,
  ProblemRegistrationCommand,
  ProblemRegistrationHandleCommand,
  ProblemRegistrationQueryParams,
  ProblemRegistrationRecord,
} from './types';

interface OpsQueryParams {
  limit?: number;
  status?: string;
  topic?: string;
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
  externalOrderNo?: string;
  taskStatus?: string;
  taskType?: string;
  businessKey?: string;
  sourceSystem?: string;
  recentHours?: number;
}

interface DeadLetterOperationRequest {
  operator?: string;
  remark?: string;
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

export function listDeadLetters(params: Pick<OpsQueryParams, 'status' | 'topic' | 'eventId' | 'limit'> = {}) {
  return request<DeadLetterRecord[]>(opsUrl('dead-letters', params));
}

export function replayDeadLetter(id: string, body: DeadLetterOperationRequest = {}) {
  return request<DeadLetterOperationResult>(opsUrl(`dead-letters/${id}/replay`, {}), {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export function closeDeadLetter(id: string, body: DeadLetterOperationRequest = {}) {
  return request<DeadLetterOperationResult>(opsUrl(`dead-letters/${id}/close`, {}), {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
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

export function listIntegrationRetryIssues(
  params: Pick<OpsQueryParams, 'taskStatus' | 'taskType' | 'businessKey' | 'sourceSystem' | 'limit'> = {},
) {
  return request<IntegrationRetryIssueRecord[]>(opsUrl('integration-retry-issues', params));
}

export function listProblemRegistrations(params: ProblemRegistrationQueryParams = {}) {
  return request<ProblemRegistrationRecord[]>(opsUrl('problem-registrations', params));
}

export function createProblemRegistration(command: ProblemRegistrationCommand) {
  return request<ProblemRegistrationRecord>(opsUrl('problem-registrations', {}), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
  });
}

export function updateProblemRegistration(id: string, command: ProblemRegistrationCommand) {
  return request<ProblemRegistrationRecord>(opsUrl(`problem-registrations/${id}`, {}), {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
  });
}

export function handleProblemRegistration(id: string, command: ProblemRegistrationHandleCommand) {
  return request<ProblemRegistrationRecord>(opsUrl(`problem-registrations/${id}/handle`, {}), {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
  });
}

export function listProblemRegistrationActions(id: string) {
  return request<ProblemRegistrationActionRecord[]>(opsUrl(`problem-registrations/${id}/actions`, {}));
}

export function getOpsHealthOverview(params: Pick<OpsQueryParams, 'recentHours'> = {}) {
  return request<OpsHealthOverview>(opsUrl('health-overview', params));
}

export function getOrderObservability(
  params: Pick<OpsQueryParams, 'orderNo' | 'externalOrderNo' | 'limit'>,
) {
  return request<OrderObservabilityBundle>(opsUrl('order-observability', params));
}
