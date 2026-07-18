<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
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
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type LogisticsDataset = 'ready' | 'shipments' | 'callbacks';

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

const activeLogisticsDataset = ref<LogisticsDataset>('ready');
const logisticsLoading = ref(false);
const logisticsError = ref('');
const logisticsLimit = ref(50);
const logisticsStatus = ref('');
const logisticsOrderNo = ref('');
const logisticsCompany = ref('SF');
const logisticsPayMethod = ref('MONTHLY');
const pkgWeight = ref(1);
const pkgNum = ref(1);
const traceLogisticsNo = ref('');
const traceProvider = ref('SF');
const traceOpCode = ref('50');
const traceContent = ref('');
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
  ready: '待打包订单',
  shipments: '物流单',
  callbacks: '回调记录',
};

function datasetCount(dataset: LogisticsDataset) {
  if (dataset === 'ready') return readyDeliveryOrders.value.length;
  if (dataset === 'shipments') return shipments.value.length;
  return callbackRecords.value.length;
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function normalizedLogisticsLimit() {
  if (!Number.isFinite(logisticsLimit.value) || logisticsLimit.value <= 0) return 50;
  return Math.min(Math.trunc(logisticsLimit.value), 200);
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
    if (dataset === 'ready') {
      readyDeliveryOrders.value = await listReadyDeliveryOrders(limit);
    } else if (dataset === 'shipments') {
      shipments.value = await listShipments({
        status: logisticsStatus.value,
        orderNo: logisticsOrderNo.value,
        limit,
      });
    } else {
      callbackRecords.value = await listCallbackRecords({
        status: callbackStatus.value,
        callbackType: callbackType.value,
        limit,
      });
    }
    if (requestId === logisticsRequestId.value) {
      emit('notice', 'info', `已刷新${logisticsDatasetNames[dataset]}：${datasetCount(dataset)} 条`);
    }
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
  void refreshLogisticsRecords();
}

async function handlePackShipment(order: DeliveryOrderRecord) {
  handlingShipmentId.value = order.orderId;
  logisticsError.value = '';
  try {
    const shipment = await packShipment({
      orderNo: order.orderNo,
      logisticsCompany: logisticsCompany.value.trim() || 'SF',
      payMethod: logisticsPayMethod.value.trim() || 'MONTHLY',
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
  <section class="workspace">
    <div class="toolbar ops-tabs">
      <button class="secondary" :class="{ active: activeLogisticsDataset === 'ready' }" type="button" @click="switchLogisticsDataset('ready')">
        待打包
      </button>
      <button class="secondary" :class="{ active: activeLogisticsDataset === 'shipments' }" type="button" @click="switchLogisticsDataset('shipments')">
        物流单
      </button>
      <button class="secondary" :class="{ active: activeLogisticsDataset === 'callbacks' }" type="button" @click="switchLogisticsDataset('callbacks')">
        回调记录
      </button>
      <label class="limit-label">
        <span>条数</span>
        <input v-model.number="logisticsLimit" type="number" min="1" max="200" step="10" @keyup.enter="refreshLogisticsRecords" />
      </label>
      <button class="primary" type="button" :disabled="logisticsLoading" @click="refreshLogisticsRecords">
        {{ logisticsLoading ? '刷新中' : '刷新' }}
      </button>
    </div>

    <div class="toolbar event-toolbar">
      <label>
        <span>操作人</span>
        <input v-model="operatorModel" placeholder="admin" />
      </label>
      <template v-if="activeLogisticsDataset === 'ready'">
        <label>
          <span>物流公司</span>
          <input v-model="logisticsCompany" placeholder="SF / EMS" />
        </label>
        <label>
          <span>付款方式</span>
          <input v-model="logisticsPayMethod" placeholder="MONTHLY" />
        </label>
        <label>
          <span>重量 kg</span>
          <input v-model.number="pkgWeight" type="number" min="0" step="0.1" />
        </label>
        <label>
          <span>包裹数</span>
          <input v-model.number="pkgNum" type="number" min="1" step="1" />
        </label>
      </template>
      <template v-else-if="activeLogisticsDataset === 'shipments'">
        <label>
          <span>状态</span>
          <input v-model="logisticsStatus" placeholder="PACKED / SHIPPED / SIGNED" @keyup.enter="refreshLogisticsRecords" />
        </label>
        <label>
          <span>订单号</span>
          <input v-model="logisticsOrderNo" placeholder="ZHYF..." @keyup.enter="refreshLogisticsRecords" />
        </label>
        <label>
          <span>运单号</span>
          <input v-model="traceLogisticsNo" placeholder="SF-001" />
        </label>
        <label>
          <span>来源</span>
          <input v-model="traceProvider" placeholder="SF / EMS" />
        </label>
        <label>
          <span>轨迹码</span>
          <input v-model="traceOpCode" placeholder="50 / 80 / 203" />
        </label>
        <label class="grow">
          <span>轨迹说明</span>
          <input v-model="traceContent" placeholder="已揽收 / 已签收" @keyup.enter="handleReceiveTrace" />
        </label>
        <button class="secondary" type="button" :disabled="logisticsLoading" @click="handleReceiveTrace">
          记录轨迹
        </button>
      </template>
      <template v-else>
        <label>
          <span>状态</span>
          <input v-model="callbackStatus" placeholder="PENDING / SUCCESS / FAILED / DEAD" @keyup.enter="refreshLogisticsRecords" />
        </label>
        <label>
          <span>回调类型</span>
          <input v-model="callbackType" placeholder="ORDER_SHIPPED" @keyup.enter="refreshLogisticsRecords" />
        </label>
        <button class="secondary" type="button" :disabled="logisticsLoading" @click="handleDispatchDueCallbacks">
          派发到期回调
        </button>
      </template>
    </div>

    <p v-if="logisticsError" class="error-line">{{ logisticsError }}</p>

    <div v-if="activeLogisticsDataset === 'ready'" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>订单</th>
            <th>收件人</th>
            <th>地址</th>
            <th>状态</th>
            <th class="right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!logisticsLoading && readyDeliveryOrders.length === 0">
            <td colspan="5" class="empty">暂无待打包订单</td>
          </tr>
          <tr v-for="item in readyDeliveryOrders" :key="item.orderId">
            <td>
              <strong>{{ item.orderNo }}</strong>
              <small>{{ item.externalOrderNo }}</small>
            </td>
            <td>
              <strong>{{ item.receiverName }}</strong>
              <small>{{ item.receiverPhone }}</small>
            </td>
            <td>{{ item.receiverAddress }}</td>
            <td><StatusPill :value="item.orderStatus" :tone="statusTone(item.orderStatus)" /></td>
            <td class="actions">
              <button class="primary" type="button" :disabled="handlingShipmentId === item.orderId" @click="handlePackShipment(item)">
                打包
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else-if="activeLogisticsDataset === 'shipments'" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>订单/运单</th>
            <th>物流</th>
            <th>状态</th>
            <th>打包/出库/签收</th>
            <th class="right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!logisticsLoading && shipments.length === 0">
            <td colspan="5" class="empty">暂无物流单</td>
          </tr>
          <tr v-for="shipment in shipments" :key="shipment.shipmentId">
            <td>
              <strong>{{ shipment.orderNo }}</strong>
              <small>{{ shipment.logisticsNo }}</small>
            </td>
            <td>
              <strong>{{ shipment.logisticsCompany }}</strong>
              <small>{{ shipment.payMethod || '-' }} / {{ shipment.pkgWeight || '-' }}kg / {{ shipment.pkgNum || '-' }}件</small>
            </td>
            <td><StatusPill :value="shipment.logisticsStatus" :tone="statusTone(shipment.logisticsStatus)" /></td>
            <td>
              <strong>{{ formatDate(shipment.packageTime) }}</strong>
              <small>{{ formatDate(shipment.outboundTime) }} / {{ formatDate(shipment.signTime) }}</small>
            </td>
            <td class="actions">
              <button class="secondary" type="button" :disabled="handlingShipmentId === shipment.shipmentId || shipment.logisticsStatus !== 'PACKED'" @click="handleShipmentAction(shipment, 'ship')">
                发货
              </button>
              <button class="success" type="button" :disabled="handlingShipmentId === shipment.shipmentId || shipment.logisticsStatus === 'SIGNED'" @click="handleShipmentAction(shipment, 'sign')">
                签收
              </button>
              <button class="secondary" type="button" @click="refreshShipmentTraces(shipment)">
                轨迹
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>订单/业务</th>
            <th>类型</th>
            <th>状态</th>
            <th>重试</th>
            <th>创建时间</th>
            <th class="right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!logisticsLoading && callbackRecords.length === 0">
            <td colspan="6" class="empty">暂无回调记录</td>
          </tr>
          <tr v-for="record in callbackRecords" :key="record.id">
            <td>
              <strong>{{ record.orderNo }}</strong>
              <small>{{ record.businessId }}</small>
            </td>
            <td>{{ record.callbackType }}</td>
            <td><StatusPill :value="record.status" :tone="statusTone(record.status)" /></td>
            <td>
              <strong>{{ record.retryCount }}</strong>
              <small>{{ formatDate(record.nextRetryAt) }}</small>
            </td>
            <td>{{ formatDate(record.createdAt) }}</td>
            <td class="actions">
              <button class="success" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'success')">
                成功
              </button>
              <button class="danger" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'failed')">
                失败
              </button>
              <button class="secondary" type="button" :disabled="handlingCallbackId === record.id" @click="handleCallbackAction(record, 'replay')">
                重放
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeLogisticsDataset === 'shipments'" class="table-wrap events-wrap">
      <table>
        <thead>
          <tr>
            <th>运单</th>
            <th>轨迹状态</th>
            <th>轨迹时间</th>
            <th>内容</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="shipmentTraces.length === 0">
            <td colspan="4" class="empty">暂无轨迹明细</td>
          </tr>
          <tr v-for="trace in shipmentTraces" :key="trace.traceId">
            <td>{{ trace.logisticsNo }}</td>
            <td><StatusPill :value="trace.traceStatus" :tone="statusTone(trace.traceStatus)" /></td>
            <td>{{ formatDate(trace.traceTime) }}</td>
            <td><code>{{ trace.traceContent || trace.rawPayload }}</code></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
