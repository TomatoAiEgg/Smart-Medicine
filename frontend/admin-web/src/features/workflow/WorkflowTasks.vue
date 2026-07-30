<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  approveReviewTask,
  completeDispenseTask,
  completeRecheckTask,
  listDispenseTasks,
  listRecheckTasks,
  listReviewTasks,
  rejectReviewTask,
} from '../../api/workflow';
import { getAdminOrderDetail, getOrderProgress, updateAdminOrderAddress, updateAdminOrderRemark } from '../../api/order';
import type {
  AdminOrderAddressUpdateCommand,
  AdminOrderDetail,
  AdminOrderDetailDrug,
  AdminOrderRemarkUpdateCommand,
  OrderProgressSnapshot,
  WorkflowTaskSnapshot,
} from '../../api/types';
import type { ViewKey } from '../../app/views';
import { downloadCsv } from '../../domain/csv';
import { amountValue, displayValue, joinTruthyParts, labelFromMap, moneyValue, numericValue, pageSummaryText, currentIsoDate, currentIsoTimestamp, formatDate, sumNumbers } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type WorkflowCounts = { reviews: number; dispenses: number; rechecks: number };
type ReviewDetailDrugRow = {
  prescriptionNo: string;
  externalPrescriptionNo: string;
  detail: AdminOrderDetailDrug;
};
type ReviewAddressForm = {
  receiverName: string;
  receiverPhone: string;
  receiverProvince: string;
  receiverCity: string;
  receiverZone: string;
  receiverAddress: string;
  addressType: string;
  deliveryTime: string;
  operator: string;
  reason: string;
};
type ReviewRemarkForm = {
  remark: string;
  operator: string;
  reason: string;
};

const props = defineProps<{
  activeView: Extract<ViewKey, 'reviews' | 'dispenses' | 'rechecks'>;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countsChanged: [counts: WorkflowCounts];
}>();

const reviewTasks = ref<WorkflowTaskSnapshot[]>([]);
const dispenseTasks = ref<WorkflowTaskSnapshot[]>([]);
const recheckTasks = ref<WorkflowTaskSnapshot[]>([]);
const reviewLoading = ref(false);
const dispenseLoading = ref(false);
const recheckLoading = ref(false);
const workflowError = ref('');
const operator = ref('admin');
const comment = ref('处理完成');
const handlingTaskId = ref('');
const startTime = ref('2026-07-14 11:00:00');
const endTime = ref('2026-07-21 12:00:00');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const takingMethod = ref('');
const deliveryTarget = ref('');
const reviewType = ref('待审核');
const printStatus = ref('未打印');
const recheckStatus = ref('未复核');
const classes = ref('');
const orderNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const dosageRange = ref('');
const dispenseUserId = ref('');
const recheckUserId = ref('');
const selectedReviewTask = ref<WorkflowTaskSnapshot | null>(null);
const reviewOrderProgress = ref<OrderProgressSnapshot | null>(null);
const reviewOrderDetail = ref<AdminOrderDetail | null>(null);
const reviewProgressLoading = ref(false);
const reviewDetailLoading = ref(false);
const reviewProgressError = ref('');
const reviewOrderDetailError = ref('');
const reviewDetailNotice = ref('');
const reviewAddressModalOpen = ref(false);
const reviewAddressSubmitting = ref(false);
const reviewRemarkModalOpen = ref(false);
const reviewRemarkSubmitting = ref(false);
const reviewAddressForm = ref<ReviewAddressForm>({
  receiverName: '',
  receiverPhone: '',
  receiverProvince: '',
  receiverCity: '',
  receiverZone: '',
  receiverAddress: '',
  addressType: '',
  deliveryTime: '',
  operator: 'admin',
  reason: '',
});
const reviewRemarkForm = ref<ReviewRemarkForm>({
  remark: '',
  operator: 'admin',
  reason: '',
});

const activeWorkflowTasks = computed(() => {
  if (props.activeView === 'reviews') return reviewTasks.value;
  if (props.activeView === 'dispenses') return dispenseTasks.value;
  return recheckTasks.value;
});

const activeWorkflowLoading = computed(() => {
  if (props.activeView === 'reviews') return reviewLoading.value;
  if (props.activeView === 'dispenses') return dispenseLoading.value;
  return recheckLoading.value;
});

const activeWorkflowEmptyText = computed(() => {
  if (props.activeView === 'reviews') return '暂无待审核任务';
  if (props.activeView === 'dispenses') return '暂无待调剂任务';
  return '暂无待复核任务';
});

const activeTableColspan = computed(() => {
  if (props.activeView === 'reviews') return 14;
  if (props.activeView === 'dispenses') return 15;
  return 17;
});

const activePageSummary = computed(() => {
  const total = activeWorkflowTasks.value.length;
  return pageSummaryText(total);
});

const activeWorkflowLabel = computed(() => {
  if (props.activeView === 'reviews') return '审核';
  if (props.activeView === 'dispenses') return '调剂';
  return '复核';
});

const reviewDetailPrescriptions = computed(() => reviewOrderDetail.value?.prescriptions ?? []);
const reviewDetailDrugRows = computed<ReviewDetailDrugRow[]>(() => (
  reviewDetailPrescriptions.value.flatMap((prescription) => (
    prescription.details.map((detail) => ({
      prescriptionNo: prescription.prescriptionNo,
      externalPrescriptionNo: prescription.externalPrescriptionNo,
      detail,
    }))
  ))
));
const reviewDetailAmountSummary = computed(() => {
  const prescriptionAmount = sumNumbers(reviewDetailPrescriptions.value.map((item) => item.totalAmount));
  const drugAmount = sumNumbers(reviewDetailDrugRows.value.map((row) => row.detail.totalPrice));
  const decoctionAmount = sumNumbers(reviewDetailPrescriptions.value.map((item) => item.decoctionTotalPrice));
  const settlementDetailAmount = sumNumbers(reviewDetailDrugRows.value.map((row) => row.detail.settlementTotalPrice));
  const logisticsFee = numericValue(reviewOrderDetail.value?.logisticsFee);
  const discountAmount = numericValue(reviewOrderDetail.value?.discountAmount);
  const basePayableAmount = prescriptionAmount ?? settlementDetailAmount ?? (
    drugAmount !== null || decoctionAmount !== null ? (drugAmount ?? 0) + (decoctionAmount ?? 0) : null
  );
  return {
    prescriptionAmount,
    drugAmount,
    decoctionAmount,
    settlementDetailAmount,
    logisticsFee,
    discountAmount,
    payableAmount: basePayableAmount === null
      ? null
      : basePayableAmount + (logisticsFee ?? 0) - (discountAmount ?? 0),
  };
});

