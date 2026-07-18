<script setup lang="ts">
import { computed, ref } from 'vue';
import { ApiError } from './api/client';
import {
  bindPrescription,
  cancelMesTask,
  finishMesTask,
  listActiveMesTasks,
  listCanOperatePrescriptions,
  listDecoctionDevices,
  listDeviceWorkRecords,
  listTaskEvents,
  recordTaskError,
  recordTemperature,
  recordWaterFinished,
  startMesTask,
  terminateMesTask,
} from './api/decoction';
import {
  listCallbackRecords,
  markCallbackFailed,
  markCallbackSuccess,
  replayCallback,
  dispatchDueCallbacks,
} from './api/callback';
import {
  listReadyDeliveryOrders,
  listShipmentTraces,
  listShipments,
  packShipment,
  receiveShipmentTrace,
  shipShipment,
  signShipment,
} from './api/logistics';
import {
  createCommunityStatusPush,
  dispatchDueIntegrationRetryTasks,
  findHospitalOrderByPrescription,
  listIntegrationMessages,
  listIntegrationRetryTasks,
  recordAddressPush,
  recordCommunityMessage,
} from './api/integration';
import type {
  CallbackRecord,
  DecoctionTaskEventRecord,
  DecoctionTaskRecord,
  DeliveryOrderRecord,
  DeviceRecord,
  DeviceWorkRecord,
  HospitalOrderRecord,
  IntegrationMessageRecord,
  IntegrationRetryTaskRecord,
  PrescriptionRecord,
  ShipmentRecord,
  ShipmentTraceRecord,
} from './api/types';
import AppLayout from './app/AppLayout.vue';
import { menuItems, viewTitles, type ViewKey } from './app/views';
import StatusPill from './components/StatusPill.vue';
import { formatDate } from './domain/formatters';
import { statusTone } from './domain/status';
import DashboardHome from './features/dashboard/DashboardHome.vue';
import OrderCenter from './features/orders/OrderCenter.vue';
import OpsConsole from './features/ops/OpsConsole.vue';
import PortalLookup from './features/portal/PortalLookup.vue';
import ReportOverview from './features/reports/ReportOverview.vue';
import WorkflowTasks from './features/workflow/WorkflowTasks.vue';

type NoticeTone = 'info' | 'success' | 'error';
type LogisticsDataset = 'ready' | 'shipments' | 'callbacks';
type WorkflowCounts = { reviews: number; dispenses: number; rechecks: number };

const activeView = ref<ViewKey>('dashboard');

const operationOperator = ref('admin');
const workflowCounts = ref<WorkflowCounts>({ reviews: 0, dispenses: 0, rechecks: 0 });
const dashboardHomeRef = ref<InstanceType<typeof DashboardHome> | null>(null);
const workflowTasksRef = ref<InstanceType<typeof WorkflowTasks> | null>(null);
const reportOverviewRef = ref<InstanceType<typeof ReportOverview> | null>(null);
const opsConsoleRef = ref<InstanceType<typeof OpsConsole> | null>(null);
const portalLookupRef = ref<InstanceType<typeof PortalLookup> | null>(null);
const reportTotalOrders = ref(0);
const reportActivationKey = ref(0);
const opsCount = ref(0);
const opsActivationKey = ref(0);
const notice = ref<{ tone: NoticeTone; text: string } | null>(null);

const prescriptions = ref<PrescriptionRecord[]>([]);
const decoctionDevices = ref<DeviceRecord[]>([]);
const decoctionTasks = ref<DecoctionTaskRecord[]>([]);
const decoctionEvents = ref<DecoctionTaskEventRecord[]>([]);
const decoctionWorkRecords = ref<DeviceWorkRecord[]>([]);
const decoctionLoading = ref(false);
const decoctionError = ref('');
const selectedPrescriptionNo = ref('');
const selectedDeviceCode = ref('');
const pailNo = ref('PAIL-001');
const waterVolumeMl = ref(1200);
const temperatureCelsius = ref(98);
const durationSeconds = ref(600);
const eventRemark = ref('');
const selectedEventTaskNo = ref('');
const handlingDecoctionTaskNo = ref('');


const activeLogisticsDataset = ref<LogisticsDataset>('ready');
const logisticsLoading = ref(false);
const logisticsError = ref('');
const logisticsLimit = ref(50);
const logisticsStatus = ref('');
const logisticsOrderNo = ref('');
const logisticsCompany = ref('SF');
const logisticsPayMethod = ref('MONTHLY');
const pkgWeight = ref(1);
const pkgNum = ref(1);
const traceLogisticsNo = ref('');
const traceProvider = ref('SF');
const traceOpCode = ref('50');
const traceContent = ref('');
const callbackStatus = ref('');
const callbackType = ref('');
const handlingShipmentId = ref('');
const handlingCallbackId = ref('');
const readyDeliveryOrders = ref<DeliveryOrderRecord[]>([]);
const shipments = ref<ShipmentRecord[]>([]);
const shipmentTraces = ref<ShipmentTraceRecord[]>([]);
const callbackRecords = ref<CallbackRecord[]>([]);

const integrationLoading = ref(false);
const integrationError = ref('');
const integrationLimit = ref(50);
const integrationSourceType = ref('');
const integrationStatus = ref('');
const integrationBusinessKey = ref('');
const integrationMessages = ref<IntegrationMessageRecord[]>([]);
const integrationRetryTasks = ref<IntegrationRetryTaskRecord[]>([]);
const integrationRetryStatus = ref('');
const integrationRetryType = ref('');
const integrationDispatchLimit = ref(20);
const communityAreaCode = ref('LG');
const communityCode = ref('CH-001');
const communityExternalMessageId = ref('');
const communityMessageType = ref('ORDER_CREATED');
const communityBusinessKey = ref('');
const communityRawPayload = ref('{}');
const communityStatusOrderNo = ref('');
const communityStatus = ref('SIGNED');
const communityStatusRequestUrl = ref('');
const communityStatusPayload = ref('{}');
const addressSupplementId = ref('');
const addressHospitalCode = ref('HOSP-001');
const addressAdapterCode = ref('LGFY');
const addressOrderNo = ref('');
const addressRawPayload = ref('{}');
const addressRequestUrl = ref('');
const hospitalPrescriptionNo = ref('');
const hospitalQueryPhone = ref('');
const hospitalOrder = ref<HospitalOrderRecord | null>(null);

