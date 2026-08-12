<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getAdminOrderDetail, listAdminOrderReviews } from '../../api/order';
import { approveReviewTask, rejectReviewTask } from '../../api/workflow';
import type {
  AdminOrderDetail,
  AdminOrderReviewItem,
  AdminOrderReviewPage,
  AdminOrderReviewQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import {
  boundedPositiveInteger,
  currentIsoDate,
  displayValue,
  formatDate,
  joinDisplayParts,
  labelFromMap,
  maskPersonName,
  maskPhone,
  splitCommaValues,
} from '../../domain/formatters';
import { errorMessage } from '../../domain/errors';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type ReviewAction = 'approve' | 'reject';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  'count-changed': [count: number];
}>();

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const deliveryType = ref('');
const isWithin = ref('');
const reviewStatus = ref('PENDING');
const orderNo = ref('');
const prescriptionNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const doseRange = ref('');
const page = ref(1);
const pageSize = ref(20);
const reviewPage = ref<AdminOrderReviewPage | null>(null);
const loading = ref(false);
const errorLine = ref('');
const detailLoading = ref(false);
const selectedDetail = ref<AdminOrderDetail | null>(null);
const selectedRow = ref<AdminOrderReviewItem | null>(null);
const detailError = ref('');
const actionRow = ref<AdminOrderReviewItem | null>(null);
const actionType = ref<ReviewAction>('approve');
const actionOperator = ref('admin');
const actionComment = ref('');
const actionBatchNo = ref('');
const actionSubmitting = ref(false);

const rows = computed(() => reviewPage.value?.records ?? []);
const total = computed(() => reviewPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const actionTitle = computed(() => (actionType.value === 'approve' ? '审核通过' : '审核驳回'));

function prescriptionTypeText(value: string | null | undefined) {
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
  return labelFromMap(value, labels);
}

function hospitalTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    OTHER: '其他',
    1: '门诊',
    2: '住院',
    3: '其他',
  };
  return labelFromMap(value, labels);
}

function deliveryTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    DEFAULT: '默认',
    HOSPITAL: '送医院',
    HOME: '送个人',
    0: '默认',
    1: '送医院',
    2: '送个人',
  };
  return labelFromMap(value, labels);
}

function withinText(value: string | number | null | undefined) {
  if (String(value) === '0') return '内服';
  if (String(value) === '1') return '外用';
  return '-';
}

function reviewStatusText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    PENDING: '待审核',
    NOT_DUE: '未到期',
    REVIEWED: '已审核',
  };
  return labelFromMap(value, labels);
}

function orderStatusText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    CREATED: '待审核',
    AUDIT_PASSED: '审核通过',
    AUDIT_FAILED: '审核失败',
    RECHECKED: '已复核',
    DECOCTING: '煎煮中',
    DECOCTED: '已煎煮',
    PACKED: '已打包',
    SHIPPED: '已发货',
    IN_TRANSIT: '运输中',
    SIGNED: '已签收',
    CANCELLED: '已取消',
  };
  return labelFromMap(value, labels);
}

function commaText(value: string | null | undefined, mapper?: (item: string) => string) {
  const values = splitCommaValues(value).map((item) => (mapper ? mapper(item) : item));
  return values.length > 0 ? values.join(' / ') : '-';
}

function receiverAddress(row: AdminOrderReviewItem) {
  return joinDisplayParts(
    [
      row.receiverProvince,
      row.receiverCity,
      row.receiverZone,
      row.receiverAddress,
      maskPersonName(row.receiverName),
      maskPhone(row.receiverPhone),
    ],
    ' ',
  );
}

function patientInfo(row: AdminOrderReviewItem) {
  return joinDisplayParts([maskPersonName(row.patientName), maskPhone(row.patientPhone)], ' / ');
}

