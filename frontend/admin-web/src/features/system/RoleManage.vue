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
const errorLine = ref('');
const form = ref<RoleForm>(emptyForm());

const rows = computed(() => rolePage.value?.records ?? []);
const total = computed(() => rolePage.value?.total ?? 0);
const enabledTotal = computed(() => rows.value.filter((role) => role.enabled).length);
const operatorTotal = computed(() => rows.value.reduce((sum, role) => sum + role.operatorCount, 0));
const editing = computed(() => form.value.id !== null);
const formReadOnly = computed(() => form.value.builtIn || !props.canManage);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const permissionGroups = computed(() => {
  const groups = new Map<string, AdminRbacPermissionOption[]>();
  for (const permission of catalog.value?.permissions ?? []) {
    const domain = permission.permissionCode.split(':')[0] ?? 'other';
    const group = groups.get(domain) ?? [];
    group.push(permission);
    groups.set(domain, group);
  }
  return [...groups.entries()].map(([domain, permissions]) => ({
    domain,
    name: domainNames[domain] ?? domain,
    permissions,
  }));
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
  errorLine.value = '';
}

function editRole(role: AdminRbacRoleRecord) {
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
  errorLine.value = '';
}

function togglePermission(permissionCode: string) {
  if (formReadOnly.value) return;
  const selected = new Set(form.value.permissionCodes);
  if (selected.has(permissionCode)) selected.delete(permissionCode);
  else selected.add(permissionCode);
  form.value.permissionCodes = [...selected].sort();
}

function togglePermissionGroup(permissions: AdminRbacPermissionOption[]) {
  if (formReadOnly.value) return;
  const codes = permissions.map((permission) => permission.permissionCode);
  const selected = new Set(form.value.permissionCodes);
  const allSelected = codes.every((code) => selected.has(code));
  codes.forEach((code) => (allSelected ? selected.delete(code) : selected.add(code)));
  form.value.permissionCodes = [...selected].sort();
}

function permissionGroupSelected(permissions: AdminRbacPermissionOption[]) {
  const selected = new Set(form.value.permissionCodes);
  return permissions.every((permission) => selected.has(permission.permissionCode));
}

function toggleInstitution(institutionId: string) {
  if (formReadOnly.value || form.value.dataScopeType !== 'INSTITUTION') return;
  const selected = new Set(form.value.institutionIds);
  if (selected.has(institutionId)) selected.delete(institutionId);
  else selected.add(institutionId);
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
  loading.value = true;
  errorLine.value = '';
  try {
    pageSize.value = normalizePageSize();
    const [nextPage, nextCatalog] = await Promise.all([
      listAdminRbacRoles({ keyword: keyword.value, page: page.value, pageSize: pageSize.value }),
      catalog.value ? Promise.resolve(catalog.value) : getAdminRbacCatalog(),
    ]);
    rolePage.value = nextPage;
    catalog.value = nextCatalog;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
  } catch (error) {
    rolePage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshRoles();
}

async function saveRole() {
  if (formReadOnly.value) return;
  if (!form.value.roleCode.trim() || !form.value.roleName.trim()) {
    errorLine.value = '角色标识和角色名称不能为空';
    return;
  }
  if (form.value.permissionCodes.length === 0) {
    errorLine.value = '请至少选择一个权限';
    return;
  }
  if (form.value.dataScopeType === 'INSTITUTION' && form.value.institutionIds.length === 0) {
    errorLine.value = '指定机构范围至少需要选择一家机构';
    return;
  }
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function removeRole(role: AdminRbacRoleRecord) {
  if (!props.canManage || role.builtIn || role.operatorCount > 0) return;
  if (!window.confirm(`确认删除角色“${role.roleName}”吗？`)) return;
  deleting.value = true;
  errorLine.value = '';
  try {
    await deleteAdminRbacRole(role.id);
    if (form.value.id === role.id) resetForm();
    emit('notice', 'success', `角色 ${role.roleName} 已删除`);
    await refreshRoles();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    deleting.value = false;
  }
}

async function listRoleMembers(roleCode: string) {
  const records: AdminOperatorRecord[] = [];
  for (let nextPage = 1; nextPage <= 50; nextPage += 1) {
    const result = await listAdminOperators({ roleCode, page: nextPage, pageSize: 100 });
    records.push(...result.records);
    if (records.length >= result.total || result.records.length < 100) break;
  }
  return records;
}

async function downloadRoleMemberCsv(role: AdminRbacRoleRecord) {
  memberExportingRole.value = role.id;
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
    errorLine.value = errorMessage(error);
  } finally {
    memberExportingRole.value = '';
  }
}

function downloadRoleCsv() {
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
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshRoles();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshRoles();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) void refreshRoles();
  },
  { immediate: true },
);

