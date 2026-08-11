<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminRbacRole,
  deleteAdminRbacRole,
  getAdminRbacCatalog,
  listAdminOperators,
  listAdminRbacRoles,
  updateAdminRbacRole,
} from '../../api/order';
import type {
  AdminOperatorRecord,
  AdminRbacCatalog,
  AdminRbacDataScopeType,
  AdminRbacPermissionOption,
  AdminRbacRoleCommand,
  AdminRbacRolePage,
  AdminRbacRoleRecord,
} from '../../api/types';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminPagination from '../../components/admin/AdminPagination.vue';
import AdminPanel from '../../components/admin/AdminPanel.vue';
import AdminStatusTag from '../../components/admin/AdminStatusTag.vue';
import AdminTableShell from '../../components/admin/AdminTableShell.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import { downloadCsv } from '../../domain/csv';
import { errorMessage } from '../../domain/errors';
import { boundedPositiveInteger, enabledText, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

interface RoleForm {
  id: string | null;
  version: number;
  roleCode: string;
  roleName: string;
  dataScopeType: AdminRbacDataScopeType;
  enabled: boolean;
  builtIn: boolean;
  permissionCodes: string[];
  institutionIds: string[];
}

interface RoleStat {
  label: string;
  value: string;
}

interface PermissionGroup {
  domain: string;
  name: string;
  permissions: AdminRbacPermissionOption[];
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
  canManage: boolean;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const domainNames: Record<string, string> = {
  order: '订单管理',
  system: '系统管理',
  drug: '药品管理',
  institution: '机构管理',
  workflow: '工作流',
  sms: '短信管理',
  decoction: '煎药管理',
  ops: '运维管理',
  logistics: '物流管理',
  callback: '回调管理',
  portal: '门户服务',
  report: '报表管理',
  integration: '集成管理',
};

const keyword = ref('');
const page = ref(1);
const pageSize = ref(20);
const rolePage = ref<AdminRbacRolePage | null>(null);
const catalog = ref<AdminRbacCatalog | null>(null);
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const exporting = ref(false);
const memberExportingRole = ref('');
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);
const form = ref<RoleForm>(emptyForm());

const rows = computed(() => rolePage.value?.records ?? []);
const total = computed(() => rolePage.value?.total ?? 0);
const enabledTotal = computed(() => rows.value.filter((role) => role.enabled).length);
const operatorTotal = computed(() => rows.value.reduce((sum, role) => sum + role.operatorCount, 0));
const editing = computed(() => form.value.id !== null);
const formReadOnly = computed(() => form.value.builtIn || !props.canManage);
const filtersDisabled = computed(() => loading.value || saving.value || deleting.value);
const editorInputDisabled = computed(() => filtersDisabled.value || formReadOnly.value);
const rowActionsDisabled = computed(() => loading.value || saving.value || deleting.value);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const canExportRoles = computed(() => !loading.value && !exporting.value && rows.value.length > 0);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (listError.value && rolePage.value === null) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<RoleStat[]>(() => [
  { label: '角色总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledTotal.value) },
  { label: '本页关联用户', value: formatNumber(operatorTotal.value) },
]);
const permissionGroups = computed<PermissionGroup[]>(() => {
  const groups = new Map<string, AdminRbacPermissionOption[]>();
  for (const permission of catalog.value?.permissions ?? []) {
    const domain = permission.permissionCode.split(':')[0] ?? 'other';
    const current = groups.get(domain) ?? [];
    current.push(permission);
    groups.set(domain, current);
  }
  return [...groups.entries()].map(([domain, permissions]) => ({
    domain,
    name: domainNames[domain] ?? domain,
    permissions,
  }));
});
const roleSummaryText = computed(() => {
  if (!editing.value) return '创建后可继续调整授权范围和机构范围。';
  if (form.value.builtIn) return '内置角色仅支持查看，不能修改或删除。';
  return '角色权限变更在关联用户重新登录后生效。';
});
const roleHeaderTitle = computed(() => (editing.value ? '角色编辑器' : '新建角色'));
const roleHeaderDescription = computed(() => {
  if (!props.canManage) return '当前账号仅可查看角色、权限和机构范围。';
  if (form.value.builtIn) return '内置角色为只读，保留查看与导出能力。';
  return '使用角色名称作为主信息展示，角色标识作为辅助技术信息。';
});

function emptyForm(): RoleForm {
  return {
    id: null,
    version: 0,
    roleCode: '',
    roleName: '',
    dataScopeType: 'INSTITUTION',
    enabled: true,
    builtIn: false,
    permissionCodes: [],
    institutionIds: [],
  };
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function resetForm() {
  form.value = emptyForm();
  actionError.value = '';
}

function editRole(role: AdminRbacRoleRecord) {
  if (loading.value) return;
  form.value = {
    id: role.id,
    version: role.version,
    roleCode: role.roleCode,
    roleName: role.roleName,
    dataScopeType: role.dataScopeType,
    enabled: role.enabled,
    builtIn: role.builtIn,
    permissionCodes: [...role.permissionCodes],
    institutionIds: [...role.institutionIds],
  };
  actionError.value = '';
}

function togglePermission(permissionCode: string) {
  if (editorInputDisabled.value) return;
  const selected = new Set(form.value.permissionCodes);
  if (selected.has(permissionCode)) {
    selected.delete(permissionCode);
  } else {
    selected.add(permissionCode);
  }
  form.value.permissionCodes = [...selected].sort();
}

function togglePermissionGroup(permissions: AdminRbacPermissionOption[]) {
  if (editorInputDisabled.value) return;
  const codes = permissions.map((permission) => permission.permissionCode);
  const selected = new Set(form.value.permissionCodes);
  const allSelected = codes.every((code) => selected.has(code));
  for (const code of codes) {
    if (allSelected) {
      selected.delete(code);
    } else {
      selected.add(code);
    }
  }
  form.value.permissionCodes = [...selected].sort();
}

function permissionGroupSelected(permissions: AdminRbacPermissionOption[]) {
  const selected = new Set(form.value.permissionCodes);
  return permissions.every((permission) => selected.has(permission.permissionCode));
}

function toggleInstitution(institutionId: string) {
  if (editorInputDisabled.value || form.value.dataScopeType !== 'INSTITUTION') return;
  const selected = new Set(form.value.institutionIds);
  if (selected.has(institutionId)) {
    selected.delete(institutionId);
  } else {
    selected.add(institutionId);
  }
  form.value.institutionIds = [...selected];
}

function commandFromForm(): AdminRbacRoleCommand {
  return {
    roleCode: form.value.roleCode.trim().toUpperCase(),
    roleName: form.value.roleName.trim(),
    dataScopeType: form.value.dataScopeType,
    enabled: form.value.enabled,
    version: editing.value ? form.value.version : undefined,
    permissionCodes: [...form.value.permissionCodes],
    institutionIds: form.value.dataScopeType === 'INSTITUTION' ? [...form.value.institutionIds] : [],
  };
}

async function refreshRoles() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    pageSize.value = normalizePageSize();
    const [nextPage, nextCatalog] = await Promise.all([
      listAdminRbacRoles({ keyword: keyword.value, page: page.value, pageSize: pageSize.value }),
      catalog.value ? Promise.resolve(catalog.value) : getAdminRbacCatalog(),
    ]);
    if (requestId !== activeRefreshRequest.value) return;
    rolePage.value = nextPage;
    catalog.value = nextCatalog;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    rolePage.value = null;
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
  await refreshRoles();
}

async function saveRole() {
  if (editorInputDisabled.value) return;
  if (!form.value.roleCode.trim() || !form.value.roleName.trim()) {
    actionError.value = '角色标识和角色名称不能为空。';
    return;
  }
  if (form.value.permissionCodes.length === 0) {
    actionError.value = '请至少选择一个权限。';
    return;
  }
  if (form.value.dataScopeType === 'INSTITUTION' && form.value.institutionIds.length === 0) {
    actionError.value = '指定机构范围至少需要选择一家机构。';
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    const command = commandFromForm();
    if (form.value.id) {
      await updateAdminRbacRole(form.value.id, command);
      emit('notice', 'success', `角色 ${command.roleName} 已更新，关联用户重新登录后生效`);
    } else {
      await createAdminRbacRole(command);
      emit('notice', 'success', `角色 ${command.roleName} 已创建`);
    }
    resetForm();
    await refreshRoles();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function removeRole(role: AdminRbacRoleRecord) {
  if (!props.canManage || rowActionsDisabled.value || role.builtIn || role.operatorCount > 0) return;
  if (!window.confirm(`确认删除角色“${role.roleName}”吗？`)) return;
  deleting.value = true;
  actionError.value = '';
  try {
    await deleteAdminRbacRole(role.id);
    if (form.value.id === role.id) resetForm();
    emit('notice', 'success', `角色 ${role.roleName} 已删除`);
    await refreshRoles();
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    deleting.value = false;
  }
}

async function listRoleMembers(roleCode: string) {
  const records: AdminOperatorRecord[] = [];
  for (let nextPageNumber = 1; nextPageNumber <= 50; nextPageNumber += 1) {
    const result = await listAdminOperators({ roleCode, page: nextPageNumber, pageSize: 100 });
    records.push(...result.records);
    if (records.length >= result.total || result.records.length < 100) break;
  }
  return records;
}

async function downloadRoleMemberCsv(role: AdminRbacRoleRecord) {
  if (loading.value || memberExportingRole.value) return;
  memberExportingRole.value = role.id;
  actionError.value = '';
  try {
    const records = await listRoleMembers(role.roleCode);
    downloadCsv(
      `角色-${role.roleCode}-成员.csv`,
      ['角色', '工号', '姓名', '状态', '创建时间', '更新时间'],
      records.map((operator) => [
        role.roleName,
        operator.username,
        operator.displayName,
        enabledText(operator.enabled),
        formatDate(operator.createdAt),
        formatDate(operator.updatedAt),
      ]),
    );
    emit('notice', 'success', `已导出 ${formatNumber(records.length)} 名角色成员`);
  } catch (error) {
    actionError.value = errorMessage(error);
  } finally {
    memberExportingRole.value = '';
  }
}

function downloadRoleCsv() {
  if (!canExportRoles.value) return;
  exporting.value = true;
  try {
    downloadCsv(
      `角色列表-第${page.value}页.csv`,
      ['角色标识', '角色名称', '数据范围', '权限数', '机构数', '用户数', '状态'],
      rows.value.map((role) => [
        role.roleCode,
        role.roleName,
        role.dataScopeType === 'TENANT' ? '租户全域' : '指定机构',
        role.permissionCodes.length,
        role.institutionIds.length,
        role.operatorCount,
        enabledText(role.enabled),
      ]),
    );
  } finally {
    exporting.value = false;
  }
}

async function previousPage() {
  if (loading.value || !hasPreviousPage.value) return;
  page.value -= 1;
  await refreshRoles();
}

async function nextPage() {
  if (loading.value || !hasNextPage.value) return;
  page.value += 1;
  await refreshRoles();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshRoles();
    }
  },
  { immediate: true },
);

watch(
  () => form.value.dataScopeType,
  (scope) => {
    if (scope === 'TENANT') {
      form.value.institutionIds = [];
    }
  },
);

defineExpose({
  refreshRoles,
});
</script>

<template>
  <section class="role-page">
    <AdminToolbar>
      <label class="role-field role-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="role-input"
          :disabled="filtersDisabled"
          placeholder="角色名称 / 角色标识"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="filtersDisabled"
          @click="searchFirstPage"
        >
          {{ loading ? '查询中' : '查询' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="!canExportRoles"
          @click="downloadRoleCsv"
        >
          {{ exporting ? '导出中' : '导出当前页' }}
        </t-button>
        <t-button
          v-if="canManage"
          theme="default"
          variant="outline"
          size="small"
          :disabled="saving || deleting || loading"
          @click="resetForm"
        >
          新建角色
        </t-button>
      </template>
    </AdminToolbar>

    <div class="role-stats" aria-label="角色统计">
      <article
        v-for="stat in stats"
        :key="stat.label"
        class="role-stat"
      >
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <p v-if="!canManage" class="role-note role-note--readonly">
      当前账号仅有查看权限，角色授权操作已设为只读。
    </p>
    <p v-if="actionError" class="error-line" role="alert">{{ actionError }}</p>

    <AdminPanel class="role-list-panel">
      <template #title>角色列表</template>
      <template #description>
        {{ loading && loaded ? '正在刷新角色列表，当前操作已暂时禁用。' : `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` }}
      </template>
      <template #actions>
        <span class="role-list-note">角色名称优先展示，标识与技术限制放在次级信息。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在加载角色列表。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有符合条件的角色。"
      />
      <template v-else>
        <AdminTableShell>
          <table class="role-table">
            <thead>
              <tr>
                <th>角色</th>
                <th>数据范围</th>
                <th>权限</th>
                <th>用户</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="role in rows"
                :key="role.id"
                :class="{ 'is-selected': form.id === role.id }"
              >
                <td>
                  <div class="role-primary-cell">
                    <strong>{{ role.roleName }}</strong>
                    <small>
                      {{ role.roleCode }}
                      <span v-if="role.builtIn"> · 内置角色</span>
                    </small>
                  </div>
                </td>
                <td>
                  <div class="role-secondary-cell">
                    <strong>{{ role.dataScopeType === 'TENANT' ? '租户全域' : '指定机构' }}</strong>
                    <small>
                      {{ role.dataScopeType === 'TENANT' ? '全部机构共享' : `${formatNumber(role.institutionIds.length)} 家机构` }}
                    </small>
                  </div>
                </td>
                <td>{{ formatNumber(role.permissionCodes.length) }} 项</td>
                <td>{{ formatNumber(role.operatorCount) }}</td>
                <td>
                  <div class="role-status-cell">
                    <AdminStatusTag :enabled="role.enabled" />
                    <small v-if="role.builtIn">不可删除</small>
                  </div>
                </td>
                <td class="role-row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="rowActionsDisabled"
                    @click="editRole(role)"
                  >
                    {{ canManage && !role.builtIn ? '编辑' : '查看' }}
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="loading || Boolean(memberExportingRole) || role.operatorCount === 0"
                    @click="downloadRoleMemberCsv(role)"
                  >
                    {{ memberExportingRole === role.id ? '导出中' : '导出成员' }}
                  </t-button>
                  <t-button
                    theme="danger"
                    variant="outline"
                    size="small"
                    :disabled="!canManage || rowActionsDisabled || role.builtIn || role.operatorCount > 0"
                    :title="role.builtIn ? '内置角色不允许删除' : role.operatorCount > 0 ? '存在关联用户的角色不允许删除' : '删除角色'"
                    @click="removeRole(role)"
                  >
                    {{ deleting ? '删除中' : '删除' }}
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

    <AdminPanel class="role-editor-panel">
      <template #title>{{ roleHeaderTitle }}</template>
      <template #description>{{ roleHeaderDescription }}</template>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="editorInputDisabled || saving"
          @click="saveRole"
        >
          {{ saving ? '保存中' : editing ? '保存修改' : '创建角色' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="saving || deleting"
          @click="resetForm"
        >
          重置
        </t-button>
      </template>

      <div class="role-editor-summary">
        <div class="role-editor-summary__main">
          <strong>{{ editing ? form.roleName || '未命名角色' : '待创建角色' }}</strong>
          <span>{{ editing ? (form.roleCode || '未设置标识') : '填写基本信息后即可创建角色。' }}</span>
        </div>
        <div class="role-editor-summary__meta">
          <t-tag v-if="form.builtIn" theme="warning" variant="light" size="small">内置只读</t-tag>
          <t-tag v-if="!canManage" theme="default" variant="light" size="small">当前只读</t-tag>
          <AdminStatusTag :enabled="form.enabled" />
        </div>
      </div>

      <p class="role-note role-note--subtle">{{ roleSummaryText }}</p>

      <div class="role-basic-grid">
        <label class="role-field">
          <span>角色名称</span>
          <input
            v-model="form.roleName"
            class="role-input"
            :disabled="editorInputDisabled"
            placeholder="审核员"
          >
        </label>
        <label class="role-field">
          <span>角色标识</span>
          <input
            v-model="form.roleCode"
            class="role-input"
            :disabled="editorInputDisabled"
            placeholder="AUDITOR"
          >
        </label>
        <label class="role-field">
          <span>数据范围</span>
          <select
            v-model="form.dataScopeType"
            class="role-input"
            :disabled="editorInputDisabled"
          >
            <option value="TENANT">租户全域</option>
            <option value="INSTITUTION">指定机构</option>
          </select>
        </label>
        <label class="role-check">
          <input
            v-model="form.enabled"
            type="checkbox"
            :disabled="editorInputDisabled"
          >
          <span>启用角色</span>
        </label>
      </div>

      <section class="role-section">
        <header class="role-section__header">
          <div>
            <h3>权限范围</h3>
            <p>按业务域分组，名称优先，权限编码作为辅助信息保留。</p>
          </div>
          <strong>{{ formatNumber(form.permissionCodes.length) }} 项</strong>
        </header>

        <div class="permission-groups">
          <fieldset
            v-for="group in permissionGroups"
            :key="group.domain"
            class="permission-group"
            :disabled="editorInputDisabled"
          >
            <legend>
              <label class="permission-group__toggle">
                <input
                  type="checkbox"
                  :checked="permissionGroupSelected(group.permissions)"
                  @change="togglePermissionGroup(group.permissions)"
                >
                <span>{{ group.name }}</span>
              </label>
            </legend>
            <div class="permission-group__options">
              <label
                v-for="permission in group.permissions"
                :key="permission.permissionCode"
                class="permission-option"
              >
                <input
                  type="checkbox"
                  :checked="form.permissionCodes.includes(permission.permissionCode)"
                  @change="togglePermission(permission.permissionCode)"
                >
                <span>
                  <strong>{{ permission.permissionName }}</strong>
                  <small>{{ permission.permissionCode }}</small>
                </span>
              </label>
            </div>
          </fieldset>
        </div>
      </section>

      <section class="role-section">
        <header class="role-section__header">
          <div>
            <h3>机构范围</h3>
            <p>{{ form.dataScopeType === 'TENANT' ? '租户全域角色不需要额外选择机构。' : '仅在指定机构范围下启用机构选择。' }}</p>
          </div>
          <strong>{{ formatNumber(form.institutionIds.length) }} 家</strong>
        </header>

        <AdminPageState
          v-if="!catalog && loading"
          state="loading"
          message="正在加载机构目录。"
        />
        <div v-else-if="form.dataScopeType === 'TENANT'" class="role-inline-state">
          当前角色已设置为租户全域，机构范围自动清空。
        </div>
        <AdminPageState
          v-else-if="(catalog?.institutions.length ?? 0) === 0"
          state="empty"
          message="当前租户没有可授权机构。"
        />
        <div v-else class="institution-options">
          <label
            v-for="institution in catalog?.institutions ?? []"
            :key="institution.institutionId"
            class="institution-option"
          >
            <input
              type="checkbox"
              :disabled="editorInputDisabled"
              :checked="form.institutionIds.includes(institution.institutionId)"
              @change="toggleInstitution(institution.institutionId)"
            >
            <span>
              <strong>{{ institution.institutionName }}</strong>
              <small>{{ institution.institutionCode }}</small>
            </span>
          </label>
        </div>
      </section>
    </AdminPanel>
  </section>
</template>

<style scoped>
.role-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.role-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.role-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.role-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.role-stat span,
.role-list-note,
.role-note,
.role-editor-summary__main span,
.role-secondary-cell small,
.role-primary-cell small,
.role-status-cell small,
.permission-option small,
.institution-option small,
.role-section__header p {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.role-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.role-field span,
.role-check span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.role-field--keyword {
  flex: 1 1 280px;
}

.role-input {
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

.role-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.role-note {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.role-note--readonly {
  border-color: #f7d9a4;
  color: #8c5a00;
  background: #fff9eb;
}

.role-note--subtle {
  padding: 0;
  border: 0;
  background: transparent;
}

.role-table {
  min-width: 860px;
}

.role-table tbody tr.is-selected td {
  background: #f5f8ff;
}

.role-primary-cell,
.role-secondary-cell,
.role-status-cell {
  display: grid;
  gap: 2px;
}

.role-primary-cell strong,
.role-secondary-cell strong,
.permission-option strong,
.institution-option strong,
.role-editor-summary__main strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.role-row-actions {
  white-space: nowrap;
}

.role-row-actions :deep(.t-button) {
  margin-right: 8px;
}

.role-row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.role-editor-panel :deep(.admin-panel__content) {
  display: grid;
  gap: 14px;
}

.role-editor-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px 12px;
  padding: 12px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #fafcff;
}

.role-editor-summary__main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.role-editor-summary__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.role-basic-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(120px, auto);
  gap: 12px;
  min-width: 0;
}

.role-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding-top: 24px;
}

.role-section {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.role-section__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px 12px;
  padding-top: 12px;
  border-top: 1px solid #e3e8f0;
}

.role-section__header h3,
.role-section__header p {
  margin: 0;
}

.role-section__header h3 {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.role-section__header strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.permission-groups {
  display: grid;
  gap: 12px;
}

.permission-group {
  margin: 0;
  padding: 10px 12px 12px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.permission-group legend {
  padding: 0 4px;
}

.permission-group__toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.permission-group__options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  min-width: 0;
}

.permission-option,
.institution-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #edf1f6;
  border-radius: 6px;
  background: #fcfcfd;
}

.permission-option {
  flex: 1 1 240px;
}

.permission-option span,
.institution-option span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.institution-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  min-width: 0;
}

.role-inline-state {
  padding: 12px;
  border: 1px dashed #d7deea;
  border-radius: 6px;
  color: #667085;
  font-size: 13px;
  line-height: 20px;
  background: #fafcff;
}

@media (max-width: 980px) {
  .role-basic-grid {
    grid-template-columns: 1fr;
  }

  .role-check {
    padding-top: 0;
  }
}
</style>
