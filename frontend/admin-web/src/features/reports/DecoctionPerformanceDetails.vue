<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { downloadDecoctionPerformanceDetailsCsv, listDecoctionPerformanceDetails } from '../../api/report';
import type { DecoctionPerformanceDetailRecord } from '../../api/types';
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
const records = ref<DecoctionPerformanceDetailRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRows = computed(() => records.value.length);
const totalOperators = computed(() => new Set(records.value.map((row) => row.operator)).size);
const totalDevices = computed(() => new Set(records.value.map((row) => row.deviceCode)).size);
const totalPrescriptions = computed(() => new Set(records.value.map((row) => row.prescriptionNo).filter(Boolean)).size);
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));

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

function actionLabel(value: string) {
  if (value === 'FINISH') return '完成';
  if (value === 'START') return '开始';
  if (value === 'CANCEL') return '取消';
  return value;
}

function resultLabel(value: string) {
  if (value === 'ACCEPTED') return '已接受';
  if (value === 'REJECTED') return '已拒绝';
  return value;
}

async function refreshDecoctionPerformanceDetails() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listDecoctionPerformanceDetails({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条煎煮明细`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportDecoctionPerformanceDetails() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadDecoctionPerformanceDetailsCsv({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    saveBlob(`煎煮员绩效明细-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '煎煮员绩效明细 CSV 已导出');
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
      void refreshDecoctionPerformanceDetails();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDecoctionPerformanceDetails,
});
</script>

<template>
  <section class="legacy-page decoction-detail-page">
    <ul class="legacy-search decoction-detail-search">
      <li>
        开始日期：
        <input v-model="detailFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshDecoctionPerformanceDetails" />
      </li>
      <li>
        结束日期：
        <input v-model="detailTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshDecoctionPerformanceDetails" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshDecoctionPerformanceDetails">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportDecoctionPerformanceDetails">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats decoction-detail-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>煎煮明细</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalOperators) }}</strong>
        <span>煎煮员</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDevices) }}</strong>
        <span>设备数</span>
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
      <table class="legacy-main-table decoction-detail-table">
        <thead>
          <tr class="legacy-main-head">
            <th>作业时间</th>
            <th>煎煮员</th>
            <th>动作</th>
            <th>结果</th>
            <th>订单号</th>
            <th>外部订单号</th>
            <th>机构</th>
            <th>患者</th>
            <th>任务号</th>
            <th>处方号</th>
            <th>设备</th>
            <th>水桶</th>
            <th>前状态</th>
            <th>后状态</th>
            <th>剂数</th>
            <th>来源</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="16" class="legacy-empty">正在查询煎煮员绩效明细</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="16" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="`${row.taskNo}-${row.operator}-${row.actionTime || ''}`" class="legacy-main-info">
            <td>{{ formatDate(row.actionTime) }}</td>
            <td><strong>{{ rowValue(row.operator) }}</strong></td>
            <td>{{ actionLabel(row.actionType) }}</td>
            <td>{{ resultLabel(row.actionResult) }}</td>
            <td>{{ rowValue(row.orderNo) }}</td>
            <td>{{ rowValue(row.externalOrderNo) }}</td>
            <td class="legacy-left">{{ rowValue(row.institutionName) }}</td>
            <td>{{ rowValue(row.patientName) }}</td>
            <td>{{ rowValue(row.taskNo) }}</td>
            <td>{{ rowValue(row.prescriptionNo) }}</td>
            <td>{{ rowValue(row.deviceCode) }}</td>
            <td>{{ rowValue(row.pailNo) }}</td>
            <td>{{ rowValue(row.taskStatusBefore) }}</td>
            <td>{{ rowValue(row.taskStatusAfter) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ rowValue(row.source) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.decoction-detail-search {
  row-gap: 10px;
}

.decoction-detail-stats {
  margin-bottom: 10px;
}

.decoction-detail-table {
  min-width: 1480px;
}

.decoction-detail-table th,
.decoction-detail-table td {
  min-width: 92px;
}
</style>
