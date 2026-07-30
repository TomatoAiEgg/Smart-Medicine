<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { downloadAdminOrdersCsv, listAdminOrders } from '../../api/order';
import type {
  AdminOrderListItem,
  AdminOrderPage,
  AdminOrderQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { saveBlob } from '../../domain/download';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type NumericValue = number | string | null | undefined;

const EMPTY_VALUE = '-';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const orderStatus = ref('');
const keyword = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const page = ref(1);
const pageSize = ref(20);
const reconciliationPage = ref<AdminOrderPage | null>(null);
const loading = ref(false);
const exporting = ref(false);
const errorLine = ref('');

const rows = computed(() => reconciliationPage.value?.records ?? []);
const total = computed(() => reconciliationPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const pageAmount = computed(() => sumNumbers(rows.value.map((row) => row.totalAmount)));
const pagePrescriptionCount = computed(() => rows.value.reduce((totalCount, row) => totalCount + row.prescriptionCount, 0));
const pageDoseCount = computed(() => sumNumbers(rows.value.map((row) => row.doseCount)));

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return String(value);
}

function numericValue(value: NumericValue) {
  if (value === null || value === undefined || value === '') return null;
  const nextValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(nextValue) ? nextValue : null;
}

function sumNumbers(values: NumericValue[]) {
  let totalValue = 0;
  let hasValue = false;
  for (const value of values) {
    const nextValue = numericValue(value);
    if (nextValue !== null) {
      totalValue += nextValue;
      hasValue = true;
    }
  }
  return hasValue ? totalValue : null;
}

function moneyValue(value: NumericValue) {
  const nextValue = numericValue(value);
  return nextValue === null ? EMPTY_VALUE : nextValue.toFixed(2);
}

function amountValue(value: NumericValue) {
  const nextValue = numericValue(value);
  if (nextValue === null) return EMPTY_VALUE;
  return Number.isInteger(nextValue) ? String(nextValue) : String(Number(nextValue.toFixed(4)));
}

function statusText(status: string | null | undefined) {
  if (!status) return EMPTY_VALUE;
  const labels: Record<string, string> = {
    CREATED: '已创建',
    PENDING: '待处理',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    TERMINATED: '已终止',
    COMPLETED: '已完成',
    SUCCESS: '成功',
    FAILED: '失败',
    PACKED: '已打包',
    SHIPPED: '已发货',
    SIGNED: '已签收',
    AUDIT_PASSED: '审核通过',
    AUDIT_FAILED: '审核失败',
  };
  return labels[status] ?? status;
}

function hospitalTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    1: '门诊',
    2: '住院',
    3: '其他',
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    OTHER: '其他',
  };
  return type ? labels[type] ?? type : EMPTY_VALUE;
}

function deliveryTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    PATIENT: '送个人',
    PICKUP: '自提',
  };
  return type ? labels[type] ?? type : EMPTY_VALUE;
}

function receiverSummary(row: AdminOrderListItem) {
  const address = [row.receiverProvince, row.receiverCity, row.receiverZone, row.receiverAddress]
    .filter((item): item is string => !!item && item.trim().length > 0)
    .join('');
  const pieces = [row.receiverName, row.receiverPhone, address]
    .filter((item): item is string => !!item && item.trim().length > 0);
  return pieces.length > 0 ? pieces.join(' / ') : EMPTY_VALUE;
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

function queryParams(options: { includePaging: boolean }): AdminOrderQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    prescriptionType: prescriptionType.value,
    hospitalType: hospitalType.value,
    orderStatus: orderStatus.value,
    keyword: keyword.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    patientName: patientName.value,
    ...(options.includePaging ? { page: page.value, pageSize: pageSize.value } : {}),
  };
}