watch(
  () => form.value.dataScopeType,
  (scope) => {
    if (scope === 'TENANT') form.value.institutionIds = [];
  },
);

defineExpose({ refreshRoles });
</script>

<template>
  <section class="legacy-page role-page">
    <ul class="legacy-search role-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-medium" placeholder="角色名称 / 标识" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0 || exporting" @click="downloadRoleCsv">
          导出当前页
        </button>
      </li>
      <li v-if="canManage">
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">新增角色</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line" role="alert">{{ errorLine }}</p>
    <p v-if="!canManage" class="role-readonly-note">当前账号仅有查看权限，角色授权操作已设为只读。</p>

    <ul class="legacy-stats role-stats">
      <li><strong>{{ formatNumber(total) }}</strong><span>角色总数</span></li>
      <li><strong>{{ formatNumber(enabledTotal) }}</strong><span>本页启用</span></li>
      <li><strong>{{ formatNumber(operatorTotal) }}</strong><span>本页关联用户</span></li>
    </ul>

    <div class="role-workspace">
      <div class="legacy-panel role-list-panel">
        <table class="legacy-main-table role-table">
          <thead>
            <tr class="legacy-main-head">
              <th>角色</th><th>数据范围</th><th>权限</th><th>用户</th><th>状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading" class="legacy-main-info"><td colspan="6" class="legacy-empty">正在加载角色</td></tr>
            <tr v-else-if="rows.length === 0" class="legacy-main-info"><td colspan="6" class="legacy-empty">没有符合条件的角色</td></tr>
            <tr v-for="role in rows" :key="role.id" class="legacy-main-info" :class="{ selected: form.id === role.id }">
              <td><strong>{{ role.roleName }}</strong><small>{{ role.roleCode }}<span v-if="role.builtIn"> · 内置</span></small></td>
              <td>{{ role.dataScopeType === 'TENANT' ? '租户全域' : `${role.institutionIds.length} 家机构` }}</td>
              <td>{{ role.permissionCodes.length }} 项</td>
              <td>{{ formatNumber(role.operatorCount) }}</td>
              <td>{{ enabledText(role.enabled) }}</td>
              <td>
                <div class="role-row-actions">
                  <button class="legacy-btn" type="button" @click="editRole(role)">{{ canManage && !role.builtIn ? '编辑' : '查看' }}</button>
                  <button class="legacy-btn" type="button" :disabled="memberExportingRole === role.id || role.operatorCount === 0" @click="downloadRoleMemberCsv(role)">导出成员</button>
                  <button class="legacy-btn legacy-btn-danger" type="button" :disabled="!canManage || role.builtIn || role.operatorCount > 0 || deleting" @click="removeRole(role)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="legacy-pagination">
          <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="previousPage">上一页</button>
          <span>第 {{ page }} 页 / 共 {{ formatNumber(total) }} 条</span>
          <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="nextPage">下一页</button>
        </div>
      </div>

      <form class="legacy-panel role-editor" @submit.prevent="saveRole">
        <header class="role-editor-header">
          <div><strong>{{ editing ? '角色授权' : '新增角色' }}</strong><small>权限变更在关联用户重新登录后生效</small></div>
          <span v-if="form.builtIn">内置角色只读</span>
        </header>

        <div class="role-form-grid">
          <label>角色标识<input v-model="form.roleCode" class="legacy-input" :disabled="formReadOnly" placeholder="AUDITOR" /></label>
          <label>角色名称<input v-model="form.roleName" class="legacy-input" :disabled="formReadOnly" placeholder="审核员" /></label>
          <label>数据范围
            <select v-model="form.dataScopeType" class="legacy-input" :disabled="formReadOnly">
              <option value="TENANT">租户全域</option><option value="INSTITUTION">指定机构</option>
            </select>
          </label>
          <label class="role-enabled"><input v-model="form.enabled" type="checkbox" :disabled="formReadOnly" />启用角色</label>
        </div>

        <section class="role-editor-section">
          <h3>功能权限 <span>{{ form.permissionCodes.length }} 项</span></h3>
          <div class="permission-groups">
            <fieldset v-for="group in permissionGroups" :key="group.domain" :disabled="formReadOnly">
              <legend>
                <label><input type="checkbox" :checked="permissionGroupSelected(group.permissions)" @change="togglePermissionGroup(group.permissions)" />{{ group.name }}</label>
              </legend>
              <label v-for="permission in group.permissions" :key="permission.permissionCode" class="permission-option">
                <input type="checkbox" :checked="form.permissionCodes.includes(permission.permissionCode)" @change="togglePermission(permission.permissionCode)" />
                <span><strong>{{ permission.permissionName }}</strong><small>{{ permission.permissionCode }}</small></span>
              </label>
            </fieldset>
          </div>
        </section>

        <section v-if="form.dataScopeType === 'INSTITUTION'" class="role-editor-section">
          <h3>机构范围 <span>{{ form.institutionIds.length }} 家</span></h3>
          <div class="institution-options">
            <label v-for="institution in catalog?.institutions ?? []" :key="institution.institutionId">
              <input type="checkbox" :disabled="formReadOnly" :checked="form.institutionIds.includes(institution.institutionId)" @change="toggleInstitution(institution.institutionId)" />
              <span><strong>{{ institution.institutionName }}</strong><small>{{ institution.institutionCode }}</small></span>
            </label>
            <p v-if="(catalog?.institutions.length ?? 0) === 0">当前租户没有可授权机构。</p>
          </div>
        </section>

        <footer class="role-editor-actions">
          <button class="legacy-btn legacy-btn-primary" type="submit" :disabled="formReadOnly || saving">{{ saving ? '保存中' : editing ? '保存授权' : '创建角色' }}</button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
        </footer>
      </form>
    </div>
  </section>
