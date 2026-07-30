<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { createAdminInstitutionApi, listAdminInstitutionApis, updateAdminInstitutionApi } from '../../api/order';
import type {
  AdminInstitutionApiCommand,
  AdminInstitutionApiPage,
  AdminInstitutionApiRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { enabledBooleanParam, enabledText, displayValue, currentIsoDate, formatDate, formatNumber } from '../../domain/formatters';

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
const errorLine = ref('');
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

async function refreshInstitutionApis() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminInstitutionApis({
      keyword: keyword.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    apiPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个接口`);
  } catch (error) {
    apiPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshInstitutionApis();
}

function resetForm() {
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
  if (!form.value.apiCode.trim() || !form.value.apiName.trim() || !form.value.requestPath.trim()) {
    errorLine.value = '接口编码、名称和路径不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleApi(row: AdminInstitutionApiRecord) {
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshInstitutionApis();
}

async function nextPage() {
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
  <section class="legacy-page api-info-page">
    <ul class="legacy-search api-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="编码 / 名称 / 路径 / 描述"
          @keyup.enter="searchFirstPage"
        />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadApiCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats api-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>接口总数</span>
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

    <div class="api-edit legacy-panel">
      <div class="api-form-grid">
        <label>
          接口编码
          <input v-model="form.apiCode" class="legacy-input" :disabled="editing" placeholder="createOrder" />
        </label>
        <label>
          接口名称
          <input v-model="form.apiName" class="legacy-input" placeholder="机构下单" />
        </label>
        <label>
          方法
          <select v-model="form.requestMethod" class="legacy-input">
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PATCH">PATCH</option>
            <option value="PUT">PUT</option>
            <option value="DELETE">DELETE</option>
          </select>
        </label>
        <label>
          路径
          <input v-model="form.requestPath" class="legacy-input" placeholder="/api/institution/createOrder" />
        </label>
        <label>
          状态
          <select v-model="form.enabled" class="legacy-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label>
          描述
          <input v-model="form.description" class="legacy-input" placeholder="接口用途说明" />
        </label>
        <div class="api-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveApi">
            {{ saving ? '保存中' : editing ? '保存修改' : '新增接口' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </div>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table api-table">
        <thead>
          <tr class="legacy-main-head">
            <th>接口编码</th>
            <th>接口名称</th>
            <th>方法</th>
            <th>路径</th>
            <th>描述</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">正在查询接口</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">没有相关接口</td>
          </tr>
          <tr v-for="row in rows" :key="row.id" class="legacy-main-info">
            <td><code>{{ displayValue(row.apiCode) }}</code></td>
            <td class="legacy-left"><strong>{{ displayValue(row.apiName) }}</strong></td>
            <td>{{ displayValue(row.requestMethod) }}</td>
            <td class="legacy-left"><code>{{ displayValue(row.requestPath) }}</code></td>
            <td class="legacy-left">{{ displayValue(row.description) }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-btn" type="button" :disabled="saving" @click="editApi(row)">编辑</button>
              <button class="legacy-btn" type="button" :disabled="saving" @click="toggleApi(row)">
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
.api-search {
  row-gap: 10px;
}

.api-stats {
  margin-bottom: 10px;
}

.api-edit {
  margin-bottom: 10px;
  padding: 12px;
}

.api-form-grid {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(140px, 1fr) minmax(160px, 1fr) 100px minmax(240px, 1.4fr) 110px minmax(220px, 1.2fr) auto;
}

.api-form-grid label {
  color: #4b5563;
  display: grid;
  gap: 4px;
  font-size: 13px;
}

.api-actions {
  display: flex;
  gap: 8px;
}

.api-table {
  min-width: 1180px;
}

.api-table th,
.api-table td {
  min-width: 100px;
}

.api-table code {
  background: #f3f4f6;
  border-radius: 4px;
  color: #111827;
  display: inline-block;
  padding: 2px 6px;
}

@media (max-width: 1180px) {
  .api-form-grid {
    grid-template-columns: 1fr;
  }

  .api-actions {
    flex-wrap: wrap;
  }
}
</style>
