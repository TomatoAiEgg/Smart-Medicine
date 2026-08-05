<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { createAdminOperator, listAdminOperators, listAdminRbacRoles, updateAdminOperator } from '../../api/order';
import type { AdminOperatorCommand, AdminOperatorPage, AdminOperatorRecord, AdminRbacRoleRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
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
const roleNameByCode = computed(() => new Map(roleOptions.value.map((role) => [role.roleCode, role.roleName])));

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
  <section class="legacy-page operator-page">
    <ul class="legacy-search operator-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-medium" placeholder="工号 / 姓名 / 角色" @keyup.enter="searchFirstPage" />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadOperatorCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats operator-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>工号总数</span>
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

    <div class="operator-edit legacy-panel">
      <div class="operator-form-grid">
        <label>
          工号
          <input v-model="form.username" class="legacy-input" :disabled="editing || !canManage" placeholder="operator01" />
        </label>
        <label>
          姓名
          <input v-model="form.displayName" class="legacy-input" :disabled="!canManage" placeholder="操作员姓名" />
        </label>
        <label>
          角色
          <select v-model="form.roleCode" class="legacy-input" :disabled="!canManage">
            <option value="">未分配角色</option>
            <option v-for="role in roleOptions" :key="role.id" :value="role.roleCode" :disabled="!role.enabled">
              {{ role.roleName }}（{{ role.roleCode }}）
            </option>
          </select>
        </label>
        <label class="operator-enabled">
          <input v-model="form.enabled" type="checkbox" :disabled="!canManage" />
          启用
        </label>
        <div class="operator-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving || !canManage" @click="saveOperator">
            {{ saving ? '保存中' : editing ? '保存修改' : '新增工号' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </div>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table operator-table">
        <thead>
          <tr class="legacy-main-head">
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
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">正在查询工号</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">没有相关工号</td>
          </tr>
          <tr v-for="row in rows" :key="row.id" class="legacy-main-info">
            <td><strong>{{ displayValue(row.username) }}</strong></td>
            <td>{{ displayValue(row.displayName) }}</td>
            <td>{{ row.roleCode ? `${roleNameByCode.get(row.roleCode) ?? row.roleCode}（${row.roleCode}）` : '-' }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-btn" type="button" :disabled="saving || !canManage" @click="editOperator(row)">编辑</button>
              <button class="legacy-btn" type="button" :disabled="saving || !canManage" @click="toggleOperator(row)">
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
.operator-search {
  row-gap: 10px;
}

.operator-stats {
  margin-bottom: 10px;
}

.operator-edit {
  margin-bottom: 10px;
  padding: 12px;
}

.operator-form-grid {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(140px, 1fr) minmax(140px, 1fr) minmax(160px, 1fr) 86px auto;
}

.operator-form-grid label {
  color: #4b5563;
  display: grid;
  gap: 4px;
  font-size: 13px;
}

.operator-enabled {
  align-items: center;
  display: flex !important;
  gap: 6px !important;
  min-height: 34px;
}

.operator-actions {
  display: flex;
  gap: 8px;
}

.operator-table {
  min-width: 960px;
}

.operator-table th,
.operator-table td {
  min-width: 110px;
}

@media (max-width: 980px) {
  .operator-form-grid {
    grid-template-columns: 1fr;
  }

  .operator-actions {
    flex-wrap: wrap;
  }
}
</style>
