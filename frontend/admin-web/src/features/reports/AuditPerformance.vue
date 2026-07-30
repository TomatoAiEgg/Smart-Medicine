<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadAuditPerformanceCsv, listAuditPerformance } from '../../api/report';
import type { AuditPerformanceRecord } from '../../api/types';
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
const records = ref<AuditPerformanceRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalAuditors = computed(() => records.value.length);
const totalAudits = computed(() => records.value.reduce((total, row) => total + row.auditCount, 0));
const totalApproved = computed(() => records.value.reduce((total, row) => total + row.approvedCount, 0));
const totalRejected = computed(() => records.value.reduce((total, row) => total + row.rejectedCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));

async function refreshAuditPerformance() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listAuditPerformance({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 名审核员绩效`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportAuditPerformance() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadAuditPerformanceCsv({
      from: dateInputToIso(performanceFrom.value),
      to: dateInputToIso(performanceTo.value, true),
    });
    saveBlob(`审核员绩效统计-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '审核员绩效统计 CSV 已导出');
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
      void refreshAuditPerformance();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshAuditPerformance,
});
</script>

<template>
  <section class="legacy-page audit-performance-page">
    <ul class="legacy-search audit-performance-search">
      <li>
        开始日期：
        <input v-model="performanceFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshAuditPerformance" />
      </li>
      <li>
        结束日期：
        <input v-model="performanceTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshAuditPerformance" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshAuditPerformance">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportAuditPerformance">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats audit-performance-stats">
      <li>
        <strong>{{ formatNumber(totalAuditors) }}</strong>
        <span>审核员</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalAudits) }}</strong>
        <span>审核次数</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalApproved) }}</strong>
        <span>通过</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalRejected) }}</strong>
        <span>驳回</span>
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
      <table class="legacy-main-table audit-performance-table">
        <thead>
          <tr class="legacy-main-head">
            <th>审核员</th>
            <th>审核次数</th>
            <th>通过</th>
            <th>驳回</th>
            <th>订单数</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>首次审核</th>
            <th>末次审核</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="9" class="legacy-empty">正在查询审核员绩效统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="9" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="row.auditor" class="legacy-main-info">
            <td><strong>{{ displayValue(row.auditor) }}</strong></td>
            <td>{{ formatNumber(row.auditCount) }}</td>
            <td>{{ formatNumber(row.approvedCount) }}</td>
            <td>{{ formatNumber(row.rejectedCount) }}</td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td>{{ formatDate(row.firstAuditedAt) }}</td>
            <td>{{ formatDate(row.lastAuditedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.audit-performance-search {
  row-gap: 10px;
}

.audit-performance-stats {
  margin-bottom: 10px;
}

.audit-performance-table {
  min-width: 1100px;
}

.audit-performance-table th,
.audit-performance-table td {
  min-width: 90px;
}
</style>
