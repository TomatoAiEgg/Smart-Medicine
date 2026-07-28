<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { createAdminHerbIndex, listAdminHerbs, listAdminInstitutions } from '../../api/order';
import type { AdminHerbRecord, AdminInstitutionRecord } from '../../api/types';
import { formatNumber } from '../../domain/formatters';
import { csvCell, parseCsv, parseEnabled, type CsvRow } from './csvImport';

type NoticeTone = 'info' | 'success' | 'error';
type ImportStatus = 'SUCCESS' | 'FAILED';

interface ImportResult {
  rowNumber: number;
  institution: string;
  externalHerbCode: string;
  externalHerbName: string;
  herb: string;
  status: ImportStatus;
  message: string;
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const fileInput = ref<HTMLInputElement | null>(null);
const selectedFileName = ref('');
const rows = ref<CsvRow[]>([]);
const results = ref<ImportResult[]>([]);
const institutions = ref<AdminInstitutionRecord[]>([]);
const herbs = ref<AdminHerbRecord[]>([]);
const importing = ref(false);
const loadingOptions = ref(false);
const errorLine = ref('');
const loaded = ref(false);

const totalRows = computed(() => rows.value.length);
const successCount = computed(() => results.value.filter((row) => row.status === 'SUCCESS').length);
const failedCount = computed(() => results.value.filter((row) => row.status === 'FAILED').length);
const canImport = computed(() => rows.value.length > 0 && !importing.value && !loadingOptions.value);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function normalize(value: string) {
  return value.trim().toLowerCase();
}

function requiredCell(row: CsvRow, aliases: readonly string[], label: string) {
  const value = csvCell(row, aliases);
  if (!value) {
    throw new Error(`${label}不能为空`);
  }
  return value;
}

function findInstitution(row: CsvRow) {
  const id = csvCell(row, ['institutionId', '机构ID']);
  const code = csvCell(row, ['institutionCode', '机构编码']);
  const name = csvCell(row, ['institutionName', '机构名称']);
  const matched = institutions.value.find((item) => {
    return (
      (id && item.id === id) ||
      (code && normalize(item.institutionCode) === normalize(code)) ||
      (name && normalize(item.institutionName) === normalize(name))
    );
  });
  if (!matched) {
    throw new Error('未匹配到机构，请检查 institutionId/institutionCode/institutionName');
  }
  return matched;
}

function findHerb(row: CsvRow) {
  const id = csvCell(row, ['herbId', '平台药品ID', '药品ID']);
  const code = csvCell(row, ['herbCode', '平台药品编码', '药品编码']);
  const name = csvCell(row, ['herbName', '平台药品名称', '药品名称']);
  const matched = herbs.value.find((item) => {
    return (
      (id && item.id === id) ||
      (code && normalize(item.herbCode) === normalize(code)) ||
      (name && normalize(item.herbName) === normalize(name))
    );
  });
  if (!matched) {
    throw new Error('未匹配到平台药品，请检查 herbId/herbCode/herbName');
  }
  return matched;
}

async function refreshOptions() {
  loadingOptions.value = true;
  errorLine.value = '';
  try {
    const [nextInstitutions, nextHerbs] = await Promise.all([
      listAdminInstitutions({ page: 1, pageSize: 100 }),
      listAdminHerbs({ page: 1, pageSize: 100, enabled: true }),
    ]);
    institutions.value = nextInstitutions.records;
    herbs.value = nextHerbs.records;
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    loadingOptions.value = false;
  }
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0] ?? null;
  results.value = [];
  errorLine.value = '';
  selectedFileName.value = file?.name ?? '';
  if (!file) {
    rows.value = [];
    return;
  }
  try {
    rows.value = parseCsv(await file.text());
    emit('countChanged', rows.value.length);
    emit('notice', 'success', `已读取 ${formatNumber(rows.value.length)} 行药品索引`);
  } catch (error) {
    rows.value = [];
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  }
}

