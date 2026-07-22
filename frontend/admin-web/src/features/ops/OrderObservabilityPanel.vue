<script setup lang="ts">
import { computed, defineComponent, h, ref, watch, type PropType } from 'vue';
import { ApiError } from '../../api/client';
import { getOrderObservability } from '../../api/ops';
import type {
  ApiAccessLogRecord,
  DeadLetterRecord,
  EventOutboxRecord,
  IntegrationRetryIssueRecord,
  MessageConsumeRecord,
  OperationLogRecord,
  OpsCallbackRecord,
  OpsWorkflowTaskRecord,
  OrderObservabilityBundle,
  OrderStatusLogRecord,
  OrderValidationRecord,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const orderNo = ref('');
const externalOrderNo = ref('');
const limit = ref(50);
const loading = ref(false);
const errorText = ref('');
const bundle = ref<OrderObservabilityBundle | null>(null);

const failedConsumeStatuses = new Set(['FAILED', 'FAILED_RETRYABLE', 'FAILED_FATAL', 'DEAD']);
const failedCallbackStatuses = new Set(['FAILED', 'DEAD']);
const failedOutboxStatuses = new Set(['FAILED', 'PUBLISH_FAILED', 'DEAD']);
const openDeadLetterStatuses = new Set(['OPEN']);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function normalizedLimit() {
  if (!Number.isFinite(limit.value) || limit.value <= 0) return 50;
  return Math.min(Math.trunc(limit.value), 200);
}

function isFailedConsume(record: MessageConsumeRecord) {
  return failedConsumeStatuses.has(record.status);
}

function isFailedCallback(record: OpsCallbackRecord) {
  return failedCallbackStatuses.has(record.status);
}

function isFailedOutbox(record: EventOutboxRecord) {
  return failedOutboxStatuses.has(record.status);
}

function isOpenDeadLetter(record: DeadLetterRecord) {
  return openDeadLetterStatuses.has(record.status);
}

type EvidenceItem =
  | EventOutboxRecord
  | MessageConsumeRecord
  | OrderValidationRecord
  | IntegrationRetryIssueRecord
  | OperationLogRecord
  | ApiAccessLogRecord;

function itemTitle(item: EvidenceItem) {
  if ('eventType' in item) return item.eventType;
  if ('consumerGroup' in item) return item.consumerGroup;
  if ('validationStatus' in item) return item.validationStatus;
  if ('taskType' in item) return item.taskType;
  if ('action' in item) return item.action;
  return item.requestPath;
}

function itemSubtitle(item: EvidenceItem) {
  if ('eventId' in item && item.eventId) return item.eventId;
  if ('businessKey' in item) return item.businessKey || item.taskId;
  if ('operator' in item) return item.operator || item.id;
  if ('appKey' in item) return `${item.appKey} / ${item.requestIp}`;
  return 'id' in item ? item.id : '-';
}

function itemStatus(item: EvidenceItem) {
  if ('status' in item) return item.status;
  if ('validationStatus' in item) return item.validationStatus;
  if ('taskStatus' in item) return item.taskStatus;
  if ('result' in item) return item.result;
  return item.resultCode;
}

function itemDetail(item: EvidenceItem) {
  if ('lastError' in item) return item.lastError || item.aggregateId || '-';
  if ('validationMessage' in item) return item.validationMessage || '-';
  if ('responseBody' in item) return item.responseBody || item.failureReason || item.requestUrl;
  if ('reason' in item) return item.reason || item.eventId || '-';
  return item.resultCode;
}

function itemTime(item: EvidenceItem) {
  if ('updatedAt' in item && item.updatedAt) return item.updatedAt;
  if ('taskUpdatedAt' in item) return item.taskUpdatedAt;
  return item.createdAt;
}

const RecordTable = defineComponent({
  name: 'RecordTable',
  props: {
    title: { type: String, required: true },
    items: { type: Array as PropType<Array<EventOutboxRecord | MessageConsumeRecord>>, required: true },
  },
  setup(props) {
    return () => h('div', { class: 'table-wrap ops-table' }, [
      h('table', [
        h('thead', [h('tr', [
          h('th', props.title),
          h('th', '状态'),
          h('th', '重试'),
          h('th', '错误/对象'),
          h('th', '时间'),
        ])]),
        h('tbody', props.items.length === 0
          ? [h('tr', [h('td', { class: 'empty', colspan: 5 }, `暂无${props.title}`)])]
          : props.items.map((item) => h('tr', { key: item.id }, [
            h('td', [h('strong', itemTitle(item)), h('small', itemSubtitle(item))]),
            h('td', [h(StatusPill, { value: itemStatus(item), tone: statusTone(itemStatus(item)) })]),
            h('td', 'retryCount' in item ? String(item.retryCount) : '-'),
            h('td', { class: 'truncate' }, itemDetail(item)),
            h('td', [h('strong', formatDate('createdAt' in item ? item.createdAt : null)), h('small', formatDate(itemTime(item)))]),
          ]))),
      ]),
    ]);
  },
});

const SimpleEvidenceTable = defineComponent({
  name: 'SimpleEvidenceTable',
  props: {
    title: { type: String, required: true },
    items: { type: Array as PropType<EvidenceItem[]>, required: true },
  },
  setup(props) {
    return () => h('div', { class: 'table-wrap ops-table' }, [
      h('table', [
        h('thead', [h('tr', [
          h('th', props.title),
          h('th', '状态'),
          h('th', '明细'),
          h('th', '时间'),
        ])]),
        h('tbody', props.items.length === 0
          ? [h('tr', [h('td', { class: 'empty', colspan: 4 }, `暂无${props.title}`)])]
          : props.items.map((item) => h('tr', { key: itemSubtitle(item) }, [
            h('td', [h('strong', itemTitle(item)), h('small', itemSubtitle(item))]),
            h('td', [h(StatusPill, { value: itemStatus(item), tone: statusTone(itemStatus(item)) })]),
            h('td', { class: 'truncate' }, itemDetail(item)),
            h('td', formatDate(itemTime(item))),
          ]))),
      ]),
    ]);
  },
});

const failedConsumes = computed(() => bundle.value?.messageConsumeLogs.filter(isFailedConsume) ?? []);
const failedCallbacks = computed(() => bundle.value?.callbackRecords.filter(isFailedCallback) ?? []);
const failedOutbox = computed(() => bundle.value?.outboxEvents.filter(isFailedOutbox) ?? []);
const openDeadLetters = computed(() => bundle.value?.deadLetters.filter(isOpenDeadLetter) ?? []);

const riskCount = computed(() => (
  failedConsumes.value.length
  + failedCallbacks.value.length
  + failedOutbox.value.length
  + openDeadLetters.value.length
));

const summaryItems = computed(() => {
  const current = bundle.value;
  return [
    { label: '状态日志', value: current?.statusLogs.length ?? 0, tone: 'neutral' },
    { label: '流程任务', value: current?.workflowTasks.length ?? 0, tone: 'neutral' },
    { label: 'Outbox', value: current?.outboxEvents.length ?? 0, tone: failedOutbox.value.length > 0 ? 'danger' : 'success' },
    { label: '消费日志', value: current?.messageConsumeLogs.length ?? 0, tone: failedConsumes.value.length > 0 ? 'danger' : 'success' },
    { label: '回调记录', value: current?.callbackRecords.length ?? 0, tone: failedCallbacks.value.length > 0 ? 'danger' : 'success' },
    { label: '开放死信', value: openDeadLetters.value.length, tone: openDeadLetters.value.length > 0 ? 'danger' : 'success' },
    { label: '接入日志', value: current?.recentAccessLogs.length ?? 0, tone: 'neutral' },
    { label: '风险项', value: riskCount.value, tone: riskCount.value > 0 ? 'danger' : 'success' },
  ];
});

async function queryObservability() {
  const trimmedOrderNo = orderNo.value.trim();
  const trimmedExternalOrderNo = externalOrderNo.value.trim();
  if (!trimmedOrderNo && !trimmedExternalOrderNo) {
    errorText.value = '请输入平台订单号或 HIS 外部订单号';
    bundle.value = null;
    return;
  }

  loading.value = true;
  errorText.value = '';
  limit.value = normalizedLimit();
  try {
    bundle.value = await getOrderObservability({
      orderNo: trimmedOrderNo,
      externalOrderNo: trimmedExternalOrderNo,
      limit: limit.value,
    });
    emit('notice', riskCount.value > 0 ? 'error' : 'success', `${bundle.value.order.orderNo} 链路证据已刷新`);
  } catch (error) {
    bundle.value = null;
    errorText.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

function refreshOrderObservability() {
  return queryObservability();
}

watch(riskCount, (count) => emit('countChanged', count), { immediate: true });

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && bundle.value) void queryObservability();
  },
);

