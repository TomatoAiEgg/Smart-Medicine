<script setup lang="ts">
import { computed, ref } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  cancelAdminOrder,
  downloadAdminOrdersCsv,
  getAdminOrderDetail,
  getOrder,
  getOrderProgress,
  initializeAdminOrder,
  listAdminOrders,
  updateAdminOrderAddress,
  updateAdminPrescription,
} from '../../api/order';
import {
  bindPrescription,
  finishMesTask,
  listDecoctionDevices,
  startMesTask,
} from '../../api/decoction';
import { packShipment, shipShipment, signShipment } from '../../api/logistics';
import {
  approveReviewTask,
  completeDispenseTask,
  completeRecheckTask,
} from '../../api/workflow';
import type {
  AdminOrderDetail,
  AdminOrderDetailDrug,
  AdminOrderDetailPrescription,
  AdminOrderListItem,
  AdminOrderPage,
  AdminOrderAddressUpdateCommand,
  AdminOrderCancelCommand,
  AdminOrderInitializeCommand,
  OrderCreateResult,
  OrderProgressSnapshot,
  AdminOrderQueryParams,
  AdminPrescriptionUpdateCommand,
  DecoctionProgress,
  MesTaskOperationCommand,
  PackShipmentCommand,
  ShipmentActionCommand,
  ShipmentProgress,
  SimulatorOperationCommand,
  WorkflowProgress,
} from '../../api/types';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminPagination from '../../components/admin/AdminPagination.vue';
import AdminPanel from '../../components/admin/AdminPanel.vue';
import AdminTableShell from '../../components/admin/AdminTableShell.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import StatusPill from '../../components/StatusPill.vue';
import { saveBlob } from '../../domain/download';
import {
  EMPTY_VALUE,
  amountValue,
  boundedPositiveInteger,
  currentIsoDate,
  currentIsoTimestamp,
  displayValue,
  formatDate,
  joinDisplayParts,
  labelFromMap,
  maskPersonName,
  maskPhone,
  moneyValue,
  numericValue,
  splitCommaValues,
  sumNumbers,
} from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
interface DetailDrugRow {
  prescriptionNo: string;
  externalPrescriptionNo: string;
  detail: AdminOrderDetailDrug;
}
interface AddressForm {
  receiverName: string;
  receiverPhone: string;
  receiverProvince: string;
  receiverCity: string;
  receiverZone: string;
  receiverAddress: string;
  addressType: string;
  deliveryTime: string;
  operator: string;
  reason: string;
}
interface CancelForm {
  operator: string;
  reason: string;
}
interface InitializeForm {
  operator: string;
  reason: string;
}
interface PrescriptionForm {
  prescriptionId: string;
  prescriptionType: string;
  hospitalType: string;
  doseCount: FormNumberValue;
  decoctionCount: FormNumberValue;
  boilTimes: FormNumberValue;
  isWithin: FormNumberValue;
  perPackNum: FormNumberValue;
  perPackDose: FormNumberValue;
  medicationMethod: string;
  medicationInstruction: string;
  prescriptionRemark: string;
  operator: string;
  reason: string;
}
interface SignForm {
  operator: string;
  remark: string;
}
type FormNumberValue = number | '' | null;
type AdminOrderListPaymentFields = AdminOrderListItem & {
  payMethod?: string | null;
  payStatus?: string | null;
  paymentStatus?: string | null;
};
const CANCELLABLE_ORDER_STATUSES = new Set(['CREATED', 'AUDIT_PASSED', 'RECHECKED']);
const EDITABLE_PRESCRIPTION_ORDER_STATUSES = new Set(['CREATED', 'AUDIT_PASSED']);
const SIGNABLE_SHIPMENT_STATUSES = new Set(['PACKED', 'SHIPPED', 'IN_TRANSIT']);
const ADVANCE_FLOW_TASK_TYPES = new Set(['ORDER_REVIEW', 'PRESCRIPTION_DISPENSE', 'PRESCRIPTION_RECHECK']);
const ADVANCE_FLOW_ORDER_STATUSES = new Set(['RECHECKED', 'DECOCTING', 'DECOCTED', 'PACKED', 'SHIPPED', 'IN_TRANSIT']);

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const orderStatus = ref('');
const decoctionCenter = ref('');
const deliveryType = ref('');
const logisticsCompany = ref('');
const province = ref('');
const orderNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const receiverPhone = ref('');
const order = ref<OrderCreateResult | null>(null);
const orderProgress = ref<OrderProgressSnapshot | null>(null);
const orderDetail = ref<AdminOrderDetail | null>(null);
const orderPage = ref<AdminOrderPage | null>(null);
const orderLoading = ref(false);
const exportLoading = ref(false);
const detailLoading = ref(false);
const orderError = ref('');
const selectedOrderNo = ref('');
const pendingDetailOrderNo = ref('');
const page = ref(1);
const pageSize = ref(20);
const addressModalOpen = ref(false);
const addressSubmitting = ref(false);
const addressError = ref('');
const prescriptionModalOpen = ref(false);
const prescriptionSubmitting = ref(false);
const prescriptionError = ref('');
const cancelModalOpen = ref(false);
const cancelSubmitting = ref(false);
const cancelError = ref('');
const initializeModalOpen = ref(false);
const initializeSubmitting = ref(false);
const initializeError = ref('');
const signModalOpen = ref(false);
const signSubmitting = ref(false);
const signError = ref('');
const flowSubmitting = ref(false);
let orderRequestSequence = 0;
let detailRequestSequence = 0;
const addressForm = ref<AddressForm>({
  receiverName: '',
  receiverPhone: '',
  receiverProvince: '',
  receiverCity: '',
  receiverZone: '',
  receiverAddress: '',
  addressType: '',
  deliveryTime: '',
  operator: 'admin',
  reason: '',
});
const cancelForm = ref<CancelForm>({
  operator: 'admin',
  reason: '',
});
const initializeForm = ref<InitializeForm>({
  operator: 'admin',
  reason: '',
});
const prescriptionForm = ref<PrescriptionForm>({
  prescriptionId: '',
  prescriptionType: '',
  hospitalType: '',
  doseCount: null,
  decoctionCount: null,
  boilTimes: null,
  isWithin: null,
  perPackNum: null,
  perPackDose: null,
  medicationMethod: '',
  medicationInstruction: '',
  prescriptionRemark: '',
  operator: 'admin',
  reason: '',
});
const signForm = ref<SignForm>({
  operator: 'admin',
  remark: '',
});

const calculatedPrescriptionDecoctionCount = computed(() => {
  const doseCount = formNumber(prescriptionForm.value.doseCount);
  const boilTimes = formNumber(prescriptionForm.value.boilTimes);
  return doseCount !== null && boilTimes !== null
    ? doseCount * boilTimes
    : prescriptionForm.value.decoctionCount;
});
const orderRows = computed(() => orderPage.value?.records ?? []);
const detailPrescriptions = computed(() => orderDetail.value?.prescriptions ?? []);
const editableDetailPrescriptions = computed(() => (
  detailPrescriptions.value.filter((prescription) => prescription.prescriptionStatus !== 'CANCELLED')
));
const detailDrugRows = computed<DetailDrugRow[]>(() => detailPrescriptions.value.flatMap((prescription) => (
  prescription.details.map((detail) => ({
    prescriptionNo: prescription.prescriptionNo,
    externalPrescriptionNo: prescription.externalPrescriptionNo,
    detail,
  }))
)));
const detailAmountSummary = computed(() => {
  const prescriptionAmount = sumNumbers(detailPrescriptions.value.map((item) => item.totalAmount));
  const drugAmount = sumNumbers(detailDrugRows.value.map((row) => row.detail.totalPrice));
  const decoctionAmount = sumNumbers(detailPrescriptions.value.map((item) => item.decoctionTotalPrice));
  const settlementDetailAmount = sumNumbers(detailDrugRows.value.map((row) => row.detail.settlementTotalPrice));
  const logisticsFee = numericValue(orderDetail.value?.logisticsFee);
  const discountAmount = numericValue(orderDetail.value?.discountAmount);
  const baseReceivableAmount = prescriptionAmount ?? settlementDetailAmount ?? (
    drugAmount !== null || decoctionAmount !== null ? (drugAmount ?? 0) + (decoctionAmount ?? 0) : null
  );
  return {
    prescriptionAmount,
    drugAmount,
    decoctionAmount,
    logisticsFee,
    discountAmount,
    receivableAmount: baseReceivableAmount === null
      ? null
      : baseReceivableAmount + (logisticsFee ?? 0) - (discountAmount ?? 0),
  };
});
const prescriptions = computed(() => orderProgress.value?.prescriptions ?? []);
const workflowTasks = computed(() => orderProgress.value?.workflowTasks ?? []);
const dispenseRecords = computed(() => orderProgress.value?.dispenseRecords ?? []);
const decoctionTasks = computed(() => orderProgress.value?.decoctionTasks ?? []);
const shipments = computed(() => orderProgress.value?.shipments ?? []);
const callbacks = computed(() => orderProgress.value?.callbacks ?? []);
const statusLogs = computed(() => orderProgress.value?.statusLogs ?? []);
const resultCount = computed(() => orderPage.value?.total ?? 0);
const primaryOrderStatus = computed(() => orderProgress.value?.orderStatus ?? order.value?.status ?? '');
const orderCreatedAt = computed(() => formatDate(orderProgress.value?.createdAt));
const orderUpdatedAt = computed(() => formatDate(orderProgress.value?.updatedAt));
const hasPreviousPage = computed(() => page.value > 1 && !orderLoading.value);
const hasNextPage = computed(() => (
  !orderLoading.value && page.value * pageSize.value < resultCount.value
));
const canEditAddress = computed(() => !!orderDetail.value && !detailLoading.value && !addressSubmitting.value);
const canCancelOrder = computed(() => (
  !!orderDetail.value
  && !detailLoading.value
  && !cancelSubmitting.value
  && CANCELLABLE_ORDER_STATUSES.has(orderDetail.value.orderStatus)
));
const canEditPrescription = computed(() => (
  !!orderDetail.value
  && editableDetailPrescriptions.value.length > 0
  && !detailLoading.value
  && !prescriptionSubmitting.value
  && EDITABLE_PRESCRIPTION_ORDER_STATUSES.has(orderDetail.value.orderStatus)
));
const canInitializeOrder = computed(() => (
  !!orderDetail.value
  && orderDetail.value.orderStatus !== 'CREATED'
  && !detailLoading.value
  && !initializeSubmitting.value
));
const signableShipment = computed<ShipmentProgress | null>(() => (
  shipments.value.find((shipment) => SIGNABLE_SHIPMENT_STATUSES.has(shipment.logisticsStatus)) ?? null
));
const canSignOrder = computed(() => (
  !!orderDetail.value
  && !!signableShipment.value
  && !detailLoading.value
  && !signSubmitting.value
));
const hasAdvanceFlowAction = computed(() => (
  workflowTasks.value.some((task) => ADVANCE_FLOW_TASK_TYPES.has(task.taskType) && task.taskStatus === 'PENDING')
  || ADVANCE_FLOW_ORDER_STATUSES.has(primaryOrderStatus.value)
));
const canAdvanceFlow = computed(() => (
  !!orderDetail.value
  && !!orderProgress.value
  && hasAdvanceFlowAction.value
  && !orderLoading.value
  && !detailLoading.value
  && !flowSubmitting.value
));
const pageSummary = computed(() => {
  const total = resultCount.value;
  if (total === 0) return '显示第 0 至 0 项记录，共 0 项';
  const start = (page.value - 1) * pageSize.value + 1;
  const end = Math.min(start + orderRows.value.length - 1, total);
  return `显示第 ${start} 至 ${end} 项记录，共 ${total} 项`;
});

