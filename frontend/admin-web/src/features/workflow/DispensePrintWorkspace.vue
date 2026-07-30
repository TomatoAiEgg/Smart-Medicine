<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { errorMessage } from '../../domain/errors';
import { getAdminOrderDetail, getOrderProgress } from '../../api/order';
import { completeDispenseTask, listDispenseTasks } from '../../api/workflow';
import type {
  AdminOrderDetail,
  AdminOrderDetailDrug,
  OrderProgressSnapshot,
  WorkflowTaskSnapshot,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type NumericValue = string | number | null | undefined;
type PrintDrugRow = {
  prescriptionNo: string;
  externalPrescriptionNo: string;
  detail: AdminOrderDetailDrug;
};

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const tasks = ref<WorkflowTaskSnapshot[]>([]);
const selectedTask = ref<WorkflowTaskSnapshot | null>(null);
const orderProgress = ref<OrderProgressSnapshot | null>(null);
const orderDetail = ref<AdminOrderDetail | null>(null);
const loading = ref(false);
const progressLoading = ref(false);
const detailLoading = ref(false);
const completingTaskId = ref('');
const errorText = ref('');
const progressError = ref('');
const detailError = ref('');

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const takingMethod = ref('');
const hospitalType = ref('');
const deliveryTarget = ref('');
const printStatus = ref('未打印');
const batchNo = ref('');
const orderKeyword = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const dispenserNo = ref('');
const dispenseComment = ref('调剂打印完成');

const searchableKeyword = computed(() => orderKeyword.value.trim().toLowerCase());
const visibleTasks = computed(() => {
  const keyword = searchableKeyword.value;
  if (!keyword) return tasks.value;

  return tasks.value.filter((task) => {
    const values = [task.orderNo, task.externalOrderNo, task.orderId, task.taskId];
    return values.some((value) => value.toLowerCase().includes(keyword));
  });
});

const selectedPrescriptions = computed(() => orderProgress.value?.prescriptions ?? []);
const selectedWorkflowTasks = computed(() => orderProgress.value?.workflowTasks ?? []);
const selectedDispenseRecords = computed(() => orderProgress.value?.dispenseRecords ?? []);
const selectedDetailPrescriptions = computed(() => orderDetail.value?.prescriptions ?? []);
const printDrugRows = computed<PrintDrugRow[]>(() => (
  selectedDetailPrescriptions.value.flatMap((prescription) => (
    prescription.details.map((detail) => ({
      prescriptionNo: prescription.prescriptionNo,
      externalPrescriptionNo: prescription.externalPrescriptionNo,
      detail,
    }))
  ))
));
const primaryPrescription = computed(() => selectedPrescriptions.value[0] ?? null);
const latestDispenseRecord = computed(() => {
  const records = selectedDispenseRecords.value;
  return records.length > 0 ? records[records.length - 1] : null;
});
const detailAmountSummary = computed(() => {
  const prescriptionAmount = sumNumbers(selectedDetailPrescriptions.value.map((item) => item.totalAmount));
  const drugAmount = sumNumbers(printDrugRows.value.map((row) => row.detail.totalPrice));
  const decoctionAmount = sumNumbers(selectedDetailPrescriptions.value.map((item) => item.decoctionTotalPrice));
  return {
    prescriptionAmount,
    drugAmount,
    decoctionAmount,
    payableAmount: drugAmount !== null || decoctionAmount !== null ? (drugAmount ?? 0) + (decoctionAmount ?? 0) : null,
  };
});

const pageSummary = computed(() => {
  const total = visibleTasks.value.length;
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${total} 项`;
});

const previewPrescriptionNo = computed(() => {
  const prescriptionNos = selectedPrescriptions.value
    .map((prescription) => prescription.prescriptionNo)
    .filter((value) => value.trim().length > 0);
  return prescriptionNos.length > 0 ? prescriptionNos.join('、') : emptyDetailText();
});

const previewExternalPrescriptionNo = computed(() => {
  const prescriptionNos = selectedPrescriptions.value
    .map((prescription) => prescription.externalPrescriptionNo)
    .filter((value) => value.trim().length > 0);
  return prescriptionNos.length > 0 ? prescriptionNos.join('、') : emptyDetailText();
});

const previewBatchNo = computed(() => orderDetail.value?.batchNo || batchNo.value || emptyDetailText());
const previewPatient = computed(() => {
  const pieces = [orderDetail.value?.patientName, orderDetail.value?.patientPhone].filter(Boolean);
  return pieces.length > 0 ? pieces.join(' / ') : patientName.value || waitingDetail();
});
const previewReceiver = computed(() => {
  const address = [
    orderDetail.value?.receiverProvince,
    orderDetail.value?.receiverCity,
    orderDetail.value?.receiverZone,
    orderDetail.value?.receiverAddress,
  ].filter(Boolean).join('');
  const pieces = [orderDetail.value?.receiverName, orderDetail.value?.receiverPhone, address].filter(Boolean);
  return pieces.length > 0 ? pieces.join(' / ') : waitingDetail();
});
const previewDeliveryTime = computed(() => formatDate(orderDetail.value?.deliveryTime));
const previewPrintStatus = computed(() => {
  if (latestDispenseRecord.value) return printStatusText(latestDispenseRecord.value.printStatus);
  if (selectedTask.value?.taskStatus === 'COMPLETED') return '已完成';
  return printStatus.value;
});

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function numericValue(value: NumericValue) {
  if (value === null || value === undefined || value === '') return null;
  const nextValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(nextValue) ? nextValue : null;
}

function sumNumbers(values: NumericValue[]) {
  let total = 0;
  let hasValue = false;
  for (const value of values) {
    const nextValue = numericValue(value);
    if (nextValue !== null) {
      total += nextValue;
      hasValue = true;
    }
  }
  return hasValue ? total : null;
}

function moneyValue(value: NumericValue) {
  const nextValue = numericValue(value);
  return nextValue === null ? '-' : nextValue.toFixed(2);
}

function amountValue(value: NumericValue) {
  const nextValue = numericValue(value);
  if (nextValue === null) return '-';
  return Number.isInteger(nextValue) ? String(nextValue) : String(Number(nextValue.toFixed(4)));
}

function emptyDetailText() {
  return selectedTask.value ? '暂无订单详情' : '选择后查看订单详情';
}

function waitingDetail() {
  return emptyDetailText();
}

function selectedFilterValue(value: string) {
  return value || waitingDetail();
}

function isSelectedTask(task: WorkflowTaskSnapshot) {
  return selectedTask.value?.taskId === task.taskId;
}

function taskStatusText(status: string) {
  const labels: Record<string, string> = {
    PENDING: '待处理',
    IN_PROGRESS: '处理中',
    COMPLETED: '已完成',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
  };
  return labels[status] || status;
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
  return labels[type] || type;
}

function printStatusText(status: string) {
  const labels: Record<string, string> = {
    PENDING: '未打印',
    NOT_PRINTED: '未打印',
    PRINTED: '已打印',
    REPRINTED: '已重打',
    FAILED: '打印失败',
  };
  return labels[status] || status;
}

function downloadVisibleTasksCsv() {
  downloadCsv(
    `调剂打印任务-${visibleTasks.value.length}条.csv`,
    ['平台订单号', '外部订单号', '订单ID', '任务ID', '任务类型', '任务状态', '订单状态', '校验状态', '校验提示', '处理人', '处理意见', '接单时间', '更新时间', '完成时间'],
    visibleTasks.value.map((task) => [
      task.orderNo,
      task.externalOrderNo,
      task.orderId,
      task.taskId,
      taskTypeText(task.taskType),
      taskStatusText(task.taskStatus),
      task.orderStatus,
      task.validationStatus,
      task.validationMessage,
      task.reviewer,
      task.reviewComment,
      formatDate(task.createdAt),
      formatDate(task.updatedAt),
      formatDate(task.completedAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${formatNumber(visibleTasks.value.length)} 条调剂打印任务`);
}

async function loadOrderProgress(task: WorkflowTaskSnapshot) {
  progressLoading.value = true;
  progressError.value = '';
  orderProgress.value = null;
  try {
    orderProgress.value = await getOrderProgress(task.orderNo);
  } catch (error) {
    progressError.value = errorMessage(error);
  } finally {
    progressLoading.value = false;
  }
}

async function loadOrderDetail(task: WorkflowTaskSnapshot) {
  detailLoading.value = true;
  detailError.value = '';
  orderDetail.value = null;
  try {
    orderDetail.value = await getAdminOrderDetail(task.orderNo);
  } catch (error) {
    detailError.value = errorMessage(error);
  } finally {
    detailLoading.value = false;
  }
}

function selectTask(task: WorkflowTaskSnapshot) {
  selectedTask.value = task;
  errorText.value = '';
  void loadOrderProgress(task);
  void loadOrderDetail(task);
}

async function refreshDispenseTasks() {
  loading.value = true;
  errorText.value = '';
  try {
    tasks.value = await listDispenseTasks();
    emit('countChanged', tasks.value.length);
    emit('notice', 'info', `已刷新调剂任务：${tasks.value.length} 条`);

    const currentTaskId = selectedTask.value?.taskId;
    const nextSelectedTask = currentTaskId
      ? tasks.value.find((task) => task.taskId === currentTaskId) ?? tasks.value[0] ?? null
      : tasks.value[0] ?? null;

    if (nextSelectedTask) {
      selectedTask.value = nextSelectedTask;
      await Promise.all([loadOrderProgress(nextSelectedTask), loadOrderDetail(nextSelectedTask)]);
    } else {
      selectedTask.value = null;
      orderProgress.value = null;
      orderDetail.value = null;
      progressError.value = '';
      detailError.value = '';
    }
  } catch (error) {
    tasks.value = [];
    selectedTask.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    progressError.value = '';
    detailError.value = '';
    errorText.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function completeSelectedTask() {
  if (!selectedTask.value) {
    errorText.value = '请先选择待调剂任务';
    return;
  }

  const reviewer = dispenserNo.value.trim();
  if (!reviewer) {
    errorText.value = '调剂工号不能为空';
    return;
  }

  completingTaskId.value = selectedTask.value.taskId;
  errorText.value = '';
  try {
    const result = await completeDispenseTask(selectedTask.value.taskId, {
      reviewer,
      reviewComment: dispenseComment.value.trim() || '调剂打印完成',
    });
    emit('notice', 'success', `${result.orderNo} 已完成调剂/打印`);
    await refreshDispenseTasks();
  } catch (error) {
    errorText.value = errorMessage(error);
  } finally {
    completingTaskId.value = '';
  }
}

onMounted(() => {
  void refreshDispenseTasks();
});

defineExpose({
  refreshDispenseTasks,
});
</script>

<template>
  <section class="legacy-page dispense-print-page">
    <ul class="legacy-search dispense-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="YYYY-MM-DD HH:mm:ss" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="YYYY-MM-DD HH:mm:ss" />
      </li>
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
        服用方法：
        <select v-model="takingMethod" class="legacy-input">
          <option value="">请选择</option>
          <option value="内服">内服</option>
          <option value="外用">外用</option>
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
        送医院：
        <select v-model="deliveryTarget" class="legacy-input">
          <option value="">请选择</option>
          <option value="默认">默认</option>
          <option value="送医院">送医院</option>
          <option value="送个人">送个人</option>
        </select>
      </li>
      <li>
        打印状态：
        <select v-model="printStatus" class="legacy-input">
          <option value="未打印">未打印</option>
          <option value="已打印">已打印</option>
        </select>
      </li>
      <li>
        批次：
        <select v-model="batchNo" class="legacy-input">
          <option value="">请选择</option>
          <option value="早批次">早批次</option>
          <option value="晚批次">晚批次</option>
        </select>
      </li>
      <li>
        平台订单/处方号：
        <input v-model="orderKeyword" class="legacy-input input-large" />
      </li>
      <li>
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" />
      </li>
      <li>
        病人姓名：
        <input v-model="patientName" class="legacy-input input-large" />
      </li>
      <li>
        调剂工号：
        <input v-model="dispenserNo" class="legacy-input" />
      </li>
      <li>
        备注：
        <input v-model="dispenseComment" class="legacy-input input-large" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshDispenseTasks">
          {{ loading ? '刷新中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || visibleTasks.length === 0" @click="downloadVisibleTasksCsv">
          导出当前结果
        </button>
      </li>
    </ul>

    <p class="dispense-api-note">
      当前列表接口仅返回工作流待办和订单编号；选中任务后加载订单详情，展示病人、地址、药品明细和费用。
    </p>
    <p v-if="errorText" class="error-line">{{ errorText }}</p>

    <div class="dispense-workbench">
      <section class="dispense-list-column">
        <div class="legacy-panel">
          <table class="legacy-main-table workflow-main-table dispense-print-table">
            <thead>
              <tr class="legacy-main-head">
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
            </thead>
            <tbody>
              <tr v-if="loading" class="legacy-main-info">
                <td colspan="15" class="legacy-empty">正在刷新调剂任务</td>
              </tr>
              <tr v-else-if="visibleTasks.length === 0" class="legacy-main-info">
                <td colspan="15" class="legacy-empty">暂无待调剂任务</td>
              </tr>
              <tr
                v-for="task in visibleTasks"
                :key="task.taskId"
                class="legacy-main-info dispense-task-row"
                :class="{ active: isSelectedTask(task) }"
                @click="selectTask(task)"
              >
                <td>
                  <strong>{{ rowValue(task.orderNo) }}</strong>
                  <small>选择后加载处方号</small>
                </td>
                <td>{{ patientName || waitingDetail() }}</td>
                <td class="legacy-left">{{ waitingDetail() }}</td>
                <td>{{ waitingDetail() }}</td>
                <td>{{ formatDate(task.createdAt) }}</td>
                <td>{{ selectedFilterValue(deliveryTarget) }}</td>
                <td>{{ selectedFilterValue(institution) }}</td>
                <td>{{ hospitalPrescriptionNo || waitingDetail() }}</td>
                <td>{{ selectedFilterValue(hospitalType) }}</td>
                <td>{{ selectedFilterValue(prescriptionType) }}</td>
                <td>{{ selectedFilterValue(takingMethod) }}</td>
                <td>{{ waitingDetail() }}</td>
                <td>{{ selectedFilterValue(batchNo) }}</td>
                <td>{{ dispenserNo || '-' }}</td>
                <td>
                  <button class="legacy-link-btn workflow-pass-btn" type="button" @click.stop="selectTask(task)">
                    选择打印
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <p class="legacy-page-summary">{{ pageSummary }}</p>
      </section>

      <aside class="dispense-detail-column">
        <section class="dispense-command-panel">
          <h3>调剂操作</h3>
          <div class="dispense-command-grid">
            <label>
              调剂工号
              <input v-model="dispenserNo" class="legacy-input" placeholder="请输入工号" />
            </label>
            <label>
              备注
              <input v-model="dispenseComment" class="legacy-input" placeholder="本次调剂备注" />
            </label>
          </div>
          <button
            class="legacy-btn legacy-btn-primary dispense-complete-btn"
            type="button"
            :disabled="!selectedTask || completingTaskId === selectedTask.taskId"
            @click="completeSelectedTask"
          >
            {{ completingTaskId ? '提交中' : '完成调剂/打印' }}
          </button>
        </section>

        <section class="dispense-detail-panel">
          <h3>选中任务详情</h3>
          <p v-if="!selectedTask" class="legacy-empty">请选择待调剂任务</p>
          <template v-else>
            <div class="dispense-detail-grid">
              <div>
                <span>订单号</span>
                <strong>{{ rowValue(orderProgress?.orderNo ?? selectedTask.orderNo) }}</strong>
              </div>
              <div>
                <span>外部订单号</span>
                <strong>{{ rowValue(orderProgress?.externalOrderNo ?? selectedTask.externalOrderNo) }}</strong>
              </div>
              <div>
                <span>订单状态</span>
                <strong>{{ rowValue(orderProgress?.orderStatus ?? selectedTask.orderStatus) }}</strong>
              </div>
              <div>
                <span>任务状态</span>
                <strong>{{ taskStatusText(selectedTask.taskStatus) }}</strong>
              </div>
              <div>
                <span>平台处方号</span>
                <strong>{{ previewPrescriptionNo }}</strong>
              </div>
              <div>
                <span>机构处方号</span>
                <strong>{{ previewExternalPrescriptionNo }}</strong>
              </div>
              <div>
                <span>打印状态</span>
                <strong>{{ previewPrintStatus }}</strong>
              </div>
              <div>
                <span>批次</span>
                <strong>{{ previewBatchNo }}</strong>
              </div>
            </div>

            <p v-if="progressLoading" class="legacy-empty">正在加载处方与流程信息</p>
            <p v-else-if="progressError" class="error-line">订单进度加载失败：{{ progressError }}</p>
            <p v-if="detailLoading" class="legacy-empty">正在加载订单详情</p>
            <p v-else-if="detailError" class="error-line">订单详情加载失败：{{ detailError }}</p>
          </template>
        </section>

        <section class="dispense-print-sheet" aria-label="A5 调剂单打印预览">
          <div class="print-title">
            <h3>调剂单</h3>
            <span>A5 打印预览</span>
          </div>

          <div class="print-meta-grid">
            <div>
              <span>平台处方号</span>
              <strong>{{ previewPrescriptionNo }}</strong>
            </div>
            <div>
              <span>机构处方号</span>
              <strong>{{ previewExternalPrescriptionNo }}</strong>
            </div>
            <div>
              <span>订单号</span>
              <strong>{{ rowValue(orderProgress?.orderNo ?? selectedTask?.orderNo) }}</strong>
            </div>
            <div>
              <span>病人信息</span>
              <strong>{{ previewPatient }}</strong>
            </div>
            <div>
              <span>收货信息</span>
              <strong>{{ previewReceiver }}</strong>
            </div>
            <div>
              <span>调剂工号</span>
              <strong>{{ dispenserNo || '-' }}</strong>
            </div>
            <div>
              <span>批次</span>
              <strong>{{ previewBatchNo }}</strong>
            </div>
            <div>
              <span>接单时间</span>
              <strong>{{ formatDate(selectedTask?.createdAt) }}</strong>
            </div>
            <div>
              <span>送货时间</span>
              <strong>{{ previewDeliveryTime }}</strong>
            </div>
          </div>

          <div class="print-section">
            <h4>处方信息</h4>
            <table class="print-table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>状态</th>
                  <th>明细数</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="selectedPrescriptions.length === 0">
                  <td colspan="4">暂无处方信息</td>
                </tr>
                <tr v-for="prescription in selectedPrescriptions" :key="prescription.prescriptionId">
                  <td>{{ rowValue(prescription.prescriptionNo) }}</td>
                  <td>{{ rowValue(prescription.externalPrescriptionNo) }}</td>
                  <td>{{ rowValue(prescription.prescriptionStatus) }}</td>
                  <td>{{ prescription.detailCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="print-section">
            <h4>流程信息</h4>
            <table class="print-table">
              <thead>
                <tr>
                  <th>环节</th>
                  <th>状态</th>
                  <th>处理人</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="selectedWorkflowTasks.length === 0">
                  <td colspan="4">暂无流程信息</td>
                </tr>
                <tr v-for="workflowTask in selectedWorkflowTasks" :key="workflowTask.taskId">
                  <td>{{ taskTypeText(workflowTask.taskType) }}</td>
                  <td>{{ taskStatusText(workflowTask.taskStatus) }}</td>
                  <td>{{ rowValue(workflowTask.operator) }}</td>
                  <td>{{ formatDate(workflowTask.completedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="print-section">
            <h4>药品明细</h4>
            <p v-if="detailLoading" class="legacy-empty">正在加载订单详情</p>
            <p v-else-if="detailError" class="error-line">订单详情加载失败：{{ detailError }}</p>
            <table v-else class="print-table">
              <thead>
                <tr>
                  <th>药品</th>
                  <th>规格/产地</th>
                  <th>剂量</th>
                  <th>数量</th>
                  <th>金额</th>
                  <th>用法/提示</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="printDrugRows.length === 0">
                  <td colspan="6">暂无药品明细</td>
                </tr>
                <tr v-for="row in printDrugRows" :key="row.detail.detailId">
                  <td>
                    <strong>{{ rowValue(row.detail.drugName || row.detail.platformDrugName) }}</strong>
                    <small>{{ rowValue(row.detail.drugCode || row.detail.platformDrugCode) }}</small>
                  </td>
                  <td>{{ rowValue([row.detail.drugSpecs, row.detail.drugOrigin].filter(Boolean).join(' / ')) }}</td>
                  <td>{{ rowValue([row.detail.dose, row.detail.unit].filter(Boolean).join(' / ')) }}</td>
                  <td>{{ amountValue(row.detail.quantity) }}</td>
                  <td>{{ moneyValue(row.detail.totalPrice) }}</td>
                  <td>{{ rowValue([row.detail.specialUsage, row.detail.validationTips].filter(Boolean).join(' / ')) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="print-section">
            <h4>费用区</h4>
            <p v-if="detailLoading" class="legacy-empty">正在加载订单详情</p>
            <p v-else-if="detailError" class="error-line">订单详情加载失败：{{ detailError }}</p>
            <div v-else class="print-amount-grid">
              <div>
                <span>处方金额</span>
                <strong>{{ moneyValue(detailAmountSummary.prescriptionAmount) }}</strong>
              </div>
              <div>
                <span>药品金额</span>
                <strong>{{ moneyValue(detailAmountSummary.drugAmount) }}</strong>
              </div>
              <div>
                <span>煎煮费</span>
                <strong>{{ moneyValue(detailAmountSummary.decoctionAmount) }}</strong>
              </div>
              <div>
                <span>药品+煎煮合计</span>
                <strong>{{ moneyValue(detailAmountSummary.payableAmount) }}</strong>
              </div>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.dispense-search {
  align-items: center;
}

.dispense-api-note {
  margin: 0 0 10px;
  color: #8a5a00;
  font-size: 13px;
}

.dispense-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 520px);
  gap: 14px;
  align-items: start;
}

.dispense-list-column,
.dispense-detail-column {
  min-width: 0;
}

.dispense-detail-column {
  display: grid;
  gap: 12px;
}

.dispense-print-table {
  min-width: 1680px;
}

.dispense-task-row {
  cursor: pointer;
}

.dispense-task-row.active td {
  background: #f0f7ff;
}

.dispense-task-row small {
  display: block;
  margin-top: 3px;
  color: #667085;
  font-size: 11px;
  line-height: 1.35;
}

.dispense-command-panel,
.dispense-detail-panel {
  padding: 12px;
  border: 1px solid #d8e0ea;
  background: #fff;
}

.dispense-command-panel h3,
.dispense-detail-panel h3 {
  margin: 0 0 10px;
  font-size: 14px;
  color: #1f2937;
}

.dispense-command-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  margin-bottom: 10px;
}

.dispense-command-grid label {
  display: grid;
  gap: 4px;
  color: #475467;
  font-size: 12px;
}

.dispense-complete-btn {
  width: 100%;
}

.dispense-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.dispense-detail-grid div {
  min-width: 0;
  padding: 8px;
  border: 1px solid #edf1f5;
  background: #fbfcfe;
}

.dispense-detail-grid span,
.print-meta-grid span {
  display: block;
  margin-bottom: 3px;
  color: #667085;
  font-size: 12px;
}

.dispense-detail-grid strong,
.print-meta-grid strong {
  display: block;
  overflow-wrap: anywhere;
  color: #1f2937;
  font-size: 13px;
}

.dispense-print-sheet {
  min-height: 660px;
  padding: 18px;
  border: 1px solid #1f2937;
  background: #fff;
  color: #111827;
  aspect-ratio: 148 / 210;
  box-shadow: 0 8px 20px rgb(16 24 40 / 8%);
}

.print-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
  padding-bottom: 8px;
  border-bottom: 2px solid #111827;
}

.print-title h3 {
  margin: 0;
  font-size: 20px;
  letter-spacing: 0;
}

.print-title span {
  color: #475467;
  font-size: 12px;
}

.print-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  padding: 12px 0;
}

.print-section {
  margin-top: 10px;
}

.print-section h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #111827;
}

.print-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.print-table th,
.print-table td {
  padding: 6px;
  border: 1px solid #d0d5dd;
  text-align: left;
  vertical-align: top;
}

.print-table th {
  background: #f2f4f7;
  font-weight: 700;
}

.print-table strong,
.print-table small {
  display: block;
}

.print-table small {
  margin-top: 2px;
  color: #667085;
}

.print-amount-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.print-amount-grid div {
  min-width: 0;
  padding: 8px;
  border: 1px solid #d0d5dd;
  background: #fcfcfd;
}

.print-amount-grid span {
  display: block;
  margin-bottom: 3px;
  color: #667085;
  font-size: 12px;
}

.print-amount-grid strong {
  display: block;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.print-placeholder {
  padding: 10px;
  border: 1px dashed #98a2b3;
  background: #fcfcfd;
}

.print-placeholder p {
  margin: 0;
  color: #667085;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .dispense-workbench {
    grid-template-columns: 1fr;
  }

  .dispense-print-sheet {
    max-width: 520px;
  }
}

@media (max-width: 640px) {
  .dispense-detail-grid,
  .print-meta-grid {
    grid-template-columns: 1fr;
  }

  .dispense-print-sheet {
    min-height: auto;
    aspect-ratio: auto;
  }
}
</style>
