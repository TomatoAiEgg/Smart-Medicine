<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminHerbIndex,
  listAdminHerbIndexes,
  listAdminHerbs,
  listAdminInstitutions,
  updateAdminHerbIndex,
} from '../../api/order';
import type {
  AdminHerbIndexPage,
  AdminHerbIndexRecord,
  AdminHerbRecord,
  AdminInstitutionRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const matchTypes = [
  { value: 'MANUAL', label: '手工维护' },
  { value: 'IMPORT', label: '导入生成' },
  { value: 'AUTO', label: '自动匹配' },
] as const;

const keyword = ref('');
const institutionFilter = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const indexPage = ref<AdminHerbIndexPage | null>(null);
const institutions = ref<AdminInstitutionRecord[]>([]);
const herbs = ref<AdminHerbRecord[]>([]);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref({
  id: '',
  institutionId: '',
  externalHerbCode: '',
  externalHerbName: '',
  herbId: '',
  matchType: 'MANUAL',
  enabled: true,
  remark: '',
});

const rows = computed(() => indexPage.value?.records ?? []);
const total = computed(() => indexPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== '');

function queryEnabled() {
  return enabledFilter.value === '' ? undefined : enabledFilter.value;
}

function enabledText(value: boolean) {
  return value ? '启用' : '停用';
}

function matchTypeText(value: string) {
  return matchTypes.find((option) => option.value === value)?.label ?? value;
}

function downloadIndexCsv() {
  downloadCsv(
    `药品索引-第${page.value}页.csv`,
    [
      '机构编码',
      '机构名称',
      '机构药品编码',
      '机构药品名称',
      '平台药品编码',
      '平台药品名称',
      '匹配类型',
      '状态',
      '备注',
      '创建时间',
      '更新时间',
    ],
    rows.value.map((row) => [
      row.institutionCode,
      row.institutionName,
      row.externalHerbCode,
      row.externalHerbName,
      row.herbCode,
      row.herbName,
      matchTypeText(row.matchType),
      enabledText(row.enabled),
      row.remark,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条药品索引`);
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

function resetForm() {
  form.value = {
    id: '',
    institutionId: '',
    externalHerbCode: '',
    externalHerbName: '',
    herbId: '',
    matchType: 'MANUAL',
    enabled: true,
    remark: '',
  };
}

function editIndex(row: AdminHerbIndexRecord) {
  form.value = {
    id: row.id,
    institutionId: row.institutionId,
    externalHerbCode: row.externalHerbCode,
    externalHerbName: row.externalHerbName,
    herbId: row.herbId,
    matchType: row.matchType,
    enabled: row.enabled,
    remark: row.remark ?? '',
  };
}

async function refreshOptions() {
  const [nextInstitutions, nextHerbs] = await Promise.all([
    listAdminInstitutions({ page: 1, pageSize: 100 }),
    listAdminHerbs({ page: 1, pageSize: 100, enabled: true }),
  ]);
  institutions.value = nextInstitutions.records;
  herbs.value = nextHerbs.records;
}

async function refreshHerbIndexes() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    await refreshOptions();
    const nextPage = await listAdminHerbIndexes({
      keyword: keyword.value,
      institutionId: institutionFilter.value,
      enabled: queryEnabled(),
      page: page.value,
      pageSize: pageSize.value,
    });
    indexPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新药品索引：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    indexPage.value = null;
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshHerbIndexes();
}

async function saveIndex() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      institutionId: form.value.institutionId,
      externalHerbCode: form.value.externalHerbCode.trim(),
      externalHerbName: form.value.externalHerbName.trim(),
      herbId: form.value.herbId,
      matchType: form.value.matchType,
      enabled: form.value.enabled,
      remark: form.value.remark.trim(),
    };
    const saved = editing.value
      ? await updateAdminHerbIndex(form.value.id, command)
      : await createAdminHerbIndex(command);
    emit('notice', 'success', `${saved.externalHerbName} 已保存索引`);
    resetForm();
    await refreshHerbIndexes();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleIndex(row: AdminHerbIndexRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminHerbIndex(row.id, {
      externalHerbName: row.externalHerbName,
      herbId: row.herbId,
      matchType: row.matchType,
      enabled: !row.enabled,
      remark: row.remark ?? '',
    });
    emit('notice', 'success', `${row.externalHerbName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshHerbIndexes();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshHerbIndexes();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshHerbIndexes();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshHerbIndexes();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshHerbIndexes,
});
</script>

<template>
  <section class="legacy-page herb-index-page">
    <ul class="legacy-search herb-index-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构：
        <select v-model="institutionFilter" class="legacy-input input-large" @change="searchFirstPage">
          <option value="">全部机构</option>
          <option v-for="institution in institutions" :key="institution.id" :value="institution.id">
            {{ institution.institutionName }}
          </option>
        </select>
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
          查询
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadIndexCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <ul class="legacy-stats herb-index-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>索引总数</span>
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

    <section class="legacy-panel herb-index-form-panel">
      <div class="legacy-panel-title">{{ editing ? '编辑药品索引' : '新增药品索引' }}</div>
      <div class="herb-index-form-grid">
        <label>
          机构
          <select v-model="form.institutionId" class="legacy-input" :disabled="editing || saving">
            <option value="">请选择机构</option>
            <option v-for="institution in institutions" :key="institution.id" :value="institution.id">
              {{ institution.institutionName }}
            </option>
          </select>
        </label>
        <label>
          机构药品编码
          <input v-model="form.externalHerbCode" class="legacy-input" :disabled="editing || saving" />
        </label>
        <label>
          机构药品名称
          <input v-model="form.externalHerbName" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          平台药品
          <select v-model="form.herbId" class="legacy-input" :disabled="saving">
            <option value="">请选择平台药品</option>
            <option v-for="herb in herbs" :key="herb.id" :value="herb.id">
              {{ herb.herbName }}（{{ herb.herbCode }}）
            </option>
          </select>
        </label>
        <label>
          匹配类型
          <select v-model="form.matchType" class="legacy-input" :disabled="saving">
            <option v-for="option in matchTypes" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="enabled-field">
          <input v-model="form.enabled" type="checkbox" :disabled="saving" />
          启用
        </label>
        <label class="remark-field">
          备注
          <input v-model="form.remark" class="legacy-input" :disabled="saving" />
        </label>
      </div>
      <div class="herb-index-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveIndex">
          {{ editing ? '保存索引' : '新增索引' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </section>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>机构</th>
            <th>机构药品编码</th>
            <th>机构药品名称</th>
            <th>平台药品编码</th>
            <th>平台药品名称</th>
            <th>匹配类型</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="9" class="empty-cell">暂无药品索引</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>
              <strong>{{ row.institutionName }}</strong>
              <small>{{ row.institutionCode }}</small>
            </td>
            <td>{{ row.externalHerbCode }}</td>
            <td>{{ row.externalHerbName }}</td>
            <td>{{ row.herbCode }}</td>
            <td>{{ row.herbName }}</td>
            <td>{{ matchTypeText(row.matchType) }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editIndex(row)">编辑</button>
              <button class="legacy-link-btn" type="button" :disabled="saving" @click="toggleIndex(row)">
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
      <label>
        每页
        <input
          v-model.number="pageSize"
          class="legacy-input input-small"
          type="number"
          min="1"
          max="100"
          @keyup.enter="searchFirstPage"
        />
      </label>
    </div>
  </section>
</template>

<style scoped>
.herb-index-search {
  align-items: center;
}

.herb-index-stats {
  margin-bottom: 16px;
}

.herb-index-form-panel {
  margin-bottom: 16px;
}

.herb-index-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.herb-index-form-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.enabled-field {
  grid-template-columns: auto 1fr;
  align-items: center;
  align-content: end;
}

.remark-field {
  grid-column: 1 / -1;
}

.herb-index-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

small {
  display: block;
  color: #64748b;
}

.action-cell {
  white-space: nowrap;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

@media (max-width: 920px) {
  .herb-index-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
