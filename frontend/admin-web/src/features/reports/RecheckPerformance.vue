<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadRecheckPerformanceCsv, listRecheckPerformance } from '../../api/report';
import type { RecheckPerformanceRecord } from '../../api/types';
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

const performanceFrom = ref(defaultDate(-13));
const performanceTo = ref(defaultDate(0));
const records = ref<RecheckPerformanceRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRecheckers = computed(() => records.value.length);
const totalRechecks = computed(() => records.value.reduce((total, row) => total + row.recheckCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return String(value);
}

async function refreshRecheckPerformance() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listRecheckPerformance({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 名复核员绩效`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportRecheckPerformance() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadRecheckPerformanceCsv({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    saveBlob(`复核员绩效统计-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '复核员绩效统计 CSV 已导出');
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
      void refreshRecheckPerformance();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshRecheckPerformance,
});
</script>

<template>
  <section class="legacy-page recheck-performance-page">
    <ul class="legacy-search recheck-performance-search">
      <li>
        开始日期：
        <input v-model="performanceFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshRecheckPerformance" />
      </li>
      <li>
        结束日期：
        <input v-model="performanceTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshRecheckPerformance" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshRecheckPerformance">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportRecheckPerformance">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats recheck-performance-stats">
      <li>
        <strong>{{ formatNumber(totalRecheckers) }}</strong>
        <span>复核员</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalRechecks) }}</strong>
        <span>复核次数</span>
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
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table recheck-performance-table">
        <thead>
          <tr class="legacy-main-head">
            <th>复核员</th>
            <th>复核次数</th>
            <th>订单数</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>首次复核</th>
            <th>末次复核</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">正在查询复核员绩效统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="row.rechecker" class="legacy-main-info">
            <td><strong>{{ rowValue(row.rechecker) }}</strong></td>
            <td>{{ formatNumber(row.recheckCount) }}</td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ formatDate(row.firstRecheckedAt) }}</td>
            <td>{{ formatDate(row.lastRecheckedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.recheck-performance-search {
  row-gap: 10px;
}

.recheck-performance-stats {
  margin-bottom: 10px;
}

.recheck-performance-table {
  min-width: 920px;
}

.recheck-performance-table th,
.recheck-performance-table td {
  min-width: 98px;
}
</style>
