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

export interface AdminOrderQueryParams {
  startTime?: string;
  endTime?: string;
  institution?: string;
  prescriptionType?: string;
  hospitalType?: string;
  orderStatus?: string;
  excludeOrderStatus?: string;
  decoctionCenter?: string;
  deliveryType?: string;
  logisticsCompany?: string;
  province?: string;
  keyword?: string;
  hospitalPrescriptionNo?: string;
  patientName?: string;
  receiverPhone?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminOrderListItem {
  orderId: string;
  tenantId: string;
  institutionId: string;
  institutionName: string;
  storageType: string | null;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  patientName: string | null;
  patientPhone: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  prescriptionId: string;
  prescriptionStatus: string;
  prescriptionNos: string;
  externalPrescriptionNos: string;
  prescriptionTypes: string;
  hospitalTypes: string;
  prescriptionCount: number;
  detailCount: number;
  doseCount: number | null;
  isWithin: number | null;
  totalAmount: number | string | null;
  deliveryTime: string | null;
  batchNo: string | null;
  orderRemark: string | null;
  logisticsCompany: string | null;
  logisticsNo: string | null;
  logisticsStatus: string | null;
  latestTraceTime: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderPage {
  records: AdminOrderListItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminOrderReceiptQueryParams {
  prescriptionNo?: string;
  receiverName?: string;
  receiverPhone?: string;
  patientName?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminOrderReceiptItem {
  orderId: string;
  tenantId: string;
  orderNo: string;
  externalOrderNo: string;
  institutionName: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  patientName: string | null;
  prescriptionTypes: string;
  orderStatus: string;
  logisticsCompany: string | null;
  logisticsNo: string | null;
  logisticsStatus: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderReceiptPage {
  records: AdminOrderReceiptItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminOrderReceiptCommand {
  operator?: string;
  reason?: string;
}

export interface AdminBatchOrderReceiptCommand {
  orderNos: string[];
  operator?: string;
  reason?: string;
}

export interface AdminOrderReceiptResult {
  orderNo: string;
  fromStatus: string | null;
  toStatus: string;
  success: boolean;
  message: string;
  signedAt: string;
}

export interface AdminBatchOrderReceiptResult {
  totalCount: number;
  successCount: number;
  failCount: number;
  items: AdminOrderReceiptResult[];
}

export interface AdminOperatorQueryParams {
  keyword?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminOperatorRecord {
  id: string;
  tenantId: string;
  username: string;
  displayName: string;
  roleCode: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOperatorPage {
  records: AdminOperatorRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminOperatorCommand {
  username?: string;
  displayName: string;
  roleCode?: string;
  enabled?: boolean;
}

export interface AdminDictTypeQueryParams {
  keyword?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminDictTypeRecord {
  id: string;
  tenantId: string;
  typeCode: string;
  typeName: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminDictTypePage {
  records: AdminDictTypeRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminDictTypeCommand {
  typeCode?: string;
  typeName: string;
  enabled?: boolean;
}

export interface AdminDictItemQueryParams {
  keyword?: string;
  typeId?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminDictItemRecord {
  id: string;
  tenantId: string;
  typeId: string;
  typeCode: string;
  typeName: string;
  itemCode: string;
  itemName: string;
  itemValue: string | null;
  sortNo: number;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminDictItemPage {
  records: AdminDictItemRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminDictItemCommand {
  typeId?: string;
  itemCode?: string;
  itemName: string;
  itemValue?: string;
  sortNo?: number;
  enabled?: boolean;
  remark?: string;
}

export interface AdminSystemConfigQueryParams {
  keyword?: string;
  valueType?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminSystemConfigRecord {
  id: string;
  tenantId: string;
  configKey: string;
  configName: string;
  configValue: string;
  valueType: string;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminSystemConfigPage {
  records: AdminSystemConfigRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminSystemConfigCommand {
  configKey?: string;
  configName: string;
  configValue: string;
  valueType?: string;
  enabled?: boolean;
  remark?: string;
}

export interface AdminInstitutionQueryParams {
  keyword?: string;
  status?: string;
  institutionType?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminInstitutionRecord {
  id: string;
  tenantId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  status: string;
  storageType: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminInstitutionPage {
  records: AdminInstitutionRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminInstitutionCommand {
  institutionCode?: string;
  institutionName: string;
  institutionType?: string;
  status?: string;
  storageType?: string;
}

export interface AdminInstitutionAppQueryParams {
  keyword?: string;
  institutionId?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminInstitutionAppRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  appKey: string;
  signType: string;
  callbackUrl: string | null;
  enabled: boolean;
  appSecretConfigured: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminInstitutionAppPage {
  records: AdminInstitutionAppRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminInstitutionAppCommand {
  institutionId?: string;
  appKey?: string;
  appSecret?: string;
  signType?: string;
  callbackUrl?: string;
  enabled?: boolean;
}

export interface AdminInstitutionApiQueryParams {
  keyword?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminInstitutionApiRecord {
  id: string;
  apiCode: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  description: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminInstitutionApiPage {
  records: AdminInstitutionApiRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminInstitutionApiCommand {
  apiCode?: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  description?: string;
  enabled?: boolean;
}

export interface AdminInstitutionApiPermissionQueryParams {
  keyword?: string;
  institutionId?: string;
  apiId?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminInstitutionApiPermissionRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  apiId: string;
  apiCode: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminInstitutionApiPermissionPage {
  records: AdminInstitutionApiPermissionRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminInstitutionApiPermissionCommand {
  institutionId?: string;
  apiId?: string;
  remark?: string;
  enabled?: boolean;
}

export interface AdminInstitutionIpWhitelistQueryParams {
  keyword?: string;
  institutionId?: string;
  ipRange?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminInstitutionIpWhitelistRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  ipRange: string;
  enabled: boolean;
  createdAt: string;
}

export interface AdminInstitutionIpWhitelistPage {
  records: AdminInstitutionIpWhitelistRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminInstitutionIpWhitelistCommand {
  institutionId?: string;
  ipRange: string;
  enabled?: boolean;
}

export interface AdminLogisticsSpecialRuleQueryParams {
  keyword?: string;
  institutionId?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminLogisticsSpecialRuleRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  ruleName: string;
  logisticsCompany: string;
  baseFee: number | string;
  extraFee: number | string;
  freeThreshold: number | string;
  remark: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminLogisticsSpecialRulePage {
  records: AdminLogisticsSpecialRuleRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminLogisticsSpecialRuleCommand {
  institutionId?: string;
  ruleName: string;
  logisticsCompany: string;
  baseFee?: number | string;
  extraFee?: number | string;
  freeThreshold?: number | string;
  remark?: string;
  enabled?: boolean;
}

export interface AdminLogisticsAddressCostQueryParams {
  keyword?: string;
  institutionId?: string;
  logisticsCompany?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminLogisticsAddressCostRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  logisticsCompany: string;
  province: string;
  city: string;
  district: string;
  costAmount: number | string;
  remark: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminLogisticsAddressCostPage {
  records: AdminLogisticsAddressCostRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminLogisticsAddressCostCommand {
  institutionId?: string;
  logisticsCompany: string;
  province: string;
  city?: string;
  district?: string;
  costAmount?: number | string;
  remark?: string;
  enabled?: boolean;
}

export interface AdminOrderMergeQueryParams {
  keyword?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminOrderMergeRecord {
  id: string;
  tenantId: string;
  mergeNo: string;
  logisticsCompany: string | null;
  logisticsNo: string | null;
  status: string;
  remark: string | null;
  orderCount: number;
  orderNos: string;
  institutionNames: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderMergePage {
  records: AdminOrderMergeRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminOrderMergeCommand {
  orderNos?: string[];
  logisticsCompany?: string;
  logisticsNo?: string;
  remark?: string;
}

export interface AdminOrderInterceptRuleQueryParams {
  keyword?: string;
  interceptStage?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminOrderInterceptRuleRecord {
  id: string;
  tenantId: string;
  ruleCode: string;
  ruleName: string;
  interceptStage: string;
  matchField: string;
  matchType: string;
  matchValue: string;
  reason: string;
  priority: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminOrderInterceptRulePage {
  records: AdminOrderInterceptRuleRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminOrderInterceptRuleCommand {
  ruleCode?: string;
  ruleName: string;
  interceptStage?: string;
  matchField: string;
  matchType?: string;
  matchValue: string;
  reason: string;
  priority?: number;
  enabled?: boolean;
}

export interface AdminManualProcessQueryParams {
  startTime?: string;
  endTime?: string;
  institution?: string;
  prescriptionType?: string;
  hospitalType?: string;
  isWithin?: number | string;
  processType?: string;
  deliveryType?: string;
  orderNo?: string;
  prescriptionNo?: string;
  hospitalPrescriptionNo?: string;
  patientName?: string;
  doseRange?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminManualProcessItem {
  orderId: string;
  tenantId: string;
  institutionId: string;
  institutionName: string;
  storageType: string | null;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  patientNames: string | null;
  hospitalTypes: string | null;
  prescriptionTypes: string | null;
  prescriptionNos: string | null;
  externalPrescriptionNos: string | null;
  doseCounts: string | null;
  prescriptionCount: number;
  deliveryTime: string | null;
  orderRemark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminManualProcessPage {
  records: AdminManualProcessItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminManualProcessCommand {
  operator?: string;
  auditor?: string;
  auditTime?: string;
  dispenser?: string;
  dispenseTime?: string;
  rechecker?: string;
  recheckTime?: string;
  pailNo?: string;
  soakTimeStart?: string;
  boilTimeStart?: string;
  outboundTime?: string;
  signTime?: string;
  remark?: string;
}

export interface AdminManualProcessResult {
  orderId: string;
  orderNo: string;
  fromStatus: string;
  toStatus: string;
  workflowTaskCount: number;
  dispenseRecordCount: number;
  decoctionTaskCount: number;
  logisticsNo: string;
  callbackSuppressed: boolean;
  processedAt: string;
}

export interface AdminOrderWarehouseQueryParams {
  startTime?: string;
  endTime?: string;
  institution?: string;
  prescriptionType?: string;
  hospitalType?: string;
  orderStatus?: string;
  decoctionCenter?: string;
  deliveryType?: string;
  logisticsCompany?: string;
  province?: string;
  orderNo?: string;
  prescriptionNo?: string;
  hospitalPrescriptionNo?: string;
  patientName?: string;
  receiverPhone?: string;
  nodeTime?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminOrderWarehouseItem {
  orderId: string;
  tenantId: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  createdAt: string;
  batchNo: string | null;
  institutionName: string | null;
  storageType: string | null;
  addressType: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  deliveryTime: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  hospitalTypes: string | null;
  patientName: string | null;
  patientAge: string | null;
  departmentNames: string | null;
  prescriptionTypes: string | null;
  prescriptionNos: string | null;
  externalPrescriptionNos: string | null;
  doseCounts: string | null;
  perPackNums: string | null;
  perPackDoses: string | null;
  logisticsCompany: string | null;
  logisticsNo: string | null;
}

export interface AdminOrderWarehousePage {
  records: AdminOrderWarehouseItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminPrescriptionReprintQueryParams {
  startTime?: string;
  endTime?: string;
  prescriptionNo?: string;
  page?: number;
  pageSize?: number;
}

export interface AdminPrescriptionReprintItem {
  orderId: string;
  prescriptionId: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  prescriptionNo: string;
  externalPrescriptionNo: string | null;
  prescriptionStatus: string;
  institutionName: string;
  patientName: string | null;
  patientPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  deliveryTime: string | null;
  createdAt: string;
  hospitalType: string | null;
  prescriptionType: string | null;
  isWithin: number | null;
  doseCount: number | null;
  batchNo: string | null;
  dispenser: string | null;
}

export interface AdminPrescriptionReprintPage {
  records: AdminPrescriptionReprintItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminPrescriptionPrintPayload {
  orderId: string;
  prescriptionId: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  institutionName: string;
  patientName: string | null;
  patientPhone: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  deliveryTime: string | null;
  batchNo: string | null;
  prescriptionNo: string;
  externalPrescriptionNo: string | null;
  prescriptionType: string | null;
  prescriptionStatus: string;
  hospitalType: string | null;
  doseCount: number | null;
  decoctionCount: number | null;
  boilTimes: number | null;
  isWithin: number | null;
  perPackNum: number | null;
  perPackDose: number | null;
  totalAmount: number | string | null;
  doctorName: string | null;
  diagnosis: string | null;
  departmentName: string | null;
  wardName: string | null;
  bedNo: string | null;
  medicationMethod: string | null;
  medicationInstruction: string | null;
  prescriptionRemark: string | null;
  details: AdminOrderDetailDrug[];
  printedAt: string;
}

export interface AdminLabelTemplateQueryParams {
  keyword?: string;
  institutionId?: string;
  prescriptionType?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminLabelTemplateRecord {
  id: string;
  tenantId: string;
  templateCode: string;
  templateName: string;
  scopeType: string;
  institutionId: string | null;
  institutionName: string | null;
  prescriptionType: string | null;
  labelWidthMm: number;
  labelHeightMm: number;
  contentTemplate: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminLabelTemplatePage {
  records: AdminLabelTemplateRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminLabelTemplateCommand {
  templateCode?: string;
  templateName: string;
  scopeType?: string;
  institutionId?: string | null;
  prescriptionType?: string;
  labelWidthMm?: number;
  labelHeightMm?: number;
  contentTemplate: string;
  enabled?: boolean;
}

export interface SmsTemplateQueryParams {
  keyword?: string;
  templateType?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface SmsTemplateRecord {
  id: string;
  tenantId: string;
  templateCode: string;
  templateName: string;
  templateType: string;
  contentTemplate: string;
  signature: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SmsTemplatePage {
  records: SmsTemplateRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SmsTemplateCommand {
  templateCode?: string;
  templateName: string;
  templateType?: string;
  contentTemplate: string;
  signature?: string;
  enabled?: boolean;
}

export interface SmsSendCommand {
  templateId: string;
  receiverPhone: string;
  receiverName?: string;
  relatedOrderNo?: string;
  variables?: Record<string, string>;
  operator?: string;
}

export interface SmsSendResult {
  id: string;
  tenantId: string;
  templateId: string;
  templateCode: string;
  templateName: string;
  receiverPhone: string;
  receiverName: string | null;
  relatedOrderNo: string | null;
  signature: string | null;
  content: string;
  sendStatus: string;
  providerMessageId: string | null;
  failureReason: string | null;
  retryCount: number;
  operator: string | null;
  createdAt: string;
  updatedAt: string;
  sentAt: string | null;
}

export interface SmsRecordQueryParams {
  keyword?: string;
  sendStatus?: string;
  page?: number;
  pageSize?: number;
}

export interface SmsRecordPage {
  records: SmsSendResult[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminOrderAddressUpdateCommand {
  receiverName: string;
  receiverPhone: string;
  receiverProvince?: string;
  receiverCity?: string;
  receiverZone?: string;
  receiverAddress: string;
  addressType?: string;
  deliveryTime?: string;
  operator?: string;
  reason?: string;
}

export interface AdminOrderAddressUpdateResult {
  orderId: string;
  orderNo: string;
  receiverName: string;
  receiverPhone: string;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string;
  addressType: string | null;
  deliveryTime: string | null;
  updatedAt: string;
}

export interface AdminOrderCancelCommand {
  operator?: string;
  reason: string;
}

export interface AdminOrderCancelResult {
  orderId: string;
  orderNo: string;
  fromStatus: string;
  toStatus: string;
  cancelledPrescriptionCount: number;
  cancelledWorkflowTaskCount: number;
  cancelledAt: string;
}

export interface AdminOrderInitializeCommand {
  operator?: string;
  reason: string;
}

export interface AdminOrderInitializeResult {
  orderId: string;
  orderNo: string;
  fromStatus: string;
  toStatus: string;
  resetPrescriptionCount: number;
  cancelledWorkflowTaskCount: number;
  cancelledDecoctionTaskCount: number;
  deletedShipmentCount: number;
  eventId: string;
  initializedAt: string;
}

export interface AdminPrescriptionActionCommand {
  operator?: string;
  reason?: string;
}

export interface AdminPrescriptionActionResult {
  orderId: string;
  orderNo: string;
  prescriptionId: string;
  prescriptionNo: string;
  fromPrescriptionStatus: string;
  toPrescriptionStatus: string;
  orderStatusChanged: boolean;
  fromOrderStatus: string;
  toOrderStatus: string;
  eventId: string;
  operatedAt: string;
}

export interface AdminPrescriptionUpdateCommand {
  prescriptionType: string;
  hospitalType?: string;
  doseCount?: number | null;
  decoctionCount?: number | null;
  boilTimes?: number | null;
  isWithin?: number | null;
  perPackNum?: number | null;
  perPackDose?: number | null;
  medicationMethod?: string;
  medicationInstruction?: string;
  prescriptionRemark?: string;
  operator?: string;
  reason?: string;
}

export interface AdminOrderDetail {
  orderId: string;
  tenantId: string;
  institutionId: string;
  institutionName: string;
  storageType: string | null;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  patientName: string | null;
  patientPhone: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverProvince: string | null;
  receiverCity: string | null;
  receiverZone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  deliveryTime: string | null;
  batchNo: string | null;
  orderRemark: string | null;
  validationStatus: string | null;
  validationMessage: string | null;
  validationCreatedAt: string | null;
  createdAt: string;
  updatedAt: string;
  prescriptions: AdminOrderDetailPrescription[];
}

export interface AdminOrderDetailPrescription {
  prescriptionId: string;
  prescriptionNo: string;
  externalPrescriptionNo: string;
  prescriptionType: string | null;
  prescriptionStatus: string;
  hospitalType: string | null;
  doseCount: number | null;
  decoctionCount: number | null;
  boilTimes: number | null;
  isWithin: number | null;
  perPackNum: number | null;
  perPackDose: number | null;
  decoctionUnitPrice: number | string | null;
  decoctionTotalPrice: number | string | null;
  totalAmount: number | string | null;
  doctorName: string | null;
  diagnosis: string | null;
  departmentName: string | null;
  wardName: string | null;
  bedNo: string | null;
  medicationMethod: string | null;
  medicationInstruction: string | null;
  prescriptionRemark: string | null;
  detailCount: number;
  createdAt: string;
  details: AdminOrderDetailDrug[];
}

export interface AdminOrderDetailDrug {
  detailId: string;
  drugCode: string | null;
  drugName: string | null;
  platformDrugCode: string | null;
  platformDrugName: string | null;
  drugSpecs: string | null;
  drugOrigin: string | null;
  dose: string | null;
  unit: string | null;
  specialUsage: string | null;
  quantity: number | string | null;
  unitPrice: number | string | null;
  settlementUnitPrice: number | string | null;
  totalPrice: number | string | null;
  settlementTotalPrice: number | string | null;
  sortNo: number;
  batchNo: string | null;
  remark: string | null;
  validationTips: string | null;
  createdAt: string;
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
  topic: string | null;
  tag: string | null;
  source: string | null;
  aggregateType: string;
  aggregateId: string;
  status: string;
  retryCount: number;
  maxRetryCount: number;
  nextRetryAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface MessageConsumeRecord {
  id: string;
  consumerGroup: string;
  messageId: string;
  eventId: string;
  topic: string | null;
  tag: string | null;
  aggregateId: string | null;
  status: string;
  retryCount: number;
  lastError: string | null;
  traceEndpoint: string | null;
  consumeStartedAt: string | null;
  consumeFinishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeadLetterRecord {
  id: string;
  eventId: string;
  topic: string | null;
  tag: string | null;
  consumerGroup: string | null;
  aggregateId: string | null;
  errorMessage: string | null;
  retryCount: number;
  status: string;
  operator: string | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeadLetterOperationResult {
  id: string;
  eventId: string;
  status: string;
  outboxResetCount: number;
  message: string;
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

export interface ProblemRegistrationQueryParams {
  status?: string;
  orderNo?: string;
  keyword?: string;
  limit?: number;
}

export interface ProblemRegistrationRecord {
  id: string;
  tenantId: string;
  orderId: string;
  institutionId: string | null;
  orderNo: string;
  externalOrderNo: string | null;
  institutionName: string | null;
  problemType: string;
  problemReason: string;
  handlingPlan: string;
  amount: number;
  status: string;
  operator: string;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
  processedAt: string | null;
  closedAt: string | null;
}

export interface ProblemRegistrationActionRecord {
  id: string;
  registrationId: string;
  action: string;
  fromStatus: string | null;
  toStatus: string | null;
  operator: string;
  remark: string | null;
  createdAt: string;
}

export interface ProblemRegistrationCommand {
  orderNo?: string;
  problemType?: string;
  problemReason: string;
  handlingPlan: string;
  amount?: number;
  operator?: string;
  remark?: string;
}

export interface ProblemRegistrationHandleCommand {
  status: string;
  handlingPlan?: string;
  amount?: number;
  operator?: string;
  remark?: string;
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

export interface OrderIdentityRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  orderNo: string;
  externalOrderNo: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderStatusLogRecord {
  id: string;
  tenantId: string;
  orderId: string;
  fromStatus: string | null;
  toStatus: string;
  operatorType: string;
  operatorId: string | null;
  source: string;
  reason: string | null;
  createdAt: string;
}

export interface OpsWorkflowTaskRecord {
  id: string;
  tenantId: string;
  orderId: string;
  taskType: string;
  taskStatus: string;
  sourceEventId: string;
  assignedTo: string | null;
  reviewComment: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface OpsCallbackRecord {
  id: string;
  tenantId: string;
  orderId: string | null;
  callbackType: string;
  businessId: string;
  requestUrl: string | null;
  responseBody: string | null;
  status: string;
  retryCount: number;
  nextRetryAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OperationLogRecord {
  id: string;
  tenantId: string;
  orderId: string | null;
  prescriptionId: string | null;
  eventId: string | null;
  operator: string | null;
  action: string;
  result: string;
  reason: string | null;
  createdAt: string;
}

export interface OrderObservabilityBundle {
  order: OrderIdentityRecord;
  statusLogs: OrderStatusLogRecord[];
  workflowTasks: OpsWorkflowTaskRecord[];
  outboxEvents: EventOutboxRecord[];
  messageConsumeLogs: MessageConsumeRecord[];
  deadLetters: DeadLetterRecord[];
  validationRecords: OrderValidationRecord[];
  callbackRecords: OpsCallbackRecord[];
  integrationRetries: IntegrationRetryIssueRecord[];
  operationLogs: OperationLogRecord[];
  recentAccessLogs: ApiAccessLogRecord[];
}

export interface DeliveryOrderRecord {
  tenantId: string;
  orderId: string;
  orderNo: string;
  externalOrderNo: string;
  orderStatus: string;
  institutionName: string | null;
  patientName: string | null;
  receiverName: string;
  receiverPhone: string;
  receiverAddress: string;
  addressType: string | null;
  deliveryTime: string | null;
  orderCreatedAt: string;
  hospitalTypes: string | null;
}

export interface ShipmentRecord {
  shipmentId: string;
  tenantId: string;
  orderId: string;
  orderNo: string;
  externalOrderNo: string;
  orderCreatedAt: string;
  institutionName: string | null;
  patientName: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  receiverAddress: string | null;
  addressType: string | null;
  deliveryTime: string | null;
  hospitalTypes: string | null;
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

export interface LogisticsInfoRecord {
  traceId: string;
  tenantId: string;
  shipmentId: string;
  orderId: string;
  orderNo: string;
  externalOrderNo: string;
  logisticsNo: string;
  logisticsCompany: string;
  operationInfo: string | null;
  traceStatus: string;
  receiverPhone: string | null;
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

export interface InstitutionPrescriptionCountRecord {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  orderCount: number;
  prescriptionCount: number;
  doseCount: number;
  totalAmount: number | string | null;
}

export interface DispensePerformanceRecord {
  dispenser: string;
  dispenseCount: number;
  orderCount: number;
  prescriptionCount: number;
  doseCount: number;
  firstDispensedAt: string | null;
  lastDispensedAt: string | null;
}

export interface DispensePerformanceDetailRecord {
  dispenser: string;
  orderNo: string;
  externalOrderNo: string;
  institutionName: string;
  patientName: string | null;
  prescriptionCount: number;
  doseCount: number;
  printStatus: string | null;
  dispenseComment: string | null;
  dispensedAt: string | null;
}

export interface RecheckPerformanceRecord {
  rechecker: string;
  recheckCount: number;
  orderCount: number;
  prescriptionCount: number;
  doseCount: number;
  firstRecheckedAt: string | null;
  lastRecheckedAt: string | null;
}

export interface RecheckPerformanceDetailRecord {
  rechecker: string;
  recheckResult: string;
  orderNo: string;
  externalOrderNo: string;
  institutionName: string;
  patientName: string | null;
  prescriptionCount: number;
  doseCount: number;
  recheckComment: string | null;
  recheckedAt: string | null;
}

export interface AuditPerformanceRecord {
  auditor: string;
  auditCount: number;
  approvedCount: number;
  rejectedCount: number;
  orderCount: number;
  prescriptionCount: number;
  doseCount: number;
  firstAuditedAt: string | null;
  lastAuditedAt: string | null;
}

export interface AuditPerformanceDetailRecord {
  auditor: string;
  auditResult: string;
  orderNo: string;
  externalOrderNo: string;
  institutionName: string;
  patientName: string | null;
  prescriptionCount: number;
  doseCount: number;
  reviewComment: string | null;
  auditedAt: string | null;
}

export interface DecoctionPerformanceRecord {
  operator: string;
  decoctionCount: number;
  orderCount: number;
  prescriptionCount: number;
  doseCount: number;
  deviceCount: number;
  firstFinishedAt: string | null;
  lastFinishedAt: string | null;
}

export interface DecoctionPerformanceDetailRecord {
  operator: string;
  orderNo: string;
  externalOrderNo: string;
  institutionName: string;
  patientName: string | null;
  taskNo: string;
  prescriptionNo: string | null;
  deviceCode: string;
  pailNo: string | null;
  actionType: string;
  actionResult: string;
  taskStatusBefore: string | null;
  taskStatusAfter: string | null;
  doseCount: number;
  source: string | null;
  actionTime: string | null;
}

export interface HerbDosageRecord {
  herbCode: string;
  herbName: string;
  drugSpecs: string | null;
  drugOrigin: string | null;
  unit: string | null;
  detailCount: number;
  prescriptionCount: number;
  orderCount: number;
  totalQuantity: number | string | null;
  totalAmount: number | string | null;
  settlementAmount: number | string | null;
}

export interface InstitutionHerbReconciliationRecord {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  herbCode: string;
  herbName: string;
  drugSpecs: string | null;
  drugOrigin: string | null;
  unit: string | null;
  detailCount: number;
  prescriptionCount: number;
  orderCount: number;
  totalQuantity: number | string | null;
  totalAmount: number | string | null;
  settlementAmount: number | string | null;
}

export interface PrescriptionHerbDetailRecord {
  institutionCode: string;
  institutionName: string;
  orderNo: string;
  externalOrderNo: string;
  prescriptionNo: string;
  externalPrescriptionNo: string;
  herbCode: string;
  herbName: string;
  drugSpecs: string | null;
  drugOrigin: string | null;
  dose: string | null;
  unit: string | null;
  specialUsage: string | null;
  quantity: number | string | null;
  unitPrice: number | string | null;
  totalPrice: number | string | null;
  settlementUnitPrice: number | string | null;
  settlementTotalPrice: number | string | null;
  batchNo: string | null;
  remark: string | null;
  prescriptionCreatedAt: string | null;
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
