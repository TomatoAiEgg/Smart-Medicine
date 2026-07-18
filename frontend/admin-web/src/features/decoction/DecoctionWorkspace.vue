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

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function newOperationId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
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
  <section class="workspace">
    <div class="toolbar">
      <label>
        <span>操作人</span>
        <input v-model="operatorModel" placeholder="admin" />
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
          <option v-for="device in decoctionDevices" :key="device.deviceCode" :value="device.deviceCode">
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
              <button class="success" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleStartDecoction(task)">
                开始
              </button>
              <button class="secondary" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleWaterFinished(task)">
                加水
              </button>
              <button class="danger" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'BOUND'" @click="handleMesTask(task, 'cancel')">
                取消
              </button>
              <button class="secondary" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleTemperature(task)">
                温度
              </button>
              <button class="success" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleFinishDecoction(task)">
                完成
              </button>
              <button class="danger" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo || task.taskStatus !== 'DECOCTING'" @click="handleMesTask(task, 'terminate')">
                终止
              </button>
              <button class="danger" type="button" :disabled="handlingDecoctionTaskNo === task.taskNo" @click="handleTaskError(task)">
                异常
              </button>
              <button class="secondary" type="button" @click="refreshTaskEvents(task.taskNo)">
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
              <strong>{{ record.taskStatusBefore || '-' }} -> {{ record.taskStatusAfter || '-' }}</strong>
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
</template>
