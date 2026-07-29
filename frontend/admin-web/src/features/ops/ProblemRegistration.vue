<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  createProblemRegistration,
  handleProblemRegistration,
  listProblemRegistrationActions,
  listProblemRegistrations,
  updateProblemRegistration,
} from '../../api/ops';
import type {
  ProblemRegistrationActionRecord,
  ProblemRegistrationRecord,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
  operationOperator: string;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
  'update:operationOperator': [value: string];
}>();

const statusOptions = [
  { value: '', label: '未关闭' },
  { value: 'ALL', label: '全部' },
  { value: 'OPEN', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
] as const;

const problemTypeOptions = [
  { value: 'ORDER', label: '订单问题' },
  { value: 'LOGISTICS', label: '物流问题' },
  { value: 'PRESCRIPTION', label: '处方问题' },
  { value: 'PAYMENT', label: '金额问题' },
] as const;

const handleStatusOptions = [
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
] as const;

const operator = computed({
  get: () => props.operationOperator,
  set: (value: string) => emit('update:operationOperator', value),
});
const statusFilter = ref('');
const orderNoFilter = ref('');
const keyword = ref('');
const limit = ref(50);
const loading = ref(false);
const submitting = ref(false);
const actionLoading = ref(false);
const errorLine = ref('');
const requestId = ref(0);
const records = ref<ProblemRegistrationRecord[]>([]);
const actions = ref<ProblemRegistrationActionRecord[]>([]);
const selectedRecord = ref<ProblemRegistrationRecord | null>(null);

const form = ref({
  id: '',
  orderNo: '',
  problemType: 'ORDER',
  problemReason: '',
  handlingPlan: '',
  amount: 0,
  remark: '',
});

const handleForm = ref({
  id: '',
  status: 'PROCESSING',
  handlingPlan: '',
  amount: 0,
  remark: '',
});

const activeCount = computed(() => records.value.filter((record) => record.status !== 'CLOSED').length);
const closedCount = computed(() => records.value.filter((record) => record.status === 'CLOSED').length);
const isEditing = computed(() => form.value.id !== '');

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

function formatAmount(value: number | null | undefined) {
  const amount = value ?? 0;
  return amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function normalizedLimit() {
  if (!Number.isFinite(limit.value) || limit.value <= 0) return 50;
  return Math.min(Math.trunc(limit.value), 200);
}

async function refreshProblemRegistrations() {
  const nextRequestId = requestId.value + 1;
  requestId.value = nextRequestId;
  loading.value = true;
  errorLine.value = '';
  limit.value = normalizedLimit();
  try {
    records.value = await listProblemRegistrations({
      status: statusFilter.value,
      orderNo: orderNoFilter.value,
      keyword: keyword.value,
      limit: limit.value,
    });
    if (nextRequestId !== requestId.value) return;
    emit('countChanged', records.value.length);
    emit('notice', 'info', `已刷新问题件登记：${records.value.length} 条`);
  } catch (error) {
    if (nextRequestId === requestId.value) {
      records.value = [];
      emit('countChanged', 0);
      errorLine.value = errorMessage(error);
    }
  } finally {
    if (nextRequestId === requestId.value) {
      loading.value = false;
    }
  }
}

function resetFilters() {
  statusFilter.value = '';
  orderNoFilter.value = '';
  keyword.value = '';
  limit.value = 50;
  void refreshProblemRegistrations();
}

function resetForm() {
  form.value = {
    id: '',
    orderNo: '',
    problemType: 'ORDER',
    problemReason: '',
    handlingPlan: '',
    amount: 0,
    remark: '',
  };
}

function editRecord(record: ProblemRegistrationRecord) {
  form.value = {
    id: record.id,
    orderNo: record.orderNo,
    problemType: record.problemType,
    problemReason: record.problemReason,
    handlingPlan: record.handlingPlan,
    amount: record.amount,
    remark: record.remark ?? '',
  };
}

async function saveRecord() {
  submitting.value = true;
  errorLine.value = '';
  try {
    const command = {
      orderNo: form.value.orderNo.trim(),
      problemType: form.value.problemType,
      problemReason: form.value.problemReason.trim(),
      handlingPlan: form.value.handlingPlan.trim(),
      amount: Number.isFinite(form.value.amount) ? form.value.amount : 0,
      operator: operator.value.trim() || 'admin',
      remark: form.value.remark.trim(),
    };
    const saved = isEditing.value
      ? await updateProblemRegistration(form.value.id, command)
      : await createProblemRegistration(command);
    emit('notice', 'success', `${saved.orderNo} 问题件已保存`);
    resetForm();
    await refreshProblemRegistrations();
    await selectRecord(saved);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

function prepareHandle(record: ProblemRegistrationRecord) {
  selectedRecord.value = record;
  handleForm.value = {
    id: record.id,
    status: record.status === 'OPEN' ? 'PROCESSING' : record.status,
    handlingPlan: record.handlingPlan,
    amount: record.amount,
    remark: '',
  };
  void loadActions(record.id);
}

async function submitHandle() {
  if (!handleForm.value.id) return;
  submitting.value = true;
  errorLine.value = '';
  try {
    const updated = await handleProblemRegistration(handleForm.value.id, {
      status: handleForm.value.status,
      handlingPlan: handleForm.value.handlingPlan.trim(),
      amount: Number.isFinite(handleForm.value.amount) ? handleForm.value.amount : 0,
      operator: operator.value.trim() || 'admin',
      remark: handleForm.value.remark.trim(),
    });
    emit('notice', 'success', `${updated.orderNo} 已更新为${statusLabel(updated.status)}`);
    await refreshProblemRegistrations();
    await selectRecord(updated);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function selectRecord(record: ProblemRegistrationRecord) {
  selectedRecord.value = record;
  handleForm.value = {
    id: record.id,
    status: record.status === 'OPEN' ? 'PROCESSING' : record.status,
    handlingPlan: record.handlingPlan,
    amount: record.amount,
    remark: '',
  };
  await loadActions(record.id);
}

async function loadActions(registrationId: string) {
  actionLoading.value = true;
  try {
    actions.value = await listProblemRegistrationActions(registrationId);
  } catch (error) {
    actions.value = [];
    errorLine.value = errorMessage(error);
  } finally {
    actionLoading.value = false;
  }
}

function statusLabel(status: string) {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function problemTypeLabel(type: string) {
  return problemTypeOptions.find((option) => option.value === type)?.label ?? type;
}

function downloadProblemCsv() {
  downloadCsv(
    `问题件登记-${limit.value}条.csv`,
    [
      '订单号',
      '外部订单号',
      '机构',
      '问题类型',
      '状态',
      '登记原因',
      '处理方案',
      '金额',
      '操作人',
      '备注',
      '创建时间',
      '更新时间',
      '处理时间',
      '关闭时间',
    ],
    records.value.map((record) => [
      record.orderNo,
      record.externalOrderNo,
      record.institutionName,
      problemTypeLabel(record.problemType),
      statusLabel(record.status),
      record.problemReason,
      record.handlingPlan,
      record.amount,
      record.operator,
      record.remark,
      formatDate(record.createdAt),
      formatDate(record.updatedAt),
      formatDate(record.processedAt),
      formatDate(record.closedAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${formatNumber(records.value.length)} 条问题件登记`);
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshProblemRegistrations();
  },
  { immediate: true },
);

defineExpose({
  refreshProblemRegistrations,
});
</script>

<template>
  <section class="legacy-page problem-registration-page">
    <ul class="legacy-search problem-search">
      <li>
        状态：
        <select v-model="statusFilter" class="legacy-input input-medium" @change="refreshProblemRegistrations">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </li>
      <li>
        订单号：
        <input v-model="orderNoFilter" class="legacy-input input-large" @keyup.enter="refreshProblemRegistrations" />
      </li>
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="refreshProblemRegistrations" />
      </li>
      <li>
        条数：
        <input v-model.number="limit" class="legacy-input input-small" type="number" min="1" max="200" @keyup.enter="refreshProblemRegistrations" />
      </li>
      <li class="legacy-search-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshProblemRegistrations">
          查询
        </button>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">
          重置
        </button>
        <button class="legacy-btn" type="button" :disabled="loading || records.length === 0" @click="downloadProblemCsv">
          导出当前结果
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <div class="legacy-stats">
      <span>当前列表：{{ records.length }}</span>
      <span>未关闭：{{ activeCount }}</span>
      <span>已关闭：{{ closedCount }}</span>
    </div>

    <div class="problem-layout">
      <section class="legacy-panel problem-form-panel">
        <div class="legacy-panel-title">{{ isEditing ? '编辑问题件' : '登记问题件' }}</div>
        <div class="problem-form-grid">
          <label>
            <span>订单号</span>
            <input v-model="form.orderNo" class="legacy-input" :disabled="isEditing || submitting" />
          </label>
          <label>
            <span>问题类型</span>
            <select v-model="form.problemType" class="legacy-input" :disabled="submitting">
              <option v-for="option in problemTypeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <span>金额</span>
            <input v-model.number="form.amount" class="legacy-input" type="number" min="0" step="0.01" :disabled="submitting" />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="operator" class="legacy-input" :disabled="submitting" />
          </label>
          <label class="wide-field">
            <span>登记原因</span>
            <textarea v-model="form.problemReason" class="legacy-input" rows="3" :disabled="submitting" />
          </label>
          <label class="wide-field">
            <span>处理方案</span>
            <textarea v-model="form.handlingPlan" class="legacy-input" rows="3" :disabled="submitting" />
          </label>
          <label class="wide-field">
            <span>备注</span>
            <textarea v-model="form.remark" class="legacy-input" rows="2" :disabled="submitting" />
          </label>
        </div>
        <div class="problem-form-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="saveRecord">
            保存
          </button>
          <button class="legacy-btn" type="button" :disabled="submitting" @click="resetForm">
            清空
          </button>
        </div>
      </section>

      <section class="legacy-panel problem-handle-panel">
        <div class="legacy-panel-title">处理状态</div>
        <template v-if="selectedRecord">
          <div class="selected-summary">
            <strong>{{ selectedRecord.orderNo }}</strong>
            <StatusPill :value="statusLabel(selectedRecord.status)" :tone="statusTone(selectedRecord.status)" />
          </div>
          <div class="problem-form-grid compact-grid">
            <label>
              <span>处理状态</span>
              <select v-model="handleForm.status" class="legacy-input" :disabled="submitting">
                <option v-for="option in handleStatusOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label>
              <span>金额</span>
              <input v-model.number="handleForm.amount" class="legacy-input" type="number" min="0" step="0.01" :disabled="submitting" />
            </label>
            <label class="wide-field">
              <span>处理方案</span>
              <textarea v-model="handleForm.handlingPlan" class="legacy-input" rows="3" :disabled="submitting" />
            </label>
            <label class="wide-field">
              <span>处理备注</span>
              <textarea v-model="handleForm.remark" class="legacy-input" rows="3" :disabled="submitting" />
            </label>
          </div>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitHandle">
            提交处理
          </button>
        </template>
        <div v-else class="empty-hint">请选择一条问题件</div>
      </section>
    </div>

    <div class="legacy-table-wrap problem-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>机构</th>
            <th>类型</th>
            <th>状态</th>
            <th>登记原因</th>
            <th>处理方案</th>
            <th>金额</th>
            <th>操作人</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && records.length === 0">
            <td colspan="10" class="empty-cell">暂无问题件</td>
          </tr>
          <tr v-for="record in records" :key="record.id" :class="{ selected: selectedRecord?.id === record.id }">
            <td>
              <strong>{{ record.orderNo }}</strong>
              <div class="muted-text">{{ rowValue(record.externalOrderNo) }}</div>
            </td>
            <td>{{ rowValue(record.institutionName) }}</td>
            <td>{{ problemTypeLabel(record.problemType) }}</td>
            <td>
              <StatusPill :value="statusLabel(record.status)" :tone="statusTone(record.status)" />
            </td>
            <td class="text-cell">{{ record.problemReason }}</td>
            <td class="text-cell">{{ record.handlingPlan }}</td>
            <td>{{ formatAmount(record.amount) }}</td>
            <td>{{ rowValue(record.operator) }}</td>
            <td>{{ formatDate(record.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editRecord(record)">编辑</button>
              <button class="legacy-link-btn workflow-pass-btn" type="button" @click="prepareHandle(record)">处理</button>
              <button class="legacy-link-btn" type="button" @click="selectRecord(record)">记录</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <section v-if="selectedRecord" class="legacy-panel action-panel">
      <div class="legacy-panel-title">处理记录</div>
      <div v-if="actionLoading" class="empty-hint">加载中...</div>
      <div v-else-if="actions.length === 0" class="empty-hint">暂无处理记录</div>
      <ul v-else class="action-list">
        <li v-for="action in actions" :key="action.id">
          <span class="action-time">{{ formatDate(action.createdAt) }}</span>
          <strong>{{ action.action }}</strong>
          <span>{{ rowValue(action.fromStatus) }} → {{ rowValue(action.toStatus) }}</span>
          <span>{{ action.operator }}</span>
          <span class="action-remark">{{ rowValue(action.remark) }}</span>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.problem-search {
  align-items: center;
}

.problem-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 16px;
  margin-bottom: 16px;
}

.problem-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.problem-form-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.wide-field {
  grid-column: 1 / -1;
}

.compact-grid {
  margin-bottom: 12px;
}

.problem-form-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.selected-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.problem-table-wrap {
  margin-top: 8px;
}

.text-cell {
  min-width: 180px;
  max-width: 280px;
  white-space: normal;
}

.muted-text,
.empty-hint {
  color: #64748b;
  font-size: 12px;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

.action-cell {
  min-width: 132px;
  white-space: nowrap;
}

.legacy-table tr.selected {
  background: #eff6ff;
}

.action-panel {
  margin-top: 16px;
}

.action-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.action-list li {
  display: grid;
  grid-template-columns: 160px 80px 120px 90px minmax(180px, 1fr);
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.action-time,
.action-remark {
  color: #64748b;
}

@media (max-width: 980px) {
  .problem-layout {
    grid-template-columns: 1fr;
  }

  .action-list li {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .problem-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
