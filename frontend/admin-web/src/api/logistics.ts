import { request } from './client';
import type {
  DeliveryOrderRecord,
  PackShipmentCommand,
  ShipmentActionCommand,
  ShipmentRecord,
  ShipmentTraceRecord,
  TraceCommand,
} from './types';

interface LogisticsQueryParams {
  status?: string;
  orderNo?: string;
  limit?: number;
}

function buildQuery(params: LogisticsQueryParams) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

export function listReadyDeliveryOrders(limit = 50) {
  return request<DeliveryOrderRecord[]>(`/logistics-api/api/admin/logistics/orders/ready?limit=${limit}`);
}

export function listShipments(params: LogisticsQueryParams = {}) {
  const query = buildQuery(params);
  return request<ShipmentRecord[]>(`/logistics-api/api/admin/logistics/shipments${query ? `?${query}` : ''}`);
}

export function packShipment(command: PackShipmentCommand) {
  return request<ShipmentRecord>('/logistics-api/api/admin/logistics/shipments/pack', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function shipShipment(shipmentId: string, command: ShipmentActionCommand) {
  return request<ShipmentRecord>(`/logistics-api/api/admin/logistics/shipments/${shipmentId}/ship`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function signShipment(shipmentId: string, command: ShipmentActionCommand) {
  return request<ShipmentRecord>(`/logistics-api/api/admin/logistics/shipments/${shipmentId}/sign`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function receiveShipmentTrace(command: TraceCommand) {
  return request<ShipmentRecord>('/logistics-api/api/admin/logistics/shipments/trace', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function listShipmentTraces(shipmentId: string) {
  return request<ShipmentTraceRecord[]>(`/logistics-api/api/admin/logistics/shipments/${shipmentId}/traces`);
}
