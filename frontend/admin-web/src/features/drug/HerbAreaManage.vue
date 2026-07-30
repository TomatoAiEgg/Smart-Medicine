<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { createAdminHerbArea, listAdminHerbAreas, updateAdminHerbArea } from '../../api/order';
import type { AdminHerbAreaPage, AdminHerbAreaRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, enabledStringParam, enabledText, displayValue, formatDate, formatNumber } from '../../domain/formatters';

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
const areaPage = ref<AdminHerbAreaPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref({
  id: '',
  areaCode: '',
  areaName: '',
  enabled: true,
  remark: '',
});

const rows = computed(() => areaPage.value?.records ?? []);
const total = computed(() => areaPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== '');

function downloadAreaCsv() {
  downloadCsv(
    `药材区域-第${page.value}页.csv`,
    ['区域编码', '区域名称', '状态', '备注', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.areaCode,
      row.areaName,
      enabledText(row.enabled),
      row.remark,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个药材区域`);
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function resetForm() {
  form.value = {
    id: '',
    areaCode: '',
    areaName: '',
    enabled: true,
    remark: '',
  };
}

function editArea(row: AdminHerbAreaRecord) {
  form.value = {
    id: row.id,
    areaCode: row.areaCode,
    areaName: row.areaName,
    enabled: row.enabled,
    remark: row.remark ?? '',
  };
}

async function refreshHerbAreas() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminHerbAreas({
      keyword: keyword.value,
      enabled: enabledStringParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    areaPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新药材区域：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    areaPage.value = null;
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshHerbAreas();
}

async function saveArea() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      areaCode: form.value.areaCode.trim(),
      areaName: form.value.areaName.trim(),
      enabled: form.value.enabled,
      remark: form.value.remark.trim(),
    };
    const saved = editing.value
      ? await updateAdminHerbArea(form.value.id, command)
      : await createAdminHerbArea(command);
    emit('notice', 'success', `${saved.areaName} 已保存`);
    resetForm();
    await refreshHerbAreas();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleArea(row: AdminHerbAreaRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminHerbArea(row.id, {
      areaName: row.areaName,
      enabled: !row.enabled,
      remark: row.remark ?? '',
    });
    emit('notice', 'success', `${row.areaName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshHerbAreas();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshHerbAreas();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshHerbAreas();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshHerbAreas();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshHerbAreas,
});
</script>

<template>
  <section class="legacy-page herb-area-page">
    <ul class="legacy-search herb-area-search">
      <li>
        关键词：
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadAreaCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <ul class="legacy-stats herb-area-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>区域总数</span>
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

    <section class="legacy-panel herb-area-form-panel">
      <div class="legacy-panel-title">{{ editing ? '编辑药材区域' : '新增药材区域' }}</div>
      <div class="herb-area-form-grid">
        <label>
          区域编码
          <input v-model="form.areaCode" class="legacy-input" :disabled="editing || saving" />
        </label>
        <label>
          区域名称
          <input v-model="form.areaName" class="legacy-input" :disabled="saving" />
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
      <div class="herb-area-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveArea">
          {{ editing ? '保存区域' : '新增区域' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </section>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>区域编码</th>
            <th>区域名称</th>
            <th>状态</th>
            <th>备注</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="6" class="empty-cell">暂无药材区域</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.areaCode }}</td>
            <td>{{ row.areaName }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td class="remark-cell">{{ displayValue(row.remark) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editArea(row)">编辑</button>
              <button class="legacy-link-btn" type="button" :disabled="saving" @click="toggleArea(row)">
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
.herb-area-search {
  align-items: center;
}

.herb-area-stats {
  margin-bottom: 16px;
}

.herb-area-form-panel {
  margin-bottom: 16px;
}

.herb-area-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.herb-area-form-grid label {
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

.herb-area-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.remark-cell {
  max-width: 420px;
  white-space: normal;
  word-break: break-word;
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
  .herb-area-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
