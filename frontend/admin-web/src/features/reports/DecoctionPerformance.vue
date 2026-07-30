<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadDecoctionPerformanceCsv, listDecoctionPerformance } from '../../api/report';
import type { DecoctionPerformanceRecord } from '../../api/types';
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
const records = ref<DecoctionPerformanceRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalOperators = computed(() => records.value.length);
const totalDecoctions = computed(() => records.value.reduce((total, row) => total + row.decoctionCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));
const totalDevices = computed(() => records.value.reduce((total, row) => total + row.deviceCount, 0));

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return String(value);
}

async function refreshDecoctionPerformance() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listDecoctionPerformance({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 名煎煮员绩效`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportDecoctionPerformance() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadDecoctionPerformanceCsv({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    saveBlob(`煎煮员绩效统计-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '煎煮员绩效统计 CSV 已导出');
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
      void refreshDecoctionPerformance();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDecoctionPerformance,
});
</script>

<template>
  <section class="legacy-page decoction-performance-page">
    <ul class="legacy-search decoction-performance-search">
      <li>
        开始日期：
        <input v-model="performanceFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshDecoctionPerformance" />
      </li>
      <li>
        结束日期：
        <input v-model="performanceTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshDecoctionPerformance" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshDecoctionPerformance">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportDecoctionPerformance">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats decoction-performance-stats">
      <li>
        <strong>{{ formatNumber(totalOperators) }}</strong>
        <span>煎煮员</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDecoctions) }}</strong>
        <span>煎煮次数</span>
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
        <strong>{{ formatNumber(totalDevices) }}</strong>
        <span>设备数</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table decoction-performance-table">
        <thead>
          <tr class="legacy-main-head">
            <th>煎煮员</th>
            <th>煎煮次数</th>
            <th>订单数</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>设备数</th>
            <th>首次完成</th>
            <th>末次完成</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">正在查询煎煮员绩效统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="row.operator" class="legacy-main-info">
            <td><strong>{{ rowValue(row.operator) }}</strong></td>
            <td>{{ formatNumber(row.decoctionCount) }}</td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ formatNumber(row.deviceCount) }}</td>
            <td>{{ formatDate(row.firstFinishedAt) }}</td>
            <td>{{ formatDate(row.lastFinishedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.decoction-performance-search {
  row-gap: 10px;
}

.decoction-performance-stats {
  margin-bottom: 10px;
}

.decoction-performance-table {
  min-width: 980px;
}

.decoction-performance-table th,
.decoction-performance-table td {
  min-width: 96px;
}
</style>
