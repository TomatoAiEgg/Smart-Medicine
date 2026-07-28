import { request } from './client';
import type {
  AdminInstitutionApiCommand,
  AdminInstitutionApiPage,
  AdminInstitutionApiQueryParams,
  AdminInstitutionApiRecord,
  AdminInstitutionAppCommand,
  AdminInstitutionAppPage,
  AdminInstitutionAppQueryParams,
  AdminInstitutionAppRecord,
  AdminInstitutionCommand,
  AdminInstitutionIpWhitelistCommand,
  AdminInstitutionIpWhitelistPage,
  AdminInstitutionIpWhitelistQueryParams,
  AdminInstitutionIpWhitelistRecord,
  AdminInstitutionPage,
  AdminInstitutionQueryParams,
  AdminInstitutionRecord,
  AdminOrderAddressUpdateCommand,
  AdminOrderAddressUpdateResult,
  AdminBatchOrderReceiptCommand,
  AdminBatchOrderReceiptResult,
  AdminManualProcessCommand,
  AdminManualProcessPage,
  AdminManualProcessQueryParams,
  AdminManualProcessResult,
  AdminOrderWarehousePage,
  AdminOrderWarehouseQueryParams,
  AdminOrderCancelCommand,
  AdminOrderCancelResult,
  AdminOrderDetail,
  AdminOrderInitializeCommand,
  AdminOrderInitializeResult,
  AdminOrderDetailPrescription,
  AdminOrderPage,
  AdminOrderQueryParams,
  AdminOperatorCommand,
  AdminOperatorPage,
  AdminOperatorQueryParams,
  AdminOperatorRecord,
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

export function listAdminInstitutions(params: AdminInstitutionQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/institutions?${query}` : '/order-api/api/admin/institutions';
  return request<AdminInstitutionPage>(url);
}

export function createAdminInstitution(command: AdminInstitutionCommand) {
  return request<AdminInstitutionRecord>('/order-api/api/admin/institutions', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function updateAdminInstitution(institutionId: string, command: AdminInstitutionCommand) {
  return request<AdminInstitutionRecord>(`/order-api/api/admin/institutions/${encodeURIComponent(institutionId)}`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function listAdminInstitutionApps(params: AdminInstitutionAppQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/institution-apps?${query}` : '/order-api/api/admin/institution-apps';
  return request<AdminInstitutionAppPage>(url);
}

export function createAdminInstitutionApp(command: AdminInstitutionAppCommand) {
  return request<AdminInstitutionAppRecord>('/order-api/api/admin/institution-apps', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function updateAdminInstitutionApp(appId: string, command: AdminInstitutionAppCommand) {
  return request<AdminInstitutionAppRecord>(`/order-api/api/admin/institution-apps/${encodeURIComponent(appId)}`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function listAdminInstitutionApis(params: AdminInstitutionApiQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/institution-apis?${query}` : '/order-api/api/admin/institution-apis';
  return request<AdminInstitutionApiPage>(url);
}

export function createAdminInstitutionApi(command: AdminInstitutionApiCommand) {
  return request<AdminInstitutionApiRecord>('/order-api/api/admin/institution-apis', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function updateAdminInstitutionApi(apiId: string, command: AdminInstitutionApiCommand) {
  return request<AdminInstitutionApiRecord>(`/order-api/api/admin/institution-apis/${encodeURIComponent(apiId)}`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function listAdminInstitutionIpWhitelists(params: AdminInstitutionIpWhitelistQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query
    ? `/order-api/api/admin/institution-ip-whitelists?${query}`
    : '/order-api/api/admin/institution-ip-whitelists';
  return request<AdminInstitutionIpWhitelistPage>(url);
}

export function createAdminInstitutionIpWhitelist(command: AdminInstitutionIpWhitelistCommand) {
  return request<AdminInstitutionIpWhitelistRecord>('/order-api/api/admin/institution-ip-whitelists', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function updateAdminInstitutionIpWhitelist(whitelistId: string, command: AdminInstitutionIpWhitelistCommand) {
  return request<AdminInstitutionIpWhitelistRecord>(
    `/order-api/api/admin/institution-ip-whitelists/${encodeURIComponent(whitelistId)}`,
    {
      method: 'PATCH',
      body: JSON.stringify(command),
    },
  );
}

export function listAdminOperators(params: AdminOperatorQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/operators?${query}` : '/order-api/api/admin/operators';
  return request<AdminOperatorPage>(url);
}

export function createAdminOperator(command: AdminOperatorCommand) {
  return request<AdminOperatorRecord>('/order-api/api/admin/operators', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function updateAdminOperator(operatorId: string, command: AdminOperatorCommand) {
  return request<AdminOperatorRecord>(`/order-api/api/admin/operators/${encodeURIComponent(operatorId)}`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function listAdminPrescriptionReprints(params: AdminPrescriptionReprintQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query
    ? `/order-api/api/admin/prescription-reprints?${query}`
    : '/order-api/api/admin/prescription-reprints';
  return request<AdminPrescriptionReprintPage>(url);
}

export function listAdminManualProcessOrders(params: AdminManualProcessQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query
    ? `/order-api/api/admin/manual-process-orders?${query}`
    : '/order-api/api/admin/manual-process-orders';
  return request<AdminManualProcessPage>(url);
}

export function manualProcessAdminOrder(orderNo: string, command: AdminManualProcessCommand) {
  return request<AdminManualProcessResult>(
    `/order-api/api/admin/manual-process-orders/${encodeURIComponent(orderNo)}/process`,
    {
      method: 'POST',
      body: JSON.stringify(command),
    },
  );
}

export function listAdminOrderWarehouses(params: AdminOrderWarehouseQueryParams = {}) {
  const query = buildOrderQuery(params);
  const url = query ? `/order-api/api/admin/order-warehouses?${query}` : '/order-api/api/admin/order-warehouses';
  return request<AdminOrderWarehousePage>(url);
}

export async function downloadAdminOrderWarehousesCsv(params: AdminOrderWarehouseQueryParams = {}) {
  const query = buildOrderQuery(params);
  const response = await fetch(`/order-api/api/admin/order-warehouses/export.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
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
