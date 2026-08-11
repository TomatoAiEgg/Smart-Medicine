<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { createAdminInstitution, listAdminInstitutions, updateAdminInstitution } from '../../api/order';
import type { AdminInstitutionCommand, AdminInstitutionPage, AdminInstitutionRecord } from '../../api/types';
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
  formatDate,
  formatNumber,
  labelFromMap,
} from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

interface InstitutionForm {
  id: string | null;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  status: string;
  storageType: string;
}

interface InstitutionStat {
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
const status = ref('');
const institutionType = ref('');
const page = ref(1);
const pageSize = ref(20);
const institutionPage = ref<AdminInstitutionPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);
const form = ref<InstitutionForm>({
  id: null,
  institutionCode: '',
  institutionName: '',
  institutionType: 'HOSPITAL',
  status: 'ENABLED',
  storageType: '',
});

const rows = computed(() => institutionPage.value?.records ?? []);
const total = computed(() => institutionPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.status === 'ENABLED').length);
const disabledCount = computed(() => rows.value.filter((row) => row.status !== 'ENABLED').length);
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
const stats = computed<InstitutionStat[]>(() => [
  { label: '机构总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function statusLabel(value: string) {
  return labelFromMap(value, {
    ENABLED: '启用',
    DISABLED: '停用',
  }, value);
}

function typeLabel(value: string) {
  return labelFromMap(value, {
    HOSPITAL: '医院',
    PHARMACY: '药房',
    PLATFORM: '平台',
  }, value);
}

function commandFromForm(): AdminInstitutionCommand {
  return {
    institutionCode: form.value.institutionCode.trim(),
    institutionName: form.value.institutionName.trim(),
    institutionType: form.value.institutionType,
    status: form.value.status,
    storageType: form.value.storageType.trim(),
  };
}

function downloadInstitutionCsv() {
  if (loading.value || rows.value.length === 0) return;
  downloadCsv(
    `机构列表-${currentIsoDate()}.csv`,
    ['机构编码', '机构名称', '类型', '状态', '煎煮中心', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.institutionCode,
      row.institutionName,
      typeLabel(row.institutionType),
      statusLabel(row.status),
      row.storageType,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个机构`);
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshInstitutions() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminInstitutions({
      keyword: keyword.value,
      status: status.value,
      institutionType: institutionType.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    if (requestId !== activeRefreshRequest.value) return;
    const lastPage = Math.max(1, Math.ceil(nextPage.total / nextPage.pageSize));
    if (nextPage.records.length === 0 && page.value > lastPage) {
      page.value = lastPage;
      await refreshInstitutions();
      return;
    }
    institutionPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个机构`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    listError.value = errorMessage(error);
    if (!loaded.value) {
      institutionPage.value = null;
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
  await refreshInstitutions();
}

function resetForm() {
  actionError.value = '';
  form.value = {
    id: null,
    institutionCode: '',
    institutionName: '',
    institutionType: 'HOSPITAL',
    status: 'ENABLED',
    storageType: '',
  };
}

function editInstitution(row: AdminInstitutionRecord) {
  actionError.value = '';
  form.value = {
    id: row.id,
    institutionCode: row.institutionCode,
    institutionName: row.institutionName,
    institutionType: row.institutionType,
    status: row.status,
    storageType: row.storageType ?? '',
  };
}

async function saveInstitution() {
  if (saving.value) return;
  if (!form.value.institutionCode.trim() || !form.value.institutionName.trim()) {
    actionError.value = '机构编码和机构名称不能为空';
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    if (form.value.id) {
      await updateAdminInstitution(form.value.id, commandFromForm());
      emit('notice', 'success', `机构 ${form.value.institutionName} 已更新`);
    } else {
      await createAdminInstitution(commandFromForm());
      emit('notice', 'success', `机构 ${form.value.institutionName} 已新增`);
    }
    resetForm();
    await refreshInstitutions();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleInstitution(row: AdminInstitutionRecord) {
  if (saving.value) return;
  saving.value = true;
  actionError.value = '';
  try {
    await updateAdminInstitution(row.id, {
      institutionName: row.institutionName,
      institutionType: row.institutionType,
      status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
      storageType: row.storageType ?? '',
    });
    emit('notice', 'success', `机构 ${row.institutionName} 已${row.status === 'ENABLED' ? '停用' : '启用'}`);
    await refreshInstitutions();
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
  await refreshInstitutions();
}

async function nextPage() {
  if (loading.value) return;
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshInstitutions();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshInstitutions();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshInstitutions,
});
</script>

<template>
  <section class="institution-page">
    <AdminToolbar>
      <label class="institution-field institution-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="institution-input"
          :disabled="loading"
          placeholder="机构编码 / 名称 / 煎煮中心"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="institution-field institution-field--status">
        <span>状态</span>
        <select
          v-model="status"
          class="institution-input"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <option value="">全部</option>
          <option value="ENABLED">启用</option>
          <option value="DISABLED">停用</option>
        </select>
      </label>
      <label class="institution-field institution-field--type">
        <span>类型</span>
        <select
          v-model="institutionType"
          class="institution-input"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <option value="">全部</option>
          <option value="HOSPITAL">医院</option>
          <option value="PHARMACY">药房</option>
          <option value="PLATFORM">平台</option>
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
          @click="downloadInstitutionCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="institution-stats" aria-label="机构统计">
      <article v-for="stat in stats" :key="stat.label" class="institution-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="institution-edit-panel">
      <template #title>{{ editing ? '编辑机构' : '新增机构' }}</template>
      <template #description>维护机构名称、类型、状态与煎煮中心。</template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="saving"
          @click="saveInstitution"
        >
          {{ saving ? '保存中' : editing ? '保存修改' : '新增机构' }}
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

      <div class="institution-form-grid">
        <label class="institution-field">
          <span>机构编码</span>
          <input
            v-model="form.institutionCode"
            class="institution-input"
            :disabled="editing"
            placeholder="hospital-code"
          >
        </label>
        <label class="institution-field">
          <span>机构名称</span>
          <input
            v-model="form.institutionName"
            class="institution-input"
            placeholder="机构名称"
          >
        </label>
        <label class="institution-field">
          <span>类型</span>
          <select v-model="form.institutionType" class="institution-input">
            <option value="HOSPITAL">医院</option>
            <option value="PHARMACY">药房</option>
            <option value="PLATFORM">平台</option>
          </select>
        </label>
        <label class="institution-field">
          <span>状态</span>
          <select v-model="form.status" class="institution-input">
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <label class="institution-field">
          <span>煎煮中心</span>
          <input
            v-model="form.storageType"
            class="institution-input"
            placeholder="中心/仓储标识"
          >
        </label>
      </div>
    </AdminPanel>

    <AdminPanel class="institution-list-panel">
      <template #title>机构列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索机构列表。' }}
      </template>
      <template #actions>
        <span class="institution-list-note">机构名称为主信息，编码显示在名称下方。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询机构。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关机构。"
      />
      <template v-else>
        <p v-if="listError" class="error-line institution-list-error" role="alert">{{ listError }}</p>
        <AdminTableShell>
          <table class="institution-table">
            <thead>
              <tr>
                <th>机构</th>
                <th>类型</th>
                <th>状态</th>
                <th>煎煮中心</th>
                <th>创建时间</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="institution-primary-cell">
                    <strong>{{ displayValue(row.institutionName) }}</strong>
                    <small>{{ displayValue(row.institutionCode) }}</small>
                  </div>
                </td>
                <td>{{ typeLabel(row.institutionType) }}</td>
                <td>
                  <AdminStatusTag :enabled="row.status === 'ENABLED'" />
                </td>
                <td>{{ displayValue(row.storageType) }}</td>
                <td>{{ formatDate(row.createdAt) }}</td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="institution-row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="editInstitution(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="toggleInstitution(row)"
                  >
                    {{ row.status === 'ENABLED' ? '停用' : '启用' }}
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
.institution-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.institution-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.institution-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.institution-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.institution-stat span,
.institution-list-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.institution-form-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.institution-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.institution-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.institution-field--keyword {
  flex: 1 1 300px;
}

.institution-field--status,
.institution-field--type {
  flex: 0 0 150px;
}

.institution-input {
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

.institution-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.institution-table {
  min-width: 1040px;
}

.institution-primary-cell {
  display: grid;
  gap: 2px;
  min-width: 180px;
}

.institution-primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.institution-primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.institution-row-actions {
  white-space: nowrap;
}

.institution-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.institution-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 20px;
}

.institution-list-error {
  margin-bottom: 12px;
}

@media (max-width: 980px) {
  .institution-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
