<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminLabelPrintRecord,
  getAdminPrescriptionPrintPayload,
  listAdminLabelPrintRecords,
  listAdminLabelTemplates,
  listAdminPrescriptionReprints,
} from '../../api/order';
import type {
  AdminLabelPrintRecord,
  AdminLabelTemplateRecord,
  AdminOrderDetailDrug,
  AdminPrescriptionPrintPayload,
  AdminPrescriptionReprintItem,
  AdminPrescriptionReprintPage,
  AdminPrescriptionReprintQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, displayValue, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const startTime = ref('');
const endTime = ref('');
const prescriptionNo = ref('');
const page = ref(1);
const pageSize = ref(20);
const labelPage = ref<AdminPrescriptionReprintPage | null>(null);
const labelTemplates = ref<AdminLabelTemplateRecord[]>([]);
const selectedTemplateId = ref('');
const loading = ref(false);
const templateLoading = ref(false);
const printingNo = ref('');
const batchPrinting = ref(false);
const recordLoading = ref(false);
const retryingRecordId = ref('');
const errorLine = ref('');
const templateError = ref('');
const selectedPrescriptionNos = ref<string[]>([]);
const printRecords = ref<AdminLabelPrintRecord[]>([]);

const rows = computed(() => labelPage.value?.records ?? []);
const total = computed(() => labelPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const selectedRows = computed(() => rows.value.filter((row) => selectedPrescriptionNos.value.includes(row.prescriptionNo)));
const selectedCurrentPageAll = computed(() => rows.value.length > 0 && selectedRows.value.length === rows.value.length);
const selectedTemplate = computed(() => (
  labelTemplates.value.find((template) => template.id === selectedTemplateId.value)
  ?? null
));
const failedPrintRecords = computed(() => printRecords.value.filter((record) => record.printStatus === 'FAILED'));

function fullAddress(row: AdminPrescriptionReprintItem | AdminPrescriptionPrintPayload) {
  return [
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ].filter(Boolean).join('') || '-';
}

function queryParams(): AdminPrescriptionReprintQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    prescriptionNo: prescriptionNo.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function prescriptionTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    DECOCTION: '代煎',
    SELF_DECOCTION: '自煎',
    HERBAL_PIECE: '饮片',
    CREAM: '膏方',
    PILL: '丸剂',
    POWDER: '散剂',
    OTHER: '其他',
    1: '饮片',
    2: '代煎',
    3: '膏方',
    4: '丸剂',
    5: '散剂',
  };
  return value ? labels[value] ?? value : '-';
}

function medicationMethodText(value: number | null | undefined) {
  if (value === 0) return '内服';
  if (value === 1) return '外用';
  return '-';
}

function batchText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    MORNING: '早批次',
    NOON: '午批次',
    EVENING: '晚批次',
    1: '早批次',
    2: '午批次',
    3: '晚批次',
  };
  return value ? labels[value] ?? value : '-';
}

function printStatusText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    PRINTED: '已打开',
    FAILED: '失败',
  };
  return value ? labels[value] ?? value : '-';
}

function templateOptionText(template: AdminLabelTemplateRecord) {
  return `${template.templateName} / ${displayValue(template.prescriptionType || '通用')} / ${template.labelWidthMm}x${template.labelHeightMm}mm`;
}

function patientInfo(row: AdminPrescriptionReprintItem) {
  return [row.patientName, row.patientPhone].filter(Boolean).join(' / ') || '-';
}

function isPrescriptionSelected(nextPrescriptionNo: string) {
  return selectedPrescriptionNos.value.includes(nextPrescriptionNo);
}

function togglePrescriptionSelection(nextPrescriptionNo: string, checked: boolean) {
  selectedPrescriptionNos.value = checked
    ? Array.from(new Set([...selectedPrescriptionNos.value, nextPrescriptionNo]))
    : selectedPrescriptionNos.value.filter((item) => item !== nextPrescriptionNo);
}

function toggleCurrentPageSelection(checked: boolean) {
  selectedPrescriptionNos.value = checked ? rows.value.map((row) => row.prescriptionNo) : [];
}

