<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { getOrderObservability } from '../../api/ops';
import type {
  DeadLetterRecord,
  EventOutboxRecord,
  MessageConsumeRecord,
  OpsCallbackRecord,
  OrderObservabilityBundle,
} from '../../api/types';
import { formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type LegacyTone = 'normal' | 'success' | 'warning' | 'danger';

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

function text(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function time(value: string | null | undefined) {
  return value ? formatDate(value) : '-';
}

function statusTone(status: string | null | undefined): LegacyTone {
  const value = (status ?? '').toUpperCase();
  if (['SUCCESS', 'COMPLETED', 'DONE', 'PUBLISHED', 'DELIVERED', 'ACKED'].includes(value)) return 'success';
  if (['PENDING', 'PROCESSING', 'READY', 'RETRYING', 'OPEN'].includes(value)) return 'warning';
  if (['FAILED', 'FAILED_RETRYABLE', 'FAILED_FATAL', 'DEAD', 'PUBLISH_FAILED', 'REJECTED'].includes(value)) return 'danger';
  return 'normal';
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

const totalEvidenceCount = computed(() => {
  const current = bundle.value;
  if (!current) return 0;
  return current.statusLogs.length
    + current.workflowTasks.length
    + current.outboxEvents.length
    + current.messageConsumeLogs.length
    + current.callbackRecords.length
    + current.deadLetters.length
    + current.validationRecords.length
    + current.integrationRetries.length
    + current.operationLogs.length
    + current.recentAccessLogs.length;
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
    emit('notice', riskCount.value > 0 ? 'error' : 'success', `订单 ${bundle.value.order.orderNo} 链路已刷新`);
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
  <section class="legacy-page">
    <div class="legacy-head-title clearfix">
      <h1>订单链路查询</h1>
    </div>

    <div class="legacy-panel">
      <ul class="legacy-search">
        <li>
          平台订单号：
          <input
            v-model="orderNo"
            class="legacy-input input-large"
            placeholder="ZHYF1784716810207"
            @keyup.enter="queryObservability"
          />
        </li>
        <li>
          HIS外部订单号：
          <input
            v-model="externalOrderNo"
            class="legacy-input input-large"
            placeholder="HIS订单号"
            @keyup.enter="queryObservability"
          />
        </li>
        <li>
          查询条数：
          <input
            v-model.number="limit"
            class="legacy-input input-small"
            type="number"
            min="1"
            max="200"
            step="10"
            @keyup.enter="queryObservability"
          />
        </li>
        <li>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="queryObservability">
            {{ loading ? '查询中' : '查询' }}
          </button>
        </li>
      </ul>

      <p v-if="errorText" class="legacy-error">{{ errorText }}</p>

      <template v-if="bundle">
        <table class="legacy-main-table">
          <tbody>
            <tr class="legacy-main-head">
              <th colspan="8">订单基本信息</th>
            </tr>
            <tr class="legacy-main-info">
              <td class="legacy-label">平台订单号</td>
              <td>{{ text(bundle.order.orderNo) }}</td>
              <td class="legacy-label">HIS外部订单号</td>
              <td>{{ text(bundle.order.externalOrderNo) }}</td>
              <td class="legacy-label">机构ID</td>
              <td>{{ text(bundle.order.institutionId) }}</td>
              <td class="legacy-label">订单状态</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(bundle.order.status)}`">
                  {{ text(bundle.order.status) }}
                </span>
              </td>
            </tr>
            <tr class="legacy-main-info">
              <td class="legacy-label">创建时间</td>
              <td>{{ time(bundle.order.createdAt) }}</td>
              <td class="legacy-label">更新时间</td>
              <td>{{ time(bundle.order.updatedAt) }}</td>
              <td class="legacy-label">链路记录</td>
              <td>{{ formatNumber(totalEvidenceCount) }}</td>
              <td class="legacy-label">开放风险</td>
              <td>
                <span :class="riskCount > 0 ? 'legacy-red' : 'legacy-green'">
                  {{ riskCount > 0 ? `${riskCount} 项待处理` : '无开放风险' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>变更时间</th>
              <th>原状态</th>
              <th>新状态</th>
              <th>操作来源</th>
              <th>操作人</th>
              <th>原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.statusLogs.length === 0" class="legacy-main-info">
              <td colspan="6" class="legacy-empty">没有状态轨迹</td>
            </tr>
            <tr v-for="log in bundle.statusLogs" :key="log.id" class="legacy-main-info">
              <td>{{ time(log.createdAt) }}</td>
              <td>{{ text(log.fromStatus) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(log.toStatus)}`">
                  {{ text(log.toStatus) }}
                </span>
              </td>
              <td>{{ text(log.operatorType) }} / {{ text(log.source) }}</td>
              <td>{{ text(log.operatorId) }}</td>
              <td class="legacy-left">{{ text(log.reason) }}</td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>任务类型</th>
              <th>任务状态</th>
              <th>来源事件</th>
              <th>处理人</th>
              <th>处理意见</th>
              <th>创建时间</th>
              <th>完成/更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.workflowTasks.length === 0" class="legacy-main-info">
              <td colspan="7" class="legacy-empty">没有流程任务</td>
            </tr>
            <tr v-for="task in bundle.workflowTasks" :key="task.id" class="legacy-main-info">
              <td>{{ text(task.taskType) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(task.taskStatus)}`">
                  {{ text(task.taskStatus) }}
                </span>
              </td>
              <td class="legacy-left">{{ text(task.sourceEventId) }}</td>
              <td>{{ text(task.assignedTo) }}</td>
              <td class="legacy-left">{{ text(task.reviewComment) }}</td>
              <td>{{ time(task.createdAt) }}</td>
              <td>{{ time(task.completedAt || task.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>消息类型</th>
              <th>事件ID</th>
              <th>Topic/Tag</th>
              <th>业务对象</th>
              <th>状态</th>
              <th>重试</th>
              <th>错误信息</th>
              <th>创建/更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.outboxEvents.length === 0" class="legacy-main-info">
              <td colspan="8" class="legacy-empty">没有 Outbox 消息</td>
            </tr>
            <tr v-for="event in bundle.outboxEvents" :key="event.id" class="legacy-main-info">
              <td>{{ text(event.eventType) }}</td>
              <td class="legacy-left">{{ text(event.eventId) }}</td>
              <td>{{ text(event.topic) }} / {{ text(event.tag) }}</td>
              <td>{{ text(event.aggregateType) }}：{{ text(event.aggregateId) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(event.status)}`">
                  {{ text(event.status) }}
                </span>
              </td>
              <td>{{ event.retryCount }} / {{ event.maxRetryCount }}</td>
              <td class="legacy-left">{{ text(event.lastError) }}</td>
              <td>{{ time(event.createdAt) }}<br />{{ time(event.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>消费组</th>
              <th>消息ID</th>
              <th>事件ID</th>
              <th>Topic/Tag</th>
              <th>状态</th>
              <th>重试</th>
              <th>错误信息</th>
              <th>消费时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.messageConsumeLogs.length === 0" class="legacy-main-info">
              <td colspan="8" class="legacy-empty">没有消费日志</td>
            </tr>
            <tr v-for="consume in bundle.messageConsumeLogs" :key="consume.id" class="legacy-main-info">
              <td>{{ text(consume.consumerGroup) }}</td>
              <td class="legacy-left">{{ text(consume.messageId) }}</td>
              <td class="legacy-left">{{ text(consume.eventId) }}</td>
              <td>{{ text(consume.topic) }} / {{ text(consume.tag) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(consume.status)}`">
                  {{ text(consume.status) }}
                </span>
              </td>
              <td>{{ consume.retryCount }}</td>
              <td class="legacy-left">{{ text(consume.lastError) }}</td>
              <td>{{ time(consume.consumeStartedAt) }}<br />{{ time(consume.consumeFinishedAt || consume.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>回调类型</th>
              <th>业务对象</th>
              <th>请求地址</th>
              <th>状态</th>
              <th>重试</th>
              <th>响应内容</th>
              <th>创建/更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.callbackRecords.length === 0" class="legacy-main-info">
              <td colspan="7" class="legacy-empty">没有回调记录</td>
            </tr>
            <tr v-for="record in bundle.callbackRecords" :key="record.id" class="legacy-main-info">
              <td>{{ text(record.callbackType) }}</td>
              <td>{{ text(record.businessId) }}</td>
              <td class="legacy-left">{{ text(record.requestUrl) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(record.status)}`">
                  {{ text(record.status) }}
                </span>
              </td>
              <td>{{ record.retryCount }}<br />{{ time(record.nextRetryAt) }}</td>
              <td class="legacy-left">{{ text(record.responseBody) }}</td>
              <td>{{ time(record.createdAt) }}<br />{{ time(record.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>死信事件</th>
              <th>Topic/Tag</th>
              <th>消费组</th>
              <th>业务对象</th>
              <th>状态</th>
              <th>重试</th>
              <th>错误信息</th>
              <th>创建/更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.deadLetters.length === 0" class="legacy-main-info">
              <td colspan="8" class="legacy-empty">没有死信记录</td>
            </tr>
            <tr v-for="record in bundle.deadLetters" :key="record.id" class="legacy-main-info">
              <td class="legacy-left">{{ text(record.eventId) }}</td>
              <td>{{ text(record.topic) }} / {{ text(record.tag) }}</td>
              <td>{{ text(record.consumerGroup) }}</td>
              <td>{{ text(record.aggregateId) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(record.status)}`">
                  {{ text(record.status) }}
                </span>
              </td>
              <td>{{ record.retryCount }}</td>
              <td class="legacy-left">{{ text(record.errorMessage || record.remark) }}</td>
              <td>{{ time(record.createdAt) }}<br />{{ time(record.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <table class="legacy-main-table">
          <thead>
            <tr class="legacy-main-head">
              <th>日志类型</th>
              <th>对象</th>
              <th>结果/状态</th>
              <th>说明</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="bundle.validationRecords.length === 0 && bundle.integrationRetries.length === 0 && bundle.operationLogs.length === 0 && bundle.recentAccessLogs.length === 0" class="legacy-main-info">
              <td colspan="5" class="legacy-empty">没有补充日志</td>
            </tr>
            <tr v-for="record in bundle.validationRecords" :key="`validation-${record.id}`" class="legacy-main-info">
              <td>订单校验</td>
              <td>{{ text(record.orderId) }}<br />{{ text(record.eventId) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(record.validationStatus)}`">
                  {{ text(record.validationStatus) }}
                </span>
              </td>
              <td class="legacy-left">{{ text(record.validationMessage) }}</td>
              <td>{{ time(record.createdAt) }}</td>
            </tr>
            <tr v-for="record in bundle.integrationRetries" :key="`integration-${record.taskId}`" class="legacy-main-info">
              <td>集成重试</td>
              <td>{{ text(record.businessKey) }}<br />{{ text(record.targetSystem) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(record.taskStatus)}`">
                  {{ text(record.taskStatus) }}
                </span>
              </td>
              <td class="legacy-left">{{ text(record.failureReason || record.responseBody || record.requestUrl) }}</td>
              <td>{{ time(record.taskCreatedAt) }}<br />{{ time(record.taskUpdatedAt) }}</td>
            </tr>
            <tr v-for="record in bundle.operationLogs" :key="`operation-${record.id}`" class="legacy-main-info">
              <td>操作日志</td>
              <td>{{ text(record.operator) }}<br />{{ text(record.eventId || record.orderId) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(record.result)}`">
                  {{ text(record.result) }}
                </span>
              </td>
              <td class="legacy-left">{{ text(record.action) }}：{{ text(record.reason) }}</td>
              <td>{{ time(record.createdAt) }}</td>
            </tr>
            <tr v-for="record in bundle.recentAccessLogs" :key="`access-${record.id}`" class="legacy-main-info">
              <td>访问日志</td>
              <td>{{ text(record.appKey) }}<br />{{ text(record.requestIp) }}</td>
              <td>
                <span class="legacy-status" :class="`legacy-status-${statusTone(record.resultCode)}`">
                  {{ text(record.resultCode) }}
                </span>
              </td>
              <td class="legacy-left">{{ text(record.requestPath) }}</td>
              <td>{{ time(record.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </template>
    </div>
  </section>
</template>
