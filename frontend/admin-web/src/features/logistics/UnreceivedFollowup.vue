<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { listShipmentTraces, listShipments, receiveShipmentTrace, signShipment } from '../../api/logistics';
import type { ShipmentRecord, ShipmentTraceRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type FollowupStatus = 'UNSIGNED' | 'PACKED' | 'SHIPPED' | 'IN_TRANSIT' | 'SIGNED';

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

const startTime = ref('');
const endTime = ref('');
const orderNo = ref('');
const receiverName = ref('');
const receiverPhone = ref('');
const logisticsNo = ref('');
const logisticsCompany = ref('');
const status = ref<FollowupStatus>('UNSIGNED');
const limit = ref(50);
const loading = ref(false);
const submitting = ref(false);
const traceLoading = ref(false);
const errorLine = ref('');
const requestId = ref(0);
const records = ref<ShipmentRecord[]>([]);
const selectedShipment = ref<ShipmentRecord | null>(null);
const shipmentTraces = ref<ShipmentTraceRecord[]>([]);
const followupContent = ref('');
const followupStatus = ref<'IN_TRANSIT' | 'SHIPPED'>('IN_TRANSIT');
const signRemark = ref('');

const rows = computed(() => {
  if (status.value === 'UNSIGNED') {
    return records.value.filter((record) => record.logisticsStatus !== 'SIGNED');
  }
  return records.value;
});

const operator = computed({
  get: () => props.operationOperator,
  set: (value: string) => emit('update:operationOperator', value),
});

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function normalizedLimit() {
  if (!Number.isFinite(limit.value) || limit.value <= 0) return 50;
  return Math.min(Math.trunc(limit.value), 200);
}

function queryStatus() {
  return status.value === 'UNSIGNED' ? undefined : status.value;
}

function fullAddress(record: ShipmentRecord) {
  return [
    record.receiverAddress,
  ].filter(Boolean).join('') || '-';
}

function defaultFollowupContent(record: ShipmentRecord) {
  return `未签收跟进：${record.receiverName || '收货人'} ${record.receiverPhone || ''}`.trim();
}

function downloadUnreceivedCsv() {
  downloadCsv(
    `未签收跟进-${limit.value}条.csv`,
    [
      '订单号',
      '外部订单号',
      '机构',
      '病人',
      '收货人',
      '收货电话',
      '收货地址',
      '物流单号',
      '物流公司',
      '物流状态',
      '包裹数',
      '重量',
      '打包时间',
      '发货时间',
      '签收时间',
      '创建时间',
      '更新时间',
    ],
    rows.value.map((record) => [
      record.orderNo,
      record.externalOrderNo,
      record.institutionName,
      record.patientName,
      record.receiverName,
      record.receiverPhone,
      fullAddress(record),
      record.logisticsNo,
      record.logisticsCompany,
      record.logisticsStatus,
      record.pkgNum,
      record.pkgWeight,
      formatDate(record.packageTime),
      formatDate(record.outboundTime),
      formatDate(record.signTime),
      formatDate(record.createdAt),
      formatDate(record.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出 ${formatNumber(rows.value.length)} 条未签收跟进记录`);
}

async function refreshUnreceivedFollowups() {
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
      receiverName: receiverName.value,
      receiverPhone: receiverPhone.value,
      logisticsNo: logisticsNo.value,
      logisticsCompany: logisticsCompany.value,
      status: queryStatus(),
      limit: limit.value,
    });
    if (nextRequestId !== requestId.value) return;
    records.value = nextRecords;
    emit('countChanged', rows.value.length);
    emit('notice', 'info', `已刷新未签收跟进：${rows.value.length} 条`);
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

async function searchFirstPage() {
  await refreshUnreceivedFollowups();
}

function resetFilters() {
  startTime.value = '';
  endTime.value = '';
  orderNo.value = '';
  receiverName.value = '';
  receiverPhone.value = '';
  logisticsNo.value = '';
  logisticsCompany.value = '';
  status.value = 'UNSIGNED';
  void refreshUnreceivedFollowups();
}

async function openShipment(record: ShipmentRecord) {
  selectedShipment.value = record;
  followupContent.value = defaultFollowupContent(record);
  signRemark.value = '';
  await refreshShipmentTraces(record);
}

function closeShipment() {
  selectedShipment.value = null;
  shipmentTraces.value = [];
  followupContent.value = '';
  signRemark.value = '';
}

async function refreshShipmentTraces(record = selectedShipment.value) {
  if (!record) return;
  traceLoading.value = true;
  errorLine.value = '';
  try {
    shipmentTraces.value = await listShipmentTraces(record.shipmentId);
  } catch (error) {
    shipmentTraces.value = [];
    errorLine.value = errorMessage(error);
  } finally {
    traceLoading.value = false;
  }
}

async function submitFollowupTrace() {
  if (!selectedShipment.value) {
    errorLine.value = '请先选择物流单';
    return;
  }
  if (!followupContent.value.trim()) {
    errorLine.value = '请输入跟进内容';
    return;
  }
  submitting.value = true;
  errorLine.value = '';
  try {
    const updated = await receiveShipmentTrace({
      logisticsNo: selectedShipment.value.logisticsNo,
      provider: selectedShipment.value.logisticsCompany || 'MANUAL',
      opCode: followupStatus.value,
      traceContent: followupContent.value.trim(),
      traceTime: new Date().toISOString(),
      operator: operator.value.trim() || 'admin',
    });
    selectedShipment.value = updated;
    emit('notice', 'success', `${updated.logisticsNo} 跟进轨迹已记录`);
    await refreshShipmentTraces(updated);
    await refreshUnreceivedFollowups();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function submitSign() {
  if (!selectedShipment.value) {
    errorLine.value = '请先选择物流单';
    return;
  }
  submitting.value = true;
  errorLine.value = '';
  try {
    const updated = await signShipment(selectedShipment.value.shipmentId, {
      operator: operator.value.trim() || 'admin',
      remark: signRemark.value.trim() || '未签收跟进后手动签收',
    });
    selectedShipment.value = updated;
    emit('notice', 'success', `${updated.orderNo} 已手动签收`);
    await refreshShipmentTraces(updated);
    await refreshUnreceivedFollowups();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshUnreceivedFollowups();
  },
  { immediate: true },
);

defineExpose({
  refreshUnreceivedFollowups,
});
</script>

<template>
  <section class="legacy-page logistics-unreceived-page">
    <ul class="legacy-search logistics-unreceived-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" placeholder="yyyy-MM-dd HH:mm:ss" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        平台订单号：
        <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        收货人：
        <input v-model="receiverName" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        收货电话：
        <input v-model="receiverPhone" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        物流单号：
        <input v-model="logisticsNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        物流公司：
        <input v-model="logisticsCompany" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        状态：
        <select v-model="status" class="legacy-input" @change="searchFirstPage">
          <option value="UNSIGNED">未签收</option>
          <option value="PACKED">已打包</option>
          <option value="SHIPPED">已发货</option>
          <option value="IN_TRANSIT">运输中</option>
          <option value="SIGNED">已签收</option>
        </select>
      </li>
      <li>
        条数：
        <input v-model.number="limit" class="legacy-input input-small" type="number" min="1" max="200" step="10" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">重置</button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadUnreceivedCsv">
          导出当前结果
        </button>
      </li>
    </ul>

    <p class="logistics-unreceived-hint">
      当前页面复用物流单、物流轨迹和签收接口；跟进处理会写入物流轨迹，关闭跟进以手动签收完成。
    </p>
    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <table class="legacy-main-table logistics-unreceived-table">
      <thead>
        <tr class="legacy-main-head">
          <th>订单号</th>
          <th>机构</th>
          <th>病人</th>
          <th>收货人</th>
          <th>收货电话</th>
          <th>收货地址</th>
          <th>物流单号</th>
          <th>物流公司</th>
          <th>物流状态</th>
          <th>发货时间</th>
          <th>签收时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading" class="legacy-main-info">
          <td colspan="12" class="legacy-empty">正在查询未签收列表</td>
        </tr>
        <tr v-else-if="rows.length === 0" class="legacy-main-info">
          <td colspan="12" class="legacy-empty">没有相关数据</td>
        </tr>
        <tr v-for="record in rows" :key="record.shipmentId" class="legacy-main-info">
          <td>{{ record.orderNo }}</td>
          <td>{{ rowValue(record.institutionName) }}</td>
          <td>{{ rowValue(record.patientName) }}</td>
          <td>{{ rowValue(record.receiverName) }}</td>
          <td>{{ rowValue(record.receiverPhone) }}</td>
          <td class="legacy-left">{{ fullAddress(record) }}</td>
          <td>{{ record.logisticsNo }}</td>
          <td>{{ record.logisticsCompany }}</td>
          <td><StatusPill :value="record.logisticsStatus" :tone="statusTone(record.logisticsStatus)" /></td>
          <td>{{ formatDate(record.outboundTime) }}</td>
          <td>{{ formatDate(record.signTime) }}</td>
          <td>
            <button class="legacy-link-btn workflow-pass-btn" type="button" @click="openShipment(record)">
              跟进
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="page_and_btn">
      <div class="dataTables_info">显示第 {{ rows.length > 0 ? 1 : 0 }} 至 {{ rows.length }} 项记录，共 {{ rows.length }} 项</div>
    </div>

    <div v-if="selectedShipment" class="unreceived-drawer-mask">
      <section class="unreceived-drawer legacy-panel">
        <div class="legacy-section-title">
          <h2>未签收跟进</h2>
        </div>
        <div class="legacy-detail-grid">
          <span>订单号</span>
          <strong>{{ selectedShipment.orderNo }}</strong>
          <span>物流单号</span>
          <strong>{{ selectedShipment.logisticsNo }}</strong>
          <span>收货人</span>
          <strong>{{ rowValue(selectedShipment.receiverName) }} / {{ rowValue(selectedShipment.receiverPhone) }}</strong>
          <span>当前状态</span>
          <strong><StatusPill :value="selectedShipment.logisticsStatus" :tone="statusTone(selectedShipment.logisticsStatus)" /></strong>
        </div>

        <ul class="legacy-search unreceived-followup-form">
          <li>
            操作人：
            <input v-model="operator" class="legacy-input input-medium" />
          </li>
          <li>
            跟进状态：
            <select v-model="followupStatus" class="legacy-input">
              <option value="IN_TRANSIT">运输中</option>
              <option value="SHIPPED">已发货</option>
            </select>
          </li>
          <li class="unreceived-followup-content">
            跟进内容：
            <input v-model="followupContent" class="legacy-input input-large" placeholder="请输入本次跟进结果" />
          </li>
          <li>
            <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitFollowupTrace">
              {{ submitting ? '提交中' : '记录跟进' }}
            </button>
          </li>
        </ul>

        <ul class="legacy-search unreceived-sign-form">
          <li class="unreceived-followup-content">
            签收备注：
            <input v-model="signRemark" class="legacy-input input-large" placeholder="未签收跟进后手动签收" />
          </li>
          <li>
            <button class="legacy-btn legacy-btn-export" type="button" :disabled="submitting || selectedShipment.logisticsStatus === 'SIGNED'" @click="submitSign">
              手动签收
            </button>
          </li>
          <li>
            <button class="legacy-btn" type="button" :disabled="submitting" @click="closeShipment">关闭</button>
          </li>
        </ul>

        <section class="legacy-panel unreceived-trace-panel">
          <div class="legacy-section-title">
            <h2>物流轨迹</h2>
          </div>
          <table class="legacy-main-table unreceived-trace-table">
            <thead>
              <tr class="legacy-main-head">
                <th>状态</th>
                <th>内容</th>
                <th>轨迹时间</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="traceLoading" class="legacy-main-info">
                <td colspan="4" class="legacy-empty">正在查询物流轨迹</td>
              </tr>
              <tr v-else-if="shipmentTraces.length === 0" class="legacy-main-info">
                <td colspan="4" class="legacy-empty">暂无物流轨迹</td>
              </tr>
              <tr v-for="trace in shipmentTraces" :key="trace.traceId" class="legacy-main-info">
                <td><StatusPill :value="trace.traceStatus" :tone="statusTone(trace.traceStatus)" /></td>
                <td class="legacy-left">{{ rowValue(trace.traceContent) }}</td>
                <td>{{ formatDate(trace.traceTime) }}</td>
                <td>{{ formatDate(trace.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </section>
    </div>
  </section>
</template>

<style scoped>
.logistics-unreceived-search {
  row-gap: 10px;
}

.logistics-unreceived-hint {
  margin: 0 0 10px;
  color: #6f7d91;
  font-size: 13px;
}

.logistics-unreceived-table {
  min-width: 1320px;
}

.unreceived-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgb(15 23 42 / 38%);
}

.unreceived-drawer {
  box-sizing: border-box;
  width: min(900px, 100%);
  height: 100vh;
  margin: 0;
  overflow: auto;
}

.unreceived-followup-form,
.unreceived-sign-form {
  margin-top: 12px;
}

.unreceived-followup-content {
  min-width: 420px;
}

.unreceived-trace-panel {
  margin-top: 14px;
}

.unreceived-trace-table {
  min-width: 760px;
}

@media (max-width: 780px) {
  .unreceived-drawer {
    width: 100%;
  }

  .unreceived-followup-content {
    min-width: 100%;
  }
}
</style>
