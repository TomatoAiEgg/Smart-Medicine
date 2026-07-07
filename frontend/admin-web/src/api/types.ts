export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface OrderCreateResult {
  orderId: string;
  orderNo: string;
  externalOrderNo: string;
  status: string;
  duplicated: boolean;
}

export interface WorkflowTaskSnapshot {
  taskId: string;
  tenantId: string;
  orderId: string;
  taskType: string;
  taskStatus: string;
  sourceEventId: string;
  reviewer: string | null;
  reviewComment: string | null;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  validationStatus: string | null;
  validationMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface OrderReviewCommand {
  reviewer: string;
  reviewComment: string;
}

export interface OrderReviewResult {
  taskId: string;
  orderId: string;
  orderNo: string;
  taskStatus: string;
  orderStatus: string;
  reviewer: string;
  reviewComment: string | null;
  completedAt: string;
}

export interface PrescriptionRecord {
  tenantId: string;
  orderId: string;
  prescriptionId: string;
  orderNo: string;
  externalOrderNo: string;
  prescriptionNo: string;
  orderStatus: string;
}

export interface DeviceRecord {
  deviceCode: string;
  deviceName: string;
  deviceStatus: string;
  activeTaskNo: string | null;
  activePrescriptionNo: string | null;
}

export interface DecoctionTaskRecord {
  taskId: string;
  taskNo: string;
  tenantId: string;
  orderId: string;
  prescriptionId: string;
  orderNo: string;
  prescriptionNo: string;
  deviceCode: string;
  pailNo: string | null;
  taskStatus: string;
  operator: string;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DecoctionTaskEventRecord {
  eventId: string;
  taskId: string;
  taskNo: string;
  tenantId: string;
  orderId: string;
  eventType: string;
  operationId: string;
  operator: string;
  eventPayload: string;
  eventTime: string;
  createdAt: string;
}

export interface DeviceWorkRecord {
  recordId: string;
  taskId: string;
  taskNo: string;
  tenantId: string;
  orderId: string;
  prescriptionNo: string;
  deviceCode: string;
  pailNo: string | null;
  actionType: string;
  actionResult: string;
  taskStatusBefore: string | null;
  taskStatusAfter: string | null;
  operationId: string;
  source: string;
  operator: string;
  detailPayload: string;
  actionTime: string;
  createdAt: string;
}

export interface EventOutboxRecord {
  id: string;
  tenantId: string;
  eventId: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  status: string;
  retryCount: number;
  nextRetryAt: string | null;
  createdAt: string;
  publishedAt: string | null;
}

export interface MessageConsumeRecord {
  id: string;
  consumerGroup: string;
  messageId: string;
  eventId: string;
  status: string;
  createdAt: string;
}

export interface OrderValidationRecord {
  id: string;
  tenantId: string;
  orderId: string;
  eventId: string;
  validationStatus: string;
  validationMessage: string | null;
  createdAt: string;
}

export interface ApiAccessLogRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  appKey: string;
  requestPath: string;
  requestIp: string;
  resultCode: string;
  createdAt: string;
}

export interface DeliveryOrderRecord {
  tenantId: string;
  orderId: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  receiverName: string;
  receiverPhone: string;
  receiverAddress: string;
}

export interface ShipmentRecord {
  shipmentId: string;
  tenantId: string;
  orderId: string;
  orderNo: string;
  logisticsNo: string;
  logisticsCompany: string;
  logisticsStatus: string;
  payMethod: string | null;
  pkgWeight: number | null;
  pkgNum: number | null;
  packageTime: string | null;
  outboundTime: string | null;
  signTime: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ShipmentTraceRecord {
  traceId: string;
  tenantId: string;
  shipmentId: string;
  orderId: string;
  logisticsNo: string;
  traceStatus: string;
  traceContent: string | null;
  rawPayload: string;
  traceTime: string;
  createdAt: string;
}

export interface CallbackRecord {
  id: string;
  tenantId: string;
  orderId: string;
  orderNo: string;
  callbackType: string;
  businessId: string;
  requestUrl: string | null;
  requestBody: string;
  responseBody: string | null;
  status: string;
  retryCount: number;
  nextRetryAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PackShipmentCommand {
  orderNo: string;
  logisticsCompany: string;
  logisticsNo?: string;
  payMethod?: string;
  pkgWeight?: number;
  pkgNum?: number;
  operator: string;
}

export interface ShipmentActionCommand {
  operator: string;
  remark?: string;
}

export interface TraceCommand {
  logisticsNo: string;
  provider: string;
  opCode: string;
  traceContent?: string;
  rawPayload?: string;
  traceTime: string;
  operator: string;
}

export interface SimulatorOperationCommand {
  operationId: string;
  deviceCode: string;
  prescriptionNo: string;
  pailNo?: string;
  operator: string;
  timestamp: string;
  sign: string;
}

export interface DecoctionEventCommand {
  operationId: string;
  operator: string;
  timestamp: string;
  sign: string;
  reason?: string;
  waterVolumeMl?: number;
  temperatureCelsius?: number;
  durationSeconds?: number;
  remark?: string;
}

export interface MesTaskOperationCommand {
  operationId: string;
  operator: string;
  timestamp: string;
  sign: string;
}
