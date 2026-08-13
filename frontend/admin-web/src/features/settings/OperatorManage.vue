<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { revokeAdminUserSessions } from '../../api/auth';
import { createAdminOperator, listAdminOperators, listAdminRbacRoles, updateAdminOperator } from '../../api/order';
import type { AdminOperatorCommand, AdminOperatorPage, AdminOperatorRecord, AdminRbacRoleRecord } from '../../api/types';
import AdminDrawerForm from '../../components/admin/AdminDrawerForm.vue';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminPagination from '../../components/admin/AdminPagination.vue';
import AdminPanel from '../../components/admin/AdminPanel.vue';
import AdminStatusTag from '../../components/admin/AdminStatusTag.vue';
import AdminTableShell from '../../components/admin/AdminTableShell.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import { downloadCsv } from '../../domain/csv';
import { errorMessage } from '../../domain/errors';
import { boundedPositiveInteger, enabledStringParam, enabledText, displayValue, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface OperatorForm {
  id: string | null;
  username: string;
  displayName: string;
  roleCode: string;
  enabled: boolean;
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
  canManage: boolean;
  currentUserId: string;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const keyword = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const operatorPage = ref<AdminOperatorPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const editorOpen = ref(false);
const revokingUserId = ref('');
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const roleOptions = ref<AdminRbacRoleRecord[]>([]);
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);
const form = ref<OperatorForm>({
  id: null,
  username: '',
  displayName: '',
  roleCode: '',
  enabled: true,
});

const rows = computed(() => operatorPage.value?.records ?? []);
const total = computed(() => operatorPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);
const roleNameByCode = computed(() => new Map(roleOptions.value.map((role) => [role.roleCode, role.roleName] as const)));
const canExport = computed(() => !loading.value && rows.value.length > 0);
const rowActionsDisabled = computed(() => loading.value || saving.value || !props.canManage);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (listError.value && operatorPage.value === null) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
function downloadOperatorCsv() {
  downloadCsv(
    `后台工号-第${page.value}页.csv`,
    ['工号', '姓名', '角色名称', '角色编码', '状态', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.username,
      row.displayName,
      rolePrimaryText(row),
      row.roleCode,
      enabledText(row.enabled),
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个工号`);
}

function commandFromForm(): AdminOperatorCommand {
  return {
    ...(form.value.id ? {} : { username: form.value.username.trim() }),
    displayName: form.value.displayName.trim(),
    roleCode: form.value.roleCode.trim(),
    enabled: form.value.enabled,
  };
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function rolePrimaryText(row: AdminOperatorRecord) {
  if (!row.roleCode) return '未分配角色';
  return roleNameByCode.value.get(row.roleCode) ?? '未知角色';
}

async function refreshOperators() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    pageSize.value = normalizePageSize();
    const [nextPage, roles] = await Promise.all([
      listAdminOperators({
        keyword: keyword.value,
        enabled: enabledStringParam(enabledFilter.value),
        page: page.value,
        pageSize: pageSize.value,
      }),
      listAdminRbacRoles({ page: 1, pageSize: 100 }),
    ]);
    if (requestId !== activeRefreshRequest.value) return;
    operatorPage.value = nextPage;
    roleOptions.value = roles.records;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个工号`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    operatorPage.value = null;
    loaded.value = false;
    listError.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    if (requestId === activeRefreshRequest.value) {
      loading.value = false;
    }
  }
}

async function searchFirstPage() {
  if (loading.value) return;
  page.value = 1;
  await refreshOperators();
}

function resetForm() {
  actionError.value = '';
  form.value = {
    id: null,
    username: '',
    displayName: '',
    roleCode: '',
    enabled: true,
  };
}

function openCreateForm() {
  if (!props.canManage || saving.value) return;
  resetForm();
  editorOpen.value = true;
}

function openEditForm(row: AdminOperatorRecord) {
  if (!props.canManage || saving.value) return;
  actionError.value = '';
  form.value = {
    id: row.id,
    username: row.username,
    displayName: row.displayName,
    roleCode: row.roleCode ?? '',
    enabled: row.enabled,
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

async function saveOperator() {
  if (!props.canManage || saving.value) return;
  if (!form.value.username.trim() || !form.value.displayName.trim()) {
    actionError.value = '工号和姓名不能为空';
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    if (form.value.id) {
      await updateAdminOperator(form.value.id, commandFromForm());
      emit('notice', 'success', `工号 ${form.value.username} 已更新`);
    } else {
      await createAdminOperator(commandFromForm());
      emit('notice', 'success', `工号 ${form.value.username} 已新增`);
    }
    closeEditor();
    await refreshOperators();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleOperator(row: AdminOperatorRecord) {
  if (!props.canManage) return;
  saving.value = true;
  actionError.value = '';
  try {
    await updateAdminOperator(row.id, {
      displayName: row.displayName,
      roleCode: row.roleCode ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `工号 ${row.username} 已${row.enabled ? '停用' : '启用'}`);
    await refreshOperators();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function forceLogout(row: AdminOperatorRecord) {
  if (!props.canManage || row.id === props.currentUserId) return;
  if (!window.confirm(`确认强制下线工号“${row.username}”的全部登录会话吗？`)) return;
  revokingUserId.value = row.id;
  actionError.value = '';
  try {
    const result = await revokeAdminUserSessions(row.id);
    emit(
      'notice',
      'success',
      result.revokedSessions > 0
        ? `工号 ${row.username} 已强制下线 ${formatNumber(result.revokedSessions)} 个会话`
        : `工号 ${row.username} 当前没有活跃会话`,
    );
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    revokingUserId.value = '';
  }
}

async function previousPage() {
  if (loading.value) return;
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOperators();
}

async function nextPage() {
  if (loading.value) return;
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOperators();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshOperators();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshOperators,
});
</script>

<template>
  <section class="operator-page">
    <AdminToolbar>
      <label class="operator-field operator-field--keyword">
        <span>关键字</span>
        <t-input
          v-model="keyword"
          name="operator-keyword"
          size="small"
          clearable
          :disabled="loading"
          placeholder="工号 / 姓名 / 角色"
          @enter="searchFirstPage"
        />
      </label>
      <label class="operator-field operator-field--status">
        <span>状态</span>
        <t-select
          v-model="enabledFilter"
          size="small"
          :disabled="loading"
          @change="searchFirstPage"
        >
          <t-option value="" label="全部" />
          <t-option value="true" label="启用" />
          <t-option value="false" label="停用" />
        </t-select>
      </label>
      <template #actions>
        <t-button
          theme="primary"
          size="small"
          :disabled="loading || saving || !canManage"
          @click="openCreateForm"
        >
          <template #icon><t-icon name="add" /></template>
          新增工号
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="!canExport"
          @click="downloadOperatorCsv"
        >
          <template #icon><t-icon name="download" /></template>
          导出当前页
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
      </template>
    </AdminToolbar>

    <AdminPanel class="operator-list-panel">
      <template #title>工号列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索工号列表。' }}
      </template>
      <template #actions>
        <span class="operator-list-note">角色主值显示名称，附带编码。</span>
      </template>

      <p v-if="actionError" class="error-line" role="alert">{{ actionError }}</p>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询工号。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关工号。"
      />
      <template v-else>
        <AdminTableShell>
          <table class="operator-table">
            <thead>
              <tr>
                <th>工号</th>
                <th>姓名</th>
                <th>角色</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <strong class="operator-primary-text">{{ displayValue(row.username) }}</strong>
                </td>
                <td>{{ displayValue(row.displayName) }}</td>
                <td>
                  <div class="operator-role-cell">
                    <strong>{{ rolePrimaryText(row) }}</strong>
                    <small v-if="row.roleCode">{{ row.roleCode }}</small>
                  </div>
                </td>
                <td>
                  <AdminStatusTag :enabled="row.enabled" />
                </td>
                <td>{{ formatDate(row.createdAt) }}</td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="operator-row-actions">
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
                    @click="toggleOperator(row)"
                  >
                    {{ row.enabled ? '停用' : '启用' }}
                  </t-button>
                  <t-button
                    theme="danger"
                    variant="outline"
                    size="small"
                    :disabled="loading || saving || Boolean(revokingUserId) || !canManage || row.id === currentUserId"
                    :title="row.id === currentUserId ? '当前账号请使用退出登录' : '立即撤销该账号的全部登录会话'"
                    @click="forceLogout(row)"
                  >
                    {{ revokingUserId === row.id ? '下线中' : '强制下线' }}
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
      :title="editing ? '编辑工号' : '新增工号'"
      description="配置工号姓名、角色和状态。"
      :submitting="saving"
      :save-label="editing ? '保存修改' : '新增工号'"
      width="520px"
      @update:open="handleEditorOpenChange"
      @save="saveOperator"
    >
      <p v-if="actionError" class="error-line operator-editor-error" role="alert">{{ actionError }}</p>

      <div class="operator-form-grid">
        <label class="operator-field">
          <span>工号</span>
          <t-input
            v-model="form.username"
            name="operator-username"
            size="small"
            :disabled="editing || !canManage"
            placeholder="operator01"
            autofocus
          />
        </label>
        <label class="operator-field">
          <span>姓名</span>
          <t-input
            v-model="form.displayName"
            name="operator-display-name"
            size="small"
            :disabled="!canManage"
            placeholder="操作员姓名"
          />
        </label>
        <label class="operator-field operator-field--wide">
          <span>角色</span>
          <t-select
            v-model="form.roleCode"
            size="small"
            :disabled="!canManage"
          >
            <t-option value="" label="未分配角色" />
            <t-option
              v-for="role in roleOptions"
              :key="role.id"
              :value="role.roleCode"
              :label="`${role.roleName}（${role.roleCode}）`"
              :disabled="!role.enabled"
            />
          </t-select>
        </label>
        <label class="operator-switch-field operator-field--wide">
          <span>状态</span>
          <span class="operator-switch-control">
            <t-switch v-model="form.enabled" size="small" :disabled="!canManage" />
            <span>{{ form.enabled ? '启用' : '停用' }}</span>
          </span>
        </label>
      </div>
    </AdminDrawerForm>
  </section>
</template>

<style scoped>
.operator-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.operator-list-note {
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.operator-field {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.operator-field > span,
.operator-switch-field > span:first-child {
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.operator-field--keyword {
  flex: 1 1 280px;
  min-width: 220px;
}

.operator-field--status {
  flex: 0 0 144px;
}

.operator-field :deep(.t-input),
.operator-field :deep(.t-select) {
  width: 100%;
}

.operator-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 12px;
  min-width: 0;
}

.operator-field--wide {
  grid-column: 1 / -1;
}

.operator-switch-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.operator-switch-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: var(--admin-control-height);
  color: var(--admin-text);
  font-size: 13px;
  line-height: 20px;
}

.operator-table {
  min-width: 1040px;
}

.operator-primary-text,
.operator-role-cell strong {
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.operator-role-cell {
  display: grid;
  gap: 2px;
}

.operator-role-cell small {
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.operator-row-actions {
  white-space: nowrap;
}

.operator-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.operator-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: var(--admin-danger);
  font-size: 13px;
  line-height: 20px;
}

.operator-editor-error {
  margin-bottom: 12px;
}

@media (max-width: 639px) {
  .operator-form-grid {
    grid-template-columns: 1fr;
  }

  .operator-field--wide {
    grid-column: auto;
  }
}
</style>
