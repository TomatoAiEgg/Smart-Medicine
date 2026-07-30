<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadAuditPerformanceDetailsCsv, listAuditPerformanceDetails } from '../../api/report';
import type { AuditPerformanceDetailRecord } from '../../api/types';
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

const detailFrom = ref(defaultDate(-13));
const detailTo = ref(defaultDate(0));
const records = ref<AuditPerformanceDetailRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRows = computed(() => records.value.length);
const totalAuditors = computed(() => new Set(records.value.map((row) => row.auditor)).size);
const totalApproved = computed(() => records.value.filter((row) => row.auditResult === 'APPROVED').length);
const totalRejected = computed(() => records.value.filter((row) => row.auditResult === 'REJECTED').length);
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalDoses = computed(() => records.value.reduce((total, row) => total + row.doseCount, 0));

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return String(value);
}

function resultLabel(value: string) {
  if (value === 'APPROVED') return '通过';
  if (value === 'REJECTED') return '驳回';
  return value;
}

async function refreshAuditPerformanceDetails() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listAuditPerformanceDetails({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条审核明细`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportAuditPerformanceDetails() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadAuditPerformanceDetailsCsv({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    saveBlob(`审核员绩效明细-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '审核员绩效明细 CSV 已导出');
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
      void refreshAuditPerformanceDetails();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshAuditPerformanceDetails,
});
</script>

<template>
  <section class="legacy-page audit-detail-page">
    <ul class="legacy-search audit-detail-search">
      <li>
        开始日期：
        <input v-model="detailFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshAuditPerformanceDetails" />
      </li>
      <li>
        结束日期：
        <input v-model="detailTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshAuditPerformanceDetails" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshAuditPerformanceDetails">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportAuditPerformanceDetails">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats audit-detail-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>审核明细</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalAuditors) }}</strong>
        <span>审核员</span>
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
        <strong>{{ formatNumber(totalPrescriptions) }}</strong>
        <span>处方数</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDoses) }}</strong>
        <span>剂数</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table audit-detail-table">
        <thead>
          <tr class="legacy-main-head">
            <th>审核时间</th>
            <th>审核员</th>
            <th>结果</th>
            <th>订单号</th>
            <th>外部订单号</th>
            <th>机构</th>
            <th>患者</th>
            <th>处方数</th>
            <th>剂数</th>
            <th>审核意见</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="10" class="legacy-empty">正在查询审核员绩效明细</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="10" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="`${row.orderNo}-${row.auditor}-${row.auditedAt || ''}`" class="legacy-main-info">
            <td>{{ formatDate(row.auditedAt) }}</td>
            <td><strong>{{ rowValue(row.auditor) }}</strong></td>
            <td>{{ resultLabel(row.auditResult) }}</td>
            <td>{{ rowValue(row.orderNo) }}</td>
            <td>{{ rowValue(row.externalOrderNo) }}</td>
            <td class="legacy-left">{{ rowValue(row.institutionName) }}</td>
            <td>{{ rowValue(row.patientName) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.doseCount) }}</td>
            <td class="legacy-left">{{ rowValue(row.reviewComment) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.audit-detail-search {
  row-gap: 10px;
}

.audit-detail-stats {
  margin-bottom: 10px;
}

.audit-detail-table {
  min-width: 1160px;
}

.audit-detail-table th,
.audit-detail-table td {
  min-width: 96px;
}
</style>
