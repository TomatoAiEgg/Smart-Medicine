<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
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
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, displayValue, formatDate, formatNumber, joinDisplayParts, labelFromMap } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const startTime = ref('');
const endTime = ref('');
const prescriptionNo = ref('');
const page = ref(1);
const pageSize = ref(20);
const reprintPage = ref<AdminPrescriptionReprintPage | null>(null);
const loading = ref(false);
const printingNo = ref('');
const errorLine = ref('');

const rows = computed(() => reprintPage.value?.records ?? []);
const total = computed(() => reprintPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function fullAddress(row: AdminPrescriptionReprintItem | AdminPrescriptionPrintPayload) {
  return joinDisplayParts([
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ]);
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

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function hospitalTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    OTHER: '其他',
    1: '门诊',
    2: '住院',
    3: '其他',
  };
  return labelFromMap(value, labels);
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
  return labelFromMap(value, labels);
}

function medicationMethodText(value: number | null | undefined) {
  if (value === 0) return '内服';
  if (value === 1) return '外用';
  return '-';
}

function addressTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    PATIENT: '送个人',
    PICKUP: '自提',
    EXPRESS: '快递',
    0: '默认',
    1: '送医院',
    2: '送个人',
  };
  return labelFromMap(value, labels);
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
  return labelFromMap(value, labels);
}

function patientInfo(row: AdminPrescriptionReprintItem) {
  return joinDisplayParts([row.patientName, row.patientPhone], ' / ');
}

