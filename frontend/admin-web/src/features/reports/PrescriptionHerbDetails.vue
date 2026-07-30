<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { downloadPrescriptionHerbDetailsCsv, listPrescriptionHerbDetails } from '../../api/report';
import type { PrescriptionHerbDetailRecord } from '../../api/types';
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
const records = ref<PrescriptionHerbDetailRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRows = computed(() => records.value.length);
const totalQuantity = computed(() => records.value.reduce((total, row) => total + numericValue(row.quantity), 0));
const totalAmount = computed(() => records.value.reduce((total, row) => total + numericValue(row.totalPrice), 0));
const totalSettlementAmount = computed(() => records.value.reduce((total, row) => total + numericValue(row.settlementTotalPrice), 0));
const totalOrders = computed(() => new Set(records.value.map((row) => row.orderNo)).size);
const totalPrescriptions = computed(() => new Set(records.value.map((row) => row.prescriptionNo)).size);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function numericValue(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') return 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function decimalValue(value: number | string | null | undefined, maximumFractionDigits = 4) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits,
  }).format(numericValue(value));
}

function moneyValue(value: number | string | null | undefined) {
  return decimalValue(value, 2);
}

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return String(value);
}

async function refreshPrescriptionHerbDetails() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listPrescriptionHerbDetails({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条药材明细`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportPrescriptionHerbDetails() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadPrescriptionHerbDetailsCsv({
      from: dateInputToIso(detailFrom.value),
      to: dateInputToIso(detailTo.value, true),
    });
    saveBlob(`药材明细列表-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '药材明细 CSV 已导出');
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
      void refreshPrescriptionHerbDetails();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshPrescriptionHerbDetails,
});
</script>

<template>
  <section class="legacy-page prescription-herb-page">
    <ul class="legacy-search prescription-herb-search">
      <li>
        开始日期：
        <input v-model="detailFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshPrescriptionHerbDetails" />
      </li>
      <li>
        结束日期：
        <input v-model="detailTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshPrescriptionHerbDetails" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshPrescriptionHerbDetails">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportPrescriptionHerbDetails">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats prescription-herb-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>明细行</span>
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
        <strong>{{ decimalValue(totalQuantity) }}</strong>
        <span>总数量</span>
      </li>
      <li>
        <strong>{{ moneyValue(totalAmount) }}</strong>
        <span>销售金额</span>
      </li>
      <li>
        <strong>{{ moneyValue(totalSettlementAmount) }}</strong>
        <span>结算金额</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table prescription-herb-table">
        <thead>
          <tr class="legacy-main-head">
            <th>处方时间</th>
            <th>机构</th>
            <th>订单号</th>
            <th>外部订单号</th>
            <th>处方号</th>
            <th>机构处方号</th>
            <th>药材编码</th>
            <th>药材名称</th>
            <th>规格</th>
            <th>产地</th>
            <th>剂量</th>
            <th>单位</th>
            <th>特殊用法</th>
            <th>数量</th>
            <th>单价</th>
            <th>销售金额</th>
            <th>结算单价</th>
            <th>结算金额</th>
            <th>批号</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="20" class="legacy-empty">正在查询药材明细</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="20" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr
            v-for="row in records"
            :key="`${row.orderNo}-${row.prescriptionNo}-${row.herbCode}-${row.herbName}-${row.batchNo || ''}`"
            class="legacy-main-info"
          >
            <td>{{ formatDate(row.prescriptionCreatedAt) }}</td>
            <td class="legacy-left">{{ rowValue(row.institutionName) }}</td>
            <td>{{ rowValue(row.orderNo) }}</td>
            <td>{{ rowValue(row.externalOrderNo) }}</td>
            <td>{{ rowValue(row.prescriptionNo) }}</td>
            <td>{{ rowValue(row.externalPrescriptionNo) }}</td>
            <td>{{ rowValue(row.herbCode) }}</td>
            <td class="legacy-left"><strong>{{ rowValue(row.herbName) }}</strong></td>
            <td>{{ rowValue(row.drugSpecs) }}</td>
            <td>{{ rowValue(row.drugOrigin) }}</td>
            <td>{{ rowValue(row.dose) }}</td>
            <td>{{ rowValue(row.unit) }}</td>
            <td>{{ rowValue(row.specialUsage) }}</td>
            <td>{{ decimalValue(row.quantity) }}</td>
            <td>{{ moneyValue(row.unitPrice) }}</td>
            <td>{{ moneyValue(row.totalPrice) }}</td>
            <td>{{ moneyValue(row.settlementUnitPrice) }}</td>
            <td>{{ moneyValue(row.settlementTotalPrice) }}</td>
            <td>{{ rowValue(row.batchNo) }}</td>
            <td>{{ rowValue(row.remark) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.prescription-herb-search {
  row-gap: 10px;
}

.prescription-herb-stats {
  margin-bottom: 10px;
}

.prescription-herb-table {
  min-width: 1920px;
}

.prescription-herb-table th,
.prescription-herb-table td {
  min-width: 92px;
}
</style>
