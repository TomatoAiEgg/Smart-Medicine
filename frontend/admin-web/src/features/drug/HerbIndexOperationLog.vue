<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { listAdminHerbIndexOperationLogs, listAdminInstitutions } from '../../api/order';
import type {
  AdminHerbIndexOperationLogPage,
  AdminInstitutionRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const actionTypes = [
  { value: '', label: '全部' },
  { value: 'CREATED', label: '新增' },
  { value: 'UPDATED', label: '编辑' },
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' },
] as const;

const keyword = ref('');
const institutionFilter = ref('');
const actionType = ref('');
const page = ref(1);
const pageSize = ref(20);
const logPage = ref<AdminHerbIndexOperationLogPage | null>(null);
const institutions = ref<AdminInstitutionRecord[]>([]);
const loading = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const rows = computed(() => logPage.value?.records ?? []);
const total = computed(() => logPage.value?.total ?? 0);
const createdCount = computed(() => rows.value.filter((row) => row.actionType === 'CREATED').length);
const changedCount = computed(() => rows.value.filter((row) => row.actionType !== 'CREATED').length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function actionText(value: string) {
  return actionTypes.find((option) => option.value === value)?.label ?? value;
}

function downloadLogCsv() {
  downloadCsv(
    `药品索引操作日志-第${page.value}页.csv`,
    [
      '操作时间',
      '动作',
      '机构编码',
      '机构名称',
      '机构药品编码',
      '机构药品名称',
      '平台药品编码',
      '平台药品名称',
      '操作人',
      '备注',
    ],
    rows.value.map((row) => [
      formatDate(row.createdAt),
      actionText(row.actionType),
      row.institutionCode,
      row.institutionName,
      row.externalHerbCode,
      row.externalHerbName,
      row.herbCode,
      row.herbName,
      row.operator,
      row.remark,
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条药品索引日志`);
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

async function refreshOptions() {
  const nextInstitutions = await listAdminInstitutions({ page: 1, pageSize: 100 });
  institutions.value = nextInstitutions.records;
}

async function refreshHerbIndexOperationLogs() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    await refreshOptions();
    const nextPage = await listAdminHerbIndexOperationLogs({
      keyword: keyword.value,
      institutionId: institutionFilter.value,
      actionType: actionType.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    logPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新药品索引日志：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    logPage.value = null;
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshHerbIndexOperationLogs();
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshHerbIndexOperationLogs();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshHerbIndexOperationLogs();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshHerbIndexOperationLogs();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshHerbIndexOperationLogs,
});
</script>

<template>
  <section class="legacy-page herb-index-log-page">
    <ul class="legacy-search herb-index-log-search">
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
        动作：
        <select v-model="actionType" class="legacy-input input-small" @change="searchFirstPage">
          <option v-for="option in actionTypes" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          查询
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadLogCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <ul class="legacy-stats herb-index-log-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>日志总数</span>
      </li>
      <li>
        <strong>{{ formatNumber(createdCount) }}</strong>
        <span>本页新增</span>
      </li>
      <li>
        <strong>{{ formatNumber(changedCount) }}</strong>
        <span>本页变更</span>
      </li>
    </ul>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>操作时间</th>
            <th>动作</th>
            <th>机构</th>
            <th>机构药品</th>
            <th>平台药品</th>
            <th>操作人</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="7" class="empty-cell">暂无药品索引操作日志</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ actionText(row.actionType) }}</td>
            <td>
              <strong>{{ row.institutionName }}</strong>
              <small>{{ row.institutionCode }}</small>
            </td>
            <td>
              <strong>{{ row.externalHerbName }}</strong>
              <small>{{ row.externalHerbCode }}</small>
            </td>
            <td>
              <strong>{{ row.herbName }}</strong>
              <small>{{ row.herbCode }}</small>
            </td>
            <td>{{ row.operator }}</td>
            <td class="remark-cell">{{ displayValue(row.remark) }}</td>
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
.herb-index-log-search {
  align-items: center;
}

.herb-index-log-stats {
  margin-bottom: 16px;
}

small {
  display: block;
  color: #64748b;
}

.remark-cell {
  max-width: 320px;
  white-space: normal;
  word-break: break-word;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}
</style>
