import { request } from './client';
import type {
  AddressPushCommand,
  CommunityMessageCommand,
  CommunityStatusPushCommand,
  HospitalOrderRecord,
  IntegrationMessageRecord,
  IntegrationRetryTaskRecord,
} from './types';

interface IntegrationMessageQuery {
  sourceType?: string;
  processStatus?: string;
  businessKey?: string;
  limit?: number;
}

interface IntegrationRetryTaskQuery {
  taskType?: string;
  taskStatus?: string;
  businessKey?: string;
  limit?: number;
}

interface IntegrationQueryParams extends IntegrationMessageQuery {
  phone?: string;
  taskType?: string;
  taskStatus?: string;
}

function setQueryParam(query: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && String(value).trim() !== '') {
    query.set(key, String(value).trim());
  }
}

function buildQuery(params: IntegrationQueryParams) {
  const query = new URLSearchParams();
  setQueryParam(query, 'sourceType', params.sourceType);
  setQueryParam(query, 'processStatus', params.processStatus);
  setQueryParam(query, 'businessKey', params.businessKey);
  setQueryParam(query, 'limit', params.limit);
  setQueryParam(query, 'phone', params.phone);
  setQueryParam(query, 'taskType', params.taskType);
  setQueryParam(query, 'taskStatus', params.taskStatus);
  return query.toString();
}

export function recordCommunityMessage(command: CommunityMessageCommand) {
  return request<IntegrationMessageRecord>('/integration-api/api/integration/community/messages', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function recordAddressPush(command: AddressPushCommand) {
  return request<IntegrationMessageRecord>('/integration-api/api/integration/address/push-records', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function createCommunityStatusPush(command: CommunityStatusPushCommand) {
  return request<IntegrationMessageRecord>('/integration-api/api/integration/community/status-pushes', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function findHospitalOrderByPrescription(prescriptionNo: string, phone: string) {
  const query = buildQuery({ phone });
  return request<HospitalOrderRecord>(
    `/integration-api/api/integration/hospital/prescriptions/${encodeURIComponent(prescriptionNo)}/order?${query}`,
  );
}

export function listIntegrationMessages(params: IntegrationMessageQuery = {}) {
  const query = buildQuery(params);
  return request<IntegrationMessageRecord[]>(`/integration-api/api/admin/integration/messages${query ? `?${query}` : ''}`);
}

export function listIntegrationRetryTasks(params: IntegrationRetryTaskQuery = {}) {
  const query = buildQuery(params);
  return request<IntegrationRetryTaskRecord[]>(`/integration-api/api/admin/integration/retry-tasks${query ? `?${query}` : ''}`);
}

export function dispatchDueIntegrationRetryTasks(limit = 20) {
  const query = buildQuery({ limit });
  return request<number>(`/integration-api/api/admin/integration/retry-tasks/dispatch-due?${query}`, {
    method: 'POST',
  });
}