</template>

<style scoped>
.role-search { row-gap: 10px; }
.role-stats { margin-bottom: 10px; }
.role-readonly-note { background: #fff7e6; border: 1px solid #ffd591; color: #8c5a00; margin: 0 0 10px; padding: 8px 10px; }
.role-workspace { align-items: start; display: grid; gap: 12px; grid-template-columns: minmax(620px, 1.25fr) minmax(420px, .75fr); }
.role-list-panel { min-width: 0; overflow-x: auto; }
.role-table { min-width: 760px; }
.role-table td:first-child strong, .role-table td:first-child small { display: block; }
.role-table td:first-child small, .role-editor small, .permission-option small, .institution-options small { color: #718096; margin-top: 2px; }
.role-table tr.selected td { background: #edf5ff; }
.role-row-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.legacy-btn-danger { color: #c53030; }
.role-editor { min-width: 0; padding: 14px; }
.role-editor-header { align-items: center; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; margin-bottom: 12px; padding-bottom: 10px; }
.role-editor-header div, .role-editor-header strong, .role-editor-header small { display: grid; gap: 2px; }
.role-editor-header span { color: #8c5a00; font-size: 12px; }
.role-form-grid { display: grid; gap: 10px; grid-template-columns: 1fr 1fr; }
.role-form-grid label { color: #4a5568; display: grid; font-size: 13px; gap: 4px; }
.role-enabled { align-content: center; display: flex !important; gap: 6px !important; }
.role-editor-section { border-top: 1px solid #e2e8f0; margin-top: 14px; padding-top: 12px; }
.role-editor-section h3 { align-items: center; display: flex; font-size: 14px; justify-content: space-between; margin: 0 0 10px; }
.role-editor-section h3 span { color: #718096; font-size: 12px; font-weight: 400; }
.permission-groups { display: grid; gap: 8px; }
.permission-groups fieldset { border: 1px solid #d8e1ec; margin: 0; padding: 8px 10px 10px; }
.permission-groups legend { color: #1f365c; font-size: 13px; font-weight: 600; padding: 0 4px; }
.permission-groups legend label, .permission-option, .institution-options label { align-items: flex-start; display: flex; gap: 7px; }
.permission-option { margin-top: 8px; }
.permission-option span, .permission-option strong, .permission-option small, .institution-options span, .institution-options strong, .institution-options small { display: block; min-width: 0; overflow-wrap: anywhere; }
.permission-option strong, .institution-options strong { font-size: 13px; font-weight: 500; }
.institution-options { display: grid; gap: 8px; max-height: 220px; overflow-y: auto; }
.role-editor-actions { display: flex; gap: 8px; margin-top: 14px; }
@media (max-width: 1180px) { .role-workspace { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .role-form-grid { grid-template-columns: 1fr; } .role-editor-actions { flex-wrap: wrap; } }
</style>
