<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  listAdminManualProcessOrders,
  manualProcessAdminOrder,
} from '../../api/order';
import type {
  AdminManualProcessItem,
  AdminManualProcessPage,
  AdminManualProcessQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { displayValue, currentIsoDate, formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

interface ProcessDialog {
  row: AdminManualProcessItem;
  operator: string;
  auditor: string;
  auditTime: string;
  dispenser: string;
  dispenseTime: string;
  rechecker: string;
  recheckTime: string;
  pailNo: string;
  soakTimeStart: string;
  boilTimeStart: string;
  outboundTime: string;
  signTime: string;
  remark: string;
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const isWithin = ref('');
const processType = ref('PENDING');
const deliveryType = ref('');
const orderNo = ref('');
const prescriptionNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const doseRange = ref('');
const page = ref(1);
const pageSize = ref(20);
const manualProcessPage = ref<AdminManualProcessPage | null>(null);
const loading = ref(false);
const submitting = ref(false);
const errorLine = ref('');
const processDialog = ref<ProcessDialog | null>(null);

const rows = computed(() => manualProcessPage.value?.records ?? []);
const total = computed(() => manualProcessPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function splitValues(value: string | null | undefined) {
  if (!value) return [];
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function fullAddress(row: AdminManualProcessItem) {
  return [
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ].filter(Boolean).join('') || '-';
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
  const items = splitValues(value);
  return items.length ? items.map((item) => labels[item] ?? item).join('、') : '-';
}

function hospitalTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    1: '门诊',
    2: '住院',
  };
  const items = splitValues(value);
  return items.length ? items.map((item) => labels[item] ?? item).join('、') : '-';
}

function deliveryTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    HOME: '送患者',
    1: '送医院',
    2: '送患者',
  };
  return value ? labels[value] ?? value : '-';
}

function canProcess(row: AdminManualProcessItem) {
  return row.orderStatus === 'CREATED';
}

function isDecoctionRow(row: AdminManualProcessItem) {
  return splitValues(row.prescriptionTypes).some((item) => item === '2' || item === 'DECOCTION');
}

function queryParams(): AdminManualProcessQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    prescriptionType: prescriptionType.value,
    hospitalType: hospitalType.value,
    isWithin: isWithin.value,
    processType: processType.value,
    deliveryType: deliveryType.value,
    orderNo: orderNo.value,
    prescriptionNo: prescriptionNo.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    patientName: patientName.value,
    doseRange: doseRange.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function downloadManualProcessCsv() {
  downloadCsv(
    `订单走流程列表-${currentIsoDate()}.csv`,
    ['平台订单号', '机构', '收货地址', '送货时间', '接单时间', '送医院', '机构处方号', '门诊住院', '病人信息', '处方类型', '剂数', '处方列表', '处方数', '备注', '订单状态', '更新时间'],
    rows.value.map((row) => [
      row.orderNo,
      row.institutionName,
      fullAddress(row),
      formatDate(row.deliveryTime),
      formatDate(row.createdAt),
      deliveryTypeText(row.addressType),
      row.externalPrescriptionNos,
      hospitalTypeText(row.hospitalTypes),
      row.patientNames,
      prescriptionTypeText(row.prescriptionTypes),
      row.doseCounts,
      row.prescriptionNos,
      row.prescriptionCount,
      row.orderRemark,
      row.orderStatus,
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${rows.value.length} 条订单走流程记录`);
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}

function localInputTime(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function addMinutes(date: Date, minutes: number) {
  return new Date(date.getTime() + minutes * 60 * 1000);
}

function toIsoTime(value: string) {
  if (!value) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

async function refreshManualProcessOrders() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminManualProcessOrders(queryParams());
    manualProcessPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('notice', 'success', `已查询到 ${nextPage.total} 条订单走流程记录`);
  } catch (error) {
    manualProcessPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshManualProcessOrders();
}

function openProcess(row: AdminManualProcessItem) {
  const now = new Date();
  processDialog.value = {
    row,
    operator: processDialog.value?.operator || 'admin',
    auditor: processDialog.value?.auditor || 'admin',
    auditTime: localInputTime(now),
    dispenser: processDialog.value?.dispenser || 'admin',
    dispenseTime: localInputTime(addMinutes(now, 5)),
    rechecker: processDialog.value?.rechecker || 'admin',
    recheckTime: localInputTime(addMinutes(now, 30)),
    pailNo: isDecoctionRow(row) ? 'MANUAL' : '',
    soakTimeStart: localInputTime(addMinutes(now, 60)),
    boilTimeStart: localInputTime(addMinutes(now, 90)),
    outboundTime: localInputTime(addMinutes(now, 150)),
    signTime: localInputTime(addMinutes(now, 24 * 60)),
    remark: '',
  };
}

function closeProcess() {
  processDialog.value = null;
}

async function submitProcess() {
  if (!processDialog.value) return;
  const dialog = processDialog.value;
  submitting.value = true;
  errorLine.value = '';
  try {
    const result = await manualProcessAdminOrder(dialog.row.orderNo, {
      operator: dialog.operator.trim() || 'admin',
      auditor: dialog.auditor.trim() || 'admin',
      auditTime: toIsoTime(dialog.auditTime),
      dispenser: dialog.dispenser.trim() || 'admin',
      dispenseTime: toIsoTime(dialog.dispenseTime),
      rechecker: dialog.rechecker.trim() || 'admin',
      recheckTime: toIsoTime(dialog.recheckTime),
      pailNo: dialog.pailNo.trim(),
      soakTimeStart: toIsoTime(dialog.soakTimeStart),
      boilTimeStart: toIsoTime(dialog.boilTimeStart),
      outboundTime: toIsoTime(dialog.outboundTime),
      signTime: toIsoTime(dialog.signTime),
      remark: dialog.remark.trim(),
    });
    emit('notice', 'success', `订单 ${result.orderNo} 已走流程，物流号 ${result.logisticsNo}`);
    closeProcess();
    await refreshManualProcessOrders();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshManualProcessOrders();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshManualProcessOrders();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshManualProcessOrders();
  },
  { immediate: true },
);

defineExpose({
  refreshManualProcessOrders,
});
</script>

<template>
  <section class="legacy-page order-list-page manual-process-page">
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
        机构：
        <input v-model="institution" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input input-medium">
          <option value="">全部</option>
          <option value="DECOCTION">代煎</option>
          <option value="SELF_DECOCTION">自煎</option>
          <option value="HERBAL_PIECE">饮片</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input input-small">
          <option value="">全部</option>
          <option value="OUTPATIENT">门诊</option>
          <option value="INPATIENT">住院</option>
        </select>
      </li>
      <li>
        内外用：
        <select v-model="isWithin" class="legacy-input input-small">
          <option value="">全部</option>
          <option value="1">内服</option>
          <option value="0">外用</option>
        </select>
      </li>
      <li>
        审核类型：
        <select v-model="processType" class="legacy-input input-medium">
          <option value="PENDING">待审核</option>
          <option value="NOT_DUE">未到期</option>
          <option value="PROCESSED">已审核</option>
        </select>
      </li>
      <li>
        是否送医院：
        <select v-model="deliveryType" class="legacy-input input-small">
          <option value="">全部</option>
          <option value="HOSPITAL">是</option>
          <option value="HOME">否</option>
        </select>
      </li>
      <li>
        平台订单号：
        <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        平台处方号：
        <input v-model="prescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        病人姓名：
        <input v-model="patientName" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        剂数：
        <select v-model="doseRange" class="legacy-input input-small">
          <option value="">全部</option>
          <option value="LOW">小于3剂</option>
          <option value="HIGH">大于等于3剂</option>
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadManualProcessCsv">导出当前页</button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table manual-process-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台订单号</th>
            <th>收货地址</th>
            <th>送货时间</th>
            <th>接单时间</th>
            <th>送医院</th>
            <th>机构名称</th>
            <th>机构处方号</th>
            <th>门诊住院</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>处方列表</th>
            <th>备注</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="15" class="legacy-empty">正在查询订单走流程列表</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="15" class="legacy-empty">暂无订单</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.orderId">
            <td>{{ row.orderNo }}</td>
            <td class="cell-wide">{{ fullAddress(row) }}</td>
            <td>{{ displayValue(formatDate(row.deliveryTime)) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ deliveryTypeText(row.addressType) }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td>{{ displayValue(row.externalPrescriptionNos) }}</td>
            <td>{{ hospitalTypeText(row.hospitalTypes) }}</td>
            <td>{{ displayValue(row.patientNames) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionTypes) }}</td>
            <td>{{ displayValue(row.doseCounts) }}</td>
            <td>{{ displayValue(row.prescriptionNos) }}</td>
            <td class="cell-wide">{{ displayValue(row.orderRemark) }}</td>
            <td><StatusPill :value="row.orderStatus" :tone="statusTone(row.orderStatus)" /></td>
            <td>
              <button class="legacy-btn legacy-btn-primary" type="button" :disabled="!canProcess(row)" @click="openProcess(row)">
                走流程
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="legacy-pagination">
        <span>共 {{ total }} 条，第 {{ page }} 页</span>
        <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="goPreviousPage">上一页</button>
        <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="goNextPage">下一页</button>
      </div>
    </div>

    <div v-if="processDialog" class="legacy-modal-mask" @click.self="closeProcess">
      <section class="legacy-modal manual-process-modal">
        <header class="legacy-modal-head">
          <h3>订单走流程</h3>
          <button class="legacy-icon-btn" type="button" @click="closeProcess">×</button>
        </header>
        <div class="manual-process-form">
          <label>
            审单员
            <input v-model="processDialog.auditor" class="legacy-input" />
          </label>
          <label>
            审单时间
            <input v-model="processDialog.auditTime" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            调剂工号
            <input v-model="processDialog.dispenser" class="legacy-input" />
          </label>
          <label>
            调剂时间
            <input v-model="processDialog.dispenseTime" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            复核工号
            <input v-model="processDialog.rechecker" class="legacy-input" />
          </label>
          <label>
            复核时间
            <input v-model="processDialog.recheckTime" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            加水桶号
            <input v-model="processDialog.pailNo" class="legacy-input" />
          </label>
          <label>
            泡药时间
            <input v-model="processDialog.soakTimeStart" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            煎煮时间
            <input v-model="processDialog.boilTimeStart" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            物流发货时间
            <input v-model="processDialog.outboundTime" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            物流收货时间
            <input v-model="processDialog.signTime" class="legacy-input" type="datetime-local" />
          </label>
          <label>
            操作人
            <input v-model="processDialog.operator" class="legacy-input" />
          </label>
          <label class="manual-process-remark">
            备注
            <textarea v-model="processDialog.remark" class="legacy-input" rows="3" />
          </label>
        </div>
        <footer class="legacy-modal-actions">
          <button class="legacy-btn" type="button" :disabled="submitting" @click="closeProcess">取消</button>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitProcess">
            {{ submitting ? '提交中' : '提交' }}
          </button>
        </footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.manual-process-page {
  min-width: 1180px;
}

.manual-process-table th,
.manual-process-table td {
  white-space: nowrap;
}

.manual-process-table .cell-wide {
  max-width: 240px;
  white-space: normal;
  line-height: 1.5;
}

.manual-process-modal {
  width: min(760px, calc(100vw - 40px));
}

.manual-process-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.manual-process-form label {
  display: grid;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.manual-process-form .manual-process-remark {
  grid-column: 1 / -1;
}

.manual-process-form textarea {
  resize: vertical;
}

@media (max-width: 760px) {
  .manual-process-page {
    min-width: 0;
  }

  .manual-process-form {
    grid-template-columns: 1fr;
  }
}
</style>
