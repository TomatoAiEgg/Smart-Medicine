<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { createAdminHerb, listAdminHerbs, updateAdminHerb } from '../../api/order';
import type { AdminHerbPage, AdminHerbRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { enabledStringParam, enabledText, displayValue, formatDate, formatNumber } from '../../domain/formatters';

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

const keyword = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const herbPage = ref<AdminHerbPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref({
  id: '',
  herbCode: '',
  herbName: '',
  drugSpecs: '',
  drugOrigin: '',
  unit: '',
  retailPrice: '',
  enabled: true,
  remark: '',
});

const rows = computed(() => herbPage.value?.records ?? []);
const total = computed(() => herbPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== '');

function downloadHerbCsv() {
  downloadCsv(
    `药品目录-第${page.value}页.csv`,
    ['药品编码', '药品名称', '规格', '产地', '单位', '零售价', '状态', '备注', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.herbCode,
      row.herbName,
      row.drugSpecs,
      row.drugOrigin,
      row.unit,
      row.retailPrice,
      enabledText(row.enabled),
      row.remark,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个药品`);
}

function formatPrice(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) return String(value);
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(numericValue);
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

function resetForm() {
  form.value = {
    id: '',
    herbCode: '',
    herbName: '',
    drugSpecs: '',
    drugOrigin: '',
    unit: '',
    retailPrice: '',
    enabled: true,
    remark: '',
  };
}

function editHerb(row: AdminHerbRecord) {
  form.value = {
    id: row.id,
    herbCode: row.herbCode,
    herbName: row.herbName,
    drugSpecs: row.drugSpecs ?? '',
    drugOrigin: row.drugOrigin ?? '',
    unit: row.unit ?? '',
    retailPrice: String(row.retailPrice ?? ''),
    enabled: row.enabled,
    remark: row.remark ?? '',
  };
}

async function refreshHerbs() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminHerbs({
      keyword: keyword.value,
      enabled: enabledStringParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    herbPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新药品目录：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    herbPage.value = null;
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshHerbs();
}

async function saveHerb() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      herbCode: form.value.herbCode.trim(),
      herbName: form.value.herbName.trim(),
      drugSpecs: form.value.drugSpecs.trim(),
      drugOrigin: form.value.drugOrigin.trim(),
      unit: form.value.unit.trim(),
      retailPrice: form.value.retailPrice === '' ? undefined : form.value.retailPrice,
      enabled: form.value.enabled,
      remark: form.value.remark.trim(),
    };
    const saved = editing.value ? await updateAdminHerb(form.value.id, command) : await createAdminHerb(command);
    emit('notice', 'success', `${saved.herbName} 已保存`);
    resetForm();
    await refreshHerbs();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleHerb(row: AdminHerbRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminHerb(row.id, {
      herbName: row.herbName,
      drugSpecs: row.drugSpecs ?? '',
      drugOrigin: row.drugOrigin ?? '',
      unit: row.unit ?? '',
      retailPrice: row.retailPrice,
      enabled: !row.enabled,
      remark: row.remark ?? '',
    });
    emit('notice', 'success', `${row.herbName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshHerbs();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshHerbs();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshHerbs();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshHerbs();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshHerbs,
});
</script>

<template>
  <section class="legacy-page herb-list-page">
    <ul class="legacy-search herb-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadHerbCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <ul class="legacy-stats herb-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>药品总数</span>
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

    <section class="legacy-panel herb-form-panel">
      <div class="legacy-panel-title">{{ editing ? '编辑药品' : '新增药品' }}</div>
      <div class="herb-form-grid">
        <label>
          药品编码
          <input v-model="form.herbCode" class="legacy-input" :disabled="editing || saving" />
        </label>
        <label>
          药品名称
          <input v-model="form.herbName" class="legacy-input" :disabled="saving" />
        </label>
        <label class="enabled-field">
          <input v-model="form.enabled" type="checkbox" :disabled="saving" />
          启用
        </label>
        <label>
          规格
          <input v-model="form.drugSpecs" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          产地
          <input v-model="form.drugOrigin" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          单位
          <input v-model="form.unit" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          零售价
          <input v-model="form.retailPrice" class="legacy-input" inputmode="decimal" :disabled="saving" />
        </label>
        <label class="remark-field">
          备注
          <input v-model="form.remark" class="legacy-input" :disabled="saving" />
        </label>
      </div>
      <div class="herb-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveHerb">
          {{ editing ? '保存药品' : '新增药品' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </section>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>药品编码</th>
            <th>药品名称</th>
            <th>规格</th>
            <th>产地</th>
            <th>单位</th>
            <th>零售价</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="9" class="empty-cell">暂无药品目录</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.herbCode }}</td>
            <td class="name-cell">{{ row.herbName }}</td>
            <td>{{ displayValue(row.drugSpecs) }}</td>
            <td>{{ displayValue(row.drugOrigin) }}</td>
            <td>{{ displayValue(row.unit) }}</td>
            <td>{{ formatPrice(row.retailPrice) }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editHerb(row)">编辑</button>
              <button class="legacy-link-btn" type="button" :disabled="saving" @click="toggleHerb(row)">
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
.herb-search {
  align-items: center;
}

.herb-stats {
  margin-bottom: 16px;
}

.herb-form-panel {
  margin-bottom: 16px;
}

.herb-form-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.herb-form-grid label {
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
  grid-column: span 3;
}

.herb-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.name-cell {
  min-width: 140px;
  font-weight: 600;
}

.action-cell {
  white-space: nowrap;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

@media (max-width: 1100px) {
  .herb-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .remark-field {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .herb-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