function downloadReprintCsv() {
  downloadCsv(
    `处方重打-第${page.value}页.csv`,
    [
      '平台处方号',
      '外部处方号',
      '平台订单号',
      '外部订单号',
      '病人信息',
      '收货地址',
      '送货时间',
      '接单时间',
      '送货方式',
      '机构名称',
      '门诊住院',
      '处方类型',
      '服用方法',
      '剂数',
      '批次',
      '调剂工号',
      '处方状态',
      '订单状态',
    ],
    rows.value.map((row) => [
      row.prescriptionNo,
      row.externalPrescriptionNo,
      row.orderNo,
      row.externalOrderNo,
      patientInfo(row),
      fullAddress(row),
      formatDate(row.deliveryTime),
      formatDate(row.createdAt),
      addressTypeText(row.addressType),
      row.institutionName,
      hospitalTypeText(row.hospitalType),
      prescriptionTypeText(row.prescriptionType),
      medicationMethodText(row.isWithin),
      row.doseCount,
      batchText(row.batchNo),
      row.dispenser,
      row.prescriptionStatus,
      row.orderStatus,
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条处方重打记录`);
}

async function refreshPrescriptionReprints() {
  loading.value = true;
  errorLine.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminPrescriptionReprints(queryParams());
    reprintPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('notice', 'success', `已查询到 ${nextPage.total} 条可重打处方`);
  } catch (error) {
    reprintPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshPrescriptionReprints();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshPrescriptionReprints();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshPrescriptionReprints();
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

function renderDrugRows(details: AdminOrderDetailDrug[]) {
  return details.map((detail, index) => `
    <tr>
      <td>${index + 1}</td>
      <td>${escapeHtml(drugName(detail))}</td>
      <td>${escapeHtml(detail.drugSpecs)}</td>
      <td>${escapeHtml(detail.dose)}${escapeHtml(detail.unit)}</td>
      <td>${escapeHtml(detail.quantity)}</td>
      <td>${escapeHtml(detail.specialUsage)}</td>
      <td>${escapeHtml(detail.drugOrigin)}</td>
    </tr>
  `).join('');
}

function renderPrintHtml(payload: AdminPrescriptionPrintPayload) {
  const details = renderDrugRows(payload.details);
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>处方重打-${escapeHtml(payload.prescriptionNo)}</title>
  <style>
    @page { size: A5 landscape; margin: 8mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 12px; }
    .sheet { width: 100%; min-height: 100vh; padding: 8mm; }
    .header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; border-bottom: 2px solid #111827; padding-bottom: 8px; }
    .title { margin: 0; font-size: 20px; letter-spacing: 0; }
    .subtitle { margin-top: 4px; color: #475569; }
    .qr { width: 80px; height: 80px; border: 1px solid #111827; display: grid; place-items: center; font-size: 10px; text-align: center; word-break: break-all; }
    .meta { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px 12px; margin: 10px 0; }
    .meta div { min-width: 0; }
    .label { color: #64748b; }
    table { width: 100%; border-collapse: collapse; }
    th, td { border: 1px solid #cbd5e1; padding: 5px; text-align: left; vertical-align: top; }
    th { background: #f1f5f9; }
    .footer { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: 10px; border-top: 1px solid #94a3b8; padding-top: 8px; }
    .toolbar { position: fixed; right: 14px; top: 14px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    @media print { .toolbar { display: none; } .sheet { padding: 0; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <section class="sheet">
    <div class="header">
      <div>
        <h1 class="title">${escapeHtml(payload.institutionName)} 处方笺</h1>
        <div class="subtitle">处方号：${escapeHtml(payload.prescriptionNo)}　订单号：${escapeHtml(payload.orderNo)}</div>
      </div>
      <div class="qr">${escapeHtml(payload.prescriptionNo)}</div>
    </div>
    <div class="meta">
      <div><span class="label">患者：</span>${escapeHtml(payload.patientName)}</div>
      <div><span class="label">电话：</span>${escapeHtml(payload.patientPhone)}</div>
      <div><span class="label">门诊住院：</span>${escapeHtml(hospitalTypeText(payload.hospitalType))}</div>
      <div><span class="label">处方类型：</span>${escapeHtml(prescriptionTypeText(payload.prescriptionType))}</div>
      <div><span class="label">剂数：</span>${escapeHtml(payload.doseCount)}</div>
      <div><span class="label">服用方式：</span>${escapeHtml(medicationMethodText(payload.isWithin))}</div>
      <div><span class="label">批次：</span>${escapeHtml(batchText(payload.batchNo))}</div>
      <div><span class="label">送货方式：</span>${escapeHtml(addressTypeText(payload.addressType))}</div>
      <div><span class="label">医师：</span>${escapeHtml(payload.doctorName)}</div>
      <div><span class="label">科室：</span>${escapeHtml(payload.departmentName)}</div>
      <div><span class="label">病区/床号：</span>${escapeHtml(joinDisplayParts([payload.wardName, payload.bedNo], ' / ', ''))}</div>
      <div><span class="label">配送时间：</span>${escapeHtml(formatDate(payload.deliveryTime))}</div>
    </div>
    <div class="meta" style="grid-template-columns: 1fr;">
      <div><span class="label">收货地址：</span>${escapeHtml(fullAddress(payload))}</div>
      <div><span class="label">用药说明：</span>${escapeHtml(payload.medicationInstruction || payload.medicationMethod)}</div>
      <div><span class="label">诊断/备注：</span>${escapeHtml(joinDisplayParts([payload.diagnosis, payload.prescriptionRemark], '；', ''))}</div>
    </div>
    <table>
      <thead>
        <tr><th>序号</th><th>药品名称</th><th>规格</th><th>剂量</th><th>数量</th><th>特殊用法</th><th>产地</th></tr>
      </thead>
      <tbody>${details || '<tr><td colspan="7">暂无药品明细</td></tr>'}</tbody>
    </table>
    <div class="footer">
      <div>调剂：</div>
      <div>复核：</div>
      <div>煎煮：</div>
      <div>重打时间：${escapeHtml(formatDate(payload.printedAt))}</div>
    </div>
  </section>
</body>
</html>`;
}

async function openPrintPreview(row: AdminPrescriptionReprintItem) {
  printingNo.value = row.prescriptionNo;
  errorLine.value = '';
  try {
    const payload = await getAdminPrescriptionPrintPayload(row.prescriptionNo);
    const printWindow = window.open('', '_blank', 'width=1100,height=780');
    if (!printWindow) {
      errorLine.value = '浏览器阻止了打印预览窗口';
      return;
    }
    printWindow.document.open();
    printWindow.document.write(renderPrintHtml(payload));
    printWindow.document.close();
    emit('notice', 'success', `处方 ${row.prescriptionNo} 打印预览已打开`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    printingNo.value = '';
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshPrescriptionReprints();
  },
  { immediate: true },
);

defineExpose({
  refreshPrescriptionReprints,
});
</script>

<template>
  <section class="legacy-page order-list-page prescription-reprint-page">
    <ul class="legacy-search order-list-search">
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
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadReprintCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-main-table prescription-reprint-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台处方号</th>
            <th>病人信息</th>
            <th>收货地址</th>
            <th>送货时间</th>
            <th>接单时间</th>
            <th>送医院</th>
            <th>机构名称</th>
            <th>机构处方号</th>
            <th>门诊住院</th>
            <th>处方类型</th>
            <th>服用方法</th>
            <th>剂数</th>
            <th>批次</th>
            <th>调剂工号</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="15" class="legacy-empty">正在查询处方重打列表</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="15" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" :key="row.prescriptionId" class="legacy-main-info">
            <td>{{ row.prescriptionNo }}</td>
            <td>{{ patientInfo(row) }}</td>
            <td class="legacy-left">{{ fullAddress(row) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ addressTypeText(row.addressType) }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td>{{ displayValue(row.externalPrescriptionNo) }}</td>
            <td>{{ hospitalTypeText(row.hospitalType) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionType) }}</td>
            <td>{{ medicationMethodText(row.isWithin) }}</td>
            <td>{{ displayValue(row.doseCount) }}</td>
            <td>{{ batchText(row.batchNo) }}</td>
            <td>{{ displayValue(row.dispenser) }}</td>
            <td>
              <button
                class="legacy-link-btn workflow-pass-btn"
                type="button"
                :disabled="printingNo === row.prescriptionNo"
                @click="openPrintPreview(row)"
              >
                {{ printingNo === row.prescriptionNo ? '打开中' : '处方重打' }}
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
.prescription-reprint-table th,
.prescription-reprint-table td {
  min-width: 90px;
}

.prescription-reprint-table th:nth-child(3),
.prescription-reprint-table td:nth-child(3) {
  min-width: 180px;
}
</style>
