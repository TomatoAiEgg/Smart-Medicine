<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminSystemConfig,
  listAdminSystemConfigs,
  updateAdminSystemConfig,
} from '../../api/order';
import type {
  AdminSystemConfigCommand,
  AdminSystemConfigPage,
  AdminSystemConfigRecord,
} from '../../api/types';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminPagination from '../../components/admin/AdminPagination.vue';
import AdminPanel from '../../components/admin/AdminPanel.vue';
import AdminStatusTag from '../../components/admin/AdminStatusTag.vue';
import AdminTableShell from '../../components/admin/AdminTableShell.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import { downloadCsv } from '../../domain/csv';
import { errorMessage } from '../../domain/errors';
import {
  boundedPositiveInteger,
  enabledStringParam,
  formatDate,
  formatNumber,
} from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';
type ValueType = 'STRING' | 'BOOLEAN' | 'NUMBER' | 'JSON';

interface SystemConfigForm {
  id: string;
  configKey: string;
  configName: string;
  configValue: string;
  valueType: ValueType;
  enabled: boolean;
  remark: string;
}

interface ConfigStat {
  label: string;
  value: string;
}

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

const formValueTypes: ReadonlyArray<{ value: ValueType; label: string }> = [
  { value: 'STRING', label: '文本' },
  { value: 'BOOLEAN', label: '布尔' },
  { value: 'NUMBER', label: '数字' },
  { value: 'JSON', label: 'JSON' },
];

const keyword = ref('');
const valueType = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const configPage = ref<AdminSystemConfigPage | null>(null);
const loading = ref(false);
const mutating = ref(false);
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);

const form = ref<SystemConfigForm>({
  id: '',
  configKey: '',
  configName: '',
  configValue: '',
  valueType: 'STRING',
  enabled: true,
  remark: '',
});