function escapeHtml(value: string | number | null | undefined) {
  return displayValue(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function selectedValue(value: string) {
  return value || '-';
}

function institutionName(task: WorkflowTaskSnapshot) {
  return institution.value || task.externalOrderNo || '-';
}

function hospitalPrescription(task: WorkflowTaskSnapshot) {
  return hospitalPrescriptionNo.value || task.externalOrderNo || '-';
}

function patientInfo() {
  return patientName.value || '-';
}

function deliveryTargetText() {
  return deliveryTarget.value || '-';
}

function batchText() {
  return classes.value || '-';
}

function prescriptionList(task: WorkflowTaskSnapshot) {
  return `${task.orderNo}-${task.taskId.slice(0, 8)}`;
}

function validationText(task: WorkflowTaskSnapshot) {
  const status = displayValue(task.validationStatus);
  const message = displayValue(task.validationMessage);
  return `${status} / ${message}`;
}

function taskTypeText(type: string) {
  const labels: Record<string, string> = {
    REVIEW: '审核',
    DISPENSE: '调剂',
    RECHECK: '复核',
    ORDER_REVIEW: '订单审核',
    PRESCRIPTION_DISPENSE: '处方调剂',
    PRESCRIPTION_RECHECK: '处方复核',
  };
  return labelFromMap(type, labels, type);
}

function taskStatusText(status: string) {
  const labels: Record<string, string> = {
    PENDING: '待处理',
    IN_PROGRESS: '处理中',
    COMPLETED: '已完成',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
  };
  return labelFromMap(status, labels, status);
}

function exportActiveWorkflowTasks() {
  const label = activeWorkflowLabel.value;
  downloadCsv(
    `${label}待办任务-${currentIsoDate()}.csv`,
    ['任务ID', '任务类型', '任务状态', '平台订单号', '外部订单号', '订单状态', '来源事件', '处理人', '处理意见', '校验状态', '校验提示', '创建时间', '更新时间', '完成时间'],
    activeWorkflowTasks.value.map((task) => [
      task.taskId,
      taskTypeText(task.taskType),
      taskStatusText(task.taskStatus),
      task.orderNo,
      task.externalOrderNo,
      task.orderStatus,
      task.sourceEventId,
      task.reviewer,
      task.reviewComment,
      task.validationStatus,
      task.validationMessage,
      formatDate(task.createdAt),
      formatDate(task.updatedAt),
      formatDate(task.completedAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${activeWorkflowTasks.value.length} 条${label}待办任务`);
}

async function loadReviewOrderProgress(task: WorkflowTaskSnapshot) {
  reviewProgressLoading.value = true;
  reviewProgressError.value = '';
  reviewOrderProgress.value = null;
  try {
    reviewOrderProgress.value = await getOrderProgress(task.orderNo);
  } catch (error) {
    reviewProgressError.value = errorMessage(error);
  } finally {
    reviewProgressLoading.value = false;
  }
}

async function loadReviewOrderDetail(task: WorkflowTaskSnapshot) {
  reviewDetailLoading.value = true;
  reviewOrderDetailError.value = '';
  reviewOrderDetail.value = null;
  try {
    reviewOrderDetail.value = await getAdminOrderDetail(task.orderNo);
  } catch (error) {
    reviewOrderDetailError.value = errorMessage(error);
  } finally {
    reviewDetailLoading.value = false;
  }
}

function selectReviewTask(task: WorkflowTaskSnapshot) {
  selectedReviewTask.value = task;
  reviewDetailNotice.value = '';
  workflowError.value = '';
  void loadReviewOrderProgress(task);
  void loadReviewOrderDetail(task);
}

function backToReviewList() {
  selectedReviewTask.value = null;
  reviewOrderProgress.value = null;
  reviewOrderDetail.value = null;
  reviewProgressError.value = '';
  reviewOrderDetailError.value = '';
  reviewDetailNotice.value = '';
  reviewAddressModalOpen.value = false;
  reviewRemarkModalOpen.value = false;
}

function currentCounts(): WorkflowCounts {
  return {
    reviews: reviewTasks.value.length,
    dispenses: dispenseTasks.value.length,
    rechecks: recheckTasks.value.length,
  };
}

function emitCountsChanged() {
  emit('countsChanged', currentCounts());
}

async function refreshReviewTasks() {
  reviewLoading.value = true;
  workflowError.value = '';
  try {
    reviewTasks.value = await listReviewTasks();
    emit('notice', 'info', `已刷新审核任务：${reviewTasks.value.length} 条`);
  } catch (error) {
    reviewTasks.value = [];
    workflowError.value = errorMessage(error);
  } finally {
    reviewLoading.value = false;
    emitCountsChanged();
  }
}

async function refreshDispenseTasks() {
  dispenseLoading.value = true;
  workflowError.value = '';
  try {
    dispenseTasks.value = await listDispenseTasks();
    emit('notice', 'info', `已刷新调剂任务：${dispenseTasks.value.length} 条`);
  } catch (error) {
    dispenseTasks.value = [];
    workflowError.value = errorMessage(error);
  } finally {
    dispenseLoading.value = false;
    emitCountsChanged();
  }
}

async function refreshRecheckTasks() {
  recheckLoading.value = true;
  workflowError.value = '';
  try {
    recheckTasks.value = await listRecheckTasks();
    emit('notice', 'info', `已刷新复核任务：${recheckTasks.value.length} 条`);
  } catch (error) {
    recheckTasks.value = [];
    workflowError.value = errorMessage(error);
  } finally {
    recheckLoading.value = false;
    emitCountsChanged();
  }
}

async function refreshCurrentTasks() {
  if (props.activeView === 'reviews') {
    await refreshReviewTasks();
    return;
  }
  if (props.activeView === 'dispenses') {
    await refreshDispenseTasks();
    return;
  }
  await refreshRecheckTasks();
}

async function refreshAllWorkflowTasks() {
  await Promise.all([refreshReviewTasks(), refreshDispenseTasks(), refreshRecheckTasks()]);
  emitCountsChanged();
}

function shouldRefreshActiveTasks() {
  if (props.activeView === 'reviews') return reviewTasks.value.length === 0;
  if (props.activeView === 'dispenses') return dispenseTasks.value.length === 0;
  return recheckTasks.value.length === 0;
}

async function submitReview(
  task: WorkflowTaskSnapshot,
  action: 'approve' | 'reject',
  reviewComment: string,
  batchNo?: string,
) {
  if (!operator.value.trim()) {
    workflowError.value = '处理人不能为空';
    return;
  }

  handlingTaskId.value = task.taskId;
  workflowError.value = '';
  reviewDetailNotice.value = '';
  try {
    const command = {
      reviewer: operator.value.trim(),
      reviewComment,
      batchNo,
    };
    const result = action === 'approve'
      ? await approveReviewTask(task.taskId, command)
      : await rejectReviewTask(task.taskId, command);
    emit('notice', 'success', `${result.orderNo} 已${action === 'approve' ? '审核通过' : '审核拒绝'}`);
    backToReviewList();
    await refreshAllWorkflowTasks();
  } catch (error) {
    workflowError.value = errorMessage(error);
  } finally {
    handlingTaskId.value = '';
  }
}

async function handleReviewFailure(task: WorkflowTaskSnapshot) {
  await submitReview(task, 'reject', comment.value.trim() || '审核失败');
}

async function handleBatchApproval(task: WorkflowTaskSnapshot, batch: '早批次' | '晚批次') {
  const batchNo = batch === '早批次' ? 1 : 3;
  await submitReview(task, 'approve', `审核通过；批次：${batch}(${batchNo})`, String(batchNo));
}

function renderSplitPreviewDrugRows(details: readonly AdminOrderDetailDrug[]) {
  if (details.length === 0) return '<tr><td colspan="8">暂无药品明细</td></tr>';
  return details.map((detail, index) => `
    <tr>
      <td>${index + 1}</td>
      <td>${escapeHtml(detail.drugCode)}</td>
      <td>${escapeHtml(detail.drugName)}</td>
      <td>${escapeHtml(detail.platformDrugName)}</td>
      <td>${escapeHtml(detail.drugSpecs)}</td>
      <td>${escapeHtml(joinTruthyParts([detail.dose, detail.unit], ' / ', ''))}</td>
      <td>${escapeHtml(amountValue(detail.quantity))}</td>
      <td>${escapeHtml(moneyValue(detail.totalPrice))}</td>
    </tr>
  `).join('');
}

function renderSplitPreviewSections(detail: AdminOrderDetail) {
  if (detail.prescriptions.length === 0) return '<section class="split-section"><p>暂无可预览处方</p></section>';
  return detail.prescriptions.map((prescription, index) => `
    <section class="split-section">
      <h2>拆单建议 ${index + 1}：${escapeHtml(prescription.prescriptionNo)}</h2>
      <div class="grid">
        <div><span>机构处方号</span><strong>${escapeHtml(prescription.externalPrescriptionNo)}</strong></div>
        <div><span>处方类型</span><strong>${escapeHtml(prescription.prescriptionType)}</strong></div>
        <div><span>处方状态</span><strong>${escapeHtml(prescription.prescriptionStatus)}</strong></div>
        <div><span>剂数</span><strong>${escapeHtml(prescription.doseCount)}</strong></div>
        <div><span>煎煮次数</span><strong>${escapeHtml(prescription.decoctionCount)}</strong></div>
        <div><span>处方金额</span><strong>${escapeHtml(moneyValue(prescription.totalAmount))}</strong></div>
        <div><span>医生</span><strong>${escapeHtml(prescription.doctorName)}</strong></div>
        <div><span>科室/病区</span><strong>${escapeHtml(prescription.departmentName)} / ${escapeHtml(prescription.wardName)}</strong></div>
      </div>
      <table>
        <thead>
          <tr>
            <th>序号</th><th>机构药品编码</th><th>机构药品名称</th><th>平台药品名称</th><th>规格</th><th>剂量</th><th>数量</th><th>金额</th>
          </tr>
        </thead>
        <tbody>${renderSplitPreviewDrugRows(prescription.details)}</tbody>
      </table>
    </section>
  `).join('');
}

function renderSplitPreviewHtml(detail: AdminOrderDetail) {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>拆单预览-${escapeHtml(detail.orderNo)}</title>
  <style>
    @page { size: A4; margin: 12mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 12px; }
    .toolbar { position: fixed; right: 14px; top: 14px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    h1 { margin: 0 0 10px; font-size: 22px; letter-spacing: 0; }
    h2 { margin: 16px 0 8px; font-size: 16px; letter-spacing: 0; }
    .meta { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 10px; color: #475569; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; margin-bottom: 8px; }
    .grid div { border: 1px solid #cbd5e1; padding: 6px; }
    .grid span { display: block; color: #64748b; margin-bottom: 3px; }
    .grid strong { word-break: break-word; }
    table { width: 100%; border-collapse: collapse; page-break-inside: avoid; }
    th, td { border: 1px solid #cbd5e1; padding: 5px; text-align: left; vertical-align: top; }
    th { background: #f1f5f9; }
    .split-section { page-break-inside: avoid; margin-top: 10px; }
    .foot { margin-top: 12px; color: #64748b; }
    @media print { .toolbar { display: none; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <h1>订单拆单预览</h1>
  <div class="meta">
    <span>平台订单号：${escapeHtml(detail.orderNo)}</span>
    <span>外部订单号：${escapeHtml(detail.externalOrderNo)}</span>
    <span>机构：${escapeHtml(detail.institutionName)}</span>
    <span>患者：${escapeHtml(detail.patientName)}</span>
    <span>打印时间：${escapeHtml(formatDate(currentIsoTimestamp()))}</span>
  </div>
  ${renderSplitPreviewSections(detail)}
  <div class="foot">本预览只基于当前订单处方明细生成，不提交拆单、不改变订单或处方状态。</div>
</body>
</html>`;
}

function printReviewSplitPreview() {
  if (!reviewOrderDetail.value) {
    reviewDetailNotice.value = '订单详情尚未加载完成，暂不能生成拆单预览。';
    emit('notice', 'info', reviewDetailNotice.value);
    return;
  }
  reviewDetailNotice.value = '';
  const printWindow = window.open('', '_blank', 'width=1200,height=820');
  if (!printWindow) {
    reviewDetailNotice.value = '浏览器阻止了拆单预览窗口';
    return;
  }
  printWindow.document.open();
  printWindow.document.write(renderSplitPreviewHtml(reviewOrderDetail.value));
  printWindow.document.close();
  emit('notice', 'success', `${reviewOrderDetail.value.orderNo} 拆单预览窗口已打开`);
}

function fillReviewRemarkForm() {
  reviewRemarkForm.value = {
    remark: reviewOrderDetail.value?.orderRemark ?? '',
    operator: operator.value.trim() || 'admin',
    reason: '',
  };
}

function openReviewRemarkModal() {
  if (!selectedReviewTask.value) return;
  if (!reviewOrderDetail.value) {
    reviewDetailNotice.value = '订单详情尚未加载完成，暂不能修改备注。';
    emit('notice', 'info', reviewDetailNotice.value);
    return;
  }
  fillReviewRemarkForm();
  reviewDetailNotice.value = '';
  reviewRemarkModalOpen.value = true;
}

function closeReviewRemarkModal() {
  if (reviewRemarkSubmitting.value) return;
  reviewRemarkModalOpen.value = false;
}

async function submitReviewRemarkUpdate() {
  const targetTask = selectedReviewTask.value;
  if (!targetTask) return;

  const remark = reviewRemarkForm.value.remark.trim();
  if (!remark) {
    reviewDetailNotice.value = '订单备注不能为空。';
    return;
  }

  const command: AdminOrderRemarkUpdateCommand = {
    remark,
    operator: reviewRemarkForm.value.operator.trim() || operator.value.trim() || 'admin',
    reason: trimmedOptional(reviewRemarkForm.value.reason),
  };

  reviewRemarkSubmitting.value = true;
  reviewDetailNotice.value = '';
  try {
    const targetOrderNo = reviewOrderDetail.value?.orderNo ?? targetTask.orderNo;
    await updateAdminOrderRemark(targetOrderNo, command);
    reviewOrderDetail.value = await getAdminOrderDetail(targetOrderNo);
    reviewRemarkModalOpen.value = false;
    reviewDetailNotice.value = '订单备注已更新。';
    emit('notice', 'success', `${targetOrderNo} 订单备注已更新`);
  } catch (error) {
    reviewDetailNotice.value = errorMessage(error);
  } finally {
    reviewRemarkSubmitting.value = false;
  }
}

function fillReviewAddressForm() {
  if (!reviewOrderDetail.value) return;
  reviewAddressForm.value = {
    receiverName: reviewOrderDetail.value.receiverName ?? '',
    receiverPhone: reviewOrderDetail.value.receiverPhone ?? '',
    receiverProvince: reviewOrderDetail.value.receiverProvince ?? '',
    receiverCity: reviewOrderDetail.value.receiverCity ?? '',
    receiverZone: reviewOrderDetail.value.receiverZone ?? '',
    receiverAddress: reviewOrderDetail.value.receiverAddress ?? '',
    addressType: reviewOrderDetail.value.addressType ?? '',
    deliveryTime: reviewOrderDetail.value.deliveryTime ?? '',
    operator: operator.value.trim() || 'admin',
    reason: '',
  };
}

function openReviewAddressModal() {
  if (!selectedReviewTask.value) return;
  if (!reviewOrderDetail.value) {
    reviewDetailNotice.value = '订单详情尚未加载完成，暂不能修改地址。';
    emit('notice', 'info', reviewDetailNotice.value);
    return;
  }
  fillReviewAddressForm();
  reviewDetailNotice.value = '';
  reviewAddressModalOpen.value = true;
}

function closeReviewAddressModal() {
  if (reviewAddressSubmitting.value) return;
  reviewAddressModalOpen.value = false;
}

function trimmedOptional(value: string) {
  const nextValue = value.trim();
  return nextValue.length > 0 ? nextValue : undefined;
}

async function submitReviewAddressUpdate() {
  const targetTask = selectedReviewTask.value;
  if (!targetTask) return;

  const receiverName = reviewAddressForm.value.receiverName.trim();
  const receiverPhone = reviewAddressForm.value.receiverPhone.trim();
  const receiverAddress = reviewAddressForm.value.receiverAddress.trim();
  if (!receiverName || !receiverPhone || !receiverAddress) {
    reviewDetailNotice.value = '收货人、联系电话和详细地址不能为空。';
    return;
  }

  const command: AdminOrderAddressUpdateCommand = {
    receiverName,
    receiverPhone,
    receiverProvince: trimmedOptional(reviewAddressForm.value.receiverProvince),
    receiverCity: trimmedOptional(reviewAddressForm.value.receiverCity),
    receiverZone: trimmedOptional(reviewAddressForm.value.receiverZone),
    receiverAddress,
    addressType: trimmedOptional(reviewAddressForm.value.addressType),
    deliveryTime: trimmedOptional(reviewAddressForm.value.deliveryTime),
    operator: reviewAddressForm.value.operator.trim() || operator.value.trim() || 'admin',
    reason: trimmedOptional(reviewAddressForm.value.reason),
  };

  reviewAddressSubmitting.value = true;
  reviewDetailNotice.value = '';
  try {
    const targetOrderNo = reviewOrderDetail.value?.orderNo ?? targetTask.orderNo;
    await updateAdminOrderAddress(targetOrderNo, command);
    reviewOrderDetail.value = await getAdminOrderDetail(targetOrderNo);
    reviewAddressModalOpen.value = false;
    reviewDetailNotice.value = '收货地址已更新。';
    emit('notice', 'success', `${targetOrderNo} 收货地址已更新`);
  } catch (error) {
    reviewDetailNotice.value = errorMessage(error);
  } finally {
    reviewAddressSubmitting.value = false;
  }
}

async function handleDispense(task: WorkflowTaskSnapshot) {
  if (!operator.value.trim()) {
    workflowError.value = '处理人不能为空';
    return;
  }

  handlingTaskId.value = task.taskId;
  workflowError.value = '';
  try {
    const result = await completeDispenseTask(task.taskId, {
      reviewer: operator.value.trim(),
      reviewComment: comment.value.trim(),
    });
    emit('notice', 'success', `${result.orderNo} 已完成调剂`);
    await refreshAllWorkflowTasks();
  } catch (error) {
    workflowError.value = errorMessage(error);
  } finally {
    handlingTaskId.value = '';
  }
}

async function handleRecheck(task: WorkflowTaskSnapshot) {
  if (!operator.value.trim()) {
    workflowError.value = '处理人不能为空';
    return;
  }

  handlingTaskId.value = task.taskId;
  workflowError.value = '';
  try {
    const result = await completeRecheckTask(task.taskId, {
      reviewer: operator.value.trim(),
      reviewComment: comment.value.trim(),
    });
    emit('notice', 'success', `${result.orderNo} 已完成复核`);
    await refreshRecheckTasks();
  } catch (error) {
    workflowError.value = errorMessage(error);
  } finally {
    handlingTaskId.value = '';
  }
}

onMounted(() => {
  void refreshAllWorkflowTasks();
});

watch(
  () => props.activeView,
  () => {
    if (props.activeView !== 'reviews') backToReviewList();
    if (shouldRefreshActiveTasks()) void refreshCurrentTasks();
  },
);

defineExpose({
  refreshCurrentTasks,
});
</script>

<template>
  <section class="legacy-page workflow-page">
    <template v-if="activeView === 'reviews' && selectedReviewTask">
      <div class="review-detail-workbench">
        <div class="review-detail-toolbar">
          <button class="legacy-btn" type="button" @click="backToReviewList">返回列表</button>
          <button
            class="legacy-btn review-danger-btn"
            type="button"
            :disabled="handlingTaskId === selectedReviewTask.taskId"
            @click="handleReviewFailure(selectedReviewTask)"
          >
            审核失败
          </button>
          <button
            class="legacy-btn legacy-btn-primary"
            type="button"
            :disabled="handlingTaskId === selectedReviewTask.taskId"
            @click="handleBatchApproval(selectedReviewTask, '早批次')"
          >
            早批次通过
          </button>
          <button
            class="legacy-btn legacy-btn-primary"
            type="button"
            :disabled="handlingTaskId === selectedReviewTask.taskId"
            @click="handleBatchApproval(selectedReviewTask, '晚批次')"
          >
            晚批次通过
          </button>
          <button
            class="legacy-btn"
            type="button"
            :disabled="reviewDetailLoading || reviewRemarkSubmitting"
            @click="openReviewRemarkModal"
          >
            备注
          </button>
          <button
            class="legacy-btn"
            type="button"
            :disabled="reviewDetailLoading || !reviewOrderDetail"
            @click="printReviewSplitPreview"
          >
            拆单预览
          </button>
          <button
            class="legacy-btn"
            type="button"
            :disabled="reviewDetailLoading || reviewAddressSubmitting"
            @click="openReviewAddressModal"
          >
            修改地址
          </button>
        </div>

        <div class="review-detail-command">
          <label>
            处理人：
            <input v-model="operator" class="legacy-input" placeholder="admin" />
          </label>
          <label>
            审核失败意见：
            <input v-model="comment" class="legacy-input input-large" placeholder="填写审核失败原因" />
          </label>
          <span>早/晚批次会写入订单批次字段，并同步保留在审核意见中。</span>
        </div>

        <p v-if="workflowError" class="error-line">{{ workflowError }}</p>
        <p v-if="reviewDetailNotice" class="review-detail-notice">{{ reviewDetailNotice }}</p>

        <div v-if="reviewRemarkModalOpen" class="review-address-modal-mask">
          <section class="review-address-modal" aria-label="修改订单备注">
            <div class="review-address-modal-head">
              <h3>修改订单备注</h3>
              <button class="legacy-link-btn" type="button" :disabled="reviewRemarkSubmitting" @click="closeReviewRemarkModal">关闭</button>
            </div>
            <div class="review-address-form">
              <label class="review-address-form-wide">
                <span>订单备注</span>
                <textarea v-model="reviewRemarkForm.remark" class="legacy-input review-remark-textarea" rows="4" />
              </label>
              <label>
                <span>操作人</span>
                <input v-model="reviewRemarkForm.operator" class="legacy-input" />
              </label>
              <label>
                <span>修改原因</span>
                <input v-model="reviewRemarkForm.reason" class="legacy-input" placeholder="可选" />
              </label>
            </div>
            <div class="review-address-modal-actions">
              <button class="legacy-btn" type="button" :disabled="reviewRemarkSubmitting" @click="closeReviewRemarkModal">取消</button>
              <button class="legacy-btn legacy-btn-primary" type="button" :disabled="reviewRemarkSubmitting" @click="submitReviewRemarkUpdate">
                {{ reviewRemarkSubmitting ? '保存中' : '保存备注' }}
              </button>
            </div>
          </section>
        </div>

        <div v-if="reviewAddressModalOpen" class="review-address-modal-mask">
          <section class="review-address-modal" aria-label="修改收货地址">
            <div class="review-address-modal-head">
              <h3>修改收货地址</h3>
              <button class="legacy-link-btn" type="button" :disabled="reviewAddressSubmitting" @click="closeReviewAddressModal">关闭</button>
            </div>
            <div class="review-address-form">
              <label>
                <span>收货人</span>
                <input v-model="reviewAddressForm.receiverName" class="legacy-input" />
              </label>
              <label>
                <span>联系电话</span>
                <input v-model="reviewAddressForm.receiverPhone" class="legacy-input" />
              </label>
              <label>
                <span>省</span>
                <input v-model="reviewAddressForm.receiverProvince" class="legacy-input" />
              </label>
              <label>
                <span>市</span>
                <input v-model="reviewAddressForm.receiverCity" class="legacy-input" />
              </label>
              <label>
                <span>区县</span>
                <input v-model="reviewAddressForm.receiverZone" class="legacy-input" />
              </label>
              <label>
                <span>送货方式</span>
                <select v-model="reviewAddressForm.addressType" class="legacy-input">
                  <option value="">未设置</option>
                  <option value="HOSPITAL">送医院</option>
                  <option value="PATIENT">送个人</option>
                  <option value="PICKUP">自提</option>
                </select>
              </label>
              <label class="review-address-form-wide">
                <span>详细地址</span>
                <input v-model="reviewAddressForm.receiverAddress" class="legacy-input" />
              </label>
              <label>
                <span>送货时间</span>
                <input v-model="reviewAddressForm.deliveryTime" class="legacy-input" placeholder="YYYY-MM-DD HH:mm:ss" />
              </label>
              <label>
                <span>操作人</span>
                <input v-model="reviewAddressForm.operator" class="legacy-input" />
              </label>
              <label class="review-address-form-wide">
                <span>修改原因</span>
                <input v-model="reviewAddressForm.reason" class="legacy-input" placeholder="可选" />
              </label>
            </div>
            <div class="review-address-modal-actions">
              <button class="legacy-btn" type="button" :disabled="reviewAddressSubmitting" @click="closeReviewAddressModal">取消</button>
              <button class="legacy-btn legacy-btn-primary" type="button" :disabled="reviewAddressSubmitting" @click="submitReviewAddressUpdate">
                {{ reviewAddressSubmitting ? '保存中' : '保存地址' }}
              </button>
            </div>
          </section>
        </div>

        <section class="review-detail-section">
          <h3>提示信息</h3>
          <div class="review-detail-grid">
            <div>
              <span>校验状态</span>
              <strong>{{ displayValue(selectedReviewTask.validationStatus) }}</strong>
            </div>
            <div>
              <span>校验提示</span>
              <strong>{{ displayValue(selectedReviewTask.validationMessage) }}</strong>
            </div>
            <div>
              <span>任务提示</span>
              <strong>{{ validationText(selectedReviewTask) }}</strong>
            </div>
          </div>
        </section>

        <section class="review-detail-section">
          <h3>订单信息</h3>
          <div class="review-detail-grid">
            <div>
              <span>平台订单号</span>
              <strong>{{ displayValue(reviewOrderProgress?.orderNo ?? selectedReviewTask.orderNo) }}</strong>
            </div>
            <div>
              <span>订单 ID</span>
              <strong>{{ displayValue(reviewOrderProgress?.orderId ?? selectedReviewTask.orderId) }}</strong>
            </div>
            <div>
              <span>外部订单号</span>
              <strong>{{ displayValue(reviewOrderProgress?.externalOrderNo ?? selectedReviewTask.externalOrderNo) }}</strong>
            </div>
            <div>
              <span>订单状态</span>
              <strong>{{ displayValue(reviewOrderProgress?.orderStatus ?? selectedReviewTask.orderStatus) }}</strong>
            </div>
            <div>
              <span>创建时间</span>
              <strong>{{ formatDate(reviewOrderProgress?.createdAt ?? selectedReviewTask.createdAt) }}</strong>
            </div>
            <div>
              <span>更新时间</span>
              <strong>{{ formatDate(reviewOrderProgress?.updatedAt ?? selectedReviewTask.updatedAt) }}</strong>
            </div>
          </div>
        </section>

        <section class="review-detail-section">
          <h3>处方信息 / 流程信息</h3>
          <p v-if="reviewProgressLoading" class="legacy-empty">正在加载订单进度</p>
          <p v-else-if="reviewProgressError" class="error-line">订单进度加载失败：{{ reviewProgressError }}</p>
          <template v-else-if="reviewOrderProgress">
            <h4>处方信息</h4>
            <table class="legacy-main-table review-detail-table">
              <thead>
                <tr class="legacy-main-head">
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>处方状态</th>
                  <th>明细数</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="reviewOrderProgress.prescriptions.length === 0" class="legacy-main-info">
                  <td colspan="5" class="legacy-empty">暂无处方进度</td>
                </tr>
                <tr v-for="prescription in reviewOrderProgress.prescriptions" :key="prescription.prescriptionId" class="legacy-main-info">
                  <td>{{ displayValue(prescription.prescriptionNo) }}</td>
                  <td>{{ displayValue(prescription.externalPrescriptionNo) }}</td>
                  <td>{{ displayValue(prescription.prescriptionStatus) }}</td>
                  <td>{{ prescription.detailCount }}</td>
                  <td>{{ formatDate(prescription.createdAt) }}</td>
                </tr>
              </tbody>
            </table>

            <h4>流程信息</h4>
            <table class="legacy-main-table review-detail-table">
              <thead>
                <tr class="legacy-main-head">
                  <th>环节</th>
                  <th>状态</th>
                  <th>处理人</th>
                  <th>意见</th>
                  <th>创建时间</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="reviewOrderProgress.workflowTasks.length === 0" class="legacy-main-info">
                  <td colspan="6" class="legacy-empty">暂无流程进度</td>
                </tr>
                <tr v-for="workflowTask in reviewOrderProgress.workflowTasks" :key="workflowTask.taskId" class="legacy-main-info">
                  <td>{{ taskTypeText(workflowTask.taskType) }}</td>
                  <td>{{ taskStatusText(workflowTask.taskStatus) }}</td>
                  <td>{{ displayValue(workflowTask.operator) }}</td>
                  <td class="legacy-left">{{ displayValue(workflowTask.comment) }}</td>
                  <td>{{ formatDate(workflowTask.createdAt) }}</td>
                  <td>{{ formatDate(workflowTask.completedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </template>
          <p v-else class="legacy-empty">选择审核任务后加载订单进度</p>
        </section>

        <section class="review-detail-section">
          <h3>药品信息</h3>
          <p v-if="reviewDetailLoading" class="legacy-empty">正在加载订单详情</p>
          <p v-else-if="reviewOrderDetailError" class="error-line">订单详情加载失败：{{ reviewOrderDetailError }}</p>
          <div v-else class="review-detail-table-wrap">
            <table class="legacy-main-table review-detail-table review-drug-table">
              <thead>
                <tr class="legacy-main-head">
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>机构药品编码</th>
                  <th>机构药品名称</th>
                  <th>平台药品名称</th>
                  <th>规格</th>
                  <th>产地</th>
                  <th>剂量</th>
                  <th>数量</th>
                  <th>单价</th>
                  <th>金额</th>
                  <th>结算金额</th>
                  <th>批号</th>
                  <th>审方提示</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="reviewDetailDrugRows.length === 0" class="legacy-main-info">
                  <td colspan="14" class="legacy-empty">暂无药品明细</td>
                </tr>
                <tr v-for="row in reviewDetailDrugRows" :key="row.detail.detailId" class="legacy-main-info">
                  <td>{{ displayValue(row.prescriptionNo) }}</td>
                  <td>{{ displayValue(row.externalPrescriptionNo) }}</td>
                  <td>{{ displayValue(row.detail.drugCode) }}</td>
                  <td class="legacy-left">{{ displayValue(row.detail.drugName) }}</td>
                  <td class="legacy-left">{{ displayValue(row.detail.platformDrugName) }}</td>
                  <td>{{ displayValue(row.detail.drugSpecs) }}</td>
                  <td>{{ displayValue(row.detail.drugOrigin) }}</td>
                  <td>{{ joinTruthyParts([row.detail.dose, row.detail.unit], ' / ') }}</td>
                  <td>{{ amountValue(row.detail.quantity) }}</td>
                  <td>{{ moneyValue(row.detail.unitPrice) }}</td>
                  <td>{{ moneyValue(row.detail.totalPrice) }}</td>
                  <td>{{ moneyValue(row.detail.settlementTotalPrice) }}</td>
                  <td>{{ displayValue(row.detail.batchNo) }}</td>
                  <td class="legacy-left">{{ displayValue(row.detail.validationTips) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="review-detail-section">
          <h3>费用汇总</h3>
          <p v-if="reviewDetailLoading" class="legacy-empty">正在加载订单详情</p>
          <p v-else-if="reviewOrderDetailError" class="error-line">订单详情加载失败：{{ reviewOrderDetailError }}</p>
          <div v-else class="review-detail-grid">
            <div>
              <span>处方金额</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.prescriptionAmount) }}</strong>
            </div>
            <div>
              <span>药品金额</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.drugAmount) }}</strong>
            </div>
            <div>
              <span>煎煮费</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.decoctionAmount) }}</strong>
            </div>
            <div>
              <span>结算药品金额</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.settlementDetailAmount) }}</strong>
            </div>
            <div>
              <span>物流费</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.logisticsFee) }}</strong>
            </div>
            <div>
              <span>优惠金额</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.discountAmount) }}</strong>
            </div>
            <div>
              <span>应收金额</span>
              <strong>{{ moneyValue(reviewDetailAmountSummary.payableAmount) }}</strong>
            </div>
          </div>
        </section>
      </div>
    </template>

    <template v-else>
    <ul class="legacy-search workflow-search">
      <template v-if="activeView !== 'reviews'">
        <li>
          开始时间：
          <input v-model="startTime" class="legacy-input input-large" />
        </li>
        <li>
          结束时间：
          <input v-model="endTime" class="legacy-input input-large" />
        </li>
      </template>
      <li>
        机构：
        <select v-model="institution" class="legacy-input input-large">
          <option value="">请选择</option>
          <option value="良益堂煎药中心">良益堂煎药中心</option>
          <option value="广州良益堂（康正堂店）">广州良益堂（康正堂店）</option>
          <option value="代煎代配药房">代煎代配药房</option>
        </select>
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input">
          <option value="">请选择</option>
          <option value="代煎">代煎</option>
          <option value="自煎">自煎</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input">
          <option value="">请选择</option>
          <option value="门诊">门诊</option>
          <option value="住院">住院</option>
          <option value="其他">其他</option>
        </select>
      </li>
      <li>
        服用方法：
        <select v-model="takingMethod" class="legacy-input">
          <option value="">请选择</option>
          <option value="内服">内服</option>
          <option value="外用">外用</option>
        </select>
      </li>
      <li v-if="activeView === 'reviews'">
        审核类型：
        <select v-model="reviewType" class="legacy-input">
          <option value="待审核">待审核</option>
          <option value="未到期">未到期</option>
          <option value="已审核">已审核</option>
        </select>
      </li>
      <li v-if="activeView === 'dispenses'">
        打印状态：
        <select v-model="printStatus" class="legacy-input">
          <option value="未打印">未打印</option>
          <option value="已打印">已打印</option>
        </select>
      </li>
      <li v-if="activeView === 'rechecks'">
        复核状态：
        <select v-model="recheckStatus" class="legacy-input">
          <option value="未复核">未复核</option>
          <option value="已复核">已复核</option>
        </select>
      </li>
      <li>
        {{ activeView === 'rechecks' ? '送医院：' : '是否送医院：' }}
        <select v-model="deliveryTarget" class="legacy-input">
          <option value="">请选择</option>
          <option value="默认">默认</option>
          <option value="送医院">送医院</option>
          <option value="送个人">送个人</option>
        </select>
      </li>
      <li v-if="activeView !== 'reviews'">
        {{ activeView === 'rechecks' ? '审核批次：' : '批次：' }}
        <select v-model="classes" class="legacy-input">
          <option value="">请选择</option>
          <option value="早批次">早批次</option>
          <option value="晚批次">晚批次</option>
        </select>
      </li>
      <li>
        平台订单号：
        <input v-model="orderNo" class="legacy-input input-large" />
      </li>
      <li v-if="activeView !== 'rechecks'">
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" />
      </li>
      <li v-if="activeView !== 'rechecks'">
        病人姓名：
        <input v-model="patientName" class="legacy-input input-large" />
      </li>
      <li v-if="activeView === 'reviews'">
        剂数范围：
        <select v-model="dosageRange" class="legacy-input">
          <option value="">请选择</option>
          <option value="小于3剂">小于3剂</option>
          <option value="大于等于3剂">大于等于3剂</option>
        </select>
      </li>
      <li v-if="activeView === 'rechecks'">
        调剂工号：
        <input v-model="dispenseUserId" class="legacy-input input-large" />
      </li>
      <li v-if="activeView === 'rechecks'">
        复核工号：
        <input v-model="recheckUserId" class="legacy-input input-large" />
      </li>
      <li>
        处理人：
        <input v-model="operator" class="legacy-input" placeholder="admin" />
      </li>
      <li>
        处理意见：
        <input v-model="comment" class="legacy-input input-large" placeholder="填写本次处理意见" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="activeWorkflowLoading" @click="refreshCurrentTasks">
          {{ activeWorkflowLoading ? '刷新中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-export" type="button" :disabled="activeWorkflowLoading || activeWorkflowTasks.length === 0" @click="exportActiveWorkflowTasks">
          导出
        </button>
      </li>
    </ul>

    <p v-if="workflowError" class="error-line">{{ workflowError }}</p>

    <div class="legacy-panel">
      <table
        class="legacy-main-table workflow-main-table"
        :class="{
          'workflow-review-table': activeView === 'reviews',
          'workflow-dispense-table': activeView === 'dispenses',
          'workflow-recheck-table': activeView === 'rechecks',
        }"
      >
        <thead>
          <tr v-if="activeView === 'reviews'" class="legacy-main-head">
            <th>平台订单号</th>
            <th>收货地址</th>
            <th>送货时间</th>
            <th>接单时间</th>
            <th>送医院</th>
            <th>机构名称</th>
            <th>机构处方号</th>
            <th>门诊住院</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>处方列表</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
          <tr v-else-if="activeView === 'dispenses'" class="legacy-main-head">
            <th>平台处方号</th>
            <th>病人信息</th>
            <th>收货地址</th>
            <th>送货时间</th>
            <th>接单时间</th>
            <th>送医院</th>
            <th>机构名称</th>
            <th>机构处方号</th>
            <th>门诊住院</th>
            <th>处方类型</th>
            <th>服用方法</th>
            <th>剂数</th>
            <th>批次</th>
            <th>调剂工号</th>
            <th>操作</th>
          </tr>
          <tr v-else class="legacy-main-head">
            <th>平台处方号</th>
            <th>机构名称</th>
            <th>审核批次</th>
            <th>送医院</th>
            <th>送货时间</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>服用方法</th>
            <th>门诊住院</th>
            <th>接单时间</th>
            <th>调剂时间</th>
            <th>调剂员工号</th>
            <th>复核时间</th>
            <th>复核员工号</th>
            <th>加水桶</th>
            <th>订单备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="activeWorkflowLoading" class="legacy-main-info">
            <td :colspan="activeTableColspan" class="legacy-empty">正在刷新任务</td>
          </tr>
          <tr v-else-if="activeWorkflowTasks.length === 0" class="legacy-main-info">
            <td :colspan="activeTableColspan" class="legacy-empty">{{ activeWorkflowEmptyText }}</td>
          </tr>
          <tr v-for="task in activeWorkflowTasks" :key="task.taskId" class="legacy-main-info">
            <template v-if="activeView === 'reviews'">
              <td>
                <strong>{{ task.orderNo }}</strong>
                <small>{{ task.orderId }}</small>
              </td>
              <td class="legacy-left">-</td>
              <td>-</td>
              <td>{{ formatDate(task.createdAt) }}</td>
              <td>{{ deliveryTargetText() }}</td>
              <td>{{ institutionName(task) }}</td>
              <td>{{ hospitalPrescription(task) }}</td>
              <td>{{ selectedValue(hospitalType) }}</td>
              <td>{{ patientInfo() }}</td>
              <td>{{ selectedValue(prescriptionType) }}</td>
              <td>{{ selectedValue(dosageRange) }}</td>
              <td>{{ prescriptionList(task) }}</td>
              <td class="legacy-left">{{ displayValue(task.reviewComment) }}</td>
              <td class="workflow-action-cell">
                <button
                  class="legacy-link-btn workflow-pass-btn"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="selectReviewTask(task)"
                >
                  查看详情
                </button>
              </td>
            </template>

            <template v-else-if="activeView === 'dispenses'">
              <td>{{ task.orderNo }}</td>
              <td>{{ patientInfo() }}</td>
              <td class="legacy-left">-</td>
              <td>-</td>
              <td>{{ formatDate(task.createdAt) }}</td>
              <td>{{ deliveryTargetText() }}</td>
              <td>{{ institutionName(task) }}</td>
              <td>{{ hospitalPrescription(task) }}</td>
              <td>{{ selectedValue(hospitalType) }}</td>
              <td>{{ selectedValue(prescriptionType) }}</td>
              <td>{{ selectedValue(takingMethod) }}</td>
              <td>-</td>
              <td>{{ batchText() }}</td>
              <td>
                <input v-model="operator" class="legacy-input workflow-inline-input" />
              </td>
              <td>
                <button
                  class="legacy-link-btn workflow-pass-btn"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="handleDispense(task)"
                >
                  {{ task.taskStatus === 'COMPLETED' ? '重新打印' : '打印' }}
                </button>
              </td>
            </template>

            <template v-else>
              <td>{{ task.orderNo }}</td>
              <td>{{ institutionName(task) }}</td>
              <td>{{ batchText() }}</td>
              <td>{{ deliveryTargetText() }}</td>
              <td>-</td>
              <td>{{ patientInfo() }}</td>
              <td>{{ selectedValue(prescriptionType) }}</td>
              <td>{{ selectedValue(takingMethod) }}</td>
              <td>{{ selectedValue(hospitalType) }}</td>
              <td>{{ formatDate(task.createdAt) }}</td>
              <td>-</td>
              <td>{{ displayValue(dispenseUserId) }}</td>
              <td>{{ formatDate(task.completedAt) }}</td>
              <td>
                <input v-model="operator" class="legacy-input workflow-inline-input" />
              </td>
              <td>
                <input class="legacy-input workflow-inline-input" />
              </td>
              <td class="legacy-left">{{ displayValue(task.reviewComment) }}</td>
              <td>
                <button
                  class="legacy-link-btn workflow-pass-btn"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="handleRecheck(task)"
                >
                  复核
                </button>
              </td>
            </template>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">{{ activePageSummary }}</p>

    <div class="workflow-task-detail">
      <div>
        <span>任务状态</span>
        <strong>{{ activeView === 'reviews' ? reviewType : activeView === 'dispenses' ? printStatus : recheckStatus }}</strong>
      </div>
      <div>
        <span>当前待办</span>
        <strong>{{ activeWorkflowTasks.length }}</strong>
      </div>
      <div>
        <span>处理人</span>
        <strong>{{ operator }}</strong>
      </div>
      <div>
        <span>处理意见</span>
        <strong>{{ comment }}</strong>
      </div>
    </div>
    </template>
  </section>
</template>

<style scoped>
.review-detail-workbench {
  display: grid;
  gap: 12px;
}

.review-detail-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.review-detail-command {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #d8e0ea;
  background: #f8fafc;
}

.review-detail-command label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  white-space: nowrap;
}