function downloadLabelPrintCsv() {
  downloadCsv(
    `处方标签打印-第${page.value}页.csv`,
    [
      '平台处方号',
      '外部处方号',
      '平台订单号',
      '外部订单号',
      '处方状态',
      '订单状态',
      '病人信息',
      '机构名称',
      '送货地址',
      '送货时间',
      '处方类型',
      '服用方法',
      '剂数',
      '批次',
      '接单时间',
      '调剂工号',
    ],
    rows.value.map((row) => [
      row.prescriptionNo,
      row.externalPrescriptionNo,
      row.orderNo,
      row.externalOrderNo,
      row.prescriptionStatus,
      row.orderStatus,
      patientInfo(row),
      row.institutionName,
      fullAddress(row),
      formatDate(row.deliveryTime),
      prescriptionTypeText(row.prescriptionType),
      medicationMethodText(row.isWithin),
      row.doseCount,
      batchText(row.batchNo),
      formatDate(row.createdAt),
      row.dispenser,
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条处方标签`);
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

async function refreshLabelPrints() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminPrescriptionReprints(queryParams());
    labelPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    selectedPrescriptionNos.value = [];
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询到 ${nextPage.total} 条可打印处方标签`);
  } catch (error) {
    labelPage.value = null;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function refreshLabelTemplates() {
  templateLoading.value = true;
  templateError.value = '';
  try {
    const nextPage = await listAdminLabelTemplates({
      enabled: true,
      page: 1,
      pageSize: 100,
    });
    labelTemplates.value = nextPage.records;
    if (!labelTemplates.value.some((template) => template.id === selectedTemplateId.value)) {
      selectedTemplateId.value = labelTemplates.value[0]?.id ?? '';
    }
  } catch (error) {
    labelTemplates.value = [];
    selectedTemplateId.value = '';
    templateError.value = errorMessage(error);
  } finally {
    templateLoading.value = false;
  }
}

async function refreshLabelPrintRecords() {
  recordLoading.value = true;
  try {
    const nextPage = await listAdminLabelPrintRecords({
      page: 1,
      pageSize: 20,
    });
    printRecords.value = nextPage.records;
  } catch (error) {
    emit('notice', 'error', `标签打印记录加载失败：${errorMessage(error)}`);
  } finally {
    recordLoading.value = false;
  }
}

async function saveLabelPrintRecord(
  prescriptionNoValue: string,
  printStatus: 'PRINTED' | 'FAILED',
  failureReason?: string,
  retryOf?: string | null,
) {
  try {
    await createAdminLabelPrintRecord(prescriptionNoValue, {
      printStatus,
      templateId: selectedTemplate.value?.id ?? null,
      templateName: selectedTemplate.value?.templateName ?? null,
      failureReason: failureReason ?? null,
      operator: 'admin',
      retryOf: retryOf ?? null,
    });
    await refreshLabelPrintRecords();
  } catch (error) {
    emit('notice', 'error', `标签打印记录保存失败：${errorMessage(error)}`);
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshLabelPrints();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshLabelPrints();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshLabelPrints();
}

function escapeHtml(value: string | number | null | undefined) {
  return displayValue(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function drugName(detail: AdminOrderDetailDrug) {
  return detail.platformDrugName || detail.drugName || detail.platformDrugCode || detail.drugCode || '-';
}

function renderDrugSummary(details: AdminOrderDetailDrug[]) {
  if (details.length === 0) return '<li>暂无药品明细</li>';
  return details.slice(0, 10).map((detail) => `
    <li>
      <strong>${escapeHtml(drugName(detail))}</strong>
      <span>${escapeHtml(detail.dose)}${escapeHtml(detail.unit)} / ${escapeHtml(detail.quantity)}</span>
    </li>
  `).join('');
}

function renderLabelSection(payload: AdminPrescriptionPrintPayload, template: AdminLabelTemplateRecord | null) {
  const drugs = renderDrugSummary(payload.details);
  const templateName = template?.templateName ?? '默认浏览器标签';
  return `
  <section class="label">
    <main>
      <h1 class="title">${escapeHtml(payload.institutionName)} 处方标签</h1>
      <div class="line muted">模板：${escapeHtml(templateName)}</div>
      <div class="line"><strong>处方：</strong>${escapeHtml(payload.prescriptionNo)}</div>
      <div class="line"><strong>患者：</strong>${escapeHtml(payload.patientName)}　${escapeHtml(payload.patientPhone)}</div>
      <div class="line"><strong>类型：</strong>${escapeHtml(prescriptionTypeText(payload.prescriptionType))} / ${escapeHtml(medicationMethodText(payload.isWithin))} / ${escapeHtml(batchText(payload.batchNo))}</div>
      <div class="line"><strong>剂数：</strong>${escapeHtml(payload.doseCount)}　<strong>煎煮量：</strong>${escapeHtml(payload.decoctionCount)}　<strong>每剂：</strong>${escapeHtml(payload.perPackNum)}包/${escapeHtml(payload.perPackDose)}ml</div>
      <div class="line"><strong>配送：</strong>${escapeHtml(formatDate(payload.deliveryTime))}</div>
      <div class="line muted">${escapeHtml(fullAddress(payload))}</div>
      <ul>${drugs}</ul>
    </main>
    <aside>
      <div class="code">${escapeHtml(payload.prescriptionNo)}</div>
      <div class="line muted">打印：${escapeHtml(formatDate(payload.printedAt))}</div>
    </aside>
  </section>`;
}

function renderLabelHtml(payloads: AdminPrescriptionPrintPayload[], template: AdminLabelTemplateRecord | null) {
  const labelWidthMm = template?.labelWidthMm ?? 90;
  const labelHeightMm = template?.labelHeightMm ?? 60;
  const titlePrescriptionNo = payloads.length === 1 ? payloads[0]?.prescriptionNo : `批量${payloads.length}张`;
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>处方标签-${escapeHtml(titlePrescriptionNo)}</title>
  <style>
    @page { size: ${labelWidthMm}mm ${labelHeightMm}mm; margin: 3mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 10px; }
    .toolbar { position: fixed; right: 12px; top: 12px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    .label { width: ${labelWidthMm}mm; min-height: ${labelHeightMm}mm; padding: 4mm; border: 1px solid #111827; display: grid; grid-template-columns: 1fr 22mm; gap: 3mm; }
    .title { margin: 0 0 2mm; font-size: 15px; letter-spacing: 0; }
    .muted { color: #475569; }
    .line { margin: 1mm 0; }
    .label + .label { page-break-before: always; }
    .code { border: 1px solid #111827; min-height: 22mm; display: grid; place-items: center; text-align: center; word-break: break-all; padding: 2mm; font-size: 9px; }
    ul { margin: 2mm 0 0; padding: 0; list-style: none; display: grid; gap: 1mm; }
    li { display: flex; justify-content: space-between; gap: 2mm; border-bottom: 1px dotted #cbd5e1; padding-bottom: 1mm; }
    strong { font-weight: 700; }
    @media print { .toolbar { display: none; } .label { border: 0; padding: 0; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  ${payloads.map((payload) => renderLabelSection(payload, template)).join('\n')}
</body>
</html>`;
}

async function openLabelPrintByPrescriptionNo(prescriptionNoValue: string, retryOf?: string | null) {
  errorLine.value = '';
  try {
    const payload = await getAdminPrescriptionPrintPayload(prescriptionNoValue);
    const printWindow = window.open('', '_blank', 'width=720,height=520');
    if (!printWindow) {
      errorLine.value = '浏览器阻止了标签打印窗口';
      await saveLabelPrintRecord(prescriptionNoValue, 'FAILED', errorLine.value, retryOf);
      return;
    }
    printWindow.document.open();
    printWindow.document.write(renderLabelHtml([payload], selectedTemplate.value));
    printWindow.document.close();
    await saveLabelPrintRecord(prescriptionNoValue, 'PRINTED', undefined, retryOf);
    emit('notice', 'success', `处方 ${prescriptionNoValue} 标签打印窗口已打开`);
  } catch (error) {
    errorLine.value = errorMessage(error);
    await saveLabelPrintRecord(prescriptionNoValue, 'FAILED', errorLine.value, retryOf);
  }
}

async function openLabelPrint(row: AdminPrescriptionReprintItem) {
  printingNo.value = row.prescriptionNo;
  try {
    await openLabelPrintByPrescriptionNo(row.prescriptionNo);
  } finally {
    printingNo.value = '';
  }
}

async function retryLabelPrint(record: AdminLabelPrintRecord) {
  retryingRecordId.value = record.id;
  try {
    await openLabelPrintByPrescriptionNo(record.prescriptionNo, record.id);
  } finally {
    retryingRecordId.value = '';
  }
}

async function openSelectedLabelPrints() {
  if (selectedRows.value.length === 0) {
    errorLine.value = '请先勾选要批量打印的处方';
    return;
  }
  batchPrinting.value = true;
  errorLine.value = '';
  const selectedPrescriptionNosSnapshot = selectedRows.value.map((row) => row.prescriptionNo);
  try {
    const payloads = await Promise.all(
      selectedPrescriptionNosSnapshot.map((nextPrescriptionNo) => getAdminPrescriptionPrintPayload(nextPrescriptionNo)),
    );
    const printWindow = window.open('', '_blank', 'width=720,height=520');
    if (!printWindow) {
      errorLine.value = '浏览器阻止了标签打印窗口';
      await Promise.all(selectedPrescriptionNosSnapshot.map((nextPrescriptionNo) => (
        saveLabelPrintRecord(nextPrescriptionNo, 'FAILED', errorLine.value)
      )));
      return;
    }
    printWindow.document.open();
    printWindow.document.write(renderLabelHtml(payloads, selectedTemplate.value));
    printWindow.document.close();
    await Promise.all(selectedPrescriptionNosSnapshot.map((nextPrescriptionNo) => (
      saveLabelPrintRecord(nextPrescriptionNo, 'PRINTED')
    )));
    emit('notice', 'success', `已打开 ${payloads.length} 张处方标签的批量打印窗口`);
  } catch (error) {
    errorLine.value = errorMessage(error);
    await Promise.all(selectedPrescriptionNosSnapshot.map((nextPrescriptionNo) => (
      saveLabelPrintRecord(nextPrescriptionNo, 'FAILED', errorLine.value)
    )));
  } finally {
    batchPrinting.value = false;
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (!active) return;
    void refreshLabelPrints();
    void refreshLabelTemplates();
    void refreshLabelPrintRecords();
  },
  { immediate: true },
);

defineExpose({
  refreshLabelPrints,
  refreshLabelPrintRecords,
});
</script>

<template>
  <section class="legacy-page label-print-page">
    <ul class="legacy-search label-print-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        平台处方号：
        <input v-model="prescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        标签模板：
        <select v-model="selectedTemplateId" class="legacy-input input-large" :disabled="templateLoading">
          <option value="">{{ templateLoading ? '加载中' : '默认浏览器标签' }}</option>
          <option v-for="template in labelTemplates" :key="template.id" :value="template.id">
            {{ templateOptionText(template) }}
          </option>
        </select>
      </li>
      <li>
        条数：
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="5" max="100" step="5" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadLabelPrintCsv">
          导出当前页
        </button>
      </li>
      <li>
        <button
          class="legacy-btn legacy-btn-export"
          type="button"
          :disabled="batchPrinting || selectedRows.length === 0"
          @click="openSelectedLabelPrints"
        >
          {{ batchPrinting ? '打开中' : `批量打印标签(${selectedRows.length})` }}
        </button>
      </li>
    </ul>

    <p class="label-print-hint">
      当前页面使用真实可打印处方记录生成浏览器标签；窗口打开成功或失败会写入打印记录，失败记录可在本页重试。
    </p>
    <p v-if="selectedTemplate" class="label-template-hint">
      当前模板：{{ selectedTemplate.templateName }} / {{ selectedTemplate.labelWidthMm }} x {{ selectedTemplate.labelHeightMm }} mm
    </p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>
    <p v-if="templateError" class="error-line">标签模板加载失败：{{ templateError }}</p>

    <section class="legacy-panel label-print-record-panel">
      <div class="label-print-record-title">
        <div>
          <strong>最近打印记录</strong>
          <span>失败 {{ failedPrintRecords.length }} 条</span>
        </div>
        <button class="legacy-btn" type="button" :disabled="recordLoading" @click="refreshLabelPrintRecords">
          {{ recordLoading ? '刷新中' : '刷新记录' }}
        </button>
      </div>
      <table class="legacy-main-table label-print-record-table">
        <thead>
          <tr class="legacy-main-head">
            <th>处方号</th>
            <th>状态</th>
            <th>模板</th>
            <th>失败原因</th>
            <th>操作人</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="recordLoading" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">正在加载打印记录</td>
          </tr>
          <tr v-else-if="printRecords.length === 0" class="legacy-main-info">
            <td colspan="7" class="legacy-empty">暂无打印记录</td>
          </tr>
          <tr v-for="record in printRecords" :key="record.id" class="legacy-main-info">
            <td>
              <strong>{{ record.prescriptionNo }}</strong>
              <small>{{ displayValue(record.orderNo) }}</small>
            </td>
            <td><StatusPill :value="printStatusText(record.printStatus)" :tone="statusTone(record.printStatus)" /></td>
            <td>{{ displayValue(record.templateName) }}</td>
            <td class="legacy-left">{{ displayValue(record.failureReason) }}</td>
            <td>{{ displayValue(record.operator) }}</td>
            <td>{{ formatDate(record.createdAt) }}</td>
            <td>
              <button
                class="legacy-link-btn workflow-pass-btn"
                type="button"
                :disabled="record.printStatus !== 'FAILED' || retryingRecordId === record.id"
                @click="retryLabelPrint(record)"
              >
                {{ retryingRecordId === record.id ? '重试中' : '重试' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div class="legacy-panel">
      <table class="legacy-main-table label-print-table">
        <thead>
          <tr class="legacy-main-head">
            <th>
              <input
                type="checkbox"
                :checked="selectedCurrentPageAll"
                :disabled="rows.length === 0 || loading || batchPrinting"
                aria-label="选择当前页处方"
                @change="toggleCurrentPageSelection(($event.target as HTMLInputElement).checked)"
              />
            </th>
            <th>平台处方号</th>
            <th>状态</th>
            <th>病人信息</th>
            <th>机构名称</th>
            <th>送货地址</th>
            <th>送货时间</th>
            <th>处方类型</th>
            <th>服用方法</th>
            <th>剂数</th>
            <th>批次</th>
            <th>接单时间</th>
            <th>调剂工号</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="14" class="legacy-empty">正在查询处方标签</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="14" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" :key="row.prescriptionId" class="legacy-main-info">
            <td>
              <input
                type="checkbox"
                :checked="isPrescriptionSelected(row.prescriptionNo)"
                :disabled="printingNo === row.prescriptionNo || batchPrinting"
                :aria-label="`选择处方 ${row.prescriptionNo}`"
                @change="togglePrescriptionSelection(row.prescriptionNo, ($event.target as HTMLInputElement).checked)"
              />
            </td>
            <td>
              <strong>{{ row.prescriptionNo }}</strong>
              <small>{{ displayValue(row.externalPrescriptionNo) }}</small>
            </td>
            <td>
              <StatusPill :value="row.prescriptionStatus" :tone="statusTone(row.prescriptionStatus)" />
              <small>{{ row.orderStatus }}</small>
            </td>
            <td>{{ patientInfo(row) }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td class="legacy-left">{{ fullAddress(row) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionType) }}</td>
            <td>{{ medicationMethodText(row.isWithin) }}</td>
            <td>{{ displayValue(row.doseCount) }}</td>
            <td>{{ batchText(row.batchNo) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ displayValue(row.dispenser) }}</td>
            <td>
              <button
                class="legacy-link-btn workflow-pass-btn"
                type="button"
                :disabled="printingNo === row.prescriptionNo"
                @click="openLabelPrint(row)"
              >
                {{ printingNo === row.prescriptionNo ? '打开中' : '打印标签' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">
      显示第 {{ rows.length > 0 ? (page - 1) * pageSize + 1 : 0 }} 至 {{ (page - 1) * pageSize + rows.length }} 项记录，共 {{ total }} 项
    </p>
    <div class="legacy-pagination">
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="goPreviousPage">上一页</button>
      <span>第 {{ page }} 页</span>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="goNextPage">下一页</button>
    </div>
  </section>
</template>

<style scoped>
.label-print-search {
  row-gap: 10px;
}

.label-print-hint {
  margin: 0 0 10px;
  color: #6f7d91;
  font-size: 13px;
}

.label-template-hint {
  margin: -4px 0 10px;
  color: #475467;
  font-size: 13px;
}

.label-print-table {
  min-width: 1180px;
}

.label-print-table th,
.label-print-table td {
  min-width: 88px;
}

.label-print-table th:nth-child(5),
.label-print-table td:nth-child(5) {
  min-width: 190px;
  white-space: normal;
}

.label-print-record-panel {
  margin-bottom: 12px;
}

.label-print-record-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.label-print-record-title div {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.label-print-record-title span {
  color: #64748b;
  font-size: 13px;
}

.label-print-record-table {
  min-width: 920px;
}
</style>
