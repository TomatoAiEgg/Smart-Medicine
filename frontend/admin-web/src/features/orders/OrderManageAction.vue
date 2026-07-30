<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  cancelAdminPrescription,
  initializeAdminPrescription,
  listAdminOrders,
} from '../../api/order';
import type {
  AdminOrderListItem,
  AdminOrderPage,
  AdminOrderQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, displayValue, currentIsoDate, formatDate, joinDisplayParts, labelFromMap } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type ManageAction = 'initialize' | 'cancel';

interface ActionDialog {
  action: ManageAction;
  row: AdminOrderListItem;
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

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionNo = ref('');
const hospitalPrescriptionNo = ref('');
const page = ref(1);
const pageSize = ref(20);
const orderPage = ref<AdminOrderPage | null>(null);
const loading = ref(false);
const submitting = ref(false);
const errorLine = ref('');
const actionDialog = ref<ActionDialog | null>(null);

const rows = computed(() => orderPage.value?.records ?? []);
const total = computed(() => orderPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function queryParams(): AdminOrderQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    keyword: prescriptionNo.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    excludeOrderStatus: 'SIGNED',
    page: page.value,
    pageSize: pageSize.value,
  };
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function prescriptionTypeText(value: string | null | undefined) {
  if (!value) return '-';
  const labels: Record<string, string> = {
    DECOCTION: '代煎',
    SELF_DECOCTION: '自煎',
    HERBAL_PIECE: '饮片',
    CREAM: '膏方',
    PILL: '丸剂',
    POWDER: '散剂',
    OTHER: '其他',
    1: '饮片',
    2: '代煎',
    3: '膏方',
    4: '丸剂',
    5: '散剂',
  };
  return value.split(',').map((item) => labelFromMap(item, labels, item)).join('、');
}

function medicationMethodText(value: number | null | undefined) {
  if (value === 1) return '内服';
  if (value === 0) return '外用';
  return '-';
}

function patientInfo(row: AdminOrderListItem) {
  return joinDisplayParts([row.patientName, row.patientPhone], ' / ');
}

function fullAddress(row: AdminOrderListItem) {
  return joinDisplayParts([
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ]);
}

function canInitialize(row: AdminOrderListItem) {
  return row.orderStatus !== 'CREATED' && row.orderStatus !== 'SIGNED';
}

function canCancel(row: AdminOrderListItem) {
  return !['SIGNED', 'CANCELLED', 'AUDIT_FAILED'].includes(row.orderStatus)
    && row.prescriptionStatus !== 'CANCELLED';
}

function downloadManageActionCsv() {
  downloadCsv(
    `订单管理动作列表-${currentIsoDate()}.csv`,
    ['平台处方号', '平台订单号', '机构', '机构处方号', '病人信息', '收货地址', '处方类型', '剂数', '服用方式', '金额', '物流公司', '物流单号', '物流状态', '订单状态', '处方状态', '订单时间', '更新时间'],
    rows.value.map((row) => [
      row.prescriptionNos,
      row.orderNo,
      row.institutionName,
      row.externalPrescriptionNos,
      patientInfo(row),
      fullAddress(row),
      prescriptionTypeText(row.prescriptionTypes),
      row.doseCount,
      medicationMethodText(row.isWithin),
      row.totalAmount,
      row.logisticsCompany,
      row.logisticsNo,
      row.logisticsStatus,
      row.orderStatus,
      row.prescriptionStatus,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${rows.value.length} 条订单管理动作记录`);
}

async function refreshOrderManageActions() {
  loading.value = true;
  errorLine.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminOrders(queryParams());
    orderPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('notice', 'success', `已查询到 ${nextPage.total} 条可操作处方`);
  } catch (error) {
    orderPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderManageActions();
}

function openAction(row: AdminOrderListItem, action: ManageAction) {
  actionDialog.value = {
    row,
    action,
    operator: actionDialog.value?.operator || 'admin',
    reason: action === 'initialize' ? '订单操作初始化处方' : '订单操作取消处方',
  };
}

function closeAction() {
  actionDialog.value = null;
}

async function submitAction() {
  if (!actionDialog.value) return;
  const dialog = actionDialog.value;
  submitting.value = true;
  errorLine.value = '';
  try {
    const command = {
      operator: dialog.operator.trim() || 'admin',
      reason: dialog.reason.trim(),
    };
    if (dialog.action === 'initialize') {
      await initializeAdminPrescription(dialog.row.orderNo, dialog.row.prescriptionId, command);
      emit('notice', 'success', `处方 ${dialog.row.prescriptionNos} 已初始化`);
    } else {
      await cancelAdminPrescription(dialog.row.orderNo, dialog.row.prescriptionId, command);
      emit('notice', 'success', `处方 ${dialog.row.prescriptionNos} 已取消`);
    }
    closeAction();
    await refreshOrderManageActions();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderManageActions();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderManageActions();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshOrderManageActions();
  },
  { immediate: true },
);

defineExpose({
  refreshOrderManageActions,
});
</script>

<template>
  <section class="legacy-page order-list-page order-manage-action-page">
    <ul class="legacy-search order-list-search">
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
        平台处方号：
        <input v-model="prescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadManageActionCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-main-table order-manage-action-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台处方号</th>
            <th>平台订单号</th>
            <th>机构名称</th>
            <th>机构处方号</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>服用方式</th>
            <th>订单时间</th>
            <th>订单状态</th>
            <th>处方状态</th>
            <th>是否打印</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">正在查询订单操作列表</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" :key="row.prescriptionId" class="legacy-main-info">
            <td>{{ displayValue(row.prescriptionNos) }}</td>
            <td>{{ row.orderNo }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td>{{ displayValue(row.externalPrescriptionNos) }}</td>
            <td>{{ patientInfo(row) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionTypes) }}</td>
            <td>{{ displayValue(row.doseCount) }}</td>
            <td>{{ medicationMethodText(row.isWithin) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td><StatusPill :value="row.orderStatus" :tone="statusTone(row.orderStatus)" /></td>
            <td><StatusPill :value="row.prescriptionStatus" :tone="statusTone(row.prescriptionStatus)" /></td>
            <td>-</td>
            <td class="action-cell">
              <button
                v-if="canInitialize(row)"
                class="legacy-link-btn workflow-pass-btn"
                type="button"
                @click="openAction(row, 'initialize')"
              >
                初始化
              </button>
              <button
                v-if="canCancel(row)"
                class="legacy-link-btn workflow-reject-btn"
                type="button"
                @click="openAction(row, 'cancel')"
              >
                取消处方
              </button>
              <span v-if="!canInitialize(row) && !canCancel(row)" class="disabled-action">-</span>
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

    <div v-if="actionDialog" class="order-action-modal-mask">
      <section class="order-action-modal legacy-panel">
        <div class="legacy-section-title">
          <h2>{{ actionDialog.action === 'initialize' ? '初始化处方' : '取消处方' }}</h2>
        </div>
        <div class="legacy-detail-grid">
          <span>平台处方号</span>
          <strong>{{ actionDialog.row.prescriptionNos }}</strong>
          <span>平台订单号</span>
          <strong>{{ actionDialog.row.orderNo }}</strong>
          <span>机构处方号</span>
          <strong>{{ displayValue(actionDialog.row.externalPrescriptionNos) }}</strong>
          <span>当前状态</span>
          <strong>
            <StatusPill :value="actionDialog.row.orderStatus" :tone="statusTone(actionDialog.row.orderStatus)" />
          </strong>
        </div>
        <ul class="legacy-search action-form">
          <li>
            操作人：
            <input v-model="actionDialog.operator" class="legacy-input" />
          </li>
          <li class="action-reason-input">
            操作原因：
            <input v-model="actionDialog.reason" class="legacy-input input-large" @keyup.enter="submitAction" />
          </li>
        </ul>
        <div class="order-action-modal-actions">
          <button class="legacy-btn" type="button" :disabled="submitting" @click="closeAction">关闭</button>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitAction">
            {{ submitting ? '提交中' : '提交' }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.action-cell {
  min-width: 118px;
  white-space: nowrap;
}

.action-cell .legacy-link-btn + .legacy-link-btn {
  margin-left: 8px;
}

.disabled-action {
  color: #94a3b8;
}

.order-action-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgb(15 23 42 / 38%);
}

.order-action-modal {
  width: min(680px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
}

.action-form {
  margin-top: 12px;
}

.action-reason-input {
  min-width: 360px;
}

.order-action-modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}

@media (max-width: 780px) {
  .action-reason-input {
    min-width: 100%;
  }

  .order-action-modal-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
