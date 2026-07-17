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

export interface OrderProgressSnapshot {
  orderId: string;
  tenantId: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  createdAt: string;
  updatedAt: string;
  prescriptions: PrescriptionProgress[];
  workflowTasks: WorkflowProgress[];
  dispenseRecords: DispenseProgress[];
  decoctionTasks: DecoctionProgress[];
  shipments: ShipmentProgress[];
  callbacks: CallbackProgress[];
  statusLogs: StatusLogProgress[];
}

export interface PrescriptionProgress {
  prescriptionId: string;
  prescriptionNo: string;
  externalPrescriptionNo: string;
  prescriptionStatus: string;
  detailCount: number;
  createdAt: string;
}

export interface WorkflowProgress {
  taskId: string;
  taskType: string;
  taskStatus: string;
  operator: string | null;
  comment: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface DispenseProgress {
  recordId: string;
  taskId: string;
  dispenser: string;
  dispenseComment: string | null;
  printStatus: string;
  dispensedAt: string;
}

export interface DecoctionProgress {
  taskId: string;
  taskNo: string;
  prescriptionNo: string;
  deviceCode: string;
  pailNo: string | null;
  taskStatus: string;
  operator: string;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface ShipmentProgress {
  shipmentId: string;
  logisticsNo: string;
  logisticsCompany: string;
  logisticsStatus: string;
  latestTraceStatus: string | null;
  latestTraceContent: string | null;
  latestTraceTime: string | null;
}

export interface CallbackProgress {
  callbackId: string;
  callbackType: string;
  businessId: string;
  callbackStatus: string;
  retryCount: number;
  nextRetryAt: string | null;
  updatedAt: string;
}

export interface StatusLogProgress {
  logId: string;
  fromStatus: string | null;
  toStatus: string;
  operatorType: string;
  source: string;
  createdAt: string;
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

export interface LogisticsCallbackIssueRecord {
  callbackId: string;
  tenantId: string;
  orderId: string | null;
  orderNo: string | null;
  callbackType: string;
  businessId: string;
  requestUrl: string | null;
  responseBody: string | null;
  callbackStatus: string;
  retryCount: number;
  nextRetryAt: string | null;
  callbackCreatedAt: string;
  callbackUpdatedAt: string;
  shipmentId: string | null;
  logisticsNo: string | null;
  logisticsCompany: string | null;
  logisticsStatus: string | null;
  latestTraceStatus: string | null;
  latestTraceContent: string | null;
  latestTraceTime: string | null;
}

export interface IntegrationRetryIssueRecord {
  taskId: string;
  messageId: string;
  taskType: string;
  targetSystem: string;
  businessKey: string | null;
  requestUrl: string;
  responseBody: string | null;
  taskStatus: string;
  retryCount: number;
  nextRetryAt: string | null;
  taskCreatedAt: string;
  taskUpdatedAt: string;
  processedAt: string | null;
  sourceType: string;
  sourceSystem: string;
  externalMessageId: string;
  messageType: string;
  processStatus: string;
  failureReason: string | null;
}

export interface OpsHealthOverview {
  recentHours: number;
  pendingOutbox: number;
  failedOutbox: number;
  failedConsumes: number;
  rejectedValidations: number;
  failedCallbacks: number;
  deadCallbacks: number;
  failedIntegrationRetries: number;
  deadIntegrationRetries: number;
  recentAccessCount: number;
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

export interface PortalPrescriptionRecord {
  prescriptionNo: string;
  prescriptionStatus: string;
  prescriptionType: string | null;
  doctorName: string | null;
  diagnosis: string | null;
}

export interface PortalShipmentRecord {
  logisticsNo: string | null;
  logisticsCompany: string | null;
  logisticsStatus: string | null;
  latestTraceTime: string | null;
}

export interface PortalOrderRecord {
  tenantId: string;
  orderId: string;
  institutionName: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  patientName: string | null;
  patientPhone: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverAddress: string | null;
  createdAt: string;
  prescriptions: PortalPrescriptionRecord[];
  shipment: PortalShipmentRecord | null;
}

export interface AddressSupplementCommand {
  phone: string;
  receiverName: string;
  receiverPhone: string;
  receiverProvince?: string;
  receiverCity?: string;
  receiverZone?: string;
  receiverAddress: string;
  requesterName?: string;
  requesterPhone?: string;
  remark?: string;
}

export interface AddressSupplementRecord {
  supplementId: string;
  tenantId: string;
  orderId: string;
  orderNo: string;
  supplementStatus: string;
  receiverName: string;
  receiverPhone: string;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string;
  requesterName: string | null;
  requesterPhone: string | null;
  remark: string | null;
  createdAt: string;
}

export interface ReportStatusCount {
  status: string;
  count: number;
}

export interface DailyOrderCount {
  day: string;
  count: number;
}

export interface ReportOverview {
  from: string | null;
  to: string | null;
  trendDays: number;
  totalOrders: number;
  totalPrescriptions: number;
  totalShipments: number;
  totalCallbacks: number;
  pendingAddressSupplements: number;
  orderStatusCounts: ReportStatusCount[];
  callbackStatusCounts: ReportStatusCount[];
  dailyOrderCounts: DailyOrderCount[];
}

export interface CommunityMessageCommand {
  areaCode?: string;
  communityCode: string;
  externalMessageId: string;
  messageType: string;
  businessKey?: string;
  rawPayload?: string;
}

export interface AddressPushCommand {
  supplementId: string;
  hospitalCode: string;
  adapterCode: string;
  orderNo: string;
  rawPayload?: string;
  requestUrl?: string;
}

export interface CommunityStatusPushCommand {
  communityCode: string;
  orderNo: string;
  status: string;
  requestUrl: string;
  rawPayload?: string;
}

export interface IntegrationMessageRecord {
  messageId: string;
  sourceType: string;
  sourceSystem: string;
  externalMessageId: string;
  messageType: string;
  businessKey: string | null;
  processStatus: string;
  normalizedPayload: string;
  rawPayload: string;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
  processedAt: string | null;
}

export interface IntegrationRetryTaskRecord {
  taskId: string;
  messageId: string;
  taskType: string;
  targetSystem: string;
  businessKey: string | null;
  requestUrl: string;
  requestBody: string;
  responseBody: string | null;
  taskStatus: string;
  retryCount: number;
  nextRetryAt: string | null;
  createdAt: string;
  updatedAt: string;
  processedAt: string | null;
}

export interface HospitalOrderRecord {
  tenantId: string;
  orderId: string;
  institutionName: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  prescriptionNo: string;
  prescriptionStatus: string;
  patientName: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverAddress: string | null;
  logisticsNo: string | null;
  logisticsCompany: string | null;
  logisticsStatus: string | null;
  createdAt: string;
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
