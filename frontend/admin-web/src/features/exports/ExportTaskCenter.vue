<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminOrderExportTask,
  createAdminOrderWarehouseExportTask,
  downloadAdminExportTaskFile,
  listAdminExportTasks,
  runAdminExportTask,
  runPendingAdminExportTasks,
} from '../../api/order';
import type { AdminExportTaskCreateParams, AdminExportTaskRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { errorMessage } from '../../domain/errors';
import { saveBlob } from '../../domain/download';
import { boundedPositiveInteger, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const loading = ref(false);
const actionLoading = ref(false);
const errorLine = ref('');
const taskType = ref('');
const taskStatus = ref('');
const keyword = ref('');
const page = ref(1);
const pageSize = ref(20);
const requestedBy = ref('admin');
const runImmediately = ref(true);
const orderKeyword = ref('');
const warehouseOrderNo = ref('');
const warehousePrescriptionNo = ref('');
const pendingRunLimit = ref(20);
const taskPage = ref<{ records: AdminExportTaskRecord[]; total: number; page: number; pageSize: number } | null>(null);
const runningTaskId = ref('');
const downloadingTaskId = ref('');

const rows = computed(() => taskPage.value?.records ?? []);
const total = computed(() => taskPage.value?.total ?? 0);
const successCount = computed(() => rows.value.filter((row) => row.taskStatus === 'SUCCESS').length);
const failedCount = computed(() => rows.value.filter((row) => row.taskStatus === 'FAILED').length);
const pendingCount = computed(() => rows.value.filter((row) => row.taskStatus === 'PENDING' || row.taskStatus === 'RUNNING').length);

function normalizedPageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function normalizedPendingRunLimit() {
  return boundedPositiveInteger(pendingRunLimit.value, 20, 50);
}

function taskTypeText(value: string) {
  const labels: Record<string, string> = {
    ADMIN_ORDERS: '订单信息汇总',
    ADMIN_ORDER_WAREHOUSES: '订单仓库汇总',
  };
  return labels[value] ?? value;
}

function taskStatusText(value: string) {
  const labels: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败',
  };
  return labels[value] ?? value;
}

function fileSizeText(value: number | null) {
  if (value === null || value <= 0) return '-';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function queryParamText(row: AdminExportTaskRecord) {
  if (!row.queryParam || row.queryParam === '{}') return '全部条件';
  try {
    const parsed = JSON.parse(row.queryParam) as unknown;
    if (!isStringRecord(parsed)) return row.queryParam;
    const entries = Object.entries(parsed);
    if (entries.length === 0) return '全部条件';
    return entries.map(([key, value]) => `${key}=${value}`).join('；');
  } catch {
    return row.queryParam;
  }
}

function isStringRecord(value: unknown): value is Record<string, string> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  return Object.values(value).every((entry) => typeof entry === 'string');
}

function baseCreateParams(): AdminExportTaskCreateParams {
  return {
    requestedBy: requestedBy.value.trim() || 'admin',
    runImmediately: runImmediately.value,
  };
}

async function refreshExportTasks() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizedPageSize();
  try {
    taskPage.value = await listAdminExportTasks({
      taskType: taskType.value,
      taskStatus: taskStatus.value,
      keyword: keyword.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    emit('countChanged', taskPage.value.total);
  } catch (error) {
    errorLine.value = errorMessage(error);
    taskPage.value = null;
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function createOrderTask() {
  actionLoading.value = true;
  errorLine.value = '';
  try {
    const task = await createAdminOrderExportTask({
      ...baseCreateParams(),
      keyword: orderKeyword.value.trim() || undefined,
    });
    emit('notice', task.taskStatus === 'SUCCESS' ? 'success' : 'info', `${taskTypeText(task.taskType)}任务已创建`);
    await refreshExportTasks();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    actionLoading.value = false;
  }
}

async function createWarehouseTask() {
  actionLoading.value = true;
  errorLine.value = '';
  try {
    const task = await createAdminOrderWarehouseExportTask({
      ...baseCreateParams(),
      orderNo: warehouseOrderNo.value.trim() || undefined,
      prescriptionNo: warehousePrescriptionNo.value.trim() || undefined,
    });
    emit('notice', task.taskStatus === 'SUCCESS' ? 'success' : 'info', `${taskTypeText(task.taskType)}任务已创建`);
    await refreshExportTasks();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    actionLoading.value = false;
  }
}

async function runPendingTasks() {
  actionLoading.value = true;
  errorLine.value = '';
  pendingRunLimit.value = normalizedPendingRunLimit();
  try {
    const result = await runPendingAdminExportTasks(pendingRunLimit.value);
    emit('notice', 'success', `已执行 ${result.totalCount} 个待处理任务，成功 ${result.successCount} 个，失败 ${result.failCount} 个`);
    await refreshExportTasks();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    actionLoading.value = false;
  }
}

async function runTask(row: AdminExportTaskRecord) {
  runningTaskId.value = row.taskId;
  errorLine.value = '';
  try {
    const task = await runAdminExportTask(row.taskId);
    emit('notice', task.taskStatus === 'SUCCESS' ? 'success' : 'error', `${taskTypeText(task.taskType)} ${taskStatusText(task.taskStatus)}`);
    await refreshExportTasks();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    runningTaskId.value = '';
  }
}

async function downloadTask(row: AdminExportTaskRecord) {
  downloadingTaskId.value = row.taskId;
  errorLine.value = '';
  try {
    const blob = await downloadAdminExportTaskFile(row.taskId);
    saveBlob(row.fileName || 'export-task.csv', blob);
    emit('notice', 'success', `${row.fileName || '导出文件'} 已下载`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    downloadingTaskId.value = '';
  }
}

watch(total, (count) => emit('countChanged', count), { immediate: true });

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshExportTasks();
  },
  { immediate: true },
);

defineExpose({
  refreshExportTasks,
});
</script>

<template>
  <section class="legacy-page export-task-page">
    <div class="legacy-search-panel export-task-toolbar">
      <label>
        <span>任务类型</span>
        <select v-model="taskType" class="legacy-input" @change="refreshExportTasks">
          <option value="">全部类型</option>
          <option value="ADMIN_ORDERS">订单信息汇总</option>
          <option value="ADMIN_ORDER_WAREHOUSES">订单仓库汇总</option>
        </select>
      </label>
      <label>
        <span>任务状态</span>
        <select v-model="taskStatus" class="legacy-input" @change="refreshExportTasks">
          <option value="">全部状态</option>
          <option value="PENDING">待执行</option>
          <option value="RUNNING">执行中</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
        </select>
      </label>
      <label class="grow">
        <span>关键词</span>
        <input v-model="keyword" class="legacy-input" placeholder="任务名 / 文件名 / 创建人" @keyup.enter="refreshExportTasks" />
      </label>
      <label class="limit-label">
        <span>每页</span>
        <input v-model.number="pageSize" class="legacy-input" type="number" min="1" max="100" @keyup.enter="refreshExportTasks" />
      </label>
      <button class="primary" type="button" :disabled="loading" @click="refreshExportTasks">
        {{ loading ? '查询中' : '查询' }}
      </button>
    </div>

    <div class="legacy-search-panel export-create-panel">
      <label>
        <span>创建人</span>
        <input v-model="requestedBy" class="legacy-input" placeholder="admin" />
      </label>
      <label class="checkbox-label">
        <input v-model="runImmediately" type="checkbox" />
        <span>创建后立即执行</span>
      </label>
      <label>
        <span>订单关键词</span>
        <input v-model="orderKeyword" class="legacy-input" placeholder="订单号 / 处方号 / 患者" @keyup.enter="createOrderTask" />
      </label>
      <button class="secondary" type="button" :disabled="actionLoading" @click="createOrderTask">
        创建订单汇总
      </button>
      <label>
        <span>仓库订单号</span>
        <input v-model="warehouseOrderNo" class="legacy-input" placeholder="ZHYF..." @keyup.enter="createWarehouseTask" />
      </label>
      <label>
        <span>仓库处方号</span>
        <input v-model="warehousePrescriptionNo" class="legacy-input" placeholder="RX..." @keyup.enter="createWarehouseTask" />
      </label>
      <button class="secondary" type="button" :disabled="actionLoading" @click="createWarehouseTask">
        创建仓库汇总
      </button>
      <label class="limit-label">
        <span>执行数</span>
        <input v-model.number="pendingRunLimit" class="legacy-input" type="number" min="1" max="50" />
      </label>
      <button type="button" :disabled="actionLoading" @click="runPendingTasks">
        执行待处理
      </button>
    </div>

    <ul class="legacy-stats export-task-stats">
      <li>
        <span>总任务</span>
        <strong>{{ formatNumber(total) }}</strong>
      </li>
      <li>
        <span>当前页成功</span>
        <strong>{{ formatNumber(successCount) }}</strong>
      </li>
      <li>
        <span>当前页失败</span>
        <strong>{{ formatNumber(failedCount) }}</strong>
      </li>
      <li>
        <span>当前页待处理</span>
        <strong>{{ formatNumber(pendingCount) }}</strong>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel export-task-table-panel">
      <table class="legacy-main-table export-task-table">
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>查询条件</th>
            <th>文件</th>
            <th>行数/大小</th>
            <th>创建/完成</th>
            <th>失败原因</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="legacy-empty">正在查询导出任务</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="8" class="legacy-empty">暂无导出任务</td>
          </tr>
          <template v-else>
            <tr v-for="row in rows" :key="row.taskId">
              <td>
                <strong>{{ taskTypeText(row.taskType) }}</strong>
                <small>{{ row.taskName }} / {{ row.requestedBy || '-' }}</small>
              </td>
              <td>
                <StatusPill :value="taskStatusText(row.taskStatus)" :tone="statusTone(row.taskStatus)" />
                <small>重试 {{ row.retryCount }} 次</small>
              </td>
              <td class="query-cell">{{ queryParamText(row) }}</td>
              <td>
                <strong>{{ row.fileName || '-' }}</strong>
                <small>{{ row.contentType || '-' }}</small>
              </td>
              <td>
                <strong>{{ row.rowCount === null ? '-' : formatNumber(row.rowCount) }}</strong>
                <small>{{ fileSizeText(row.fileSizeBytes) }}</small>
              </td>
              <td>
                <strong>{{ formatDate(row.createdAt) }}</strong>
                <small>{{ formatDate(row.completedAt || row.startedAt || row.updatedAt) }}</small>
              </td>
              <td><code>{{ row.failureReason || '-' }}</code></td>
              <td>
                <div class="actions">
                  <button
                    class="secondary"
                    type="button"
                    :disabled="row.taskStatus !== 'SUCCESS' || downloadingTaskId === row.taskId"
                    @click="downloadTask(row)"
                  >
                    {{ downloadingTaskId === row.taskId ? '下载中' : '下载' }}
                  </button>
                  <button
                    type="button"
                    :disabled="row.taskStatus === 'RUNNING' || runningTaskId === row.taskId"
                    @click="runTask(row)"
                  >
                    {{ runningTaskId === row.taskId ? '执行中' : row.taskStatus === 'FAILED' ? '重试' : '执行' }}
                  </button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.export-task-toolbar,
.export-create-panel {
  align-items: end;
}

.export-task-toolbar .grow {
  flex: 1 1 260px;
}

.export-create-panel {
  margin-top: 10px;
}

.checkbox-label {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}

.checkbox-label input {
  width: auto;
}

.export-task-stats {
  margin: 12px 0;
}

.export-task-table-panel {
  overflow-x: auto;
}

.export-task-table {
  min-width: 1180px;
}

.export-task-table th,
.export-task-table td {
  vertical-align: top;
}

.export-task-table td strong,
.export-task-table td small {
  display: block;
}

.query-cell {
  max-width: 260px;
  white-space: normal;
  word-break: break-all;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

code {
  white-space: normal;
  word-break: break-all;
}
</style>
