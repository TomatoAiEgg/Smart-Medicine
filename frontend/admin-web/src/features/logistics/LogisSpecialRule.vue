<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminLogisticsSpecialRule,
  listAdminInstitutions,
  listAdminLogisticsSpecialRules,
  updateAdminLogisticsSpecialRule,
} from '../../api/order';
import type {
  AdminInstitutionRecord,
  AdminLogisticsSpecialRuleCommand,
  AdminLogisticsSpecialRulePage,
  AdminLogisticsSpecialRuleRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, enabledBooleanParam, enabledText, displayValue, currentIsoDate, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';
type AmountValue = number | string | null | undefined;

interface RuleForm {
  id: string | null;
  institutionId: string;
  ruleName: string;
  logisticsCompany: string;
  baseFee: string;
  extraFee: string;
  freeThreshold: string;
  remark: string;
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
const institutionId = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const rulePage = ref<AdminLogisticsSpecialRulePage | null>(null);
const institutionOptions = ref<AdminInstitutionRecord[]>([]);
const loading = ref(false);
const loadingInstitutions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref<RuleForm>({
  id: null,
  institutionId: '',
  ruleName: '',
  logisticsCompany: 'DEFAULT',
  baseFee: '0',
  extraFee: '0',
  freeThreshold: '0',
  remark: '',
  enabled: true,
});

const rows = computed(() => rulePage.value?.records ?? []);
const total = computed(() => rulePage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);

function amountNumber(value: AmountValue) {
  if (value === null || value === undefined || value === '') return 0;
  const nextValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(nextValue) ? nextValue : 0;
}

function amountText(value: AmountValue) {
  return amountNumber(value).toFixed(2);
}

function amountInput(value: string) {
  const nextValue = Number(value);
  return Number.isFinite(nextValue) && nextValue >= 0 ? value : '0';
}

function institutionText(row: AdminInstitutionRecord | AdminLogisticsSpecialRuleRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function commandFromForm(): AdminLogisticsSpecialRuleCommand {
  return {
    institutionId: form.value.institutionId,
    ruleName: form.value.ruleName.trim(),
    logisticsCompany: form.value.logisticsCompany.trim(),
    baseFee: amountInput(form.value.baseFee),
    extraFee: amountInput(form.value.extraFee),
    freeThreshold: amountInput(form.value.freeThreshold),
    remark: form.value.remark.trim(),
    enabled: form.value.enabled,
  };
}

function downloadSpecialRuleCsv() {
  downloadCsv(
    `物流特殊规则-${currentIsoDate()}.csv`,
    ['机构', '规则名称', '物流公司', '基础费用', '附加费用', '免邮阈值', '状态', '备注', '更新时间'],
    rows.value.map((row) => [
      institutionText(row),
      row.ruleName,
      row.logisticsCompany,
      amountText(row.baseFee),
      amountText(row.extraFee),
      amountText(row.freeThreshold),
      enabledText(row.enabled),
      row.remark,
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条物流规则`);
}

async function loadInstitutionOptions() {
  if (loadingInstitutions.value || institutionOptions.value.length > 0) return;
  loadingInstitutions.value = true;
  try {
    const institutionPage = await listAdminInstitutions({ page: 1, pageSize: 100 });
    institutionOptions.value = institutionPage.records;
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    loadingInstitutions.value = false;
  }
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshLogisticsSpecialRules() {
  loading.value = true;
  errorLine.value = '';
  try {
    await loadInstitutionOptions();
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminLogisticsSpecialRules({
      keyword: keyword.value,
      institutionId: institutionId.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    rulePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条物流规则`);
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
  await refreshLogisticsSpecialRules();
}

function resetForm() {
  form.value = {
    id: null,
    institutionId: '',
    ruleName: '',
    logisticsCompany: 'DEFAULT',
    baseFee: '0',
    extraFee: '0',
    freeThreshold: '0',
    remark: '',
    enabled: true,
  };
}

function editRule(row: AdminLogisticsSpecialRuleRecord) {
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    ruleName: row.ruleName,
    logisticsCompany: row.logisticsCompany,
    baseFee: amountText(row.baseFee),
    extraFee: amountText(row.extraFee),
    freeThreshold: amountText(row.freeThreshold),
    remark: row.remark ?? '',
    enabled: row.enabled,
  };
}

async function saveRule() {
  if (!form.value.institutionId || !form.value.ruleName.trim() || !form.value.logisticsCompany.trim()) {
    errorLine.value = '机构、规则名称和物流公司不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    if (form.value.id) {
      await updateAdminLogisticsSpecialRule(form.value.id, commandFromForm());
      emit('notice', 'success', `规则 ${form.value.ruleName} 已更新`);
    } else {
      await createAdminLogisticsSpecialRule(commandFromForm());
      emit('notice', 'success', `规则 ${form.value.ruleName} 已新增`);
    }
    resetForm();
    await refreshLogisticsSpecialRules();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleRule(row: AdminLogisticsSpecialRuleRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminLogisticsSpecialRule(row.id, {
      ruleName: row.ruleName,
      logisticsCompany: row.logisticsCompany,
      baseFee: row.baseFee,
      extraFee: row.extraFee,
      freeThreshold: row.freeThreshold,
      remark: row.remark ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `规则 ${row.ruleName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshLogisticsSpecialRules();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshLogisticsSpecialRules();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshLogisticsSpecialRules();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshLogisticsSpecialRules();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshLogisticsSpecialRules,
});
</script>

<template>
  <section class="legacy-page logistics-special-rule-page">
    <ul class="legacy-search special-rule-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="机构 / 规则 / 物流公司 / 备注"
          @keyup.enter="searchFirstPage"
        />
      </li>
      <li>
        机构：
        <select v-model="institutionId" class="legacy-input input-medium" @change="searchFirstPage">
          <option value="">全部</option>
          <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
            {{ institutionText(row) }}
          </option>
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadSpecialRuleCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats special-rule-stats">
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

    <div class="special-rule-edit legacy-panel">
      <div class="special-rule-form-grid">
        <label>
          机构
          <select v-model="form.institutionId" class="legacy-input" :disabled="editing || loadingInstitutions">
            <option value="">{{ loadingInstitutions ? '加载机构中' : '请选择机构' }}</option>
            <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
              {{ institutionText(row) }}
            </option>
          </select>
        </label>
        <label>
          规则名称
          <input v-model="form.ruleName" class="legacy-input" placeholder="默认物流费" />
        </label>
        <label>
          物流公司
          <input v-model="form.logisticsCompany" class="legacy-input" placeholder="SF / EMS / DEFAULT" />
        </label>
        <label>
          状态
          <select v-model="form.enabled" class="legacy-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label>
          基础费用
          <input v-model="form.baseFee" class="legacy-input" type="number" min="0" step="0.01" />
        </label>
        <label>
          附加费用
          <input v-model="form.extraFee" class="legacy-input" type="number" min="0" step="0.01" />
        </label>
        <label>
          免邮阈值
          <input v-model="form.freeThreshold" class="legacy-input" type="number" min="0" step="0.01" />
        </label>
        <label>
          备注
          <input v-model="form.remark" class="legacy-input" placeholder="费用规则说明" />
        </label>
      </div>
      <div class="special-rule-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveRule">
          {{ saving ? '保存中' : editing ? '保存规则' : '新增规则' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table special-rule-table">
        <thead>
          <tr>
            <th>机构</th>
            <th>规则</th>
            <th>物流公司</th>
            <th>基础费用</th>
            <th>附加费用</th>
            <th>免邮阈值</th>
            <th>状态</th>
            <th>备注</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10">正在加载物流规则...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="10">暂无物流规则</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>
              <strong>{{ row.institutionName }}</strong>
              <small>{{ row.institutionCode }}</small>
            </td>
            <td>{{ row.ruleName }}</td>
            <td>{{ row.logisticsCompany }}</td>
            <td>{{ amountText(row.baseFee) }}</td>
            <td>{{ amountText(row.extraFee) }}</td>
            <td>{{ amountText(row.freeThreshold) }}</td>
            <td>
              <span class="legacy-status" :class="row.enabled ? 'status-success' : 'status-muted'">
                {{ enabledText(row.enabled) }}
              </span>
            </td>
            <td>{{ displayValue(row.remark) }}</td>
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
.logistics-special-rule-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.special-rule-search,
.special-rule-stats {
  margin: 0;
}

.special-rule-edit {
  padding: 14px;
}

.special-rule-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.special-rule-form-grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.special-rule-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.special-rule-table td {
  vertical-align: top;
}

.special-rule-table strong,
.special-rule-table small {
  display: block;
}

.special-rule-table small {
  margin-top: 3px;
  color: #6b7280;
}

@media (max-width: 1100px) {
  .special-rule-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .special-rule-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
