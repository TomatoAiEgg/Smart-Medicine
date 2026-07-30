<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  closeDeadLetter,
  listDeadLetters,
  listIntegrationRetryIssues,
  listLogisticsCallbackIssues,
  replayDeadLetter,
} from '../../api/ops';
import type {
  DeadLetterRecord,
  IntegrationRetryIssueRecord,
  LogisticsCallbackIssueRecord,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, displayValue, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type ExceptionDataset = 'deadLetters' | 'callbackIssues' | 'integrationIssues';

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

const activeDataset = ref<ExceptionDataset>('deadLetters');
const limit = ref(50);
const status = ref('');
const topic = ref('');
const eventId = ref('');
const callbackStatus = ref('');
const callbackType = ref('');
const businessId = ref('');
const orderNo = ref('');
const taskStatus = ref('');
const taskType = ref('');
const businessKey = ref('');
const sourceSystem = ref('');
const operator = computed({
  get: () => props.operationOperator,
  set: (value: string) => emit('update:operationOperator', value),
});
const operationRemark = ref('');
const loading = ref(false);
const submittingId = ref('');
const errorLine = ref('');
const requestId = ref(0);
const deadLetters = ref<DeadLetterRecord[]>([]);
const callbackIssues = ref<LogisticsCallbackIssueRecord[]>([]);
const integrationIssues = ref<IntegrationRetryIssueRecord[]>([]);

const datasetLabels: Record<ExceptionDataset, string> = {
  deadLetters: '死信消息',
  callbackIssues: '物流回调失败',
  integrationIssues: '集成重试失败',
};
const datasetTabs: { key: ExceptionDataset; label: string }[] = [
  { key: 'deadLetters', label: datasetLabels.deadLetters },
  { key: 'callbackIssues', label: datasetLabels.callbackIssues },
  { key: 'integrationIssues', label: datasetLabels.integrationIssues },
];

const currentCount = computed(() => {
  if (activeDataset.value === 'deadLetters') return deadLetters.value.length;
  if (activeDataset.value === 'callbackIssues') return callbackIssues.value.length;
  return integrationIssues.value.length;
});

function downloadExceptionCsv() {
  if (activeDataset.value === 'deadLetters') {
    downloadCsv(
      `异常日志-死信消息-${limit.value}条.csv`,
      ['事件ID', '聚合对象', 'Topic', 'Tag', '消费者', '状态', '错误信息', '重试次数', '操作人', '备注', '创建时间', '更新时间'],
      deadLetters.value.map((record) => [
        record.eventId,
        record.aggregateId,
        record.topic,
        record.tag,
        record.consumerGroup,
        record.status,
        record.errorMessage,
        record.retryCount,
        record.operator,
        record.remark,
        formatDate(record.createdAt),
        formatDate(record.updatedAt),
      ]),
    );
  } else if (activeDataset.value === 'callbackIssues') {
    downloadCsv(
      `异常日志-物流回调失败-${limit.value}条.csv`,
      [
        '回调ID',
        '回调类型',
        '订单号',
        '业务ID',
        '物流公司',
        '物流单号',
        '回调状态',
        '物流状态',
        '重试次数',
        '下次重试',
        '请求地址',
        '响应内容',
        '最新轨迹状态',
        '最新轨迹内容',
        '最新轨迹时间',
        '创建时间',
        '更新时间',
      ],
      callbackIssues.value.map((record) => [
        record.callbackId,
        record.callbackType,
        record.orderNo,
        record.businessId,
        record.logisticsCompany,
        record.logisticsNo,
        record.callbackStatus,
        record.logisticsStatus,
        record.retryCount,
        formatDate(record.nextRetryAt),
        record.requestUrl,
        record.responseBody,
        record.latestTraceStatus,
        record.latestTraceContent,
        formatDate(record.latestTraceTime),
        formatDate(record.callbackCreatedAt),
        formatDate(record.callbackUpdatedAt),
      ]),
    );
  } else {
    downloadCsv(
      `异常日志-集成重试失败-${limit.value}条.csv`,
      [
        '任务ID',
        '消息ID',
        '任务类型',
        '来源系统',
        '目标系统',
        '来源类型',
        '业务键',
        '请求地址',
        '响应内容',
        '失败原因',
        '任务状态',
        '重试次数',
        '下次重试',
        '消息类型',
        '处理状态',
        '外部消息ID',
        '处理时间',
        '创建时间',
        '更新时间',
      ],
      integrationIssues.value.map((record) => [
        record.taskId,
        record.messageId,
        record.taskType,
        record.sourceSystem,
        record.targetSystem,
        record.sourceType,
        record.businessKey,
        record.requestUrl,
        record.responseBody,
        record.failureReason,
        record.taskStatus,
        record.retryCount,
        formatDate(record.nextRetryAt),
        record.messageType,
        record.processStatus,
        record.externalMessageId,
        formatDate(record.processedAt),
        formatDate(record.taskCreatedAt),
        formatDate(record.taskUpdatedAt),
      ]),
    );
  }
  emit('notice', 'success', `已导出${datasetLabels[activeDataset.value]} ${formatNumber(currentCount.value)} 条`);
}

function normalizedLimit() {
  return boundedPositiveInteger(limit.value, 50, 200);
}

function clearCurrentRecords() {
  if (activeDataset.value === 'deadLetters') deadLetters.value = [];
  if (activeDataset.value === 'callbackIssues') callbackIssues.value = [];
  if (activeDataset.value === 'integrationIssues') integrationIssues.value = [];
}

async function refreshExceptionLogs() {
  const nextRequestId = requestId.value + 1;
  requestId.value = nextRequestId;
  loading.value = true;
  errorLine.value = '';
  limit.value = normalizedLimit();
  try {
    if (activeDataset.value === 'deadLetters') {
      deadLetters.value = await listDeadLetters({
        status: status.value,
        topic: topic.value,
        eventId: eventId.value,
        limit: limit.value,
      });
    } else if (activeDataset.value === 'callbackIssues') {
      callbackIssues.value = await listLogisticsCallbackIssues({
        callbackStatus: callbackStatus.value,
        callbackType: callbackType.value,
        businessId: businessId.value,
        orderNo: orderNo.value,
        limit: limit.value,
      });
    } else {
      integrationIssues.value = await listIntegrationRetryIssues({
        taskStatus: taskStatus.value,
        taskType: taskType.value,
        businessKey: businessKey.value,
        sourceSystem: sourceSystem.value,
        limit: limit.value,
      });
    }
    if (nextRequestId !== requestId.value) return;
    emit('countChanged', currentCount.value);
    emit('notice', 'info', `已刷新${datasetLabels[activeDataset.value]}：${currentCount.value} 条`);
  } catch (error) {
    if (nextRequestId === requestId.value) {
      clearCurrentRecords();
      errorLine.value = errorMessage(error);
      emit('countChanged', 0);
    }
  } finally {
    if (nextRequestId === requestId.value) {
      loading.value = false;
    }
  }
}

function switchDataset(dataset: ExceptionDataset) {
  activeDataset.value = dataset;
  void refreshExceptionLogs();
}

function resetFilters() {
  status.value = '';
  topic.value = '';
  eventId.value = '';
  callbackStatus.value = '';
  callbackType.value = '';
  businessId.value = '';
  orderNo.value = '';
  taskStatus.value = '';
  taskType.value = '';
  businessKey.value = '';
  sourceSystem.value = '';
  void refreshExceptionLogs();
}

async function replayDeadLetterRecord(record: DeadLetterRecord) {
  submittingId.value = record.id;
  errorLine.value = '';
  try {
    const result = await replayDeadLetter(record.id, {
      operator: operator.value.trim() || 'admin',
      remark: operationRemark.value.trim() || '异常日志信息查询页面重放死信',
    });
    emit('notice', 'success', `${result.eventId} 已重放，重置 Outbox ${result.outboxResetCount} 条`);
    await refreshExceptionLogs();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submittingId.value = '';
  }
}

async function closeDeadLetterRecord(record: DeadLetterRecord) {
  submittingId.value = record.id;
  errorLine.value = '';
  try {
    const result = await closeDeadLetter(record.id, {
      operator: operator.value.trim() || 'admin',
      remark: operationRemark.value.trim() || '异常日志信息查询页面关闭死信',
    });
    emit('notice', 'success', `${result.eventId} 已关闭`);
    await refreshExceptionLogs();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submittingId.value = '';
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshExceptionLogs();
  },
  { immediate: true },
);

defineExpose({
  refreshExceptionLogs,
});
</script>

<template>
  <section class="legacy-page exception-log-page">
    <ul class="legacy-search exception-log-tabs">
      <li v-for="dataset in datasetTabs" :key="dataset.key">
        <button
          class="legacy-link-btn"
          :class="{ active: activeDataset === dataset.key }"
          type="button"
          @click="switchDataset(dataset.key)"
        >
          {{ dataset.label }}
        </button>
      </li>
    </ul>

    <ul class="legacy-search exception-log-search">
      <template v-if="activeDataset === 'deadLetters'">
        <li>
          死信状态：
          <input v-model="status" class="legacy-input input-medium" placeholder="DEAD / REPLAYED" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          Topic：
          <input v-model="topic" class="legacy-input input-large" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          事件 ID：
          <input v-model="eventId" class="legacy-input input-large" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          操作备注：
          <input v-model="operationRemark" class="legacy-input input-large" placeholder="重放/关闭原因" />
        </li>
      </template>

      <template v-else-if="activeDataset === 'callbackIssues'">
        <li>
          回调状态：
          <input v-model="callbackStatus" class="legacy-input input-medium" placeholder="FAILED / DEAD" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          回调类型：
          <input v-model="callbackType" class="legacy-input input-medium" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          订单号：
          <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          业务 ID：
          <input v-model="businessId" class="legacy-input input-large" @keyup.enter="refreshExceptionLogs" />
        </li>
      </template>

      <template v-else>
        <li>
          任务状态：
          <input v-model="taskStatus" class="legacy-input input-medium" placeholder="FAILED / DEAD" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          任务类型：
          <input v-model="taskType" class="legacy-input input-medium" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          业务键：
          <input v-model="businessKey" class="legacy-input input-large" @keyup.enter="refreshExceptionLogs" />
        </li>
        <li>
          来源系统：
          <input v-model="sourceSystem" class="legacy-input input-medium" @keyup.enter="refreshExceptionLogs" />
        </li>
      </template>

      <li>
        条数：
        <input v-model.number="limit" class="legacy-input input-small" type="number" min="1" max="200" step="10" @keyup.enter="refreshExceptionLogs" />
      </li>
      <li>
        操作人：
        <input v-model="operator" class="legacy-input input-medium" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshExceptionLogs">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">重置</button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || currentCount === 0" @click="downloadExceptionCsv">
          导出当前结果
        </button>
      </li>
    </ul>

    <p class="exception-log-hint">
      当前页面聚合死信消息、物流回调失败和集成重试失败，用于替代第一版运维异常查询入口；不展示服务器异常堆栈和敏感请求内容。
    </p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <table v-if="activeDataset === 'deadLetters'" class="legacy-main-table exception-log-table">
      <thead>
        <tr class="legacy-main-head">
          <th>事件</th>
          <th>Topic/Tag</th>
          <th>消费者</th>
          <th>状态</th>
          <th>错误信息</th>
          <th>重试</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading" class="legacy-main-info">
          <td colspan="8" class="legacy-empty">正在查询死信消息</td>
        </tr>
        <tr v-else-if="deadLetters.length === 0" class="legacy-main-info">
          <td colspan="8" class="legacy-empty">暂无死信消息</td>
        </tr>
        <tr v-for="record in deadLetters" :key="record.id" class="legacy-main-info">
          <td>
            <strong>{{ record.eventId }}</strong>
            <small>{{ displayValue(record.aggregateId) }}</small>
          </td>
          <td>
            <strong>{{ displayValue(record.topic) }}</strong>
            <small>{{ displayValue(record.tag) }}</small>
          </td>
          <td>{{ displayValue(record.consumerGroup) }}</td>
          <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
          <td class="legacy-left exception-message">{{ displayValue(record.errorMessage) }}</td>
          <td>{{ record.retryCount }}</td>
          <td>{{ formatDate(record.updatedAt) }}</td>
          <td class="exception-actions">
            <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="submittingId === record.id" @click="replayDeadLetterRecord(record)">
              重放
            </button>
            <button class="legacy-link-btn danger-link" type="button" :disabled="submittingId === record.id" @click="closeDeadLetterRecord(record)">
              关闭
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <table v-else-if="activeDataset === 'callbackIssues'" class="legacy-main-table exception-log-table">
      <thead>
        <tr class="legacy-main-head">
          <th>回调</th>
          <th>订单/物流</th>
          <th>状态</th>
          <th>重试</th>
          <th>失败原因</th>
          <th>最新轨迹</th>
          <th>更新时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading" class="legacy-main-info">
          <td colspan="7" class="legacy-empty">正在查询物流回调失败</td>
        </tr>
        <tr v-else-if="callbackIssues.length === 0" class="legacy-main-info">
          <td colspan="7" class="legacy-empty">暂无物流回调失败</td>
        </tr>
        <tr v-for="record in callbackIssues" :key="record.callbackId" class="legacy-main-info">
          <td>
            <strong>{{ record.callbackType }}</strong>
            <small>{{ record.callbackId }}</small>
          </td>
          <td>
            <strong>{{ displayValue(record.orderNo) }}</strong>
            <small>{{ record.logisticsNo || record.businessId }}</small>
          </td>
          <td>
            <StatusPill :value="record.callbackStatus" :tone="statusTone(record.callbackStatus)" />
            <small>{{ displayValue(record.logisticsStatus) }}</small>
          </td>
          <td>
            <strong>{{ record.retryCount }}</strong>
            <small>{{ formatDate(record.nextRetryAt) }}</small>
          </td>
          <td class="legacy-left exception-message">{{ displayValue(record.responseBody || record.requestUrl) }}</td>
          <td>
            <strong>{{ displayValue(record.latestTraceStatus) }}</strong>
            <small>{{ displayValue(record.latestTraceContent) }}</small>
          </td>
          <td>{{ formatDate(record.callbackUpdatedAt) }}</td>
        </tr>
      </tbody>
    </table>

    <table v-else class="legacy-main-table exception-log-table">
      <thead>
        <tr class="legacy-main-head">
          <th>任务</th>
          <th>来源/目标</th>
          <th>业务键</th>
          <th>状态</th>
          <th>重试</th>
          <th>失败原因</th>
          <th>消息状态</th>
          <th>更新时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading" class="legacy-main-info">
          <td colspan="8" class="legacy-empty">正在查询集成重试失败</td>
        </tr>
        <tr v-else-if="integrationIssues.length === 0" class="legacy-main-info">
          <td colspan="8" class="legacy-empty">暂无集成重试失败</td>
        </tr>
        <tr v-for="record in integrationIssues" :key="record.taskId" class="legacy-main-info">
          <td>
            <strong>{{ record.taskType }}</strong>
            <small>{{ record.taskId }}</small>
          </td>
          <td>
            <strong>{{ record.sourceSystem }} -> {{ record.targetSystem }}</strong>
            <small>{{ record.sourceType }}</small>
          </td>
          <td>{{ displayValue(record.businessKey) }}</td>
          <td><StatusPill :value="record.taskStatus" :tone="statusTone(record.taskStatus)" /></td>
          <td>
            <strong>{{ record.retryCount }}</strong>
            <small>{{ formatDate(record.nextRetryAt) }}</small>
          </td>
          <td class="legacy-left exception-message">{{ displayValue(record.responseBody || record.failureReason || record.requestUrl) }}</td>
          <td>
            <StatusPill :value="record.processStatus" :tone="statusTone(record.processStatus)" />
            <small>{{ record.messageType }}</small>
          </td>
          <td>{{ formatDate(record.taskUpdatedAt) }}</td>
        </tr>
      </tbody>
    </table>

    <div class="page_and_btn">
      <div class="dataTables_info">显示第 {{ currentCount > 0 ? 1 : 0 }} 至 {{ currentCount }} 项记录，共 {{ currentCount }} 项</div>
    </div>
  </section>
</template>

<style scoped>
.exception-log-tabs {
  margin-bottom: 10px;
}

.exception-log-tabs .legacy-link-btn.active {
  background: #1f6feb;
  border-color: #1f6feb;
  color: #fff;
}

.exception-log-search {
  row-gap: 10px;
}

.exception-log-hint {
  margin: 0 0 10px;
  color: #6f7d91;
  font-size: 13px;
}

.exception-log-table {
  min-width: 1160px;
}

.exception-message {
  max-width: 360px;
  white-space: normal;
}

.exception-actions {
  white-space: nowrap;
}

.exception-actions .legacy-link-btn + .legacy-link-btn {
  margin-left: 8px;
}

.danger-link {
  color: #b42318;
}
</style>
