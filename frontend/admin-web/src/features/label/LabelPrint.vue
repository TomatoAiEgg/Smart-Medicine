<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  getAdminPrescriptionPrintPayload,
  listAdminPrescriptionReprints,
} from '../../api/order';
import type {
  AdminOrderDetailDrug,
  AdminPrescriptionPrintPayload,
  AdminPrescriptionReprintItem,
  AdminPrescriptionReprintPage,
  AdminPrescriptionReprintQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
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
const loading = ref(false);
const printingNo = ref('');
const errorLine = ref('');

const rows = computed(() => labelPage.value?.records ?? []);
const total = computed(() => labelPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

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

function patientInfo(row: AdminPrescriptionReprintItem) {
  return [row.patientName, row.patientPhone].filter(Boolean).join(' / ') || '-';
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
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
  return rowValue(value)
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

function renderLabelHtml(payload: AdminPrescriptionPrintPayload) {
  const drugs = renderDrugSummary(payload.details);
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>处方标签-${escapeHtml(payload.prescriptionNo)}</title>
  <style>
    @page { size: 90mm 60mm; margin: 3mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 10px; }
    .toolbar { position: fixed; right: 12px; top: 12px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    .label { width: 90mm; min-height: 60mm; padding: 4mm; border: 1px solid #111827; display: grid; grid-template-columns: 1fr 22mm; gap: 3mm; }
    .title { margin: 0 0 2mm; font-size: 15px; letter-spacing: 0; }
    .muted { color: #475569; }
    .line { margin: 1mm 0; }
    .code { border: 1px solid #111827; min-height: 22mm; display: grid; place-items: center; text-align: center; word-break: break-all; padding: 2mm; font-size: 9px; }
    ul { margin: 2mm 0 0; padding: 0; list-style: none; display: grid; gap: 1mm; }
    li { display: flex; justify-content: space-between; gap: 2mm; border-bottom: 1px dotted #cbd5e1; padding-bottom: 1mm; }
    strong { font-weight: 700; }
    @media print { .toolbar { display: none; } .label { border: 0; padding: 0; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <section class="label">
    <main>
      <h1 class="title">${escapeHtml(payload.institutionName)} 处方标签</h1>
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
  </section>
</body>
</html>`;
}

async function openLabelPrint(row: AdminPrescriptionReprintItem) {
  printingNo.value = row.prescriptionNo;
  errorLine.value = '';
  try {
    const payload = await getAdminPrescriptionPrintPayload(row.prescriptionNo);
    const printWindow = window.open('', '_blank', 'width=720,height=520');
    if (!printWindow) {
      errorLine.value = '浏览器阻止了标签打印窗口';
      return;
    }
    printWindow.document.open();
    printWindow.document.write(renderLabelHtml(payload));
    printWindow.document.close();
    emit('notice', 'success', `处方 ${row.prescriptionNo} 标签打印窗口已打开`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    printingNo.value = '';
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshLabelPrints();
  },
  { immediate: true },
);

defineExpose({
  refreshLabelPrints,
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
        条数：
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="5" max="100" step="5" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
    </ul>

    <p class="label-print-hint">
      当前页面使用真实处方打印数据生成浏览器标签；标签模板配置、打印记录和失败重试仍等待后端契约。
    </p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table label-print-table">
        <thead>
          <tr class="legacy-main-head">
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
            <td colspan="13" class="legacy-empty">正在查询处方标签</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="13" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" :key="row.prescriptionId" class="legacy-main-info">
            <td>
              <strong>{{ row.prescriptionNo }}</strong>
              <small>{{ rowValue(row.externalPrescriptionNo) }}</small>
            </td>
            <td>
              <StatusPill :value="row.prescriptionStatus" :tone="statusTone(row.prescriptionStatus)" />
              <small>{{ row.orderStatus }}</small>
            </td>
            <td>{{ patientInfo(row) }}</td>
            <td>{{ rowValue(row.institutionName) }}</td>
            <td class="legacy-left">{{ fullAddress(row) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionType) }}</td>
            <td>{{ medicationMethodText(row.isWithin) }}</td>
            <td>{{ rowValue(row.doseCount) }}</td>
            <td>{{ batchText(row.batchNo) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ rowValue(row.dispenser) }}</td>
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
</style>
