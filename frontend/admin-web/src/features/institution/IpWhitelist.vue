<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminInstitutionIpWhitelist,
  listAdminInstitutionIpWhitelists,
  listAdminInstitutions,
  updateAdminInstitutionIpWhitelist,
} from '../../api/order';
import type {
  AdminInstitutionIpWhitelistCommand,
  AdminInstitutionIpWhitelistPage,
  AdminInstitutionIpWhitelistRecord,
  AdminInstitutionRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { enabledBooleanParam, enabledText, displayValue, currentIsoDate, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface WhitelistForm {
  id: string | null;
  institutionId: string;
  ipRange: string;
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
const ipRange = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const whitelistPage = ref<AdminInstitutionIpWhitelistPage | null>(null);
const institutionOptions = ref<AdminInstitutionRecord[]>([]);
const loading = ref(false);
const loadingInstitutions = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref<WhitelistForm>({
  id: null,
  institutionId: '',
  ipRange: '',
  enabled: true,
});

const rows = computed(() => whitelistPage.value?.records ?? []);
const total = computed(() => whitelistPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);

function institutionText(row: AdminInstitutionIpWhitelistRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function optionText(row: AdminInstitutionRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function commandFromForm(): AdminInstitutionIpWhitelistCommand {
  return {
    institutionId: form.value.institutionId,
    ipRange: form.value.ipRange.trim(),
    enabled: form.value.enabled,
  };
}

function downloadWhitelistCsv() {
  downloadCsv(
    `机构IP白名单-${currentIsoDate()}.csv`,
    ['机构', '机构类型', 'IP段', '状态', '创建时间'],
    rows.value.map((row) => [
      institutionText(row),
      row.institutionType,
      row.ipRange,
      enabledText(row.enabled),
      formatDate(row.createdAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条白名单`);
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

async function refreshIpWhitelists() {
  loading.value = true;
  errorLine.value = '';
  try {
    await loadInstitutionOptions();
    const nextPage = await listAdminInstitutionIpWhitelists({
      keyword: keyword.value,
      institutionId: institutionId.value,
      ipRange: ipRange.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    whitelistPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条白名单`);
  } catch (error) {
    whitelistPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshIpWhitelists();
}

function resetForm() {
  form.value = {
    id: null,
    institutionId: '',
    ipRange: '',
    enabled: true,
  };
}

function editWhitelist(row: AdminInstitutionIpWhitelistRecord) {
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    ipRange: row.ipRange,
    enabled: row.enabled,
  };
}

async function saveWhitelist() {
  if (!form.value.institutionId || !form.value.ipRange.trim()) {
    errorLine.value = '机构和 IP 段不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    if (form.value.id) {
      await updateAdminInstitutionIpWhitelist(form.value.id, commandFromForm());
      emit('notice', 'success', `白名单 ${form.value.ipRange} 已更新`);
    } else {
      await createAdminInstitutionIpWhitelist(commandFromForm());
      emit('notice', 'success', `白名单 ${form.value.ipRange} 已新增`);
    }
    resetForm();
    await refreshIpWhitelists();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleWhitelist(row: AdminInstitutionIpWhitelistRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminInstitutionIpWhitelist(row.id, {
      ipRange: row.ipRange,
      enabled: !row.enabled,
    });
    emit('notice', 'success', `白名单 ${row.ipRange} 已${row.enabled ? '停用' : '启用'}`);
    await refreshIpWhitelists();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshIpWhitelists();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshIpWhitelists();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshIpWhitelists();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshIpWhitelists,
});
</script>

<template>
  <section class="legacy-page ip-whitelist-page">
    <ul class="legacy-search whitelist-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="机构编码 / 名称 / IP 段"
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
        IP 段：
        <input v-model="ipRange" class="legacy-input input-small" placeholder="10.0.0.0/24" @keyup.enter="searchFirstPage" />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadWhitelistCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats whitelist-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>白名单总数</span>
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

    <div class="whitelist-edit legacy-panel">
      <div class="whitelist-form-grid">
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
          IP 段
          <input v-model="form.ipRange" class="legacy-input" placeholder="单 IP / CIDR / 范围说明" />
        </label>
        <label>
          状态
          <select v-model="form.enabled" class="legacy-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <div class="whitelist-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveWhitelist">
            {{ saving ? '保存中' : editing ? '保存修改' : '新增白名单' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </div>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table whitelist-table">
        <thead>
          <tr class="legacy-main-head">
            <th>机构</th>
            <th>机构类型</th>
            <th>IP 段</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="6" class="legacy-empty">正在查询白名单</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="6" class="legacy-empty">没有相关白名单</td>
          </tr>
          <tr v-for="row in rows" :key="row.id" class="legacy-main-info">
            <td class="legacy-left">
              <strong>{{ institutionText(row) }}</strong>
            </td>
            <td>{{ displayValue(row.institutionType) }}</td>
            <td><code>{{ displayValue(row.ipRange) }}</code></td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>
              <button class="legacy-btn" type="button" :disabled="saving" @click="editWhitelist(row)">编辑</button>
              <button class="legacy-btn" type="button" :disabled="saving" @click="toggleWhitelist(row)">
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
.whitelist-search {
  row-gap: 10px;
}

.whitelist-stats {
  margin-bottom: 10px;
}

.whitelist-edit {
  margin-bottom: 10px;
  padding: 12px;
}

.whitelist-form-grid {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(240px, 1.4fr) minmax(220px, 1.2fr) 110px auto;
}

.whitelist-form-grid label {
  color: #4b5563;
  display: grid;
  gap: 4px;
  font-size: 13px;
}

.whitelist-actions {
  display: flex;
  gap: 8px;
}

.whitelist-table {
  min-width: 980px;
}

.whitelist-table th,
.whitelist-table td {
  min-width: 110px;
}

.whitelist-table code {
  background: #f3f4f6;
  border-radius: 4px;
  color: #111827;
  display: inline-block;
  padding: 2px 6px;
}

@media (max-width: 960px) {
  .whitelist-form-grid {
    grid-template-columns: 1fr;
  }

  .whitelist-actions {
    flex-wrap: wrap;
  }
}
</style>
