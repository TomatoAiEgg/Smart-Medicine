<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadDispensePerformanceCsv, listDispensePerformance } from '../../api/report';
import type { DispensePerformanceRecord } from '../../api/types';
import { saveBlob } from '../../domain/download';
import { displayValue, currentIsoDate, dateInputToIso, defaultDate, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

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
const records = ref<DispensePerformanceRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalDispensers = computed(() => records.value.length);
const totalDispenses = computed(() => records.value.reduce((total, row) => total + row.dispenseCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));

async function refreshDispensePerformance() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listDispensePerformance({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 名调剂员绩效`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportDispensePerformance() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadDispensePerformanceCsv({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    saveBlob(`调剂员绩效统计-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '调剂员绩效统计 CSV 已导出');
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
      void refreshDispensePerformance();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDispensePerformance,
});
</script>

<template>
  <section class="legacy-page dispense-performance-page">
    <ul class="legacy-search dispense-performance-search">
      <li>
        开始日期：
        <input v-model="performanceFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshDispensePerformance" />
      </li>
      <li>
        结束日期：
        <input v-model="performanceTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshDispensePerformance" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshDispensePerformance">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportDispensePerformance">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats dispense-performance-stats">
      <li>
        <strong>{{ formatNumber(totalDispensers) }}</strong>
        <span>调剂员</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDispenses) }}</strong>
        <span>调剂次数</span>
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
      <table class="legacy-main-table dispense-performance-table">
        <thead>
          <tr class="legacy-main-head">
            <th>调剂员</th>
            <th>调剂次数</th>
            <th>订单数</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>首次调剂</th>
            <th>末次调剂</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">正在查询调剂员绩效统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="row.dispenser" class="legacy-main-info">
            <td><strong>{{ displayValue(row.dispenser) }}</strong></td>
            <td>{{ formatNumber(row.dispenseCount) }}</td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ formatDate(row.firstDispensedAt) }}</td>
            <td>{{ formatDate(row.lastDispensedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.dispense-performance-search {
  row-gap: 10px;
}

.dispense-performance-stats {
  margin-bottom: 10px;
}

.dispense-performance-table {
  min-width: 920px;
}

.dispense-performance-table th,
.dispense-performance-table td {
  min-width: 98px;
}
</style>
