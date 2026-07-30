<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  downloadInstitutionPrescriptionCountsCsv,
  listInstitutionPrescriptionCounts,
} from '../../api/report';
import type { InstitutionPrescriptionCountRecord } from '../../api/types';
import { saveBlob } from '../../domain/download';
import { dateInputToIso, defaultDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type NumericValue = number | string | null | undefined;

const EMPTY_VALUE = '-';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const countFrom = ref(defaultDate(-13));
const countTo = ref(defaultDate(0));
const records = ref<InstitutionPrescriptionCountRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalInstitutions = computed(() => records.value.length);
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));
const totalAmount = computed(() => sumNumbers(records.value.map((row) => row.totalAmount)));

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

function numericValue(value: NumericValue) {
  if (value === null || value === undefined || value === '') return null;
  const nextValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(nextValue) ? nextValue : null;
}

function sumNumbers(values: NumericValue[]) {
  let totalValue = 0;
  let hasValue = false;
  for (const value of values) {
    const nextValue = numericValue(value);
    if (nextValue !== null) {
      totalValue += nextValue;
      hasValue = true;
    }
  }
  return hasValue ? totalValue : null;
}

function moneyValue(value: NumericValue) {
  const nextValue = numericValue(value);
  return nextValue === null ? EMPTY_VALUE : nextValue.toFixed(2);
}

async function refreshInstitutionPrescriptionCounts() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listInstitutionPrescriptionCounts({
      from: dateInputToIso(countFrom.value),
      to: dateInputToIso(countTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 家机构处方统计`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportInstitutionPrescriptionCounts() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadInstitutionPrescriptionCountsCsv({
      from: dateInputToIso(countFrom.value),
      to: dateInputToIso(countTo.value, true),
    });
    saveBlob(`机构处方数量统计-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '机构处方数量统计 CSV 已导出');
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
      void refreshInstitutionPrescriptionCounts();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshInstitutionPrescriptionCounts,
});
</script>

<template>
  <section class="legacy-page institution-prescription-counts-page">
    <ul class="legacy-search institution-counts-search">
      <li>
        开始日期：
        <input v-model="countFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshInstitutionPrescriptionCounts" />
      </li>
      <li>
        结束日期：
        <input v-model="countTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshInstitutionPrescriptionCounts" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshInstitutionPrescriptionCounts">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportInstitutionPrescriptionCounts">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats institution-counts-stats">
      <li>
        <strong>{{ formatNumber(totalInstitutions) }}</strong>
        <span>机构数</span>
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
        <strong>{{ moneyValue(totalAmount) }}</strong>
        <span>金额</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table institution-counts-table">
        <thead>
          <tr class="legacy-main-head">
            <th>机构编码</th>
            <th>机构名称</th>
            <th>订单数</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="6" class="legacy-empty">正在查询机构处方数量统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="6" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="row.institutionId" class="legacy-main-info">
            <td>{{ rowValue(row.institutionCode) }}</td>
            <td class="legacy-left"><strong>{{ rowValue(row.institutionName) }}</strong></td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ moneyValue(row.totalAmount) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.institution-counts-search {
  row-gap: 10px;
}

.institution-counts-stats {
  margin-bottom: 10px;
}

.institution-counts-table {
  min-width: 860px;
}

.institution-counts-table th,
.institution-counts-table td {
  min-width: 96px;
}

.institution-counts-table th:nth-child(2),
.institution-counts-table td:nth-child(2) {
  min-width: 220px;
  white-space: normal;
}
</style>
