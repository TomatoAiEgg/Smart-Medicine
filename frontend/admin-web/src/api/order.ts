import { request } from './client';
import type {
  AdminOrderAddressUpdateCommand,
  AdminOrderAddressUpdateResult,
  AdminBatchOrderReceiptCommand,
  AdminBatchOrderReceiptResult,
  AdminOrderCancelCommand,
  AdminOrderCancelResult,
  AdminOrderDetail,
  AdminOrderInitializeCommand,
  AdminOrderInitializeResult,
  AdminOrderDetailPrescription,
  AdminOrderPage,
  AdminOrderQueryParams,
  AdminOrderReceiptCommand,
  AdminOrderReceiptPage,
  AdminOrderReceiptQueryParams,
  AdminOrderReceiptResult,
  AdminPrescriptionActionCommand,
  AdminPrescriptionActionResult,
  AdminPrescriptionPrintPayload,
  AdminPrescriptionReprintPage,
  AdminPrescriptionReprintQueryParams,
  AdminPrescriptionUpdateCommand,
  OrderCreateResult,
  OrderProgressSnapshot,
} from './types';

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

export function listAdminOrderReceipts(params: AdminOrderReceiptQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/order-receipts?${query}` : '/order-api/api/admin/order-receipts';
  return request<AdminOrderReceiptPage>(url);
}

export function listAdminPrescriptionReprints(params: AdminPrescriptionReprintQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query
    ? `/order-api/api/admin/prescription-reprints?${query}`
    : '/order-api/api/admin/prescription-reprints';
  return request<AdminPrescriptionReprintPage>(url);
}

export function getAdminPrescriptionPrintPayload(prescriptionNo: string) {
  return request<AdminPrescriptionPrintPayload>(
    `/order-api/api/admin/prescription-reprints/${encodeURIComponent(prescriptionNo)}/print-payload`,
  );
}

export async function downloadAdminOrdersCsv(params: AdminOrderQueryParams = {}) {
  const query = buildOrderQuery(params);
  const response = await fetch(`/order-api/api/admin/orders/export.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function getOrder(orderNo: string) {
  return request<OrderCreateResult>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}`);
}

export function getAdminOrderDetail(orderNo: string) {
  return request<AdminOrderDetail>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/detail`);
}

export function updateAdminOrderAddress(orderNo: string, command: AdminOrderAddressUpdateCommand) {
  return request<AdminOrderAddressUpdateResult>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/address`,
    {
      method: 'PATCH',
      body: JSON.stringify(command),
    },
  );
}

export function updateAdminPrescription(
  orderNo: string,
  prescriptionId: string,
  command: AdminPrescriptionUpdateCommand,
) {
  return request<AdminOrderDetailPrescription>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/prescriptions/${encodeURIComponent(prescriptionId)}`,
    {
      method: 'PATCH',
      body: JSON.stringify(command),
    },
  );
}

export function cancelAdminOrder(orderNo: string, command: AdminOrderCancelCommand) {
  return request<AdminOrderCancelResult>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/cancel`,
    {
      method: 'POST',
      body: JSON.stringify(command),
    },
  );
}

export function initializeAdminOrder(orderNo: string, command: AdminOrderInitializeCommand) {
  return request<AdminOrderInitializeResult>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/initialize`,
    {
      method: 'POST',
      body: JSON.stringify(command),
    },
  );
}

export function initializeAdminPrescription(
  orderNo: string,
  prescriptionId: string,
  command: AdminPrescriptionActionCommand,
) {
  return request<AdminPrescriptionActionResult>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/prescriptions/${encodeURIComponent(prescriptionId)}/initialize`,
    {
      method: 'POST',
      body: JSON.stringify(command),
    },
  );
}

export function cancelAdminPrescription(
  orderNo: string,
  prescriptionId: string,
  command: AdminPrescriptionActionCommand,
) {
  return request<AdminPrescriptionActionResult>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/prescriptions/${encodeURIComponent(prescriptionId)}/cancel`,
    {
      method: 'POST',
      body: JSON.stringify(command),
    },
  );
}

export function receiptAdminOrder(orderNo: string, command: AdminOrderReceiptCommand) {
  return request<AdminOrderReceiptResult>(
    `/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/receipt`,
    {
      method: 'POST',
      body: JSON.stringify(command),
    },
  );
}

export function batchReceiptAdminOrders(command: AdminBatchOrderReceiptCommand) {
  return request<AdminBatchOrderReceiptResult>('/order-api/api/admin/order-receipts/batch', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function getOrderProgress(orderNo: string) {
  return request<OrderProgressSnapshot>(`/order-api/api/admin/orders/${encodeURIComponent(orderNo)}/progress`);
}
