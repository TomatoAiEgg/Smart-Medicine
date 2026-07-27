import { request } from './client';
import type { AdminOrderPage, AdminOrderQueryParams, OrderCreateResult, OrderProgressSnapshot } from './types';

function buildOrderQuery(params: AdminOrderQueryParams) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

export function listAdminOrders(params: AdminOrderQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/orders?${query}` : '/order-api/api/admin/orders';
  return request<AdminOrderPage>(url);
}

export function getOrder(orderNo: string) {
  return request<OrderCreateResult>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}`);
}

export function getOrderProgress(orderNo: string) {
  return request<OrderProgressSnapshot>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/progress`);
}
