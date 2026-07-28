<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { createAdminHerb, listAdminHerbs, updateAdminHerb } from '../../api/order';
import type { AdminHerbCommand } from '../../api/types';
import { formatNumber } from '../../domain/formatters';
import { csvCell, downloadCsv, parseCsv, parseEnabled, type CsvRow } from './csvImport';

type NoticeTone = 'info' | 'success' | 'error';
type ImportStatus = 'SUCCESS' | 'FAILED';

interface ImportResult {
  rowNumber: number;
  herbCode: string;
  herbName: string;
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
const importing = ref(false);
const errorLine = ref('');
const loaded = ref(false);
const overwriteExisting = ref(false);

const totalRows = computed(() => rows.value.length);
const successCount = computed(() => results.value.filter((row) => row.status === 'SUCCESS').length);
const failedCount = computed(() => results.value.filter((row) => row.status === 'FAILED').length);
const failedResults = computed(() => results.value.filter((row) => row.status === 'FAILED'));
const canImport = computed(() => rows.value.length > 0 && !importing.value);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function requiredCell(row: CsvRow, aliases: readonly string[], label: string) {
  const value = csvCell(row, aliases);
  if (!value) {
    throw new Error(`${label}不能为空`);
  }
  return value;
}

function optionalNumber(value: string) {
  if (!value) return undefined;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new Error('零售价必须是数字');
  }
  return parsed;
}

async function findExistingHerb(herbCode: string) {
  const page = await listAdminHerbs({ keyword: herbCode, page: 1, pageSize: 100 });
  return page.records.find((row) => row.herbCode === herbCode) ?? null;
}

async function saveHerb(command: AdminHerbCommand) {
  if (overwriteExisting.value && command.herbCode) {
    const existing = await findExistingHerb(command.herbCode);
    if (existing) {
      await updateAdminHerb(existing.id, command);
      return '覆盖成功';
    }
  }
  await createAdminHerb(command);
  return '导入成功';
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
    emit('notice', 'success', `已读取 ${formatNumber(rows.value.length)} 行药品目录`);
  } catch (error) {
    rows.value = [];
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  }
}

async function importHerbs() {
  if (!canImport.value) return;
  importing.value = true;
  errorLine.value = '';
  results.value = [];
  for (const row of rows.value) {
    const herbCode = csvCell(row, ['herbCode', '药品编码', '药材编码', '编码']);
    const herbName = csvCell(row, ['herbName', '药品名称', '药材名称', '名称']);
    try {
      const command = {
        herbCode: requiredCell(row, ['herbCode', '药品编码', '药材编码', '编码'], '药品编码'),
        herbName: requiredCell(row, ['herbName', '药品名称', '药材名称', '名称'], '药品名称'),
        drugSpecs: csvCell(row, ['drugSpecs', '规格']),
        drugOrigin: csvCell(row, ['drugOrigin', '产地']),
        unit: csvCell(row, ['unit', '单位']),
        retailPrice: optionalNumber(csvCell(row, ['retailPrice', '零售价', '价格'])),
        enabled: parseEnabled(csvCell(row, ['enabled', '状态', '启用'])),
        remark: csvCell(row, ['remark', '备注']),
      };
      const message = await saveHerb(command);
      results.value.push({
        rowNumber: row.rowNumber,
        herbCode,
        herbName,
        status: 'SUCCESS',
        message,
      });
    } catch (error) {
      results.value.push({
        rowNumber: row.rowNumber,
        herbCode,
        herbName,
        status: 'FAILED',
        message: errorMessage(error),
      });
    }
  }
  importing.value = false;
  emit('countChanged', successCount.value);
  emit('notice', failedCount.value ? 'error' : 'success', `药品目录导入完成：成功 ${successCount.value} 行，失败 ${failedCount.value} 行`);
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
    'herbCode,herbName,drugSpecs,drugOrigin,unit,retailPrice,enabled,remark',
    'HERB001,示例药品,10g,四川,g,0.35,true,示例',
  ].join('\n');
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'herb-import-template.csv';
  link.click();
  URL.revokeObjectURL(url);
}

function downloadFailures() {
  downloadCsv(
    'herb-import-errors.csv',
    ['行号', '药品编码', '药品名称', '错误原因'],
    failedResults.value.map((row) => ({
      行号: row.rowNumber,
      药品编码: row.herbCode,
      药品名称: row.herbName,
      错误原因: row.message,
    })),
  );
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      loaded.value = true;
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
  <section class="legacy-page herb-import-page">
    <ul class="legacy-search herb-import-actions">
      <li>
        CSV 文件：
        <input ref="fileInput" class="legacy-input input-large" type="file" accept=".csv,text/csv" @change="handleFileChange" />
      </li>
      <li>
        <button class="legacy-btn" type="button" @click="downloadTemplate">下载模板</button>
      </li>
      <li>
        <label class="form-check import-check">
          <input v-model="overwriteExisting" type="checkbox" />
          覆盖已存在
        </label>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="failedResults.length === 0 || importing" @click="downloadFailures">
          下载失败明细
        </button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="!canImport" @click="importHerbs">
          {{ importing ? '导入中' : '开始导入' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="importing" @click="resetImport">清空</button>
      </li>
    </ul>

    <p v-if="selectedFileName" class="legacy-hint">当前文件：{{ selectedFileName }}</p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats herb-import-stats">
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
      <table class="legacy-main-table herb-import-table">
        <thead>
          <tr class="legacy-main-head">
            <th>行号</th>
            <th>药品编码</th>
            <th>药品名称</th>
            <th>状态</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="results.length === 0" class="legacy-main-info">
            <td colspan="5" class="legacy-empty">选择 CSV 后执行导入，结果会显示在这里</td>
          </tr>
          <tr v-for="row in results" :key="row.rowNumber" class="legacy-main-info">
            <td>{{ row.rowNumber }}</td>
            <td><strong>{{ row.herbCode || '-' }}</strong></td>
            <td>{{ row.herbName || '-' }}</td>
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
.herb-import-actions {
  row-gap: 10px;
}

.herb-import-stats {
  margin-bottom: 10px;
}

.import-check {
  align-items: center;
  display: inline-flex;
  gap: 6px;
}

.herb-import-table {
  min-width: 760px;
}

.herb-import-table th,
.herb-import-table td {
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
