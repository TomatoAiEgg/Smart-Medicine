<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { revokeAdminUserSessions } from '../../api/auth';
import { createAdminOperator, listAdminOperators, listAdminRbacRoles, updateAdminOperator } from '../../api/order';
import type { AdminOperatorCommand, AdminOperatorPage, AdminOperatorRecord, AdminRbacRoleRecord } from '../../api/types';
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

interface OperatorStat {
  label: string;
  value: string;
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
const revokingUserId = ref('');
const loaded = ref(false);
const errorLine = ref('');
const roleOptions = ref<AdminRbacRoleRecord[]>([]);
const form = ref<OperatorForm>({
  id: null,
  username: '',
  displayName: '',
  roleCode: '',
  enabled: true,
});

const rows = computed(() => operatorPage.value?.records ?? []);
const total = computed(() => operatorPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== null);
const roleNameByCode = computed(() => new Map(roleOptions.value.map((role) => [role.roleCode, role.roleName] as const)));
const canExport = computed(() => !loading.value && rows.value.length > 0);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (errorLine.value && operatorPage.value === null) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<OperatorStat[]>(() => [
  { label: '工号总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function downloadOperatorCsv() {
  downloadCsv(
    `后台工号-第${page.value}页.csv`,
    ['工号', '姓名', '角色', '状态', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.username,
      row.displayName,
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
    username: form.value.username.trim(),
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
  loading.value = true;
  errorLine.value = '';
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
    operatorPage.value = nextPage;
    roleOptions.value = roles.records;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个工号`);
  } catch (error) {
    operatorPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOperators();
}

function resetForm() {
  form.value = {
    id: null,
    username: '',
    displayName: '',
    roleCode: '',
    enabled: true,
  };
}

function editOperator(row: AdminOperatorRecord) {
  form.value = {
    id: row.id,
    username: row.username,
    displayName: row.displayName,
    roleCode: row.roleCode ?? '',
    enabled: row.enabled,
  };
}

async function saveOperator() {
  if (!props.canManage) return;
  if (!form.value.username.trim() || !form.value.displayName.trim()) {
    errorLine.value = '工号和姓名不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    if (form.value.id) {
      await updateAdminOperator(form.value.id, commandFromForm());
      emit('notice', 'success', `工号 ${form.value.username} 已更新`);
    } else {
      await createAdminOperator(commandFromForm());
      emit('notice', 'success', `工号 ${form.value.username} 已新增`);
    }
    resetForm();
    await refreshOperators();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleOperator(row: AdminOperatorRecord) {
  if (!props.canManage) return;
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminOperator(row.id, {
      displayName: row.displayName,
      roleCode: row.roleCode ?? '',
      enabled: !row.enabled,
    });
    emit('notice', 'success', `工号 ${row.username} 已${row.enabled ? '停用' : '启用'}`);
    await refreshOperators();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function forceLogout(row: AdminOperatorRecord) {
  if (!props.canManage || row.id === props.currentUserId) return;
  if (!window.confirm(`确认强制下线工号“${row.username}”的全部登录会话吗？`)) return;
  revokingUserId.value = row.id;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    revokingUserId.value = '';
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOperators();
}

async function nextPage() {
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
        <input
          v-model="keyword"
          class="operator-input"
          placeholder="工号 / 姓名 / 角色"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="operator-field operator-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="operator-input"
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
          @click="downloadOperatorCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="operator-stats" aria-label="工号统计">
      <article
        v-for="stat in stats"
        :key="stat.label"
        class="operator-stat"
      >
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="operator-edit-panel">
      <template #title>{{ editing ? '编辑工号' : '新增工号' }}</template>
      <template #description>
        {{ canManage ? '维护工号名称、角色与状态。' : '当前账号仅可查看，编辑与状态变更按钮已禁用。' }}
      </template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="saving || !canManage"
          @click="saveOperator"
        >
          {{ saving ? '保存中' : editing ? '保存修改' : '新增工号' }}
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

      <p v-if="errorLine" class="error-line" role="alert">{{ errorLine }}</p>

      <div class="operator-form-grid">
        <label class="operator-field">
          <span>工号</span>
          <input
            v-model="form.username"
            class="operator-input"
            :disabled="editing || !canManage"
            placeholder="operator01"
          >
        </label>
        <label class="operator-field">
          <span>姓名</span>
          <input
            v-model="form.displayName"
            class="operator-input"
            :disabled="!canManage"
            placeholder="操作员姓名"
          >
        </label>
        <label class="operator-field">
          <span>角色</span>
          <select
            v-model="form.roleCode"
            class="operator-input"
            :disabled="!canManage"
          >
            <option value="">未分配角色</option>
            <option
              v-for="role in roleOptions"
              :key="role.id"
              :value="role.roleCode"
              :disabled="!role.enabled"
            >
              {{ role.roleName }}（{{ role.roleCode }}）
            </option>
          </select>
        </label>
        <label class="operator-check">
          <input
            v-model="form.enabled"
            type="checkbox"
            :disabled="!canManage"
          >
          <span>启用</span>
        </label>
      </div>
    </AdminPanel>

    <AdminPanel class="operator-list-panel">
      <template #title>工号列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索工号列表。' }}
      </template>
      <template #actions>
        <span class="operator-list-note">角色主值显示名称，附带编码。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询工号。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="errorLine"
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
                    :disabled="saving || !canManage"
                    @click="editOperator(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="saving || !canManage"
                    @click="toggleOperator(row)"
                  >
                    {{ row.enabled ? '停用' : '启用' }}
                  </t-button>
                  <t-button
                    theme="danger"
                    variant="outline"
                    size="small"
                    :disabled="saving || Boolean(revokingUserId) || !canManage || row.id === currentUserId"
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
  </section>
</template>

<style scoped>
.operator-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.operator-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.operator-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.operator-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.operator-stat span,
.operator-list-note {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.operator-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(96px, auto);
  gap: 12px;
  min-width: 0;
}

.operator-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.operator-field span,
.operator-check span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.operator-field--keyword {
  flex: 1 1 280px;
}

.operator-field--status {
  flex: 0 0 160px;
}

.operator-input {
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

.operator-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.operator-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding-top: 24px;
}

.operator-table {
  min-width: 960px;
}

.operator-primary-text,
.operator-role-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.operator-role-cell {
  display: grid;
  gap: 2px;
}

.operator-role-cell small {
  color: #667085;
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

@media (max-width: 980px) {
  .operator-form-grid {
    grid-template-columns: 1fr;
  }

  .operator-check {
    padding-top: 0;
  }
}
</style>
