<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminInstitutionApp,
  listAdminInstitutionApps,
  listAdminInstitutions,
  updateAdminInstitutionApp,
} from '../../api/order';
import type {
  AdminInstitutionAppCommand,
  AdminInstitutionAppPage,
  AdminInstitutionAppRecord,
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

interface AppForm {
  id: string | null;
  institutionId: string;
  appKey: string;
  appSecret: string;
  signType: string;
  callbackUrl: string;
  enabled: boolean;
}

interface AppStat {
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
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const appPage = ref<AdminInstitutionAppPage | null>(null);
const institutionOptions = ref<AdminInstitutionRecord[]>([]);
const institutionOptionsLoaded = ref(false);
const loading = ref(false);
const loadingInstitutions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);
let institutionOptionsRequest: Promise<void> | null = null;
const form = ref<AppForm>({
  id: null,
  institutionId: '',
  appKey: '',
  appSecret: '',
  signType: 'HMAC_SHA256',
  callbackUrl: '',
  enabled: true,
});

const rows = computed(() => appPage.value?.records ?? []);
const total = computed(() => appPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);
const canExport = computed(() => !loading.value && rows.value.length > 0);
const rowActionsDisabled = computed(() => loading.value || saving.value);
const saveButtonLabel = computed(() => {
  if (saving.value) return '保存中';
  if (!editing.value) return '新增应用';
  return form.value.appSecret.trim() ? '保存并重置密钥' : '保存修改';
});
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && rows.value.length === 0) return 'loading';
  if (listError.value && rows.value.length === 0) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<AppStat[]>(() => [
  { label: '应用总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function secretLabel(row: AdminInstitutionAppRecord) {
  return row.appSecretConfigured ? '已配置' : '未配置';
}

function institutionText(row: AdminInstitutionAppRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function optionText(row: AdminInstitutionRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function commandFromForm(): AdminInstitutionAppCommand {
  return {
    institutionId: form.value.institutionId,
    appKey: form.value.appKey.trim(),
    appSecret: form.value.appSecret.trim(),
    signType: form.value.signType,
    callbackUrl: form.value.callbackUrl.trim(),
    enabled: form.value.enabled,
  };
}

function downloadAppCsv() {
  if (loading.value || rows.value.length === 0) return;
  downloadCsv(
    `机构应用列表-${currentIsoDate()}.csv`,
    ['机构', 'AppKey', '签名类型', '密钥状态', '回调地址', '状态', '更新时间'],
    rows.value.map((row) => [
      institutionText(row),
      row.appKey,
      row.signType,
      secretLabel(row),
      row.callbackUrl,
      enabledText(row.enabled),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个应用`);
}

function loadInstitutionOptions() {
  if (institutionOptionsLoaded.value) return Promise.resolve();
  if (!institutionOptionsRequest) {
    loadingInstitutions.value = true;
    institutionOptionsRequest = listAdminInstitutions({ page: 1, pageSize: 100 })
      .then((institutionPage) => {
        institutionOptions.value = institutionPage.records;
        institutionOptionsLoaded.value = true;
      })
      .finally(() => {
        institutionOptionsRequest = null;
        loadingInstitutions.value = false;
      });
  }
  return institutionOptionsRequest;
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshInstitutionApps() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    await loadInstitutionOptions();
    if (requestId !== activeRefreshRequest.value) return;

    const requestedPage = page.value;
    const requestedPageSize = normalizePageSize();
    const nextPage = await listAdminInstitutionApps({
      keyword: keyword.value,
      institutionId: institutionId.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: requestedPage,
      pageSize: requestedPageSize,
    });
    if (requestId !== activeRefreshRequest.value) return;

    const lastPage = Math.max(1, Math.ceil(nextPage.total / Math.max(1, nextPage.pageSize)));
    if (nextPage.records.length === 0 && requestedPage > lastPage) {
      page.value = lastPage;
      await refreshInstitutionApps();
      return;
    }

    appPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个应用`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    listError.value = errorMessage(error);
    if (!loaded.value) {
      appPage.value = null;
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
  await refreshInstitutionApps();
}

function resetForm() {
  actionError.value = '';
  form.value = {
    id: null,
    institutionId: '',
    appKey: '',
    appSecret: '',
    signType: 'HMAC_SHA256',
    callbackUrl: '',
    enabled: true,
  };
}

function editApp(row: AdminInstitutionAppRecord) {
  actionError.value = '';
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    appKey: row.appKey,
    appSecret: '',
    signType: row.signType,
    callbackUrl: row.callbackUrl ?? '',
    enabled: row.enabled,
  };
}

async function saveApp() {
  if (saving.value) return;
  if (!form.value.institutionId || !form.value.appKey.trim()) {
    actionError.value = '机构和 AppKey 不能为空';
    return;
  }
  if (!editing.value && !form.value.appSecret.trim()) {
    actionError.value = '新增应用时密钥不能为空';
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    if (form.value.id) {
      await updateAdminInstitutionApp(form.value.id, commandFromForm());
      emit('notice', 'success', `应用 ${form.value.appKey} 已更新`);
    } else {
      await createAdminInstitutionApp(commandFromForm());
      emit('notice', 'success', `应用 ${form.value.appKey} 已新增`);
    }
    resetForm();
    await refreshInstitutionApps();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleApp(row: AdminInstitutionAppRecord) {
  if (saving.value) return;
  saving.value = true;
  actionError.value = '';
  try {
    await updateAdminInstitutionApp(row.id, {
      signType: row.signType,
      callbackUrl: row.callbackUrl ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `应用 ${row.appKey} 已${row.enabled ? '停用' : '启用'}`);
    await refreshInstitutionApps();
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
  await refreshInstitutionApps();
}

async function nextPage() {
  if (loading.value) return;
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshInstitutionApps();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshInstitutionApps();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshInstitutionApps,
});
</script>

<template>
  <section class="institution-app-page">
    <AdminToolbar>
      <label class="app-field app-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="app-input"
          :disabled="loading"
          placeholder="机构编码 / 名称 / AppKey / 回调地址"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="app-field app-field--institution">
        <span>机构</span>
        <select
          v-model="institutionId"
          class="app-input"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <option value="">全部</option>
          <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
            {{ optionText(row) }}
          </option>
        </select>
      </label>
      <label class="app-field app-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="app-input"
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
          @click="downloadAppCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="app-stats" aria-label="应用统计">
      <article v-for="stat in stats" :key="stat.label" class="app-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="app-edit-panel">
      <template #title>{{ editing ? '编辑应用' : '新增应用' }}</template>
      <template #description>维护机构应用标识、签名方式、回调地址与启用状态。</template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="saving"
          @click="saveApp"
        >
          {{ saveButtonLabel }}
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

      <div class="app-form-grid">
        <label class="app-field">
          <span>机构</span>
          <select
            v-model="form.institutionId"
            class="app-input"
            :disabled="editing || loadingInstitutions"
          >
            <option value="">{{ loadingInstitutions ? '加载机构中' : '请选择机构' }}</option>
            <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
              {{ optionText(row) }}
            </option>
          </select>
        </label>
        <label class="app-field">
          <span>AppKey</span>
          <input
            v-model="form.appKey"
            class="app-input"
            :disabled="editing"
            placeholder="his-demo-app"
          >
        </label>
        <label class="app-field">
          <span>AppSecret</span>
          <input
            v-model="form.appSecret"
            class="app-input"
            type="password"
            :placeholder="editing ? '留空不修改' : '请输入密钥'"
          >
        </label>
        <label class="app-field">
          <span>签名类型</span>
          <select v-model="form.signType" class="app-input">
            <option value="HMAC_SHA256">HMAC_SHA256</option>
          </select>
        </label>
        <label class="app-field app-field--callback">
          <span>回调地址</span>
          <input
            v-model="form.callbackUrl"
            class="app-input"
            placeholder="https://example.com/callback"
          >
        </label>
        <label class="app-field">
          <span>状态</span>
          <select v-model="form.enabled" class="app-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
      </div>
    </AdminPanel>

    <AdminPanel class="app-list-panel">
      <template #title>应用列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索机构应用。' }}
      </template>
      <template #actions>
        <span class="app-list-note">仅展示密钥配置状态，不显示密钥内容。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询应用配置。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关应用配置。"
      />
      <template v-else>
        <p v-if="listError" class="error-line app-list-error" role="alert">{{ listError }}</p>
        <AdminTableShell>
          <table class="app-table">
            <thead>
              <tr>
                <th>机构</th>
                <th>AppKey</th>
                <th>签名类型</th>
                <th>密钥配置状态</th>
                <th>回调地址</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="app-primary-cell">
                    <strong>{{ displayValue(row.institutionName) }}</strong>
                    <small>{{ displayValue(row.institutionCode) }}</small>
                  </div>
                </td>
                <td><code class="app-key">{{ displayValue(row.appKey) }}</code></td>
                <td>{{ displayValue(row.signType) }}</td>
                <td>{{ secretLabel(row) }}</td>
                <td><div class="app-callback">{{ displayValue(row.callbackUrl) }}</div></td>
                <td><AdminStatusTag :enabled="row.enabled" /></td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="app-row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="editApp(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="toggleApp(row)"
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
.institution-app-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.app-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.app-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.app-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.app-stat span,
.app-list-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.app-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.app-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.app-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.app-field--keyword {
  flex: 1 1 300px;
}

.app-field--institution {
  flex: 1 1 260px;
}

.app-field--status {
  flex: 0 0 150px;
}

.app-field--callback {
  grid-column: span 2;
}

.app-input {
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

.app-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.app-table {
  min-width: 1180px;
}

.app-primary-cell {
  display: grid;
  gap: 2px;
  min-width: 180px;
}

.app-primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.app-primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.app-key {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  color: #111827;
  background: #f3f4f6;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 18px;
}

.app-callback {
  min-width: 240px;
  max-width: 380px;
  overflow-wrap: break-word;
  white-space: normal;
  word-break: break-word;
}

.app-row-actions {
  white-space: nowrap;
}

.app-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.app-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 20px;
}

.app-list-error {
  margin-bottom: 12px;
}

@media (max-width: 980px) {
  .app-form-grid {
    grid-template-columns: 1fr;
  }

  .app-field--callback {
    grid-column: auto;
  }
}
</style>