async function importIndexes() {
  if (!canImport.value) return;
  importing.value = true;
  errorLine.value = '';
  results.value = [];
  await refreshOptions();
  if (errorLine.value) {
    importing.value = false;
    return;
  }

  for (const row of rows.value) {
    const externalHerbCode = csvCell(row, ['externalHerbCode', '机构药品编码', '院内药品编码']);
    const externalHerbName = csvCell(row, ['externalHerbName', '机构药品名称', '院内药品名称']);
    const institutionLabel = csvCell(row, ['institutionCode', '机构编码']) || csvCell(row, ['institutionName', '机构名称']);
    const herbLabel = csvCell(row, ['herbCode', '平台药品编码', '药品编码']) || csvCell(row, ['herbName', '平台药品名称', '药品名称']);
    try {
      const institution = findInstitution(row);
      const herb = findHerb(row);
      await createAdminHerbIndex({
        institutionId: institution.id,
        externalHerbCode: requiredCell(row, ['externalHerbCode', '机构药品编码', '院内药品编码'], '机构药品编码'),
        externalHerbName: requiredCell(row, ['externalHerbName', '机构药品名称', '院内药品名称'], '机构药品名称'),
        herbId: herb.id,
        matchType: csvCell(row, ['matchType', '匹配类型']) || 'IMPORT',
        enabled: parseEnabled(csvCell(row, ['enabled', '状态', '启用'])),
        remark: csvCell(row, ['remark', '备注']),
      });
      results.value.push({
        rowNumber: row.rowNumber,
        institution: institutionLabel || institution.institutionName,
        externalHerbCode,
        externalHerbName,
        herb: herbLabel || herb.herbName,
        status: 'SUCCESS',
        message: '导入成功',
      });
    } catch (error) {
      results.value.push({
        rowNumber: row.rowNumber,
        institution: institutionLabel,
        externalHerbCode,
        externalHerbName,
        herb: herbLabel,
        status: 'FAILED',
        message: errorMessage(error),
      });
    }
  }
  importing.value = false;
  emit('countChanged', successCount.value);
  emit('notice', failedCount.value ? 'error' : 'success', `药品索引导入完成：成功 ${successCount.value} 行，失败 ${failedCount.value} 行`);
}

function resetImport() {
  rows.value = [];
  results.value = [];
  selectedFileName.value = '';
  errorLine.value = '';
  if (fileInput.value) {
    fileInput.value.value = '';
  }
  emit('countChanged', 0);
}

function downloadTemplate() {
  const content = [
    'institutionCode,institutionName,externalHerbCode,externalHerbName,herbCode,herbName,matchType,enabled,remark',
    'HOSP001,示例机构,EXT001,院内药品,HERB001,平台药品,IMPORT,true,示例',
  ].join('\n');
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'herb-index-import-template.csv';
  link.click();
  URL.revokeObjectURL(url);
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      loaded.value = true;
      void refreshOptions();
      emit('countChanged', rows.value.length);
    }
  },
  { immediate: true },
);

defineExpose({
  resetImport,
});
</script>

<template>
  <section class="legacy-page herb-index-import-page">
    <ul class="legacy-search herb-index-import-actions">
      <li>
        CSV 文件：
        <input ref="fileInput" class="legacy-input input-large" type="file" accept=".csv,text/csv" @change="handleFileChange" />
      </li>
      <li>
        <button class="legacy-btn" type="button" @click="downloadTemplate">下载模板</button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="!canImport" @click="importIndexes">
          {{ importing ? '导入中' : loadingOptions ? '加载基础数据' : '开始导入' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="importing" @click="resetImport">清空</button>
      </li>
    </ul>

    <p v-if="selectedFileName" class="legacy-hint">当前文件：{{ selectedFileName }}</p>
    <p class="legacy-hint">已加载 {{ formatNumber(institutions.length) }} 家机构、{{ formatNumber(herbs.length) }} 个启用药品用于匹配。</p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats herb-index-import-stats">
      <li>
        <strong>{{ formatNumber(totalRows) }}</strong>
        <span>待导入行</span>
      </li>
      <li>
        <strong>{{ formatNumber(successCount) }}</strong>
        <span>成功</span>
      </li>
      <li>
        <strong>{{ formatNumber(failedCount) }}</strong>
        <span>失败</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table herb-index-import-table">
        <thead>
          <tr class="legacy-main-head">
            <th>行号</th>
            <th>机构</th>
            <th>机构药品编码</th>
            <th>机构药品名称</th>
            <th>平台药品</th>
            <th>状态</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="results.length === 0" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">选择 CSV 后执行导入，结果会显示在这里</td>
          </tr>
          <tr v-for="row in results" :key="row.rowNumber" class="legacy-main-info">
            <td>{{ row.rowNumber }}</td>
            <td>{{ row.institution || '-' }}</td>
            <td><strong>{{ row.externalHerbCode || '-' }}</strong></td>
            <td>{{ row.externalHerbName || '-' }}</td>
            <td>{{ row.herb || '-' }}</td>
            <td>
              <span :class="['import-status', row.status === 'SUCCESS' ? 'is-success' : 'is-failed']">
                {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
            <td>{{ row.message }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.herb-index-import-actions {
  row-gap: 10px;
}

.herb-index-import-stats {
  margin-bottom: 10px;
}

.herb-index-import-table {
  min-width: 980px;
}

.herb-index-import-table th,
.herb-index-import-table td {
  min-width: 110px;
}

.import-status {
  border-radius: 4px;
  display: inline-block;
  font-weight: 700;
  padding: 3px 8px;
}

.import-status.is-success {
  background: #dcfce7;
  color: #166534;
}

.import-status.is-failed {
  background: #fee2e2;
  color: #991b1b;
}
</style>
