<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminLogisticsAddressCost,
  listAdminInstitutions,
  listAdminLogisticsAddressCosts,
  updateAdminLogisticsAddressCost,
} from '../../api/order';
import type {
  AdminInstitutionRecord,
  AdminLogisticsAddressCostCommand,
  AdminLogisticsAddressCostPage,
  AdminLogisticsAddressCostRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { currentIsoDate, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';
type AmountValue = number | string | null | undefined;

interface CostForm {
  id: string | null;
  institutionId: string;
  logisticsCompany: string;
  province: string;
  city: string;
  district: string;
  costAmount: string;
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
const logisticsCompany = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const costPage = ref<AdminLogisticsAddressCostPage | null>(null);
const institutionOptions = ref<AdminInstitutionRecord[]>([]);
const loading = ref(false);
const loadingInstitutions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref<CostForm>({
  id: null,
  institutionId: '',
  logisticsCompany: 'DEFAULT',
  province: '',
  city: '',
  district: '',
  costAmount: '0',
  remark: '',
  enabled: true,
});

const rows = computed(() => costPage.value?.records ?? []);
const total = computed(() => costPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function enabledLabel(value: boolean) {
  return value ? '启用' : '停用';
}

function enabledParam() {
  if (enabledFilter.value === 'true') return true;
  if (enabledFilter.value === 'false') return false;
  return undefined;
}

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

function institutionText(row: AdminInstitutionRecord | AdminLogisticsAddressCostRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function addressText(row: AdminLogisticsAddressCostRecord) {
  return [row.province, row.city, row.district].filter((part) => part.trim() !== '').join(' / ');
}

function commandFromForm(): AdminLogisticsAddressCostCommand {
  return {
    institutionId: form.value.institutionId,
    logisticsCompany: form.value.logisticsCompany.trim(),
    province: form.value.province.trim(),
    city: form.value.city.trim(),
    district: form.value.district.trim(),
    costAmount: amountInput(form.value.costAmount),
    remark: form.value.remark.trim(),
    enabled: form.value.enabled,
  };
}

function downloadAddressCostCsv() {
  downloadCsv(
    `物流地址费用-${currentIsoDate()}.csv`,
    ['机构', '物流公司', '省份', '城市', '区县', '地址', '费用', '状态', '备注', '更新时间'],
    rows.value.map((row) => [
      institutionText(row),
      row.logisticsCompany,
      row.province,
      row.city,
      row.district,
      addressText(row),
      amountText(row.costAmount),
      enabledLabel(row.enabled),
      row.remark,
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条地址费用`);
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

async function refreshAddressCosts() {
  loading.value = true;
  errorLine.value = '';
  try {
    await loadInstitutionOptions();
    const nextPage = await listAdminLogisticsAddressCosts({
      keyword: keyword.value,
      institutionId: institutionId.value,
      logisticsCompany: logisticsCompany.value,
      enabled: enabledParam(),
      page: page.value,
      pageSize: pageSize.value,
    });
    costPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条地址费用`);
  } catch (error) {
    costPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshAddressCosts();
}

function resetForm() {
  form.value = {
    id: null,
    institutionId: '',
    logisticsCompany: 'DEFAULT',
    province: '',
    city: '',
    district: '',
    costAmount: '0',
    remark: '',
    enabled: true,
  };
}

function editCost(row: AdminLogisticsAddressCostRecord) {
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    logisticsCompany: row.logisticsCompany,
    province: row.province,
    city: row.city,
    district: row.district,
    costAmount: amountText(row.costAmount),
    remark: row.remark ?? '',
    enabled: row.enabled,
  };
}

async function saveCost() {
  if (!form.value.institutionId || !form.value.logisticsCompany.trim() || !form.value.province.trim()) {
    errorLine.value = '机构、物流公司和省份不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    if (form.value.id) {
      await updateAdminLogisticsAddressCost(form.value.id, commandFromForm());
      emit('notice', 'success', '地址费用已更新');
    } else {
      await createAdminLogisticsAddressCost(commandFromForm());
      emit('notice', 'success', '地址费用已新增');
    }
    resetForm();
    await refreshAddressCosts();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleCost(row: AdminLogisticsAddressCostRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminLogisticsAddressCost(row.id, {
      logisticsCompany: row.logisticsCompany,
      province: row.province,
      city: row.city,
      district: row.district,
      costAmount: row.costAmount,
      remark: row.remark ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `${addressText(row)} 已${row.enabled ? '停用' : '启用'}`);
    await refreshAddressCosts();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshAddressCosts();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshAddressCosts();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshAddressCosts();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshAddressCosts,
});
</script>

<template>
  <section class="legacy-page address-cost-page">
    <ul class="legacy-search address-cost-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="机构 / 地址 / 备注"
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
        物流公司：
        <input v-model="logisticsCompany" class="legacy-input input-small" placeholder="SF" @keyup.enter="searchFirstPage" />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadAddressCostCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats address-cost-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>费用总数</span>
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

    <div class="address-cost-edit legacy-panel">
      <div class="address-cost-form-grid">
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
          物流公司
          <input v-model="form.logisticsCompany" class="legacy-input" placeholder="SF / EMS / DEFAULT" />
        </label>
        <label>
          省份
          <input v-model="form.province" class="legacy-input" placeholder="广东省" />
        </label>
        <label>
          城市
          <input v-model="form.city" class="legacy-input" placeholder="深圳市" />
        </label>
        <label>
          区县
          <input v-model="form.district" class="legacy-input" placeholder="南山区" />
        </label>
        <label>
          费用
          <input v-model="form.costAmount" class="legacy-input" type="number" min="0" step="0.01" />
        </label>
        <label>
          状态
          <select v-model="form.enabled" class="legacy-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label>
          备注
          <input v-model="form.remark" class="legacy-input" placeholder="地址费用说明" />
        </label>
      </div>
      <div class="address-cost-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveCost">
          {{ saving ? '保存中' : editing ? '保存费用' : '新增费用' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table address-cost-table">
        <thead>
          <tr>
            <th>机构</th>
            <th>物流公司</th>
            <th>地址</th>
            <th>费用</th>
            <th>状态</th>
            <th>备注</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8">正在加载地址费用...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="8">暂无地址费用</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>
              <strong>{{ row.institutionName }}</strong>
              <small>{{ row.institutionCode }}</small>
            </td>
            <td>{{ row.logisticsCompany }}</td>
            <td>{{ addressText(row) }}</td>
            <td>{{ amountText(row.costAmount) }}</td>
            <td>
              <span class="legacy-status" :class="row.enabled ? 'status-success' : 'status-muted'">
                {{ enabledLabel(row.enabled) }}
              </span>
            </td>
            <td>{{ rowValue(row.remark) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-link" type="button" :disabled="saving" @click="editCost(row)">编辑</button>
              <button class="legacy-link" type="button" :disabled="saving" @click="toggleCost(row)">
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
.address-cost-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.address-cost-search,
.address-cost-stats {
  margin: 0;
}

.address-cost-edit {
  padding: 14px;
}

.address-cost-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.address-cost-form-grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.address-cost-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.address-cost-table td {
  vertical-align: top;
}

.address-cost-table strong,
.address-cost-table small {
  display: block;
}

.address-cost-table small {
  margin-top: 3px;
  color: #6b7280;
}

@media (max-width: 1100px) {
  .address-cost-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .address-cost-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
