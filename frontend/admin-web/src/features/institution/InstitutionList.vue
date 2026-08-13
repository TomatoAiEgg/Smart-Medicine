<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { createAdminInstitution, listAdminInstitutions, updateAdminInstitution } from '../../api/order';
import type { AdminInstitutionCommand, AdminInstitutionPage, AdminInstitutionRecord } from '../../api/types';
import AdminDrawerForm from '../../components/admin/AdminDrawerForm.vue';
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
const editorOpen = ref(false);
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
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);
const canExport = computed(() => !loading.value && rows.value.length > 0);
const rowActionsDisabled = computed(() => loading.value || saving.value);
const formEnabled = computed({
  get: () => form.value.status === 'ENABLED',
  set: (enabled: boolean) => {
    form.value.status = enabled ? 'ENABLED' : 'DISABLED';
  },
});
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (listError.value && rows.value.length === 0) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
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
    ...(form.value.id ? {} : { institutionCode: form.value.institutionCode.trim() }),
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

function openCreateForm() {
  if (saving.value) return;
  resetForm();
  editorOpen.value = true;
}

function openEditForm(row: AdminInstitutionRecord) {
  if (saving.value) return;
  actionError.value = '';
  form.value = {
    id: row.id,
    institutionCode: row.institutionCode,
    institutionName: row.institutionName,
    institutionType: row.institutionType,
    status: row.status,
    storageType: row.storageType ?? '',
  };
  editorOpen.value = true;
}

function closeEditor() {
  editorOpen.value = false;
  resetForm();
}

function handleEditorOpenChange(open: boolean) {
  if (open) {
    editorOpen.value = true;
    return;
  }
  closeEditor();
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
    closeEditor();
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
        <t-input
          v-model="keyword"
          name="institution-keyword"
          size="small"
          clearable
          :disabled="loading"
          placeholder="机构编码 / 名称 / 煎煮中心"
          @enter="searchFirstPage"
        />
      </label>
      <label class="institution-field institution-field--status">
        <span>状态</span>
        <t-select
          v-model="status"
          size="small"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <t-option value="" label="全部" />
          <t-option value="ENABLED" label="启用" />
          <t-option value="DISABLED" label="停用" />
        </t-select>
      </label>
      <label class="institution-field institution-field--type">
        <span>类型</span>
        <t-select
          v-model="institutionType"
          size="small"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <t-option value="" label="全部" />
          <t-option value="HOSPITAL" label="医院" />
          <t-option value="PHARMACY" label="药房" />
          <t-option value="PLATFORM" label="平台" />
        </t-select>
      </label>
      <template #actions>
        <t-button
          theme="primary"
          size="small"
          :disabled="loading || saving"
          @click="openCreateForm"
        >
          <template #icon><t-icon name="add" /></template>
          新增机构
        </t-button>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="loading"
          @click="searchFirstPage"
        >
          <template #icon><t-icon name="search" /></template>
          {{ loading ? '查询中' : '查询' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="!canExport"
          @click="downloadInstitutionCsv"
        >
          <template #icon><t-icon name="download" /></template>
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <AdminPanel class="institution-list-panel">
      <template #title>机构列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索机构列表。' }}
      </template>
      <template #actions>
        <span class="institution-list-note">机构名称为主信息，编码显示在名称下方。</span>
      </template>

      <p v-if="actionError" class="error-line" role="alert">{{ actionError }}</p>

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
                    @click="openEditForm(row)"
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

    <AdminDrawerForm
      :open="editorOpen"
      :title="editing ? '编辑机构' : '新增机构'"
      description="配置机构名称、类型、状态和煎煮中心。"
      :submitting="saving"
      :save-label="editing ? '保存修改' : '新增机构'"
      width="560px"
      @update:open="handleEditorOpenChange"
      @save="saveInstitution"
    >
      <p v-if="actionError" class="error-line institution-editor-error" role="alert">{{ actionError }}</p>

      <div class="institution-form-grid">
        <label class="institution-field">
          <span>机构编码</span>
          <t-input
            v-model="form.institutionCode"
            name="institution-code"
            size="small"
            :disabled="editing"
            placeholder="hospital-code"
            autofocus
          />
        </label>
        <label class="institution-field">
          <span>机构名称</span>
          <t-input
            v-model="form.institutionName"
            name="institution-name"
            size="small"
            placeholder="机构名称"
          />
        </label>
        <label class="institution-field">
          <span>类型</span>
          <t-select v-model="form.institutionType" size="small">
            <t-option value="HOSPITAL" label="医院" />
            <t-option value="PHARMACY" label="药房" />
            <t-option value="PLATFORM" label="平台" />
          </t-select>
        </label>
        <label class="institution-switch-field">
          <span>状态</span>
          <span class="institution-switch-control">
            <t-switch v-model="formEnabled" size="small" />
            <span>{{ formEnabled ? '启用' : '停用' }}</span>
          </span>
        </label>
        <label class="institution-field institution-field--wide">
          <span>煎煮中心</span>
          <t-input
            v-model="form.storageType"
            name="institution-storage-type"
            size="small"
            placeholder="中心/仓储标识"
          />
        </label>
      </div>
    </AdminDrawerForm>
  </section>
</template>

<style scoped>
.institution-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.institution-list-note {
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.institution-field {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.institution-field > span,
.institution-switch-field > span:first-child {
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.institution-field--keyword {
  flex: 1 1 300px;
  min-width: 240px;
}

.institution-field--status,
.institution-field--type {
  flex: 0 0 136px;
}

.institution-field :deep(.t-input),
.institution-field :deep(.t-select) {
  width: 100%;
}

.institution-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 12px;
  min-width: 0;
}

.institution-field--wide {
  grid-column: 1 / -1;
}

.institution-switch-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.institution-switch-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: var(--admin-control-height);
  color: var(--admin-text);
  font-size: 13px;
  line-height: 20px;
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
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.institution-primary-cell small {
  color: var(--admin-text-secondary);
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
  color: var(--admin-danger);
  font-size: 13px;
  line-height: 20px;
}

.institution-editor-error,
.institution-list-error {
  margin-bottom: 12px;
}

@media (max-width: 639px) {
  .institution-form-grid {
    grid-template-columns: 1fr;
  }

  .institution-field--wide {
    grid-column: auto;
  }
}
</style>