const activeDecoctionCount = computed(() => decoctionTasks.value.length);
const activeLogisticsCount = computed(() => {
  if (activeLogisticsDataset.value === 'ready') return readyDeliveryOrders.value.length;
  if (activeLogisticsDataset.value === 'shipments') return shipments.value.length;
  return callbackRecords.value.length;
});
const activeIntegrationCount = computed(() => integrationMessages.value.length + integrationRetryTasks.value.length);
const currentViewTitle = computed(() => viewTitles[activeView.value]);
const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => ({
  reviews: workflowCounts.value.reviews,
  dispenses: workflowCounts.value.dispenses,
  rechecks: workflowCounts.value.rechecks,
  decoction: activeDecoctionCount.value,
  logistics: activeLogisticsCount.value,
  reports: reportTotalOrders.value,
  integration: activeIntegrationCount.value,
  ops: opsCount.value,
}));


const logisticsDatasetNames: Record<LogisticsDataset, string> = {
  ready: '待打包订单',
  shipments: '物流单',
  callbacks: '回调记录',
};

function showNotice(tone: NoticeTone, text: string) {
  notice.value = { tone, text };
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

async function refreshCurrentTasks() {
  if (activeView.value === 'dashboard') {
    await dashboardHomeRef.value?.refreshDashboard();
    return;
  }
  if (activeView.value === 'reviews' || activeView.value === 'dispenses' || activeView.value === 'rechecks') {
    await workflowTasksRef.value?.refreshCurrentTasks();
    return;
  }
  if (activeView.value === 'integration') {
    await refreshIntegrationMessages();
    return;
  }
  if (activeView.value === 'reports') {
    await reportOverviewRef.value?.refreshReports();
    return;
  }
  if (activeView.value === 'portal') {
    await portalLookupRef.value?.handlePortalQuery();
    return;
  }
  if (activeView.value === 'logistics') {
    await refreshLogisticsRecords();
    return;
  }
  if (activeView.value === 'ops') {
    await opsConsoleRef.value?.refreshOpsConsole();
    return;
  }
  if (activeView.value === 'decoction') {
    await refreshDecoctionSimulator();
    return;
  }
}

async function refreshDecoctionSimulator() {
  decoctionLoading.value = true;
  decoctionError.value = '';
  try {
    const [nextPrescriptions, nextDevices, nextTasks] = await Promise.all([
      listCanOperatePrescriptions(),
      listDecoctionDevices(),
      listActiveMesTasks(),
    ]);
    prescriptions.value = nextPrescriptions;
    decoctionDevices.value = nextDevices;
    decoctionTasks.value = nextTasks;
    if (!selectedEventTaskNo.value && decoctionTasks.value.length > 0) {
      selectedEventTaskNo.value = decoctionTasks.value[0].taskNo;
    }
    if (!selectedPrescriptionNo.value && prescriptions.value.length > 0) {
      selectedPrescriptionNo.value = prescriptions.value[0].prescriptionNo;
    }
    if (!selectedDeviceCode.value && decoctionDevices.value.length > 0) {
      selectedDeviceCode.value = decoctionDevices.value[0].deviceCode;
    }
    showNotice('info', `已刷新煎煮模拟：可操作处方 ${prescriptions.value.length} 条，活动任务 ${decoctionTasks.value.length} 条`);
  } catch (error) {
    prescriptions.value = [];
    decoctionDevices.value = [];
    decoctionTasks.value = [];
    decoctionError.value = errorMessage(error);
  } finally {
    decoctionLoading.value = false;
  }
}


function normalizedLogisticsLimit() {
  if (!Number.isFinite(logisticsLimit.value) || logisticsLimit.value <= 0) return 50;
  return Math.min(Math.trunc(logisticsLimit.value), 200);
}

function normalizedIntegrationLimit() {
  if (!Number.isFinite(integrationLimit.value) || integrationLimit.value <= 0) return 50;
  return Math.min(Math.trunc(integrationLimit.value), 200);
}

async function refreshIntegrationMessages() {
  integrationLoading.value = true;
  integrationError.value = '';
  const limit = normalizedIntegrationLimit();
  integrationLimit.value = limit;
  try {
    const [messages, retryTasks] = await Promise.all([
      listIntegrationMessages({
        sourceType: integrationSourceType.value,
        processStatus: integrationStatus.value,
        businessKey: integrationBusinessKey.value,
        limit,
      }),
      listIntegrationRetryTasks({
        taskType: integrationRetryType.value,
        taskStatus: integrationRetryStatus.value,
        businessKey: integrationBusinessKey.value,
        limit,
      }),
    ]);
    integrationMessages.value = messages;
    integrationRetryTasks.value = retryTasks;
    showNotice('info', `已刷新集成适配：消息 ${integrationMessages.value.length} 条，重试 ${integrationRetryTasks.value.length} 条`);
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleCommunityMessage() {
  if (!communityCode.value.trim() || !communityExternalMessageId.value.trim() || !communityMessageType.value.trim()) {
    integrationError.value = '社康编码、外部消息 ID 和消息类型不能为空';
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const record = await recordCommunityMessage({
      areaCode: communityAreaCode.value.trim() || undefined,
      communityCode: communityCode.value.trim(),
      externalMessageId: communityExternalMessageId.value.trim(),
      messageType: communityMessageType.value.trim(),
      businessKey: communityBusinessKey.value.trim() || undefined,
      rawPayload: communityRawPayload.value,
    });
    integrationBusinessKey.value = record.businessKey || integrationBusinessKey.value;
    showNotice('success', `社康消息已记录：${record.externalMessageId}`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleAddressPushRecord() {
  if (!addressSupplementId.value.trim() || !addressHospitalCode.value.trim() || !addressAdapterCode.value.trim() || !addressOrderNo.value.trim()) {
    integrationError.value = '补录 ID、医院编码、适配器和订单号不能为空';
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const record = await recordAddressPush({
      supplementId: addressSupplementId.value.trim(),
      hospitalCode: addressHospitalCode.value.trim(),
      adapterCode: addressAdapterCode.value.trim(),
      orderNo: addressOrderNo.value.trim(),
      rawPayload: addressRawPayload.value,
      requestUrl: addressRequestUrl.value.trim() || undefined,
    });
    integrationBusinessKey.value = record.businessKey || addressOrderNo.value.trim();
    showNotice('success', `地址回推记录已写入：${record.externalMessageId}`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleCommunityStatusPush() {
  if (!communityCode.value.trim() || !communityStatusOrderNo.value.trim() || !communityStatus.value.trim() || !communityStatusRequestUrl.value.trim()) {
    integrationError.value = '社康编码、订单号、状态和目标 URL 不能为空';
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const record = await createCommunityStatusPush({
      communityCode: communityCode.value.trim(),
      orderNo: communityStatusOrderNo.value.trim(),
      status: communityStatus.value.trim(),
      requestUrl: communityStatusRequestUrl.value.trim(),
      rawPayload: communityStatusPayload.value,
    });
    integrationBusinessKey.value = record.businessKey || communityStatusOrderNo.value.trim();
    showNotice('success', `社康状态回写任务已创建：${record.externalMessageId}`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleDispatchIntegrationRetryTasks() {
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const handled = await dispatchDueIntegrationRetryTasks(integrationDispatchLimit.value);
    showNotice('success', `已派发到期集成重试任务 ${handled} 条`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleHospitalPrescriptionQuery() {
  if (!hospitalPrescriptionNo.value.trim() || !hospitalQueryPhone.value.trim()) {
    integrationError.value = '处方号和手机号不能为空';
    hospitalOrder.value = null;
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    hospitalOrder.value = await findHospitalOrderByPrescription(
      hospitalPrescriptionNo.value.trim(),
      hospitalQueryPhone.value.trim(),
    );
    showNotice('success', `已查询到处方订单 ${hospitalOrder.value.orderNo}`);
  } catch (error) {
    hospitalOrder.value = null;
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function refreshLogisticsRecords() {
  logisticsLoading.value = true;
  logisticsError.value = '';
  const limit = normalizedLogisticsLimit();
  logisticsLimit.value = limit;
  try {
    if (activeLogisticsDataset.value === 'ready') {
      readyDeliveryOrders.value = await listReadyDeliveryOrders(limit);
    } else if (activeLogisticsDataset.value === 'shipments') {
      shipments.value = await listShipments({
        status: logisticsStatus.value,
        orderNo: logisticsOrderNo.value,
        limit,
      });
    } else {
      callbackRecords.value = await listCallbackRecords({
        status: callbackStatus.value,
        callbackType: callbackType.value,
        limit,
      });
    }
    showNotice('info', `已刷新${logisticsDatasetNames[activeLogisticsDataset.value]}：${activeLogisticsCount.value} 条`);
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    logisticsLoading.value = false;
  }
}

function switchLogisticsDataset(dataset: LogisticsDataset) {
  activeLogisticsDataset.value = dataset;
  void refreshLogisticsRecords();
}

async function handlePackShipment(order: DeliveryOrderRecord) {
  handlingShipmentId.value = order.orderId;
  logisticsError.value = '';
  try {
    const shipment = await packShipment({
      orderNo: order.orderNo,
      logisticsCompany: logisticsCompany.value.trim() || 'SF',
      payMethod: logisticsPayMethod.value.trim() || 'MONTHLY',
      pkgWeight: pkgWeight.value,
      pkgNum: pkgNum.value,
      operator: operationOperator.value.trim() || 'admin',
    });
    showNotice('success', `${shipment.orderNo} 已打包，运单 ${shipment.logisticsNo}`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    handlingShipmentId.value = '';
  }
}

async function handleShipmentAction(shipment: ShipmentRecord, action: 'ship' | 'sign') {
  handlingShipmentId.value = shipment.shipmentId;
  logisticsError.value = '';
  try {
    const command = {
      operator: operationOperator.value.trim() || 'admin',
      remark: traceContent.value.trim() || undefined,
    };
    const result = action === 'ship'
      ? await shipShipment(shipment.shipmentId, command)
      : await signShipment(shipment.shipmentId, command);
    showNotice('success', `${result.orderNo} 已${action === 'ship' ? '发货' : '签收'}`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    handlingShipmentId.value = '';
  }
}

async function handleReceiveTrace() {
  if (!traceLogisticsNo.value.trim()) {
    logisticsError.value = '请输入运单号';
    return;
  }
  logisticsLoading.value = true;
  logisticsError.value = '';
  try {
    const shipment = await receiveShipmentTrace({
      logisticsNo: traceLogisticsNo.value.trim(),
      provider: traceProvider.value.trim() || 'SF',
      opCode: traceOpCode.value.trim(),
      traceContent: traceContent.value.trim() || undefined,
      rawPayload: JSON.stringify({ source: 'admin-web', opCode: traceOpCode.value.trim() }),
      traceTime: new Date().toISOString(),
      operator: operationOperator.value.trim() || 'admin',
    });
    showNotice('success', `${shipment.logisticsNo} 轨迹已记录为 ${shipment.logisticsStatus}`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    logisticsLoading.value = false;
  }
}

async function refreshShipmentTraces(shipment: ShipmentRecord) {
  logisticsError.value = '';
  try {
    shipmentTraces.value = await listShipmentTraces(shipment.shipmentId);
  } catch (error) {
    shipmentTraces.value = [];
    logisticsError.value = errorMessage(error);
  }
}

async function handleCallbackAction(record: CallbackRecord, action: 'success' | 'failed' | 'replay') {
  handlingCallbackId.value = record.id;
  logisticsError.value = '';
  try {
    if (action === 'success') {
      await markCallbackSuccess(record.id);
    } else if (action === 'failed') {
      await markCallbackFailed(record.id);
    } else {
      await replayCallback(record.id);
    }
    showNotice('success', `${record.callbackType} 已处理`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    handlingCallbackId.value = '';
  }
}

async function handleDispatchDueCallbacks() {
  logisticsLoading.value = true;
  logisticsError.value = '';
  try {
    const handled = await dispatchDueCallbacks(normalizedLogisticsLimit());
    showNotice('success', `已派发到期回调 ${handled} 条`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    logisticsLoading.value = false;
  }
}

function newOperationId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

async function handleBindPrescription() {
  if (!operationOperator.value.trim()) {
    decoctionError.value = '操作人不能为空';
    return;
  }
  if (!selectedPrescriptionNo.value || !selectedDeviceCode.value) {
    decoctionError.value = '请选择处方和设备';
    return;
  }

  decoctionLoading.value = true;
  decoctionError.value = '';
  try {
    const result = await bindPrescription({
      operationId: newOperationId('pda-bind'),
      deviceCode: selectedDeviceCode.value,
      prescriptionNo: selectedPrescriptionNo.value,
      pailNo: pailNo.value.trim(),
      operator: operationOperator.value.trim(),
      timestamp: new Date().toISOString(),
      sign: 'dev-sign',
    });
    showNotice('success', `${result.prescriptionNo} 已绑定设备 ${result.deviceCode}`);
    selectedPrescriptionNo.value = '';
    await refreshDecoctionSimulator();
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    decoctionLoading.value = false;
  }
}

async function handleStartDecoction(task: DecoctionTaskRecord) {
  await handleMesTask(task, 'start');
}

async function handleFinishDecoction(task: DecoctionTaskRecord) {
  await handleMesTask(task, 'finish');
}

async function handleMesTask(task: DecoctionTaskRecord, action: 'start' | 'finish' | 'cancel' | 'terminate') {
  if (!operationOperator.value.trim()) {
    decoctionError.value = '操作人不能为空';
    return;
  }

  handlingDecoctionTaskNo.value = task.taskNo;
  decoctionError.value = '';
  try {
    const command = {
      operationId: newOperationId(`mes-${action}`),
      operator: operationOperator.value.trim(),
      timestamp: new Date().toISOString(),
      sign: 'dev-sign',
    };
    let result: DecoctionTaskRecord;
    if (action === 'start') {
      result = await startMesTask(task.taskNo, command);
    } else if (action === 'finish') {
      result = await finishMesTask(task.taskNo, command);
    } else if (action === 'cancel') {
      result = await cancelMesTask(task.taskNo, eventCommand('mes-cancel', {
        reason: eventRemark.value.trim() || 'manual cancel',
      }));
    } else {
      result = await terminateMesTask(task.taskNo, eventCommand('mes-terminate', {
        reason: eventRemark.value.trim() || 'manual terminate',
      }));
    }
    const actionText: Record<typeof action, string> = {
      start: '开始煎煮',
      finish: '完成煎煮',
      cancel: '取消绑定',
      terminate: '终止煎煮',
    };
    showNotice('success', `${result.taskNo} 已${actionText[action]}`);
    await refreshDecoctionSimulator();
    await refreshTaskEvents(task.taskNo);
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    handlingDecoctionTaskNo.value = '';
  }
}

function eventCommand(prefix: string, extra: Record<string, unknown> = {}) {
  return {
    operationId: newOperationId(prefix),
    operator: operationOperator.value.trim(),
    timestamp: new Date().toISOString(),
    sign: 'dev-sign',
    remark: eventRemark.value.trim() || undefined,
    ...extra,
  };
}

async function handleWaterFinished(task: DecoctionTaskRecord) {
  await handleTaskEvent(task, 'water');
}

async function handleTemperature(task: DecoctionTaskRecord) {
  await handleTaskEvent(task, 'temperature');
}

async function handleTaskError(task: DecoctionTaskRecord) {
  await handleTaskEvent(task, 'error');
}

async function handleTaskEvent(task: DecoctionTaskRecord, action: 'water' | 'temperature' | 'error') {
  if (!operationOperator.value.trim()) {
    decoctionError.value = '操作人不能为空';
    return;
  }

  handlingDecoctionTaskNo.value = task.taskNo;
  decoctionError.value = '';
  try {
    if (action === 'water') {
      await recordWaterFinished(task.taskNo, eventCommand('mes-water', { waterVolumeMl: waterVolumeMl.value }));
      showNotice('success', `${task.taskNo} 已记录加水完成`);
    } else if (action === 'temperature') {
      await recordTemperature(task.taskNo, eventCommand('mes-temp', {
        temperatureCelsius: temperatureCelsius.value,
        durationSeconds: durationSeconds.value,
      }));
      showNotice('success', `${task.taskNo} 已记录温度`);
    } else {
      await recordTaskError(task.taskNo, eventCommand('mes-error', { reason: eventRemark.value.trim() || 'manual error' }));
      showNotice('success', `${task.taskNo} 已记录异常`);
    }
    selectedEventTaskNo.value = task.taskNo;
    await refreshTaskEvents(task.taskNo);
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    handlingDecoctionTaskNo.value = '';
  }
}

async function refreshTaskEvents(taskNo = selectedEventTaskNo.value) {
  if (!taskNo) {
    decoctionEvents.value = [];
    decoctionWorkRecords.value = [];
    return;
  }
  decoctionError.value = '';
  try {
    selectedEventTaskNo.value = taskNo;
    const [events, workRecords] = await Promise.all([
      listTaskEvents(taskNo),
      listDeviceWorkRecords(taskNo),
    ]);
    decoctionEvents.value = events;
    decoctionWorkRecords.value = workRecords;
  } catch (error) {
    decoctionEvents.value = [];
    decoctionWorkRecords.value = [];
    decoctionError.value = errorMessage(error);
  }
}

function switchView(view: ViewKey) {
  activeView.value = view;
  if (view === 'reports') reportActivationKey.value += 1;
  if (view === 'ops') opsActivationKey.value += 1;
  if (view === 'decoction' && decoctionTasks.value.length === 0) void refreshDecoctionSimulator();
  if (view === 'logistics') void refreshLogisticsRecords();
  if (view === 'integration' && integrationMessages.value.length === 0) void refreshIntegrationMessages();
}
</script>

<template>
  <AppLayout
    :active-view="activeView"
    :title="currentViewTitle.title"
    :subtitle="currentViewTitle.subtitle"
    :menu-items="menuItems"
    :counts="menuCounts"
    :notice="notice"
    @refresh="refreshCurrentTasks"
    @switch-view="switchView"
  >
      <ReportOverview
        v-show="activeView === 'reports'"
        ref="reportOverviewRef"
        :active="activeView === 'reports'"
        :activation-key="reportActivationKey"
        @count-changed="reportTotalOrders = $event"
        @notice="showNotice"
      />

      <OpsConsole
        v-show="activeView === 'ops'"
        ref="opsConsoleRef"
        :active="activeView === 'ops'"
        :activation-key="opsActivationKey"
        @count-changed="opsCount = $event"
        @notice="showNotice"
      />

      <DashboardHome v-if="activeView === 'dashboard'" ref="dashboardHomeRef" @notice="showNotice" />

      <WorkflowTasks
        ref="workflowTasksRef"
        v-else-if="activeView === 'reviews' || activeView === 'dispenses' || activeView === 'rechecks'"
        :active-view="activeView"
        @counts-changed="workflowCounts = $event"
        @notice="showNotice"
      />

      <OrderCenter v-else-if="activeView === 'orders'" @notice="showNotice" />

      <PortalLookup v-else-if="activeView === 'portal'" ref="portalLookupRef" @notice="showNotice" />

      <section v-else-if="activeView === 'integration'" class="workspace">
        <div class="toolbar">
          <label>
            <span>来源类型</span>
            <input v-model="integrationSourceType" placeholder="COMMUNITY_HOSPITAL / ADDRESS_PUSH" @keyup.enter="refreshIntegrationMessages" />
          </label>
          <label>
            <span>处理状态</span>
            <input v-model="integrationStatus" placeholder="PENDING / FAILED" @keyup.enter="refreshIntegrationMessages" />
          </label>
          <label class="grow">
            <span>业务键</span>
            <input v-model="integrationBusinessKey" placeholder="订单号 / 社康业务号" @keyup.enter="refreshIntegrationMessages" />
          </label>
          <label>
            <span>任务类型</span>
            <input v-model="integrationRetryType" placeholder="ADDRESS_PUSH / COMMUNITY_STATUS_PUSH" @keyup.enter="refreshIntegrationMessages" />
          </label>
          <label>
            <span>任务状态</span>
            <input v-model="integrationRetryStatus" placeholder="PENDING / FAILED / DEAD" @keyup.enter="refreshIntegrationMessages" />
          </label>
          <label class="limit-label">
            <span>条数</span>
            <input v-model.number="integrationLimit" type="number" min="1" max="200" step="10" @keyup.enter="refreshIntegrationMessages" />
          </label>
          <label class="limit-label">
            <span>派发数</span>
            <input v-model.number="integrationDispatchLimit" type="number" min="1" max="200" step="10" />
          </label>
          <button class="primary" type="button" :disabled="integrationLoading" @click="refreshIntegrationMessages">
            {{ integrationLoading ? '刷新中' : '刷新' }}
          </button>
          <button class="secondary" type="button" :disabled="integrationLoading" @click="handleDispatchIntegrationRetryTasks">
            派发到期
          </button>
        </div>

        <div class="toolbar event-toolbar">
          <label>
            <span>区域</span>
            <input v-model="communityAreaCode" placeholder="LG" />
          </label>
          <label>
            <span>社康编码</span>
            <input v-model="communityCode" placeholder="CH-001" />
          </label>
          <label>
            <span>外部消息 ID</span>
            <input v-model="communityExternalMessageId" placeholder="MSG-001" />
          </label>
          <label>
            <span>消息类型</span>
            <input v-model="communityMessageType" placeholder="ORDER_CREATED" />
          </label>
          <label>
            <span>业务键</span>
            <input v-model="communityBusinessKey" placeholder="CH-ORDER-001" />
          </label>
          <label class="grow payload-input">
            <span>原始报文</span>
            <input v-model="communityRawPayload" placeholder="{...} / XML" @keyup.enter="handleCommunityMessage" />
          </label>
          <button class="secondary" type="button" :disabled="integrationLoading" @click="handleCommunityMessage">
            记录社康消息
          </button>
        </div>

        <div class="toolbar event-toolbar">
          <label>
            <span>补录 ID</span>
            <input v-model="addressSupplementId" placeholder="UUID" />
          </label>
          <label>
            <span>医院编码</span>
            <input v-model="addressHospitalCode" placeholder="HOSP-001" />
          </label>
          <label>
            <span>适配器</span>
            <input v-model="addressAdapterCode" placeholder="LGFY" />
          </label>
          <label>
            <span>订单号</span>
            <input v-model="addressOrderNo" placeholder="ZHYF..." />
          </label>
          <label class="grow payload-input">
            <span>回推报文</span>
            <input v-model="addressRawPayload" placeholder="{...}" @keyup.enter="handleAddressPushRecord" />
          </label>
          <label class="grow payload-input">
            <span>目标 URL</span>
            <input v-model="addressRequestUrl" placeholder="http://..." @keyup.enter="handleAddressPushRecord" />
          </label>
          <button class="secondary" type="button" :disabled="integrationLoading" @click="handleAddressPushRecord">
            记录地址回推
          </button>
        </div>

        <div class="toolbar event-toolbar">
          <label>
            <span>社康编码</span>
            <input v-model="communityCode" placeholder="CH-001" />
          </label>
          <label>
            <span>订单号</span>
            <input v-model="communityStatusOrderNo" placeholder="ZHYF..." />
          </label>
          <label>
            <span>回写状态</span>
            <input v-model="communityStatus" placeholder="SIGNED / CANCELLED" />
          </label>
          <label class="grow payload-input">
            <span>目标 URL</span>
            <input v-model="communityStatusRequestUrl" placeholder="http://..." />
          </label>
          <label class="grow payload-input">
            <span>回写报文</span>
            <input v-model="communityStatusPayload" placeholder="{...}" @keyup.enter="handleCommunityStatusPush" />
          </label>
          <button class="secondary" type="button" :disabled="integrationLoading" @click="handleCommunityStatusPush">
            创建社康回写
          </button>
        </div>

        <div class="toolbar event-toolbar">
          <label>
            <span>处方号</span>
            <input v-model="hospitalPrescriptionNo" placeholder="RX..." @keyup.enter="handleHospitalPrescriptionQuery" />
          </label>
          <label>
            <span>手机号</span>
            <input v-model="hospitalQueryPhone" placeholder="患者或收件人手机号" @keyup.enter="handleHospitalPrescriptionQuery" />
          </label>
          <button class="secondary" type="button" :disabled="integrationLoading" @click="handleHospitalPrescriptionQuery">
            处方查单
          </button>
        </div>

        <p v-if="integrationError" class="error-line">{{ integrationError }}</p>

        <div v-if="hospitalOrder" class="detail-grid">
          <div>
            <span>医院</span>
            <strong>{{ hospitalOrder.institutionName }}</strong>
          </div>
          <div>
            <span>平台订单号</span>
            <strong>{{ hospitalOrder.orderNo }}</strong>
          </div>
          <div>
            <span>外部单号</span>
            <strong>{{ hospitalOrder.externalOrderNo }}</strong>
          </div>
          <div>
            <span>订单状态</span>
            <StatusPill :value="hospitalOrder.orderStatus" :tone="statusTone(hospitalOrder.orderStatus)" />
          </div>
          <div>
            <span>处方</span>
            <strong>{{ hospitalOrder.prescriptionNo }}</strong>
            <small>{{ hospitalOrder.prescriptionStatus }}</small>
          </div>
          <div>
            <span>收件人</span>
            <strong>{{ hospitalOrder.receiverName || '-' }} / {{ hospitalOrder.receiverPhone || '-' }}</strong>
          </div>
          <div class="wide">
            <span>收件地址</span>
            <strong>{{ hospitalOrder.receiverAddress || '-' }}</strong>
          </div>
          <div>
            <span>物流</span>
            <strong>{{ hospitalOrder.logisticsCompany || '-' }} / {{ hospitalOrder.logisticsNo || '-' }}</strong>
            <small>{{ hospitalOrder.logisticsStatus || '未发货' }}</small>
          </div>
        </div>

        <div class="table-wrap integration-table">
          <table>
            <thead>
              <tr>
                <th>任务/目标</th>
                <th>业务键</th>
                <th>状态</th>
                <th>重试</th>
                <th>时间</th>
                <th>响应</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!integrationLoading && integrationRetryTasks.length === 0">
                <td colspan="6" class="empty">暂无集成重试任务</td>
              </tr>
              <tr v-for="task in integrationRetryTasks" :key="task.taskId">
                <td>
                  <strong>{{ task.taskType }} / {{ task.targetSystem }}</strong>
                  <small>{{ task.requestUrl }}</small>
                </td>
                <td>
                  <strong>{{ task.businessKey || '-' }}</strong>
                  <small>{{ task.messageId }}</small>
                </td>
                <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
                <td>
                  <strong>{{ task.retryCount }}</strong>
                  <small>{{ formatDate(task.nextRetryAt) }}</small>
                </td>
                <td>
                  <strong>{{ formatDate(task.createdAt) }}</strong>
                  <small>{{ formatDate(task.processedAt) }}</small>
                </td>
                <td><code>{{ task.responseBody || task.requestBody || '-' }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="table-wrap integration-table">
          <table>
            <thead>
              <tr>
                <th>来源/外部消息</th>
                <th>类型/业务键</th>
                <th>状态</th>
                <th>时间</th>
                <th>载荷</th>
                <th>失败原因</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!integrationLoading && integrationMessages.length === 0">
                <td colspan="6" class="empty">暂无集成适配消息</td>
              </tr>
              <tr v-for="message in integrationMessages" :key="message.messageId">
                <td>
                  <strong>{{ message.sourceType }} / {{ message.sourceSystem }}</strong>
                  <small>{{ message.externalMessageId }}</small>
                </td>
                <td>
                  <strong>{{ message.messageType }}</strong>
                  <small>{{ message.businessKey || '-' }}</small>
                </td>
                <td><StatusPill :value="message.processStatus" :tone="statusTone(message.processStatus)" /></td>
                <td>
                  <strong>{{ formatDate(message.createdAt) }}</strong>
                  <small>{{ formatDate(message.updatedAt) }}</small>
                </td>
                <td><code>{{ message.normalizedPayload || message.rawPayload || '-' }}</code></td>
                <td><code>{{ message.failureReason || '-' }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="activeView === 'logistics'" class="workspace">
        <div class="toolbar ops-tabs">
          <button
            class="secondary"
            :class="{ active: activeLogisticsDataset === 'ready' }"
            type="button"
            @click="switchLogisticsDataset('ready')"
          >
            待打包
          </button>
          <button
            class="secondary"
            :class="{ active: activeLogisticsDataset === 'shipments' }"
            type="button"
            @click="switchLogisticsDataset('shipments')"
          >
            物流单
          </button>
          <button
            class="secondary"
            :class="{ active: activeLogisticsDataset === 'callbacks' }"
            type="button"
            @click="switchLogisticsDataset('callbacks')"
          >
            回调记录
          </button>
          <label class="limit-label">
            <span>条数</span>
            <input v-model.number="logisticsLimit" type="number" min="1" max="200" step="10" @keyup.enter="refreshLogisticsRecords" />
          </label>
          <button class="primary" type="button" :disabled="logisticsLoading" @click="refreshLogisticsRecords">
            {{ logisticsLoading ? '刷新中' : '刷新' }}
          </button>
        </div>

        <div class="toolbar event-toolbar">
          <label>
            <span>操作人</span>
            <input v-model="operationOperator" placeholder="admin" />
          </label>
          <template v-if="activeLogisticsDataset === 'ready'">
            <label>
              <span>物流公司</span>
              <input v-model="logisticsCompany" placeholder="SF / EMS" />
            </label>
            <label>
              <span>付款方式</span>
              <input v-model="logisticsPayMethod" placeholder="MONTHLY" />
            </label>
            <label>
              <span>重量 kg</span>
              <input v-model.number="pkgWeight" type="number" min="0" step="0.1" />
            </label>
            <label>
              <span>包裹数</span>
              <input v-model.number="pkgNum" type="number" min="1" step="1" />
            </label>
          </template>
          <template v-else-if="activeLogisticsDataset === 'shipments'">
            <label>
              <span>状态</span>
              <input v-model="logisticsStatus" placeholder="PACKED / SHIPPED / SIGNED" @keyup.enter="refreshLogisticsRecords" />
            </label>
            <label>
              <span>订单号</span>
              <input v-model="logisticsOrderNo" placeholder="ZHYF..." @keyup.enter="refreshLogisticsRecords" />
            </label>
            <label>
              <span>运单号</span>
              <input v-model="traceLogisticsNo" placeholder="SF-001" />
            </label>
            <label>
              <span>来源</span>
              <input v-model="traceProvider" placeholder="SF / EMS" />
            </label>
            <label>
              <span>轨迹码</span>
              <input v-model="traceOpCode" placeholder="50 / 80 / 203" />
            </label>
            <label class="grow">
              <span>轨迹说明</span>
              <input v-model="traceContent" placeholder="已揽收 / 已签收" @keyup.enter="handleReceiveTrace" />
            </label>
            <button class="secondary" type="button" :disabled="logisticsLoading" @click="handleReceiveTrace">
              记录轨迹
            </button>
          </template>
          <template v-else>
            <label>
              <span>状态</span>
              <input v-model="callbackStatus" placeholder="PENDING / SUCCESS / FAILED / DEAD" @keyup.enter="refreshLogisticsRecords" />
            </label>
            <label>
              <span>回调类型</span>
              <input v-model="callbackType" placeholder="ORDER_SHIPPED" @keyup.enter="refreshLogisticsRecords" />
            </label>
            <button class="secondary" type="button" :disabled="logisticsLoading" @click="handleDispatchDueCallbacks">
              派发到期回调
            </button>
          </template>
        </div>

        <p v-if="logisticsError" class="error-line">{{ logisticsError }}</p>

        <div v-if="activeLogisticsDataset === 'ready'" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>订单</th>
                <th>收件人</th>
                <th>地址</th>
                <th>状态</th>
                <th class="right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!logisticsLoading && readyDeliveryOrders.length === 0">
                <td colspan="5" class="empty">暂无待打包订单</td>
              </tr>
              <tr v-for="item in readyDeliveryOrders" :key="item.orderId">
                <td>
                  <strong>{{ item.orderNo }}</strong>
                  <small>{{ item.externalOrderNo }}</small>
                </td>
                <td>
                  <strong>{{ item.receiverName }}</strong>
                  <small>{{ item.receiverPhone }}</small>
                </td>
                <td>{{ item.receiverAddress }}</td>
                <td><StatusPill :value="item.orderStatus" :tone="statusTone(item.orderStatus)" /></td>
                <td class="actions">
                  <button
                    class="primary"
                    type="button"
                    :disabled="handlingShipmentId === item.orderId"
                    @click="handlePackShipment(item)"
                  >
                    打包
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="activeLogisticsDataset === 'shipments'" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>订单/运单</th>
                <th>物流</th>
                <th>状态</th>
                <th>打包/出库/签收</th>
                <th class="right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!logisticsLoading && shipments.length === 0">
                <td colspan="5" class="empty">暂无物流单</td>
              </tr>
              <tr v-for="shipment in shipments" :key="shipment.shipmentId">
                <td>
                  <strong>{{ shipment.orderNo }}</strong>
                  <small>{{ shipment.logisticsNo }}</small>
                </td>
                <td>
                  <strong>{{ shipment.logisticsCompany }}</strong>
                  <small>{{ shipment.payMethod || '-' }} / {{ shipment.pkgWeight || '-' }}kg / {{ shipment.pkgNum || '-' }}件</small>
                </td>
                <td><StatusPill :value="shipment.logisticsStatus" :tone="statusTone(shipment.logisticsStatus)" /></td>
                <td>
                  <strong>{{ formatDate(shipment.packageTime) }}</strong>
                  <small>{{ formatDate(shipment.outboundTime) }} / {{ formatDate(shipment.signTime) }}</small>
                </td>
                <td class="actions">
                  <button
                    class="secondary"
                    type="button"
                    :disabled="handlingShipmentId === shipment.shipmentId || shipment.logisticsStatus !== 'PACKED'"
                    @click="handleShipmentAction(shipment, 'ship')"
                  >
                    发货
                  </button>
                  <button
                    class="success"
                    type="button"
                    :disabled="handlingShipmentId === shipment.shipmentId || shipment.logisticsStatus === 'SIGNED'"
                    @click="handleShipmentAction(shipment, 'sign')"
                  >
                    签收
                  </button>
                  <button class="secondary" type="button" @click="refreshShipmentTraces(shipment)">
                    轨迹
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>订单/业务</th>
                <th>类型</th>
                <th>状态</th>
                <th>重试</th>
                <th>创建时间</th>
                <th class="right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!logisticsLoading && callbackRecords.length === 0">
                <td colspan="6" class="empty">暂无回调记录</td>
              </tr>
              <tr v-for="record in callbackRecords" :key="record.id">
                <td>
                  <strong>{{ record.orderNo }}</strong>
                  <small>{{ record.businessId }}</small>
                </td>
                <td>{{ record.callbackType }}</td>
                <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
                <td>
                  <strong>{{ record.retryCount }}</strong>
                  <small>{{ formatDate(record.nextRetryAt) }}</small>
                </td>
                <td>{{ formatDate(record.createdAt) }}</td>
                <td class="actions">
                  <button class="success" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'success')">
                    成功
                  </button>
                  <button class="danger" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'failed')">
                    失败
                  </button>
                  <button class="secondary" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'replay')">
                    重放
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="activeLogisticsDataset === 'shipments'" class="table-wrap events-wrap">
          <table>
            <thead>
              <tr>
                <th>运单</th>
                <th>轨迹状态</th>
                <th>轨迹时间</th>
                <th>内容</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="shipmentTraces.length === 0">
                <td colspan="4" class="empty">暂无轨迹明细</td>
              </tr>
              <tr v-for="trace in shipmentTraces" :key="trace.traceId">
                <td>{{ trace.logisticsNo }}</td>
                <td><StatusPill :value="trace.traceStatus" :tone="statusTone(trace.traceStatus)" /></td>
                <td>{{ formatDate(trace.traceTime) }}</td>
                <td><code>{{ trace.traceContent || trace.rawPayload }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="activeView === 'decoction'" class="workspace">
        <div class="toolbar">
          <label>
            <span>操作人</span>
            <input v-model="operationOperator" placeholder="admin" />
          </label>
          <label class="grow">
            <span>可操作处方</span>
            <select v-model="selectedPrescriptionNo">
              <option value="" disabled>选择复核完成处方</option>
              <option
                v-for="prescription in prescriptions"
                :key="prescription.prescriptionId"
                :value="prescription.prescriptionNo"
              >
                {{ prescription.prescriptionNo }} / {{ prescription.orderNo }}
              </option>
            </select>
          </label>
          <label>
            <span>煎煮设备</span>
            <select v-model="selectedDeviceCode">
              <option value="" disabled>选择设备</option>
              <option
                v-for="device in decoctionDevices"
                :key="device.deviceCode"
                :value="device.deviceCode"
              >
                {{ device.deviceCode }} / {{ device.deviceStatus }}
              </option>
            </select>
          </label>
          <label>
            <span>水桶号</span>
            <input v-model="pailNo" placeholder="PAIL-001" />
          </label>
          <button class="primary" type="button" :disabled="decoctionLoading" @click="handleBindPrescription">
            绑定
          </button>
          <button class="secondary" type="button" :disabled="decoctionLoading" @click="refreshDecoctionSimulator">
            {{ decoctionLoading ? '刷新中' : '刷新' }}
          </button>
        </div>

        <p v-if="decoctionError" class="error-line">{{ decoctionError }}</p>

        <div class="detail-grid devices-grid">
          <div v-for="device in decoctionDevices" :key="device.deviceCode">
            <span>{{ device.deviceName }}</span>
            <strong>{{ device.deviceCode }}</strong>
            <StatusPill :value="device.deviceStatus" :tone="statusTone(device.deviceStatus)" />
            <small>{{ device.activeTaskNo || '无活动任务' }}</small>
          </div>
        </div>

        <div class="toolbar event-toolbar">
          <label>
            <span>加水量 ml</span>
            <input v-model.number="waterVolumeMl" type="number" min="0" step="50" />
          </label>
          <label>
            <span>温度 ℃</span>
            <input v-model.number="temperatureCelsius" type="number" min="0" step="1" />
          </label>
          <label>
            <span>时长秒</span>
            <input v-model.number="durationSeconds" type="number" min="0" step="30" />
          </label>
          <label class="grow">
            <span>事件备注</span>
            <input v-model="eventRemark" placeholder="异常原因或事件备注" />
          </label>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>任务号</th>
                <th>订单/处方</th>
                <th>设备/水桶</th>
                <th>任务状态</th>
                <th>开始时间</th>
                <th>完成时间</th>
                <th class="right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!decoctionLoading && decoctionTasks.length === 0">
                <td colspan="7" class="empty">暂无活动煎煮任务</td>
              </tr>
              <tr v-for="task in decoctionTasks" :key="task.taskId">
                <td>
                  <strong>{{ task.taskNo }}</strong>
                  <small>{{ task.taskId }}</small>
                </td>
                <td>
                  <strong>{{ task.orderNo }}</strong>
                  <small>{{ task.prescriptionNo }}</small>
                </td>
                <td>
                  <strong>{{ task.deviceCode }}</strong>
                  <small>{{ task.pailNo || '-' }}</small>
                </td>
                <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
                <td>{{ formatDate(task.startedAt) }}</td>
                <td>{{ formatDate(task.finishedAt) }}</td>
                <td class="actions">
                  <button
                    class="success"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'"
                    @click="handleStartDecoction(task)"
                  >
                    开始
                  </button>
                  <button
                    class="secondary"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'"
                    @click="handleWaterFinished(task)"
                  >
                    加水
                  </button>
                  <button
                    class="danger"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'"
                    @click="handleMesTask(task, 'cancel')"
                  >
                    取消
                  </button>
                  <button
                    class="secondary"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'"
                    @click="handleTemperature(task)"
                  >
                    温度
                  </button>
                  <button
                    class="success"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'"
                    @click="handleFinishDecoction(task)"
                  >
                    完成
                  </button>
                  <button
                    class="danger"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'"
                    @click="handleMesTask(task, 'terminate')"
                  >
                    终止
                  </button>
                  <button
                    class="danger"
                    type="button"
                    :disabled="handlingDecoctionTaskNo === task.taskNo"
                    @click="handleTaskError(task)"
                  >
                    异常
                  </button>
                  <button
                    class="secondary"
                    type="button"
                    @click="refreshTaskEvents(task.taskNo)"
                  >
                    事件
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="table-wrap events-wrap">
          <table>
            <thead>
              <tr>
                <th>作业任务</th>
                <th>动作/结果</th>
                <th>状态变化</th>
                <th>设备/来源</th>
                <th>操作人</th>
                <th>作业时间</th>
                <th>作业内容</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="decoctionWorkRecords.length === 0">
                <td colspan="7" class="empty">暂无作业明细</td>
              </tr>
              <tr v-for="record in decoctionWorkRecords" :key="record.recordId">
                <td>
                  <strong>{{ record.taskNo }}</strong>
                  <small>{{ record.operationId }}</small>
                </td>
                <td>
                  <StatusPill :value="record.actionType" :tone="statusTone(record.actionResult)" />
                  <small>{{ record.actionResult }}</small>
                </td>
                <td>
                  <strong>{{ record.taskStatusBefore || '-' }} → {{ record.taskStatusAfter || '-' }}</strong>
                  <small>{{ record.prescriptionNo }}</small>
                </td>
                <td>
                  <strong>{{ record.deviceCode }}</strong>
                  <small>{{ record.source }}</small>
                </td>
                <td>{{ record.operator }}</td>
                <td>{{ formatDate(record.actionTime) }}</td>
                <td><code>{{ record.detailPayload }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="table-wrap events-wrap">
          <table>
            <thead>
              <tr>
                <th>事件任务</th>
                <th>事件类型</th>
                <th>操作人</th>
                <th>事件时间</th>
                <th>事件内容</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="decoctionEvents.length === 0">
                <td colspan="5" class="empty">暂无事件记录</td>
              </tr>
              <tr v-for="event in decoctionEvents" :key="event.eventId">
                <td>
                  <strong>{{ event.taskNo }}</strong>
                  <small>{{ event.operationId }}</small>
                </td>
                <td><StatusPill :value="event.eventType" :tone="statusTone(event.eventType)" /></td>
                <td>{{ event.operator }}</td>
                <td>{{ formatDate(event.eventTime) }}</td>
                <td><code>{{ event.eventPayload }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
  </AppLayout>
</template>