function formNumber(value: FormNumberValue) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function hospitalTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    1: '门诊',
    2: '住院',
    3: '其他',
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    OTHER: '其他',
    门诊: '门诊',
    住院: '住院',
    其他: '其他',
  };
  return labelFromMap(type, labels);
}

function prescriptionTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    DECOCTION: '代煎',
    SELF_DECOCTION: '自煎',
    HERBAL_PIECE: '饮片',
    CREAM: '膏方',
    PILL: '丸剂',
    POWDER: '散剂',
    OTHER: '其他',
    代煎: '代煎',
    自煎: '自煎',
    饮片: '饮片',
    膏方: '膏方',
    丸剂: '丸剂',
    散剂: '散剂',
    其他: '其他',
  };
  const values = splitCommaValues(type);
  return values.length > 0
    ? values.map((value) => labelFromMap(value, labels)).join(' / ')
    : EMPTY_VALUE;
}

function isWithinText(type: number | null | undefined) {
  if (type === 0) return '内服';
  if (type === 1) return '外用';
  return EMPTY_VALUE;
}

function batchText(batchNo: string | null | undefined) {
  const labels: Record<string, string> = {
    1: '早批次',
    2: '午批次',
    3: '晚批次',
    MORNING: '早批次',
    NOON: '午批次',
    EVENING: '晚批次',
    早批次: '早批次',
    午批次: '午批次',
    晚批次: '晚批次',
  };
  return labelFromMap(batchNo, labels);
}

function legacyDateTimeInput(value: string | null | undefined) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (nextValue: number) => String(nextValue).padStart(2, '0');
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function receiverSummary(row: AdminOrderListItem) {
  return joinDisplayParts([maskPersonName(row.receiverName), maskPhone(row.receiverPhone)], ' / ');
}

function maskSensitiveIdentifier(value: string | null | undefined) {
  if (!value) return EMPTY_VALUE;
  const normalized = value.trim();
  if (normalized.length <= 2) return `${normalized.slice(0, 1)}*`;
  if (normalized.length <= 4) return `${normalized.slice(0, 1)}**${normalized.slice(-1)}`;
  return `${normalized.slice(0, 2)}${'*'.repeat(Math.min(8, normalized.length - 4))}${normalized.slice(-2)}`;
}

function detailPatientSummary() {
  if (!orderDetail.value) return EMPTY_VALUE;
  return joinDisplayParts(
    [maskPersonName(orderDetail.value.patientName), maskPhone(orderDetail.value.patientPhone)],
    ' / ',
  );
}

function detailReceiverSummary() {
  if (!orderDetail.value) return EMPTY_VALUE;
  const address = [
    orderDetail.value.receiverProvince,
    orderDetail.value.receiverCity,
    orderDetail.value.receiverZone,
    orderDetail.value.receiverAddress,
  ].filter((item): item is string => !!item && item.trim().length > 0).join('');
  return joinDisplayParts(
    [maskPersonName(orderDetail.value.receiverName), maskPhone(orderDetail.value.receiverPhone), address],
    ' / ',
  );
}

function paymentSummary(row: AdminOrderListItem) {
  const payment = row as AdminOrderListPaymentFields;
  const status = payment.paymentStatus ?? payment.payStatus;
  const methodLabels: Record<string, string> = {
    CASH: '现金',
    ONLINE: '线上支付',
    WECHAT: '微信支付',
    ALIPAY: '支付宝',
    COD: '货到付款',
  };
  return joinDisplayParts(
    [status ? statusText(status) : null, payment.payMethod ? labelFromMap(payment.payMethod, methodLabels) : null],
    ' / ',
  );
}

function validationSummary() {
  if (!orderDetail.value?.validationStatus && !orderDetail.value?.validationMessage) return EMPTY_VALUE;
  const pieces = [
    statusText(orderDetail.value.validationStatus),
    orderDetail.value.validationMessage,
    formatDate(orderDetail.value.validationCreatedAt),
  ].filter((item): item is string => !!item && item !== EMPTY_VALUE && item.trim().length > 0);
  return pieces.join(' / ');
}

function deliveryTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    PATIENT: '送个人',
    PICKUP: '自提',
    '送医院': '送医院',
    '送个人': '送个人',
    '自提': '自提',
  };
  return labelFromMap(type, labels);
}

function statusText(status: string | null | undefined) {
  if (!status) return EMPTY_VALUE;
  const labels: Record<string, string> = {
    CREATED: '已创建',
    PENDING: '待处理',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    TERMINATED: '已终止',
    COMPLETED: '已完成',
    SUCCESS: '成功',
    FAILED: '失败',
    DEAD: '死信',
    NEW: '新建',
    RETRYING: '重试中',
    PACKED: '已打包',
    SHIPPED: '已发货',
    IN_TRANSIT: '运输中',
    SIGNED: '已签收',
    BOUND: '已绑定',
    DECOCTING: '煎煮中',
    DECOCTED: '已煎煮',
    AUDIT_PASSED: '审核通过',
    AUDIT_FAILED: '审核失败',
    RECHECKED: '已复核',
    PASSED: '已通过',
    SENT: '已发送',
    OK: '正常',
    PAID: '已支付',
    UNPAID: '未支付',
    REFUNDED: '已退款',
    PARTIALLY_REFUNDED: '部分退款',
  };
  return labelFromMap(status, labels);
}

function taskTypeText(type: string) {
  const labels: Record<string, string> = {
    REVIEW: '审核',
    DISPENSE: '调剂',
    RECHECK: '复核',
    ORDER_REVIEW: '订单审核',
    PRESCRIPTION_DISPENSE: '处方调剂',
    PRESCRIPTION_RECHECK: '处方复核',
  };
  return labelFromMap(type, labels, type);
}

function callbackTypeText(type: string) {
  const labels: Record<string, string> = {
    ORDER_CREATED: '订单创建',
    ORDER_APPROVED: '订单审核',
    ORDER_SHIPPED: '订单发货',
    ORDER_SIGNED: '订单签收',
    PRESCRIPTION_STATUS: '处方状态',
  };
  return labelFromMap(type, labels, type);
}

