<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { downloadHerbDosageCsv, listHerbDosage } from '../../api/report';
import type { HerbDosageRecord } from '../../api/types';
import { saveBlob } from '../../domain/download';
import { dateInputToIso, defaultDate, formatNumber } from '../../domain/formatters';

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

const dosageFrom = ref(defaultDate(-13));
const dosageTo = ref(defaultDate(0));
const records = ref<HerbDosageRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalHerbs = computed(() => records.value.length);
const totalDetails = computed(() => records.value.reduce((total, row) => total + row.detailCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalQuantity = computed(() => records.value.reduce((total, row) => total + numericValue(row.totalQuantity), 0));
const totalAmount = computed(() => records.value.reduce((total, row) => total + numericValue(row.totalAmount), 0));
const totalSettlementAmount = computed(() => records.value.reduce((total, row) => total + numericValue(row.settlementAmount), 0));

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

async function refreshHerbDosage() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listHerbDosage({
      from: dateInputToIso(dosageFrom.value),
      to: dateInputToIso(dosageTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条药材用量统计`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportHerbDosage() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadHerbDosageCsv({
      from: dateInputToIso(dosageFrom.value),
      to: dateInputToIso(dosageTo.value, true),
    });
    saveBlob(`药材用量统计-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '药材用量统计 CSV 已导出');
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
      void refreshHerbDosage();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshHerbDosage,
});
</script>

<template>
  <section class="legacy-page herb-dosage-page">
    <ul class="legacy-search herb-dosage-search">
      <li>
        开始日期：
        <input v-model="dosageFrom" class="legacy-input input-medium" type="date" @keyup.enter="refreshHerbDosage" />
      </li>
      <li>
        结束日期：
        <input v-model="dosageTo" class="legacy-input input-medium" type="date" @keyup.enter="refreshHerbDosage" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshHerbDosage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportHerbDosage">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats herb-dosage-stats">
      <li>
        <strong>{{ formatNumber(totalHerbs) }}</strong>
        <span>药材数</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalDetails) }}</strong>
        <span>明细行</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalPrescriptions) }}</strong>
        <span>处方数</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalOrders) }}</strong>
        <span>订单数</span>
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
      <table class="legacy-main-table herb-dosage-table">
        <thead>
          <tr class="legacy-main-head">
            <th>药材编码</th>
            <th>药材名称</th>
            <th>规格</th>
            <th>产地</th>
            <th>单位</th>
            <th>明细行</th>
            <th>处方数</th>
            <th>订单数</th>
            <th>总数量</th>
            <th>销售金额</th>
            <th>结算金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="11" class="legacy-empty">正在查询药材用量统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="11" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in records" :key="`${row.herbCode}-${row.herbName}-${row.drugSpecs || ''}`" class="legacy-main-info">
            <td>{{ rowValue(row.herbCode) }}</td>
            <td class="legacy-left"><strong>{{ rowValue(row.herbName) }}</strong></td>
            <td>{{ rowValue(row.drugSpecs) }}</td>
            <td>{{ rowValue(row.drugOrigin) }}</td>
            <td>{{ rowValue(row.unit) }}</td>
            <td>{{ formatNumber(row.detailCount) }}</td>
            <td>{{ formatNumber(row.prescriptionCount) }}</td>
            <td>{{ formatNumber(row.orderCount) }}</td>
            <td>{{ decimalValue(row.totalQuantity) }}</td>
            <td>{{ moneyValue(row.totalAmount) }}</td>
            <td>{{ moneyValue(row.settlementAmount) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.herb-dosage-search {
  row-gap: 10px;
}

.herb-dosage-stats {
  margin-bottom: 10px;
}

.herb-dosage-table {
  min-width: 1180px;
}

.herb-dosage-table th,
.herb-dosage-table td {
  min-width: 90px;
}
</style>
