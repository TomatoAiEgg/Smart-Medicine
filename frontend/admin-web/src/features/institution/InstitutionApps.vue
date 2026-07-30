<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
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
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';

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
const loading = ref(false);
const loadingInstitutions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
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
const saveButtonLabel = computed(() => {
  if (saving.value) return '保存中';
  if (!editing.value) return '新增应用';
  return form.value.appSecret.trim() ? '保存并重置密钥' : '保存修改';
});

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

function enabledLabel(value: boolean) {
  return value ? '启用' : '停用';
}

function secretLabel(row: AdminInstitutionAppRecord) {
  return row.appSecretConfigured ? '已配置' : '未配置';
}

function enabledParam() {
  if (enabledFilter.value === 'true') return true;
  if (enabledFilter.value === 'false') return false;
  return undefined;
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
  downloadCsv(
    `机构应用列表-${new Date().toISOString().slice(0, 10)}.csv`,
    ['机构', 'AppKey', '签名类型', '密钥状态', '回调地址', '状态', '更新时间'],
    rows.value.map((row) => [
      institutionText(row),
      row.appKey,
      row.signType,
      secretLabel(row),
      row.callbackUrl,
      enabledLabel(row.enabled),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个应用`);
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

async function refreshInstitutionApps() {
  loading.value = true;
  errorLine.value = '';
  try {
    await loadInstitutionOptions();
    const nextPage = await listAdminInstitutionApps({
      keyword: keyword.value,
      institutionId: institutionId.value,
      enabled: enabledParam(),
      page: page.value,
      pageSize: pageSize.value,
    });
    appPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个应用`);
  } catch (error) {
    appPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshInstitutionApps();
}

function resetForm() {
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
  if (!form.value.institutionId || !form.value.appKey.trim()) {
    errorLine.value = '机构和 AppKey 不能为空';
    return;
  }
  if (!editing.value && !form.value.appSecret.trim()) {
    errorLine.value = '新增应用时密钥不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleApp(row: AdminInstitutionAppRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminInstitutionApp(row.id, {
      signType: row.signType,
      callbackUrl: row.callbackUrl ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `应用 ${row.appKey} 已${row.enabled ? '停用' : '启用'}`);
    await refreshInstitutionApps();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshInstitutionApps();
}

async function nextPage() {
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
  <section class="legacy-page institution-app-page">
    <ul class="legacy-search app-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="机构编码 / 名称 / AppKey / 回调地址"
          @keyup.enter="searchFirstPage"
        />
      </li>
      <li>
        机构：
        <select v-model="institutionId" class="legacy-input input-medium" @change="searchFirstPage">
          <option value="">全部</option>
          <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
            {{ optionText(row) }}
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadAppCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats app-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>应用总数</span>
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

    <div class="app-edit legacy-panel">
      <div class="app-form-grid">
        <label>
          机构
          <select v-model="form.institutionId" class="legacy-input" :disabled="editing || loadingInstitutions">
            <option value="">{{ loadingInstitutions ? '加载机构中' : '请选择机构' }}</option>
            <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
              {{ optionText(row) }}
            </option>
          </select>
        </label>
        <label>
          AppKey
          <input v-model="form.appKey" class="legacy-input" :disabled="editing" placeholder="his-demo-app" />
        </label>
        <label>
          AppSecret
          <input
            v-model="form.appSecret"
            class="legacy-input"
            type="password"
            :placeholder="editing ? '留空不修改' : '请输入密钥'"
          />
        </label>
        <label>
          签名类型
          <select v-model="form.signType" class="legacy-input">
            <option value="HMAC_SHA256">HMAC_SHA256</option>
          </select>
        </label>
        <label>
          回调地址
          <input v-model="form.callbackUrl" class="legacy-input" placeholder="https://example.com/callback" />
        </label>
        <label>
          状态
          <select v-model="form.enabled" class="legacy-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <div class="app-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveApp">
            {{ saveButtonLabel }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </div>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table app-table">
        <thead>
          <tr class="legacy-main-head">
            <th>机构</th>
            <th>AppKey</th>
            <th>签名类型</th>
            <th>密钥</th>
            <th>回调地址</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">正在查询应用配置</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">没有相关应用配置</td>
          </tr>
          <tr v-for="row in rows" :key="row.id" class="legacy-main-info">
            <td class="legacy-left">
              <strong>{{ institutionText(row) }}</strong>
            </td>
            <td><code>{{ rowValue(row.appKey) }}</code></td>
            <td>{{ rowValue(row.signType) }}</td>
            <td>{{ secretLabel(row) }}</td>
            <td class="legacy-left">{{ rowValue(row.callbackUrl) }}</td>
            <td>{{ enabledLabel(row.enabled) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-btn" type="button" :disabled="saving" @click="editApp(row)">编辑</button>
              <button class="legacy-btn" type="button" :disabled="saving" @click="toggleApp(row)">
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
    </div>
  </section>
</template>

<style scoped>
.app-search {
  row-gap: 10px;
}

.app-stats {
  margin-bottom: 10px;
}

.app-edit {
  margin-bottom: 10px;
  padding: 12px;
}

.app-form-grid {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(220px, 1.3fr) minmax(150px, 1fr) minmax(150px, 1fr) 140px minmax(220px, 1.3fr) 110px auto;
}

.app-form-grid label {
  color: #4b5563;
  display: grid;
  gap: 4px;
  font-size: 13px;
}

.app-actions {
  display: flex;
  gap: 8px;
}

.app-table {
  min-width: 1180px;
}

.app-table th,
.app-table td {
  min-width: 110px;
}

.app-table code {
  background: #f3f4f6;
  border-radius: 4px;
  color: #111827;
  display: inline-block;
  padding: 2px 6px;
}

@media (max-width: 1180px) {
  .app-form-grid {
    grid-template-columns: 1fr;
  }

  .app-actions {
    flex-wrap: wrap;
  }
}
</style>
