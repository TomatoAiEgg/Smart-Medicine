<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { listSmsRecords } from '../../api/sms';
import type { SmsSendResult } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, displayValue, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'SIMULATED', label: '已登记（未发送）' },
  { value: 'SUCCESS', label: '发送成功' },
  { value: 'FAILED', label: '发送失败' },
] as const;

const keyword = ref('');
const sendStatus = ref('');
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const errorLine = ref('');
const recordPage = ref<{ records: SmsSendResult[]; total: number; page: number; pageSize: number } | null>(null);
const selectedRecord = ref<SmsSendResult | null>(null);

const records = computed(() => recordPage.value?.records ?? []);
const total = computed(() => recordPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function sendStatusText(value: string) {
  return statusOptions.find((option) => option.value === value)?.label ?? value;
}

function downloadRecordCsv() {
  downloadCsv(
    `短信发送记录-第${page.value}页.csv`,
    [
      '模板编码',
      '模板名称',
      '接收手机号',
      '接收人',
      '关联订单',
      '签名',
      '短信内容',
      '状态',
      '服务商流水',
      '失败原因',
      '重试次数',
      '操作人',
      '登记时间',
      '发送时间',
      '更新时间',
    ],
    records.value.map((record) => [
      record.templateCode,
      record.templateName,
      record.receiverPhone,
      record.receiverName,
      record.relatedOrderNo,
      record.signature,
      record.content,
      sendStatusText(record.sendStatus),
      record.providerMessageId,
      record.failureReason,
      record.retryCount,
      record.operator,
      formatDate(record.createdAt),
      formatDate(record.sentAt),
      formatDate(record.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(records.value.length)} 条短信发送记录`);
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshSmsRecords() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listSmsRecords({
      keyword: keyword.value,
      sendStatus: sendStatus.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    recordPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新短信记录：${nextPage.total} 条`);
  } catch (error) {
    recordPage.value = null;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshSmsRecords();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshSmsRecords();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshSmsRecords();
}

function resetFilters() {
  keyword.value = '';
  sendStatus.value = '';
  page.value = 1;
  void refreshSmsRecords();
}

function showDetail(record: SmsSendResult) {
  selectedRecord.value = record;
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) {
      void refreshSmsRecords();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshSmsRecords,
});
</script>

<template>
  <section class="legacy-page sms-record-page">
    <ul class="legacy-search sms-record-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        状态：
        <select v-model="sendStatus" class="legacy-input input-medium" @change="searchFirstPage">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </li>
      <li class="legacy-search-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          查询
        </button>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">
          重置
        </button>
        <button class="legacy-btn" type="button" :disabled="loading || records.length === 0" @click="downloadRecordCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <div class="legacy-stats">
      <span>记录总数：{{ total }}</span>
      <span>当前页：{{ records.length }}</span>
      <span>页码：{{ page }}</span>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>模板</th>
            <th>接收手机号</th>
            <th>接收人</th>
            <th>关联订单</th>
            <th>状态</th>
            <th>重试</th>
            <th>操作人</th>
            <th>登记时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && records.length === 0">
            <td colspan="9" class="empty-cell">暂无短信发送记录</td>
          </tr>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.templateName }}</td>
            <td>{{ record.receiverPhone }}</td>
            <td>{{ displayValue(record.receiverName) }}</td>
            <td>{{ displayValue(record.relatedOrderNo) }}</td>
            <td>{{ sendStatusText(record.sendStatus) }}</td>
            <td>{{ record.retryCount }}</td>
            <td>{{ displayValue(record.operator) }}</td>
            <td>{{ formatDate(record.createdAt) }}</td>
            <td>
              <button class="legacy-link-btn" type="button" @click="showDetail(record)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="goPreviousPage">
        上一页
      </button>
      <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="goNextPage">
        下一页
      </button>
      <label>
        每页
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="1" max="100" @keyup.enter="searchFirstPage" />
      </label>
    </div>

    <section v-if="selectedRecord" class="legacy-panel record-detail-panel">
      <div class="legacy-panel-title">短信详情</div>
      <div class="detail-grid">
        <span>模板编码：{{ selectedRecord.templateCode }}</span>
        <span>模板名称：{{ selectedRecord.templateName }}</span>
        <span>手机号：{{ selectedRecord.receiverPhone }}</span>
        <span>接收人：{{ displayValue(selectedRecord.receiverName) }}</span>
        <span>关联订单：{{ displayValue(selectedRecord.relatedOrderNo) }}</span>
        <span>服务商流水：{{ displayValue(selectedRecord.providerMessageId) }}</span>
        <span>失败原因：{{ displayValue(selectedRecord.failureReason) }}</span>
        <span>更新时间：{{ formatDate(selectedRecord.updatedAt) }}</span>
      </div>
      <div class="detail-content">
        <div v-if="selectedRecord.signature">【{{ selectedRecord.signature }}】</div>
        <p>{{ selectedRecord.content }}</p>
      </div>
    </section>
  </section>
</template>

<style scoped>
.sms-record-search {
  align-items: center;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

.record-detail-panel {
  margin-top: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  color: #475569;
  font-size: 13px;
}

.detail-content {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
  color: #0f172a;
  line-height: 1.7;
  word-break: break-word;
}

.detail-content p {
  margin: 0;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
