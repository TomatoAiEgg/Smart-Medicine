<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  createAdminOrderInterceptRule,
  listAdminOrderInterceptRules,
  updateAdminOrderInterceptRule,
} from '../../api/order';
import type {
  AdminOrderInterceptRuleCommand,
  AdminOrderInterceptRulePage,
  AdminOrderInterceptRuleRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface RuleForm {
  id: string | null;
  ruleCode: string;
  ruleName: string;
  interceptStage: string;
  matchField: string;
  matchType: string;
  matchValue: string;
  reason: string;
  priority: number;
  enabled: boolean;
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const keyword = ref('');
const interceptStage = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const rulePage = ref<AdminOrderInterceptRulePage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref<RuleForm>({
  id: null,
  ruleCode: '',
  ruleName: '',
  interceptStage: 'CREATE_ORDER',
  matchField: 'receiverPhone',
  matchType: 'CONTAINS',
  matchValue: '',
  reason: '',
  priority: 100,
  enabled: true,
});

const rows = computed(() => rulePage.value?.records ?? []);
const total = computed(() => rulePage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}(HTTP ${error.status})` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function enabledParam() {
  if (enabledFilter.value === 'true') return true;
  if (enabledFilter.value === 'false') return false;
  return undefined;
}

function enabledLabel(value: boolean) {
  return value ? '启用' : '停用';
}

function stageLabel(value: string) {
  const labels: Record<string, string> = {
    CREATE_ORDER: '机构推单',
    STATUS_FLOW: '状态流转',
    LOGISTICS: '物流处理',
  };
  return labels[value] ?? value;
}

function matchTypeLabel(value: string) {
  const labels: Record<string, string> = {
    EQUALS: '等于',
    CONTAINS: '包含',
    STARTS_WITH: '开头匹配',
    REGEX: '正则',
  };
  return labels[value] ?? value;
}

function normalizedPriority() {
  if (!Number.isFinite(form.value.priority) || form.value.priority < 0) return 100;
  return Math.trunc(form.value.priority);
}

function commandFromForm(): AdminOrderInterceptRuleCommand {
  return {
    ruleCode: form.value.ruleCode.trim(),
    ruleName: form.value.ruleName.trim(),
    interceptStage: form.value.interceptStage,
    matchField: form.value.matchField.trim(),
    matchType: form.value.matchType,
    matchValue: form.value.matchValue.trim(),
    reason: form.value.reason.trim(),
    priority: normalizedPriority(),
    enabled: form.value.enabled,
  };
}

function downloadRuleCsv() {
  downloadCsv(
    `订单拦截规则-${new Date().toISOString().slice(0, 10)}.csv`,
    ['规则编码', '规则名称', '场景', '匹配字段', '匹配类型', '匹配值', '优先级', '状态', '原因', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.ruleCode,
      row.ruleName,
      stageLabel(row.interceptStage),
      row.matchField,
      matchTypeLabel(row.matchType),
      row.matchValue,
      row.priority,
      enabledLabel(row.enabled),
      row.reason,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条拦截规则`);
}

