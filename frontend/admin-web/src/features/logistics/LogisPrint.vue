<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { listShipments } from '../../api/logistics';
import type { ShipmentRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { amountValue, boundedPositiveInteger, pageSummaryText, displayValue, currentIsoTimestamp, formatDate, formatNumber, labelFromMap, sumNumbers } from '../../domain/formatters';
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
const orderNo = ref('');
const patientName = ref('');
const receiverName = ref('');
const receiverPhone = ref('');
const logisticsCompany = ref('');
const logisticsNo = ref('');
const status = ref('');
const limit = ref(50);
const loading = ref(false);
const printing = ref(false);
const errorLine = ref('');
const records = ref<ShipmentRecord[]>([]);
const requestId = ref(0);

const totalWeight = computed(() => sumNumbers(records.value.map((record) => record.pkgWeight)));
const totalPackages = computed(() => sumNumbers(records.value.map((record) => record.pkgNum)));

function normalizedLimit() {
  return boundedPositiveInteger(limit.value, 50, 200);
}

function statusText(value: string | null | undefined) {
  if (!value) return '-';
  const labels: Record<string, string> = {
    PACKED: '已打包',
    SHIPPED: '已发货',
    IN_TRANSIT: '运输中',
    SIGNED: '已签收',
    FAILED: '失败',
    CANCELLED: '已取消',
  };
  return labelFromMap(value, labels);
}

function deliveryTypeText(value: string | null | undefined) {
  if (!value) return '-';
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    PATIENT: '送个人',
    PICKUP: '自提',
  };
  return labelFromMap(value, labels);
}

