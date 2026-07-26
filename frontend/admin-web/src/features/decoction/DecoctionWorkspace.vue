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
  listTaskEvents,
  recordTaskError,
  recordTemperature,
  recordWaterFinished,
  startMesTask,
  terminateMesTask,
} from '../../api/decoction';
import type {
  DecoctionTaskEventRecord,
  DecoctionTaskRecord,
  DeviceRecord,
  DeviceWorkRecord,
  PrescriptionRecord,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type DecoctionDataset = 'binds' | 'devices' | 'pails' | 'prints' | 'events';

const props = defineProps<{
  active: boolean;
  activationKey: number;
  operationOperator: string;
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
const decoctionEvents = ref<DecoctionTaskEventRecord[]>([]);
const decoctionWorkRecords = ref<DeviceWorkRecord[]>([]);
const decoctionLoading = ref(false);
const decoctionError = ref('');
const activeDecoctionDataset = ref<DecoctionDataset>('binds');
const startTime = ref('2026-07-14 11:00:00');
const endTime = ref('2026-07-21 12:00:00');
const decoctionCenter = ref('良益堂煎药中心');
const prescriptionNoQuery = ref('');
const deviceCodeQuery = ref('');
const deviceType = ref('');
const deviceGroup = ref('');
const deviceStatus = ref('');
const printStatus = ref('');
const selectedPrescriptionNo = ref('');
const selectedDeviceCode = ref('');
const pailNo = ref('PAIL-001');
const waterVolumeMl = ref(1200);
const temperatureCelsius = ref(98);
const durationSeconds = ref(600);
const eventRemark = ref('');
const selectedEventTaskNo = ref('');
const handlingDecoctionTaskNo = ref('');

const activeDecoctionCount = computed(() => decoctionTasks.value.length);

const activeDecoctionTableColspan = computed(() => {
  if (activeDecoctionDataset.value === 'devices') return 14;
  if (activeDecoctionDataset.value === 'pails') return 4;
  if (activeDecoctionDataset.value === 'prints') return 8;
  if (activeDecoctionDataset.value === 'events') return 7;
  return 12;
});

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
  return task.pailNo ? '加水桶' : '设备';
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
    emit('notice', 'info', `已刷新煎煮模拟：可操作处方 ${prescriptions.value.length} 条，活动任务 ${decoctionTasks.value.length} 条`);
  } catch (error) {
    prescriptions.value = [];
    decoctionDevices.value = [];
    decoctionTasks.value = [];
    decoctionError.value = errorMessage(error);
  } finally {
    decoctionLoading.value = false;
  }
}