function scrollToOrderDetail() {
  document.getElementById('order-detail-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function pendingWorkflowTask(taskType: string) {
  return workflowTasks.value.find((task) => task.taskType === taskType && task.taskStatus === 'PENDING') ?? null;
}

function latestDecoctionTask() {
  const activeTask = decoctionTasks.value.find((task) => task.taskStatus === 'DECOCTING')
    ?? decoctionTasks.value.find((task) => task.taskStatus === 'BOUND');
  return activeTask ?? decoctionTasks.value[0] ?? null;
}

function latestShipment() {
  return shipments.value[0] ?? null;
}

function firstActivePrescriptionNo() {
  return prescriptions.value.find((prescription) => prescription.prescriptionStatus !== 'CANCELLED')?.prescriptionNo
    ?? detailPrescriptions.value.find((prescription) => prescription.prescriptionStatus !== 'CANCELLED')?.prescriptionNo
    ?? null;
}

function flowOperationId(action: string) {
  return `order-center-${action}-${Date.now()}`;
}

function reviewCommand(action: string) {
  return {
    reviewer: 'admin',
    reviewComment: `订单中心走流程：${action}`,
  };
}

function mesCommand(action: string): MesTaskOperationCommand {
  return {
    operationId: flowOperationId(action),
    operator: 'admin',
    timestamp: currentIsoTimestamp(),
    sign: 'order-center-flow',
  };
}

async function refreshSelectedOrder(targetOrderNo: string) {
  const refreshed = await refreshAfterSuccessfulMutation(targetOrderNo);
  if (!refreshed) reportPostMutationRefreshFailure();
  return refreshed;
}

async function refreshAfterSuccessfulMutation(targetOrderNo: string) {
  const [detailResult, listResult] = await Promise.allSettled([
    Promise.all([
      getOrder(targetOrderNo),
      getAdminOrderDetail(targetOrderNo),
      getOrderProgress(targetOrderNo),
    ]),
    queryOrder(false),
  ]);
  if (detailResult.status === 'fulfilled') {
    const [nextOrder, nextDetail, nextProgress] = detailResult.value;
    order.value = nextOrder;
    orderDetail.value = nextDetail;
    orderProgress.value = nextProgress;
    selectedOrderNo.value = targetOrderNo;
  }
  return detailResult.status === 'fulfilled'
    && listResult.status === 'fulfilled'
    && listResult.value;
}

function reportPostMutationRefreshFailure() {
  order.value = null;
  orderDetail.value = null;
  orderProgress.value = null;
  selectedOrderNo.value = '';
  orderError.value = '操作已成功，但数据刷新失败，请重新查询';
}

async function bindNextDecoctionTask(targetOrderNo: string) {
  const prescriptionNo = firstActivePrescriptionNo();
  if (!prescriptionNo) {
    throw new Error('当前订单没有可绑定煎药任务的处方');
  }
  const devices = await listDecoctionDevices();
  const idleDevice = devices.find((device) => device.deviceStatus === 'IDLE') ?? devices[0];
  if (!idleDevice) {
    throw new Error('当前没有可用煎药设备');
  }
  const command: SimulatorOperationCommand = {
    operationId: flowOperationId('bind'),
    deviceCode: idleDevice.deviceCode,
    prescriptionNo,
    pailNo: `FLOW-${Date.now()}`,
    operator: 'admin',
    timestamp: currentIsoTimestamp(),
    sign: 'order-center-flow',
  };
  await bindPrescription(command);
  if (!await refreshSelectedOrder(targetOrderNo)) return;
  emit('notice', 'success', `订单 ${targetOrderNo} 已绑定煎药任务`);
}

async function advanceDecoctionTask(targetOrderNo: string, task: DecoctionProgress) {
  if (task.taskStatus === 'BOUND') {
    await startMesTask(task.taskNo, mesCommand('start'));
    if (!await refreshSelectedOrder(targetOrderNo)) return;
    emit('notice', 'success', `订单 ${targetOrderNo} 已开始煎药`);
    return;
  }
  if (task.taskStatus === 'DECOCTING') {
    await finishMesTask(task.taskNo, mesCommand('finish'));
    if (!await refreshSelectedOrder(targetOrderNo)) return;
    emit('notice', 'success', `订单 ${targetOrderNo} 已完成煎药`);
    return;
  }
  throw new Error('当前煎药任务状态不支持走流程');
}

async function advanceShipmentFlow(targetOrderNo: string) {
  const shipment = latestShipment();
  if (!shipment) {
    const command: PackShipmentCommand = {
      orderNo: targetOrderNo,
      logisticsCompany: '订单中心手工物流',
      operator: 'admin',
    };
    await packShipment(command);
    if (!await refreshSelectedOrder(targetOrderNo)) return;
    emit('notice', 'success', `订单 ${targetOrderNo} 已打包`);
    return;
  }
  const command: ShipmentActionCommand = {
    operator: 'admin',
    remark: '订单中心走流程',
  };
  if (shipment.logisticsStatus === 'PACKED') {
    await shipShipment(shipment.shipmentId, command);
    if (!await refreshSelectedOrder(targetOrderNo)) return;
    emit('notice', 'success', `订单 ${targetOrderNo} 已发货`);
    return;
  }
  if (SIGNABLE_SHIPMENT_STATUSES.has(shipment.logisticsStatus)) {
    await signShipment(shipment.shipmentId, command);
    if (!await refreshSelectedOrder(targetOrderNo)) return;
    emit('notice', 'success', `订单 ${targetOrderNo} 已签收`);
    return;
  }
  throw new Error('当前物流状态不支持走流程');
}

async function advanceOrderFlow() {
  if (!orderDetail.value || !orderProgress.value) {
    orderError.value = '请先查看一条订单详情后再走流程';
    return;
  }
  const targetOrderNo = orderDetail.value.orderNo;
  const orderStatus = orderProgress.value.orderStatus;
  flowSubmitting.value = true;
  orderError.value = '';
  try {
    const reviewTask = pendingWorkflowTask('ORDER_REVIEW') as WorkflowProgress | null;
    if (reviewTask) {
      await approveReviewTask(reviewTask.taskId, reviewCommand('审方通过'));
      if (!await refreshSelectedOrder(targetOrderNo)) return;
      emit('notice', 'success', `订单 ${targetOrderNo} 已审方通过`);
      return;
    }
    const dispenseTask = pendingWorkflowTask('PRESCRIPTION_DISPENSE') as WorkflowProgress | null;
    if (dispenseTask) {
      await completeDispenseTask(dispenseTask.taskId, reviewCommand('完成调剂'));
      if (!await refreshSelectedOrder(targetOrderNo)) return;
      emit('notice', 'success', `订单 ${targetOrderNo} 已完成调剂`);
      return;
    }
    const recheckTask = pendingWorkflowTask('PRESCRIPTION_RECHECK') as WorkflowProgress | null;
    if (recheckTask) {
      await completeRecheckTask(recheckTask.taskId, reviewCommand('完成复核'));
      if (!await refreshSelectedOrder(targetOrderNo)) return;
      emit('notice', 'success', `订单 ${targetOrderNo} 已完成复核`);
      return;
    }
    if (orderStatus === 'RECHECKED' || orderStatus === 'DECOCTING') {
      const task = latestDecoctionTask();
      if (task) {
        await advanceDecoctionTask(targetOrderNo, task);
      } else {
        await bindNextDecoctionTask(targetOrderNo);
      }
      return;
    }
    if (['DECOCTED', 'PACKED', 'SHIPPED', 'IN_TRANSIT'].includes(orderStatus)) {
      await advanceShipmentFlow(targetOrderNo);
      return;
    }
    orderError.value = '当前订单没有可自动推进的下一步';
  } catch (error) {
    orderError.value = errorMessage(error);
  } finally {
    flowSubmitting.value = false;
  }
}

function openAddressModal() {
  if (!orderDetail.value) {
    orderError.value = '请先查看一条订单详情后再修改地址';
    return;
  }
  addressError.value = '';
  addressForm.value = {
    receiverName: orderDetail.value.receiverName ?? '',
    receiverPhone: orderDetail.value.receiverPhone ?? '',
    receiverProvince: orderDetail.value.receiverProvince ?? '',
    receiverCity: orderDetail.value.receiverCity ?? '',
    receiverZone: orderDetail.value.receiverZone ?? '',
    receiverAddress: orderDetail.value.receiverAddress ?? '',
    addressType: orderDetail.value.addressType ?? '',
    deliveryTime: legacyDateTimeInput(orderDetail.value.deliveryTime),
    operator: addressForm.value.operator || 'admin',
    reason: '',
  };
  addressModalOpen.value = true;
}

function closeAddressModal() {
  if (addressSubmitting.value) return;
  addressModalOpen.value = false;
  addressError.value = '';
}

async function submitAddressUpdate() {
  if (!orderDetail.value) {
    addressError.value = '订单详情已失效，请关闭弹窗后重新查看';
    return;
  }
  const command: AdminOrderAddressUpdateCommand = {
    receiverName: addressForm.value.receiverName.trim(),
    receiverPhone: addressForm.value.receiverPhone.trim(),
    receiverProvince: addressForm.value.receiverProvince.trim(),
    receiverCity: addressForm.value.receiverCity.trim(),
    receiverZone: addressForm.value.receiverZone.trim(),
    receiverAddress: addressForm.value.receiverAddress.trim(),
    addressType: addressForm.value.addressType,
    deliveryTime: addressForm.value.deliveryTime.trim(),
    operator: addressForm.value.operator.trim() || 'admin',
    reason: addressForm.value.reason.trim(),
  };
  if (!command.receiverName || !command.receiverPhone || !command.receiverAddress) {
    addressError.value = '收货人、收货电话和详细地址不能为空';
    return;
  }
  addressSubmitting.value = true;
  addressError.value = '';
  try {
    const targetOrderNo = orderDetail.value.orderNo;
    try {
      await updateAdminOrderAddress(targetOrderNo, command);
    } catch (error) {
      addressError.value = errorMessage(error);
      return;
    }
    addressModalOpen.value = false;
    const refreshed = await refreshAfterSuccessfulMutation(targetOrderNo);
    if (!refreshed) {
      reportPostMutationRefreshFailure();
      return;
    }
    emit('notice', 'success', `订单 ${targetOrderNo} 地址已更新`);
  } finally {
    addressSubmitting.value = false;
  }
}

function fillPrescriptionForm(prescription: AdminOrderDetailPrescription) {
  prescriptionForm.value = {
    prescriptionId: prescription.prescriptionId,
    prescriptionType: prescription.prescriptionType ?? '',
    hospitalType: prescription.hospitalType ?? '',
    doseCount: prescription.doseCount,
    decoctionCount: prescription.decoctionCount,
    boilTimes: prescription.boilTimes,
    isWithin: prescription.isWithin,
    perPackNum: prescription.perPackNum,
    perPackDose: prescription.perPackDose,
    medicationMethod: prescription.medicationMethod ?? '',
    medicationInstruction: prescription.medicationInstruction ?? '',
    prescriptionRemark: prescription.prescriptionRemark ?? '',
    operator: prescriptionForm.value.operator || 'admin',
    reason: '',
  };
}

function selectedPrescription() {
  return detailPrescriptions.value.find((item) => item.prescriptionId === prescriptionForm.value.prescriptionId) ?? null;
}

function openPrescriptionModal() {
  if (!orderDetail.value) {
    orderError.value = '请先查看一条订单详情后再修改处方';
    return;
  }
  if (!canEditPrescription.value) {
    orderError.value = '当前订单状态不允许修改处方';
    return;
  }
  const firstPrescription = editableDetailPrescriptions.value[0];
  if (!firstPrescription) {
    orderError.value = '当前订单没有可修改处方';
    return;
  }
  prescriptionError.value = '';
  fillPrescriptionForm(firstPrescription);
  prescriptionModalOpen.value = true;
}

function closePrescriptionModal() {
  if (prescriptionSubmitting.value) return;
  prescriptionModalOpen.value = false;
  prescriptionError.value = '';
}

function changePrescriptionForm() {
  prescriptionError.value = '';
  const prescription = selectedPrescription();
  if (prescription) {
    fillPrescriptionForm(prescription);
  }
}

async function submitPrescriptionUpdate() {
  if (!orderDetail.value) {
    prescriptionError.value = '订单详情已失效，请关闭弹窗后重新查看';
    return;
  }
  const targetPrescription = selectedPrescription();
  if (!targetPrescription) {
    prescriptionError.value = '请选择要修改的处方';
    return;
  }
  const doseCount = formNumber(prescriptionForm.value.doseCount);
  const boilTimes = formNumber(prescriptionForm.value.boilTimes);
  const command: AdminPrescriptionUpdateCommand = {
    prescriptionType: prescriptionForm.value.prescriptionType,
    hospitalType: prescriptionForm.value.hospitalType,
    doseCount,
    decoctionCount: formNumber(calculatedPrescriptionDecoctionCount.value),
    boilTimes,
    isWithin: formNumber(prescriptionForm.value.isWithin),
    perPackNum: formNumber(prescriptionForm.value.perPackNum),
    perPackDose: formNumber(prescriptionForm.value.perPackDose),
    medicationMethod: prescriptionForm.value.medicationMethod.trim(),
    medicationInstruction: prescriptionForm.value.medicationInstruction.trim(),
    prescriptionRemark: prescriptionForm.value.prescriptionRemark.trim(),
    operator: prescriptionForm.value.operator.trim() || 'admin',
    reason: prescriptionForm.value.reason.trim(),
  };
  if (!command.prescriptionType) {
    prescriptionError.value = '处方类型不能为空';
    return;
  }
  if (command.prescriptionType === 'DECOCTION' && (!command.boilTimes || command.boilTimes <= 0)) {
    prescriptionError.value = '代煎处方的几煎必须大于 0';
    return;
  }
  prescriptionSubmitting.value = true;
  prescriptionError.value = '';
  try {
    const targetOrderNo = orderDetail.value.orderNo;
    try {
      await updateAdminPrescription(targetOrderNo, targetPrescription.prescriptionId, command);
    } catch (error) {
      prescriptionError.value = errorMessage(error);
      return;
    }
    prescriptionModalOpen.value = false;
    const refreshed = await refreshAfterSuccessfulMutation(targetOrderNo);
    if (!refreshed) {
      reportPostMutationRefreshFailure();
      return;
    }
    emit('notice', 'success', `处方 ${targetPrescription.prescriptionNo} 已更新`);
  } finally {
    prescriptionSubmitting.value = false;
  }
}

function openCancelModal() {
  if (!orderDetail.value) {
    orderError.value = '请先查看一条订单详情后再取消订单';
    return;
  }
  cancelError.value = '';
  cancelForm.value = {
    operator: cancelForm.value.operator || 'admin',
    reason: '',
  };
  cancelModalOpen.value = true;
}

function closeCancelModal() {
  if (cancelSubmitting.value) return;
  cancelModalOpen.value = false;
  cancelError.value = '';
}

async function submitCancelOrder() {
  if (!orderDetail.value) {
    cancelError.value = '订单详情已失效，请关闭弹窗后重新查看';
    return;
  }
  const command: AdminOrderCancelCommand = {
    operator: cancelForm.value.operator.trim() || 'admin',
    reason: cancelForm.value.reason.trim(),
  };
  if (!command.reason) {
    cancelError.value = '取消原因不能为空';
    return;
  }
  cancelSubmitting.value = true;
  cancelError.value = '';
  try {
    const targetOrderNo = orderDetail.value.orderNo;
    try {
      await cancelAdminOrder(targetOrderNo, command);
    } catch (error) {
      cancelError.value = errorMessage(error);
      return;
    }
    cancelModalOpen.value = false;
    const refreshed = await refreshAfterSuccessfulMutation(targetOrderNo);
    if (!refreshed) {
      reportPostMutationRefreshFailure();
      return;
    }
    emit('notice', 'success', `订单 ${targetOrderNo} 已取消`);
  } finally {
    cancelSubmitting.value = false;
  }
}

function openInitializeModal() {
  if (!orderDetail.value) {
    orderError.value = '请先查看一条订单详情后再初始化';
    return;
  }
  initializeError.value = '';
  initializeForm.value = {
    operator: initializeForm.value.operator || 'admin',
    reason: '',
  };
  initializeModalOpen.value = true;
}

function closeInitializeModal() {
  if (initializeSubmitting.value) return;
  initializeModalOpen.value = false;
  initializeError.value = '';
}

async function submitInitializeOrder() {
  if (!orderDetail.value) {
    initializeError.value = '订单详情已失效，请关闭弹窗后重新查看';
    return;
  }
  const command: AdminOrderInitializeCommand = {
    operator: initializeForm.value.operator.trim() || 'admin',
    reason: initializeForm.value.reason.trim(),
  };
  if (!command.reason) {
    initializeError.value = '初始化原因不能为空';
    return;
  }
  initializeSubmitting.value = true;
  initializeError.value = '';
  try {
    const targetOrderNo = orderDetail.value.orderNo;
    let result: Awaited<ReturnType<typeof initializeAdminOrder>>;
    try {
      result = await initializeAdminOrder(targetOrderNo, command);
    } catch (error) {
      initializeError.value = errorMessage(error);
      return;
    }
    initializeModalOpen.value = false;
    const refreshed = await refreshAfterSuccessfulMutation(targetOrderNo);
    if (!refreshed) {
      reportPostMutationRefreshFailure();
      return;
    }
    emit(
      'notice',
      'success',
      `订单 ${targetOrderNo} 已初始化：处方 ${result.resetPrescriptionCount} 条，流程任务 ${result.cancelledWorkflowTaskCount} 条`,
    );
  } finally {
    initializeSubmitting.value = false;
  }
}

function openSignModal() {
  if (!orderDetail.value) {
    orderError.value = '请先查看一条订单详情后再签收';
    return;
  }
  if (!signableShipment.value) {
    orderError.value = '当前订单没有可签收物流单';
    return;
  }
  signError.value = '';
  signForm.value = {
    operator: signForm.value.operator || 'admin',
    remark: '',
  };
  signModalOpen.value = true;
}

function closeSignModal() {
  if (signSubmitting.value) return;
  signModalOpen.value = false;
  signError.value = '';
}

async function submitSignOrder() {
  if (!orderDetail.value || !signableShipment.value) {
    signError.value = '订单详情或可签收物流单已失效，请关闭弹窗后重新查看';
    return;
  }
  const targetOrderNo = orderDetail.value.orderNo;
  const targetShipment = signableShipment.value;
  const command: ShipmentActionCommand = {
    operator: signForm.value.operator.trim() || 'admin',
    remark: signForm.value.remark.trim() || '订单中心手动签收',
  };
  signSubmitting.value = true;
  signError.value = '';
  try {
    try {
      await signShipment(targetShipment.shipmentId, command);
    } catch (error) {
      signError.value = errorMessage(error);
      return;
    }
    signModalOpen.value = false;
    const refreshed = await refreshAfterSuccessfulMutation(targetOrderNo);
    if (!refreshed) {
      reportPostMutationRefreshFailure();
      return;
    }
    emit('notice', 'success', `订单 ${targetOrderNo} 已签收`);
  } finally {
    signSubmitting.value = false;
  }
}

async function queryOrder(showSuccess = true) {
  const requestSequence = ++orderRequestSequence;
  detailRequestSequence += 1;
  pendingDetailOrderNo.value = '';
  detailLoading.value = false;
  orderLoading.value = true;
  orderError.value = '';
  try {
    pageSize.value = boundedPositiveInteger(pageSize.value, 20, 100);
    const nextPage = await listAdminOrders(currentOrderQueryParams({ includePaging: true }));
    if (requestSequence !== orderRequestSequence) return true;
    orderPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    order.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    selectedOrderNo.value = '';
    if (showSuccess) emit('notice', 'success', `已查询到 ${nextPage.total} 条处方订单记录`);
    return true;
  } catch (error) {
    if (requestSequence !== orderRequestSequence) return true;
    orderPage.value = null;
    order.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    orderError.value = errorMessage(error);
    return false;
  } finally {
    if (requestSequence === orderRequestSequence) {
      orderLoading.value = false;
    }
  }
}

function currentOrderQueryParams(options: { includePaging: boolean }): AdminOrderQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    prescriptionType: prescriptionType.value,
    hospitalType: hospitalType.value,
    orderStatus: orderStatus.value,
    decoctionCenter: decoctionCenter.value,
    deliveryType: deliveryType.value,
    logisticsCompany: logisticsCompany.value,
    province: province.value,
    keyword: orderNo.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    patientName: patientName.value,
    receiverPhone: receiverPhone.value,
    ...(options.includePaging ? { page: page.value, pageSize: pageSize.value } : {}),
  };
}