async function refreshPrescriptionReconciliation() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminOrders(queryParams({ includePaging: true }));
    reconciliationPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询到 ${nextPage.total} 条处方对账明细`);
  } catch (error) {
    reconciliationPage.value = null;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshPrescriptionReconciliation();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshPrescriptionReconciliation();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshPrescriptionReconciliation();
}

async function exportPrescriptionReconciliation() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadAdminOrdersCsv(queryParams({ includePaging: false }));
    saveBlob(`处方对账明细-${new Date().toISOString().slice(0, 10)}.csv`, blob);
    emit('notice', 'success', '处方对账明细已导出');
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    exporting.value = false;
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshPrescriptionReconciliation();
  },
  { immediate: true },
);

defineExpose({
  refreshPrescriptionReconciliation,
});
</script>

<template>
  <section class="legacy-page prescription-reconciliation-page">
    <ul class="legacy-search prescription-reconciliation-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构：
        <input v-model="institution" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input">
          <option value="">全部</option>
          <option value="DECOCTION">代煎</option>
          <option value="SELF_DECOCTION">自煎</option>
          <option value="HERBAL_PIECE">饮片</option>
          <option value="CREAM">膏方</option>
          <option value="PILL">丸剂</option>
          <option value="POWDER">散剂</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input">
          <option value="">全部</option>
          <option value="OUTPATIENT">门诊</option>
          <option value="INPATIENT">住院</option>
          <option value="OTHER">其他</option>
        </select>
      </li>
      <li>
        订单状态：
        <input v-model="orderStatus" class="legacy-input input-medium" placeholder="AUDIT_PASSED" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        订单/处方：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        患者：
        <input v-model="patientName" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        条数：
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="5" max="100" step="5" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting" @click="exportPrescriptionReconciliation">
          {{ exporting ? '导出中' : '导出 CSV' }}
        </button>
      </li>
    </ul>

    <p class="reconciliation-hint">
      当前明细口径来自订单/处方分页查询，金额为处方订单金额字段；机构结算汇总、对账批次和异步导出任务等待专用后端口径。
    </p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats reconciliation-stats">
      <li>
        <strong>{{ total }}</strong>
        <span>匹配记录</span>
      </li>
      <li>
        <strong>{{ pagePrescriptionCount }}</strong>
        <span>本页处方数</span>
      </li>
      <li>
        <strong>{{ amountValue(pageDoseCount) }}</strong>
        <span>本页剂数</span>
      </li>
      <li>
        <strong>{{ moneyValue(pageAmount) }}</strong>
        <span>本页金额</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table reconciliation-table">
        <thead>
          <tr class="legacy-main-head">
            <th>处方号</th>
            <th>机构处方号</th>
            <th>订单号</th>
            <th>机构</th>
            <th>患者</th>
            <th>门诊住院</th>
            <th>处方类型</th>
            <th>处方数</th>
            <th>明细数</th>
            <th>剂数</th>
            <th>金额</th>
            <th>配送</th>
            <th>收货信息</th>
            <th>订单状态</th>
            <th>物流状态</th>
            <th>接单时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="16" class="legacy-empty">正在查询处方对账明细</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="16" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" :key="`${row.orderId}-${row.prescriptionId}`" class="legacy-main-info">
            <td>{{ rowValue(row.prescriptionNos) }}</td>
            <td>{{ rowValue(row.externalPrescriptionNos) }}</td>
            <td>
              <strong>{{ row.orderNo }}</strong>
              <small>{{ row.externalOrderNo }}</small>
            </td>
            <td>{{ rowValue(row.institutionName) }}</td>
            <td>
              <strong>{{ rowValue(row.patientName) }}</strong>
              <small>{{ rowValue(row.patientPhone) }}</small>
            </td>
            <td>{{ hospitalTypeText(row.hospitalTypes) }}</td>
            <td>{{ rowValue(row.prescriptionTypes) }}</td>
            <td>{{ row.prescriptionCount }}</td>
            <td>{{ row.detailCount }}</td>
            <td>{{ amountValue(row.doseCount) }}</td>
            <td>{{ moneyValue(row.totalAmount) }}</td>
            <td>
              <strong>{{ deliveryTypeText(row.addressType) }}</strong>
              <small>{{ formatDate(row.deliveryTime) }}</small>
            </td>
            <td class="legacy-left">{{ receiverSummary(row) }}</td>
            <td><StatusPill :value="statusText(row.orderStatus)" :tone="statusTone(row.orderStatus)" /></td>
            <td>
              <StatusPill :value="statusText(row.logisticsStatus)" :tone="statusTone(row.logisticsStatus)" />
              <small>{{ rowValue(row.logisticsNo) }}</small>
            </td>
            <td>{{ formatDate(row.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">
      显示第 {{ rows.length > 0 ? (page - 1) * pageSize + 1 : 0 }} 至 {{ (page - 1) * pageSize + rows.length }} 项记录，共 {{ total }} 项
    </p>
    <div class="legacy-pagination">
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="goPreviousPage">上一页</button>
      <span>第 {{ page }} 页</span>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="goNextPage">下一页</button>
    </div>
  </section>
</template>

<style scoped>
.prescription-reconciliation-search {
  row-gap: 10px;
}

.reconciliation-hint {
  margin: 0 0 10px;
  color: #6f7d91;
  font-size: 13px;
}

.reconciliation-stats {
  margin-bottom: 10px;
}

.reconciliation-table {
  min-width: 1380px;
}

.reconciliation-table th,
.reconciliation-table td {
  min-width: 86px;
}

.reconciliation-table th:nth-child(13),
.reconciliation-table td:nth-child(13) {
  min-width: 220px;
  white-space: normal;
}
</style>
