import { request } from './client';
import type { OrderCreateResult, OrderProgressSnapshot } from './types';

export function getOrder(orderNo: string) {
  return request<OrderCreateResult>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}`);
}

export function getOrderProgress(orderNo: string) {
  return request<OrderProgressSnapshot>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/progress`);
}
