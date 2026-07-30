<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { listDecoctionPerformanceDetails } from '../../api/report';
import {
  bindPrescription,
  cancelMesTask,
  cancelPdaDecoction,
  createAdminDecoctionDevice,
  finishMesTask,
  finishPdaDecoction,
  listAdminDecoctionDevices,
  listActiveMesTasks,
  listCanOperatePrescriptions,
  listDeviceWorkRecords,
  listPendingMesTasks,
  listTaskEvents,
  recordTaskError,
  recordTemperature,
  recordWaterFinished,
  startMesTask,
  startPdaDecoction,
  terminateMesTask,
  terminatePdaDecoction,
  updateAdminDecoctionDevice,
} from '../../api/decoction';
import type {
  DecoctionDeviceCommand,
  DecoctionPerformanceDetailRecord,
  DecoctionEventCommand,
  DecoctionTaskEventRecord,
  DecoctionTaskRecord,
  DeviceRecord,
  DeviceWorkRecord,
  MesTaskOperationCommand,
  PrescriptionRecord,
  SimulatorOperationCommand,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { dateInputToIso, defaultDate, formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type DecoctionDataset = 'devices' | 'binds' | 'printerConfig' | 'pails' | 'cloudPrints' | 'workRecords';
type MesAction = 'start' | 'finish' | 'cancel' | 'terminate';
type PdaAction = 'start' | 'finish' | 'cancel' | 'terminate';
type TaskEventAction = 'water' | 'temperature' | 'error';
type EventCommandExtra = Partial<Omit<DecoctionEventCommand, 'operationId' | 'operator' | 'timestamp' | 'sign'>>;
type CsvExportValue = string | number | null | undefined;
type DeviceFormState = {
  deviceCode: string;
  deviceName: string;
  deviceType: string;
  deviceGroup: string;
  decoctionCenter: string;
  pdaCode: string;
  printerCode: string;
  printTemplateCode: string;
  enabled: boolean;
  remark: string;
};

const props = defineProps<{
  active: boolean;
  activationKey: number;
  operationOperator: string;
  routeKey: string;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
  cloudPrintCountChanged: [count: number];
  'update:operationOperator': [value: string];
}>();

const operatorModel = computed({
  get: () => props.operationOperator,
  set: (value: string) => emit('update:operationOperator', value),
});

const prescriptions = ref<PrescriptionRecord[]>([]);
const decoctionDevices = ref<DeviceRecord[]>([]);
const decoctionTasks = ref<DecoctionTaskRecord[]>([]);
const pendingMesTasks = ref<DecoctionTaskRecord[]>([]);
const decoctionEvents = ref<DecoctionTaskEventRecord[]>([]);
const decoctionWorkRecords = ref<DeviceWorkRecord[]>([]);
const cloudPrintRecords = ref<DecoctionPerformanceDetailRecord[]>([]);
const decoctionLoading = ref(false);
const cloudPrintLoading = ref(false);
const cloudPrintLoaded = ref(false);
const eventLoading = ref(false);
const decoctionError = ref('');
const activeDecoctionDataset = ref<DecoctionDataset>('binds');
const startTime = ref(defaultDate(-13));
const endTime = ref(defaultDate(0));
const decoctionCenter = ref('');
const prescriptionNoQuery = ref('');
const deviceCodeQuery = ref('');
const deviceType = ref('');
const deviceGroup = ref('');
const deviceStatus = ref('');
const printStatus = ref('');
const selectedPrescriptionNo = ref('');
const selectedDeviceCode = ref('');
const pailNo = ref('');
const waterVolumeMl = ref(1200);
const temperatureCelsius = ref(98);
const durationSeconds = ref(600);
const eventRemark = ref('');
const selectedEventTaskNo = ref('');
const handlingDecoctionTaskNo = ref('');
const deviceFormOpen = ref(false);
const deviceSaving = ref(false);
const editingDeviceCode = ref('');
const deviceForm = ref<DeviceFormState>(emptyDeviceForm());

const activeDecoctionCount = computed(() => decoctionTasks.value.length);
const pendingMesTaskCount = computed(() => pendingMesTasks.value.length);
const mainLoading = computed(() => (
  activeDecoctionDataset.value === 'cloudPrints' ? cloudPrintLoading.value : decoctionLoading.value
));

const filteredCloudPrintRecords = computed(() => {
  const prescriptionNo = prescriptionNoQuery.value.trim().toLowerCase();
  const deviceCode = deviceCodeQuery.value.trim().toLowerCase();
  const actionResult = printStatus.value.trim();

  return cloudPrintRecords.value.filter((record) => {
    const matchesPrescription = !prescriptionNo
      || rowValue(record.prescriptionNo).toLowerCase().includes(prescriptionNo);
    const matchesDevice = !deviceCode
      || rowValue(record.deviceCode).toLowerCase().includes(deviceCode)
      || rowValue(record.pailNo).toLowerCase().includes(deviceCode);
    const matchesResult = !actionResult || record.actionResult === actionResult;
    return matchesPrescription && matchesDevice && matchesResult;
  });
});

const filteredDecoctionDevices = computed(() => {
  const prescriptionNo = prescriptionNoQuery.value.trim().toLowerCase();
  const deviceCode = deviceCodeQuery.value.trim().toLowerCase();
  const type = deviceType.value.trim();
  const group = deviceGroup.value.trim().toLowerCase();
  const center = decoctionCenter.value.trim();
  const status = deviceStatus.value.trim();

  return decoctionDevices.value.filter((device) => {
    const matchesPrescription = !prescriptionNo
      || rowValue(device.activePrescriptionNo).toLowerCase().includes(prescriptionNo)
      || rowValue(device.activeTaskNo).toLowerCase().includes(prescriptionNo);
    const matchesDevice = !deviceCode
      || rowValue(device.deviceCode).toLowerCase().includes(deviceCode)
      || rowValue(device.deviceName).toLowerCase().includes(deviceCode)
      || rowValue(device.activeTaskNo).toLowerCase().includes(deviceCode)
      || rowValue(device.activePrescriptionNo).toLowerCase().includes(deviceCode);
    const matchesType = !type || device.deviceType === type;
    const matchesGroup = !group || rowValue(device.deviceGroup).toLowerCase().includes(group);
    const matchesCenter = !center || device.decoctionCenter === center;
    const matchesStatus = !status
      || device.deviceStatus === status
      || (status === 'ONLINE' && device.enabled)
      || (status === 'OFFLINE' && !device.enabled)
      || (status === 'BUSY' && Boolean(device.activeTaskNo));
    return matchesPrescription && matchesDevice && matchesType && matchesGroup && matchesCenter && matchesStatus;
  });
});

const filteredDecoctionTasks = computed(() => {
  const prescriptionNo = prescriptionNoQuery.value.trim().toLowerCase();
  const deviceCode = deviceCodeQuery.value.trim().toLowerCase();
  const status = deviceStatus.value.trim();

  return decoctionTasks.value.filter((task) => {
    const matchesPrescription = !prescriptionNo
      || rowValue(task.prescriptionNo).toLowerCase().includes(prescriptionNo)
      || rowValue(task.orderNo).toLowerCase().includes(prescriptionNo)
      || rowValue(task.taskNo).toLowerCase().includes(prescriptionNo);
    const matchesDevice = !deviceCode
      || rowValue(task.deviceCode).toLowerCase().includes(deviceCode)
      || rowValue(task.pailNo).toLowerCase().includes(deviceCode);
    const matchesStatus = !status || task.taskStatus === status;
    return matchesPrescription && matchesDevice && matchesStatus;
  });
});

const selectedEventTask = computed(() => (
  decoctionTasks.value.find((task) => task.taskNo === selectedEventTaskNo.value) ?? null
));

const decoctionDeviceByCode = computed(() => (
  new Map(decoctionDevices.value.map((device) => [device.deviceCode, device]))
));

const waterPailRows = computed(() => {
  const rows = new Map<string, DecoctionTaskRecord>();
  decoctionTasks.value.forEach((task) => {
    const nextPailNo = task.pailNo?.trim();
    if (nextPailNo && !rows.has(nextPailNo)) {
      rows.set(nextPailNo, task);
    }
  });
  return Array.from(rows.entries()).map(([nextPailNo, task]) => ({
    pailNo: nextPailNo,
    task,
  }));
});

const filteredWaterPailRows = computed(() => {
  const visibleTaskIds = new Set(filteredDecoctionTasks.value.map((task) => task.taskId));
  return waterPailRows.value.filter((row) => visibleTaskIds.has(row.task.taskId));
});

const filteredDecoctionWorkRecords = computed(() => {
  const prescriptionNo = prescriptionNoQuery.value.trim().toLowerCase();
  const deviceCode = deviceCodeQuery.value.trim().toLowerCase();
  const actionResult = printStatus.value.trim();
  const status = deviceStatus.value.trim();

  return decoctionWorkRecords.value.filter((record) => {
    const matchesPrescription = !prescriptionNo
      || rowValue(record.prescriptionNo).toLowerCase().includes(prescriptionNo)
      || rowValue(record.taskNo).toLowerCase().includes(prescriptionNo);
    const matchesDevice = !deviceCode
      || rowValue(record.deviceCode).toLowerCase().includes(deviceCode)
      || rowValue(record.pailNo).toLowerCase().includes(deviceCode);
    const matchesResult = !actionResult || record.actionResult === actionResult;
    const matchesStatus = !status || record.taskStatusBefore === status || record.taskStatusAfter === status;
    return matchesPrescription && matchesDevice && matchesResult && matchesStatus;
  });
});

const activeDecoctionTableColspan = computed(() => {
  if (activeDecoctionDataset.value === 'devices') return 12;
  if (activeDecoctionDataset.value === 'printerConfig') return 9;
  if (activeDecoctionDataset.value === 'pails') return 7;
  if (activeDecoctionDataset.value === 'cloudPrints') return 8;
  if (activeDecoctionDataset.value === 'workRecords') return 9;
  return 14;
});

const activePageTotal = computed(() => {
  if (activeDecoctionDataset.value === 'devices') return filteredDecoctionDevices.value.length;
  if (activeDecoctionDataset.value === 'binds') return filteredDecoctionTasks.value.length;
  if (activeDecoctionDataset.value === 'printerConfig') return filteredDecoctionDevices.value.length;
  if (activeDecoctionDataset.value === 'pails') return filteredWaterPailRows.value.length;
  if (activeDecoctionDataset.value === 'cloudPrints') return filteredCloudPrintRecords.value.length;
  if (activeDecoctionDataset.value === 'workRecords') return filteredDecoctionWorkRecords.value.length;
  return 0;
});

function datasetFromRouteKey(routeKey: string): DecoctionDataset {
  if (routeKey === 'decoction') return 'devices';
  if (routeKey === 'decoctionPdaPrinterRelations') return 'printerConfig';
  if (routeKey === 'decoctionWaterPails') return 'pails';
  if (routeKey === 'decoctionCloudPrintRecords') return 'cloudPrints';
  if (routeKey === 'decoctionPrescriptionBindings') return 'binds';
  return 'binds';
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function newOperationId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function emptyDeviceForm(): DeviceFormState {
  return {
    deviceCode: '',
    deviceName: '',
    deviceType: '煎药机',
    deviceGroup: '',
    decoctionCenter: '良益堂煎煮中心',
    pdaCode: '',
    printerCode: '',
    printTemplateCode: '',
    enabled: true,
    remark: '',
  };
}

function escapeCsvCell(value: CsvExportValue) {
  const text = value === null || value === undefined ? '' : String(value);
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

function downloadCsv(filename: string, headers: readonly string[], rows: readonly CsvExportValue[][]) {
  const lines = [
    headers.map(escapeCsvCell).join(','),
    ...rows.map((row) => row.map(escapeCsvCell).join(',')),
  ];
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function pageSummary(total: number) {
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${total} 项`;
}

function switchDecoctionDataset(dataset: DecoctionDataset) {
  activeDecoctionDataset.value = dataset;
  if (dataset === 'cloudPrints' && props.active && !cloudPrintLoaded.value) {
    void refreshCloudPrintRecords();
  }
}

function taskDataStatus(task: DecoctionTaskRecord) {
  return task.taskStatus === 'CANCELLED' || task.taskStatus === 'TERMINATED' ? '停用' : '正常';
}

function bindType(task: DecoctionTaskRecord) {
  return task.pailNo ? '水桶绑定' : '设备绑定';
}

function deviceUseStatus(device: DeviceRecord) {
  if (!device.enabled) return '停用';
  return device.activeTaskNo ? '使用中' : '空闲';
}

function deviceCommandFromForm(): DecoctionDeviceCommand {
  return {
    deviceCode: deviceForm.value.deviceCode.trim(),
    deviceName: deviceForm.value.deviceName.trim(),
    deviceType: deviceForm.value.deviceType.trim(),
    deviceGroup: deviceForm.value.deviceGroup.trim() || null,
    decoctionCenter: deviceForm.value.decoctionCenter.trim() || null,
    pdaCode: deviceForm.value.pdaCode.trim() || null,
    printerCode: deviceForm.value.printerCode.trim() || null,
    printTemplateCode: deviceForm.value.printTemplateCode.trim() || null,
    enabled: deviceForm.value.enabled,
    remark: deviceForm.value.remark.trim() || null,
  };
}

function deviceCommandFromRecord(device: DeviceRecord, enabled = device.enabled): DecoctionDeviceCommand {
  return {
    deviceName: device.deviceName,
    deviceType: device.deviceType,
    deviceGroup: device.deviceGroup,
    decoctionCenter: device.decoctionCenter,
    pdaCode: device.pdaCode,
    printerCode: device.printerCode,
    printTemplateCode: device.printTemplateCode,
    enabled,
    remark: device.remark,
  };
}

function openCreateDeviceForm(initialDeviceType = '煎药机') {
  editingDeviceCode.value = '';
  deviceForm.value = emptyDeviceForm();
  deviceForm.value.deviceType = initialDeviceType;
  deviceFormOpen.value = true;
}

function openEditDeviceForm(device: DeviceRecord) {
  editingDeviceCode.value = device.deviceCode;
  deviceForm.value = {
    deviceCode: device.deviceCode,
    deviceName: device.deviceName,
    deviceType: device.deviceType,
    deviceGroup: device.deviceGroup ?? '',
    decoctionCenter: device.decoctionCenter ?? '',
    pdaCode: device.pdaCode ?? '',
    printerCode: device.printerCode ?? '',
    printTemplateCode: device.printTemplateCode ?? '',
    enabled: device.enabled,
    remark: device.remark ?? '',
  };
  deviceFormOpen.value = true;
}

function closeDeviceForm() {
  if (deviceSaving.value) return;
  deviceFormOpen.value = false;
  editingDeviceCode.value = '';
  deviceForm.value = emptyDeviceForm();
}

async function saveDeviceForm() {
  if (!deviceForm.value.deviceCode.trim() || !deviceForm.value.deviceName.trim()) {
    decoctionError.value = '设备编号和设备名称不能为空';
    return;
  }
  deviceSaving.value = true;
  decoctionError.value = '';
  try {
    if (editingDeviceCode.value) {
      await updateAdminDecoctionDevice(editingDeviceCode.value, deviceCommandFromForm());
      emit('notice', 'success', `设备 ${editingDeviceCode.value} 已更新`);
    } else {
      const created = await createAdminDecoctionDevice(deviceCommandFromForm());
      emit('notice', 'success', `设备 ${created.deviceCode} 已新增`);
    }
    deviceFormOpen.value = false;
    editingDeviceCode.value = '';
    deviceForm.value = emptyDeviceForm();
    await refreshDecoctionSimulator();
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    deviceSaving.value = false;
  }
}

async function toggleDeviceEnabled(device: DeviceRecord, enabled: boolean) {
  deviceSaving.value = true;
  decoctionError.value = '';
  try {
    const updated = await updateAdminDecoctionDevice(device.deviceCode, deviceCommandFromRecord(device, enabled));
    emit('notice', 'success', `设备 ${updated.deviceCode} 已${enabled ? '启用' : '停用'}`);
    await refreshDecoctionSimulator();
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    deviceSaving.value = false;
  }
}

function deviceName(deviceCode: string) {
  const registeredDeviceName = decoctionDeviceByCode.value.get(deviceCode)?.deviceName;
  return registeredDeviceName ?? `未登记设备：${deviceCode}`;
}

function downloadDeviceCsv() {
  downloadCsv(
    `煎煮设备列表-${new Date().toISOString().slice(0, 10)}.csv`,
    ['设备编号', '设备名称', '设备类型', '设备组别', '煎煮中心', 'PDA', '打码机', '模板', '启用', '设备状态', '使用状态', '活动任务', '活动处方'],
    filteredDecoctionDevices.value.map((device) => [
      device.deviceCode,
      device.deviceName,
      device.deviceType,
      device.deviceGroup,
      device.decoctionCenter,
      device.pdaCode,
      device.printerCode,
      device.printTemplateCode,
      device.enabled ? '启用' : '停用',
      device.deviceStatus,
      deviceUseStatus(device),
      device.activeTaskNo,
      device.activePrescriptionNo,
    ]),
  );
  emit('notice', 'success', `已导出 ${filteredDecoctionDevices.value.length} 台煎煮设备`);
}

function downloadPrinterConfigCsv() {
  downloadCsv(
    `打码机打印配置-${new Date().toISOString().slice(0, 10)}.csv`,
    ['ID', 'PDA 编号', '设备编号', '设备名称', '打码机编号', '打印模板', '状态', '修改时间', '备注'],
    filteredDecoctionDevices.value.map((device) => [
      device.deviceId,
      device.pdaCode,
      device.deviceCode,
      device.deviceName,
      device.printerCode,
      device.printTemplateCode,
      device.enabled ? '启用' : '停用',
      formatDate(device.updatedAt),
      device.remark,
    ]),
  );
  emit('notice', 'success', `已导出 ${filteredDecoctionDevices.value.length} 条设备打印配置`);
}

function downloadWaterPailCsv() {
  downloadCsv(
    `加水桶当前绑定-${new Date().toISOString().slice(0, 10)}.csv`,
    ['加水桶号', '关联任务', '处方号', '设备编号', '状态', '操作人', '创建时间', '修改时间'],
    filteredWaterPailRows.value.map((row) => [
      row.pailNo,
      row.task.taskNo,
      row.task.prescriptionNo,
      row.task.deviceCode,
      row.task.taskStatus,
      row.task.operator,
      formatDate(row.task.createdAt),
      formatDate(row.task.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${filteredWaterPailRows.value.length} 个当前绑定加水桶`);
}

function downloadEventCsv() {
  downloadCsv(
    `煎煮事件记录-${new Date().toISOString().slice(0, 10)}.csv`,
    ['任务编号', '事件类型', '操作编号', '操作人', '事件时间', '事件内容', '创建时间'],
    decoctionEvents.value.map((event) => [
      event.taskNo,
      event.eventType,
      event.operationId,
      event.operator,
      formatDate(event.eventTime),
      event.eventPayload,
      formatDate(event.createdAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${decoctionEvents.value.length} 条事件记录`);
}

function downloadWorkRecordCsv() {
  downloadCsv(
    `煎煮作业记录-${new Date().toISOString().slice(0, 10)}.csv`,
    ['任务编号', '处方号', '设备编号', '水桶号', '动作', '结果', '前状态', '后状态', '操作编号', '来源', '操作人', '作业时间', '作业内容'],
    filteredDecoctionWorkRecords.value.map((record) => [
      record.taskNo,
      record.prescriptionNo,
      record.deviceCode,
      record.pailNo,
      record.actionType,
      record.actionResult,
      record.taskStatusBefore,
      record.taskStatusAfter,
      record.operationId,
      record.source,
      record.operator,
      formatDate(record.actionTime),
      record.detailPayload,
    ]),
  );
  emit('notice', 'success', `已导出 ${filteredDecoctionWorkRecords.value.length} 条作业记录`);
}

function cloudPrintQueryParams() {
  return {
    from: dateInputToIso(startTime.value),
    to: dateInputToIso(endTime.value, true),
  };
}

async function refreshCloudPrintRecords() {
  if (cloudPrintLoading.value) return;
  cloudPrintLoading.value = true;
  decoctionError.value = '';
  try {
    const nextRecords = await listDecoctionPerformanceDetails(cloudPrintQueryParams());
    cloudPrintRecords.value = nextRecords;
    cloudPrintLoaded.value = true;
    emit('countChanged', filteredCloudPrintRecords.value.length);
    emit('cloudPrintCountChanged', filteredCloudPrintRecords.value.length);
    emit('notice', 'success', `已查询 ${nextRecords.length} 条煎煮作业/打印相关记录`);
  } catch (error) {
    cloudPrintRecords.value = [];
    cloudPrintLoaded.value = false;
    emit('countChanged', 0);
    emit('cloudPrintCountChanged', 0);
    decoctionError.value = errorMessage(error);
  } finally {
    cloudPrintLoading.value = false;
  }
}

function downloadCloudPrintCsv() {
  downloadCsv(
    `煎煮作业打印相关记录-${new Date().toISOString().slice(0, 10)}.csv`,
    ['任务号', '订单号', '处方号', '设备编码', '水桶号', '动作', '结果', '操作人', '来源', '剂数', '作业时间'],
    filteredCloudPrintRecords.value.map((record) => [
      record.taskNo,
      record.orderNo,
      record.prescriptionNo,
      record.deviceCode,
      record.pailNo,
      record.actionType,
      record.actionResult,
      record.operator,
      record.source,
      record.doseCount,
      formatDate(record.actionTime),
    ]),
  );
  emit('notice', 'success', `已导出 ${filteredCloudPrintRecords.value.length} 条煎煮作业/打印相关记录`);
}

function handleMainQuery() {
  if (activeDecoctionDataset.value === 'cloudPrints') {
    void refreshCloudPrintRecords();
    return;
  }
  void refreshDecoctionSimulator();
}

function makeMesCommand(prefix: string): MesTaskOperationCommand {
  return {
    operationId: newOperationId(prefix),
    operator: operatorModel.value.trim(),
    timestamp: new Date().toISOString(),
    sign: 'dev-sign',
  };
}

function makePdaCommand(task: DecoctionTaskRecord, prefix: string): SimulatorOperationCommand {
  return {
    operationId: newOperationId(prefix),
    deviceCode: task.deviceCode,
    prescriptionNo: task.prescriptionNo,
    pailNo: task.pailNo ?? undefined,
    operator: operatorModel.value.trim(),
    timestamp: new Date().toISOString(),
    sign: 'dev-sign',
  };
}

function eventCommand(prefix: string, extra: EventCommandExtra = {}): DecoctionEventCommand {
  return {
    operationId: newOperationId(prefix),
    operator: operatorModel.value.trim(),
    timestamp: new Date().toISOString(),
    sign: 'dev-sign',
    remark: eventRemark.value.trim() || undefined,
    ...extra,
  };
}

function requireOperator() {
  if (operatorModel.value.trim()) return true;
  decoctionError.value = '操作人不能为空';
  return false;
}

function isPositiveNumber(value: number) {
  return Number.isFinite(value) && value > 0;
}

function isNonNegativeNumber(value: number) {
  return Number.isFinite(value) && value >= 0;
}

async function refreshTaskEvents(taskNo = selectedEventTaskNo.value) {
  if (!taskNo) {
    decoctionEvents.value = [];
    decoctionWorkRecords.value = [];
    return;
  }

  eventLoading.value = true;
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
  } finally {
    eventLoading.value = false;
  }
}

async function refreshDecoctionSimulator() {
  decoctionLoading.value = true;
  decoctionError.value = '';
  try {
    const [nextPrescriptions, nextDevices, nextPendingTasks, nextTasks] = await Promise.all([
      listCanOperatePrescriptions(),
      listAdminDecoctionDevices(),
      listPendingMesTasks(),
      listActiveMesTasks(),
    ]);
    prescriptions.value = nextPrescriptions;
    decoctionDevices.value = nextDevices;
    pendingMesTasks.value = nextPendingTasks;
    decoctionTasks.value = nextTasks;

    if (!prescriptions.value.some((prescription) => prescription.prescriptionNo === selectedPrescriptionNo.value)) {
      selectedPrescriptionNo.value = prescriptions.value[0]?.prescriptionNo ?? '';
    }
    const availableDevices = decoctionDevices.value.filter((device) => device.enabled && !device.activeTaskNo);
    if (!availableDevices.some((device) => device.deviceCode === selectedDeviceCode.value)) {
      selectedDeviceCode.value = availableDevices[0]?.deviceCode ?? '';
    }
    if (!decoctionTasks.value.some((task) => task.taskNo === selectedEventTaskNo.value)) {
      selectedEventTaskNo.value = decoctionTasks.value[0]?.taskNo ?? '';
    }
    if (selectedEventTaskNo.value) {
      await refreshTaskEvents(selectedEventTaskNo.value);
    } else {
      decoctionEvents.value = [];
      decoctionWorkRecords.value = [];
    }
    emit('notice', 'info', `已刷新煎煮作业：可操作处方 ${prescriptions.value.length} 条，设备 ${decoctionDevices.value.length} 台，待 MES 开始 ${pendingMesTasks.value.length} 条，活动任务 ${decoctionTasks.value.length} 条`);
  } catch (error) {
    prescriptions.value = [];
    decoctionDevices.value = [];
    pendingMesTasks.value = [];
    decoctionTasks.value = [];
    decoctionEvents.value = [];
    decoctionWorkRecords.value = [];
    decoctionError.value = errorMessage(error);
  } finally {
    decoctionLoading.value = false;
  }
}

async function handleBindPrescription() {
  if (!requireOperator()) return;
  if (!selectedPrescriptionNo.value || !selectedDeviceCode.value) {
    decoctionError.value = '请选择可操作处方和煎煮设备';
    return;
  }
  if (!pailNo.value.trim()) {
    decoctionError.value = '水桶号不能为空';
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
      operator: operatorModel.value.trim(),
      timestamp: new Date().toISOString(),
      sign: 'dev-sign',
    });
    selectedEventTaskNo.value = result.taskNo;
    emit('notice', 'success', `${result.prescriptionNo} 已绑定设备 ${result.deviceCode}`);
    selectedPrescriptionNo.value = '';
    await refreshDecoctionSimulator();
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    decoctionLoading.value = false;
  }
}

async function handleMesTask(task: DecoctionTaskRecord, action: MesAction) {
  if (!requireOperator()) return;

  handlingDecoctionTaskNo.value = task.taskNo;
  decoctionError.value = '';
  try {
    let result: DecoctionTaskRecord;
    if (action === 'start') {
      result = await startMesTask(task.taskNo, makeMesCommand('mes-start'));
    } else if (action === 'finish') {
      result = await finishMesTask(task.taskNo, makeMesCommand('mes-finish'));
    } else if (action === 'cancel') {
      result = await cancelMesTask(task.taskNo, eventCommand('mes-cancel', {
        reason: eventRemark.value.trim() || '人工取消',
      }));
    } else {
      result = await terminateMesTask(task.taskNo, eventCommand('mes-terminate', {
        reason: eventRemark.value.trim() || '人工终止',
      }));
    }
    const actionText: Record<MesAction, string> = {
      start: '开始煎煮',
      finish: '完成煎煮',
      cancel: '取消绑定',
      terminate: '终止煎煮',
    };
    selectedEventTaskNo.value = result.taskNo;
    emit('notice', 'success', `${result.taskNo} 已${actionText[action]}`);
    await refreshDecoctionSimulator();
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    handlingDecoctionTaskNo.value = '';
  }
}

async function handlePdaTask(task: DecoctionTaskRecord, action: PdaAction) {
  if (!requireOperator()) return;

  handlingDecoctionTaskNo.value = task.taskNo;
  decoctionError.value = '';
  try {
    let result: DecoctionTaskRecord;
    if (action === 'start') {
      result = await startPdaDecoction(makePdaCommand(task, 'pda-start'));
    } else if (action === 'finish') {
      result = await finishPdaDecoction(makePdaCommand(task, 'pda-finish'));
    } else if (action === 'cancel') {
      result = await cancelPdaDecoction(makePdaCommand(task, 'pda-cancel'));
    } else {
      result = await terminatePdaDecoction(makePdaCommand(task, 'pda-terminate'));
    }
    const actionText: Record<PdaAction, string> = {
      start: '开始煎煮',
      finish: '完成煎煮',
      cancel: '取消绑定',
      terminate: '终止煎煮',
    };
    selectedEventTaskNo.value = result.taskNo;
    emit('notice', 'success', `${result.taskNo} 已通过 PDA ${actionText[action]}`);
    await refreshDecoctionSimulator();
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    handlingDecoctionTaskNo.value = '';
  }
}

async function handleTaskEvent(task: DecoctionTaskRecord, action: TaskEventAction) {
  if (!requireOperator()) return;
  if (action === 'water' && !isPositiveNumber(waterVolumeMl.value)) {
    decoctionError.value = '加水量必须大于 0';
    return;
  }
  if (action === 'temperature' && (!isNonNegativeNumber(temperatureCelsius.value) || !isPositiveNumber(durationSeconds.value))) {
    decoctionError.value = '温度不能小于 0，时长必须大于 0';
    return;
  }

  handlingDecoctionTaskNo.value = task.taskNo;
  decoctionError.value = '';
  try {
    if (action === 'water') {
      await recordWaterFinished(task.taskNo, eventCommand('mes-water', { waterVolumeMl: waterVolumeMl.value }));
      emit('notice', 'success', `${task.taskNo} 已记录加水完成`);
    } else if (action === 'temperature') {
      await recordTemperature(task.taskNo, eventCommand('mes-temp', {
        temperatureCelsius: temperatureCelsius.value,
        durationSeconds: durationSeconds.value,
      }));
      emit('notice', 'success', `${task.taskNo} 已记录温度`);
    } else {
      await recordTaskError(task.taskNo, eventCommand('mes-error', {
        reason: eventRemark.value.trim() || '人工登记异常',
      }));
      emit('notice', 'success', `${task.taskNo} 已记录异常`);
    }
    selectedEventTaskNo.value = task.taskNo;
    await refreshTaskEvents(task.taskNo);
  } catch (error) {
    decoctionError.value = errorMessage(error);
  } finally {
    handlingDecoctionTaskNo.value = '';
  }
}

watch(activeDecoctionCount, (count) => {
  if (activeDecoctionDataset.value !== 'cloudPrints') {
    emit('countChanged', count);
  }
}, { immediate: true });

watch(filteredCloudPrintRecords, (records) => {
  if (props.active && activeDecoctionDataset.value === 'cloudPrints') {
    emit('countChanged', records.length);
    emit('cloudPrintCountChanged', records.length);
  }
});

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (!active) return;
    if (activeDecoctionDataset.value === 'cloudPrints') {
      void refreshCloudPrintRecords();
      return;
    }
    if (decoctionTasks.value.length === 0) {
      void refreshDecoctionSimulator();
    }
  },
  { immediate: true },
);

watch(
  () => props.routeKey,
  (routeKey) => {
    const dataset = datasetFromRouteKey(routeKey);
    activeDecoctionDataset.value = dataset;
    if (props.active && dataset === 'cloudPrints' && !cloudPrintLoaded.value) {
      void refreshCloudPrintRecords();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDecoctionSimulator,
});
</script>

<template>
  <section class="legacy-page decoction-page">
    <ul class="legacy-search decoction-mode-search">
      <li class="logistics-mode-item">
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'devices' }" type="button" @click="switchDecoctionDataset('devices')">
          设备列表查询
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'binds' }" type="button" @click="switchDecoctionDataset('binds')">
          处方设备绑定列表
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'printerConfig' }" type="button" @click="switchDecoctionDataset('printerConfig')">
          打码机打印配置
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'pails' }" type="button" @click="switchDecoctionDataset('pails')">
          加水桶管理
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'cloudPrints' }" type="button" @click="switchDecoctionDataset('cloudPrints')">
          云打印记录列表
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'workRecords' }" type="button" @click="switchDecoctionDataset('workRecords')">
          作业记录
        </button>
      </li>
    </ul>

    <ul class="legacy-search decoction-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" type="date" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" type="date" />
      </li>
      <li>
        煎煮中心：
        <select v-model="decoctionCenter" class="legacy-input input-large">
          <option value="">请选择</option>
          <option value="良益堂煎药中心">良益堂煎药中心</option>
          <option value="良益堂煎煮中心">良益堂煎煮中心</option>
        </select>
      </li>
      <li>
        处方号：
        <input v-model="prescriptionNoQuery" class="legacy-input input-large" placeholder="处方/订单/任务号" />
      </li>
      <li>
        设备编号：
        <input v-model="deviceCodeQuery" class="legacy-input" placeholder="设备/水桶" />
      </li>
      <li>
        设备类型：
        <select v-model="deviceType" class="legacy-input">
          <option value="">请选择</option>
          <option value="煎药机">煎药机</option>
          <option value="包装机">包装机</option>
          <option value="打码机">打码机</option>
        </select>
      </li>
      <li>
        设备组别：
        <input v-model="deviceGroup" class="legacy-input" placeholder="设备组别" />
      </li>
      <li>
        状态：
        <select v-model="deviceStatus" class="legacy-input">
          <option value="">请选择</option>
          <option value="ONLINE">在线</option>
          <option value="OFFLINE">离线</option>
          <option value="BUSY">占用</option>
          <option value="BOUND">已绑定</option>
          <option value="DECOCTING">煎煮中</option>
          <option value="DECOCTED">已完成</option>
          <option value="CANCELLED">已取消</option>
          <option value="TERMINATED">已终止</option>
        </select>
      </li>
      <li>
        打印状态：
        <select v-model="printStatus" class="legacy-input">
          <option value="">请选择</option>
          <option value="ACCEPTED">成功</option>
          <option value="REJECTED">失败</option>
        </select>
      </li>
      <li>
        操作人：
        <input v-model="operatorModel" class="legacy-input" placeholder="admin" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="mainLoading" @click="handleMainQuery">
          {{ mainLoading ? '查询中' : '查询' }}
        </button>
      </li>
      <li class="decoction-filter-tip">
        <template v-if="activeDecoctionDataset === 'cloudPrints'">
          云打印记录当前复用煎煮作业明细接口，支持时间、处方号、设备/水桶和动作结果筛选。
        </template>
        <template v-else>
          当前基于已加载结果做前端筛选；设备管理已接入煎煮中心、设备类型和设备组别字段。
        </template>
      </li>
    </ul>

    <ul class="legacy-search decoction-operate-search">
      <li>
        可操作处方：
        <select v-model="selectedPrescriptionNo" class="legacy-input input-large">
          <option value="">选择复核完成处方</option>
          <option
            v-for="prescription in prescriptions"
            :key="prescription.prescriptionId"
            :value="prescription.prescriptionNo"
          >
            {{ prescription.prescriptionNo }} / {{ prescription.orderNo }}
          </option>
        </select>
      </li>
      <li>
        煎煮设备：
        <select v-model="selectedDeviceCode" class="legacy-input input-large">
          <option value="">选择设备</option>
          <option
            v-for="device in decoctionDevices"
            :key="device.deviceCode"
            :value="device.deviceCode"
            :disabled="!device.enabled || Boolean(device.activeTaskNo)"
          >
            {{ device.deviceCode }} / {{ device.deviceName }} / {{ device.deviceStatus }}
          </option>
        </select>
      </li>
      <li>
        水桶号：
        <input v-model="pailNo" class="legacy-input" placeholder="请输入水桶号" />
      </li>
      <li>
        加水量 ml：
        <input v-model.number="waterVolumeMl" class="legacy-input input-small" type="number" min="1" step="50" />
      </li>
      <li>
        温度 ℃：
        <input v-model.number="temperatureCelsius" class="legacy-input input-small" type="number" min="0" step="1" />
      </li>
      <li>
        时长秒：
        <input v-model.number="durationSeconds" class="legacy-input input-small" type="number" min="1" step="30" />
      </li>
      <li>
        备注：
        <input v-model="eventRemark" class="legacy-input input-large" placeholder="异常原因、取消原因或事件备注" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="decoctionLoading" @click="handleBindPrescription">
          绑定
        </button>
      </li>
    </ul>

    <p v-if="decoctionError" class="error-line">{{ decoctionError }}</p>

    <section
      v-if="(activeDecoctionDataset === 'devices' || activeDecoctionDataset === 'printerConfig') && deviceFormOpen"
      class="legacy-panel decoction-device-form"
    >
      <div class="decoction-device-form-head">
        <strong>{{ editingDeviceCode ? `编辑设备 ${editingDeviceCode}` : '新增煎煮设备' }}</strong>
        <button class="legacy-link-btn" type="button" :disabled="deviceSaving" @click="closeDeviceForm">关闭</button>
      </div>
      <div class="decoction-device-form-grid">
        <label>
          设备编号
          <input v-model="deviceForm.deviceCode" class="legacy-input" :disabled="Boolean(editingDeviceCode) || deviceSaving" />
        </label>
        <label>
          设备名称
          <input v-model="deviceForm.deviceName" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label>
          设备类型
          <select v-model="deviceForm.deviceType" class="legacy-input" :disabled="deviceSaving">
            <option value="煎药机">煎药机</option>
            <option value="包装机">包装机</option>
            <option value="打码机">打码机</option>
          </select>
        </label>
        <label>
          设备组别
          <input v-model="deviceForm.deviceGroup" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label>
          煎煮中心
          <input v-model="deviceForm.decoctionCenter" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label>
          PDA 编号
          <input v-model="deviceForm.pdaCode" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label>
          打码机编号
          <input v-model="deviceForm.printerCode" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label>
          打印模板
          <input v-model="deviceForm.printTemplateCode" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label>
          备注
          <input v-model="deviceForm.remark" class="legacy-input" :disabled="deviceSaving" />
        </label>
        <label class="decoction-device-enabled">
          <input v-model="deviceForm.enabled" type="checkbox" :disabled="deviceSaving" />
          启用
        </label>
      </div>
      <div class="decoction-device-form-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="deviceSaving" @click="saveDeviceForm">
          {{ deviceSaving ? '保存中' : '保存设备' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="deviceSaving" @click="closeDeviceForm">取消</button>
      </div>
    </section>

    <ul class="legacy-stats decoction-stats">
      <li>
        <strong>{{ prescriptions.length }}</strong>
        <span>可操作处方</span>
      </li>
      <li>
        <strong>{{ decoctionDevices.length }}</strong>
        <span>设备数</span>
      </li>
      <li>
        <strong>{{ pendingMesTaskCount }}</strong>
        <span>待 MES 开始</span>
      </li>
      <li>
        <strong>{{ activeDecoctionCount }}</strong>
        <span>活动任务</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table
        class="legacy-main-table decoction-main-table"
        :class="{
          'decoction-device-table': activeDecoctionDataset === 'devices',
          'decoction-printer-table': activeDecoctionDataset === 'printerConfig',
          'decoction-pail-table': activeDecoctionDataset === 'pails',
          'decoction-print-table': activeDecoctionDataset === 'cloudPrints',
          'decoction-work-table': activeDecoctionDataset === 'workRecords',
        }"
      >
        <thead>
          <tr v-if="activeDecoctionDataset === 'binds'" class="legacy-main-head">
            <th>任务ID</th>
            <th>任务编号</th>
            <th>绑定类型</th>
            <th>设备编号</th>
            <th>水桶号</th>
            <th>处方号</th>
            <th>订单号</th>
            <th>应加水量</th>
            <th>真实加水量</th>
            <th>当前状态</th>
            <th>数据状态</th>
            <th>操作人</th>
            <th>创建/修改时间</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'devices'" class="legacy-main-head">
            <th>设备编号</th>
            <th>设备名称</th>
            <th>设备类型</th>
            <th>PDA 编号</th>
            <th>打码机编号</th>
            <th>设备组别</th>
            <th>设备状态</th>
            <th>使用状态</th>
            <th>活动任务</th>
            <th>煎煮中心</th>
            <th>操作人</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'printerConfig'" class="legacy-main-head">
            <th>ID</th>
            <th>PDA 编号</th>
            <th>设备编号</th>
            <th>设备名称</th>
            <th>打码机编号</th>
            <th>打印模板</th>
            <th>状态</th>
            <th>修改时间</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'pails'" class="legacy-main-head">
            <th>ID</th>
            <th>加水桶号</th>
            <th>煎煮中心</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>修改时间</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'cloudPrints'" class="legacy-main-head">
            <th>任务号</th>
            <th>处方号</th>
            <th>关联剂数</th>
            <th>设备编码</th>
            <th>水桶/来源</th>
            <th>动作结果</th>
            <th>操作人</th>
            <th>作业时间</th>
          </tr>
          <tr v-else class="legacy-main-head">
            <th>作业任务</th>
            <th>处方号</th>
            <th>设备/水桶</th>
            <th>动作</th>
            <th>结果</th>
            <th>状态变化</th>
            <th>来源</th>
            <th>操作人/时间</th>
            <th>作业内容</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="mainLoading" class="legacy-main-info">
            <td :colspan="activeDecoctionTableColspan" class="legacy-empty">正在查询煎煮数据</td>
          </tr>

          <template v-else-if="activeDecoctionDataset === 'binds'">
            <tr v-if="filteredDecoctionTasks.length === 0" class="legacy-main-info">
              <td colspan="14" class="legacy-empty">暂无处方设备绑定记录</td>
            </tr>
            <tr v-for="task in filteredDecoctionTasks" :key="task.taskId" class="legacy-main-info">
              <td>{{ task.taskId }}</td>
              <td>{{ task.taskNo }}</td>
              <td>{{ bindType(task) }}</td>
              <td>{{ rowValue(task.deviceCode) }}</td>
              <td>{{ rowValue(task.pailNo) }}</td>
              <td>{{ task.prescriptionNo }}</td>
              <td>{{ task.orderNo }}</td>
              <td>待接口</td>
              <td>待接口</td>
              <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
              <td>{{ taskDataStatus(task) }}</td>
              <td>{{ rowValue(task.operator) }}</td>
              <td>
                <strong>{{ formatDate(task.createdAt) }}</strong>
                <small>{{ formatDate(task.updatedAt) }}</small>
              </td>
              <td class="decoction-action-cell">
                <button class="legacy-link-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleTaskEvent(task, 'water')">加水</button>
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleMesTask(task, 'start')">开始</button>
                <button class="legacy-link-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleTaskEvent(task, 'temperature')">温度</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo" @click="handleTaskEvent(task, 'error')">异常</button>
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleMesTask(task, 'finish')">完成</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleMesTask(task, 'cancel')">取消</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleMesTask(task, 'terminate')">终止</button>
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handlePdaTask(task, 'start')">PDA开始</button>
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handlePdaTask(task, 'finish')">PDA完成</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handlePdaTask(task, 'cancel')">PDA取消</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handlePdaTask(task, 'terminate')">PDA终止</button>
                <button class="legacy-link-btn" type="button" :disabled="eventLoading" @click="refreshTaskEvents(task.taskNo)">事件/记录</button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'devices'">
            <tr v-if="filteredDecoctionDevices.length === 0" class="legacy-main-info">
              <td colspan="12" class="legacy-empty">暂无设备</td>
            </tr>
            <tr v-for="device in filteredDecoctionDevices" :key="device.deviceCode" class="legacy-main-info">
              <td>{{ device.deviceCode }}</td>
              <td>{{ rowValue(device.deviceName) }}</td>
              <td>{{ rowValue(device.deviceType) }}</td>
              <td>{{ rowValue(device.pdaCode) }}</td>
              <td>{{ rowValue(device.printerCode) }}</td>
              <td>{{ rowValue(device.deviceGroup) }}</td>
              <td><StatusPill :value="device.deviceStatus" :tone="statusTone(device.deviceStatus)" /></td>
              <td>{{ deviceUseStatus(device) }}</td>
              <td>{{ rowValue(device.activeTaskNo || device.activePrescriptionNo) }}</td>
              <td>{{ rowValue(device.decoctionCenter) }}</td>
              <td>{{ rowValue(device.remark) }}</td>
              <td class="decoction-action-cell">
                <button class="legacy-link-btn" type="button" :disabled="deviceSaving" @click="openEditDeviceForm(device)">编辑</button>
                <button class="legacy-link-btn" type="button" :disabled="deviceSaving || device.enabled" @click="toggleDeviceEnabled(device, true)">启用</button>
                <button
                  class="legacy-link-btn workflow-reject-btn"
                  type="button"
                  :disabled="deviceSaving || !device.enabled || Boolean(device.activeTaskNo)"
                  @click="toggleDeviceEnabled(device, false)"
                >
                  停用
                </button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'printerConfig'">
            <tr v-if="filteredDecoctionDevices.length === 0" class="legacy-main-info">
              <td colspan="9" class="legacy-empty">暂无设备打印配置</td>
            </tr>
            <tr v-for="device in filteredDecoctionDevices" :key="`printer-${device.deviceCode}`" class="legacy-main-info">
              <td>{{ rowValue(device.deviceId) }}</td>
              <td>{{ rowValue(device.pdaCode) }}</td>
              <td>{{ rowValue(device.deviceCode) }}</td>
              <td>{{ rowValue(device.deviceName) }}</td>
              <td>{{ rowValue(device.printerCode) }}</td>
              <td>{{ rowValue(device.printTemplateCode) }}</td>
              <td><StatusPill :value="device.enabled ? '启用' : '停用'" :tone="statusTone(device.deviceStatus)" /></td>
              <td>{{ formatDate(device.updatedAt) }}</td>
              <td class="decoction-action-cell">
                <button class="legacy-link-btn" type="button" :disabled="deviceSaving" @click="openEditDeviceForm(device)">编辑</button>
                <button
                  class="legacy-link-btn workflow-reject-btn"
                  type="button"
                  :disabled="deviceSaving || !device.enabled || Boolean(device.activeTaskNo)"
                  @click="toggleDeviceEnabled(device, false)"
                >
                  停用
                </button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'pails'">
            <tr v-if="filteredWaterPailRows.length === 0" class="legacy-main-info">
              <td colspan="7" class="legacy-empty">暂无当前绑定加水桶</td>
            </tr>
            <tr v-for="(row, index) in filteredWaterPailRows" :key="row.pailNo" class="legacy-main-info">
              <td>{{ index + 1 }}</td>
              <td>{{ row.pailNo }}</td>
              <td>待接口</td>
              <td><StatusPill :value="row.task.taskStatus" :tone="statusTone(row.task.taskStatus)" /></td>
              <td>{{ formatDate(row.task.createdAt) }}</td>
              <td>{{ formatDate(row.task.updatedAt) }}</td>
              <td class="decoction-action-cell">
                <button class="legacy-link-btn" type="button" disabled title="等待后端管理契约">编辑</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" disabled title="等待后端管理契约">停用</button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'cloudPrints'">
            <tr v-if="filteredCloudPrintRecords.length === 0" class="legacy-main-info">
              <td colspan="8" class="legacy-empty">暂无煎煮作业/打印相关记录</td>
            </tr>
            <tr
              v-for="record in filteredCloudPrintRecords"
              :key="`${record.taskNo}-${record.actionType}-${record.actionTime}`"
              class="legacy-main-info"
            >
              <td>{{ record.taskNo }}</td>
              <td>{{ rowValue(record.prescriptionNo) }}</td>
              <td>{{ record.doseCount }}</td>
              <td>{{ rowValue(record.deviceCode) }}</td>
              <td>
                <strong>{{ rowValue(record.pailNo) }}</strong>
                <small>{{ rowValue(record.source) }}</small>
              </td>
              <td>
                <strong>{{ record.actionType }}</strong>
                <small>{{ record.actionResult }}</small>
              </td>
              <td>{{ rowValue(record.operator) }}</td>
              <td>{{ formatDate(record.actionTime) }}</td>
            </tr>
          </template>

          <template v-else>
            <tr v-if="filteredDecoctionWorkRecords.length === 0" class="legacy-main-info">
              <td colspan="9" class="legacy-empty">请选择任务查看作业记录，或等待任务产生真实作业明细</td>
            </tr>
            <tr v-for="record in filteredDecoctionWorkRecords" :key="record.recordId" class="legacy-main-info">
              <td>
                <strong>{{ record.taskNo }}</strong>
                <small>{{ record.operationId }}</small>
              </td>
              <td>{{ record.prescriptionNo }}</td>
              <td>
                <strong>{{ rowValue(record.deviceCode) }}</strong>
                <small>{{ rowValue(record.pailNo) }}</small>
              </td>
              <td><StatusPill :value="record.actionType" :tone="statusTone(record.actionType)" /></td>
              <td><StatusPill :value="record.actionResult" :tone="statusTone(record.actionResult)" /></td>
              <td>
                <strong>{{ rowValue(record.taskStatusBefore) }} -> {{ rowValue(record.taskStatusAfter) }}</strong>
              </td>
              <td>{{ record.source }}</td>
              <td>
                <strong>{{ record.operator }}</strong>
                <small>{{ formatDate(record.actionTime) }}</small>
              </td>
              <td class="legacy-left"><code>{{ record.detailPayload }}</code></td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div class="decoction-management-actions" v-if="activeDecoctionDataset !== 'binds' && activeDecoctionDataset !== 'workRecords'">
      <button v-if="activeDecoctionDataset === 'devices'" class="legacy-btn legacy-btn-primary" type="button" :disabled="deviceSaving" @click="openCreateDeviceForm()">新增设备</button>
      <button v-if="activeDecoctionDataset === 'devices'" class="legacy-btn legacy-btn-export" type="button" :disabled="filteredDecoctionDevices.length === 0" @click="downloadDeviceCsv">导出设备</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-primary" type="button" :disabled="deviceSaving" @click="openCreateDeviceForm('煎药机')">新增配置</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-export" type="button" :disabled="filteredDecoctionDevices.length === 0" @click="downloadPrinterConfigCsv">导出当前配置</button>
      <button v-if="activeDecoctionDataset === 'pails'" class="legacy-btn legacy-btn-primary" type="button" disabled title="等待后端管理契约">批量新增</button>
      <button v-if="activeDecoctionDataset === 'pails'" class="legacy-btn legacy-btn-export" type="button" :disabled="filteredWaterPailRows.length === 0" @click="downloadWaterPailCsv">导出</button>
      <button v-if="activeDecoctionDataset === 'cloudPrints'" class="legacy-btn legacy-btn-primary" type="button" :disabled="cloudPrintLoading" @click="refreshCloudPrintRecords">
        {{ cloudPrintLoading ? '查询中' : '查询云打印记录' }}
      </button>
      <button v-if="activeDecoctionDataset === 'cloudPrints'" class="legacy-btn legacy-btn-export" type="button" :disabled="filteredCloudPrintRecords.length === 0" @click="downloadCloudPrintCsv">导出</button>
      <button v-if="activeDecoctionDataset === 'cloudPrints'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端补打契约">补打</button>
      <span v-if="activeDecoctionDataset === 'cloudPrints'">当前复用煎煮绩效明细查询作业/打印相关记录，不新增独立云打印流水或补打接口。</span>
      <span v-else-if="activeDecoctionDataset === 'devices'">设备管理已接入主数据接口；停用设备不可继续绑定新煎煮任务。</span>
      <span v-else-if="activeDecoctionDataset === 'printerConfig'">PDA、打码机和打印模板配置复用设备主数据接口维护。</span>
      <span v-else>等待后端管理契约，当前仅展示已有煎煮作业 API 返回的数据。</span>
    </div>

    <p class="legacy-page-summary">{{ pageSummary(activePageTotal) }}</p>

    <div class="legacy-panel decoction-event-panel">
      <div class="decoction-record-toolbar">
        <span>事件/作业记录</span>
        <select v-model="selectedEventTaskNo" class="legacy-input input-large">
          <option value="">选择任务</option>
          <option v-for="task in decoctionTasks" :key="task.taskNo" :value="task.taskNo">
            {{ task.taskNo }} / {{ task.prescriptionNo }} / {{ task.taskStatus }}
          </option>
        </select>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="eventLoading || !selectedEventTaskNo" @click="refreshTaskEvents()">
          {{ eventLoading ? '加载中' : '刷新记录' }}
        </button>
        <button class="legacy-btn legacy-btn-export" type="button" :disabled="decoctionEvents.length === 0" @click="downloadEventCsv">导出事件</button>
        <button class="legacy-btn legacy-btn-export" type="button" :disabled="filteredDecoctionWorkRecords.length === 0" @click="downloadWorkRecordCsv">导出作业</button>
        <span class="decoction-selected-task">
          当前任务：{{ selectedEventTask ? `${selectedEventTask.taskNo} / ${selectedEventTask.prescriptionNo}` : '-' }}
        </span>
      </div>

      <div class="decoction-record-grid">
        <table class="legacy-main-table decoction-event-record-table">
          <thead>
            <tr class="legacy-main-head">
              <th>事件任务</th>
              <th>事件类型</th>
              <th>操作人</th>
              <th>事件时间</th>
              <th>事件内容</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="decoctionEvents.length === 0" class="legacy-main-info">
              <td colspan="5" class="legacy-empty">暂无事件记录</td>
            </tr>
            <tr v-for="event in decoctionEvents" :key="event.eventId" class="legacy-main-info">
              <td>
                <strong>{{ event.taskNo }}</strong>
                <small>{{ event.operationId }}</small>
              </td>
              <td><StatusPill :value="event.eventType" :tone="statusTone(event.eventType)" /></td>
              <td>{{ event.operator }}</td>
              <td>{{ formatDate(event.eventTime) }}</td>
              <td class="legacy-left"><code>{{ event.eventPayload }}</code></td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table decoction-work-record-table">
          <thead>
            <tr class="legacy-main-head">
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
            <tr v-if="filteredDecoctionWorkRecords.length === 0" class="legacy-main-info">
              <td colspan="7" class="legacy-empty">暂无作业记录</td>
            </tr>
            <tr v-for="record in filteredDecoctionWorkRecords" :key="record.recordId" class="legacy-main-info">
              <td>
                <strong>{{ record.taskNo }}</strong>
                <small>{{ record.operationId }}</small>
              </td>
              <td>
                <StatusPill :value="record.actionType" :tone="statusTone(record.actionResult)" />
                <small>{{ record.actionResult }}</small>
              </td>
              <td>
                <strong>{{ rowValue(record.taskStatusBefore) }} -> {{ rowValue(record.taskStatusAfter) }}</strong>
                <small>{{ record.prescriptionNo }}</small>
              </td>
              <td>
                <strong>{{ rowValue(record.deviceCode) }}</strong>
                <small>{{ record.source }}</small>
              </td>
              <td>{{ record.operator }}</td>
              <td>{{ formatDate(record.actionTime) }}</td>
              <td class="legacy-left"><code>{{ record.detailPayload }}</code></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<style scoped>
.decoction-filter-tip {
  flex-basis: 100%;
  color: var(--admin-muted);
}

.decoction-operate-search {
  border-color: rgba(35, 104, 181, 0.22);
}

.decoction-stats {
  margin-bottom: 10px;
}

.decoction-device-form {
  margin-bottom: 12px;
  padding: 12px 14px;
}

.decoction-device-form-head,
.decoction-device-form-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.decoction-device-form-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.decoction-device-form-grid label {
  display: grid;
  gap: 5px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.decoction-device-enabled {
  align-content: end;
  grid-template-columns: auto 1fr;
}

.decoction-device-form-actions {
  justify-content: flex-end;
  margin-top: 12px;
}

.decoction-main-table {
  min-width: 1720px;
}

.decoction-device-table {
  min-width: 1380px;
}

.decoction-printer-table,
.decoction-pail-table,
.decoction-print-table {
  min-width: 1120px;
}

.decoction-work-table {
  min-width: 1420px;
}

.decoction-action-cell {
  min-width: 260px;
  white-space: normal;
}

.decoction-action-cell .legacy-link-btn {
  margin: 2px 3px;
}

.decoction-management-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 10px 0 0;
  color: var(--admin-muted);
  font-size: 13px;
}

.decoction-event-panel {
  margin-top: 12px;
}

.decoction-record-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid var(--admin-line-soft);
  color: #324258;
  font-size: 13px;
  font-weight: 700;
}

.decoction-selected-task {
  color: var(--admin-muted);
  font-weight: 500;
}

.decoction-record-grid {
  display: grid;
  grid-template-columns: minmax(680px, 1fr) minmax(760px, 1.1fr);
  gap: 0;
  overflow: auto;
}

.decoction-event-record-table,
.decoction-work-record-table {
  min-width: 0;
  border-radius: 0;
}

.legacy-main-table small {
  display: block;
  margin-top: 2px;
  color: var(--admin-muted);
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 1200px) {
  .decoction-device-form-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  .decoction-record-grid {
    grid-template-columns: minmax(900px, 1fr);
  }
}
</style>
