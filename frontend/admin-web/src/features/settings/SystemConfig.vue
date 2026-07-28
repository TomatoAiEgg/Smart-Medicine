<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  createAdminSystemConfig,
  listAdminSystemConfigs,
  updateAdminSystemConfig,
} from '../../api/order';
import type { AdminSystemConfigPage, AdminSystemConfigRecord } from '../../api/types';
import { formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';
type ValueType = 'STRING' | 'BOOLEAN' | 'NUMBER' | 'JSON';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const valueTypes = [
  { value: '', label: '全部' },
  { value: 'STRING', label: '文本' },
  { value: 'BOOLEAN', label: '布尔' },
  { value: 'NUMBER', label: '数字' },
  { value: 'JSON', label: 'JSON' },
] as const;

const formValueTypes = valueTypes.filter((option) => option.value !== '');

const keyword = ref('');
const valueType = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const configPage = ref<AdminSystemConfigPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref({
  id: '',
  configKey: '',
  configName: '',
  configValue: '',
  valueType: 'STRING' as ValueType,
  enabled: true,
  remark: '',
});

const rows = computed(() => configPage.value?.records ?? []);
const total = computed(() => configPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== '');

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function queryEnabled() {
  return enabledFilter.value === '' ? undefined : enabledFilter.value;
}

function rowValue(value: string | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return value;
}

function enabledText(value: boolean) {
  return value ? '启用' : '停用';
}

function valueTypeText(value: string) {
  return valueTypes.find((option) => option.value === value)?.label ?? value;
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

function resetForm() {
  form.value = {
    id: '',
    configKey: '',
    configName: '',
    configValue: '',
    valueType: 'STRING',
    enabled: true,
    remark: '',
  };
}

function editConfig(row: AdminSystemConfigRecord) {
  form.value = {
    id: row.id,
    configKey: row.configKey,
    configName: row.configName,
    configValue: row.configValue,
    valueType: row.valueType as ValueType,
    enabled: row.enabled,
    remark: row.remark ?? '',
  };
}

async function refreshSystemConfigs() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminSystemConfigs({
      keyword: keyword.value,
      valueType: valueType.value,
      enabled: queryEnabled(),
      page: page.value,
      pageSize: pageSize.value,
    });
    configPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新系统参数：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    configPage.value = null;
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshSystemConfigs();
}

async function saveConfig() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      configKey: form.value.configKey.trim(),
      configName: form.value.configName.trim(),
      configValue: form.value.configValue.trim(),
      valueType: form.value.valueType,
      enabled: form.value.enabled,
      remark: form.value.remark.trim(),
    };
    const saved = editing.value
      ? await updateAdminSystemConfig(form.value.id, command)
      : await createAdminSystemConfig(command);
    emit('notice', 'success', `${saved.configName} 已保存`);
    resetForm();
    await refreshSystemConfigs();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleConfig(row: AdminSystemConfigRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminSystemConfig(row.id, {
      configName: row.configName,
      configValue: row.configValue,
      valueType: row.valueType,
      enabled: !row.enabled,
      remark: row.remark ?? '',
    });
    emit('notice', 'success', `${row.configName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshSystemConfigs();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshSystemConfigs();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshSystemConfigs();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshSystemConfigs();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshSystemConfigs,
});
</script>

<template>
  <section class="legacy-page system-config-page">
    <ul class="legacy-search config-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        类型：
        <select v-model="valueType" class="legacy-input input-medium" @change="searchFirstPage">
          <option v-for="option in valueTypes" :key="option.value" :value="option.value">
            {{ option.label }}
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
          查询
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <ul class="legacy-stats config-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>参数总数</span>
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

    <section class="legacy-panel config-form-panel">
      <div class="legacy-panel-title">{{ editing ? '编辑系统参数' : '新增系统参数' }}</div>
      <div class="config-form-grid">
        <label>
          参数键
          <input v-model="form.configKey" class="legacy-input" :disabled="editing || saving" />
        </label>
        <label>
          参数名称
          <input v-model="form.configName" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          值类型
          <select v-model="form.valueType" class="legacy-input" :disabled="saving">
            <option v-for="option in formValueTypes" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="enabled-field">
          <input v-model="form.enabled" type="checkbox" :disabled="saving" />
          启用
        </label>
        <label class="value-field">
          参数值
          <textarea v-model="form.configValue" class="legacy-input config-value-input" rows="3" :disabled="saving" />
        </label>
        <label class="remark-field">
          备注
          <input v-model="form.remark" class="legacy-input" :disabled="saving" />
        </label>
      </div>
      <div class="config-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveConfig">
          {{ editing ? '保存参数' : '新增参数' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </section>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>参数键</th>
            <th>参数名称</th>
            <th>类型</th>
            <th>参数值</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="7" class="empty-cell">暂无系统参数</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.configKey }}</td>
            <td>{{ row.configName }}</td>
            <td>{{ valueTypeText(row.valueType) }}</td>
            <td class="value-cell">{{ rowValue(row.configValue) }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editConfig(row)">编辑</button>
              <button class="legacy-link-btn" type="button" :disabled="saving" @click="toggleConfig(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="previousPage">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ formatNumber(total) }} 条</span>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="nextPage">下一页</button>
      <label>
        每页
        <input
          v-model.number="pageSize"
          class="legacy-input input-small"
          type="number"
          min="1"
          max="100"
          @keyup.enter="searchFirstPage"
        />
      </label>
    </div>
  </section>
</template>

<style scoped>
.config-search {
  align-items: center;
}

.config-stats {
  margin-bottom: 16px;
}

.config-form-panel {
  margin-bottom: 16px;
}

.config-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.config-form-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.enabled-field {
  grid-template-columns: auto 1fr;
  align-items: center;
  align-content: end;
}

.value-field,
.remark-field {
  grid-column: 1 / -1;
}

.config-value-input {
  font-family: Consolas, "Microsoft YaHei", monospace;
  line-height: 1.5;
}

.config-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.value-cell {
  max-width: 420px;
  white-space: normal;
  word-break: break-word;
}

.action-cell {
  white-space: nowrap;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

@media (max-width: 920px) {
  .config-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