defineExpose({
  refreshOrderObservability,
});
</script>

<template>
  <section class="workspace observability-page">
    <div class="toolbar observability-query">
      <label>
        <span>平台订单号</span>
        <input v-model="orderNo" placeholder="ZHYF1784716810207" @keyup.enter="queryObservability" />
      </label>
      <label>
        <span>HIS 外部订单号</span>
        <input v-model="externalOrderNo" placeholder="HIS-BATCH-..." @keyup.enter="queryObservability" />
      </label>
      <label class="limit-label">
        <span>条数</span>
        <input v-model.number="limit" type="number" min="1" max="200" step="10" @keyup.enter="queryObservability" />
      </label>
      <button class="primary" type="button" :disabled="loading" @click="queryObservability">
        {{ loading ? '查询中' : '查询链路' }}
      </button>
    </div>

    <p v-if="errorText" class="error-line">{{ errorText }}</p>

    <div v-if="bundle" class="observability-header">
      <div>
        <span>平台订单</span>
        <strong>{{ bundle.order.orderNo }}</strong>
        <small>{{ bundle.order.id }}</small>
      </div>
      <div>
        <span>HIS 外部订单</span>
        <strong>{{ bundle.order.externalOrderNo }}</strong>
        <small>{{ bundle.order.institutionId }}</small>
      </div>
      <div>
        <span>订单状态</span>
        <StatusPill :value="bundle.order.status" :tone="statusTone(bundle.order.status)" />
        <small>{{ formatDate(bundle.order.updatedAt) }}</small>
      </div>
      <div>
        <span>链路风险</span>
        <strong :class="riskCount > 0 ? 'risk-danger' : 'risk-ok'">
          {{ riskCount > 0 ? `${riskCount} 项待处理` : '无开放风险' }}
        </strong>
        <small>{{ formatDate(bundle.order.createdAt) }}</small>
      </div>
    </div>

    <div v-if="bundle" class="observability-metrics">
      <div v-for="item in summaryItems" :key="item.label" :class="`metric-${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ formatNumber(item.value) }}</strong>
      </div>
    </div>

    <template v-if="bundle">
      <section class="observability-section">
        <h2>订单状态时间线</h2>
        <div class="timeline">
          <div v-for="log in bundle.statusLogs" :key="log.id" class="timeline-item">
            <span>{{ formatDate(log.createdAt) }}</span>
            <strong>{{ log.fromStatus || '开始' }} -> {{ log.toStatus }}</strong>
            <small>{{ log.operatorType }} / {{ log.source }} / {{ log.reason || '-' }}</small>
          </div>
          <div v-if="bundle.statusLogs.length === 0" class="empty">暂无状态日志</div>
        </div>
      </section>

      <section class="observability-section">
        <h2>流程任务</h2>
        <div class="table-wrap ops-table">
          <table>
            <thead>
              <tr>
                <th>任务</th>
                <th>状态</th>
                <th>事件</th>
                <th>处理人</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="bundle.workflowTasks.length === 0">
                <td colspan="5" class="empty">暂无流程任务</td>
              </tr>
              <tr v-for="task in bundle.workflowTasks" :key="task.id">
                <td><strong>{{ task.taskType }}</strong><small>{{ task.id }}</small></td>
                <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
                <td>{{ task.sourceEventId }}</td>
                <td>{{ task.assignedTo || '-' }}<small>{{ task.reviewComment || '-' }}</small></td>
                <td><strong>{{ formatDate(task.createdAt) }}</strong><small>{{ formatDate(task.completedAt || task.updatedAt) }}</small></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="observability-section">
        <h2>消息链路</h2>
        <div class="observability-split">
          <RecordTable title="Outbox" :items="bundle.outboxEvents" />
          <RecordTable title="消费日志" :items="bundle.messageConsumeLogs" />
        </div>
      </section>

      <section class="observability-section">
        <h2>回调和死信</h2>
        <div class="table-wrap ops-table">
          <table>
            <thead>
              <tr>
                <th>类型</th>
                <th>业务对象</th>
                <th>状态</th>
                <th>重试</th>
                <th>响应</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="bundle.callbackRecords.length === 0">
                <td colspan="6" class="empty">暂无回调记录</td>
              </tr>
              <tr v-for="record in bundle.callbackRecords" :key="record.id">
                <td><strong>{{ record.callbackType }}</strong><small>{{ record.id }}</small></td>
                <td>{{ record.businessId }}<small>{{ record.requestUrl || '-' }}</small></td>
                <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
                <td>{{ record.retryCount }}<small>{{ formatDate(record.nextRetryAt) }}</small></td>
                <td class="truncate">{{ record.responseBody || '-' }}</td>
                <td><strong>{{ formatDate(record.createdAt) }}</strong><small>{{ formatDate(record.updatedAt) }}</small></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="table-wrap ops-table">
          <table>
            <thead>
              <tr>
                <th>事件</th>
                <th>Topic/消费组</th>
                <th>状态</th>
                <th>重试</th>
                <th>错误</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="bundle.deadLetters.length === 0">
                <td colspan="6" class="empty">暂无死信</td>
              </tr>
              <tr v-for="record in bundle.deadLetters" :key="record.id">
                <td><strong>{{ record.eventId }}</strong><small>{{ record.aggregateId || record.id }}</small></td>
                <td>{{ record.topic || '-' }}<small>{{ record.consumerGroup || '-' }}</small></td>
                <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
                <td>{{ record.retryCount }}</td>
                <td class="truncate">{{ record.errorMessage || record.remark || '-' }}</td>
                <td><strong>{{ formatDate(record.createdAt) }}</strong><small>{{ formatDate(record.updatedAt) }}</small></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="observability-section">
        <h2>接入和补偿证据</h2>
        <div class="observability-split">
          <SimpleEvidenceTable title="订单校验" :items="bundle.validationRecords" />
          <SimpleEvidenceTable title="集成重试" :items="bundle.integrationRetries" />
          <SimpleEvidenceTable title="操作日志" :items="bundle.operationLogs" />
          <SimpleEvidenceTable title="访问日志" :items="bundle.recentAccessLogs" />
        </div>
      </section>
    </template>
  </section>
</template>
