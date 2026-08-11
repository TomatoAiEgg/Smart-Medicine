<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminInstitutionApiPermission,
  listAdminInstitutionApiPermissions,
  listAdminInstitutionApis,
  listAdminInstitutions,
  updateAdminInstitutionApiPermission,
} from '../../api/order';
import type {
  AdminInstitutionApiPermissionCommand,
  AdminInstitutionApiPermissionPage,
  AdminInstitutionApiPermissionRecord,
  AdminInstitutionApiRecord,
  AdminInstitutionRecord,
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

interface PermissionForm {
  id: string | null;
  institutionId: string;
  apiId: string;
  remark: string;
  enabled: boolean;
}

interface PermissionStat {
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
const institutionId = ref('');
const apiId = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const permissionPage = ref<AdminInstitutionApiPermissionPage | null>(null);
const institutionOptions = ref<AdminInstitutionRecord[]>([]);
const apiOptions = ref<AdminInstitutionApiRecord[]>([]);
const optionsLoaded = ref(false);
const loading = ref(false);
const loadingOptions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);
let optionsRequest: Promise<void> | null = null;
const form = ref<PermissionForm>({
  id: null,
  institutionId: '',
  apiId: '',
  remark: '',
  enabled: true,
});

const rows = computed(() => permissionPage.value?.records ?? []);
const total = computed(() => permissionPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);
const canExport = computed(() => !loading.value && rows.value.length > 0);
const rowActionsDisabled = computed(() => loading.value || saving.value);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && rows.value.length === 0) return 'loading';
  if (listError.value && rows.value.length === 0) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<PermissionStat[]>(() => [
  { label: '授权总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function institutionText(row: AdminInstitutionRecord | AdminInstitutionApiPermissionRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function apiText(row: AdminInstitutionApiRecord | AdminInstitutionApiPermissionRecord) {
  return `${row.apiName}（${row.apiCode}）`;
}

function commandFromForm(): AdminInstitutionApiPermissionCommand {
  return {
    institutionId: form.value.institutionId,
    apiId: form.value.apiId,
    remark: form.value.remark.trim(),
    enabled: form.value.enabled,
  };
}

function downloadPermissionCsv() {
  if (loading.value || rows.value.length === 0) return;
  downloadCsv(
    `机构接口授权-${currentIsoDate()}.csv`,
    ['机构', '接口', '方法', '路径', '状态', '备注', '更新时间'],
    rows.value.map((row) => [
      institutionText(row),
      apiText(row),
      row.requestMethod,
      row.requestPath,
      enabledText(row.enabled),
      row.remark,
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条接口授权`);
}

function loadOptions() {
  if (optionsLoaded.value) return Promise.resolve();
  if (!optionsRequest) {
    loadingOptions.value = true;
    optionsRequest = Promise.all([
      listAdminInstitutions({ page: 1, pageSize: 100 }),
      listAdminInstitutionApis({ page: 1, pageSize: 100 }),
    ])
      .then(([institutionPage, apiPage]) => {
        institutionOptions.value = institutionPage.records;
        apiOptions.value = apiPage.records;
        optionsLoaded.value = true;
      })
      .finally(() => {
        optionsRequest = null;
        loadingOptions.value = false;
      });
  }
  return optionsRequest;
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshApiPermissions() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    await loadOptions();
    if (requestId !== activeRefreshRequest.value) return;

    const requestedPage = page.value;
    const requestedPageSize = normalizePageSize();
    const nextPage = await listAdminInstitutionApiPermissions({
      keyword: keyword.value,
      institutionId: institutionId.value,
      apiId: apiId.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: requestedPage,
      pageSize: requestedPageSize,
    });
    if (requestId !== activeRefreshRequest.value) return;

    const lastPage = Math.max(1, Math.ceil(nextPage.total / Math.max(1, nextPage.pageSize)));
    if (nextPage.records.length === 0 && requestedPage > lastPage) {
      page.value = lastPage;
      await refreshApiPermissions();
      return;
    }

    permissionPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条接口授权`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    listError.value = errorMessage(error);
    if (!loaded.value) {
      permissionPage.value = null;
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
  await refreshApiPermissions();
}

function resetForm() {
  actionError.value = '';
  form.value = {
    id: null,
    institutionId: '',
    apiId: '',
    remark: '',
    enabled: true,
  };
}

function editPermission(row: AdminInstitutionApiPermissionRecord) {
  actionError.value = '';
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    apiId: row.apiId,
    remark: row.remark ?? '',
    enabled: row.enabled,
  };
}

async function savePermission() {
  if (saving.value) return;
  if (!form.value.institutionId || !form.value.apiId) {
    actionError.value = '机构和接口不能为空';
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    if (form.value.id) {
      await updateAdminInstitutionApiPermission(form.value.id, commandFromForm());
      emit('notice', 'success', '接口授权已更新');
    } else {
      await createAdminInstitutionApiPermission(commandFromForm());
      emit('notice', 'success', '接口授权已新增');
    }
    resetForm();
    await refreshApiPermissions();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function togglePermission(row: AdminInstitutionApiPermissionRecord) {
  if (saving.value) return;
  saving.value = true;
  actionError.value = '';
  try {
    await updateAdminInstitutionApiPermission(row.id, {
      remark: row.remark ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `${institutionText(row)} ${row.enabled ? '已停用' : '已启用'} ${row.apiCode}`);
    await refreshApiPermissions();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (loading.value || !hasPreviousPage.value) return;
  page.value -= 1;
  await refreshApiPermissions();
}

async function nextPage() {
  if (loading.value || !hasNextPage.value) return;
  page.value += 1;
  await refreshApiPermissions();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshApiPermissions();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshApiPermissions,
});
</script>

<template>
  <section class="permission-page">
    <AdminToolbar>
      <label class="permission-field permission-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="permission-input"
          :disabled="loading"
          placeholder="机构 / 接口 / 路径 / 备注"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="permission-field permission-field--institution">
        <span>机构</span>
        <select
          v-model="institutionId"
          class="permission-input"
          :disabled="loading || loadingOptions"
          @change="searchFirstPage"
        >
          <option value="">全部机构</option>
          <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
            {{ institutionText(row) }}
          </option>
        </select>
      </label>
      <label class="permission-field permission-field--api">
        <span>接口</span>
        <select
          v-model="apiId"
          class="permission-input"
          :disabled="loading || loadingOptions"
          @change="searchFirstPage"
        >
          <option value="">全部接口</option>
          <option v-for="row in apiOptions" :key="row.id" :value="row.id">
            {{ apiText(row) }}
          </option>
        </select>
      </label>
      <label class="permission-field permission-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="permission-input"
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
          @click="downloadPermissionCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="permission-stats" aria-label="接口授权统计">
      <article v-for="stat in stats" :key="stat.label" class="permission-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="permission-edit-panel">
      <template #title>{{ editing ? '编辑接口授权' : '新增接口授权' }}</template>
      <template #description>维护机构可以访问的接口和授权状态。</template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="saving || loadingOptions"
          @click="savePermission"
        >
          {{ saving ? '保存中' : editing ? '保存授权' : '新增授权' }}
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

      <div class="permission-form-grid">
        <label class="permission-field">
          <span>机构</span>
          <select
            v-model="form.institutionId"
            class="permission-input"
            :disabled="editing || loadingOptions || saving"
          >
            <option value="">{{ loadingOptions ? '加载机构中' : '请选择机构' }}</option>
            <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
              {{ institutionText(row) }}
            </option>
          </select>
        </label>
        <label class="permission-field">
          <span>接口</span>
          <select
            v-model="form.apiId"
            class="permission-input"
            :disabled="editing || loadingOptions || saving"
          >
            <option value="">{{ loadingOptions ? '加载接口中' : '请选择接口' }}</option>
            <option v-for="row in apiOptions" :key="row.id" :value="row.id">
              {{ apiText(row) }}
            </option>
          </select>
        </label>
        <label class="permission-field">
          <span>状态</span>
          <select v-model="form.enabled" class="permission-input" :disabled="saving">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label class="permission-field">
          <span>备注</span>
          <input
            v-model="form.remark"
            class="permission-input"
            :disabled="saving"
            placeholder="授权说明或生效范围"
          >
        </label>
      </div>
    </AdminPanel>

    <AdminPanel class="permission-list-panel">
      <template #title>接口授权列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按机构、接口和状态检索授权。' }}
      </template>
      <template #actions>
        <span class="permission-list-note">机构与接口名称为主值，编码作为次级信息。</span>
      </template>

      <AdminPageState v-if="listState === 'loading'" state="loading" message="正在查询接口授权。" />
      <AdminPageState v-else-if="listState === 'error'" state="error" :message="listError" />
      <AdminPageState v-else-if="listState === 'empty'" state="empty" message="没有相关接口授权。" />
      <template v-else>
        <p v-if="listError" class="error-line permission-list-error" role="alert">{{ listError }}</p>
        <AdminTableShell>
          <table class="permission-table">
            <thead>
              <tr>
                <th>机构</th>
                <th>接口</th>
                <th>方法</th>
                <th>路径</th>
                <th>状态</th>
                <th>备注</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="permission-primary-cell">
                    <strong>{{ displayValue(row.institutionName) }}</strong>
                    <small>{{ displayValue(row.institutionCode) }}</small>
                  </div>
                </td>
                <td>
                  <div class="permission-primary-cell">
                    <strong>{{ displayValue(row.apiName) }}</strong>
                    <small>{{ displayValue(row.apiCode) }}</small>
                  </div>
                </td>
                <td><code>{{ displayValue(row.requestMethod) }}</code></td>
                <td class="permission-path-cell"><code>{{ displayValue(row.requestPath) }}</code></td>
                <td><AdminStatusTag :enabled="row.enabled" /></td>
                <td class="permission-remark-cell">{{ displayValue(row.remark) }}</td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="permission-row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="editPermission(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="togglePermission(row)"
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
.permission-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.permission-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.permission-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.permission-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.permission-stat span,
.permission-list-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.permission-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.permission-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.permission-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.permission-field--keyword {
  flex: 1 1 260px;
}

.permission-field--institution,
.permission-field--api {
  flex: 1 1 220px;
}

.permission-field--status {
  flex: 0 0 140px;
}

.permission-input {
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

.permission-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.permission-list-error {
  margin: 0 0 12px;
}

.permission-table {
  min-width: 1180px;
}

.permission-table td {
  vertical-align: top;
}

.permission-primary-cell {
  display: grid;
  gap: 2px;
}

.permission-primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.permission-primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.permission-path-cell,
.permission-remark-cell {
  min-width: 180px;
  max-width: 280px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.permission-table code {
  color: #344054;
  font-size: 12px;
  line-height: 18px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.permission-row-actions {
  white-space: nowrap;
}

.permission-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.permission-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

@media (max-width: 980px) {
  .permission-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
