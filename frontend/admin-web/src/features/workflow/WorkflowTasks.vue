<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  approveReviewTask,
  completeDispenseTask,
  completeRecheckTask,
  listDispenseTasks,
  listRecheckTasks,
  listReviewTasks,
  rejectReviewTask,
} from '../../api/workflow';
import type { WorkflowTaskSnapshot } from '../../api/types';
import type { ViewKey } from '../../app/views';
import { formatDate } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type WorkflowCounts = { reviews: number; dispenses: number; rechecks: number };

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
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${total} 项`;
});

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
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

async function handleReview(task: WorkflowTaskSnapshot, action: 'approve' | 'reject') {
  if (!operator.value.trim()) {
    workflowError.value = '处理人不能为空';
    return;
  }

  handlingTaskId.value = task.taskId;
  workflowError.value = '';
  try {
    const command = {
      reviewer: operator.value.trim(),
      reviewComment: comment.value.trim(),
    };
    const result = action === 'approve'
      ? await approveReviewTask(task.taskId, command)
      : await rejectReviewTask(task.taskId, command);
    emit('notice', 'success', `${result.orderNo} 已${action === 'approve' ? '审核通过' : '审核拒绝'}`);
    await refreshAllWorkflowTasks();
  } catch (error) {
    workflowError.value = errorMessage(error);
  } finally {
    handlingTaskId.value = '';
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
    if (shouldRefreshActiveTasks()) void refreshCurrentTasks();
  },
);

defineExpose({
  refreshCurrentTasks,
});
</script>

<template>
  <section class="legacy-page workflow-page">
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
      <li v-if="activeView === 'rechecks'">
        <button class="legacy-btn legacy-btn-export" type="button" disabled>导出</button>
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
              <td class="legacy-left">{{ rowValue(task.reviewComment) }}</td>
              <td class="workflow-action-cell">
                <button
                  class="legacy-link-btn workflow-pass-btn"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="handleReview(task, 'approve')"
                >
                  通过
                </button>
                <button
                  class="legacy-link-btn workflow-reject-btn"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="handleReview(task, 'reject')"
                >
                  拒绝
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
              <td>{{ rowValue(dispenseUserId) }}</td>
              <td>{{ formatDate(task.completedAt) }}</td>
              <td>
                <input v-model="operator" class="legacy-input workflow-inline-input" />
              </td>
              <td>
                <input class="legacy-input workflow-inline-input" />
              </td>
              <td class="legacy-left">{{ rowValue(task.reviewComment) }}</td>
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
  </section>
</template>
