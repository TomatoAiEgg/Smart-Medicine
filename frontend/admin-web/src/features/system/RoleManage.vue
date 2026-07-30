<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { listAdminOperatorRoles, listAdminOperators, renameAdminOperatorRole } from '../../api/order';
import type { AdminOperatorRecord, AdminOperatorRolePage, AdminOperatorRoleRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, enabledText, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

interface RenameForm {
  oldRoleCode: string;
  newRoleCode: string;
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
const page = ref(1);
const pageSize = ref(20);
const rolePage = ref<AdminOperatorRolePage | null>(null);
const loading = ref(false);
const saving = ref(false);
const exporting = ref(false);
const memberExportingRole = ref('');
const loaded = ref(false);
const errorLine = ref('');
const exportPageSize = 100;
const maxExportPages = 50;
const renameForm = ref<RenameForm>({
  oldRoleCode: '',
  newRoleCode: '',
});

const rows = computed(() => rolePage.value?.records ?? []);
const total = computed(() => rolePage.value?.total ?? 0);
const operatorTotal = computed(() => rows.value.reduce((sum, row) => sum + row.operatorCount, 0));
const enabledTotal = computed(() => rows.value.reduce((sum, row) => sum + row.enabledCount, 0));
const disabledTotal = computed(() => rows.value.reduce((sum, row) => sum + row.disabledCount, 0));
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const canRename = computed(() => renameForm.value.oldRoleCode.trim() && renameForm.value.newRoleCode.trim());

async function listExportRoles() {
  const records: AdminOperatorRoleRecord[] = [];
  for (let nextPageNo = 1; nextPageNo <= maxExportPages; nextPageNo += 1) {
    const nextPage = await listAdminOperatorRoles({
      keyword: keyword.value,
      page: nextPageNo,
      pageSize: exportPageSize,
    });
    records.push(...nextPage.records);
    if (records.length >= nextPage.total || nextPage.records.length < exportPageSize) {
      break;
    }
  }
  return records;
}

async function downloadRoleCsv() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const records = await listExportRoles();
    const headers = ['角色标识', '操作员数', '启用数', '停用数', '首次创建', '最近更新'];
    downloadCsv(
      'operator-roles.csv',
      headers,
      records.map((row) => [
        row.roleCode,
        row.operatorCount,
        row.enabledCount,
        row.disabledCount,
        formatDate(row.createdAt),
        formatDate(row.updatedAt),
      ]),
    );
    emit('notice', 'success', `已导出 ${formatNumber(records.length)} 个角色标识`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    exporting.value = false;
  }
}

async function listRoleMembers(roleCode: string) {
  const records: AdminOperatorRecord[] = [];
  for (let nextPageNo = 1; nextPageNo <= maxExportPages; nextPageNo += 1) {
    const nextPage = await listAdminOperators({
      roleCode,
      page: nextPageNo,
      pageSize: exportPageSize,
    });
    records.push(...nextPage.records);
    if (records.length >= nextPage.total || nextPage.records.length < exportPageSize) {
      break;
    }
  }
  return records;
}

async function downloadRoleMemberCsv(row: AdminOperatorRoleRecord) {
  memberExportingRole.value = row.roleCode;
  errorLine.value = '';
  try {
    const records = await listRoleMembers(row.roleCode);
    const headers = ['角色标识', '登录账号', '姓名', '状态', '创建时间', '更新时间'];
    downloadCsv(
      `operator-role-${row.roleCode}-members.csv`,
      headers,
      records.map((operator) => [
        row.roleCode,
        operator.username,
        operator.displayName,
        enabledText(operator.enabled),
        formatDate(operator.createdAt),
        formatDate(operator.updatedAt),
      ]),
    );
    emit('notice', 'success', `已导出 ${row.roleCode} 的 ${formatNumber(records.length)} 名成员`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    memberExportingRole.value = '';
  }
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshRoles() {
  loading.value = true;
  errorLine.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminOperatorRoles({
      keyword: keyword.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    rolePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个角色标识`);
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

function startRename(row: AdminOperatorRoleRecord) {
  renameForm.value = {
    oldRoleCode: row.roleCode,
    newRoleCode: row.roleCode,
  };
}

function resetRename() {
  renameForm.value = {
    oldRoleCode: '',
    newRoleCode: '',
  };
}

async function submitRename() {
  if (!canRename.value) {
    errorLine.value = '原角色标识和新角色标识不能为空';
    return;
  }
  const oldRoleCode = renameForm.value.oldRoleCode.trim();
  const newRoleCode = renameForm.value.newRoleCode.trim();
  saving.value = true;
  errorLine.value = '';
  try {
    await renameAdminOperatorRole(oldRoleCode, { roleCode: newRoleCode });
    emit('notice', 'success', `角色标识 ${oldRoleCode} 已重命名为 ${newRoleCode}`);
    resetRename();
    await refreshRoles();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
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
    if (active && !loaded.value) {
      void refreshRoles();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshRoles,
});
</script>

<template>
  <section class="legacy-page role-page">
    <ul class="legacy-search role-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-medium" placeholder="角色标识" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="exporting || loading || total === 0" @click="downloadRoleCsv">
          {{ exporting ? '导出中' : '导出角色' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats role-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>角色标识</span>
      </li>
      <li>
        <strong>{{ formatNumber(operatorTotal) }}</strong>
        <span>本页操作员</span>
      </li>
      <li>
        <strong>{{ formatNumber(enabledTotal) }}</strong>
        <span>本页启用</span>
      </li>
      <li>
        <strong>{{ formatNumber(disabledTotal) }}</strong>
        <span>本页停用</span>
      </li>
    </ul>

    <div class="role-edit legacy-panel">
      <div class="role-form-grid">
        <label>
          原角色标识
          <input v-model="renameForm.oldRoleCode" class="legacy-input" placeholder="AUDITOR" />
        </label>
        <label>
          新角色标识
          <input v-model="renameForm.newRoleCode" class="legacy-input" placeholder="AUDITOR" />
        </label>
        <div class="role-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving || !canRename" @click="submitRename">
            {{ saving ? '保存中' : '重命名角色' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetRename">清空</button>
        </div>
      </div>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table role-table">
        <thead>
          <tr class="legacy-main-head">
            <th>角色标识</th>
            <th>操作员数</th>
            <th>启用</th>
            <th>停用</th>
            <th>首次创建</th>
            <th>最近更新</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">正在查询角色标识</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">没有相关角色标识</td>
          </tr>
          <tr v-for="row in rows" :key="row.roleCode" class="legacy-main-info">
            <td><strong>{{ row.roleCode }}</strong></td>
            <td>{{ formatNumber(row.operatorCount) }}</td>
            <td>{{ formatNumber(row.enabledCount) }}</td>
            <td>{{ formatNumber(row.disabledCount) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <div class="role-row-actions">
                <button class="legacy-btn" type="button" :disabled="saving" @click="startRename(row)">重命名</button>
                <button
                  class="legacy-btn"
                  type="button"
                  :disabled="memberExportingRole === row.roleCode || row.operatorCount === 0"
                  @click="downloadRoleMemberCsv(row)"
                >
                  {{ memberExportingRole === row.roleCode ? '导出中' : '导出成员' }}
                </button>
              </div>
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
.role-search {
  row-gap: 10px;
}

.role-stats {
  margin-bottom: 10px;
}

.role-edit {
  margin-bottom: 10px;
  padding: 12px;
}

.role-form-grid {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) auto;
}

.role-form-grid label {
  color: #4b5563;
  display: grid;
  gap: 4px;
  font-size: 13px;
}

.role-actions {
  display: flex;
  gap: 8px;
}

.role-row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-table {
  min-width: 900px;
}

.role-table th,
.role-table td {
  min-width: 110px;
}

@media (max-width: 780px) {
  .role-form-grid {
    grid-template-columns: 1fr;
  }

  .role-actions {
    flex-wrap: wrap;
  }
}
</style>