function queryParams(): AdminOrderReviewQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    prescriptionType: prescriptionType.value,
    hospitalType: hospitalType.value,
    isWithin: isWithin.value,
    deliveryType: deliveryType.value,
    reviewStatus: reviewStatus.value,
    orderNo: orderNo.value,
    prescriptionNo: prescriptionNo.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    patientName: patientName.value,
    doseRange: doseRange.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshOrderReviews(showSuccess = true) {
  loading.value = true;
  errorLine.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminOrderReviews(queryParams());
    reviewPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('count-changed', nextPage.total);
    if (showSuccess) emit('notice', 'success', `已查询到 ${nextPage.total} 条订单审核记录`);
  } catch (error) {
    reviewPage.value = null;
    emit('count-changed', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderReviews();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderReviews();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderReviews();
}

async function openDetail(row: AdminOrderReviewItem) {
  selectedRow.value = row;
  selectedDetail.value = null;
  detailError.value = '';
  detailLoading.value = true;
  try {
    selectedDetail.value = await getAdminOrderDetail(row.orderNo);
  } catch (error) {
    detailError.value = errorMessage(error);
  } finally {
    detailLoading.value = false;
  }
}

function closeDetail() {
  if (detailLoading.value) return;
  selectedDetail.value = null;
  selectedRow.value = null;
  detailError.value = '';
}

function canReview(row: AdminOrderReviewItem) {
  return reviewStatus.value === 'PENDING' && row.orderStatus === 'CREATED' && row.reviewTaskId && row.reviewTaskStatus === 'PENDING';
}

function openAction(row: AdminOrderReviewItem, nextAction: ReviewAction) {
  actionRow.value = row;
  actionType.value = nextAction;
  actionComment.value = nextAction === 'approve' ? '审核通过' : '审核驳回';
  actionBatchNo.value = '';
}

function closeAction() {
  if (actionSubmitting.value) return;
  actionRow.value = null;
}

async function submitReviewAction() {
  if (!actionRow.value || !actionRow.value.reviewTaskId) return;
  if (!actionOperator.value.trim()) {
    emit('notice', 'error', '审核人不能为空');
    return;
  }
  actionSubmitting.value = true;
  try {
    const command = {
      reviewer: actionOperator.value.trim(),
      reviewComment: actionComment.value.trim() || actionTitle.value,
      batchNo: actionBatchNo.value.trim() || undefined,
    };
    if (actionType.value === 'approve') {
      await approveReviewTask(actionRow.value.reviewTaskId, command);
    } else {
      await rejectReviewTask(actionRow.value.reviewTaskId, command);
    }
    emit('notice', 'success', `${actionRow.value.orderNo} ${actionTitle.value}完成`);
    actionRow.value = null;
    await refreshOrderReviews(false);
  } catch (error) {
    emit('notice', 'error', errorMessage(error));
  } finally {
    actionSubmitting.value = false;
  }
}

function downloadReviewCsv() {
  downloadCsv(
    `订单审核列表-${currentIsoDate()}.csv`,
    ['平台订单号', '收货地址', '送货时间', '接单时间', '送医院', '机构处方号', '门诊住院', '病人信息', '处方类型', '剂数', '处方列表', '备注', '订单状态'],
    rows.value.map((row) => [
      row.orderNo,
      receiverAddress(row),
      formatDate(row.deliveryTime),
      formatDate(row.orderCreatedAt),
      deliveryTypeText(row.addressType),
      row.externalPrescriptionNos,
      commaText(row.hospitalTypes, hospitalTypeText),
      patientInfo(row),
      commaText(row.prescriptionTypes, prescriptionTypeText),
      row.doseCounts,
      row.prescriptionNos,
      row.orderRemark,
      orderStatusText(row.orderStatus),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${rows.value.length} 条订单审核记录`);
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshOrderReviews(false);
  },
  { immediate: true },
);

defineExpose({
  refreshOrderReviews,
});
</script>

<template>
  <section class="legacy-page order-list-page order-review-records-page">
    <ul class="legacy-search order-list-search">
      <li v-if="reviewStatus === 'REVIEWED'">
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li v-if="reviewStatus === 'REVIEWED'">
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构：
        <input v-model="institution" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="2">代煎</option>
          <option value="1">饮片</option>
          <option value="3">膏方</option>
          <option value="4">丸剂</option>
          <option value="5">散剂</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="1">门诊</option>
          <option value="2">住院</option>
          <option value="3">其他</option>
        </select>
      </li>
      <li>
        送医院：
        <select v-model="deliveryType" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="0">默认</option>
          <option value="1">送医院</option>
          <option value="2">送个人</option>
        </select>
      </li>
      <li>
        服用方法：
        <select v-model="isWithin" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="0">内服</option>
          <option value="1">外用</option>
        </select>
      </li>
      <li>
        审核类型：
        <select v-model="reviewStatus" class="legacy-input input-small" @change="searchFirstPage">
          <option value="PENDING">待审核</option>
          <option value="NOT_DUE">未到期</option>
          <option value="REVIEWED">已审核</option>
        </select>
      </li>
      <li>
        送货类型：
        <select v-model="doseRange" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="LOW">剂数小于 3</option>
          <option value="HIGH">剂数大于等于 3</option>
        </select>
      </li>
      <li>
        平台订单号：
        <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
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
        病人姓名：
        <input v-model="patientName" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        条数：
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="5" max="100" step="5" />
      </li>
      <li class="legacy-actions">
        <button type="button" class="legacy-btn primary" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
        <button type="button" class="legacy-btn" :disabled="rows.length === 0" @click="downloadReviewCsv">
          导出
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="legacy-error">{{ errorLine }}</p>

    <div class="legacy-table-wrap">
      <table class="legacy-table main_table order-review-table">
        <thead>
          <tr>
            <th>平台订单号</th>
            <th>收货地址</th>
            <th>送货时间</th>
            <th>接单时间</th>
            <th>送医院</th>
            <th>机构处方号</th>
            <th>门诊住院</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>处方列表</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">{{ loading ? '正在查询订单审核记录' : '没有相关数据' }}</td>
          </tr>
          <tr v-for="row in rows" :key="row.orderId" class="legacy-main-info">
            <td>
              <button type="button" class="legacy-link-btn" @click="openDetail(row)">{{ displayValue(row.orderNo) }}</button>
            </td>
            <td class="legacy-left">{{ receiverAddress(row) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td>{{ formatDate(row.orderCreatedAt) }}</td>
            <td>{{ deliveryTypeText(row.addressType) }}</td>
            <td>{{ commaText(row.externalPrescriptionNos) }}</td>
            <td>{{ commaText(row.hospitalTypes, hospitalTypeText) }}</td>
            <td>{{ patientInfo(row) }}</td>
            <td>{{ commaText(row.prescriptionTypes, prescriptionTypeText) }}</td>
            <td>{{ displayValue(row.doseCounts) }}</td>
            <td>{{ commaText(row.prescriptionNos) }}</td>
            <td class="legacy-left">{{ displayValue(row.orderRemark) }}</td>
            <td>
              <template v-if="canReview(row)">
                <button type="button" class="legacy-btn tiny" @click="openAction(row, 'approve')">通过</button>
                <button type="button" class="legacy-btn tiny danger" @click="openAction(row, 'reject')">驳回</button>
              </template>
              <StatusPill v-else :value="reviewStatusText(reviewStatus === 'PENDING' ? row.reviewTaskStatus || row.orderStatus : reviewStatus)" :tone="statusTone(row.reviewTaskStatus || row.orderStatus)" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <span>显示 {{ rows.length > 0 ? (page - 1) * pageSize + 1 : 0 }} 至 {{ Math.min(page * pageSize, total) }} 项记录，共 {{ total }} 项</span>
      <button type="button" class="legacy-btn" :disabled="!hasPreviousPage" @click="goPreviousPage">上一页</button>
      <button type="button" class="legacy-btn" :disabled="!hasNextPage" @click="goNextPage">下一页</button>
    </div>

    <div v-if="selectedRow" class="legacy-modal-mask">
      <section class="legacy-modal order-review-detail-modal">
        <div class="legacy-modal-head">
          <h2>订单审核详情</h2>
          <button class="legacy-link-btn" type="button" :disabled="detailLoading" @click="closeDetail">关闭</button>
        </div>
        <p v-if="detailError" class="legacy-error">{{ detailError }}</p>
        <p v-if="detailLoading" class="legacy-empty">正在加载订单详情</p>
        <template v-else>
          <div class="order-review-detail-grid">
            <div><span>平台订单号</span><strong>{{ displayValue(selectedRow.orderNo) }}</strong></div>
            <div><span>订单状态</span><StatusPill :value="orderStatusText(selectedRow.orderStatus)" :tone="statusTone(selectedRow.orderStatus)" /></div>
            <div><span>医疗机构</span><strong>{{ displayValue(selectedRow.institutionName) }}</strong></div>
            <div><span>病人信息</span><strong>{{ patientInfo(selectedRow) }}</strong></div>
            <div><span>收货地址</span><strong>{{ receiverAddress(selectedRow) }}</strong></div>
            <div><span>送货时间</span><strong>{{ formatDate(selectedRow.deliveryTime) }}</strong></div>
          </div>
          <div class="legacy-table-wrap">
            <table class="legacy-table main_table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>处方类型</th>
                  <th>门诊住院</th>
                  <th>剂数</th>
                  <th>药品数</th>
                  <th>金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!selectedDetail || selectedDetail.prescriptions.length === 0">
                  <td colspan="7" class="legacy-empty">暂无处方明细</td>
                </tr>
                <tr v-for="prescription in selectedDetail?.prescriptions ?? []" :key="prescription.prescriptionId">
                  <td>{{ displayValue(prescription.prescriptionNo) }}</td>
                  <td>{{ displayValue(prescription.externalPrescriptionNo) }}</td>
                  <td>{{ prescriptionTypeText(prescription.prescriptionType) }}</td>
                  <td>{{ hospitalTypeText(prescription.hospitalType) }}</td>
                  <td>{{ displayValue(prescription.doseCount) }}</td>
                  <td>{{ prescription.details.length }}</td>
                  <td>{{ displayValue(prescription.totalAmount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>
    </div>

    <div v-if="actionRow" class="legacy-modal-mask">
      <section class="legacy-modal order-review-action-modal">
        <div class="legacy-modal-head">
          <h2>{{ actionTitle }}</h2>
          <button class="legacy-link-btn" type="button" :disabled="actionSubmitting" @click="closeAction">关闭</button>
        </div>
        <div class="order-review-action-summary">
          <strong>{{ actionRow.orderNo }}</strong>
          <span>{{ patientInfo(actionRow) }} / {{ commaText(actionRow.prescriptionTypes, prescriptionTypeText) }}</span>
        </div>
        <div class="address-form-grid">
          <label>
            <span>审核人</span>
            <input v-model="actionOperator" class="legacy-input" />
          </label>
          <label v-if="actionType === 'approve'">
            <span>批次</span>
            <select v-model="actionBatchNo" class="legacy-input">
              <option value="">不指定</option>
              <option value="1">早批次</option>
              <option value="3">晚批次</option>
            </select>
          </label>
          <label class="address-form-wide">
            <span>审核备注</span>
            <input v-model="actionComment" class="legacy-input" />
          </label>
        </div>
        <div class="legacy-modal-actions">
          <button class="legacy-btn" type="button" :disabled="actionSubmitting" @click="closeAction">返回</button>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="actionSubmitting" @click="submitReviewAction">
            {{ actionSubmitting ? '提交中' : `确认${actionTitle}` }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.order-review-table th,
.order-review-table td {
  min-width: 96px;
}

.order-review-table th:nth-child(2),
.order-review-table td:nth-child(2),
.order-review-table th:nth-child(12),
.order-review-table td:nth-child(12) {
  min-width: 180px;
}

.order-review-detail-modal {
  width: min(1080px, calc(100vw - 48px));
  max-height: calc(100vh - 80px);
  overflow: auto;
}

.order-review-action-modal {
  width: min(560px, calc(100vw - 48px));
}

.order-review-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.order-review-detail-grid > div {
  border: 1px solid #d8e0ec;
  background: #f8fbff;
  padding: 10px;
}

.order-review-detail-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 4px;
}

.order-review-detail-grid strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.order-review-action-summary {
  border: 1px solid #d8e0ec;
  background: #f8fbff;
  padding: 10px;
  margin-bottom: 12px;
}

.order-review-action-summary strong,
.order-review-action-summary span {
  display: block;
}

.order-review-action-summary span {
  color: #64748b;
  margin-top: 4px;
}

@media (max-width: 760px) {
  .order-review-detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
