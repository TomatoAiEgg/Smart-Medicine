<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  bindPrescription,
  cancelMesTask,
  finishMesTask,
  listActiveMesTasks,
  listCanOperatePrescriptions,
  listDecoctionDevices,
  listDeviceWorkRecords,
  listPendingMesTasks,
  listTaskEvents,
  recordTaskError,
  recordTemperature,
  recordWaterFinished,
  startMesTask,
  terminateMesTask,
} from '../../api/decoction';
import type {
  DecoctionEventCommand,
  DecoctionTaskEventRecord,
  DecoctionTaskRecord,
  DeviceRecord,
  DeviceWorkRecord,
  MesTaskOperationCommand,
  PrescriptionRecord,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type DecoctionDataset = 'devices' | 'binds' | 'printerConfig' | 'pails' | 'cloudPrints' | 'workRecords';
type MesAction = 'start' | 'finish' | 'cancel' | 'terminate';
type TaskEventAction = 'water' | 'temperature' | 'error';
type EventCommandExtra = Partial<Omit<DecoctionEventCommand, 'operationId' | 'operator' | 'timestamp' | 'sign'>>;

const props = defineProps<{
  active: boolean;
  activationKey: number;
  operationOperator: string;
  routeKey: string;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
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
const decoctionLoading = ref(false);
const eventLoading = ref(false);
const decoctionError = ref('');
const activeDecoctionDataset = ref<DecoctionDataset>('binds');
const startTime = ref('');
const endTime = ref('');
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

const activeDecoctionCount = computed(() => decoctionTasks.value.length);
const pendingMesTaskCount = computed(() => pendingMesTasks.value.length);

const selectedEventTask = computed(() => (
  decoctionTasks.value.find((task) => task.taskNo === selectedEventTaskNo.value) ?? null
));

const activeDecoctionTableColspan = computed(() => {
  if (activeDecoctionDataset.value === 'devices') return 12;
  if (activeDecoctionDataset.value === 'printerConfig') return 8;
  if (activeDecoctionDataset.value === 'pails') return 7;
  if (activeDecoctionDataset.value === 'cloudPrints') return 8;
  if (activeDecoctionDataset.value === 'workRecords') return 9;
  return 14;
});

const activePageTotal = computed(() => {
  if (activeDecoctionDataset.value === 'devices') return decoctionDevices.value.length;
  if (activeDecoctionDataset.value === 'binds') return decoctionTasks.value.length;
  if (activeDecoctionDataset.value === 'workRecords') return decoctionWorkRecords.value.length;
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

function pageSummary(total: number) {
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${total} 项`;
}

function switchDecoctionDataset(dataset: DecoctionDataset) {
  activeDecoctionDataset.value = dataset;
}

function taskDataStatus(task: DecoctionTaskRecord) {
  return task.taskStatus === 'CANCELLED' || task.taskStatus === 'TERMINATED' ? '停用' : '正常';
}

function bindType(task: DecoctionTaskRecord) {
  return task.pailNo ? '水桶绑定' : '设备绑定';
}

function deviceUseStatus(device: DeviceRecord) {
  return device.activeTaskNo ? '使用中' : '空闲';
}

function makeMesCommand(prefix: string): MesTaskOperationCommand {
  return {
    operationId: newOperationId(prefix),
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
      listDecoctionDevices(),
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
    if (!decoctionDevices.value.some((device) => device.deviceCode === selectedDeviceCode.value)) {
      selectedDeviceCode.value = decoctionDevices.value[0]?.deviceCode ?? '';
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

watch(activeDecoctionCount, (count) => emit('countChanged', count), { immediate: true });

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && decoctionTasks.value.length === 0) {
      void refreshDecoctionSimulator();
    }
  },
  { immediate: true },
);

watch(
  () => props.routeKey,
  (routeKey) => {
    activeDecoctionDataset.value = datasetFromRouteKey(routeKey);
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
        <input v-model="startTime" class="legacy-input input-large" placeholder="等待后端筛选契约" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="等待后端筛选契约" />
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
        <input v-model="prescriptionNoQuery" class="legacy-input input-large" placeholder="等待后端筛选契约" />
      </li>
      <li>
        设备编号：
        <input v-model="deviceCodeQuery" class="legacy-input" placeholder="待接口" />
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
        <input v-model="deviceGroup" class="legacy-input" placeholder="待接口" />
      </li>
      <li>
        状态：
        <select v-model="deviceStatus" class="legacy-input">
          <option value="">请选择</option>
          <option value="ONLINE">在线</option>
          <option value="OFFLINE">离线</option>
          <option value="BUSY">占用</option>
        </select>
      </li>
      <li>
        打印状态：
        <select v-model="printStatus" class="legacy-input">
          <option value="">请选择</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
        </select>
      </li>
      <li>
        操作人：
        <input v-model="operatorModel" class="legacy-input" placeholder="admin" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="decoctionLoading" @click="refreshDecoctionSimulator">
          {{ decoctionLoading ? '查询中' : '查询' }}
        </button>
      </li>
      <li class="decoction-filter-tip">
        当前后端只支持无筛选列表；以上筛选项已保留，等待后端查询契约后接入。
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
          <option v-for="device in decoctionDevices" :key="device.deviceCode" :value="device.deviceCode">
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
            <th>设备序列号</th>
            <th>设备IP</th>
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
            <th>PDA编号</th>
            <th>打码机编号</th>
            <th>打码机名称</th>
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
            <th>ID</th>
            <th>处方号</th>
            <th>打印张数</th>
            <th>打码机编号</th>
            <th>打码机名称</th>
            <th>打印状态</th>
            <th>备注</th>
            <th>创建时间</th>
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
          <tr v-if="decoctionLoading" class="legacy-main-info">
            <td :colspan="activeDecoctionTableColspan" class="legacy-empty">正在查询煎煮数据</td>
          </tr>

          <template v-else-if="activeDecoctionDataset === 'binds'">
            <tr v-if="decoctionTasks.length === 0" class="legacy-main-info">
              <td colspan="14" class="legacy-empty">暂无处方设备绑定记录</td>
            </tr>
            <tr v-for="task in decoctionTasks" :key="task.taskId" class="legacy-main-info">
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
                <button class="legacy-link-btn" type="button" :disabled="eventLoading" @click="refreshTaskEvents(task.taskNo)">事件/记录</button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'devices'">
            <tr v-if="decoctionDevices.length === 0" class="legacy-main-info">
              <td colspan="12" class="legacy-empty">暂无设备</td>
            </tr>
            <tr v-for="device in decoctionDevices" :key="device.deviceCode" class="legacy-main-info">
              <td>{{ device.deviceCode }}</td>
              <td>{{ rowValue(device.deviceName) }}</td>
              <td>待接口</td>
              <td>-</td>
              <td>-</td>
              <td>待接口</td>
              <td><StatusPill :value="device.deviceStatus" :tone="statusTone(device.deviceStatus)" /></td>
              <td>{{ deviceUseStatus(device) }}</td>
              <td>{{ rowValue(device.activeTaskNo || device.activePrescriptionNo) }}</td>
              <td>待接口</td>
              <td>待接口</td>
              <td class="decoction-action-cell">
                <button class="legacy-link-btn" type="button" disabled title="等待后端管理契约">编辑</button>
                <button class="legacy-link-btn" type="button" disabled title="等待后端管理契约">启用</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" disabled title="等待后端管理契约">停用</button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'printerConfig'">
            <tr class="legacy-main-info">
              <td colspan="8" class="legacy-empty">等待后端管理契约，当前不展示本地配置或输入值</td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'pails'">
            <tr class="legacy-main-info">
              <td colspan="7" class="legacy-empty">等待后端加水桶管理契约，当前不展示本地输入的水桶号</td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'cloudPrints'">
            <tr class="legacy-main-info">
              <td colspan="8" class="legacy-empty">等待后端云打印记录契约，当前不使用作业任务冒充打印记录</td>
            </tr>
          </template>

          <template v-else>
            <tr v-if="decoctionWorkRecords.length === 0" class="legacy-main-info">
              <td colspan="9" class="legacy-empty">请选择任务查看作业记录，或等待任务产生真实作业明细</td>
            </tr>
            <tr v-for="record in decoctionWorkRecords" :key="record.recordId" class="legacy-main-info">
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
      <button v-if="activeDecoctionDataset === 'devices'" class="legacy-btn legacy-btn-primary" type="button" disabled title="等待后端管理契约">新增设备</button>
      <button v-if="activeDecoctionDataset === 'devices'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">删除设备</button>
      <button v-if="activeDecoctionDataset === 'devices'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">导出设备</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-primary" type="button" disabled title="等待后端管理契约">新增PDA</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-primary" type="button" disabled title="等待后端管理契约">新增打码机</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">编辑配置</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">删除配置</button>
      <button v-if="activeDecoctionDataset === 'printerConfig'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">停用配置</button>
      <button v-if="activeDecoctionDataset === 'pails'" class="legacy-btn legacy-btn-primary" type="button" disabled title="等待后端管理契约">批量新增</button>
      <button v-if="activeDecoctionDataset === 'pails'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">导出</button>
      <button v-if="activeDecoctionDataset === 'cloudPrints'" class="legacy-btn legacy-btn-primary" type="button" disabled title="等待后端管理契约">查询云打印记录</button>
      <button v-if="activeDecoctionDataset === 'cloudPrints'" class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端管理契约">补打</button>
      <span>等待后端管理契约，当前仅展示已有煎煮作业 API 返回的数据。</span>
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
            <tr v-if="decoctionWorkRecords.length === 0" class="legacy-main-info">
              <td colspan="7" class="legacy-empty">暂无作业记录</td>
            </tr>
            <tr v-for="record in decoctionWorkRecords" :key="record.recordId" class="legacy-main-info">
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
  .decoction-record-grid {
    grid-template-columns: minmax(900px, 1fr);
  }
}
</style>