const rows = computed(() => configPage.value?.records ?? []);
const total = computed(() => configPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const editing = computed(() => form.value.id !== '');
const canExport = computed(() => !loading.value && rows.value.length > 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (listError.value && configPage.value === null) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<ConfigStat[]>(() => [
  { label: '参数总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function displayText(value: string | null | undefined) {
  if (value === null || value === undefined) return '--';
  const trimmed = String(value).trim();
  return trimmed ? trimmed : '--';
}

function valueTypeText(value: string) {
  return valueTypes.find((option) => option.value === value)?.label ?? value;
}

function resetForm() {
  actionError.value = '';
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
  actionError.value = '';
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

function downloadConfigCsv() {
  downloadCsv(
    `系统参数-第${page.value}页.csv`,
    ['参数键', '参数名称', '类型', '参数值', '状态', '备注', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.configKey,
      row.configName,
      valueTypeText(row.valueType),
      row.configValue,
      row.enabled ? '启用' : '停用',
      row.remark ?? '',
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条系统参数`);
}

async function refreshSystemConfigs() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminSystemConfigs({
      keyword: keyword.value,
      valueType: valueType.value,
      enabled: enabledStringParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    if (requestId !== activeRefreshRequest.value) return;
    configPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新系统参数：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    configPage.value = null;
    loaded.value = false;
    listError.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    if (requestId === activeRefreshRequest.value) {
      loading.value = false;
    }
  }
}

async function searchFirstPage() {
  if (loading.value || mutating.value) return;
  page.value = 1;
  await refreshSystemConfigs();
}

async function saveConfig() {
  if (loading.value || mutating.value) return;
  mutating.value = true;
  actionError.value = '';
  try {
    const command: AdminSystemConfigCommand = {
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
    actionError.value = errorMessage(error);
  } finally {
    mutating.value = false;
  }
}

async function toggleConfig(row: AdminSystemConfigRecord) {
  if (loading.value || mutating.value) return;
  mutating.value = true;
  actionError.value = '';
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
    actionError.value = errorMessage(error);
  } finally {
    mutating.value = false;
  }
}

async function previousPage() {
  if (loading.value || !hasPreviousPage.value) return;
  page.value -= 1;
  await refreshSystemConfigs();
}

async function nextPage() {
  if (loading.value || !hasNextPage.value) return;
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
  <section class="system-config-page">
    <AdminToolbar>
      <label class="config-field config-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="config-input"
          :disabled="loading || mutating"
          placeholder="参数名称 / 参数键 / 参数值"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="config-field config-field--type">
        <span>值类型</span>
        <select
          v-model="valueType"
          class="config-input"
          :disabled="loading || mutating"
          @change="searchFirstPage"
        >
          <option v-for="option in valueTypes" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label class="config-field config-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="config-input"
          :disabled="loading || mutating"
          @change="searchFirstPage"
        >
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">停用</option>
        </select>
      </label>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="loading || mutating"
          @click="searchFirstPage"
        >
          {{ loading ? '查询中' : '查询' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="!canExport"
          @click="downloadConfigCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="config-stats" aria-label="系统参数统计">
      <article v-for="stat in stats" :key="stat.label" class="config-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="config-edit-panel">
      <template #title>{{ editing ? '编辑系统参数' : '新增系统参数' }}</template>
      <template #description>维护系统参数名称、键值、类型与启停状态。</template>

      <form class="config-form" @submit.prevent="saveConfig">
        <p v-if="actionError" class="error-line" role="alert">{{ actionError }}</p>
        <div class="config-form-grid">
          <label class="config-field">
            <span>参数键</span>
            <input
              v-model="form.configKey"
              class="config-input"
              :disabled="editing || loading || mutating"
              required
              placeholder="system.config.key"
            >
          </label>
          <label class="config-field">
            <span>参数名称</span>
            <input
              v-model="form.configName"
              class="config-input"
              :disabled="loading || mutating"
              required
              placeholder="系统参数名称"
            >
          </label>
          <label class="config-field">
            <span>值类型</span>
            <select
              v-model="form.valueType"
              class="config-input"
              :disabled="loading || mutating"
            >
              <option v-for="option in formValueTypes" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="config-check">
            <input
              v-model="form.enabled"
              type="checkbox"
              :disabled="loading || mutating"
            >
            <span>启用</span>
          </label>
          <label class="config-field config-field--full">
            <span>参数值</span>
            <textarea
              v-model="form.configValue"
              class="config-input config-textarea"
              rows="3"
              :disabled="loading || mutating"
              required
            />
          </label>
          <label class="config-field config-field--full">
            <span>备注</span>
            <input
              v-model="form.remark"
              class="config-input"
              :disabled="loading || mutating"
              placeholder="备注"
            >
          </label>
        </div>
        <div class="config-form-actions">
          <t-button
            theme="primary"
            variant="outline"
            size="small"
            type="submit"
            :disabled="loading || mutating"
          >
            {{ mutating ? '保存中' : editing ? '保存参数' : '新增参数' }}
          </t-button>
          <t-button
            theme="default"
            variant="outline"
            size="small"
            type="button"
            :disabled="loading || mutating"
            @click="resetForm"
          >
            清空
          </t-button>
        </div>
      </form>
    </AdminPanel>

    <AdminPanel class="config-list-panel">
      <template #title>参数列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索系统参数。' }}
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询系统参数。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关系统参数。"
      />
      <template v-else>
        <AdminTableShell>
          <table class="config-table">
            <thead>
              <tr>
                <th>参数</th>
                <th>值类型</th>
                <th>参数值</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="primary-cell">
                    <strong>{{ row.configName }}</strong>
                    <small>{{ row.configKey }}</small>
                  </div>
                </td>
                <td>{{ valueTypeText(row.valueType) }}</td>
                <td>
                  <div class="config-value" :title="row.configValue">
                    {{ displayText(row.configValue) }}
                  </div>
                </td>
                <td>
                  <AdminStatusTag :enabled="row.enabled" />
                </td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="loading || mutating"
                    @click="editConfig(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="loading || mutating"
                    @click="toggleConfig(row)"
                  >
                    {{ row.enabled ? '停用' : '启用' }}
                  </t-button>
                </td>
              </tr>
            </tbody>
          </table>
        </AdminTableShell>

        <div class="pagination-row">
          <AdminPagination
            :page="page"
            :page-size="pageSize"
            :total="total"
            :loading="loading"
            @previous="previousPage"
            @next="nextPage"
          />
          <label class="page-size-field">
            <span>每页</span>
            <input
              v-model.number="pageSize"
              class="config-input config-input--page-size"
              type="number"
              min="1"
              max="100"
              :disabled="loading || mutating"
              @keyup.enter="searchFirstPage"
            >
          </label>
        </div>
      </template>
    </AdminPanel>
  </section>
</template>

<style scoped>
.system-config-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.config-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.config-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.config-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.config-stat span {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.config-form {
  display: grid;
  gap: 12px;
}

.config-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(92px, auto);
  gap: 12px;
  min-width: 0;
}

.config-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.config-field--keyword {
  flex: 1 1 280px;
}

.config-field--type,
.config-field--status {
  flex: 0 0 150px;
}

.config-field--full {
  grid-column: 1 / -1;
}

.config-field span,
.config-check span,
.page-size-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.config-input {
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #d7deea;
  border-radius: 6px;
  color: #1f2937;
  background: #ffffff;
  font-size: 13px;
  line-height: 20px;
}

.config-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.config-input--page-size {
  width: 92px;
}

.config-textarea {
  min-height: 92px;
  padding: 8px 10px;
  resize: vertical;
  font-family: Consolas, 'Microsoft YaHei', monospace;
}

.config-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding-top: 24px;
}

.config-form-actions,
.pagination-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 12px;
}

.page-size-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.config-table {
  min-width: 980px;
}

.primary-cell {
  display: grid;
  gap: 2px;
}

.primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.config-value {
  display: -webkit-box;
  max-width: 360px;
  overflow: hidden;
  color: #374151;
  line-height: 20px;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.row-actions {
  white-space: nowrap;
}

.row-actions :deep(.t-button) {
  margin-right: 8px;
}

.row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 980px) {
  .config-form-grid {
    grid-template-columns: 1fr;
  }

  .config-check {
    padding-top: 0;
  }

  .page-size-field {
    margin-left: 0;
  }
}
</style>
