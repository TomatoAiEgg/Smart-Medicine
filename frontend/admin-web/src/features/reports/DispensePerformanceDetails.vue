<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadDispensePerformanceDetailsCsv, listDispensePerformanceDetails } from '../../api/report';
import type { DispensePerformanceDetailRecord } from '../../api/types';
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

const detailFrom = ref(defaultDate(-13));
const detailTo = ref(defaultDate(0));
const records = ref<DispensePerformanceDetailRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRows = computed(() => records.value.length);
const totalDispensers = computed(() => new Set(records.value.map((row) => row.dispenser)).size);
const totalPrinted = computed(() => records.value.filter((row) => row.printStatus === 'PRINTED').length);
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));

function printStatusLabel(value: string | null) {
  if (value === 'PRINTED') return '已打印';
  if (value === 'PENDING') return '待打印';
  if (value === 'FAILED') return '打印失败';
  return displayValue(value);
}

async function refreshDispensePerformanceDetails() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listDispensePerformanceDetails({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条调剂明细`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportDispensePerformanceDetails() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadDispensePerformanceDetailsCsv({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    saveBlob(`调剂员绩效明细-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '调剂员绩效明细 CSV 已导出');
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
      void refreshDispensePerformanceDetails();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDispensePerformanceDetails,
});
</script>

<template>
  <section class="legacy-page dispense-detail-page">
    <ul class="legacy-search dispense-detail-search">
      <li>
        开始日期：
        <input v-model="detailFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshDispensePerformanceDetails" />
      </li>
      <li>
        结束日期：
        <input v-model="detailTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshDispensePerformanceDetails" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshDispensePerformanceDetails">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportDispensePerformanceDetails">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats dispense-detail-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>调剂明细</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDispensers) }}</strong>
        <span>调剂员</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalPrinted) }}</strong>
        <span>已打印</span>
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
      <table class="legacy-main-table dispense-detail-table">
        <thead>
          <tr class="legacy-main-head">
            <th>调剂时间</th>
            <th>调剂员</th>
            <th>打印状态</th>
            <th>订单号</th>
            <th>外部订单号</th>
            <th>机构</th>
            <th>患者</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>调剂备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="10" class="legacy-empty">正在查询调剂员绩效明细</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="10" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="`${row.orderNo}-${row.dispenser}-${row.dispensedAt || ''}`" class="legacy-main-info">
            <td>{{ formatDate(row.dispensedAt) }}</td>
            <td><strong>{{ displayValue(row.dispenser) }}</strong></td>
            <td>{{ printStatusLabel(row.printStatus) }}</td>
            <td>{{ displayValue(row.orderNo) }}</td>
            <td>{{ displayValue(row.externalOrderNo) }}</td>
            <td class="legacy-left">{{ displayValue(row.institutionName) }}</td>
            <td>{{ displayValue(row.patientName) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td class="legacy-left">{{ displayValue(row.dispenseComment) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.dispense-detail-search {
  row-gap: 10px;
}

.dispense-detail-stats {
  margin-bottom: 10px;
}

.dispense-detail-table {
  min-width: 1160px;
}

.dispense-detail-table th,
.dispense-detail-table td {
  min-width: 96px;
}
</style>
