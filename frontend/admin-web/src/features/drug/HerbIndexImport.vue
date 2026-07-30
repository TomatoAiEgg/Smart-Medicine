<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  createAdminHerbIndex,
  listAdminHerbIndexes,
  listAdminHerbs,
  listAdminInstitutions,
  updateAdminHerbIndex,
} from '../../api/order';
import type { AdminHerbIndexCommand, AdminHerbRecord, AdminInstitutionRecord } from '../../api/types';
import { formatNumber } from '../../domain/formatters';
import { csvCell, downloadCsv, parseCsv, parseEnabled, type CsvRow } from './csvImport';

type NoticeTone = 'info' | 'success' | 'error';
type ImportStatus = 'SUCCESS' | 'FAILED';
type PrecheckLevel = 'ERROR' | 'WARNING';

interface ImportResult {
  rowNumber: number;
  institution: string;
  externalHerbCode: string;
  externalHerbName: string;
  herb: string;
  status: ImportStatus;
  message: string;
}

interface PrecheckIssue {
  rowNumber: number;
  institution: string;
  externalHerbCode: string;
  externalHerbName: string;
  herb: string;
  level: PrecheckLevel;
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
const precheckIssues = ref<PrecheckIssue[]>([]);
const institutions = ref<AdminInstitutionRecord[]>([]);
const herbs = ref<AdminHerbRecord[]>([]);
const importing = ref(false);
const prechecking = ref(false);
const loadingOptions = ref(false);
const errorLine = ref('');
const loaded = ref(false);
const optionPageSize = 100;
const maxOptionPages = 50;
const overwriteExisting = ref(false);

const totalRows = computed(() => rows.value.length);
const successCount = computed(() => results.value.filter((row) => row.status === 'SUCCESS').length);
const failedCount = computed(() => results.value.filter((row) => row.status === 'FAILED').length);
const failedResults = computed(() => results.value.filter((row) => row.status === 'FAILED'));
const precheckErrorCount = computed(() => precheckIssues.value.filter((row) => row.level === 'ERROR').length);
const precheckWarningCount = computed(() => precheckIssues.value.filter((row) => row.level === 'WARNING').length);
const canImport = computed(() => rows.value.length > 0 && !importing.value && !prechecking.value && !loadingOptions.value);

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

async function listAllInstitutions() {
  const records: AdminInstitutionRecord[] = [];
  for (let page = 1; page <= maxOptionPages; page += 1) {
    const nextPage = await listAdminInstitutions({ page, pageSize: optionPageSize });
    records.push(...nextPage.records);
    if (records.length >= nextPage.total || nextPage.records.length < optionPageSize) {
      break;
    }
  }
  return records;
}

async function listAllEnabledHerbs() {
  const records: AdminHerbRecord[] = [];
  for (let page = 1; page <= maxOptionPages; page += 1) {
    const nextPage = await listAdminHerbs({ page, pageSize: optionPageSize, enabled: true });
    records.push(...nextPage.records);
    if (records.length >= nextPage.total || nextPage.records.length < optionPageSize) {
      break;
    }
  }
  return records;
}

async function findExistingIndex(institutionId: string, externalHerbCode: string) {
  const page = await listAdminHerbIndexes({ institutionId, keyword: externalHerbCode, page: 1, pageSize: 100 });
  return page.records.find((row) => row.institutionId === institutionId && row.externalHerbCode === externalHerbCode) ?? null;
}

async function saveHerbIndex(command: AdminHerbIndexCommand) {
  if (overwriteExisting.value && command.institutionId && command.externalHerbCode) {
    const existing = await findExistingIndex(command.institutionId, command.externalHerbCode);
    if (existing) {
      await updateAdminHerbIndex(existing.id, command);
      return '覆盖成功';
    }
  }
  await createAdminHerbIndex(command);
  return '导入成功';
}

function buildCommand(row: CsvRow): AdminHerbIndexCommand {
  const institution = findInstitution(row);
  const herb = findHerb(row);
  return {
    institutionId: institution.id,
    externalHerbCode: requiredCell(row, ['externalHerbCode', '机构药品编码', '院内药品编码'], '机构药品编码'),
    externalHerbName: requiredCell(row, ['externalHerbName', '机构药品名称', '院内药品名称'], '机构药品名称'),
    herbId: herb.id,
    matchType: csvCell(row, ['matchType', '匹配类型']) || 'IMPORT',
    enabled: parseEnabled(csvCell(row, ['enabled', '状态', '启用'])),
    remark: csvCell(row, ['remark', '备注']),
  };
}

async function runPrecheck(silent = false) {
  prechecking.value = true;
  errorLine.value = '';
  precheckIssues.value = [];
  await refreshOptions();
  if (errorLine.value) {
    prechecking.value = false;
    return [
      {
        rowNumber: 0,
        institution: '',
        externalHerbCode: '',
        externalHerbName: '',
        herb: '',
        level: 'ERROR' as const,
        message: errorLine.value,
      },
    ];
  }

  const issues: PrecheckIssue[] = [];
  const pairRows = new Map<string, PrecheckIssue[]>();
  for (const row of rows.value) {
    const institutionLabel = csvCell(row, ['institutionCode', '机构编码']) || csvCell(row, ['institutionName', '机构名称']);
    const externalHerbCode = csvCell(row, ['externalHerbCode', '机构药品编码', '院内药品编码']);
    const externalHerbName = csvCell(row, ['externalHerbName', '机构药品名称', '院内药品名称']);
    const herbLabel = csvCell(row, ['herbCode', '平台药品编码', '药品编码']) || csvCell(row, ['herbName', '平台药品名称', '药品名称']);
    try {
      const command = buildCommand(row);
      const pairKey = `${command.institutionId ?? ''}::${command.externalHerbCode ?? ''}`;
      pairRows.set(pairKey, [
        ...(pairRows.get(pairKey) ?? []),
        {
          rowNumber: row.rowNumber,
          institution: institutionLabel,
          externalHerbCode,
          externalHerbName,
          herb: herbLabel,
          level: 'WARNING',
          message: '',
        },
      ]);
    } catch (error) {
      issues.push({
        rowNumber: row.rowNumber,
        institution: institutionLabel,
        externalHerbCode,
        externalHerbName,
        herb: herbLabel,
        level: 'ERROR',
        message: errorMessage(error),
      });
    }
  }

  pairRows.forEach((entries) => {
    if (entries.length > 1) {
      entries.forEach((entry) => {
        issues.push({
          ...entry,
          level: 'ERROR',
          message: '文件内同一机构药品编码重复',
        });
      });
    }
  });

  try {
    for (const [pairKey, entries] of pairRows.entries()) {
      const [institutionId, externalHerbCode] = pairKey.split('::');
      if (!institutionId || !externalHerbCode) continue;
      const existing = await findExistingIndex(institutionId, externalHerbCode);
      if (existing) {
        entries.forEach((entry) => {
          issues.push({
            ...entry,
            level: overwriteExisting.value ? 'WARNING' : 'ERROR',
            message: overwriteExisting.value ? '库内已存在，导入时将覆盖' : '库内已存在，未开启覆盖',
          });
        });
      }
    }
  } catch (error) {
    issues.push({
      rowNumber: 0,
      institution: '',
      externalHerbCode: '',
      externalHerbName: '',
      herb: '',
      level: 'ERROR',
      message: `库内重复检查失败：${errorMessage(error)}`,
    });
  }

  precheckIssues.value = issues.sort((left, right) => left.rowNumber - right.rowNumber);
  prechecking.value = false;
  if (!silent) {
    emit(
      'notice',
      precheckErrorCount.value ? 'error' : 'success',
      `预检完成：错误 ${precheckErrorCount.value} 条，提示 ${precheckWarningCount.value} 条`,
    );
  }
  return issues;
}

async function refreshOptions() {
  loadingOptions.value = true;
  errorLine.value = '';
  try {
    const [nextInstitutions, nextHerbs] = await Promise.all([listAllInstitutions(), listAllEnabledHerbs()]);
    institutions.value = nextInstitutions;
    herbs.value = nextHerbs;
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
  precheckIssues.value = [];
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
  const issues = await runPrecheck(true);
  if (issues.some((row) => row.level === 'ERROR')) {
    emit('notice', 'error', '预检存在错误，已停止导入');
    return;
  }
  importing.value = true;
  errorLine.value = '';
  results.value = [];

  for (const row of rows.value) {
    const externalHerbCode = csvCell(row, ['externalHerbCode', '机构药品编码', '院内药品编码']);
    const externalHerbName = csvCell(row, ['externalHerbName', '机构药品名称', '院内药品名称']);
    const institutionLabel = csvCell(row, ['institutionCode', '机构编码']) || csvCell(row, ['institutionName', '机构名称']);
    const herbLabel = csvCell(row, ['herbCode', '平台药品编码', '药品编码']) || csvCell(row, ['herbName', '平台药品名称', '药品名称']);
    try {
      const institution = findInstitution(row);
      const herb = findHerb(row);
      const command = buildCommand(row);
      const message = await saveHerbIndex(command);
      results.value.push({
        rowNumber: row.rowNumber,
        institution: institutionLabel || institution.institutionName,
        externalHerbCode,
        externalHerbName,
        herb: herbLabel || herb.herbName,
        status: 'SUCCESS',
        message,
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
  precheckIssues.value = [];
  selectedFileName.value = '';
  errorLine.value = '';
  if (fileInput.value) {
    fileInput.value.value = '';
  }
  emit('countChanged', 0);
}

function downloadTemplate() {
  downloadCsv(
    'herb-index-import-template.csv',
    ['institutionCode', 'institutionName', 'externalHerbCode', 'externalHerbName', 'herbCode', 'herbName', 'matchType', 'enabled', 'remark'],
    [{
      institutionCode: 'HOSP001',
      institutionName: '示例机构',
      externalHerbCode: 'EXT001',
      externalHerbName: '院内药品',
      herbCode: 'HERB001',
      herbName: '平台药品',
      matchType: 'IMPORT',
      enabled: 'true',
      remark: '示例',
    }],
  );
}

function downloadFailures() {
  downloadCsv(
    'herb-index-import-errors.csv',
    ['行号', '机构', '机构药品编码', '机构药品名称', '平台药品', '错误原因'],
    failedResults.value.map((row) => ({
      行号: row.rowNumber,
      机构: row.institution,
      机构药品编码: row.externalHerbCode,
      机构药品名称: row.externalHerbName,
      平台药品: row.herb,
      错误原因: row.message,
    })),
  );
}

function downloadResults() {
  downloadCsv(
    'herb-index-import-results.csv',
    ['行号', '机构', '机构药品编码', '机构药品名称', '平台药品', '状态', '结果'],
    results.value.map((row) => ({
      行号: row.rowNumber,
      机构: row.institution,
      机构药品编码: row.externalHerbCode,
      机构药品名称: row.externalHerbName,
      平台药品: row.herb,
      状态: row.status === 'SUCCESS' ? '成功' : '失败',
      结果: row.message,
    })),
  );
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
        <button class="legacy-btn" type="button" :disabled="results.length === 0 || importing" @click="downloadResults">
          下载全部结果
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="rows.length === 0 || importing || prechecking" @click="runPrecheck(false)">
          {{ prechecking ? '预检中' : '导入预检' }}
        </button>
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
      <li>
        <strong>{{ formatNumber(precheckErrorCount) }}</strong>
        <span>预检错误</span>
      </li>
      <li>
        <strong>{{ formatNumber(precheckWarningCount) }}</strong>
        <span>预检提示</span>
      </li>
    </ul>

    <div v-if="precheckIssues.length > 0" class="legacy-panel import-precheck-panel">
      <table class="legacy-main-table herb-index-import-table">
        <thead>
          <tr class="legacy-main-head">
            <th>行号</th>
            <th>机构</th>
            <th>机构药品编码</th>
            <th>机构药品名称</th>
            <th>平台药品</th>
            <th>级别</th>
            <th>预检结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in precheckIssues" :key="`${row.rowNumber}-${row.message}`" class="legacy-main-info">
            <td>{{ row.rowNumber }}</td>
            <td>{{ row.institution || '-' }}</td>
            <td><strong>{{ row.externalHerbCode || '-' }}</strong></td>
            <td>{{ row.externalHerbName || '-' }}</td>
            <td>{{ row.herb || '-' }}</td>
            <td>
              <span :class="['import-status', row.level === 'ERROR' ? 'is-failed' : 'is-warning']">
                {{ row.level === 'ERROR' ? '错误' : '提示' }}
              </span>
            </td>
            <td>{{ row.message }}</td>
          </tr>
        </tbody>
      </table>
    </div>

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

.import-check {
  align-items: center;
  display: inline-flex;
  gap: 6px;
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

.import-status.is-warning {
  background: #fef3c7;
  color: #92400e;
}

.import-precheck-panel {
  margin-bottom: 10px;
}
</style>
