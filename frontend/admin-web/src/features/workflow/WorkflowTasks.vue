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
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

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

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
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
  <section class="workspace">
    <div class="toolbar">
      <label>
        <span>处理人</span>
        <input v-model="operator" placeholder="admin" />
      </label>
      <label class="grow">
        <span>处理意见</span>
        <input v-model="comment" placeholder="填写本次处理意见" />
      </label>
      <button
        class="primary"
        type="button"
        :disabled="activeWorkflowLoading"
        @click="refreshCurrentTasks"
      >
        {{ activeWorkflowLoading ? '刷新中' : '刷新' }}
      </button>
    </div>

    <p v-if="workflowError" class="error-line">{{ workflowError }}</p>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>订单号</th>
            <th>外部单号</th>
            <th>任务类型</th>
            <th>任务状态</th>
            <th>订单状态</th>
            <th>创建时间</th>
            <th class="right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!activeWorkflowLoading && activeWorkflowTasks.length === 0">
            <td colspan="7" class="empty">{{ activeWorkflowEmptyText }}</td>
          </tr>
          <tr v-for="task in activeWorkflowTasks" :key="task.taskId">
            <td>
              <strong>{{ task.orderNo }}</strong>
              <small>{{ task.orderId }}</small>
            </td>
            <td>{{ task.externalOrderNo }}</td>
            <td>{{ task.taskType }}</td>
            <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
            <td><StatusPill :value="task.orderStatus" :tone="statusTone(task.orderStatus)" /></td>
            <td>{{ formatDate(task.createdAt) }}</td>
            <td class="actions">
              <template v-if="activeView === 'reviews'">
                <button
                  class="success"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="handleReview(task, 'approve')"
                >
                  通过
                </button>
                <button
                  class="danger"
                  type="button"
                  :disabled="handlingTaskId === task.taskId"
                  @click="handleReview(task, 'reject')"
                >
                  拒绝
                </button>
              </template>
              <button
                v-else-if="activeView === 'dispenses'"
                class="success"
                type="button"
                :disabled="handlingTaskId === task.taskId"
                @click="handleDispense(task)"
              >
                完成调剂
              </button>
              <button
                v-else
                class="success"
                type="button"
                :disabled="handlingTaskId === task.taskId"
                @click="handleRecheck(task)"
              >
                完成复核
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
