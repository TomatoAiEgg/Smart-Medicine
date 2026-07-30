<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { createAdminInstitution, listAdminInstitutions, updateAdminInstitution } from '../../api/order';
import type { AdminInstitutionCommand, AdminInstitutionPage, AdminInstitutionRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { displayValue, currentIsoDate, formatDate, formatNumber, labelFromMap } from '../../domain/formatters';

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
const loaded = ref(false);
const errorLine = ref('');
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

async function refreshInstitutions() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminInstitutions({
      keyword: keyword.value,
      status: status.value,
      institutionType: institutionType.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    institutionPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 个机构`);
  } catch (error) {
    institutionPage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshInstitutions();
}

function resetForm() {
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
  if (!form.value.institutionCode.trim() || !form.value.institutionName.trim()) {
    errorLine.value = '机构编码和机构名称不能为空';
    return;
  }
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleInstitution(row: AdminInstitutionRecord) {
  saving.value = true;
  errorLine.value = '';
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
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshInstitutions();
}

async function nextPage() {
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
  <section class="legacy-page institution-page">
    <ul class="legacy-search institution-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-medium" placeholder="编码 / 名称 / 煎煮中心" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        状态：
        <select v-model="status" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="ENABLED">启用</option>
          <option value="DISABLED">停用</option>
        </select>
      </li>
      <li>
        类型：
        <select v-model="institutionType" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="HOSPITAL">医院</option>
          <option value="PHARMACY">药房</option>
          <option value="PLATFORM">平台</option>
        </select>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadInstitutionCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats institution-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>机构总数</span>
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

    <div class="institution-edit legacy-panel">
      <div class="institution-form-grid">
        <label>
          机构编码
          <input v-model="form.institutionCode" class="legacy-input" :disabled="editing" placeholder="hospital-code" />
        </label>
        <label>
          机构名称
          <input v-model="form.institutionName" class="legacy-input" placeholder="机构名称" />
        </label>
        <label>
          类型
          <select v-model="form.institutionType" class="legacy-input">
            <option value="HOSPITAL">医院</option>
            <option value="PHARMACY">药房</option>
            <option value="PLATFORM">平台</option>
          </select>
        </label>
        <label>
          状态
          <select v-model="form.status" class="legacy-input">
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <label>
          煎煮中心
          <input v-model="form.storageType" class="legacy-input" placeholder="中心/仓储标识" />
        </label>
        <div class="institution-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveInstitution">
            {{ saving ? '保存中' : editing ? '保存修改' : '新增机构' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </div>
    </div>

    <div class="legacy-panel">
      <table class="legacy-main-table institution-table">
        <thead>
          <tr class="legacy-main-head">
            <th>机构编码</th>
            <th>机构名称</th>
            <th>类型</th>
            <th>状态</th>
            <th>煎煮中心</th>
            <th>创建时间</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">正在查询机构</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">没有相关机构</td>
          </tr>
          <tr v-for="row in rows" :key="row.id" class="legacy-main-info">
            <td><strong>{{ displayValue(row.institutionCode) }}</strong></td>
            <td class="legacy-left">{{ displayValue(row.institutionName) }}</td>
            <td>{{ typeLabel(row.institutionType) }}</td>
            <td>{{ statusLabel(row.status) }}</td>
            <td>{{ displayValue(row.storageType) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td>
              <button class="legacy-btn" type="button" :disabled="saving" @click="editInstitution(row)">编辑</button>
              <button class="legacy-btn" type="button" :disabled="saving" @click="toggleInstitution(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
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
.institution-search {
  row-gap: 10px;
}

.institution-stats {
  margin-bottom: 10px;
}

.institution-edit {
  margin-bottom: 10px;
  padding: 12px;
}

.institution-form-grid {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(140px, 1fr) minmax(180px, 1.2fr) 120px 110px minmax(150px, 1fr) auto;
}

.institution-form-grid label {
  color: #4b5563;
  display: grid;
  gap: 4px;
  font-size: 13px;
}

.institution-actions {
  display: flex;
  gap: 8px;
}

.institution-table {
  min-width: 1120px;
}

.institution-table th,
.institution-table td {
  min-width: 110px;
}

@media (max-width: 1100px) {
  .institution-form-grid {
    grid-template-columns: 1fr;
  }

  .institution-actions {
    flex-wrap: wrap;
  }
}
</style>
