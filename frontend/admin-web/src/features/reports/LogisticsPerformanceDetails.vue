<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { downloadLogisticsPerformanceDetailsCsv, listLogisticsPerformanceDetails } from '../../api/report';
import type { LogisticsPerformanceDetailRecord } from '../../api/types';
import { saveBlob } from '../../domain/download';
import { dateInputToIso, defaultDate, formatDate, formatNumber } from '../../domain/formatters';

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

const detailFrom = ref(defaultDate(-13));
const detailTo = ref(defaultDate(0));
const records = ref<LogisticsPerformanceDetailRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRows = computed(() => records.value.length);
const totalCompanies = computed(() => new Set(records.value.map((row) => row.logisticsCompany)).size);
const totalSigned = computed(() => records.value.filter((row) => row.logisticsStatus === 'SIGNED').length);
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));
const totalPackageWeight = computed(() => records.value.reduce((total, row) => total + numericValue(row.packageWeight), 0));
const totalPackageCount = computed(() => records.value.reduce((total, row) => total + row.packageCount, 0));

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

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

function logisticsStatusLabel(value: string | null) {
  if (value === 'PACKED') return '已打包';
  if (value === 'SHIPPED') return '已发货';
  if (value === 'SIGNED') return '已签收';
  return rowValue(value);
}

async function refreshLogisticsPerformanceDetails() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listLogisticsPerformanceDetails({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条物流绩效明细`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportLogisticsPerformanceDetails() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadLogisticsPerformanceDetailsCsv({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    saveBlob(`物流绩效明细-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '物流绩效明细 CSV 已导出');
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
      void refreshLogisticsPerformanceDetails();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshLogisticsPerformanceDetails,
});
</script>

<template>
  <section class="legacy-page logistics-detail-page">
    <ul class="legacy-search logistics-detail-search">
      <li>
        开始日期：
        <input v-model="detailFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshLogisticsPerformanceDetails" />
      </li>
      <li>
        结束日期：
        <input v-model="detailTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshLogisticsPerformanceDetails" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshLogisticsPerformanceDetails">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportLogisticsPerformanceDetails">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats logistics-detail-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>物流明细</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalCompanies) }}</strong>
        <span>物流公司</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalSigned) }}</strong>
        <span>已签收</span>
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
      <table class="legacy-main-table logistics-detail-table">
        <thead>
          <tr class="legacy-main-head">
            <th>物流公司</th>
            <th>物流单号</th>
            <th>状态</th>
            <th>订单号</th>
            <th>外部订单号</th>
            <th>机构</th>
            <th>患者</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>重量</th>
            <th>包裹数</th>
            <th>打包时间</th>
            <th>出库时间</th>
            <th>签收时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="14" class="legacy-empty">正在查询物流绩效明细</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="14" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="`${row.orderNo}-${row.logisticsNo || ''}`" class="legacy-main-info">
            <td><strong>{{ rowValue(row.logisticsCompany) }}</strong></td>
            <td>{{ rowValue(row.logisticsNo) }}</td>
            <td>{{ logisticsStatusLabel(row.logisticsStatus) }}</td>
            <td>{{ rowValue(row.orderNo) }}</td>
            <td>{{ rowValue(row.externalOrderNo) }}</td>
            <td class="legacy-left">{{ rowValue(row.institutionName) }}</td>
            <td>{{ rowValue(row.patientName) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ formatWeight(row.packageWeight) }}</td>
            <td>{{ formatNumber(row.packageCount) }}</td>
            <td>{{ formatDate(row.packageTime) }}</td>
            <td>{{ formatDate(row.outboundTime) }}</td>
            <td>{{ formatDate(row.signTime) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.logistics-detail-search {
  row-gap: 10px;
}

.logistics-detail-stats {
  margin-bottom: 10px;
}

.logistics-detail-table {
  min-width: 1420px;
}

.logistics-detail-table th,
.logistics-detail-table td {
  min-width: 92px;
}
</style>
