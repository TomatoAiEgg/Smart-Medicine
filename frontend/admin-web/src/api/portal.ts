import { request } from './client';
import type { AddressSupplementCommand, AddressSupplementRecord, PortalOrderRecord } from './types';

interface PortalOrderQuery {
  orderNo?: string;
  externalOrderNo?: string;
  phone: string;
}

function buildQuery(params: PortalOrderQuery) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

export function queryPortalOrder(params: PortalOrderQuery) {
  return request<PortalOrderRecord>(`/portal-api/api/portal/orders/query?${buildQuery(params)}`);
}

export function createAddressSupplement(orderNo: string, command: AddressSupplementCommand) {
  return request<AddressSupplementRecord>(`/portal-api/api/portal/orders/${encodeURIComponent(orderNo)}/address-supplements`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}