.review-detail-command span,
.review-detail-notice {
  color: #8a5a00;
}

.review-danger-btn {
  border-color: #d73a49;
  color: #b4232f;
}

.review-detail-section {
  padding: 12px;
  border: 1px solid #d8e0ea;
  background: #fff;
}

.review-detail-section h3,
.review-detail-section h4 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
}

.review-detail-section h4 {
  margin-top: 12px;
  font-size: 13px;
}

.review-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.review-detail-grid div {
  min-width: 0;
  padding: 10px;
  border: 1px solid #edf1f5;
  background: #fbfcfe;
}

.review-detail-grid span {
  display: block;
  margin-bottom: 4px;
  color: #667085;
  font-size: 12px;
}

.review-detail-grid strong {
  display: block;
  overflow-wrap: anywhere;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
}

.review-detail-table {
  min-width: 720px;
  margin-bottom: 8px;
}

.review-detail-table-wrap {
  overflow: auto;
}

.review-drug-table {
  min-width: 1180px;
}

.review-address-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgb(15 23 42 / 38%);
}

.review-address-modal {
  width: min(760px, 100%);
  max-height: calc(100vh - 32px);
  overflow: auto;
  padding: 16px;
  border: 1px solid #d8e0ea;
  background: #fff;
  box-shadow: 0 20px 40px rgb(15 23 42 / 20%);
}

.review-address-modal-head,
.review-address-modal-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.review-address-modal-head h3 {
  margin: 0;
  font-size: 16px;
}

.review-address-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}

.review-address-form label {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.review-address-form span {
  color: #475467;
  font-size: 12px;
}

.review-address-form-wide {
  grid-column: 1 / -1;
}

.review-remark-textarea {
  min-height: 96px;
  resize: vertical;
}

@media (max-width: 720px) {
  .review-detail-toolbar,
  .review-detail-command {
    align-items: stretch;
  }

  .review-detail-command label {
    width: 100%;
  }

  .review-address-form {
    grid-template-columns: 1fr;
  }
}
</style>