function downloadLogisPrintCsv() {
  downloadCsv(
    `物流发货清单-${limit.value}条.csv`,
    [
      '平台订单号',
      '外部订单号',
      '机构',
      '患者',
      '收货人',
      '收货电话',
      '收货地址',
      '配送方式',
      '物流公司',
      '物流单号',
      '物流状态',
      '包裹数',
      '重量',
      '配送时间',
      '打包时间',
      '出库时间',
      '签收时间',
      '创建时间',
      '更新时间',
    ],
    records.value.map((record) => [
      record.orderNo,
      record.externalOrderNo,
      record.institutionName,
      record.patientName,
      record.receiverName,
      record.receiverPhone,
      record.receiverAddress,
      deliveryTypeText(record.addressType),
      record.logisticsCompany,
      record.logisticsNo,
      statusText(record.logisticsStatus),
      record.pkgNum,
      record.pkgWeight,
      formatDate(record.deliveryTime),
      formatDate(record.packageTime),
      formatDate(record.outboundTime),
      formatDate(record.signTime),
      formatDate(record.createdAt),
      formatDate(record.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${formatNumber(records.value.length)} 条物流发货清单`);
}

async function refreshLogisPrints() {
  const nextRequestId = requestId.value + 1;
  requestId.value = nextRequestId;
  loading.value = true;
  errorLine.value = '';
  limit.value = normalizedLimit();
  try {
    const nextRecords = await listShipments({
      startTime: startTime.value,
      endTime: endTime.value,
      orderNo: orderNo.value,
      patientName: patientName.value,
      receiverName: receiverName.value,
      receiverPhone: receiverPhone.value,
      logisticsCompany: logisticsCompany.value,
      logisticsNo: logisticsNo.value,
      status: status.value,
      limit: limit.value,
    });
    if (nextRequestId !== requestId.value) return;
    records.value = nextRecords;
    emit('countChanged', nextRecords.length);
    emit('notice', 'info', `已查询到 ${nextRecords.length} 条物流清单记录`);
  } catch (error) {
    if (nextRequestId === requestId.value) {
      records.value = [];
      errorLine.value = errorMessage(error);
      emit('countChanged', 0);
    }
  } finally {
    if (nextRequestId === requestId.value) {
      loading.value = false;
    }
  }
}

function resetFilters() {
  startTime.value = '';
  endTime.value = '';
  orderNo.value = '';
  patientName.value = '';
  receiverName.value = '';
  receiverPhone.value = '';
  logisticsCompany.value = '';
  logisticsNo.value = '';
  status.value = '';
  void refreshLogisPrints();
}

function escapeHtml(value: string | number | null | undefined) {
  return displayValue(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function renderPrintRows() {
  if (records.value.length === 0) return '<tr><td colspan="13">没有可打印的物流记录</td></tr>';
  return records.value.map((record, index) => `
    <tr>
      <td>${index + 1}</td>
      <td>${escapeHtml(record.orderNo)}</td>
      <td>${escapeHtml(record.externalOrderNo)}</td>
      <td>${escapeHtml(record.institutionName)}</td>
      <td>${escapeHtml(record.patientName)}</td>
      <td>${escapeHtml(record.receiverName)}</td>
      <td>${escapeHtml(record.receiverPhone)}</td>
      <td>${escapeHtml(record.receiverAddress)}</td>
      <td>${escapeHtml(record.logisticsCompany)}</td>
      <td>${escapeHtml(record.logisticsNo)}</td>
      <td>${escapeHtml(statusText(record.logisticsStatus))}</td>
      <td>${escapeHtml(amountValue(record.pkgWeight))}</td>
      <td>${escapeHtml(formatDate(record.deliveryTime))}</td>
    </tr>
  `).join('');
}

function renderPrintHtml() {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>物流发货清单</title>
  <style>
    @page { size: A4 landscape; margin: 10mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 11px; }
    .toolbar { position: fixed; right: 14px; top: 14px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    h1 { margin: 0 0 8px; font-size: 20px; letter-spacing: 0; }
    .meta { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 10px; color: #475569; }
    table { width: 100%; border-collapse: collapse; }
    th, td { border: 1px solid #cbd5e1; padding: 5px; text-align: left; vertical-align: top; }
    th { background: #f1f5f9; }
    @media print { .toolbar { display: none; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <h1>物流发货清单</h1>
  <div class="meta">
    <span>打印时间：${escapeHtml(formatDate(currentIsoTimestamp()))}</span>
    <span>记录数：${records.value.length}</span>
    <span>总件数：${escapeHtml(amountValue(totalPackages.value))}</span>
    <span>总重量：${escapeHtml(amountValue(totalWeight.value))}</span>
  </div>
  <table>
    <thead>
      <tr>
        <th>序号</th><th>平台订单号</th><th>外部订单号</th><th>机构</th><th>患者</th><th>收货人</th><th>电话</th><th>地址</th><th>物流公司</th><th>物流单号</th><th>状态</th><th>重量</th><th>配送时间</th>
      </tr>
    </thead>
    <tbody>${renderPrintRows()}</tbody>
  </table>
</body>
</html>`;
}

function canPrintWaybill(record: ShipmentRecord) {
  return !!record.logisticsNo && !!record.logisticsCompany;
}

function renderWaybillHtml(record: ShipmentRecord, reprint: boolean) {
  const printTitle = reprint ? '物流面单重打' : '物流面单';
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>${printTitle}-${escapeHtml(record.logisticsNo)}</title>
  <style>
    @page { size: 100mm 150mm; margin: 6mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 12px; }
    .toolbar { position: fixed; right: 10px; top: 10px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    .waybill { min-height: 138mm; border: 2px solid #111827; padding: 5mm; display: flex; flex-direction: column; gap: 3mm; }
    .head { display: flex; justify-content: space-between; gap: 8px; border-bottom: 1px solid #111827; padding-bottom: 3mm; }
    .company { font-size: 20px; font-weight: 800; }
    .tag { align-self: flex-start; border: 1px solid #111827; padding: 2px 6px; font-weight: 700; }
    .no { border: 1px dashed #111827; padding: 3mm; text-align: center; }
    .no strong { display: block; font-family: Consolas, monospace; font-size: 20px; letter-spacing: 1px; }
    .barcode { margin-top: 2mm; height: 14mm; background: repeating-linear-gradient(90deg, #111827 0 2px, transparent 2px 5px, #111827 5px 6px, transparent 6px 10px); }
    .block { border: 1px solid #cbd5e1; }
    .block-title { background: #f1f5f9; padding: 2mm; color: #475569; font-weight: 700; }
    .block-body { padding: 2mm; line-height: 1.55; word-break: break-word; }
    .receiver { font-size: 15px; font-weight: 700; }
    .grid { display: grid; grid-template-columns: 24mm 1fr; border: 1px solid #cbd5e1; border-bottom: 0; }
    .grid div { padding: 2mm; border-bottom: 1px solid #cbd5e1; }
    .label { background: #f8fafc; color: #475569; font-weight: 700; }
    .foot { margin-top: auto; color: #64748b; font-size: 10px; }
    @media print { .toolbar { display: none; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <section class="waybill">
    <header class="head">
      <div>
        <div class="company">${escapeHtml(record.logisticsCompany)}</div>
        <div>${escapeHtml(printTitle)}</div>
      </div>
      <div class="tag">${escapeHtml(deliveryTypeText(record.addressType))}</div>
    </header>
    <section class="no">
      <span>物流单号</span>
      <strong>${escapeHtml(record.logisticsNo)}</strong>
      <div class="barcode" aria-hidden="true"></div>
    </section>
    <section class="block">
      <div class="block-title">收件信息</div>
      <div class="block-body">
        <div class="receiver">${escapeHtml(record.receiverName)} / ${escapeHtml(record.receiverPhone)}</div>
        <div>${escapeHtml(record.receiverAddress)}</div>
      </div>
    </section>
    <section class="block">
      <div class="block-title">订单信息</div>
      <div class="block-body">
        <div>平台订单号：${escapeHtml(record.orderNo)}</div>
        <div>外部订单号：${escapeHtml(record.externalOrderNo)}</div>
        <div>机构/患者：${escapeHtml(record.institutionName)} / ${escapeHtml(record.patientName)}</div>
      </div>
    </section>
    <section class="grid">
      <div class="label">件数/重量</div><div>${escapeHtml(amountValue(record.pkgNum))} 件 / ${escapeHtml(amountValue(record.pkgWeight))} kg</div>
      <div class="label">物流状态</div><div>${escapeHtml(statusText(record.logisticsStatus))}</div>
      <div class="label">打包时间</div><div>${escapeHtml(formatDate(record.packageTime))}</div>
      <div class="label">打印时间</div><div>${escapeHtml(formatDate(currentIsoTimestamp()))}</div>
    </section>
    <div class="foot">浏览器面单基于系统已有物流单生成，不代表承运商电子面单下发结果。</div>
  </section>
</body>
</html>`;
}

function printCurrentList() {
  printing.value = true;
  errorLine.value = '';
  try {
    const printWindow = window.open('', '_blank', 'width=1200,height=820');
    if (!printWindow) {
      errorLine.value = '浏览器阻止了物流清单打印窗口';
      return;
    }
    printWindow.document.open();
    printWindow.document.write(renderPrintHtml());
    printWindow.document.close();
    emit('notice', 'success', '物流发货清单打印窗口已打开');
  } finally {
    printing.value = false;
  }
}

function printWaybill(record: ShipmentRecord, reprint = false) {
  errorLine.value = '';
  if (!canPrintWaybill(record)) {
    errorLine.value = '缺少物流公司或物流单号，无法生成浏览器面单';
    return;
  }
  const printWindow = window.open('', '_blank', 'width=900,height=680');
  if (!printWindow) {
    errorLine.value = '浏览器阻止了物流面单打印窗口';
    return;
  }
  printWindow.document.open();
  printWindow.document.write(renderWaybillHtml(record, reprint));
  printWindow.document.close();
  emit('notice', 'success', `${record.logisticsNo} ${reprint ? '重打面单' : '浏览器面单'}窗口已打开`);
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshLogisPrints();
  },
  { immediate: true },
);

defineExpose({
  refreshLogisPrints,
});
</script>

<template>
  <section class="legacy-page logis-print-page">
    <ul class="legacy-search logis-print-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        平台订单号：
        <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        患者：
        <input v-model="patientName" class="legacy-input input-medium" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        收货人：
        <input v-model="receiverName" class="legacy-input input-medium" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        手机：
        <input v-model="receiverPhone" class="legacy-input input-medium" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        物流公司：
        <input v-model="logisticsCompany" class="legacy-input input-medium" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        物流单号：
        <input v-model="logisticsNo" class="legacy-input input-large" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        状态：
        <input v-model="status" class="legacy-input input-medium" placeholder="PACKED / SHIPPED" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        条数：
        <input v-model.number="limit" class="legacy-input input-small" type="number" min="1" max="200" step="10" @keyup.enter="refreshLogisPrints" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="refreshLogisPrints">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">重置</button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || records.length === 0" @click="downloadLogisPrintCsv">
          导出当前结果
        </button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-export" type="button" :disabled="printing || records.length === 0" @click="printCurrentList">
          {{ printing ? '打开中' : '打印当前清单' }}
        </button>
      </li>
    </ul>

    <p class="logis-print-hint">
      当前页面基于真实物流单生成浏览器发货清单、行级浏览器面单和导出文件；后端已提供老接口兼容的电子面单契约，承运商真实下发需配置外部服务。
    </p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats logis-print-stats">
      <li>
        <strong>{{ records.length }}</strong>
        <span>记录数</span>
      </li>
      <li>
        <strong>{{ amountValue(totalPackages) }}</strong>
        <span>件数合计</span>
      </li>
      <li>
        <strong>{{ amountValue(totalWeight) }}</strong>
        <span>重量合计</span>
      </li>
    </ul>

    <table class="legacy-main-table logis-print-table">
      <thead>
        <tr class="legacy-main-head">
          <th>平台订单号</th>
          <th>外部订单号</th>
          <th>机构/患者</th>
          <th>收货信息</th>
          <th>配送方式</th>
          <th>物流公司</th>
          <th>物流单号</th>
          <th>物流状态</th>
          <th>件数/重量</th>
          <th>打包/出库</th>
          <th>签收时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading" class="legacy-main-info">
          <td colspan="12" class="legacy-empty">正在查询物流单</td>
        </tr>
        <tr v-else-if="records.length === 0" class="legacy-main-info">
          <td colspan="12" class="legacy-empty">没有相关数据</td>
        </tr>
        <tr v-for="record in records" :key="record.shipmentId" class="legacy-main-info">
          <td>{{ record.orderNo }}</td>
          <td>{{ record.externalOrderNo }}</td>
          <td>
            <strong>{{ displayValue(record.institutionName) }}</strong>
            <small>{{ displayValue(record.patientName) }}</small>
          </td>
          <td class="legacy-left">
            <strong>{{ displayValue(record.receiverName) }} / {{ displayValue(record.receiverPhone) }}</strong>
            <small>{{ displayValue(record.receiverAddress) }}</small>
          </td>
          <td>{{ deliveryTypeText(record.addressType) }}</td>
          <td>{{ displayValue(record.logisticsCompany) }}</td>
          <td>{{ displayValue(record.logisticsNo) }}</td>
          <td><StatusPill :value="statusText(record.logisticsStatus)" :tone="statusTone(record.logisticsStatus)" /></td>
          <td>
            <strong>{{ amountValue(record.pkgNum) }} 件</strong>
            <small>{{ amountValue(record.pkgWeight) }} kg</small>
          </td>
          <td>
            <strong>{{ formatDate(record.packageTime) }}</strong>
            <small>{{ formatDate(record.outboundTime) }}</small>
          </td>
          <td>{{ formatDate(record.signTime) }}</td>
          <td class="logis-print-actions">
            <button
              class="legacy-link-btn"
              type="button"
              :disabled="!canPrintWaybill(record)"
              title="基于已有物流单生成浏览器面单"
              @click="printWaybill(record)"
            >
              浏览器面单
            </button>
            <button
              class="legacy-link-btn"
              type="button"
              :disabled="!canPrintWaybill(record)"
              title="重新打开浏览器面单打印窗口"
              @click="printWaybill(record, true)"
            >
              重打面单
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="page_and_btn">
      <div class="dataTables_info">{{ pageSummaryText(records.length) }}</div>
    </div>
  </section>
</template>

<style scoped>
.logis-print-search {
  row-gap: 10px;
}

.logis-print-hint {
  margin: 0 0 10px;
  color: #6f7d91;
  font-size: 13px;
}

.logis-print-stats {
  margin-bottom: 10px;
}

.logis-print-table {
  min-width: 1320px;
}

.logis-print-table th,
.logis-print-table td {
  min-width: 88px;
}

.logis-print-table th:nth-child(4),
.logis-print-table td:nth-child(4) {
  min-width: 220px;
  white-space: normal;
}

.logis-print-actions {
  min-width: 140px;
}
</style>