async function handleBindPrescription() {
  if (!operatorModel.value.trim()) {
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
      operator: operatorModel.value.trim(),
      timestamp: new Date().toISOString(),
      sign: 'dev-sign',
    });
    emit('notice', 'success', `${result.prescriptionNo} 已绑定设备 ${result.deviceCode}`);
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
  if (!operatorModel.value.trim()) {
    decoctionError.value = '操作人不能为空';
    return;
  }

  handlingDecoctionTaskNo.value = task.taskNo;
  decoctionError.value = '';
  try {
    const command = {
      operationId: newOperationId(`mes-${action}`),
      operator: operatorModel.value.trim(),
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
    emit('notice', 'success', `${result.taskNo} 已${actionText[action]}`);
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
    operator: operatorModel.value.trim(),
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
  if (!operatorModel.value.trim()) {
    decoctionError.value = '操作人不能为空';
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
      await recordTaskError(task.taskNo, eventCommand('mes-error', { reason: eventRemark.value.trim() || 'manual error' }));
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

defineExpose({
  refreshDecoctionSimulator,
});
</script>

<template>
  <section class="legacy-page decoction-page">
    <ul class="legacy-search decoction-mode-search">
      <li class="logistics-mode-item">
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'binds' }" type="button" @click="switchDecoctionDataset('binds')">
          处方设备绑定列表
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'devices' }" type="button" @click="switchDecoctionDataset('devices')">
          设备列表查询
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'pails' }" type="button" @click="switchDecoctionDataset('pails')">
          加水桶管理
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'prints' }" type="button" @click="switchDecoctionDataset('prints')">
          云打印记录列表
        </button>
        <button class="legacy-link-btn" :class="{ active: activeDecoctionDataset === 'events' }" type="button" @click="switchDecoctionDataset('events')">
          作业记录
        </button>
      </li>
    </ul>

    <ul class="legacy-search decoction-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" />
      </li>
      <li>
        煎煮中心：
        <select v-model="decoctionCenter" class="legacy-input input-large">
          <option value="良益堂煎药中心">良益堂煎药中心</option>
          <option value="良益堂煎煮中心">良益堂煎煮中心</option>
        </select>
      </li>
      <li>
        处方号：
        <input v-model="prescriptionNoQuery" class="legacy-input input-large" />
      </li>
      <li>
        设备编号：
        <input v-model="deviceCodeQuery" class="legacy-input" />
      </li>
      <li v-if="activeDecoctionDataset === 'devices'">
        设备类型：
        <select v-model="deviceType" class="legacy-input">
          <option value="">请选择</option>
          <option value="煎药机">煎药机</option>
          <option value="包装机">包装机</option>
          <option value="打码机">打码机</option>
        </select>
      </li>
      <li v-if="activeDecoctionDataset === 'devices'">
        设备组别：
        <input v-model="deviceGroup" class="legacy-input" />
      </li>
      <li v-if="activeDecoctionDataset === 'devices'">
        状态：
        <select v-model="deviceStatus" class="legacy-input">
          <option value="">请选择</option>
          <option value="ONLINE">在线</option>
          <option value="OFFLINE">离线</option>
          <option value="BUSY">占用</option>
        </select>
      </li>
      <li v-if="activeDecoctionDataset === 'prints'">
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
            {{ device.deviceCode }} / {{ device.deviceStatus }}
          </option>
        </select>
      </li>
      <li>
        水桶号：
        <input v-model="pailNo" class="legacy-input" placeholder="PAIL-001" />
      </li>
      <li>
        加水量 ml：
        <input v-model.number="waterVolumeMl" class="legacy-input input-small" type="number" min="0" step="50" />
      </li>
      <li>
        温度 ℃：
        <input v-model.number="temperatureCelsius" class="legacy-input input-small" type="number" min="0" step="1" />
      </li>
      <li>
        时长秒：
        <input v-model.number="durationSeconds" class="legacy-input input-small" type="number" min="0" step="30" />
      </li>
      <li>
        事件备注：
        <input v-model="eventRemark" class="legacy-input input-large" placeholder="异常原因或事件备注" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="decoctionLoading" @click="handleBindPrescription">
          绑定
        </button>
      </li>
    </ul>

    <p v-if="decoctionError" class="error-line">{{ decoctionError }}</p>

    <div class="legacy-panel">
      <table
        class="legacy-main-table decoction-main-table"
        :class="{
          'decoction-device-table': activeDecoctionDataset === 'devices',
          'decoction-pail-table': activeDecoctionDataset === 'pails',
          'decoction-print-table': activeDecoctionDataset === 'prints',
          'decoction-event-table': activeDecoctionDataset === 'events',
        }"
      >
        <thead>
          <tr v-if="activeDecoctionDataset === 'binds'" class="legacy-main-head">
            <th>ID</th>
            <th>绑定类型</th>
            <th>绑定编号</th>
            <th>处方号</th>
            <th>应加水量</th>
            <th>真实加水量</th>
            <th>当前状态</th>
            <th>数据状态</th>
            <th>操作人</th>
            <th>创建时间</th>
            <th>修改时间</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'devices'" class="legacy-main-head">
            <th>ID</th>
            <th>设备类型</th>
            <th>设备编号</th>
            <th>设备名称</th>
            <th>设备序列号</th>
            <th>设备IP</th>
            <th>设备组别</th>
            <th>状态</th>
            <th>使用状态</th>
            <th>煎煮中心</th>
            <th>操作人</th>
            <th>创建时间</th>
            <th>修改时间</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'pails'" class="legacy-main-head">
            <th>ID</th>
            <th>加水桶号</th>
            <th>煎煮中心</th>
            <th>创建时间</th>
          </tr>
          <tr v-else-if="activeDecoctionDataset === 'prints'" class="legacy-main-head">
            <th>ID</th>
            <th>处方号</th>
            <th>打印张数</th>
            <th>打码机编码</th>
            <th>打码机名称</th>
            <th>打印状态</th>
            <th>备注</th>
            <th>创建时间</th>
          </tr>
          <tr v-else class="legacy-main-head">
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
          <tr v-if="decoctionLoading" class="legacy-main-info">
            <td :colspan="activeDecoctionTableColspan" class="legacy-empty">正在查询煎煮数据</td>
          </tr>

          <template v-else-if="activeDecoctionDataset === 'binds'">
            <tr v-if="decoctionTasks.length === 0" class="legacy-main-info">
              <td colspan="12" class="legacy-empty">暂无处方设备绑定记录</td>
            </tr>
            <tr v-for="task in decoctionTasks" :key="task.taskId" class="legacy-main-info">
              <td>{{ task.taskId }}</td>
              <td>{{ bindType(task) }}</td>
              <td>{{ rowValue(task.pailNo || task.deviceCode) }}</td>
              <td>{{ task.prescriptionNo }}</td>
              <td>{{ waterVolumeMl }}</td>
              <td>{{ task.taskStatus === 'WATER_FINISHED' ? waterVolumeMl : '-' }}</td>
              <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
              <td>{{ taskDataStatus(task) }}</td>
              <td>{{ rowValue(task.operator) }}</td>
              <td>{{ formatDate(task.createdAt) }}</td>
              <td>{{ formatDate(task.updatedAt) }}</td>
              <td class="decoction-action-cell">
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleStartDecoction(task)">开始</button>
                <button class="legacy-link-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleWaterFinished(task)">加水</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleMesTask(task, 'cancel')">取消</button>
                <button class="legacy-link-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleTemperature(task)">温度</button>
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleFinishDecoction(task)">完成</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleMesTask(task, 'terminate')">终止</button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo" @click="handleTaskError(task)">异常</button>
                <button class="legacy-link-btn" type="button" @click="refreshTaskEvents(task.taskNo)">事件</button>
              </td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'devices'">
            <tr v-if="decoctionDevices.length === 0" class="legacy-main-info">
              <td colspan="14" class="legacy-empty">暂无设备</td>
            </tr>
            <tr v-for="device in decoctionDevices" :key="device.deviceCode" class="legacy-main-info">
              <td>{{ device.deviceCode }}</td>
              <td>{{ rowValue(deviceType || '煎药机') }}</td>
              <td>{{ device.deviceCode }}</td>
              <td>{{ device.deviceName }}</td>
              <td>{{ device.deviceCode }}</td>
              <td>-</td>
              <td>{{ rowValue(deviceGroup) }}</td>
              <td><StatusPill :value="device.deviceStatus" :tone="statusTone(device.deviceStatus)" /></td>
              <td>{{ device.activeTaskNo ? '使用中' : '空闲' }}</td>
              <td>{{ decoctionCenter }}</td>
              <td>{{ operatorModel }}</td>
              <td>-</td>
              <td>-</td>
              <td><button class="legacy-link-btn" type="button" disabled>编辑</button></td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'pails'">
            <tr class="legacy-main-info">
              <td>{{ pailNo }}</td>
              <td>{{ pailNo }}</td>
              <td>{{ decoctionCenter }}</td>
              <td>-</td>
            </tr>
          </template>

          <template v-else-if="activeDecoctionDataset === 'prints'">
            <tr v-if="decoctionTasks.length === 0" class="legacy-main-info">
              <td colspan="8" class="legacy-empty">暂无云打印记录</td>
            </tr>
            <tr v-for="task in decoctionTasks" :key="task.taskId" class="legacy-main-info">
              <td>{{ task.taskId }}</td>
              <td>{{ task.prescriptionNo }}</td>
              <td>1</td>
              <td>{{ task.deviceCode }}</td>
              <td>{{ task.deviceCode }}</td>
              <td><StatusPill :value="printStatus || 'SUCCESS'" :tone="statusTone(printStatus || 'SUCCESS')" /></td>
              <td class="legacy-left">{{ rowValue(eventRemark) }}</td>
              <td>{{ formatDate(task.createdAt) }}</td>
            </tr>
          </template>

          <template v-else>
            <tr v-if="decoctionWorkRecords.length === 0" class="legacy-main-info">
              <td colspan="7" class="legacy-empty">暂无作业明细</td>
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
                <strong>{{ record.taskStatusBefore || '-' }} -> {{ record.taskStatusAfter || '-' }}</strong>
                <small>{{ record.prescriptionNo }}</small>
              </td>
              <td>
                <strong>{{ record.deviceCode }}</strong>
                <small>{{ record.source }}</small>
              </td>
              <td>{{ record.operator }}</td>
              <td>{{ formatDate(record.actionTime) }}</td>
              <td class="legacy-left"><code>{{ record.detailPayload }}</code></td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">
      {{ pageSummary(activeDecoctionDataset === 'devices' ? decoctionDevices.length : activeDecoctionDataset === 'events' ? decoctionWorkRecords.length : decoctionTasks.length) }}
    </p>

    <div class="legacy-panel decoction-event-panel">
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
    </div>
  </section>
</template>
