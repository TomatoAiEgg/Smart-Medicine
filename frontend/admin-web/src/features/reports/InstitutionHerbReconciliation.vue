<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadInstitutionHerbReconciliationCsv, listInstitutionHerbReconciliation } from '../../api/report';
import type { InstitutionHerbReconciliationRecord } from '../../api/types';
import { saveBlob } from '../../domain/download';
import { decimalValue, displayValue, currentIsoDate, dateInputToIso, defaultDate, formatNumber, moneyValueOrZero as moneyValue, numericValueOrZero as numericValue } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const reconciliationFrom = ref(defaultDate(-13));
const reconciliationTo = ref(defaultDate(0));
const records = ref<InstitutionHerbReconciliationRecord[]>([]);
const loading = ref(false);
const exporting = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const totalRows = computed(() => records.value.length);
const totalInstitutions = computed(() => new Set(records.value.map((row) => row.institutionId)).size);
const totalDetails = computed(() => records.value.reduce((total, row) => total + row.detailCount, 0));
const totalPrescriptions = computed(() => records.value.reduce((total, row) => total + row.prescriptionCount, 0));
const totalOrders = computed(() => records.value.reduce((total, row) => total + row.orderCount, 0));
const totalQuantity = computed(() => records.value.reduce((total, row) => total + numericValue(row.totalQuantity), 0));
const totalAmount = computed(() => records.value.reduce((total, row) => total + numericValue(row.totalAmount), 0));
const totalSettlementAmount = computed(() => records.value.reduce((total, row) => total + numericValue(row.settlementAmount), 0));

async function refreshInstitutionHerbReconciliation() {
  if (loading.value) return;
  loading.value = true;
  errorLine.value = '';
  try {
    const nextRecords = await listInstitutionHerbReconciliation({
      from: dateInputToIso(reconciliationFrom.value),
      to: dateInputToIso(reconciliationTo.value, true),
    });
    records.value = nextRecords;
    loaded.value = true;
    emit('countChanged', nextRecords.length);
    emit('notice', 'success', `已查询 ${formatNumber(nextRecords.length)} 条机构药材对账记录`);
  } catch (error) {
    records.value = [];
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function exportInstitutionHerbReconciliation() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadInstitutionHerbReconciliationCsv({
      from: dateInputToIso(reconciliationFrom.value),
      to: dateInputToIso(reconciliationTo.value, true),
    });
    saveBlob(`机构药材统计-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '机构药材统计 CSV 已导出');
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
      void refreshInstitutionHerbReconciliation();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshInstitutionHerbReconciliation,
});
</script>

<template>
  <section class="legacy-page institution-herb-page">
    <ul class="legacy-search institution-herb-search">
      <li>
        开始日期：
        <input
          v-model="reconciliationFrom"
          class="legacy-input input-medium"
          type="date"
          @keyup.enter="refreshInstitutionHerbReconciliation"
        />
      </li>
      <li>
        结束日期：
        <input
          v-model="reconciliationTo"
          class="legacy-input input-medium"
          type="date"
          @keyup.enter="refreshInstitutionHerbReconciliation"
        />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshInstitutionHerbReconciliation">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportInstitutionHerbReconciliation">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats institution-herb-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>对账行</span>
      </li>
      <li>
        <strong>{{ formatNumber(totalInstitutions) }}</strong>
        <span>机构数</span>
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
      <table class="legacy-main-table institution-herb-table">
        <thead>
          <tr class="legacy-main-head">
            <th>机构编码</th>
            <th>机构名称</th>
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
            <td colspan="13" class="legacy-empty">正在查询机构药材统计</td>
          </tr>
          <tr v-else-if="records.length === 0" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr
            v-for="row in records"
            :key="`${row.institutionId}-${row.herbCode}-${row.herbName}-${row.drugSpecs || ''}`"
            class="legacy-main-info"
          >
            <td>{{ displayValue(row.institutionCode) }}</td>
            <td class="legacy-left"><strong>{{ displayValue(row.institutionName) }}</strong></td>
            <td>{{ displayValue(row.herbCode) }}</td>
            <td class="legacy-left">{{ displayValue(row.herbName) }}</td>
            <td>{{ displayValue(row.drugSpecs) }}</td>
            <td>{{ displayValue(row.drugOrigin) }}</td>
            <td>{{ displayValue(row.unit) }}</td>
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
.institution-herb-search {
  row-gap: 10px;
}

.institution-herb-stats {
  margin-bottom: 10px;
}

.institution-herb-table {
  min-width: 1360px;
}

.institution-herb-table th,
.institution-herb-table td {
  min-width: 92px;
}
</style>
