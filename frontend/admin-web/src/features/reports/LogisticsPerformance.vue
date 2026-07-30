<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadLogisticsPerformanceCsv, listLogisticsPerformance } from '../../api/report';
import type { LogisticsPerformanceRecord } from '../../api/types';
import { saveBlob } from '../../domain/download';
import { currentIsoDate, dateInputToIso, defaultDate, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const EMPTY_VALUE = '-';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const performanceFrom = ref(defaultDate(-13));
const performanceTo = ref(defaultDate(0));
const records = ref<LogisticsPerformanceRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalCompanies = computed(() => records.value.length);
const totalShipments = computed(() => records.value.reduce((total, row) => total + row.shipmentCount, 0));
const totalShipped = computed(() => records.value.reduce((total, row) => total + row.shippedCount, 0));
const totalSigned = computed(() => records.value.reduce((total, row) => total + row.signedCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));
const totalPackageWeight = computed(() => records.value.reduce((total, row) => total + numericValue(row.totalPackageWeight), 0));
const totalPackageCount = computed(() => records.value.reduce((total, row) => total + row.packageCount, 0));

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return String(value);
}

function numericValue(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') return 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatWeight(value: number | string | null | undefined) {
  const parsed = numericValue(value);
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 3,
  }).format(parsed);
}

async function refreshLogisticsPerformance() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listLogisticsPerformance({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 家物流绩效`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportLogisticsPerformance() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadLogisticsPerformanceCsv({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    saveBlob(`物流绩效统计-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '物流绩效统计 CSV 已导出');
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    exporting.value = false;
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshLogisticsPerformance();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshLogisticsPerformance,
});
</script>

<template>
  <section class="legacy-page logistics-performance-page">
    <ul class="legacy-search logistics-performance-search">
      <li>
        开始日期：
        <input v-model="performanceFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshLogisticsPerformance" />
      </li>
      <li>
        结束日期：
        <input v-model="performanceTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshLogisticsPerformance" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshLogisticsPerformance">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportLogisticsPerformance">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats logistics-performance-stats">
      <li>
        <strong>{{ formatNumber(totalCompanies) }}</strong>
        <span>物流公司</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalShipments) }}</strong>
        <span>发货单</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalShipped) }}</strong>
        <span>已出库</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalSigned) }}</strong>
        <span>已签收</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalOrders) }}</strong>
        <span>订单数</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalPrescriptions) }}</strong>
        <span>处方数</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDoses) }}</strong>
        <span>剂数</span>
      </li>
      <li>
        <strong>{{ formatWeight(totalPackageWeight) }}</strong>
        <span>重量</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalPackageCount) }}</strong>
        <span>包裹数</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table logistics-performance-table">
        <thead>
          <tr class="legacy-main-head">
            <th>物流公司</th>
            <th>发货单</th>
            <th>已出库</th>
            <th>已签收</th>
            <th>订单数</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>重量</th>
            <th>包裹数</th>
            <th>首次出库</th>
            <th>末次出库</th>
            <th>首次签收</th>
            <th>末次签收</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">正在查询物流绩效统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="row.logisticsCompany" class="legacy-main-info">
            <td><strong>{{ rowValue(row.logisticsCompany) }}</strong></td>
            <td>{{ formatNumber(row.shipmentCount) }}</td>
            <td>{{ formatNumber(row.shippedCount) }}</td>
            <td>{{ formatNumber(row.signedCount) }}</td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ formatWeight(row.totalPackageWeight) }}</td>
            <td>{{ formatNumber(row.packageCount) }}</td>
            <td>{{ formatDate(row.firstOutboundAt) }}</td>
            <td>{{ formatDate(row.lastOutboundAt) }}</td>
            <td>{{ formatDate(row.firstSignedAt) }}</td>
            <td>{{ formatDate(row.lastSignedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.logistics-performance-search {
  row-gap: 10px;
}

.logistics-performance-stats {
  margin-bottom: 10px;
}

.logistics-performance-table {
  min-width: 1320px;
}

.logistics-performance-table th,
.logistics-performance-table td {
  min-width: 88px;
}
</style>
