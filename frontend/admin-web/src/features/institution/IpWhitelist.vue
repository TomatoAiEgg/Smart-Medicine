<script setup lang="ts">
import { computed, ref, watch } from 'vue';
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
  labelFromMap,
} from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface WhitelistForm {
  id: string | null;
  institutionId: string;
  ipRange: string;
  enabled: boolean;
}

interface WhitelistStat {
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
const ipRange = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const whitelistPage = ref<AdminInstitutionIpWhitelistPage | null>(null);
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
const canExport = computed(() => !loading.value && rows.value.length > 0);
const rowActionsDisabled = computed(() => loading.value || saving.value);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && rows.value.length === 0) return 'loading';
  if (listError.value && rows.value.length === 0) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<WhitelistStat[]>(() => [
  { label: '白名单总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function institutionText(row: AdminInstitutionIpWhitelistRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function optionText(row: AdminInstitutionRecord) {
  return `${row.institutionName}（${row.institutionCode}）`;
}

function institutionTypeLabel(value: string) {
  return labelFromMap(value, {
    HOSPITAL: '医院',
    PHARMACY: '药房',
    PLATFORM: '平台',
  }, value);
}

function commandFromForm(): AdminInstitutionIpWhitelistCommand {
  return {
    institutionId: form.value.institutionId,
    ipRange: form.value.ipRange.trim(),
    enabled: form.value.enabled,
  };
}

function downloadWhitelistCsv() {
  if (loading.value || rows.value.length === 0) return;
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

async function refreshIpWhitelists() {
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
    const nextPage = await listAdminInstitutionIpWhitelists({
      keyword: keyword.value,
      institutionId: institutionId.value,
      ipRange: ipRange.value,
      enabled: enabledBooleanParam(enabledFilter.value),
      page: requestedPage,
      pageSize: requestedPageSize,
    });
    if (requestId !== activeRefreshRequest.value) return;

    const lastPage = Math.max(1, Math.ceil(nextPage.total / Math.max(1, nextPage.pageSize)));
    if (nextPage.records.length === 0 && requestedPage > lastPage) {
      page.value = lastPage;
      await refreshIpWhitelists();
      return;
    }

    whitelistPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条白名单`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    listError.value = errorMessage(error);
    if (!loaded.value) {
      whitelistPage.value = null;
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
  await refreshIpWhitelists();
}

function resetForm() {
  actionError.value = '';
  form.value = {
    id: null,
    institutionId: '',
    ipRange: '',
    enabled: true,
  };
}

function editWhitelist(row: AdminInstitutionIpWhitelistRecord) {
  actionError.value = '';
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    ipRange: row.ipRange,
    enabled: row.enabled,
  };
}

async function saveWhitelist() {
  if (saving.value) return;
  if (!form.value.institutionId || !form.value.ipRange.trim()) {
    actionError.value = '机构和 IP 段不能为空';
    return;
  }
  saving.value = true;
  actionError.value = '';
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
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleWhitelist(row: AdminInstitutionIpWhitelistRecord) {
  if (saving.value) return;
  saving.value = true;
  actionError.value = '';
  try {
    await updateAdminInstitutionIpWhitelist(row.id, {
      ipRange: row.ipRange,
      enabled: !row.enabled,
    });
    emit('notice', 'success', `白名单 ${row.ipRange} 已${row.enabled ? '停用' : '启用'}`);
    await refreshIpWhitelists();
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
  await refreshIpWhitelists();
}

async function nextPage() {
  if (loading.value) return;
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
  <section class="ip-whitelist-page">
    <AdminToolbar>
      <label class="whitelist-field whitelist-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="whitelist-input"
          :disabled="loading"
          placeholder="机构编码 / 名称 / IP 段"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="whitelist-field whitelist-field--institution">
        <span>机构</span>
        <select
          v-model="institutionId"
          class="whitelist-input"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <option value="">全部</option>
          <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
            {{ optionText(row) }}
          </option>
        </select>
      </label>
      <label class="whitelist-field whitelist-field--ip">
        <span>IP 段</span>
        <input
          v-model="ipRange"
          class="whitelist-input"
          :disabled="loading"
          placeholder="10.0.0.0/24"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="whitelist-field whitelist-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="whitelist-input"
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
          @click="downloadWhitelistCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="whitelist-stats" aria-label="白名单统计">
      <article v-for="stat in stats" :key="stat.label" class="whitelist-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="whitelist-edit-panel">
      <template #title>{{ editing ? '编辑 IP 白名单' : '新增 IP 白名单' }}</template>
      <template #description>维护机构访问来源、IP 段和启用状态。</template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="saving"
          @click="saveWhitelist"
        >
          {{ saving ? '保存中' : editing ? '保存修改' : '新增白名单' }}
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

      <div class="whitelist-form-grid">
        <label class="whitelist-field">
          <span>机构</span>
          <select
            v-model="form.institutionId"
            class="whitelist-input"
            :disabled="editing || loadingInstitutions"
          >
            <option value="">{{ loadingInstitutions ? '加载机构中' : '请选择机构' }}</option>
            <option v-for="row in institutionOptions" :key="row.id" :value="row.id">
              {{ optionText(row) }}
            </option>
          </select>
        </label>
        <label class="whitelist-field whitelist-field--form-ip">
          <span>IP 段</span>
          <input
            v-model="form.ipRange"
            class="whitelist-input"
            placeholder="单 IP / CIDR / 范围说明"
          >
        </label>
        <label class="whitelist-field">
          <span>状态</span>
          <select v-model="form.enabled" class="whitelist-input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
      </div>
    </AdminPanel>

    <AdminPanel class="whitelist-list-panel">
      <template #title>IP 白名单列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索机构 IP 白名单。' }}
      </template>
      <template #actions>
        <span class="whitelist-list-note">机构名称为主信息，编码显示在名称下方。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询 IP 白名单。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关 IP 白名单。"
      />
      <template v-else>
        <p v-if="listError" class="error-line whitelist-list-error" role="alert">{{ listError }}</p>
        <AdminTableShell>
          <table class="whitelist-table">
            <thead>
              <tr>
                <th>机构</th>
                <th>机构类型</th>
                <th>IP 段</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="whitelist-primary-cell">
                    <strong>{{ displayValue(row.institutionName) }}</strong>
                    <small>{{ displayValue(row.institutionCode) }}</small>
                  </div>
                </td>
                <td>{{ institutionTypeLabel(row.institutionType) }}</td>
                <td><code class="whitelist-ip-range">{{ displayValue(row.ipRange) }}</code></td>
                <td><AdminStatusTag :enabled="row.enabled" /></td>
                <td>{{ formatDate(row.createdAt) }}</td>
                <td class="whitelist-row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="editWhitelist(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="toggleWhitelist(row)"
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
.ip-whitelist-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.whitelist-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.whitelist-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.whitelist-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.whitelist-stat span,
.whitelist-list-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.whitelist-form-grid {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(280px, 1.4fr) minmax(120px, 0.5fr);
  gap: 12px;
  min-width: 0;
}

.whitelist-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.whitelist-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.whitelist-field--keyword {
  flex: 1 1 260px;
}

.whitelist-field--institution {
  flex: 1 1 250px;
}

.whitelist-field--ip {
  flex: 0 1 190px;
}

.whitelist-field--status {
  flex: 0 0 140px;
}

.whitelist-input {
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

.whitelist-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.whitelist-table {
  min-width: 980px;
}

.whitelist-primary-cell {
  display: grid;
  gap: 2px;
  min-width: 180px;
}

.whitelist-primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.whitelist-primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.whitelist-ip-range {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  color: #111827;
  background: #f3f4f6;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 18px;
}

.whitelist-row-actions {
  white-space: nowrap;
}

.whitelist-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.whitelist-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 20px;
}

.whitelist-list-error {
  margin-bottom: 12px;
}

@media (max-width: 980px) {
  .whitelist-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
