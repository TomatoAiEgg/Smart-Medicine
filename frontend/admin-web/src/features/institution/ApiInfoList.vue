<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { createAdminInstitutionApi, listAdminInstitutionApis, updateAdminInstitutionApi } from '../../api/order';
import type {
  AdminInstitutionApiCommand,
  AdminInstitutionApiPage,
  AdminInstitutionApiRecord,
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
  currentIsoDate,
  displayValue,
  enabledBooleanParam,
  enabledText,
  formatDate,
  formatNumber,
} from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface ApiForm {
  id: string | null;
  apiCode: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  description: string;
  enabled: boolean;
}

interface ApiStat {
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

const keyword = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const apiPage = ref<AdminInstitutionApiPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);
const form = ref<ApiForm>({
  id: null,
  apiCode: '',
  apiName: '',
  requestMethod: 'POST',
  requestPath: '',
  description: '',
  enabled: true,
});

const rows = computed(() => apiPage.value?.records ?? []);
const total = computed(() => apiPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);
const canExport = computed(() => !loading.value && rows.value.length > 0);
const rowActionsDisabled = computed(() => loading.value || saving.value);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (listError.value && rows.value.length === 0) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<ApiStat[]>(() => [
  { label: '接口总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function commandFromForm(): AdminInstitutionApiCommand {
  return {
    apiCode: form.value.apiCode.trim(),
    apiName: form.value.apiName.trim(),
    requestMethod: form.value.requestMethod,
    requestPath: form.value.requestPath.trim(),
    description: form.value.description.trim(),
    enabled: form.value.enabled,
  };
}

function downloadApiCsv() {
  if (loading.value || rows.value.length === 0) return;
  downloadCsv(
    `机构接口列表-${currentIsoDate()}.csv`,
    ['接口编码', '接口名称', '方法', '路径', '描述', '状态', '更新时间'],
    rows.value.map((row) => [
      row.apiCode,
      row.apiName,
      row.requestMethod,
      row.requestPath,
      row.description,
      enabledText(row.enabled),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个接口`);
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshInstitutionApis() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminInstitutionApis({
      keyword: keyword.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    if (requestId !== activeRefreshRequest.value) return;
    const lastPage = Math.max(1, Math.ceil(nextPage.total / nextPage.pageSize));
    if (nextPage.records.length === 0 && page.value > lastPage) {
      page.value = lastPage;
      await refreshInstitutionApis();
      return;
    }
    apiPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个接口`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    listError.value = errorMessage(error);
    if (!loaded.value) {
      apiPage.value = null;
      emit('countChanged', 0);
    }
  } finally {
    if (requestId === activeRefreshRequest.value) {
      loading.value = false;
    }
  }
}

async function searchFirstPage() {
  if (loading.value) return;
  page.value = 1;
  await refreshInstitutionApis();
}

function resetForm() {
  actionError.value = '';
  form.value = {
    id: null,
    apiCode: '',
    apiName: '',
    requestMethod: 'POST',
    requestPath: '',
    description: '',
    enabled: true,
  };
}

function editApi(row: AdminInstitutionApiRecord) {
  actionError.value = '';
  form.value = {
    id: row.id,
    apiCode: row.apiCode,
    apiName: row.apiName,
    requestMethod: row.requestMethod,
    requestPath: row.requestPath,
    description: row.description ?? '',
    enabled: row.enabled,
  };
}

async function saveApi() {
  if (saving.value) return;
  if (!form.value.apiCode.trim() || !form.value.apiName.trim() || !form.value.requestPath.trim()) {
    actionError.value = '接口编码、名称和路径不能为空';
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    if (form.value.id) {
      await updateAdminInstitutionApi(form.value.id, commandFromForm());
      emit('notice', 'success', `接口 ${form.value.apiCode} 已更新`);
    } else {
      await createAdminInstitutionApi(commandFromForm());
      emit('notice', 'success', `接口 ${form.value.apiCode} 已新增`);
    }
    resetForm();
    await refreshInstitutionApis();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleApi(row: AdminInstitutionApiRecord) {
  if (saving.value) return;
  saving.value = true;
  actionError.value = '';
  try {
    await updateAdminInstitutionApi(row.id, {
      apiName: row.apiName,
      requestMethod: row.requestMethod,
      requestPath: row.requestPath,
      description: row.description ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `接口 ${row.apiCode} 已${row.enabled ? '停用' : '启用'}`);
    await refreshInstitutionApis();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (loading.value) return;
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshInstitutionApis();
}

async function nextPage() {
  if (loading.value) return;
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshInstitutionApis();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshInstitutionApis();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshInstitutionApis,
});
</script>

<template>
  <section class="api-info-page">
    <AdminToolbar>
      <label class="api-field api-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="api-input"
          :disabled="loading"
          placeholder="接口编码 / 名称 / 路径 / 描述"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="api-field api-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="api-input"
          :disabled="loading"
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
          :disabled="loading"
          @click="searchFirstPage"
        >
          {{ loading ? '查询中' : '查询' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="!canExport"
          @click="downloadApiCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="api-stats" aria-label="接口统计">
      <article v-for="stat in stats" :key="stat.label" class="api-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="api-edit-panel">
      <template #title>{{ editing ? '编辑接口' : '新增接口' }}</template>
      <template #description>维护接口名称、请求方式、路径、描述与业务状态。</template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="saving"
          @click="saveApi"
        >
          {{ saving ? '保存中' : editing ? '保存修改' : '新增接口' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="saving"
          @click="resetForm"
        >
          清空
        </t-button>
      </template>

      <p v-if="actionError" class="error-line" role="alert">{{ actionError }}</p>

      <div class="api-form-grid">
        <label class="api-field">
          <span>接口编码</span>
          <input
            v-model="form.apiCode"
            class="api-input"
            :disabled="editing"
            placeholder="createOrder"
          >
        </label>
        <label class="api-field">
          <span>接口名称</span>
          <input
            v-model="form.apiName"
            class="api-input"
            placeholder="机构下单"
          >
        </label>
        <label class="api-field">
          <span>方法</span>
          <select v-model="form.requestMethod" class="api-input">
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PATCH">PATCH</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
          </select>
        </label>
        <label class="api-field api-field--path">
          <span>路径</span>
          <input
            v-model="form.requestPath"
            class="api-input"
            placeholder="/api/institution/createOrder"
          >
        </label>
        <label class="api-field">
          <span>状态</span>
          <select v-model="form.enabled" class="api-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label class="api-field api-field--description">
          <span>描述</span>
          <input
            v-model="form.description"
            class="api-input"
            placeholder="接口用途说明"
          >
        </label>
      </div>
    </AdminPanel>

    <AdminPanel class="api-list-panel">
      <template #title>接口列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索机构接口。' }}
      </template>
      <template #actions>
        <span class="api-list-note">接口名称为主信息，编码显示在名称下方。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询接口。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关接口。"
      />
      <template v-else>
        <p v-if="listError" class="error-line api-list-error" role="alert">{{ listError }}</p>
        <AdminTableShell>
          <table class="api-table">
            <thead>
              <tr>
                <th>接口</th>
                <th>方法</th>
                <th>路径</th>
                <th>描述</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="api-primary-cell">
                    <strong>{{ displayValue(row.apiName) }}</strong>
                    <small>{{ displayValue(row.apiCode) }}</small>
                  </div>
                </td>
                <td>
                  <code class="api-method">{{ displayValue(row.requestMethod) }}</code>
                </td>
                <td>
                  <code class="api-path">{{ displayValue(row.requestPath) }}</code>
                </td>
                <td>
                  <div class="api-description">{{ displayValue(row.description) }}</div>
                </td>
                <td>
                  <AdminStatusTag :enabled="row.enabled" />
                </td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="api-row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="editApi(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="toggleApi(row)"
                  >
                    {{ row.enabled ? '停用' : '启用' }}
                  </t-button>
                </td>
              </tr>
            </tbody>
          </table>
        </AdminTableShell>

        <AdminPagination
          :page="page"
          :page-size="pageSize"
          :total="total"
          :loading="loading"
          @previous="previousPage"
          @next="nextPage"
        />
      </template>
    </AdminPanel>
  </section>
</template>

<style scoped>
.api-info-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.api-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.api-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.api-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.api-stat span,
.api-list-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.api-form-grid {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) minmax(160px, 1fr) minmax(100px, 0.6fr) minmax(240px, 1.5fr) minmax(110px, 0.6fr);
  gap: 12px;
  min-width: 0;
}

.api-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.api-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.api-field--keyword {
  flex: 1 1 320px;
}

.api-field--status {
  flex: 0 0 150px;
}

.api-field--description {
  grid-column: 1 / -1;
}

.api-input {
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

.api-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.api-table {
  min-width: 1160px;
}

.api-primary-cell {
  display: grid;
  gap: 2px;
  min-width: 180px;
}

.api-primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.api-primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.api-method,
.api-path {
  color: #111827;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 18px;
}

.api-method {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  background: #f3f4f6;
}

.api-path,
.api-description {
  display: block;
  min-width: 220px;
  max-width: 360px;
  overflow-wrap: anywhere;
  white-space: normal;
  word-break: break-word;
}

.api-description {
  color: #374151;
  font-size: 13px;
  line-height: 20px;
}

.api-row-actions {
  white-space: nowrap;
}

.api-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.api-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 20px;
}

.api-list-error {
  margin-bottom: 12px;
}

@media (max-width: 980px) {
  .api-form-grid {
    grid-template-columns: 1fr;
  }

  .api-field--description {
    grid-column: auto;
  }
}
</style>
