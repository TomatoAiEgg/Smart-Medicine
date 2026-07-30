<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
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
import { downloadCsv } from '../../domain/csv';
import { currentIsoDate, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface PermissionForm {
  id: string | null;
  institutionId: string;
  apiId: string;
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
const apiId = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const permissionPage = ref<AdminInstitutionApiPermissionPage | null>(null);
const institutionOptions = ref<AdminInstitutionRecord[]>([]);
const apiOptions = ref<AdminInstitutionApiRecord[]>([]);
const loading = ref(false);
const loadingOptions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
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
  downloadCsv(
    `机构接口授权-${currentIsoDate()}.csv`,
    ['机构', '接口', '方法', '路径', '状态', '备注', '更新时间'],
    rows.value.map((row) => [
      institutionText(row),
      apiText(row),
      row.requestMethod,
      row.requestPath,
      enabledLabel(row.enabled),
      row.remark,
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条接口授权`);
}

async function loadOptions() {
  if (loadingOptions.value || (institutionOptions.value.length > 0 && apiOptions.value.length > 0)) return;
  loadingOptions.value = true;
  try {
    const [institutionPage, apiPage] = await Promise.all([
      listAdminInstitutions({ page: 1, pageSize: 100 }),
      listAdminInstitutionApis({ page: 1, pageSize: 100 }),
    ]);
    institutionOptions.value = institutionPage.records;
    apiOptions.value = apiPage.records;
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    loadingOptions.value = false;
  }
}

async function refreshApiPermissions() {
  loading.value = true;
  errorLine.value = '';
  try {
    await loadOptions();
    const nextPage = await listAdminInstitutionApiPermissions({
      keyword: keyword.value,
      institutionId: institutionId.value,
      apiId: apiId.value,
      enabled: enabledParam(),
      page: page.value,
      pageSize: pageSize.value,
    });
    permissionPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条接口授权`);
  } catch (error) {
    permissionPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshApiPermissions();
}

function resetForm() {
  form.value = {
    id: null,
    institutionId: '',
    apiId: '',
    remark: '',
    enabled: true,
  };
}

function editPermission(row: AdminInstitutionApiPermissionRecord) {
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    apiId: row.apiId,
    remark: row.remark ?? '',
    enabled: row.enabled,
  };
}

async function savePermission() {
  if (!form.value.institutionId || !form.value.apiId) {
    errorLine.value = '机构和接口不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function togglePermission(row: AdminInstitutionApiPermissionRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminInstitutionApiPermission(row.id, {
      remark: row.remark ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `${institutionText(row)} ${row.enabled ? '已停用' : '已启用'} ${row.apiCode}`);
    await refreshApiPermissions();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshApiPermissions();
}

async function nextPage() {
  if (!hasNextPage.value) return;
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
  <section class="legacy-page api-permission-page">
    <ul class="legacy-search permission-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="机构 / 接口 / 路径 / 备注"
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
        接口：
        <select v-model="apiId" class="legacy-input input-medium" @change="searchFirstPage">
          <option value="">全部</option>
          <option v-for="row in apiOptions" :key="row.id" :value="row.id">
            {{ apiText(row) }}
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadPermissionCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats permission-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>授权总数</span>
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

    <div class="permission-edit legacy-panel">
      <div class="permission-form-grid">
        <label>
          机构
          <select v-model="form.institutionId" class="legacy-input" :disabled="editing || loadingOptions">
            <option value="">{{ loadingOptions ? '加载机构中' : '请选择机构' }}</option>
            <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
              {{ institutionText(row) }}
            </option>
          </select>
        </label>
        <label>
          接口
          <select v-model="form.apiId" class="legacy-input" :disabled="editing || loadingOptions">
            <option value="">{{ loadingOptions ? '加载接口中' : '请选择接口' }}</option>
            <option v-for="row in apiOptions" :key="row.id" :value="row.id">
              {{ apiText(row) }}
            </option>
          </select>
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
          <input v-model="form.remark" class="legacy-input" placeholder="授权说明或生效范围" />
        </label>
      </div>
      <div class="permission-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="savePermission">
          {{ saving ? '保存中' : editing ? '保存授权' : '新增授权' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table permission-table">
        <thead>
          <tr>
            <th>机构</th>
            <th>接口</th>
            <th>方法</th>
            <th>状态</th>
            <th>备注</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7">正在加载接口授权...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="7">暂无接口授权</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>
              <strong>{{ row.institutionName }}</strong>
              <small>{{ row.institutionCode }}</small>
            </td>
            <td>
              <strong>{{ row.apiName }}</strong>
              <small>{{ row.apiCode }} · {{ row.requestPath }}</small>
            </td>
            <td>{{ row.requestMethod }}</td>
            <td>
              <span class="legacy-status" :class="row.enabled ? 'status-success' : 'status-muted'">
                {{ enabledLabel(row.enabled) }}
              </span>
            </td>
            <td>{{ rowValue(row.remark) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-link" type="button" :disabled="saving" @click="editPermission(row)">编辑</button>
              <button class="legacy-link" type="button" :disabled="saving" @click="togglePermission(row)">
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
.api-permission-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.permission-search,
.permission-stats {
  margin: 0;
}

.permission-edit {
  padding: 14px;
}

.permission-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.permission-form-grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.permission-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.permission-table th:nth-child(1),
.permission-table th:nth-child(2) {
  min-width: 190px;
}

.permission-table td {
  vertical-align: top;
}

.permission-table strong,
.permission-table small {
  display: block;
}

.permission-table small {
  margin-top: 3px;
  color: #6b7280;
}

@media (max-width: 1100px) {
  .permission-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .permission-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
