<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  dispatchDueCallbacks,
  listCallbackRecords,
  markCallbackFailed,
  markCallbackSuccess,
  replayCallback,
} from '../../api/callback';
import {
  listReadyDeliveryOrders,
  listShipmentTraces,
  listShipments,
  packShipment,
  receiveShipmentTrace,
  shipShipment,
  signShipment,
} from '../../api/logistics';
import type { CallbackRecord, DeliveryOrderRecord, ShipmentRecord, ShipmentTraceRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { currentIsoDate, formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type LogisticsDataset = 'shipments' | 'ready' | 'callbacks';

const props = defineProps<{
  active: boolean;
  activationKey: number;
  operationOperator: string;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
  'update:operationOperator': [value: string];
}>();

const operatorModel = computed({
  get: () => props.operationOperator,
  set: (value: string) => emit('update:operationOperator', value),
});

const activeLogisticsDataset = ref<LogisticsDataset>('shipments');
const logisticsLoading = ref(false);
const logisticsError = ref('');
const logisticsLimit = ref(50);
const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const patientName = ref('');
const consignee = ref('');
const receiverPhone = ref('');
const hospitalType = ref('');
const deliveryType = ref('');
const logisticsStatus = ref('');
const logisticsOrderNo = ref('');
const queryLogisticsCompany = ref('');
const queryLogisticsNo = ref('');
const packLogisticsCompany = ref('SF');
const packPayMethod = ref('MONTHLY');
const pkgWeight = ref(1);
const pkgNum = ref(1);
const traceLogisticsNo = ref('');
const traceProvider = ref('SF');
const traceOpCode = ref('50');
const traceContent = ref('');
const selectedTraceShipmentNo = ref('');
const callbackStatus = ref('');
const callbackType = ref('');
const handlingShipmentId = ref('');
const handlingCallbackId = ref('');
const readyDeliveryOrders = ref<DeliveryOrderRecord[]>([]);
const shipments = ref<ShipmentRecord[]>([]);
const shipmentTraces = ref<ShipmentTraceRecord[]>([]);
const callbackRecords = ref<CallbackRecord[]>([]);
const logisticsRequestId = ref(0);

const activeLogisticsCount = computed(() => {
  if (activeLogisticsDataset.value === 'ready') return readyDeliveryOrders.value.length;
  if (activeLogisticsDataset.value === 'shipments') return shipments.value.length;
  return callbackRecords.value.length;
});

const logisticsDatasetNames: Record<LogisticsDataset, string> = {
  shipments: '订单发货查询',
  ready: '打包发货',
  callbacks: '回调记录',
};

const filterContractHint = computed(() => {
  if (activeLogisticsDataset.value === 'ready') {
    return '待打包列表已接入时间、机构、平台订单号/处方号、病人姓名、收货人、收货电话、门诊住院和送货方式筛选。';
  }
  return '发货查询已接入时间、机构、平台订单号/处方号、病人姓名、收货人、收货电话、门诊住院、订单状态、送货方式、物流公司和物流单号筛选。';
});

const pickupDeliveryOrders = computed(() => readyDeliveryOrders.value.filter((record) => isPickupType(record.addressType)));
const pickupShipments = computed(() => shipments.value.filter((record) => isPickupType(record.addressType)));
const hasPickupRecords = computed(() => (
  activeLogisticsDataset.value === 'ready' ? pickupDeliveryOrders.value.length > 0 : pickupShipments.value.length > 0
));

const tracePanelTitle = computed(() => (
  selectedTraceShipmentNo.value ? `轨迹补录/查询：${selectedTraceShipmentNo.value}` : '轨迹补录/查询'
));

function datasetCount(dataset: LogisticsDataset) {
  if (dataset === 'ready') return readyDeliveryOrders.value.length;
  if (dataset === 'shipments') return shipments.value.length;
  return callbackRecords.value.length;
}

function normalizedLogisticsLimit() {
  if (!Number.isFinite(logisticsLimit.value) || logisticsLimit.value <= 0) return 50;
  return Math.min(Math.trunc(logisticsLimit.value), 200);
}

function logisticsQueryParams(limit: number) {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    orderNo: logisticsOrderNo.value,
    patientName: patientName.value,
    receiverName: consignee.value,
    receiverPhone: receiverPhone.value,
    hospitalType: hospitalType.value,
    status: logisticsStatus.value,
    deliveryType: deliveryType.value,
    logisticsCompany: queryLogisticsCompany.value,
    logisticsNo: queryLogisticsNo.value,
    limit,
  };
}

function normalizedPkgWeight() {
  if (!Number.isFinite(pkgWeight.value) || pkgWeight.value < 0) return 1;
  return Number(pkgWeight.value.toFixed(2));
}

function normalizedPkgNum() {
  if (!Number.isFinite(pkgNum.value) || pkgNum.value <= 0) return 1;
  return Math.trunc(pkgNum.value);
}

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function escapeHtml(value: string | number | null | undefined) {
  return rowValue(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function isPickupType(value: string | null | undefined) {
  return !!value && /自提|到店|门店|pickup/i.test(value);
}

function paymentLabel(value: string | null) {
  if (!value) return '-';
  if (value === 'MONTHLY') return '寄付';
  if (value === 'COLLECT') return '到付';
  return value;
}

function pageSummary(total: number) {
  return `显示第 ${total > 0 ? 1 : 0} 至 ${total} 项记录，共 ${total} 项`;
}

function activeLogisticsTableColspan() {
  if (activeLogisticsDataset.value === 'callbacks') return 8;
  return 19;
}

function canShip(shipment: ShipmentRecord) {
  return shipment.logisticsStatus === 'PACKED';
}

function canSign(shipment: ShipmentRecord) {
  return shipment.logisticsStatus !== 'SIGNED';
}

function canPrintShipmentWaybill(shipment: ShipmentRecord) {
  return !!shipment.logisticsNo && !!shipment.logisticsCompany;
}

function renderReadyOrderPrintHtml(record: DeliveryOrderRecord) {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>物流清单-${escapeHtml(record.orderNo)}</title>
  <style>
    @page { size: A4; margin: 12mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 13px; }
    .toolbar { position: fixed; right: 14px; top: 14px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    h1 { margin: 0 0 14px; font-size: 22px; letter-spacing: 0; }
    .grid { display: grid; grid-template-columns: 30mm 1fr; border: 1px solid #cbd5e1; border-bottom: 0; }
    .grid div { padding: 8px; border-bottom: 1px solid #cbd5e1; }
    .label { background: #f8fafc; color: #475569; font-weight: 700; }
    @media print { .toolbar { display: none; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <h1>物流发货清单</h1>
  <section class="grid">
    <div class="label">订单号</div><div>${escapeHtml(record.orderNo)} / ${escapeHtml(record.externalOrderNo)}</div>
    <div class="label">机构/患者</div><div>${escapeHtml(record.institutionName)} / ${escapeHtml(record.patientName)}</div>
    <div class="label">收货人</div><div>${escapeHtml(record.receiverName)} / ${escapeHtml(record.receiverPhone)}</div>
    <div class="label">收货地址</div><div>${escapeHtml(record.receiverAddress)}</div>
    <div class="label">配送方式</div><div>${escapeHtml(record.addressType)}</div>
    <div class="label">订单状态</div><div>${escapeHtml(record.orderStatus)}</div>
    <div class="label">配送时间</div><div>${escapeHtml(formatDate(record.deliveryTime))}</div>
    <div class="label">接单时间</div><div>${escapeHtml(formatDate(record.orderCreatedAt))}</div>
    <div class="label">打印时间</div><div>${escapeHtml(formatDate(new Date().toISOString()))}</div>
  </section>
</body>
</html>`;
}

function renderShipmentPrintHtml(record: ShipmentRecord) {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>物流清单-${escapeHtml(record.orderNo)}</title>
  <style>
    @page { size: A4; margin: 12mm; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", Arial, sans-serif; font-size: 13px; }
    .toolbar { position: fixed; right: 14px; top: 14px; display: flex; gap: 8px; }
    .toolbar button { border: 1px solid #1d4ed8; background: #2563eb; color: white; border-radius: 4px; padding: 7px 12px; cursor: pointer; }
    h1 { margin: 0 0 14px; font-size: 22px; letter-spacing: 0; }
    .grid { display: grid; grid-template-columns: 30mm 1fr; border: 1px solid #cbd5e1; border-bottom: 0; }
    .grid div { padding: 8px; border-bottom: 1px solid #cbd5e1; }
    .label { background: #f8fafc; color: #475569; font-weight: 700; }
    @media print { .toolbar { display: none; } }
  </style>
</head>
<body>
  <div class="toolbar"><button onclick="window.print()">打印</button><button onclick="window.close()">关闭</button></div>
  <h1>物流发货清单</h1>
  <section class="grid">
    <div class="label">订单号</div><div>${escapeHtml(record.orderNo)} / ${escapeHtml(record.externalOrderNo)}</div>
    <div class="label">机构/患者</div><div>${escapeHtml(record.institutionName)} / ${escapeHtml(record.patientName)}</div>
    <div class="label">收货人</div><div>${escapeHtml(record.receiverName)} / ${escapeHtml(record.receiverPhone)}</div>
    <div class="label">收货地址</div><div>${escapeHtml(record.receiverAddress)}</div>
    <div class="label">物流</div><div>${escapeHtml(record.logisticsCompany)} / ${escapeHtml(record.logisticsNo)} / ${escapeHtml(record.logisticsStatus)}</div>
    <div class="label">件数/重量</div><div>${escapeHtml(record.pkgNum)} 件 / ${escapeHtml(record.pkgWeight)} kg</div>
    <div class="label">配送方式</div><div>${escapeHtml(record.addressType)} / ${escapeHtml(paymentLabel(record.payMethod))}</div>
    <div class="label">配送时间</div><div>${escapeHtml(formatDate(record.deliveryTime))}</div>
    <div class="label">打包/出库</div><div>${escapeHtml(formatDate(record.packageTime))} / ${escapeHtml(formatDate(record.outboundTime))}</div>
    <div class="label">签收时间</div><div>${escapeHtml(formatDate(record.signTime))}</div>
    <div class="label">打印时间</div><div>${escapeHtml(formatDate(new Date().toISOString()))}</div>
  </section>
</body>
</html>`;
}

function renderShipmentWaybillHtml(record: ShipmentRecord, reprint: boolean) {
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
      <div class="tag">${escapeHtml(record.addressType)}</div>
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
      <div class="label">件数/重量</div><div>${escapeHtml(record.pkgNum)} 件 / ${escapeHtml(record.pkgWeight)} kg</div>
      <div class="label">付款方式</div><div>${escapeHtml(paymentLabel(record.payMethod))}</div>
      <div class="label">物流状态</div><div>${escapeHtml(record.logisticsStatus)}</div>
      <div class="label">打包时间</div><div>${escapeHtml(formatDate(record.packageTime))}</div>
      <div class="label">打印时间</div><div>${escapeHtml(formatDate(new Date().toISOString()))}</div>
    </section>
    <div class="foot">浏览器面单基于系统已有物流单生成，不代表承运商电子面单下发结果。</div>
  </section>
</body>
</html>`;
}

function openPrintWindow(html: string, title: string) {
  const printWindow = window.open('', '_blank', 'width=900,height=680');
  if (!printWindow) {
    logisticsError.value = '浏览器阻止了物流清单打印窗口';
    return;
  }
  printWindow.document.open();
  printWindow.document.write(html);
  printWindow.document.close();
  emit('notice', 'success', `${title} 打印窗口已打开`);
}

function printReadyOrderList(record: DeliveryOrderRecord) {
  logisticsError.value = '';
  openPrintWindow(renderReadyOrderPrintHtml(record), `订单 ${record.orderNo} 物流清单`);
}

function printShipmentList(record: ShipmentRecord) {
  logisticsError.value = '';
  openPrintWindow(renderShipmentPrintHtml(record), `订单 ${record.orderNo} 物流清单`);
}

function printShipmentWaybill(record: ShipmentRecord, reprint = false) {
  logisticsError.value = '';
  if (!canPrintShipmentWaybill(record)) {
    logisticsError.value = '缺少物流公司或物流单号，无法生成浏览器面单';
    return;
  }
  openPrintWindow(renderShipmentWaybillHtml(record, reprint), `${record.logisticsNo} ${reprint ? '重打面单' : '浏览器面单'}`);
}

function deliveryOrderCsvRows(records: readonly DeliveryOrderRecord[]) {
  return records.map((record) => [
    record.orderNo,
    record.externalOrderNo,
    record.orderStatus,
    record.institutionName,
    record.patientName,
    record.receiverName,
    record.receiverPhone,
    record.receiverAddress,
    record.addressType,
    record.hospitalTypes,
    formatDate(record.deliveryTime),
    formatDate(record.orderCreatedAt),
  ]);
}

function shipmentCsvRows(records: readonly ShipmentRecord[]) {
  return records.map((record) => [
    record.orderNo,
    record.externalOrderNo,
    record.logisticsNo,
    record.logisticsCompany,
    record.logisticsStatus,
    paymentLabel(record.payMethod),
    record.pkgWeight,
    record.pkgNum,
    record.institutionName,
    record.patientName,
    record.receiverName,
    record.receiverPhone,
    record.receiverAddress,
    record.addressType,
    record.hospitalTypes,
    formatDate(record.deliveryTime),
    formatDate(record.orderCreatedAt),
    formatDate(record.packageTime),
    formatDate(record.outboundTime),
    formatDate(record.signTime),
  ]);
}

function exportLogisticsRecords() {
  if (activeLogisticsDataset.value === 'ready') {
    downloadCsv(
      `待打包订单-${currentIsoDate()}.csv`,
      ['订单号', '外部订单号', '订单状态', '机构', '患者', '收货人', '收货电话', '收货地址', '送货方式', '门诊住院', '送货时间', '下单时间'],
      deliveryOrderCsvRows(readyDeliveryOrders.value),
    );
    emit('notice', 'success', `已导出 ${readyDeliveryOrders.value.length} 条待打包订单`);
    return;
  }

  downloadCsv(
    `物流发货记录-${currentIsoDate()}.csv`,
    ['订单号', '外部订单号', '运单号', '物流公司', '物流状态', '收款方式', '重量', '件数', '机构', '患者', '收货人', '收货电话', '收货地址', '送货方式', '门诊住院', '送货时间', '下单时间', '打包时间', '出库时间', '签收时间'],
    shipmentCsvRows(shipments.value),
  );
  emit('notice', 'success', `已导出 ${shipments.value.length} 条物流发货记录`);
}

function exportPickupRecords() {
  if (activeLogisticsDataset.value === 'ready') {
    downloadCsv(
      `自提待打包订单-${currentIsoDate()}.csv`,
      ['订单号', '外部订单号', '订单状态', '机构', '患者', '收货人', '收货电话', '收货地址', '送货方式', '门诊住院', '送货时间', '下单时间'],
      deliveryOrderCsvRows(pickupDeliveryOrders.value),
    );
    emit('notice', 'success', `已导出 ${pickupDeliveryOrders.value.length} 条自提待打包订单`);
    return;
  }

  downloadCsv(
    `自提物流发货记录-${currentIsoDate()}.csv`,
    ['订单号', '外部订单号', '运单号', '物流公司', '物流状态', '收款方式', '重量', '件数', '机构', '患者', '收货人', '收货电话', '收货地址', '送货方式', '门诊住院', '送货时间', '下单时间', '打包时间', '出库时间', '签收时间'],
    shipmentCsvRows(pickupShipments.value),
  );
  emit('notice', 'success', `已导出 ${pickupShipments.value.length} 条自提物流发货记录`);
}

function exportCallbackRecords() {
  downloadCsv(
    `物流回调记录-${currentIsoDate()}.csv`,
    ['订单号', '业务ID', '回调类型', '状态', '重试次数', '下次重试', '创建时间', '更新时间', '请求地址', '请求内容', '响应内容'],
    callbackRecords.value.map((record) => [
      record.orderNo,
      record.businessId,
      record.callbackType,
      record.status,
      record.retryCount,
      formatDate(record.nextRetryAt),
      formatDate(record.createdAt),
      formatDate(record.updatedAt),
      record.requestUrl,
      record.requestBody,
      record.responseBody,
    ]),
  );
  emit('notice', 'success', `已导出 ${callbackRecords.value.length} 条物流回调记录`);
}

async function refreshLogisticsRecords() {
  const requestId = logisticsRequestId.value + 1;
  logisticsRequestId.value = requestId;
  logisticsLoading.value = true;
  logisticsError.value = '';
  const dataset = activeLogisticsDataset.value;
  const limit = normalizedLogisticsLimit();
  logisticsLimit.value = limit;
  try {
    const queryParams = logisticsQueryParams(limit);
    if (dataset === 'ready') {
      const records = await listReadyDeliveryOrders(queryParams);
      if (requestId !== logisticsRequestId.value) return;
      readyDeliveryOrders.value = records;
    } else if (dataset === 'shipments') {
      const records = await listShipments(queryParams);
      if (requestId !== logisticsRequestId.value) return;
      shipments.value = records;
    } else {
      const records = await listCallbackRecords({
        status: callbackStatus.value,
        callbackType: callbackType.value,
        limit,
      });
      if (requestId !== logisticsRequestId.value) return;
      callbackRecords.value = records;
    }
    emit('notice', 'info', `已刷新${logisticsDatasetNames[dataset]}：${datasetCount(dataset)} 条`);
  } catch (error) {
    if (requestId === logisticsRequestId.value) {
      logisticsError.value = errorMessage(error);
    }
  } finally {
    if (requestId === logisticsRequestId.value) {
      logisticsLoading.value = false;
    }
  }
}

function switchLogisticsDataset(dataset: LogisticsDataset) {
  activeLogisticsDataset.value = dataset;
  logisticsError.value = '';
  void refreshLogisticsRecords();
}

async function handlePackShipment(order: DeliveryOrderRecord) {
  handlingShipmentId.value = order.orderId;
  logisticsError.value = '';
  try {
    pkgWeight.value = normalizedPkgWeight();
    pkgNum.value = normalizedPkgNum();
    const shipment = await packShipment({
      orderNo: order.orderNo,
      logisticsCompany: packLogisticsCompany.value.trim() || 'SF',
      payMethod: packPayMethod.value.trim() || 'MONTHLY',
      pkgWeight: pkgWeight.value,
      pkgNum: pkgNum.value,
      operator: operatorModel.value.trim() || 'admin',
    });
    emit('notice', 'success', `${shipment.orderNo} 已打包，运单 ${shipment.logisticsNo}`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    handlingShipmentId.value = '';
  }
}

async function handleShipmentAction(shipment: ShipmentRecord, action: 'ship' | 'sign') {
  handlingShipmentId.value = shipment.shipmentId;
  logisticsError.value = '';
  try {
    const command = {
      operator: operatorModel.value.trim() || 'admin',
      remark: traceContent.value.trim() || undefined,
    };
    const result = action === 'ship'
      ? await shipShipment(shipment.shipmentId, command)
      : await signShipment(shipment.shipmentId, command);
    emit('notice', 'success', `${result.orderNo} 已${action === 'ship' ? '发货' : '签收'}`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    handlingShipmentId.value = '';
  }
}

async function handleReceiveTrace() {
  if (!traceLogisticsNo.value.trim()) {
    logisticsError.value = '请输入运单号';
    return;
  }
  logisticsLoading.value = true;
  logisticsError.value = '';
  try {
    const shipment = await receiveShipmentTrace({
      logisticsNo: traceLogisticsNo.value.trim(),
      provider: traceProvider.value.trim() || 'SF',
      opCode: traceOpCode.value.trim(),
      traceContent: traceContent.value.trim() || undefined,
      rawPayload: JSON.stringify({ source: 'admin-web', opCode: traceOpCode.value.trim() }),
      traceTime: new Date().toISOString(),
      operator: operatorModel.value.trim() || 'admin',
    });
    selectedTraceShipmentNo.value = shipment.logisticsNo;
    emit('notice', 'success', `${shipment.logisticsNo} 轨迹已记录为 ${shipment.logisticsStatus}`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    logisticsLoading.value = false;
  }
}

async function refreshShipmentTraces(shipment: ShipmentRecord) {
  logisticsError.value = '';
  selectedTraceShipmentNo.value = shipment.logisticsNo;
  try {
    shipmentTraces.value = await listShipmentTraces(shipment.shipmentId);
  } catch (error) {
    shipmentTraces.value = [];
    logisticsError.value = errorMessage(error);
  }
}

async function handleCallbackAction(record: CallbackRecord, action: 'success' | 'failed' | 'replay') {
  handlingCallbackId.value = record.id;
  logisticsError.value = '';
  try {
    if (action === 'success') {
      await markCallbackSuccess(record.id);
    } else if (action === 'failed') {
      await markCallbackFailed(record.id);
    } else {
      await replayCallback(record.id);
    }
    emit('notice', 'success', `${record.callbackType} 已处理`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    handlingCallbackId.value = '';
  }
}

async function handleDispatchDueCallbacks() {
  logisticsLoading.value = true;
  logisticsError.value = '';
  try {
    const handled = await dispatchDueCallbacks(normalizedLogisticsLimit());
    emit('notice', 'success', `已派发到期回调 ${handled} 条`);
    await refreshLogisticsRecords();
  } catch (error) {
    logisticsError.value = errorMessage(error);
  } finally {
    logisticsLoading.value = false;
  }
}

watch(activeLogisticsCount, (count) => emit('countChanged', count), { immediate: true });

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshLogisticsRecords();
  },
  { immediate: true },
);

defineExpose({
  refreshLogisticsRecords,
});
</script>

<template>
  <section class="legacy-page logistics-page">
    <ul class="legacy-search logistics-mode-search">
      <li class="logistics-mode-item">
        <button
          class="legacy-link-btn"
          :class="{ active: activeLogisticsDataset === 'shipments' }"
          type="button"
          @click="switchLogisticsDataset('shipments')"
        >
          订单发货查询
        </button>
        <button
          class="legacy-link-btn"
          :class="{ active: activeLogisticsDataset === 'ready' }"
          type="button"
          @click="switchLogisticsDataset('ready')"
        >
          打包发货
        </button>
        <button
          class="legacy-link-btn"
          :class="{ active: activeLogisticsDataset === 'callbacks' }"
          type="button"
          @click="switchLogisticsDataset('callbacks')"
        >
          回调记录
        </button>
      </li>
    </ul>

    <template v-if="activeLogisticsDataset !== 'callbacks'">
      <ul class="legacy-search logistics-search">
        <li>
          开始时间：
          <input v-model="startTime" class="legacy-input input-large" placeholder="YYYY-MM-DD HH:mm:ss" />
        </li>
        <li>
          结束时间：
          <input v-model="endTime" class="legacy-input input-large" placeholder="YYYY-MM-DD HH:mm:ss" />
        </li>
        <li>
          机构：
          <select v-model="institution" class="legacy-input input-large">
            <option value="">请选择</option>
            <option value="良益堂煎药中心">良益堂煎药中心</option>
            <option value="广州良益堂（康正堂店）">广州良益堂（康正堂店）</option>
            <option value="代煎代配药房">代煎代配药房</option>
          </select>
        </li>
        <li>
          平台订单号/处方号：
          <input v-model="logisticsOrderNo" class="legacy-input input-large" placeholder="订单号 / 处方号" @keyup.enter="refreshLogisticsRecords" />
        </li>
        <li>
          病人姓名：
          <input v-model="patientName" class="legacy-input input-large" />
        </li>
        <li>
          收货人：
          <input v-model="consignee" class="legacy-input input-large" />
        </li>
        <li>
          收货电话：
          <input v-model="receiverPhone" class="legacy-input input-large" />
        </li>
        <li>
          门诊住院：
          <select v-model="hospitalType" class="legacy-input">
            <option value="">请选择</option>
            <option value="门诊">门诊</option>
            <option value="住院">住院</option>
            <option value="其他">其他</option>
          </select>
        </li>
        <li>
          订单状态：
          <select v-model="logisticsStatus" class="legacy-input" @change="refreshLogisticsRecords">
            <option value="">请选择</option>
            <option value="PACKED">已打包</option>
            <option value="SHIPPED">已出库</option>
            <option value="SIGNED">已签收</option>
          </select>
        </li>
        <li>
          送货方式：
          <select v-model="deliveryType" class="legacy-input">
            <option value="">请选择</option>
            <option value="默认">默认</option>
            <option value="送医院">送医院</option>
            <option value="送个人">送个人</option>
          </select>
        </li>
        <li>
          物流公司：
          <input v-model="queryLogisticsCompany" class="legacy-input" placeholder="SF / EMS" />
        </li>
        <li>
          物流单号：
          <input v-model="queryLogisticsNo" class="legacy-input input-large" placeholder="运单号" />
        </li>
        <li>
          条数：
          <input v-model.number="logisticsLimit" class="legacy-input input-small" type="number" min="1" max="200" step="10" @keyup.enter="refreshLogisticsRecords" />
        </li>
        <li>
          操作人：
          <input v-model="operatorModel" class="legacy-input" placeholder="admin" />
        </li>
        <li>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="logisticsLoading" @click="refreshLogisticsRecords">
            {{ logisticsLoading ? '查询中' : '查询' }}
          </button>
        </li>
        <li>
          <button class="legacy-btn legacy-btn-export" type="button" :disabled="logisticsLoading || activeLogisticsCount === 0" @click="exportLogisticsRecords">导出</button>
        </li>
        <li>
          <button class="legacy-btn legacy-btn-export" type="button" :disabled="logisticsLoading || !hasPickupRecords" @click="exportPickupRecords">导出自提订单</button>
        </li>
      </ul>

      <p class="logistics-filter-hint">
        {{ filterContractHint }}
      </p>

      <ul v-if="activeLogisticsDataset === 'ready'" class="legacy-search logistics-pack-search">
        <li>
          打包物流公司：
          <input v-model="packLogisticsCompany" class="legacy-input" placeholder="SF / EMS" />
        </li>
        <li>
          收款方式：
          <select v-model="packPayMethod" class="legacy-input">
            <option value="MONTHLY">寄付</option>
            <option value="COLLECT">到付</option>
          </select>
        </li>
        <li>
          重量 kg：
          <input v-model.number="pkgWeight" class="legacy-input input-small" type="number" min="0" step="0.1" />
        </li>
        <li>
          件数：
          <input v-model.number="pkgNum" class="legacy-input input-small" type="number" min="1" step="1" />
        </li>
      </ul>
    </template>

    <ul v-else class="legacy-search logistics-search">
      <li>
        状态：
        <input v-model="callbackStatus" class="legacy-input input-large" placeholder="PENDING / SUCCESS / FAILED / DEAD" @keyup.enter="refreshLogisticsRecords" />
      </li>
      <li>
        回调类型：
        <input v-model="callbackType" class="legacy-input input-large" placeholder="ORDER_SHIPPED" @keyup.enter="refreshLogisticsRecords" />
      </li>
      <li>
        条数：
        <input v-model.number="logisticsLimit" class="legacy-input input-small" type="number" min="1" max="200" step="10" @keyup.enter="refreshLogisticsRecords" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="logisticsLoading" @click="refreshLogisticsRecords">
          {{ logisticsLoading ? '刷新中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-export" type="button" :disabled="logisticsLoading" @click="handleDispatchDueCallbacks">
          派发到期回调
        </button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-export" type="button" :disabled="logisticsLoading || callbackRecords.length === 0" @click="exportCallbackRecords">
          导出回调
        </button>
      </li>
    </ul>

    <p v-if="logisticsError" class="error-line">{{ logisticsError }}</p>

    <div class="legacy-panel">
      <table
        class="legacy-main-table logistics-main-table"
        :class="{ 'logistics-callback-table': activeLogisticsDataset === 'callbacks' }"
      >
        <thead>
          <tr v-if="activeLogisticsDataset !== 'callbacks'" class="legacy-main-head">
            <th>订单号</th>
            <th>下单时间</th>
            <th>件数</th>
            <th>物流公司</th>
            <th>运单号</th>
            <th>打包时间</th>
            <th>出库时间</th>
            <th>签收时间</th>
            <th>重量</th>
            <th>病人姓名</th>
            <th>收款方式</th>
            <th>送货方式</th>
            <th>收货人</th>
            <th>收货电话</th>
            <th>送货日期</th>
            <th>收货地址</th>
            <th>订单来源</th>
            <th>订单状态</th>
            <th>操作</th>
          </tr>
          <tr v-else class="legacy-main-head">
            <th>订单号</th>
            <th>业务 ID</th>
            <th>回调类型</th>
            <th>状态</th>
            <th>重试次数</th>
            <th>下次重试</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="logisticsLoading" class="legacy-main-info">
            <td :colspan="activeLogisticsTableColspan()" class="legacy-empty">正在查询物流数据</td>
          </tr>
          <template v-else-if="activeLogisticsDataset === 'ready'">
            <tr v-if="readyDeliveryOrders.length === 0" class="legacy-main-info">
              <td colspan="19" class="legacy-empty">暂无待打包订单</td>
            </tr>
            <tr v-for="item in readyDeliveryOrders" :key="item.orderId" class="legacy-main-info">
              <td>{{ item.orderNo }}</td>
              <td>{{ formatDate(item.orderCreatedAt) }}</td>
              <td>-</td>
              <td>-</td>
              <td>-</td>
              <td>-</td>
              <td>-</td>
              <td>-</td>
              <td>-</td>
              <td>{{ rowValue(item.patientName) }}</td>
              <td>-</td>
              <td>{{ rowValue(item.addressType) }}</td>
              <td>{{ item.receiverName }}</td>
              <td>{{ item.receiverPhone }}</td>
              <td>{{ formatDate(item.deliveryTime) }}</td>
              <td class="legacy-left">{{ item.receiverAddress }}</td>
              <td>{{ rowValue(item.externalOrderNo) }}</td>
              <td><StatusPill :value="item.orderStatus" :tone="statusTone(item.orderStatus)" /></td>
              <td class="logistics-action-cell">
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingShipmentId === item.orderId" @click="handlePackShipment(item)">
                  打包
                </button>
                <button class="legacy-link-btn" type="button" @click="printReadyOrderList(item)">打印清单</button>
                <button class="legacy-link-btn" type="button" disabled title="待打包订单尚未生成物流单号">面单(待物流单)</button>
                <button class="legacy-link-btn" type="button" disabled title="待打包订单尚未生成物流单号">重打(待物流单)</button>
              </td>
            </tr>
          </template>
          <template v-else-if="activeLogisticsDataset === 'shipments'">
            <tr v-if="shipments.length === 0" class="legacy-main-info">
              <td colspan="19" class="legacy-empty">暂无发货记录</td>
            </tr>
            <tr v-for="shipment in shipments" :key="shipment.shipmentId" class="legacy-main-info">
              <td>{{ shipment.orderNo }}</td>
              <td>{{ formatDate(shipment.orderCreatedAt) }}</td>
              <td>{{ rowValue(shipment.pkgNum) }}</td>
              <td>{{ shipment.logisticsCompany }}</td>
              <td>{{ shipment.logisticsNo }}</td>
              <td>{{ formatDate(shipment.packageTime) }}</td>
              <td>{{ formatDate(shipment.outboundTime) }}</td>
              <td>{{ formatDate(shipment.signTime) }}</td>
              <td>{{ rowValue(shipment.pkgWeight) }}</td>
              <td>{{ rowValue(shipment.patientName) }}</td>
              <td>{{ paymentLabel(shipment.payMethod) }}</td>
              <td>{{ rowValue(shipment.addressType) }}</td>
              <td>{{ rowValue(shipment.receiverName) }}</td>
              <td>{{ rowValue(shipment.receiverPhone) }}</td>
              <td>{{ formatDate(shipment.deliveryTime) }}</td>
              <td class="legacy-left">{{ rowValue(shipment.receiverAddress) }}</td>
              <td>{{ rowValue(shipment.externalOrderNo) }}</td>
              <td><StatusPill :value="shipment.logisticsStatus" :tone="statusTone(shipment.logisticsStatus)" /></td>
              <td class="logistics-action-cell">
                <button class="legacy-link-btn" type="button" @click="refreshShipmentTraces(shipment)">轨迹</button>
                <button class="legacy-link-btn" type="button" @click="printShipmentList(shipment)">打印清单</button>
                <button
                  class="legacy-link-btn"
                  type="button"
                  :disabled="!canPrintShipmentWaybill(shipment)"
                  title="基于已有物流单生成浏览器面单"
                  @click="printShipmentWaybill(shipment)"
                >
                  浏览器面单
                </button>
                <button
                  class="legacy-link-btn"
                  type="button"
                  :disabled="!canPrintShipmentWaybill(shipment)"
                  title="重新打开浏览器面单打印窗口"
                  @click="printShipmentWaybill(shipment, true)"
                >
                  重打面单
                </button>
                <button
                  class="legacy-link-btn workflow-pass-btn"
                  type="button"
                  :disabled="handlingShipmentId === shipment.shipmentId || !canShip(shipment)"
                  @click="handleShipmentAction(shipment, 'ship')"
                >
                  发货
                </button>
                <button
                  class="legacy-link-btn workflow-pass-btn"
                  type="button"
                  :disabled="handlingShipmentId === shipment.shipmentId || !canSign(shipment)"
                  @click="handleShipmentAction(shipment, 'sign')"
                >
                  签收
                </button>
              </td>
            </tr>
          </template>
          <template v-else>
            <tr v-if="callbackRecords.length === 0" class="legacy-main-info">
              <td colspan="8" class="legacy-empty">暂无回调记录</td>
            </tr>
            <tr v-for="record in callbackRecords" :key="record.id" class="legacy-main-info">
              <td>{{ record.orderNo }}</td>
              <td>{{ record.businessId }}</td>
              <td>{{ record.callbackType }}</td>
              <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
              <td>{{ record.retryCount }}</td>
              <td>{{ formatDate(record.nextRetryAt) }}</td>
              <td>{{ formatDate(record.createdAt) }}</td>
              <td class="logistics-action-cell">
                <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'success')">
                  标记成功
                </button>
                <button class="legacy-link-btn workflow-reject-btn" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'failed')">
                  标记失败
                </button>
                <button class="legacy-link-btn" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'replay')">
                  重放
                </button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">
      {{ pageSummary(activeLogisticsCount) }}
    </p>

    <section v-if="activeLogisticsDataset === 'shipments'" class="legacy-panel logistics-trace-panel">
      <div class="logistics-section-title">
        <h2>{{ tracePanelTitle }}</h2>
        <span>补录按运单号提交；查询请在发货记录行内点击“轨迹”。</span>
      </div>

      <ul class="legacy-search logistics-trace-search">
        <li>
          运单号：
          <input v-model="traceLogisticsNo" class="legacy-input input-large" placeholder="请输入运单号" />
        </li>
        <li>
          来源：
          <input v-model="traceProvider" class="legacy-input" placeholder="SF / EMS" />
        </li>
        <li>
          轨迹码：
          <input v-model="traceOpCode" class="legacy-input" placeholder="50 / 80 / 203" />
        </li>
        <li class="logistics-trace-content">
          轨迹说明：
          <input v-model="traceContent" class="legacy-input input-large" placeholder="已揽收 / 已签收" @keyup.enter="handleReceiveTrace" />
        </li>
        <li>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="logisticsLoading" @click="handleReceiveTrace">
            补录轨迹
          </button>
        </li>
      </ul>

      <table class="legacy-main-table logistics-trace-table">
        <thead>
          <tr class="legacy-main-head">
            <th>运单</th>
            <th>轨迹状态</th>
            <th>轨迹时间</th>
            <th>内容</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="shipmentTraces.length === 0" class="legacy-main-info">
            <td colspan="4" class="legacy-empty">暂无轨迹明细</td>
          </tr>
          <tr v-for="trace in shipmentTraces" :key="trace.traceId" class="legacy-main-info">
            <td>{{ trace.logisticsNo }}</td>
            <td><StatusPill :value="trace.traceStatus" :tone="statusTone(trace.traceStatus)" /></td>
            <td>{{ formatDate(trace.traceTime) }}</td>
            <td class="legacy-left"><code>{{ trace.traceContent || trace.rawPayload }}</code></td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.logistics-filter-hint {
  margin: -4px 0 10px;
  color: #6f7d91;
  font-size: 13px;
  line-height: 1.6;
}

.logistics-section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.logistics-section-title h2 {
  margin: 0;
  color: #1f5fa3;
  font-size: 15px;
}

.logistics-section-title span {
  color: #6f7d91;
  font-size: 13px;
}

@media (max-width: 780px) {
  .logistics-section-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
