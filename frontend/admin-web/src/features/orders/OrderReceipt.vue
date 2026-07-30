<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  batchReceiptAdminOrders,
  listAdminOrderReceipts,
  receiptAdminOrder,
} from '../../api/order';
import type {
  AdminBatchOrderReceiptResult,
  AdminOrderReceiptItem,
  AdminOrderReceiptPage,
  AdminOrderReceiptQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { displayValue, currentIsoDate, formatDate, labelFromMap } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

interface ReceiptForm {
  orderNo: string;
  operator: string;
  reason: string;
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const prescriptionNo = ref('');
const receiverName = ref('');
const receiverPhone = ref('');
const patientName = ref('');
const page = ref(1);
const pageSize = ref(20);
const receiptPage = ref<AdminOrderReceiptPage | null>(null);
const loading = ref(false);
const submitting = ref(false);
const batchSubmitting = ref(false);
const errorLine = ref('');
const selectedOrder = ref<AdminOrderReceiptItem | null>(null);
const batchText = ref('');
const batchOperator = ref('admin');
const batchReason = ref('');
const batchResult = ref<AdminBatchOrderReceiptResult | null>(null);
const receiptForm = ref<ReceiptForm>({
  orderNo: '',
  operator: 'admin',
  reason: '',
});

const rows = computed(() => receiptPage.value?.records ?? []);
const total = computed(() => receiptPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const batchOrderNos = computed(() => splitOrderNos(batchText.value));
const batchFailedItems = computed(() => batchResult.value?.items.filter((item) => !item.success) ?? []);

function fullAddress(row: AdminOrderReceiptItem) {
  return [
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ].filter(Boolean).join('') || '-';
}

function prescriptionTypeText(value: string | null | undefined) {
  if (!value) return '-';
  const labels: Record<string, string> = {
    DECOCTION: '代煎',
    SELF_DECOCTION: '自煎',
    OTHER: '其他',
    HERBAL_PIECE: '饮片',
    CREAM: '膏方',
    PILL: '丸剂',
    POWDER: '散剂',
    1: '饮片',
    2: '代煎',
    3: '膏方',
    4: '丸剂',
    5: '散剂',
  };
  return value.split(',').map((item) => labelFromMap(item, labels, item)).join('，');
}

function queryParams(): AdminOrderReceiptQueryParams {
  return {
    prescriptionNo: prescriptionNo.value,
    receiverName: receiverName.value,
    receiverPhone: receiverPhone.value,
    patientName: patientName.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function splitOrderNos(value: string) {
  return Array.from(new Set(value.split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)));
}

function downloadReceiptCsv() {
  downloadCsv(
    `待签收订单-${currentIsoDate()}.csv`,
    ['订单号', '外部订单号', '机构名称', '收货人', '收货电话', '收货地址', '患者', '处方类型', '物流公司', '物流单号', '订单状态', '物流状态', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.orderNo,
      row.externalOrderNo,
      row.institutionName,
      row.receiverName,
      row.receiverPhone,
      fullAddress(row),
      row.patientName,
      prescriptionTypeText(row.prescriptionTypes),
      row.logisticsCompany,
      row.logisticsNo,
      row.orderStatus,
      row.logisticsStatus,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${rows.value.length} 条待签收订单`);
}

async function refreshOrderReceipts() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminOrderReceipts(queryParams());
    receiptPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('notice', 'success', `已查询到 ${nextPage.total} 条待签收订单`);
  } catch (error) {
    receiptPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderReceipts();
}

function openReceipt(row: AdminOrderReceiptItem) {
  selectedOrder.value = row;
  receiptForm.value = {
    orderNo: row.orderNo,
    operator: receiptForm.value.operator || 'admin',
    reason: '',
  };
}

function closeReceipt() {
  selectedOrder.value = null;
  receiptForm.value.reason = '';
}

async function submitReceipt() {
  if (!selectedOrder.value) {
    errorLine.value = '请先选择订单';
    return;
  }
  submitting.value = true;
  errorLine.value = '';
  try {
    await receiptAdminOrder(selectedOrder.value.orderNo, {
      operator: receiptForm.value.operator.trim() || 'admin',
      reason: receiptForm.value.reason.trim(),
    });
    emit('notice', 'success', `订单 ${selectedOrder.value.orderNo} 已签收`);
    closeReceipt();
    await refreshOrderReceipts();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function submitBatchReceipt() {
  if (batchOrderNos.value.length === 0) {
    errorLine.value = '请输入要批量签收的订单号';
    return;
  }
  batchSubmitting.value = true;
  batchResult.value = null;
  errorLine.value = '';
  try {
    const result = await batchReceiptAdminOrders({
      orderNos: batchOrderNos.value,
      operator: batchOperator.value.trim() || 'admin',
      reason: batchReason.value.trim(),
    });
    batchResult.value = result;
    emit('notice', result.failCount > 0 ? 'info' : 'success', `批量签收成功 ${result.successCount} 条，失败 ${result.failCount} 条`);
    await refreshOrderReceipts();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    batchSubmitting.value = false;
  }
}

async function importBatchFile(event: Event) {
  if (!(event.target instanceof HTMLInputElement)) return;
  const file = event.target.files?.[0];
  if (!file) return;
  batchText.value = await file.text();
  event.target.value = '';
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderReceipts();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderReceipts();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshOrderReceipts();
  },
  { immediate: true },
);

defineExpose({
  refreshOrderReceipts,
});
</script>

<template>
  <section class="legacy-page order-list-page order-receipt-page">
    <ul class="legacy-search order-list-search">
      <li>
        处方号：
        <input v-model="prescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        收货人姓名：
        <input v-model="receiverName" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        收货人电话：
        <input v-model="receiverPhone" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        病人姓名：
        <input v-model="patientName" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadReceiptCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-main-table order-receipt-table">
        <thead>
          <tr class="legacy-main-head">
            <th>订单号</th>
            <th>机构名称</th>
            <th>收货人姓名</th>
            <th>收货人电话</th>
            <th>收货信息</th>
            <th>病人姓名</th>
            <th>处方类型</th>
            <th>物流单号</th>
            <th>订单状态</th>
            <th>物流状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="11" class="legacy-empty">正在查询待签收订单</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="11" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" :key="row.orderId" class="legacy-main-info">
            <td>{{ row.orderNo }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td>{{ displayValue(row.receiverName) }}</td>
            <td>{{ displayValue(row.receiverPhone) }}</td>
            <td class="legacy-left">{{ fullAddress(row) }}</td>
            <td>{{ displayValue(row.patientName) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionTypes) }}</td>
            <td>{{ displayValue(row.logisticsNo) }}</td>
            <td><StatusPill :value="row.orderStatus" :tone="statusTone(row.orderStatus)" /></td>
            <td><StatusPill :value="row.logisticsStatus || '-'" :tone="statusTone(row.logisticsStatus || '')" /></td>
            <td>
              <button class="legacy-link-btn workflow-pass-btn" type="button" @click="openReceipt(row)">
                手动签收
              </button>
            </td>
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

    <section class="legacy-panel batch-receipt-panel">
      <div class="legacy-section-title">
        <h2>批量签收</h2>
      </div>
      <ul class="legacy-search batch-receipt-form">
        <li class="batch-receipt-file">
          订单号文件：
          <input class="legacy-input input-large" type="file" accept=".txt,.csv" @change="importBatchFile" />
        </li>
        <li>
          操作人：
          <input v-model="batchOperator" class="legacy-input" />
        </li>
        <li class="batch-receipt-reason">
          签收备注：
          <input v-model="batchReason" class="legacy-input input-large" placeholder="批量签收备注" />
        </li>
      </ul>
      <textarea
        v-model="batchText"
        class="legacy-input batch-receipt-textarea"
        placeholder="从 Excel 复制订单号列，或每行输入一个订单号"
      />
      <div class="batch-receipt-actions">
        <span>待提交 {{ batchOrderNos.length }} 个订单号</span>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="batchSubmitting" @click="submitBatchReceipt">
          {{ batchSubmitting ? '提交中' : '提交批量签收' }}
        </button>
      </div>
      <div v-if="batchResult" class="batch-receipt-result">
        <strong>批量结果：成功 {{ batchResult.successCount }} 条，失败 {{ batchResult.failCount }} 条</strong>
        <table v-if="batchFailedItems.length > 0" class="legacy-main-table batch-failed-table">
          <thead>
            <tr class="legacy-main-head">
              <th>订单号</th>
              <th>失败原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in batchFailedItems" :key="item.orderNo" class="legacy-main-info">
              <td>{{ item.orderNo }}</td>
              <td class="legacy-left">{{ item.message }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="selectedOrder" class="receipt-modal-mask">
      <section class="receipt-modal legacy-panel">
        <div class="legacy-section-title">
          <h2>订单手动签收</h2>
        </div>
        <div class="legacy-detail-grid">
          <span>订单号</span>
          <strong>{{ selectedOrder.orderNo }}</strong>
          <span>收货人</span>
          <strong>{{ displayValue(selectedOrder.receiverName) }} / {{ displayValue(selectedOrder.receiverPhone) }}</strong>
          <span>收货信息</span>
          <strong>{{ fullAddress(selectedOrder) }}</strong>
          <span>当前状态</span>
          <strong><StatusPill :value="selectedOrder.orderStatus" :tone="statusTone(selectedOrder.orderStatus)" /></strong>
        </div>
        <ul class="legacy-search receipt-form">
          <li>
            操作人：
            <input v-model="receiptForm.operator" class="legacy-input" />
          </li>
          <li class="receipt-reason-input">
            签收备注：
            <input v-model="receiptForm.reason" class="legacy-input input-large" placeholder="备注不要换行" @keyup.enter="submitReceipt" />
          </li>
        </ul>
        <div class="receipt-modal-actions">
          <button class="legacy-btn" type="button" :disabled="submitting" @click="closeReceipt">关闭</button>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitReceipt">
            {{ submitting ? '提交中' : '提交' }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.batch-receipt-panel {
  margin-top: 14px;
}

.batch-receipt-form {
  margin-bottom: 10px;
}

.batch-receipt-file,
.batch-receipt-reason,
.receipt-reason-input {
  min-width: 360px;
}

.batch-receipt-textarea {
  box-sizing: border-box;
  min-height: 120px;
  padding: 8px;
  width: 100%;
  resize: vertical;
}

.batch-receipt-actions,
.receipt-modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}

.batch-receipt-actions span {
  color: #6f7d91;
  font-size: 13px;
}

.batch-receipt-result {
  margin-top: 12px;
}

.batch-failed-table {
  margin-top: 8px;
}

.receipt-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgb(15 23 42 / 38%);
}

.receipt-modal {
  width: min(720px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
}

.receipt-form {
  margin-top: 12px;
}

@media (max-width: 780px) {
  .batch-receipt-file,
  .batch-receipt-reason,
  .receipt-reason-input {
    min-width: 100%;
  }

  .batch-receipt-actions,
  .receipt-modal-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
