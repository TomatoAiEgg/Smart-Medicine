<script setup lang="ts">
import { ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { listLogisticsInfos } from '../../api/logistics';
import type { LogisticsInfoRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const startTime = ref('');
const endTime = ref('');
const orderNo = ref('');
const receiverPhone = ref('');
const logisticsNo = ref('');
const limit = ref(50);
const loading = ref(false);
const error = ref('');
const records = ref<LogisticsInfoRecord[]>([]);
const requestId = ref(0);

function normalizedLimit() {
  if (!Number.isFinite(limit.value) || limit.value <= 0) return 50;
  return Math.min(Math.trunc(limit.value), 200);
}

function pageSummary(total: number) {
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${total} 项`;
}

function downloadLogisticsInfoCsv() {
  downloadCsv(
    `物流信息查询-${limit.value}条.csv`,
    ['轨迹ID', '平台订单号', '外部订单号', '物流单号', '物流公司', '手机号码', '轨迹状态', '物流信息', '操作时间', '创建时间'],
    records.value.map((record) => [
      record.traceId,
      record.orderNo,
      record.externalOrderNo,
      record.logisticsNo,
      record.logisticsCompany,
      record.receiverPhone,
      record.traceStatus,
      record.operationInfo,
      formatDate(record.traceTime),
      formatDate(record.createdAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${formatNumber(records.value.length)} 条物流信息`);
}

async function refreshLogisticsInfos() {
  const nextRequestId = requestId.value + 1;
  requestId.value = nextRequestId;
  loading.value = true;
  error.value = '';
  limit.value = normalizedLimit();
  try {
    const nextRecords = await listLogisticsInfos({
      startTime: startTime.value,
      endTime: endTime.value,
      orderNo: orderNo.value,
      receiverPhone: receiverPhone.value,
      logisticsNo: logisticsNo.value,
      limit: limit.value,
    });
    if (nextRequestId !== requestId.value) return;
    records.value = nextRecords;
    emit('countChanged', nextRecords.length);
    emit('notice', 'info', `已刷新物流信息查询：${nextRecords.length} 条`);
  } catch (errorValue) {
    if (nextRequestId === requestId.value) {
      error.value = errorMessage(errorValue);
      records.value = [];
      emit('countChanged', 0);
    }
  } finally {
    if (nextRequestId === requestId.value) {
      loading.value = false;
    }
  }
}

function resetFilters() {
  startTime.value = '';
  endTime.value = '';
  orderNo.value = '';
  receiverPhone.value = '';
  logisticsNo.value = '';
  void refreshLogisticsInfos();
}

watch(() => props.active, (active) => {
  if (active && records.value.length === 0) {
    void refreshLogisticsInfos();
  }
}, { immediate: true });

watch(() => props.activationKey, () => {
  if (props.active) {
    void refreshLogisticsInfos();
  }
});

defineExpose({
  refreshLogisticsInfos,
});
</script>

<template>
  <section class="legacy-page logistics-info-page">
    <ul class="legacy-search logistics-info-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="refreshLogisticsInfos" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="refreshLogisticsInfos" />
      </li>
      <li>
        平台订单号：
        <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="refreshLogisticsInfos" />
      </li>
      <li>
        手机号码：
        <input v-model="receiverPhone" class="legacy-input input-medium" @keyup.enter="refreshLogisticsInfos" />
      </li>
      <li>
        物流单号：
        <input v-model="logisticsNo" class="legacy-input input-large" @keyup.enter="refreshLogisticsInfos" />
      </li>
      <li>
        条数：
        <input v-model.number="limit" class="legacy-input input-small" type="number" min="1" max="200" step="10" @keyup.enter="refreshLogisticsInfos" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshLogisticsInfos">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">重置</button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || records.length === 0" @click="downloadLogisticsInfoCsv">
          导出当前结果
        </button>
      </li>
    </ul>

    <p v-if="error" class="error-line">{{ error }}</p>

    <table class="legacy-main-table logistics-info-table">
      <thead>
        <tr class="legacy-main-head">
          <th>ID</th>
          <th>物流信息</th>
          <th>操作时间</th>
          <th>平台单号</th>
          <th>物流单号</th>
          <th>物流公司</th>
          <th>手机号码</th>
          <th>状态</th>
          <th>创建时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading" class="legacy-main-info">
          <td colspan="9">正在查询物流信息...</td>
        </tr>
        <tr v-else-if="records.length === 0" class="legacy-main-info">
          <td colspan="9">没有相关数据</td>
        </tr>
        <template v-else>
          <tr v-for="record in records" :key="record.traceId" class="legacy-main-info">
            <td class="mono-cell">{{ record.traceId.slice(0, 8) }}</td>
            <td class="legacy-left logistics-info-content">{{ displayValue(record.operationInfo) }}</td>
            <td>{{ formatDate(record.traceTime) }}</td>
            <td>{{ record.orderNo }}</td>
            <td>{{ record.logisticsNo }}</td>
            <td>{{ record.logisticsCompany }}</td>
            <td>{{ displayValue(record.receiverPhone) }}</td>
            <td><StatusPill :value="record.traceStatus" :tone="statusTone(record.traceStatus)" /></td>
            <td>{{ formatDate(record.createdAt) }}</td>
          </tr>
        </template>
      </tbody>
    </table>

    <div class="page_and_btn">
      <div class="dataTables_info">{{ pageSummary(records.length) }}</div>
    </div>
  </section>
</template>

<style scoped>
.logistics-info-search {
  row-gap: 10px;
}

.logistics-info-table {
  min-width: 1080px;
}

.logistics-info-content {
  min-width: 220px;
  max-width: 360px;
  white-space: normal;
}

.mono-cell {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
}
</style>
