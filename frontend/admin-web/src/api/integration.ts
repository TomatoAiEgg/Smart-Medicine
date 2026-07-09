import { request } from './client';
import type {
  AddressPushCommand,
  CommunityMessageCommand,
  HospitalOrderRecord,
  IntegrationMessageRecord,
} from './types';

interface IntegrationMessageQuery {
  sourceType?: string;
  processStatus?: string;
  businessKey?: string;
  limit?: number;
}

interface IntegrationQueryParams extends IntegrationMessageQuery {
  phone?: string;
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
