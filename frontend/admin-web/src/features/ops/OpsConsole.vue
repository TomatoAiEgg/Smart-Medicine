<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  closeDeadLetter,
  getOpsHealthOverview,
  listApiAccessLogs,
  listDeadLetters,
  listIntegrationRetryIssues,
  listLogisticsCallbackIssues as listCallbackIssues,
  listMessageConsumeLogs as listMessageConsumes,
  listOrderValidationRecords as listOrderValidations,
  listOutbox,
  replayDeadLetter,
} from '../../api/ops';
import type {
  ApiAccessLogRecord,
  DeadLetterRecord,
  EventOutboxRecord as OutboxEventRecord,
  IntegrationRetryIssueRecord as IntegrationRetryTaskRecord,
  LogisticsCallbackIssueRecord as CallbackRecord,
  MessageConsumeRecord,
  OpsHealthOverview,
  OrderValidationRecord,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type OpsDataset =
  | 'outbox'
  | 'consume'
  | 'deadLetters'
  | 'validation'
  | 'access'
  | 'callbackIssues'
  | 'integrationIssues';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const activeOpsDataset = ref<OpsDataset>('outbox');
const opsLoading = ref(false);
const opsError = ref('');
const opsHealthLoading = ref(false);
const opsHealthError = ref('');
const opsLimit = ref(50);
const opsHealthHours = ref(24);
const opsStatus = ref('');
const opsDeadLetterTopic = ref('');
const opsEventType = ref('');
const opsConsumerGroup = ref('');
const opsEventId = ref('');
const opsOrderId = ref('');
const opsValidationStatus = ref('');
const opsAppKey = ref('');
const opsResultCode = ref('');
const opsCallbackStatus = ref('');
const opsCallbackType = ref('');
const opsBusinessId = ref('');
const opsIssueOrderNo = ref('');
const opsIntegrationTaskStatus = ref('');
const opsIntegrationTaskType = ref('');
const opsIntegrationBusinessKey = ref('');
const opsIntegrationSourceSystem = ref('');
const outboxRecords = ref<OutboxEventRecord[]>([]);
const messageConsumeRecords = ref<MessageConsumeRecord[]>([]);
const deadLetterRecords = ref<DeadLetterRecord[]>([]);
const orderValidationRecords = ref<OrderValidationRecord[]>([]);
const apiAccessLogRecords = ref<ApiAccessLogRecord[]>([]);
const logisticsCallbackIssueRecords = ref<CallbackRecord[]>([]);
const integrationRetryIssueRecords = ref<IntegrationRetryTaskRecord[]>([]);
const opsHealth = ref<OpsHealthOverview | null>(null);
const opsRecordsRequestId = ref(0);

const opsDatasetNames: Record<OpsDataset, string> = {
  outbox: 'Outbox',
  deadLetters: 'Dead Letter',
  consume: '消费日志',
  validation: '订单校验',
  access: '访问日志',
  callbackIssues: '回调失败',
  integrationIssues: '集成失败',
};

function datasetCount(dataset: OpsDataset) {
  if (dataset === 'outbox') return outboxRecords.value.length;
  if (dataset === 'deadLetters') return deadLetterRecords.value.length;
  if (dataset === 'consume') return messageConsumeRecords.value.length;
  if (dataset === 'validation') return orderValidationRecords.value.length;
  if (dataset === 'access') return apiAccessLogRecords.value.length;
  if (dataset === 'integrationIssues') return integrationRetryIssueRecords.value.length;
  return logisticsCallbackIssueRecords.value.length;
}

const activeOpsCount = computed(() => datasetCount(activeOpsDataset.value));

function normalizedOpsLimit() {
  return boundedPositiveInteger(opsLimit.value, 50, 200);
}

function normalizedOpsHealthHours() {
  if (!Number.isFinite(opsHealthHours.value) || opsHealthHours.value <= 0) return 24;
  return Math.min(Math.trunc(opsHealthHours.value), 168);
}

function downloadOpsRecordsCsv() {
  if (activeOpsDataset.value === 'outbox') {
    downloadCsv(
      `运维总控-Outbox-${opsLimit.value}条.csv`,
      ['事件ID', '事件类型', 'Topic', 'Tag', '来源', '聚合类型', '聚合ID', '状态', '重试次数', '最大重试', '下次重试', '失败原因', '创建时间', '更新时间', '发布时间'],
      outboxRecords.value.map((record) => [
        record.eventId,
        record.eventType,
        record.topic,
        record.tag,
        record.source,
        record.aggregateType,
        record.aggregateId,
        record.status,
        record.retryCount,
        record.maxRetryCount,
        formatDate(record.nextRetryAt),
        record.lastError,
        formatDate(record.createdAt),
        formatDate(record.updatedAt),
        formatDate(record.publishedAt),
      ]),
    );
  } else if (activeOpsDataset.value === 'consume') {
    downloadCsv(
      `运维总控-消费日志-${opsLimit.value}条.csv`,
      ['消费组', '消息ID', '事件ID', 'Topic', 'Tag', '聚合ID', '状态', '重试次数', '失败原因', '追踪端点', '开始时间', '完成时间', '创建时间', '更新时间'],
      messageConsumeRecords.value.map((record) => [
        record.consumerGroup,
        record.messageId,
        record.eventId,
        record.topic,
        record.tag,
        record.aggregateId,
        record.status,
        record.retryCount,
        record.lastError,
        record.traceEndpoint,
        formatDate(record.consumeStartedAt),
        formatDate(record.consumeFinishedAt),
        formatDate(record.createdAt),
        formatDate(record.updatedAt),
      ]),
    );
  } else if (activeOpsDataset.value === 'deadLetters') {
    downloadCsv(
      `运维总控-死信-${opsLimit.value}条.csv`,
      ['事件ID', 'Topic', 'Tag', '消费组', '聚合ID', '状态', '重试次数', '错误信息', '操作人', '备注', '创建时间', '更新时间'],
      deadLetterRecords.value.map((record) => [
        record.eventId,
        record.topic,
        record.tag,
        record.consumerGroup,
        record.aggregateId,
        record.status,
        record.retryCount,
        record.errorMessage,
        record.operator,
        record.remark,
        formatDate(record.createdAt),
        formatDate(record.updatedAt),
      ]),
    );
  } else if (activeOpsDataset.value === 'validation') {
    downloadCsv(
      `运维总控-订单校验-${opsLimit.value}条.csv`,
      ['订单ID', '事件ID', '校验状态', '校验消息', '创建时间'],
      orderValidationRecords.value.map((record) => [
        record.orderId,
        record.eventId,
        record.validationStatus,
        record.validationMessage,
        formatDate(record.createdAt),
      ]),
    );
  } else if (activeOpsDataset.value === 'access') {
    downloadCsv(
      `运维总控-访问日志-${opsLimit.value}条.csv`,
      ['机构ID', 'AppKey', '请求路径', '请求IP', '结果码', '创建时间'],
      apiAccessLogRecords.value.map((record) => [
        record.institutionId,
        record.appKey,
        record.requestPath,
        record.requestIp,
        record.resultCode,
        formatDate(record.createdAt),
      ]),
    );
  } else if (activeOpsDataset.value === 'callbackIssues') {
    downloadCsv(
      `运维总控-回调失败-${opsLimit.value}条.csv`,
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
      logisticsCallbackIssueRecords.value.map((record) => [
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
      `运维总控-集成失败-${opsLimit.value}条.csv`,
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
      integrationRetryIssueRecords.value.map((record) => [
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
  emit('notice', 'success', `已导出${opsDatasetNames[activeOpsDataset.value]} ${formatNumber(activeOpsCount.value)} 条`);
}

async function refreshOpsHealth() {
  opsHealthLoading.value = true;
  opsHealthError.value = '';
  const recentHours = normalizedOpsHealthHours();
  opsHealthHours.value = recentHours;
  try {
    opsHealth.value = await getOpsHealthOverview({ recentHours });
  } catch (error) {
    opsHealth.value = null;
    opsHealthError.value = errorMessage(error);
  } finally {
    opsHealthLoading.value = false;
  }
}

async function refreshOpsRecords() {
  const requestId = opsRecordsRequestId.value + 1;
  opsRecordsRequestId.value = requestId;
  const dataset = activeOpsDataset.value;
  opsLoading.value = true;
  opsError.value = '';
  const limit = normalizedOpsLimit();
  opsLimit.value = limit;

  try {
    if (dataset === 'outbox') {
      outboxRecords.value = await listOutbox({
        status: opsStatus.value,
        eventType: opsEventType.value,
        limit,
      });
    } else if (dataset === 'consume') {
      messageConsumeRecords.value = await listMessageConsumes({
        status: opsStatus.value,
        consumerGroup: opsConsumerGroup.value,
        eventId: opsEventId.value,
        limit,
      });
    } else if (dataset === 'deadLetters') {
      deadLetterRecords.value = await listDeadLetters({
        status: opsStatus.value,
        topic: opsDeadLetterTopic.value,
        eventId: opsEventId.value,
        limit,
      });
    } else if (dataset === 'validation') {
      orderValidationRecords.value = await listOrderValidations({
        orderId: opsOrderId.value,
        validationStatus: opsValidationStatus.value,
        limit,
      });
    } else if (dataset === 'access') {
      apiAccessLogRecords.value = await listApiAccessLogs({
        appKey: opsAppKey.value,
        resultCode: opsResultCode.value,
        limit,
      });
    } else if (dataset === 'callbackIssues') {
      logisticsCallbackIssueRecords.value = await listCallbackIssues({
        callbackStatus: opsCallbackStatus.value,
        callbackType: opsCallbackType.value,
        businessId: opsBusinessId.value,
        orderNo: opsIssueOrderNo.value,
        limit,
      });
    } else {
      integrationRetryIssueRecords.value = await listIntegrationRetryIssues({
        taskStatus: opsIntegrationTaskStatus.value,
        taskType: opsIntegrationTaskType.value,
        businessKey: opsIntegrationBusinessKey.value,
        sourceSystem: opsIntegrationSourceSystem.value,
        limit,
      });
    }
    if (requestId === opsRecordsRequestId.value) {
      emit('notice', 'info', `已刷新${opsDatasetNames[dataset]}：${datasetCount(dataset)} 条`);
    }
  } catch (error) {
    if (requestId === opsRecordsRequestId.value) {
      opsError.value = errorMessage(error);
    }
  } finally {
    if (requestId === opsRecordsRequestId.value) {
      opsLoading.value = false;
    }
  }
}

function hasDatasetRecords(dataset: OpsDataset) {
  if (dataset === 'outbox') return outboxRecords.value.length > 0;
  if (dataset === 'deadLetters') return deadLetterRecords.value.length > 0;
  if (dataset === 'consume') return messageConsumeRecords.value.length > 0;
  if (dataset === 'validation') return orderValidationRecords.value.length > 0;
  if (dataset === 'access') return apiAccessLogRecords.value.length > 0;
  if (dataset === 'callbackIssues') return logisticsCallbackIssueRecords.value.length > 0;
  return integrationRetryIssueRecords.value.length > 0;
}

function switchOpsDataset(dataset: OpsDataset) {
  activeOpsDataset.value = dataset;
  if (!hasDatasetRecords(dataset)) void refreshOpsRecords();
}

async function refreshOpsConsole() {
  await Promise.all([refreshOpsHealth(), refreshOpsRecords()]);
}

async function handleDeadLetterAction(record: DeadLetterRecord, action: 'replay' | 'close') {
  opsLoading.value = true;
  opsError.value = '';
  const body = {
    operator: 'admin-console',
    remark: action === 'replay' ? 'manual replay from ops console' : 'manual close from ops console',
  };
  try {
    const result = action === 'replay'
      ? await replayDeadLetter(record.id, body)
      : await closeDeadLetter(record.id, body);
    emit('notice', 'success', `${result.eventId} ${result.status}`);
    await Promise.all([refreshOpsHealth(), refreshOpsRecords()]);
  } catch (error) {
    opsError.value = errorMessage(error);
  } finally {
    opsLoading.value = false;
  }
}

watch(activeOpsCount, (count) => emit('countChanged', count), { immediate: true });

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshOpsConsole();
  },
  { immediate: true },
);

defineExpose({
  refreshOpsConsole,
});
</script>

<template>
        <section class="legacy-page ops-console-page">
        <div class="legacy-search-panel ops-tabs">
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'outbox' }"
            type="button"
            @click="switchOpsDataset('outbox')"
          >
            Outbox
          </button>
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'consume' }"
            type="button"
            @click="switchOpsDataset('consume')"
          >
            消费日志
          </button>
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'deadLetters' }"
            type="button"
            @click="switchOpsDataset('deadLetters')"
          >
            死信
          </button>
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'validation' }"
            type="button"
            @click="switchOpsDataset('validation')"
          >
            订单校验
          </button>
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'access' }"
            type="button"
            @click="switchOpsDataset('access')"
          >
            访问日志
          </button>
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'callbackIssues' }"
            type="button"
            @click="switchOpsDataset('callbackIssues')"
          >
            回调失败
          </button>
          <button
            class="secondary"
            :class="{ active: activeOpsDataset === 'integrationIssues' }"
            type="button"
            @click="switchOpsDataset('integrationIssues')"
          >
            集成失败
          </button>
          <label class="limit-label">
            <span>条数</span>
            <input v-model.number="opsLimit" type="number" min="1" max="200" step="10" @keyup.enter="refreshOpsRecords" />
          </label>
          <button class="primary" type="button" :disabled="opsLoading" @click="refreshOpsRecords">
            {{ opsLoading ? '刷新中' : '刷新' }}
          </button>
          <button class="secondary" type="button" :disabled="opsLoading || activeOpsCount === 0" @click="downloadOpsRecordsCsv">
            导出当前结果
          </button>
        </div>

        <div class="legacy-search-panel ops-health-panel">
          <label>
            <span>健康窗口</span>
            <input v-model.number="opsHealthHours" type="number" min="1" max="168" step="1" @keyup.enter="refreshOpsHealth" />
          </label>
          <button type="button" :disabled="opsHealthLoading" @click="refreshOpsHealth">
            {{ opsHealthLoading ? '刷新中' : '刷新健康概览' }}
          </button>
        </div>

        <p v-if="opsHealthError" class="error-line">{{ opsHealthError }}</p>

        <div v-if="opsHealth" class="legacy-detail-grid ops-health-summary">
          <div>
            <span>窗口</span>
            <strong>{{ opsHealth.recentHours }} 小时</strong>
          </div>
          <div>
            <span>最近访问</span>
            <strong>{{ formatNumber(opsHealth.recentAccessCount) }}</strong>
          </div>
          <div>
            <span>Outbox 待发</span>
            <strong>{{ formatNumber(opsHealth.pendingOutbox) }}</strong>
          </div>
          <div>
            <span>Outbox 失败</span>
            <strong>{{ formatNumber(opsHealth.failedOutbox) }}</strong>
          </div>
          <div>
            <span>消费失败</span>
            <strong>{{ formatNumber(opsHealth.failedConsumes) }}</strong>
          </div>
          <div>
            <span>校验拒绝</span>
            <strong>{{ formatNumber(opsHealth.rejectedValidations) }}</strong>
          </div>
          <div>
            <span>回调失败/死信</span>
            <strong>{{ formatNumber(opsHealth.failedCallbacks) }} / {{ formatNumber(opsHealth.deadCallbacks) }}</strong>
          </div>
          <div>
            <span>集成失败/死信</span>
            <strong>{{ formatNumber(opsHealth.failedIntegrationRetries) }} / {{ formatNumber(opsHealth.deadIntegrationRetries) }}</strong>
          </div>
        </div>

        <div class="legacy-search-panel legacy-event-panel ops-filter-panel">
          <template v-if="activeOpsDataset === 'outbox'">
            <label>
              <span>状态</span>
              <input v-model="opsStatus" placeholder="NEW / PUBLISH_FAILED / DEAD" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>事件类型</span>
              <input v-model="opsEventType" placeholder="ORDER_CREATED" @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
          <template v-else-if="activeOpsDataset === 'consume'">
            <label>
              <span>状态</span>
              <input v-model="opsStatus" placeholder="SUCCESS / FAILED" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>消费组</span>
              <input v-model="opsConsumerGroup" placeholder="consumer group" @keyup.enter="refreshOpsRecords" />
            </label>
            <label class="grow">
              <span>事件 ID</span>
              <input v-model="opsEventId" placeholder="eventId" @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
          <template v-else-if="activeOpsDataset === 'deadLetters'">
            <label>
              <span>状态</span>
              <input v-model="opsStatus" placeholder="默认 OPEN" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>Topic</span>
              <input v-model="opsDeadLetterTopic" placeholder="zhyf-order-event" @keyup.enter="refreshOpsRecords" />
            </label>
            <label class="grow">
              <span>事件 ID</span>
              <input v-model="opsEventId" placeholder="eventId" @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
          <template v-else-if="activeOpsDataset === 'validation'">
            <label class="grow">
              <span>订单 ID</span>
              <input v-model="opsOrderId" placeholder="UUID" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>校验状态</span>
              <input v-model="opsValidationStatus" placeholder="PASSED / REJECTED" @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
          <template v-else-if="activeOpsDataset === 'access'">
            <label>
              <span>AppKey</span>
              <input v-model="opsAppKey" placeholder="机构 appKey" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>结果码</span>
              <input v-model="opsResultCode" placeholder="SUCCESS / HTTP_4XX" @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
          <template v-else-if="activeOpsDataset === 'callbackIssues'">
            <label>
              <span>回调状态</span>
              <input v-model="opsCallbackStatus" placeholder="默认 FAILED / DEAD" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>回调类型</span>
              <input v-model="opsCallbackType" placeholder="ORDER_SHIPPED" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>物流单号</span>
              <input v-model="opsBusinessId" placeholder="businessId / logisticsNo" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>订单号</span>
              <input v-model="opsIssueOrderNo" placeholder="ZHYF..." @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
          <template v-else>
            <label>
              <span>任务状态</span>
              <input v-model="opsIntegrationTaskStatus" placeholder="默认 FAILED / DEAD" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>任务类型</span>
              <input v-model="opsIntegrationTaskType" placeholder="ADDRESS_PUSH / COMMUNITY_STATUS_PUSH" @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>业务键</span>
              <input v-model="opsIntegrationBusinessKey" placeholder="ZHYF..." @keyup.enter="refreshOpsRecords" />
            </label>
            <label>
              <span>来源系统</span>
              <input v-model="opsIntegrationSourceSystem" placeholder="HOSP-E2E / CH-E2E" @keyup.enter="refreshOpsRecords" />
            </label>
          </template>
        </div>

        <p v-if="opsError" class="error-line">{{ opsError }}</p>

        <div v-if="activeOpsDataset === 'outbox'" class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
                <th>事件</th>
                <th>Topic/Tag</th>
                <th>聚合对象</th>
                <th>状态</th>
                <th>重试</th>
                <th>下次重试</th>
                <th>失败原因</th>
                <th>创建/发布时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!opsLoading && outboxRecords.length === 0">
                <td colspan="8" class="legacy-empty">暂无 Outbox 记录</td>
              </tr>
              <tr v-for="record in outboxRecords" :key="record.id">
                <td>
                  <strong>{{ record.eventType }}</strong>
                  <small>{{ record.eventId }}</small>
                </td>
                <td>
                  <strong>{{ record.topic || '-' }}</strong>
                  <small>{{ record.tag || '-' }}</small>
                </td>
                <td>
                  <strong>{{ record.aggregateType }}</strong>
                  <small>{{ record.aggregateId }}</small>
                </td>
                <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
                <td>{{ record.retryCount }} / {{ record.maxRetryCount }}</td>
                <td>{{ formatDate(record.nextRetryAt) }}</td>
                <td class="truncate">{{ record.lastError || '-' }}</td>
                <td>
                  <strong>{{ formatDate(record.createdAt) }}</strong>
                  <small>{{ formatDate(record.publishedAt || record.updatedAt) }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="activeOpsDataset === 'consume'" class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
                <th>消费组</th>
                <th>事件</th>
                <th>Topic/Tag</th>
                <th>消息 ID</th>
                <th>状态</th>
                <th>重试</th>
                <th>失败原因</th>
                <th>处理时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!opsLoading && messageConsumeRecords.length === 0">
                <td colspan="8" class="legacy-empty">暂无消费日志</td>
              </tr>
              <tr v-for="record in messageConsumeRecords" :key="record.id">
                <td><strong>{{ record.consumerGroup }}</strong></td>
                <td>
                  <strong>{{ record.eventId }}</strong>
                  <small>{{ record.aggregateId || record.id }}</small>
                </td>
                <td>
                  <strong>{{ record.topic || '-' }}</strong>
                  <small>{{ record.tag || '-' }}</small>
                </td>
                <td>{{ record.messageId }}</td>
                <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
                <td>{{ record.retryCount }}</td>
                <td class="truncate">{{ record.lastError || '-' }}</td>
                <td>
                  <strong>{{ formatDate(record.consumeStartedAt || record.createdAt) }}</strong>
                  <small>{{ formatDate(record.consumeFinishedAt || record.updatedAt) }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="activeOpsDataset === 'deadLetters'" class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
                <th>事件</th>
                <th>Topic/Tag</th>
                <th>消费组</th>
                <th>状态</th>
                <th>重试</th>
                <th>失败原因</th>
                <th>处置</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!opsLoading && deadLetterRecords.length === 0">
                <td colspan="8" class="legacy-empty">暂无死信记录</td>
              </tr>
              <tr v-for="record in deadLetterRecords" :key="record.id">
                <td>
                  <strong>{{ record.eventId }}</strong>
                  <small>{{ record.aggregateId || record.id }}</small>
                </td>
                <td>
                  <strong>{{ record.topic || '-' }}</strong>
                  <small>{{ record.tag || '-' }}</small>
                </td>
                <td>{{ record.consumerGroup || '发布侧' }}</td>
                <td>
                  <StatusPill :value="record.status" :tone="statusTone(record.status)" />
                  <small>{{ record.operator || '-' }}</small>
                </td>
                <td>{{ record.retryCount }}</td>
                <td class="truncate">{{ record.errorMessage || record.remark || '-' }}</td>
                <td>
                  <div class="actions">
                    <button
                      class="secondary"
                      type="button"
                      :disabled="opsLoading || record.status !== 'OPEN'"
                      @click="handleDeadLetterAction(record, 'replay')"
                    >
                      重放
                    </button>
                    <button
                      type="button"
                      :disabled="opsLoading || record.status !== 'OPEN'"
                      @click="handleDeadLetterAction(record, 'close')"
                    >
                      关闭
                    </button>
                  </div>
                </td>
                <td>
                  <strong>{{ formatDate(record.createdAt) }}</strong>
                  <small>{{ formatDate(record.updatedAt) }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="activeOpsDataset === 'validation'" class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
                <th>订单</th>
                <th>事件 ID</th>
                <th>校验状态</th>
                <th>校验消息</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!opsLoading && orderValidationRecords.length === 0">
                <td colspan="5" class="legacy-empty">暂无订单校验记录</td>
              </tr>
              <tr v-for="record in orderValidationRecords" :key="record.id">
                <td>
                  <strong>{{ record.orderId }}</strong>
                  <small>{{ record.tenantId }}</small>
                </td>
                <td>{{ record.eventId }}</td>
                <td><StatusPill :value="record.validationStatus" :tone="statusTone(record.validationStatus)" /></td>
                <td>{{ record.validationMessage || '-' }}</td>
                <td>{{ formatDate(record.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="activeOpsDataset === 'access'" class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
                <th>机构应用</th>
                <th>请求</th>
                <th>访问来源</th>
                <th>结果码</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!opsLoading && apiAccessLogRecords.length === 0">
                <td colspan="5" class="legacy-empty">暂无访问日志</td>
              </tr>
              <tr v-for="record in apiAccessLogRecords" :key="record.id">
                <td>
                  <strong>{{ record.appKey }}</strong>
                  <small>{{ record.institutionId }}</small>
                </td>
                <td>{{ record.requestPath }}</td>
                <td>{{ record.requestIp }}</td>
                <td><StatusPill :value="record.resultCode" :tone="statusTone(record.resultCode)" /></td>
                <td>{{ formatDate(record.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="activeOpsDataset === 'callbackIssues'" class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
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
              <tr v-if="!opsLoading && logisticsCallbackIssueRecords.length === 0">
                <td colspan="7" class="legacy-empty">暂无物流回调失败记录</td>
              </tr>
              <tr v-for="record in logisticsCallbackIssueRecords" :key="record.callbackId">
                <td>
                  <strong>{{ record.callbackType }}</strong>
                  <small>{{ record.callbackId }}</small>
                </td>
                <td>
                  <strong>{{ record.orderNo || '-' }}</strong>
                  <small>{{ record.logisticsNo || record.businessId }}</small>
                </td>
                <td>
                  <StatusPill :value="record.callbackStatus" :tone="statusTone(record.callbackStatus)" />
                  <small>{{ record.logisticsStatus || '-' }}</small>
                </td>
                <td>
                  <strong>{{ record.retryCount }}</strong>
                  <small>{{ formatDate(record.nextRetryAt) }}</small>
                </td>
                <td><code>{{ record.responseBody || record.requestUrl || '-' }}</code></td>
                <td>
                  <strong>{{ record.latestTraceStatus || '-' }}</strong>
                  <small>{{ record.latestTraceContent || '-' }}</small>
                </td>
                <td>
                  <strong>{{ formatDate(record.callbackUpdatedAt) }}</strong>
                  <small>{{ formatDate(record.latestTraceTime) }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else class="legacy-panel ops-table">
          <table class="legacy-main-table ops-main-table">
            <thead>
              <tr>
                <th>任务</th>
                <th>来源/业务</th>
                <th>状态</th>
                <th>重试</th>
                <th>失败原因</th>
                <th>消息状态</th>
                <th>更新时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!opsLoading && integrationRetryIssueRecords.length === 0">
                <td colspan="7" class="legacy-empty">暂无集成失败任务</td>
              </tr>
              <tr v-for="record in integrationRetryIssueRecords" :key="record.taskId">
                <td>
                  <strong>{{ record.taskType }}</strong>
                  <small>{{ record.taskId }}</small>
                </td>
                <td>
                  <strong>{{ record.businessKey || '-' }}</strong>
                  <small>{{ record.sourceSystem }} / {{ record.targetSystem }}</small>
                </td>
                <td>
                  <StatusPill :value="record.taskStatus" :tone="statusTone(record.taskStatus)" />
                  <small>{{ record.messageType }}</small>
                </td>
                <td>
                  <strong>{{ record.retryCount }}</strong>
                  <small>{{ formatDate(record.nextRetryAt) }}</small>
                </td>
                <td><code>{{ record.responseBody || record.requestUrl || '-' }}</code></td>
                <td>
                  <StatusPill :value="record.processStatus" :tone="statusTone(record.processStatus)" />
                  <small>{{ record.externalMessageId }}</small>
                </td>
                <td>
                  <strong>{{ formatDate(record.taskUpdatedAt) }}</strong>
                  <small>{{ formatDate(record.processedAt) }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
</template>