async function refreshOrderInterceptRules() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminOrderInterceptRules({
      keyword: keyword.value,
      interceptStage: interceptStage.value,
      enabled: enabledParam(),
      page: page.value,
      pageSize: pageSize.value,
    });
    rulePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条拦截规则`);
  } catch (error) {
    rulePage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderInterceptRules();
}

function resetForm() {
  form.value = {
    id: null,
    ruleCode: '',
    ruleName: '',
    interceptStage: 'CREATE_ORDER',
    matchField: 'receiverPhone',
    matchType: 'CONTAINS',
    matchValue: '',
    reason: '',
    priority: 100,
    enabled: true,
  };
}

function editRule(row: AdminOrderInterceptRuleRecord) {
  form.value = {
    id: row.id,
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    interceptStage: row.interceptStage,
    matchField: row.matchField,
    matchType: row.matchType,
    matchValue: row.matchValue,
    reason: row.reason,
    priority: row.priority,
    enabled: row.enabled,
  };
}

async function saveRule() {
  if (
    !form.value.ruleCode.trim()
    || !form.value.ruleName.trim()
    || !form.value.matchField.trim()
    || !form.value.matchValue.trim()
    || !form.value.reason.trim()
  ) {
    errorLine.value = '规则编码、名称、匹配字段、匹配值和原因不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    if (form.value.id) {
      await updateAdminOrderInterceptRule(form.value.id, commandFromForm());
      emit('notice', 'success', `规则 ${form.value.ruleCode} 已更新`);
    } else {
      await createAdminOrderInterceptRule(commandFromForm());
      emit('notice', 'success', `规则 ${form.value.ruleCode} 已新增`);
    }
    resetForm();
    await refreshOrderInterceptRules();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleRule(row: AdminOrderInterceptRuleRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminOrderInterceptRule(row.id, {
      ruleName: row.ruleName,
      interceptStage: row.interceptStage,
      matchField: row.matchField,
      matchType: row.matchType,
      matchValue: row.matchValue,
      reason: row.reason,
      priority: row.priority,
      enabled: !row.enabled,
    });
    emit('notice', 'success', `规则 ${row.ruleCode} 已${row.enabled ? '停用' : '启用'}`);
    await refreshOrderInterceptRules();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderInterceptRules();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderInterceptRules();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshOrderInterceptRules();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshOrderInterceptRules,
});
</script>

<template>
  <section class="legacy-page intercept-rule-page">
    <ul class="legacy-search intercept-rule-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="编码 / 名称 / 字段 / 原因"
          @keyup.enter="searchFirstPage"
        />
      </li>
      <li>
        场景：
        <select v-model="interceptStage" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="CREATE_ORDER">机构推单</option>
          <option value="STATUS_FLOW">状态流转</option>
          <option value="LOGISTICS">物流处理</option>
        </select>
      </li>
      <li>
        状态：
        <select v-model="enabledFilter" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">停用</option>
        </select>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadRuleCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats intercept-rule-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>规则总数</span>
      </li>
      <li>
        <strong>{{ formatNumber(enabledCount) }}</strong>
        <span>本页启用</span>
      </li>
      <li>
        <strong>{{ formatNumber(disabledCount) }}</strong>
        <span>本页停用</span>
      </li>
    </ul>

    <div class="intercept-rule-edit legacy-panel">
      <div class="intercept-rule-form-grid">
        <label>
          规则编码
          <input v-model="form.ruleCode" class="legacy-input" :disabled="editing" placeholder="risk-phone" />
        </label>
        <label>
          规则名称
          <input v-model="form.ruleName" class="legacy-input" placeholder="风险电话拦截" />
        </label>
        <label>
          场景
          <select v-model="form.interceptStage" class="legacy-input">
            <option value="CREATE_ORDER">机构推单</option>
            <option value="STATUS_FLOW">状态流转</option>
            <option value="LOGISTICS">物流处理</option>
          </select>
        </label>
        <label>
          匹配类型
          <select v-model="form.matchType" class="legacy-input">
            <option value="CONTAINS">包含</option>
            <option value="EQUALS">等于</option>
            <option value="STARTS_WITH">开头匹配</option>
            <option value="REGEX">正则</option>
          </select>
        </label>
        <label>
          匹配字段
          <input v-model="form.matchField" class="legacy-input" placeholder="receiverPhone" />
        </label>
        <label>
          匹配值
          <input v-model="form.matchValue" class="legacy-input" placeholder="00000000000" />
        </label>
        <label>
          优先级
          <input v-model.number="form.priority" class="legacy-input" type="number" min="0" step="1" />
        </label>
        <label>
          状态
          <select v-model="form.enabled" class="legacy-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label class="reason-field">
          拦截原因
          <input v-model="form.reason" class="legacy-input" placeholder="展示给后台处理人员的原因" />
        </label>
      </div>
      <div class="intercept-rule-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveRule">
          {{ saving ? '保存中' : editing ? '保存规则' : '新增规则' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table intercept-rule-table">
        <thead>
          <tr>
            <th>规则</th>
            <th>场景</th>
            <th>匹配</th>
            <th>优先级</th>
            <th>状态</th>
            <th>原因</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8">正在加载拦截规则...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="8">暂无拦截规则</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>
              <strong>{{ row.ruleName }}</strong>
              <small>{{ row.ruleCode }}</small>
            </td>
            <td>{{ stageLabel(row.interceptStage) }}</td>
            <td>
              <strong>{{ row.matchField }} · {{ matchTypeLabel(row.matchType) }}</strong>
              <small>{{ row.matchValue }}</small>
            </td>
            <td>{{ row.priority }}</td>
            <td>
              <span class="legacy-status" :class="row.enabled ? 'status-success' : 'status-muted'">
                {{ enabledLabel(row.enabled) }}
              </span>
            </td>
            <td>{{ row.reason }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-link" type="button" :disabled="saving" @click="editRule(row)">编辑</button>
              <button class="legacy-link" type="button" :disabled="saving" @click="toggleRule(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <span>第 {{ formatNumber(page) }} 页 / 共 {{ formatNumber(total) }} 条</span>
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="previousPage">上一页</button>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="nextPage">下一页</button>
    </div>
  </section>
</template>

<style scoped>
.intercept-rule-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.intercept-rule-search,
.intercept-rule-stats {
  margin: 0;
}

.intercept-rule-edit {
  padding: 14px;
}

.intercept-rule-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.intercept-rule-form-grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.reason-field {
  grid-column: span 2;
}

.intercept-rule-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.intercept-rule-table td {
  vertical-align: top;
}

.intercept-rule-table strong,
.intercept-rule-table small {
  display: block;
}

.intercept-rule-table small {
  margin-top: 3px;
  color: #6b7280;
}

@media (max-width: 1100px) {
  .intercept-rule-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .intercept-rule-form-grid,
  .reason-field {
    grid-template-columns: 1fr;
    grid-column: span 1;
  }
}
</style>
