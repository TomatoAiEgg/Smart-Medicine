<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { completeRecheckTask, listRecheckTasks } from '../../api/workflow';
import type { WorkflowTaskSnapshot } from '../../api/types';
import { formatDate } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type RecheckMode = 'single' | 'multi';

interface RecheckScanForm {
  prescriptionCode: string;
  dispenseUser: string;
  recheckUser: string;
  pailNo: string;
  comment: string;
}

const props = defineProps<{
  active: boolean;
  mode: RecheckMode;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const tasks = ref<WorkflowTaskSnapshot[]>([]);
const loading = ref(false);
const errorText = ref('');
const selectedTaskId = ref('');
const handlingTaskId = ref('');
const form = ref<RecheckScanForm>({
  prescriptionCode: '',
  dispenseUser: '',
  recheckUser: '',
  pailNo: '',
  comment: '扫码复核完成',
});

const modeLabel = computed(() => (props.mode === 'multi' ? '多桶复核' : '扫码复核'));
const exactMatchedTask = computed(() => {
  const keyword = normalized(form.value.prescriptionCode);
  if (!keyword) return null;
  return tasks.value.find((task) => taskMatchKeys(task).some((key) => normalized(key) === keyword)) ?? null;
});
const selectedTask = computed(() => {
  if (selectedTaskId.value) {
    const found = tasks.value.find((task) => task.taskId === selectedTaskId.value);
    if (found) return found;
  }
  return exactMatchedTask.value;
});
const filteredTasks = computed(() => {
  const keyword = normalized(form.value.prescriptionCode);
  if (!keyword) return tasks.value;
  return tasks.value.filter((task) => taskMatchKeys(task).some((key) => normalized(key).includes(keyword)));
});
const pageSummary = computed(() => {
  const total = filteredTasks.value.length;
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${tasks.value.length} 项待复核`;
});
const canComplete = computed(() => Boolean(selectedTask.value && form.value.recheckUser.trim()));
const activeTaskDetail = computed(() => {
  const task = selectedTask.value;
  if (!task) return null;
  return [
    { label: '平台处方号', value: task.orderNo },
    { label: '机构处方号', value: valueText(task.externalOrderNo) },
    { label: '订单ID', value: task.orderId },
    { label: '创建时间', value: formatDate(task.createdAt) },
  ];
});

function normalized(value: string | null | undefined) {
  return (value ?? '').trim().toLowerCase();
}

function valueText(value: string | null | undefined) {
  return value && value.trim() ? value : '-';
}

function taskMatchKeys(task: WorkflowTaskSnapshot): string[] {
  return [task.orderNo, task.externalOrderNo, task.orderId, task.taskId].filter((value) => value.trim().length > 0);
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function emitCountChanged() {
  emit('countChanged', tasks.value.length);
}

async function refreshRecheckScanTasks() {
  loading.value = true;
  errorText.value = '';
  try {
    tasks.value = await listRecheckTasks();
    if (selectedTaskId.value && !tasks.value.some((task) => task.taskId === selectedTaskId.value)) {
      selectedTaskId.value = '';
    }
    emit('notice', 'info', `已刷新待复核任务：${tasks.value.length} 条`);
  } catch (error) {
    tasks.value = [];
    selectedTaskId.value = '';
    errorText.value = errorMessage(error);
  } finally {
    loading.value = false;
    emitCountChanged();
  }
}

function selectTask(task: WorkflowTaskSnapshot) {
  selectedTaskId.value = task.taskId;
  form.value.prescriptionCode = task.orderNo;
}

function buildReviewComment() {
  const segments = [
    form.value.comment.trim() || '扫码复核完成',
    `作业模式：${modeLabel.value}`,
    `调剂工号：${form.value.dispenseUser.trim() || '-'}`,
    `复核工号：${form.value.recheckUser.trim()}`,
    `加水桶：${form.value.pailNo.trim() || '-'}`,
  ];
  return segments.join('；');
}

async function handleCompleteRecheck() {
  const task = selectedTask.value;
  if (!task) {
    errorText.value = '请先输入或选择待复核处方';
    return;
  }
  if (!form.value.recheckUser.trim()) {
    errorText.value = '复核工号不能为空';
    return;
  }

  handlingTaskId.value = task.taskId;
  errorText.value = '';
  try {
    const result = await completeRecheckTask(task.taskId, {
      reviewer: form.value.recheckUser.trim(),
      reviewComment: buildReviewComment(),
    });
    emit('notice', 'success', `${result.orderNo} 已完成复核`);
    selectedTaskId.value = '';
    form.value.prescriptionCode = '';
    form.value.pailNo = '';
    await refreshRecheckScanTasks();
  } catch (error) {
    errorText.value = errorMessage(error);
  } finally {
    handlingTaskId.value = '';
  }
}

onMounted(() => {
  void refreshRecheckScanTasks();
});

watch(
  () => props.active,
  (active) => {
    if (active && tasks.value.length === 0) void refreshRecheckScanTasks();
  },
);

watch(
  () => form.value.prescriptionCode,
  (value) => {
    if (!selectedTaskId.value) return;
    const selected = tasks.value.find((task) => task.taskId === selectedTaskId.value);
    if (!selected) {
      selectedTaskId.value = '';
      return;
    }
    const keyword = normalized(value);
    if (!keyword || !taskMatchKeys(selected).some((key) => normalized(key).includes(keyword))) {
      selectedTaskId.value = '';
    }
  },
);

defineExpose({
  refreshRecheckScanTasks,
});
</script>

<template>
  <section class="legacy-page recheck-scan-page">
    <ul class="legacy-search recheck-scan-search">
      <li>
        处方/订单号：
        <input
          v-model="form.prescriptionCode"
          class="legacy-input input-large"
          placeholder="扫描或输入平台处方号/机构处方号"
        />
      </li>
      <li>
        待复核任务：
        <select v-model="selectedTaskId" class="legacy-input input-large">
          <option value="">按处方号自动匹配</option>
          <option v-for="task in filteredTasks" :key="task.taskId" :value="task.taskId">
            {{ task.orderNo }} / {{ valueText(task.externalOrderNo) }}
          </option>
        </select>
      </li>
      <li>
        调剂工号：
        <input v-model="form.dispenseUser" class="legacy-input" placeholder="dispenseUser" />
      </li>
      <li>
        复核工号：
        <input v-model="form.recheckUser" class="legacy-input" placeholder="recheckUser" />
      </li>
      <li>
        加水桶：
        <input v-model="form.pailNo" class="legacy-input" placeholder="pailNo" />
      </li>
      <li>
        作业备注：
        <input v-model="form.comment" class="legacy-input input-large" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshRecheckScanTasks">
          {{ loading ? '刷新中' : '查处方' }}
        </button>
      </li>
      <li>
        <button
          class="legacy-btn legacy-btn-export"
          type="button"
          :disabled="!canComplete || handlingTaskId !== ''"
          @click="handleCompleteRecheck"
        >
          {{ handlingTaskId ? '复核中' : '复核' }}
        </button>
      </li>
    </ul>

    <p v-if="errorText" class="error-line">{{ errorText }}</p>

    <div class="recheck-workbench">
      <section class="recheck-active-panel">
        <header>
          <span>{{ modeLabel }}</span>
          <strong>{{ selectedTask ? selectedTask.orderNo : '未选择处方' }}</strong>
        </header>
        <div v-if="activeTaskDetail" class="recheck-detail-grid">
          <div v-for="item in activeTaskDetail" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <p v-else class="legacy-empty">请扫描处方号，或从待复核任务中选择一条记录。</p>
      </section>

      <section class="recheck-active-panel">
        <header>
          <span>作业字段</span>
          <strong>{{ form.recheckUser || '-' }}</strong>
        </header>
        <div class="recheck-detail-grid">
          <div>
            <span>调剂员 dispenseUser</span>
            <strong>{{ form.dispenseUser || '-' }}</strong>
          </div>
          <div>
            <span>复核员 recheckUser</span>
            <strong>{{ form.recheckUser || '-' }}</strong>
          </div>
          <div>
            <span>加水桶 pailNo</span>
            <strong>{{ form.pailNo || '-' }}</strong>
          </div>
          <div>
            <span>任务数量</span>
            <strong>{{ tasks.length }}</strong>
          </div>
        </div>
      </section>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table workflow-main-table recheck-scan-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台处方号</th>
            <th>机构处方号</th>
            <th>订单ID</th>
            <th>任务状态</th>
            <th>接单时间</th>
            <th>复核员</th>
            <th>复核意见</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">正在刷新待复核任务</td>
          </tr>
          <tr v-else-if="filteredTasks.length === 0" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">暂无匹配的待复核任务</td>
          </tr>
          <tr
            v-for="task in filteredTasks"
            :key="task.taskId"
            class="legacy-main-info"
            :class="{ selected: selectedTask?.taskId === task.taskId }"
          >
            <td>
              <strong>{{ task.orderNo }}</strong>
              <small>{{ task.taskId.slice(0, 8) }}</small>
            </td>
            <td>{{ valueText(task.externalOrderNo) }}</td>
            <td>{{ task.orderId }}</td>
            <td>{{ task.taskStatus }}</td>
            <td>{{ formatDate(task.createdAt) }}</td>
            <td>{{ valueText(task.reviewer) }}</td>
            <td class="legacy-left">{{ valueText(task.reviewComment) }}</td>
            <td class="workflow-action-cell">
              <button class="legacy-link-btn workflow-pass-btn" type="button" @click="selectTask(task)">选择</button>
              <button
                class="legacy-link-btn workflow-pass-btn"
                type="button"
                :disabled="handlingTaskId === task.taskId"
                @click="selectedTaskId = task.taskId; handleCompleteRecheck()"
              >
                复核
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">{{ pageSummary }}</p>
  </section>
</template>

<style scoped>
.recheck-scan-search {
  align-items: center;
}

.recheck-workbench {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 12px 0;
}

.recheck-active-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  background: var(--admin-surface);
  box-shadow: 0 2px 8px rgba(30, 48, 76, 0.04);
}

.recheck-active-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.recheck-active-panel header span,
.recheck-detail-grid span {
  color: var(--admin-muted);
  font-size: 12px;
}

.recheck-active-panel header strong {
  color: var(--admin-blue-dark);
  font-size: 18px;
}

.recheck-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.recheck-detail-grid div {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--admin-line);
  border-radius: 6px;
  background: #f8fafc;
}

.recheck-detail-grid strong {
  overflow-wrap: anywhere;
  color: var(--admin-text);
  font-size: 14px;
}

.recheck-scan-table {
  min-width: 1280px;
}

.legacy-main-info.selected td {
  background: #edf8f5;
}

@media (max-width: 1100px) {
  .recheck-workbench,
  .recheck-detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