async function exportOrders() {
  exportLoading.value = true;
  orderError.value = '';
  try {
    const blob = await downloadAdminOrdersCsv(currentOrderQueryParams({ includePaging: false }));
    saveBlob(`订单信息汇总-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '订单信息汇总已导出');
  } catch (error) {
    orderError.value = errorMessage(error);
  } finally {
    exportLoading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  orderPage.value = null;
  await queryOrder();
}

async function resetOrderFilters() {
  startTime.value = '';
  endTime.value = '';
  institution.value = '';
  prescriptionType.value = '';
  hospitalType.value = '';
  orderStatus.value = '';
  decoctionCenter.value = '';
  deliveryType.value = '';
  logisticsCompany.value = '';
  province.value = '';
  orderNo.value = '';
  hospitalPrescriptionNo.value = '';
  patientName.value = '';
  receiverPhone.value = '';
  page.value = 1;
  orderPage.value = null;
  await queryOrder();
}

async function loadOrderDetail(row: AdminOrderListItem) {
  const requestSequence = ++detailRequestSequence;
  pendingDetailOrderNo.value = row.orderNo;
  detailLoading.value = true;
  orderError.value = '';
  try {
    const [nextOrder, nextDetail, nextProgress] = await Promise.all([
      getOrder(row.orderNo),
      getAdminOrderDetail(row.orderNo),
      getOrderProgress(row.orderNo),
    ]);
    if (requestSequence !== detailRequestSequence) return;
    order.value = nextOrder;
    orderDetail.value = nextDetail;
    orderProgress.value = nextProgress;
    selectedOrderNo.value = row.orderNo;
    emit('notice', 'success', `已加载订单 ${nextOrder.orderNo} 详情`);
    scrollToOrderDetail();
  } catch (error) {
    if (requestSequence !== detailRequestSequence) return;
    order.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    selectedOrderNo.value = '';
    orderError.value = errorMessage(error);
  } finally {
    if (requestSequence === detailRequestSequence) {
      pendingDetailOrderNo.value = '';
      detailLoading.value = false;
    }
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await queryOrder();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await queryOrder();
}

defineExpose({
  refreshOrders: queryOrder,
});
</script>

<template>
  <section class="admin-list-page order-center-page">
    <AdminToolbar class="order-filter-toolbar">
      <div class="order-filter-grid">
        <label class="order-filter-field order-filter-field--wide">
          <span>时间范围</span>
          <div class="order-time-range">
            <t-input v-model="startTime" size="small" clearable placeholder="开始时间" @enter="searchFirstPage" />
            <span>至</span>
            <t-input v-model="endTime" size="small" clearable placeholder="结束时间" @enter="searchFirstPage" />
          </div>
        </label>
        <label class="order-filter-field">
          <span>机构</span>
          <t-select v-model="institution" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="良益堂煎药中心" label="良益堂煎药中心" />
            <t-option value="广州良益堂（康正堂店）" label="广州良益堂（康正堂店）" />
            <t-option value="代煎代配药房" label="代煎代配药房" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>处方类型</span>
          <t-select v-model="prescriptionType" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="代煎" label="代煎" />
            <t-option value="自煎" label="自煎" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>门诊 / 住院</span>
          <t-select v-model="hospitalType" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="1" label="门诊" />
            <t-option value="2" label="住院" />
            <t-option value="3" label="其他" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>订单状态</span>
          <t-select v-model="orderStatus" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="CREATED" label="已创建" />
            <t-option value="PENDING" label="待处理" />
            <t-option value="APPROVED" label="已通过" />
            <t-option value="REJECTED" label="已驳回" />
            <t-option value="PACKED" label="已打包" />
            <t-option value="SHIPPED" label="已发货" />
            <t-option value="SIGNED" label="已签收" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>煎煮中心</span>
          <t-select v-model="decoctionCenter" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="良益堂煎药中心" label="良益堂煎药中心" />
            <t-option value="良益堂煎煮中心" label="良益堂煎煮中心" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>配送方式</span>
          <t-select v-model="deliveryType" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="HOSPITAL" label="送医院" />
            <t-option value="PATIENT" label="送个人" />
            <t-option value="PICKUP" label="自提" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>物流公司</span>
          <t-select v-model="logisticsCompany" size="small" clearable>
            <t-option value="" label="全部" />
            <t-option value="顺丰" label="顺丰" />
            <t-option value="EMS" label="EMS" />
            <t-option value="自配送" label="自配送" />
          </t-select>
        </label>
        <label class="order-filter-field">
          <span>省份</span>
          <t-input v-model="province" size="small" clearable @enter="searchFirstPage" />
        </label>
        <label class="order-filter-field">
          <span>平台订单号 / 处方号</span>
          <t-input v-model="orderNo" size="small" clearable @enter="searchFirstPage" />
        </label>
        <label class="order-filter-field">
          <span>机构处方号</span>
          <t-input v-model="hospitalPrescriptionNo" size="small" clearable @enter="searchFirstPage" />
        </label>
        <label class="order-filter-field">
          <span>患者姓名</span>
          <t-input v-model="patientName" size="small" clearable @enter="searchFirstPage" />
        </label>
        <label class="order-filter-field">
          <span>收货电话</span>
          <t-input v-model="receiverPhone" size="small" clearable @enter="searchFirstPage" />
        </label>
        <label class="order-filter-field order-filter-field--page-size">
          <span>每页条数</span>
          <t-select v-model="pageSize" size="small">
            <t-option :value="10" label="10" />
            <t-option :value="20" label="20" />
            <t-option :value="50" label="50" />
            <t-option :value="100" label="100" />
          </t-select>
        </label>
      </div>

      <template #actions>
        <t-button theme="primary" size="small" :loading="orderLoading" @click="searchFirstPage">
          <template #icon><t-icon name="search" /></template>
          查询
        </t-button>
        <t-button theme="default" variant="outline" size="small" :disabled="orderLoading" @click="resetOrderFilters">
          <template #icon><t-icon name="refresh" /></template>
          重置
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :loading="exportLoading"
          :disabled="orderLoading"
          title="按当前筛选最多导出 5000 行"
          @click="exportOrders"
        >
          <template #icon><t-icon name="download" /></template>
          导出
        </t-button>
      </template>
    </AdminToolbar>

    <AdminPanel class="order-list-panel">
      <template #title>处方订单</template>
      <template #description>{{ orderPage ? pageSummary : '按筛选条件查询处方订单。' }}</template>

      <p v-if="orderError && orderPage" class="admin-error-line" role="alert">{{ orderError }}</p>
      <AdminPageState
        v-if="orderLoading && !orderPage"
        state="loading"
        message="正在查询处方订单。"
      />
      <AdminPageState
        v-else-if="orderError && !orderPage"
        state="error"
        :message="orderError"
      >
        <template #action>
          <t-button theme="primary" variant="outline" size="small" @click="searchFirstPage">重新查询</t-button>
        </template>
      </AdminPageState>
      <AdminPageState
        v-else-if="!orderPage"
        state="empty"
        title="等待查询"
        message="设置筛选条件后查询处方订单。"
      />
      <AdminPageState
        v-else-if="orderRows.length === 0"
        state="empty"
        message="没有符合当前筛选条件的处方订单。"
      />
      <template v-else>
        <AdminTableShell class="order-table-shell">
          <table class="order-result-table">
            <thead>
              <tr>
                <th class="column-prescription">平台处方号</th>
                <th class="column-time">平台下单时间</th>
                <th class="column-center">煎煮中心</th>
                <th class="column-institution">机构</th>
                <th class="column-type">门诊 / 住院</th>
                <th class="column-prescription">机构处方号</th>
                <th class="column-person">患者</th>
                <th class="column-type">处方类型</th>
                <th class="column-number" data-align="number">剂数</th>
                <th class="column-number" data-align="number">味数</th>
                <th class="column-amount" data-align="number">金额</th>
                <th class="column-type">配送方式</th>
                <th class="column-receiver">收件信息</th>
                <th class="column-time">配送时间</th>
                <th class="column-status" data-align="status">状态</th>
                <th class="column-payment">支付</th>
                <th class="column-type">批次</th>
                <th class="column-remark">订单备注</th>
                <th class="column-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in orderRows" :key="`${row.orderId}-${row.prescriptionNos}`">
                <td class="primary-code">{{ displayValue(row.prescriptionNos) }}</td>
                <td>{{ formatDate(row.createdAt) }}</td>
                <td>{{ displayValue(row.storageType) }}</td>
                <td>{{ displayValue(row.institutionName) }}</td>
                <td>{{ hospitalTypeText(row.hospitalTypes) }}</td>
                <td>{{ displayValue(row.externalPrescriptionNos) }}</td>
                <td>{{ maskPersonName(row.patientName) }}</td>
                <td>{{ prescriptionTypeText(row.prescriptionTypes) }}</td>
                <td data-align="number">{{ displayValue(row.doseCount) }}</td>
                <td data-align="number">{{ displayValue(row.detailCount) }}</td>
                <td data-align="number">{{ moneyValue(row.totalAmount) }}</td>
                <td>{{ deliveryTypeText(row.addressType) }}</td>
                <td>
                  <div class="receiver-cell">
                    <strong>{{ receiverSummary(row) }}</strong>
                    <small>{{ joinDisplayParts([row.receiverProvince, row.receiverCity, row.receiverZone]) }}</small>
                  </div>
                </td>
                <td>{{ formatDate(row.deliveryTime) }}</td>
                <td data-align="status">
                  <StatusPill :value="statusText(row.orderStatus)" :tone="statusTone(row.orderStatus)" />
                </td>
                <td>{{ paymentSummary(row) }}</td>
                <td>{{ batchText(row.batchNo) }}</td>
                <td class="order-remark-cell">{{ displayValue(row.orderRemark) }}</td>
                <td class="order-row-actions">
                  <t-button
                    theme="primary"
                    variant="text"
                    size="small"
                    :loading="detailLoading && pendingDetailOrderNo === row.orderNo"
                    :disabled="detailLoading && pendingDetailOrderNo === row.orderNo"
                    @click="loadOrderDetail(row)"
                  >
                    查看
                  </t-button>
                </td>
              </tr>
            </tbody>
          </table>
        </AdminTableShell>

        <AdminPagination
          :page="page"
          :page-size="pageSize"
          :total="resultCount"
          :loading="orderLoading"
          @previous="goPreviousPage"
          @next="goNextPage"
        />
      </template>
    </AdminPanel>

    <section class="order-operation-section" aria-labelledby="order-operation-title">
      <header class="order-section-header">
        <div>
          <h2 id="order-operation-title">订单操作</h2>
          <p>{{ orderDetail ? `当前订单：${orderDetail.orderNo}` : '查看订单详情后可执行操作。' }}</p>
        </div>
      </header>
      <div class="order-action-bar">
        <t-button theme="default" variant="outline" size="small" :disabled="!canEditAddress" title="先查看订单详情后修改地址" @click="openAddressModal">地址修改</t-button>
        <t-button theme="default" variant="outline" size="small" :disabled="!canEditPrescription" title="先查看可修改处方的订单详情" @click="openPrescriptionModal">处方修改</t-button>
        <t-button theme="default" variant="outline" size="small" :loading="initializeSubmitting" :disabled="!canInitializeOrder" title="将订单回退到初始审核状态" @click="openInitializeModal">初始化</t-button>
        <t-button theme="danger" variant="outline" size="small" :disabled="!canCancelOrder" title="先查看可取消订单详情" @click="openCancelModal">取消</t-button>
        <t-button theme="primary" variant="outline" size="small" :loading="flowSubmitting" :disabled="!canAdvanceFlow" title="按当前订单进度推进下一步" @click="advanceOrderFlow">走流程</t-button>
        <t-button theme="default" variant="outline" size="small" :disabled="!canSignOrder" title="先查看有可签收物流单的订单详情" @click="openSignModal">签收</t-button>
      </div>
    </section>

    <t-dialog
      :visible="addressModalOpen"
      header="修改订单地址"
      :footer="false"
      :close-btn="!addressSubmitting"
      :close-on-esc-keydown="!addressSubmitting"
      :close-on-overlay-click="false"
      width="860px"
      @close="closeAddressModal"
    >
        <p v-if="addressError" class="order-modal-error" role="alert">{{ addressError }}</p>
        <div class="order-form-grid">
          <label>
            <span>平台订单号</span>
            <input class="order-form-control" :value="orderDetail?.orderNo" disabled />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="addressForm.operator" class="order-form-control" />
          </label>
          <label>
            <span>收货人</span>
            <input v-model="addressForm.receiverName" class="order-form-control" />
          </label>
          <label>
            <span>收货电话</span>
            <input v-model="addressForm.receiverPhone" class="order-form-control" />
          </label>
          <label>
            <span>送货方式</span>
            <select v-model="addressForm.addressType" class="order-form-control">
              <option value="">默认</option>
              <option value="HOSPITAL">送医院</option>
              <option value="PATIENT">送个人</option>
              <option value="PICKUP">自提</option>
            </select>
          </label>
          <label>
            <span>送货时间</span>
            <input v-model="addressForm.deliveryTime" class="order-form-control" placeholder="YYYY-MM-DD HH:mm:ss" />
          </label>
          <label>
            <span>省份</span>
            <input v-model="addressForm.receiverProvince" class="order-form-control" />
          </label>
          <label>
            <span>城市</span>
            <input v-model="addressForm.receiverCity" class="order-form-control" />
          </label>
          <label>
            <span>区县</span>
            <input v-model="addressForm.receiverZone" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>详细地址</span>
            <input v-model="addressForm.receiverAddress" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>修改原因</span>
            <input v-model="addressForm.reason" class="order-form-control" placeholder="可选" />
          </label>
        </div>
        <div class="order-dialog-actions">
          <t-button theme="default" variant="outline" :disabled="addressSubmitting" @click="closeAddressModal">取消</t-button>
          <t-button theme="primary" :loading="addressSubmitting" @click="submitAddressUpdate">保存地址</t-button>
        </div>
    </t-dialog>

    <t-dialog
      :visible="prescriptionModalOpen"
      header="修改处方"
      :footer="false"
      :close-btn="!prescriptionSubmitting"
      :close-on-esc-keydown="!prescriptionSubmitting"
      :close-on-overlay-click="false"
      width="860px"
      @close="closePrescriptionModal"
    >
        <div class="order-dialog-notice">
          <strong>{{ displayValue(orderDetail?.orderNo) }}</strong>
          <span>当前仅支持订单创建或审核通过状态下修改处方结构化字段。</span>
        </div>
        <p v-if="prescriptionError" class="order-modal-error" role="alert">{{ prescriptionError }}</p>
        <div class="order-form-grid">
          <label class="order-form-wide">
            <span>处方</span>
            <select v-model="prescriptionForm.prescriptionId" class="order-form-control" @change="changePrescriptionForm">
              <option
                v-for="item in editableDetailPrescriptions"
                :key="item.prescriptionId"
                :value="item.prescriptionId"
              >
                {{ item.prescriptionNo }} / {{ item.externalPrescriptionNo }} / {{ statusText(item.prescriptionStatus) }}
              </option>
            </select>
          </label>
          <label>
            <span>处方类型</span>
            <select v-model="prescriptionForm.prescriptionType" class="order-form-control">
              <option value="">请选择</option>
              <option value="DECOCTION">代煎</option>
              <option value="SELF_DECOCTION">自煎</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            <span>门诊住院</span>
            <select v-model="prescriptionForm.hospitalType" class="order-form-control">
              <option value="">请选择</option>
              <option value="OUTPATIENT">门诊</option>
              <option value="INPATIENT">住院</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            <span>剂数</span>
            <input v-model.number="prescriptionForm.doseCount" class="order-form-control" type="number" min="0" />
          </label>
          <label>
            <span>几煎</span>
            <input v-model.number="prescriptionForm.boilTimes" class="order-form-control" type="number" min="0" />
          </label>
          <label>
            <span>煎煮剂数</span>
            <input class="order-form-control" type="number" min="0" :value="calculatedPrescriptionDecoctionCount ?? ''" disabled />
          </label>
          <label>
            <span>服用方式</span>
            <select v-model.number="prescriptionForm.isWithin" class="order-form-control">
              <option :value="null">请选择</option>
              <option :value="0">内服</option>
              <option :value="1">外用</option>
            </select>
          </label>
          <label>
            <span>每剂包数</span>
            <input v-model.number="prescriptionForm.perPackNum" class="order-form-control" type="number" min="0" />
          </label>
          <label>
            <span>每剂剂量</span>
            <input v-model.number="prescriptionForm.perPackDose" class="order-form-control" type="number" min="0" />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="prescriptionForm.operator" class="order-form-control" />
          </label>
          <label>
            <span>修改原因</span>
            <input v-model="prescriptionForm.reason" class="order-form-control" placeholder="可选" />
          </label>
          <label class="order-form-wide">
            <span>用药方法</span>
            <input v-model="prescriptionForm.medicationMethod" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>用药指导</span>
            <input v-model="prescriptionForm.medicationInstruction" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>处方备注</span>
            <input v-model="prescriptionForm.prescriptionRemark" class="order-form-control" />
          </label>
        </div>
        <div class="order-dialog-actions">
          <t-button theme="default" variant="outline" :disabled="prescriptionSubmitting" @click="closePrescriptionModal">取消</t-button>
          <t-button theme="primary" :loading="prescriptionSubmitting" @click="submitPrescriptionUpdate">保存处方</t-button>
        </div>
    </t-dialog>

    <t-dialog
      :visible="cancelModalOpen"
      header="取消订单"
      theme="danger"
      :footer="false"
      :close-btn="!cancelSubmitting"
      :close-on-esc-keydown="!cancelSubmitting"
      :close-on-overlay-click="false"
      width="600px"
      @close="closeCancelModal"
    >
        <div class="order-dialog-notice order-dialog-notice--danger">
          <strong>{{ displayValue(orderDetail?.orderNo) }}</strong>
          <span>取消后订单和处方会进入已取消状态，未完成工作流任务会同步关闭。</span>
        </div>
        <p v-if="cancelError" class="order-modal-error" role="alert">{{ cancelError }}</p>
        <div class="order-form-grid order-form-grid--compact">
          <label>
            <span>当前状态</span>
            <input class="order-form-control" :value="statusText(orderDetail?.orderStatus)" disabled />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="cancelForm.operator" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>取消原因</span>
            <input v-model="cancelForm.reason" class="order-form-control" placeholder="必填" />
          </label>
        </div>
        <div class="order-dialog-actions">
          <t-button theme="default" variant="outline" :disabled="cancelSubmitting" @click="closeCancelModal">返回</t-button>
          <t-button theme="danger" :loading="cancelSubmitting" @click="submitCancelOrder">确认取消</t-button>
        </div>
    </t-dialog>

    <t-dialog
      :visible="initializeModalOpen"
      header="订单初始化"
      theme="warning"
      :footer="false"
      :close-btn="!initializeSubmitting"
      :close-on-esc-keydown="!initializeSubmitting"
      :close-on-overlay-click="false"
      width="640px"
      @close="closeInitializeModal"
    >
        <div class="order-dialog-notice order-dialog-notice--warning">
          <strong>{{ displayValue(orderDetail?.orderNo) }}</strong>
          <span>初始化会把订单回退到初始审核状态，重置处方状态，取消未完成流程和活跃煎药任务，并清理物流运行记录。</span>
        </div>
        <p v-if="initializeError" class="order-modal-error" role="alert">{{ initializeError }}</p>
        <div class="order-form-grid order-form-grid--compact">
          <label>
            <span>当前状态</span>
            <input class="order-form-control" :value="statusText(orderDetail?.orderStatus)" disabled />
          </label>
          <label>
            <span>目标状态</span>
            <input class="order-form-control" value="已创建 / 待审核" disabled />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="initializeForm.operator" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>初始化原因</span>
            <input v-model="initializeForm.reason" class="order-form-control" placeholder="必填" />
          </label>
        </div>
        <div class="order-dialog-actions">
          <t-button theme="default" variant="outline" :disabled="initializeSubmitting" @click="closeInitializeModal">返回</t-button>
          <t-button theme="warning" :loading="initializeSubmitting" @click="submitInitializeOrder">确认初始化</t-button>
        </div>
    </t-dialog>

    <t-dialog
      :visible="signModalOpen"
      header="订单签收"
      :footer="false"
      :close-btn="!signSubmitting"
      :close-on-esc-keydown="!signSubmitting"
      :close-on-overlay-click="false"
      width="640px"
      @close="closeSignModal"
    >
        <div class="order-dialog-notice">
          <strong>{{ displayValue(orderDetail?.orderNo) }}</strong>
          <span>签收会通过物流服务推进订单状态，并生成物流轨迹和签收回调。</span>
        </div>
        <p v-if="signError" class="order-modal-error" role="alert">{{ signError }}</p>
        <div class="order-form-grid order-form-grid--compact">
          <label>
            <span>物流单号</span>
            <input class="order-form-control" :value="displayValue(signableShipment?.logisticsNo)" disabled />
          </label>
          <label>
            <span>物流公司</span>
            <input class="order-form-control" :value="displayValue(signableShipment?.logisticsCompany)" disabled />
          </label>
          <label>
            <span>物流状态</span>
            <input class="order-form-control" :value="statusText(signableShipment?.logisticsStatus)" disabled />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="signForm.operator" class="order-form-control" />
          </label>
          <label class="order-form-wide">
            <span>签收备注</span>
            <input v-model="signForm.remark" class="order-form-control" placeholder="可选" />
          </label>
        </div>
        <div class="order-dialog-actions">
          <t-button theme="default" variant="outline" :disabled="signSubmitting" @click="closeSignModal">返回</t-button>
          <t-button theme="primary" :loading="signSubmitting" @click="submitSignOrder">确认签收</t-button>
        </div>
    </t-dialog>

    <section id="order-detail-panel" class="order-detail-workbench">
      <template v-if="order">
        <section class="order-detail-section">
          <header class="order-section-header">
            <div>
              <h2>订单信息</h2>
              <p>最近校验：{{ validationSummary() }}</p>
            </div>
          </header>
          <div class="order-detail-grid">
            <div>
              <span>平台订单号</span>
              <strong>{{ displayValue(order.orderNo) }}</strong>
            </div>
            <div>
              <span>订单 ID</span>
              <strong>{{ displayValue(order.orderId) }}</strong>
            </div>
            <div>
              <span>外部订单号</span>
              <strong>{{ displayValue(orderDetail?.externalOrderNo || order.externalOrderNo) }}</strong>
            </div>
            <div>
              <span>订单状态</span>
              <StatusPill :value="statusText(primaryOrderStatus)" :tone="statusTone(primaryOrderStatus)" />
            </div>
            <div>
              <span>是否重复推单</span>
              <strong>{{ displayValue(order.duplicated) }}</strong>
            </div>
            <div>
              <span>创建时间</span>
              <strong>{{ formatDate(orderDetail?.createdAt || orderProgress?.createdAt) }}</strong>
            </div>
            <div>
              <span>最近更新</span>
              <strong>{{ formatDate(orderDetail?.updatedAt || orderProgress?.updatedAt) }}</strong>
            </div>
            <div>
              <span>机构名称</span>
              <strong>{{ displayValue(orderDetail?.institutionName) }}</strong>
            </div>
            <div>
              <span>原机构编号</span>
              <strong>{{ displayValue(orderDetail?.legacyCompanyNum) }}</strong>
            </div>
            <div>
              <span>机构订单时间</span>
              <strong>{{ formatDate(orderDetail?.orderTime) }}</strong>
            </div>
            <div>
              <span>创建 IP</span>
              <strong>{{ displayValue(orderDetail?.createIp) }}</strong>
            </div>
            <div>
              <span>班次</span>
              <strong>{{ displayValue(orderDetail?.classes) }}</strong>
            </div>
            <div>
              <span>患者信息</span>
              <strong>{{ detailPatientSummary() }}</strong>
            </div>
            <div>
              <span>批次</span>
              <strong>{{ batchText(orderDetail?.batchNo) }}</strong>
            </div>
            <div>
              <span>订单备注</span>
              <strong>{{ displayValue(orderDetail?.orderRemark) }}</strong>
            </div>
          </div>
        </section>

        <section class="order-detail-section">
          <header class="order-section-header">
            <div>
              <h2>配送信息</h2>
              <p>{{ shipments.length > 0 ? `共 ${shipments.length} 个物流单` : '暂无物流单' }}</p>
            </div>
          </header>
          <div class="order-detail-grid">
            <div class="order-detail-item--wide">
              <span>收件信息</span>
              <strong>{{ detailReceiverSummary() }}</strong>
            </div>
            <div>
              <span>配送方式</span>
              <strong>{{ deliveryTypeText(orderDetail?.addressType) }}</strong>
            </div>
            <div>
              <span>煎煮中心</span>
              <strong>{{ displayValue(orderDetail?.storageType) }}</strong>
            </div>
            <div>
              <span>配送时间</span>
              <strong>{{ formatDate(orderDetail?.deliveryTime) }}</strong>
            </div>
            <div>
              <span>包裹数量</span>
              <strong>{{ displayValue(orderDetail?.orderPkgNum) }}</strong>
            </div>
            <div>
              <span>包裹重量</span>
              <strong>{{ amountValue(orderDetail?.orderPkgWeight) }}</strong>
            </div>
            <div>
              <span>物流代收金额</span>
              <strong>{{ moneyValue(orderDetail?.logisticsReceivablesMoney) }}</strong>
            </div>
            <div>
              <span>物流支付方式</span>
              <strong>{{ displayValue(orderDetail?.logisticsPayMethod) }}</strong>
            </div>
            <div>
              <span>物流类型 / 模式</span>
              <strong>{{ joinDisplayParts([orderDetail?.logisticsType, orderDetail?.logisticsMode], ' / ') }}</strong>
            </div>
            <div>
              <span>第三方订单号</span>
              <strong>{{ displayValue(orderDetail?.spOrderId) }}</strong>
            </div>
            <div>
              <span>原物流标识</span>
              <strong>{{ displayValue(orderDetail?.logisId) }}</strong>
            </div>
            <div>
              <span>区域级别</span>
              <strong>{{ displayValue(orderDetail?.areaLevel) }}</strong>
            </div>
            <div>
              <span>线路编码</span>
              <strong>{{ displayValue(orderDetail?.routeCode) }}</strong>
            </div>
            <div>
              <span>基础产品编号</span>
              <strong>{{ displayValue(orderDetail?.baseProductNo) }}</strong>
            </div>
            <div>
              <span>打包时间</span>
              <strong>{{ formatDate(orderDetail?.packageTime) }}</strong>
            </div>
            <div>
              <span>出库时间</span>
              <strong>{{ formatDate(orderDetail?.outboundTime) }}</strong>
            </div>
            <div>
              <span>签收时间</span>
              <strong>{{ formatDate(orderDetail?.signTime) }}</strong>
            </div>
          </div>
        </section>

        <section class="order-detail-section">
          <header class="order-section-header">
            <h2>处方信息</h2>
          </header>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>处方状态</th>
                  <th>处方类型</th>
                  <th>门诊住院</th>
                  <th>剂数</th>
                  <th>处方金额</th>
                  <th>煎煮剂数</th>
                  <th>几煎</th>
                  <th>服用方式</th>
                  <th>每剂包数</th>
                  <th>每剂剂量</th>
                  <th>医生</th>
                  <th>诊断</th>
                  <th>科室/病区/床号</th>
                  <th>用药指导</th>
                  <th>处方备注</th>
                  <th>明细数</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="detailPrescriptions.length === 0">
                  <td colspan="19" class="empty">暂无处方信息</td>
                </tr>
                <tr v-for="item in detailPrescriptions" :key="item.prescriptionId">
                  <td>{{ displayValue(item.prescriptionNo) }}</td>
                  <td>{{ displayValue(item.externalPrescriptionNo) }}</td>
                  <td>
                    <StatusPill :value="statusText(item.prescriptionStatus)" :tone="statusTone(item.prescriptionStatus)" />
                  </td>
                  <td>{{ prescriptionTypeText(item.prescriptionType) }}</td>
                  <td>{{ hospitalTypeText(item.hospitalType) }}</td>
                  <td>{{ displayValue(item.doseCount) }}</td>
                  <td>{{ moneyValue(item.totalAmount) }}</td>
                  <td>{{ displayValue(item.decoctionCount) }}</td>
                  <td>{{ displayValue(item.boilTimes) }}</td>
                  <td>{{ isWithinText(item.isWithin) }}</td>
                  <td>{{ displayValue(item.perPackNum) }}</td>
                  <td>{{ displayValue(item.perPackDose) }}</td>
                  <td>{{ displayValue(item.doctorName) }}</td>
                  <td class="text-cell">{{ displayValue(item.diagnosis) }}</td>
                  <td class="text-cell">{{ joinDisplayParts([item.departmentName, item.wardName, item.bedNo], ' / ') }}</td>
                  <td class="text-cell">{{ joinDisplayParts([item.medicationMethod, item.medicationInstruction], ' / ') }}</td>
                  <td class="text-cell">{{ displayValue(item.prescriptionRemark) }}</td>
                  <td>{{ displayValue(item.detailCount) }}</td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <h3 class="order-subsection-title">处方扩展信息</h3>
          <div class="table-wrap">
            <table class="order-detail-table prescription-extension-table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>患者年龄</th>
                  <th>月龄 / 日龄</th>
                  <th>性别</th>
                  <th>证件号</th>
                  <th>就诊卡号</th>
                  <th>患者电话</th>
                  <th>是否孕期</th>
                  <th>饮片类型</th>
                  <th>外煎类型</th>
                  <th>医生电话</th>
                  <th>医院</th>
                  <th>医院编号</th>
                  <th>处理楼层</th>
                  <th>煎药方案</th>
                  <th>煎药医嘱</th>
                  <th>标签规格</th>
                  <th>绑定号</th>
                  <th>药品金额</th>
                  <th>审核结果 / 原因</th>
                  <th>审核留痕</th>
                  <th>调剂留痕</th>
                  <th>复核留痕</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="detailPrescriptions.length === 0">
                  <td colspan="23" class="empty">暂无处方扩展信息</td>
                </tr>
                <tr v-for="item in detailPrescriptions" :key="`extension-${item.prescriptionId}`">
                  <td>{{ displayValue(item.prescriptionNo) }}</td>
                  <td>{{ displayValue(item.patientAge) }}</td>
                  <td>{{ joinDisplayParts([item.patientMonthAge, item.patientDayAge], ' / ') }}</td>
                  <td>{{ displayValue(item.patientGender) }}</td>
                  <td>{{ maskSensitiveIdentifier(item.patientCardNo) }}</td>
                  <td>{{ maskSensitiveIdentifier(item.treatCard) }}</td>
                  <td>{{ maskPhone(item.patientTel) }}</td>
                  <td>{{ displayValue(item.isPregnant) }}</td>
                  <td>{{ displayValue(item.herbType) }}</td>
                  <td>{{ displayValue(item.wjType) }}</td>
                  <td>{{ maskPhone(item.doctorTel) }}</td>
                  <td class="text-cell">{{ displayValue(item.hospitalName) }}</td>
                  <td>{{ displayValue(item.hospitalNum) }}</td>
                  <td>{{ displayValue(item.orderHandleFloor) }}</td>
                  <td>{{ displayValue(item.jyjDecoctionPlan) }}</td>
                  <td class="text-cell">{{ displayValue(item.jyjDecoctionAdvice) }}</td>
                  <td>{{ displayValue(item.labelSize) }}</td>
                  <td>{{ displayValue(item.bindNo) }}</td>
                  <td>{{ moneyValue(item.drugsMoney) }}</td>
                  <td class="text-cell">{{ joinDisplayParts([item.auditResult, item.auditReason], ' / ') }}</td>
                  <td><a v-if="item.auditFlowPicUrl" :href="item.auditFlowPicUrl" target="_blank" rel="noopener noreferrer">查看</a><span v-else>-</span></td>
                  <td><a v-if="item.dispenseFlowPicUrl" :href="item.dispenseFlowPicUrl" target="_blank" rel="noopener noreferrer">查看</a><span v-else>-</span></td>
                  <td><a v-if="item.recheckFlowPicUrl" :href="item.recheckFlowPicUrl" target="_blank" rel="noopener noreferrer">查看</a><span v-else>-</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="order-detail-section">
          <header class="order-section-header">
            <h2>药品信息</h2>
          </header>
          <div class="table-wrap">
            <table class="order-detail-table drug-detail-table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>机构药品编码</th>
                  <th>机构药品名称</th>
                  <th>平台药品编码</th>
                  <th>平台药品名称</th>
                  <th>规格</th>
                  <th>产地</th>
                  <th>剂量</th>
                  <th>单位</th>
                  <th>数量</th>
                  <th>单价</th>
                  <th>金额</th>
                  <th>结算单价</th>
                  <th>结算金额</th>
                  <th>用法</th>
                  <th>批号</th>
                  <th>备注</th>
                  <th>审方提示</th>
                  <th>调剂药品编码</th>
                  <th>调剂药品名称</th>
                  <th>原药品名称</th>
                  <th>供应商</th>
                  <th>每剂用量</th>
                  <th>每日用量</th>
                  <th>明细状态</th>
                  <th>原明细备注</th>
                  <th>吸水率</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="detailDrugRows.length === 0">
                  <td colspan="28" class="empty">暂无药品明细</td>
                </tr>
                <tr v-for="row in detailDrugRows" :key="row.detail.detailId">
                  <td>{{ displayValue(row.prescriptionNo) }}</td>
                  <td>{{ displayValue(row.externalPrescriptionNo) }}</td>
                  <td>{{ displayValue(row.detail.drugCode) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.drugName) }}</td>
                  <td>{{ displayValue(row.detail.platformDrugCode) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.platformDrugName) }}</td>
                  <td>{{ displayValue(row.detail.drugSpecs) }}</td>
                  <td>{{ displayValue(row.detail.drugOrigin) }}</td>
                  <td>{{ displayValue(row.detail.dose) }}</td>
                  <td>{{ displayValue(row.detail.unit) }}</td>
                  <td>{{ amountValue(row.detail.quantity) }}</td>
                  <td>{{ moneyValue(row.detail.unitPrice) }}</td>
                  <td>{{ moneyValue(row.detail.totalPrice) }}</td>
                  <td>{{ moneyValue(row.detail.settlementUnitPrice) }}</td>
                  <td>{{ moneyValue(row.detail.settlementTotalPrice) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.specialUsage) }}</td>
                  <td>{{ displayValue(row.detail.batchNo) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.remark) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.validationTips) }}</td>
                  <td>{{ displayValue(row.detail.dcGoodsNum) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.dcGoodsName) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.rootsGoodsName) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.supplierName) }}</td>
                  <td>{{ amountValue(row.detail.medPerDose) }}</td>
                  <td>{{ amountValue(row.detail.medPerDay) }}</td>
                  <td>{{ statusText(row.detail.detailStatus) }}</td>
                  <td class="text-cell">{{ displayValue(row.detail.note) }}</td>
                  <td>{{ amountValue(row.detail.waterAbsorbRatio) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="order-detail-section">
          <header class="order-section-header">
            <h2>金额汇总</h2>
          </header>
          <div class="order-detail-grid amount-grid">
            <div>
              <span>处方金额</span>
              <strong>{{ moneyValue(detailAmountSummary.prescriptionAmount) }}</strong>
            </div>
            <div>
              <span>药品金额</span>
              <strong>{{ moneyValue(detailAmountSummary.drugAmount) }}</strong>
            </div>
            <div>
              <span>煎煮费</span>
              <strong>{{ moneyValue(detailAmountSummary.decoctionAmount) }}</strong>
            </div>
            <div>
              <span>物流费</span>
              <strong>{{ moneyValue(detailAmountSummary.logisticsFee) }}</strong>
            </div>
            <div>
              <span>优惠金额</span>
              <strong>{{ moneyValue(detailAmountSummary.discountAmount) }}</strong>
            </div>
            <div>
              <span>应收金额</span>
              <strong>{{ moneyValue(detailAmountSummary.receivableAmount) }}</strong>
            </div>
          </div>
        </section>

        <section class="order-detail-section">
          <header class="order-section-header">
            <h2>履约进度/状态日志</h2>
          </header>

          <div class="order-detail-grid progress-summary-grid">
            <div>
              <span>处方数</span>
              <strong>{{ prescriptions.length }}</strong>
            </div>
            <div>
              <span>流程任务</span>
              <strong>{{ workflowTasks.length }}</strong>
            </div>
            <div>
              <span>调剂记录</span>
              <strong>{{ dispenseRecords.length }}</strong>
            </div>
            <div>
              <span>煎煮任务</span>
              <strong>{{ decoctionTasks.length }}</strong>
            </div>
            <div>
              <span>物流单</span>
              <strong>{{ shipments.length }}</strong>
            </div>
            <div>
              <span>回调记录</span>
              <strong>{{ callbacks.length }}</strong>
            </div>
            <div>
              <span>状态日志</span>
              <strong>{{ statusLogs.length }}</strong>
            </div>
          </div>

          <div class="order-subsection-title">流程任务</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>流程节点</th>
                  <th>状态</th>
                  <th>处理人</th>
                  <th>意见</th>
                  <th>创建时间</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="workflowTasks.length === 0">
                  <td colspan="6" class="empty">暂无流程任务</td>
                </tr>
                <tr v-for="task in workflowTasks" :key="task.taskId">
                  <td>{{ taskTypeText(task.taskType) }}</td>
                  <td><StatusPill :value="statusText(task.taskStatus)" :tone="statusTone(task.taskStatus)" /></td>
                  <td>{{ displayValue(task.operator) }}</td>
                  <td class="text-cell">{{ displayValue(task.comment) }}</td>
                  <td>{{ formatDate(task.createdAt) }}</td>
                  <td>{{ formatDate(task.completedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="order-subsection-title">调剂/煎煮/物流</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>业务编号</th>
                  <th>状态/结果</th>
                  <th>操作人/对象</th>
                  <th>补充信息</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in dispenseRecords" :key="record.recordId">
                  <td>调剂</td>
                  <td>{{ displayValue(record.taskId) }}</td>
                  <td><StatusPill :value="statusText(record.printStatus)" :tone="statusTone(record.printStatus)" /></td>
                  <td>{{ displayValue(record.dispenser) }}</td>
                  <td class="text-cell">{{ displayValue(record.dispenseComment) }}</td>
                  <td>{{ formatDate(record.dispensedAt) }}</td>
                </tr>
                <tr v-for="task in decoctionTasks" :key="task.taskId">
                  <td>煎煮</td>
                  <td>{{ displayValue(task.taskNo) }}</td>
                  <td><StatusPill :value="statusText(task.taskStatus)" :tone="statusTone(task.taskStatus)" /></td>
                  <td>{{ displayValue(task.operator) }}</td>
                  <td class="text-cell">
                    处方 {{ displayValue(task.prescriptionNo) }}；设备 {{ displayValue(task.deviceCode) }}；桶号 {{ displayValue(task.pailNo) }}
                  </td>
                  <td>{{ formatDate(task.finishedAt || task.startedAt || task.createdAt) }}</td>
                </tr>
                <tr v-for="shipment in shipments" :key="shipment.shipmentId">
                  <td>物流</td>
                  <td>{{ displayValue(shipment.logisticsNo) }}</td>
                  <td><StatusPill :value="statusText(shipment.logisticsStatus)" :tone="statusTone(shipment.logisticsStatus)" /></td>
                  <td>{{ displayValue(shipment.logisticsCompany) }}</td>
                  <td class="text-cell">
                    {{ displayValue(shipment.latestTraceStatus) }} {{ shipment.latestTraceContent || '' }}
                  </td>
                  <td>{{ formatDate(shipment.latestTraceTime) }}</td>
                </tr>
                <tr v-if="dispenseRecords.length === 0 && decoctionTasks.length === 0 && shipments.length === 0">
                  <td colspan="6" class="empty">暂无调剂、煎煮或物流记录</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="order-subsection-title">回调记录</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>回调类型</th>
                  <th>业务 ID</th>
                  <th>状态</th>
                  <th>重试次数</th>
                  <th>下次重试</th>
                  <th>更新时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="callbacks.length === 0">
                  <td colspan="6" class="empty">暂无回调记录</td>
                </tr>
                <tr v-for="callback in callbacks" :key="callback.callbackId">
                  <td>{{ callbackTypeText(callback.callbackType) }}</td>
                  <td>{{ displayValue(callback.businessId) }}</td>
                  <td><StatusPill :value="statusText(callback.callbackStatus)" :tone="statusTone(callback.callbackStatus)" /></td>
                  <td>{{ displayValue(callback.retryCount) }}</td>
                  <td>{{ formatDate(callback.nextRetryAt) }}</td>
                  <td>{{ formatDate(callback.updatedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="order-subsection-title">状态日志</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>原状态</th>
                  <th>目标状态</th>
                  <th>操作类型</th>
                  <th>来源</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="statusLogs.length === 0">
                  <td colspan="5" class="empty">暂无状态日志</td>
                </tr>
                <tr v-for="log in statusLogs" :key="log.logId">
                  <td>{{ statusText(log.fromStatus) }}</td>
                  <td><StatusPill :value="statusText(log.toStatus)" :tone="statusTone(log.toStatus)" /></td>
                  <td>{{ displayValue(log.operatorType) }}</td>
                  <td>{{ displayValue(log.source) }}</td>
                  <td>{{ formatDate(log.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <AdminPageState
        v-else
        state="empty"
        title="尚未选择订单"
        message="从处方订单列表中查看一条记录。"
      />
    </section>
  </section>
</template>

<style scoped>
.order-center-page {
  display: grid;
  gap: 14px;
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
  color: var(--admin-text);
}

.order-filter-toolbar :deep(.admin-toolbar__filters) {
  flex-basis: 100%;
}

.order-filter-grid {
  display: grid;
  flex: 1 1 100%;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 12px;
  min-width: 0;
}

.order-filter-field,
.order-form-grid label {
  display: grid;
  align-content: end;
  gap: 4px;
  min-width: 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.order-filter-field--wide {
  grid-column: span 2;
}

.order-filter-field--page-size {
  max-width: 140px;
}

.order-time-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.order-time-range > span {
  color: var(--admin-text-placeholder);
}

.order-list-panel,
.order-table-shell,
.order-operation-section,
.order-detail-workbench,
.order-detail-section {
  min-width: 0;
}

.admin-error-line {
  margin: 0 0 10px;
  padding: 8px 10px;
  border-left: 3px solid var(--admin-danger);
  color: var(--admin-danger);
  background: var(--admin-surface-subtle);
  font-size: 13px;
  line-height: 20px;
}

.order-modal-error {
  margin: 0 0 12px;
  padding: 8px 10px;
  border-left: 3px solid var(--admin-danger);
  color: var(--admin-danger);
  background: var(--admin-surface-subtle);
  font-size: 13px;
  line-height: 20px;
}

.order-result-table {
  min-width: 2480px;
  table-layout: auto;
}

.order-result-table th,
.order-result-table td {
  white-space: nowrap;
}

.order-result-table .column-prescription {
  min-width: 150px;
}

.order-result-table .column-time {
  min-width: 146px;
}

.order-result-table .column-center,
.order-result-table .column-institution {
  min-width: 160px;
}

.order-result-table .column-type,
.order-result-table .column-payment {
  min-width: 104px;
}

.order-result-table .column-person {
  min-width: 92px;
}

.order-result-table .column-number {
  min-width: 68px;
}

.order-result-table .column-amount,
.order-result-table .column-status {
  min-width: 96px;
}

.order-result-table .column-receiver {
  min-width: 260px;
}

.order-result-table .column-remark {
  min-width: 180px;
}

.order-result-table .column-actions {
  min-width: 76px;
}

.primary-code {
  color: var(--admin-primary);
  font-weight: 600;
}

.receiver-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
  white-space: normal;
}

.receiver-cell strong,
.receiver-cell small {
  display: block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.receiver-cell strong {
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 500;
}

.receiver-cell small {
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.order-remark-cell {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.order-result-table .order-row-actions,
.order-result-table .column-actions {
  position: sticky;
  right: 0;
  z-index: 2;
  border-left: 1px solid var(--admin-border);
  background: var(--admin-surface);
  text-align: center;
}

.order-result-table .column-actions {
  z-index: 3;
  background: var(--admin-surface-subtle);
}

.order-result-table tbody tr:hover .order-row-actions {
  background: var(--admin-surface-subtle);
}

.order-operation-section,
.order-detail-section {
  border-top: 1px solid var(--admin-border);
  background: transparent;
}

.order-operation-section {
  padding-top: 2px;
}

.order-section-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  min-height: 42px;
  padding: 8px 0;
  border-bottom: 1px solid var(--admin-border);
}

.order-section-header h2,
.order-section-header p {
  margin: 0;
  letter-spacing: 0;
}

.order-section-header h2 {
  color: var(--admin-text);
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.order-section-header p {
  margin-top: 2px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.order-action-bar,
.order-dialog-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.order-action-bar {
  padding-top: 10px;
}

.order-dialog-actions {
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--admin-border);
}

.order-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
}

.order-form-grid--compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.order-form-wide {
  grid-column: 1 / -1;
}

.order-form-control {
  box-sizing: border-box;
  width: 100%;
  height: var(--admin-control-height);
  min-width: 0;
  padding: 0 8px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius);
  outline: 0;
  color: var(--admin-text);
  background: var(--admin-surface);
  font-size: 13px;
}

.order-form-control:hover {
  border-color: var(--admin-text-placeholder);
}

.order-form-control:focus {
  border-color: var(--admin-primary);
}

.order-form-control:disabled {
  color: var(--admin-text-secondary);
  background: var(--admin-surface-subtle);
  cursor: not-allowed;
}

.order-dialog-notice {
  display: grid;
  gap: 3px;
  margin-bottom: 14px;
  padding: 8px 10px;
  border-left: 3px solid var(--admin-primary);
  color: var(--admin-text-secondary);
  background: var(--admin-surface-subtle);
  font-size: 13px;
  line-height: 20px;
}

.order-dialog-notice strong {
  color: var(--admin-text);
}

.order-dialog-notice--warning {
  border-left-color: var(--admin-warning);
}

.order-dialog-notice--danger {
  border-left-color: var(--admin-danger);
}

.order-detail-workbench {
  display: grid;
  gap: 20px;
  scroll-margin-top: 12px;
}

.order-detail-section {
  padding-top: 2px;
}

.order-detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-width: 0;
}

.order-detail-grid > div {
  min-width: 0;
  padding: 9px 10px;
  border-right: 1px solid var(--admin-border);
  border-bottom: 1px solid var(--admin-border);
}

.order-detail-grid > div:nth-child(4n) {
  border-right: 0;
}

.order-detail-grid .order-detail-item--wide {
  grid-column: span 2;
}

.order-detail-grid span,
.order-detail-grid strong {
  display: block;
  letter-spacing: 0;
}

.order-detail-grid span {
  margin-bottom: 3px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.order-detail-grid strong {
  overflow-wrap: anywhere;
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
}

.amount-grid,
.progress-summary-grid {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.amount-grid > div:nth-child(4n),
.progress-summary-grid > div:nth-child(4n) {
  border-right: 1px solid var(--admin-border);
}

.amount-grid > div:nth-child(6n),
.progress-summary-grid > div:nth-child(6n) {
  border-right: 0;
}

.order-subsection-title {
  margin: 16px 0 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--admin-border);
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.table-wrap {
  max-width: 100%;
  min-width: 0;
  overflow: auto;
  border: 1px solid var(--admin-border);
  -webkit-overflow-scrolling: touch;
}

.order-detail-table {
  width: 100%;
  min-width: 1120px;
  border-collapse: collapse;
  color: var(--admin-text);
  background: var(--admin-surface);
  font-size: 12px;
}

.drug-detail-table {
  min-width: 2480px;
}

.prescription-extension-table {
  min-width: 2180px;
}

.order-detail-table th,
.order-detail-table td {
  box-sizing: border-box;
  min-width: 88px;
  padding: 7px 8px;
  border-right: 1px solid var(--admin-border);
  border-bottom: 1px solid var(--admin-border);
  line-height: 20px;
  text-align: center;
  vertical-align: top;
}

.order-detail-table th {
  position: sticky;
  z-index: 1;
  top: 0;
  color: var(--admin-text-secondary);
  background: var(--admin-surface-subtle);
  font-weight: 600;
  white-space: nowrap;
}

.order-detail-table tr:last-child td {
  border-bottom: 0;
}

.order-detail-table th:last-child,
.order-detail-table td:last-child {
  border-right: 0;
}

.order-detail-table tbody tr:hover td {
  background: var(--admin-surface-subtle);
}

.text-cell {
  min-width: 150px !important;
  text-align: left !important;
  white-space: normal;
}

.empty {
  height: 72px;
  color: var(--admin-text-secondary);
  text-align: center !important;
}

@media (max-width: 1199px) {
  .order-filter-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .order-detail-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .order-detail-grid > div,
  .amount-grid > div,
  .progress-summary-grid > div {
    border-right: 1px solid var(--admin-border);
  }

  .order-detail-grid > div:nth-child(3n),
  .amount-grid > div:nth-child(3n),
  .progress-summary-grid > div:nth-child(3n) {
    border-right: 0;
  }

  .amount-grid,
  .progress-summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .order-filter-grid,
  .order-detail-grid,
  .amount-grid,
  .progress-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .order-detail-grid > div,
  .amount-grid > div,
  .progress-summary-grid > div {
    border-right: 1px solid var(--admin-border);
  }

  .order-detail-grid > div:nth-child(2n),
  .amount-grid > div:nth-child(2n),
  .progress-summary-grid > div:nth-child(2n) {
    border-right: 0;
  }

  .order-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 639px) {
  .order-center-page {
    gap: 12px;
  }

  .order-filter-grid,
  .order-detail-grid,
  .amount-grid,
  .progress-summary-grid,
  .order-form-grid,
  .order-form-grid--compact {
    grid-template-columns: minmax(0, 1fr);
  }

  .order-filter-field--wide,
  .order-detail-grid .order-detail-item--wide,
  .order-form-wide {
    grid-column: span 1;
  }

  .order-filter-field--page-size {
    max-width: none;
  }

  .order-time-range {
    grid-template-columns: minmax(0, 1fr);
  }

  .order-time-range > span {
    display: none;
  }

  .order-action-bar :deep(.t-button) {
    flex: 1 1 calc(50% - 4px);
    min-width: 0;
  }

  .order-detail-grid > div,
  .amount-grid > div,
  .progress-summary-grid > div,
  .order-detail-grid > div:nth-child(n) {
    border-right: 0;
  }

  .order-section-header {
    align-items: flex-start;
  }

  .order-dialog-actions :deep(.t-button) {
    flex: 1 1 0;
  }
}
</style>
