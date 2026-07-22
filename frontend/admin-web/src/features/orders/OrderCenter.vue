<script setup lang="ts">
import { ref } from 'vue';
import { ApiError } from '../../api/client';
import { getOrder, getOrderProgress } from '../../api/order';
import type { OrderCreateResult, OrderProgressSnapshot } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const startTime = ref('2026-07-14 11:00:00');
const endTime = ref('2026-07-21 12:00:00');
const institution = ref('');
const prescriptionType = ref('');
const deliveryType = ref('');
const logisticsCompany = ref('');
const province = ref('');
const orderNo = ref('');
const patientName = ref('');
const receiverPhone = ref('');
const order = ref<OrderCreateResult | null>(null);
const orderProgress = ref<OrderProgressSnapshot | null>(null);
const orderLoading = ref(false);
const orderError = ref('');

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function rowValue(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
}

async function queryOrder() {
  const trimmed = orderNo.value.trim();
  if (!trimmed) {
    orderError.value = '请输入订单号';
    order.value = null;
    orderProgress.value = null;
    return;
  }

  orderLoading.value = true;
  orderError.value = '';
  try {
    const [nextOrder, nextProgress] = await Promise.all([getOrder(trimmed), getOrderProgress(trimmed)]);
    order.value = nextOrder;
    orderProgress.value = nextProgress;
    emit('notice', 'success', `已查询到订单 ${order.value.orderNo}`);
  } catch (error) {
    order.value = null;
    orderProgress.value = null;
    orderError.value = errorMessage(error);
  } finally {
    orderLoading.value = false;
  }
}
</script>

<template>
  <section class="legacy-page order-list-page">
    <ul class="legacy-search order-list-search">
      <li>
        开始时间：
        <input v-model="startTime" class="legacy-input input-large" />
      </li>
      <li>
        结束时间：
        <input v-model="endTime" class="legacy-input input-large" />
      </li>
      <li>
        机构：
        <select v-model="institution" class="legacy-input input-large">
          <option value="">请选择</option>
          <option value="良益堂煎药中心">良益堂煎药中心</option>
        </select>
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input">
          <option value="">请选择</option>
          <option value="代煎">代煎</option>
          <option value="自煎">自煎</option>
        </select>
      </li>
      <li>
        送货方式：
        <select v-model="deliveryType" class="legacy-input">
          <option value="">请选择</option>
          <option value="送医院">送医院</option>
          <option value="送个人">送个人</option>
        </select>
      </li>
      <li>
        物流公司：
        <select v-model="logisticsCompany" class="legacy-input">
          <option value="">请选择</option>
          <option value="顺丰">顺丰</option>
          <option value="自配送">自配送</option>
        </select>
      </li>
      <li>
        省份：
        <input v-model="province" class="legacy-input input-large" />
      </li>
      <li>
        平台订单号：
        <input
          v-model="orderNo"
          class="legacy-input input-large"
          placeholder="例如 ZHYF1782395865216"
          @keyup.enter="queryOrder"
        />
      </li>
      <li>
        病人姓名：
        <input v-model="patientName" class="legacy-input input-large" />
      </li>
      <li>
        收货电话：
        <input v-model="receiverPhone" class="legacy-input input-large" />
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="orderLoading" @click="queryOrder">
          {{ orderLoading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-export" type="button" disabled>导出</button>
      </li>
    </ul>

    <p v-if="orderError" class="error-line">{{ orderError }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-result-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台处方号</th>
            <th>平台订单时间</th>
            <th>煎煮中心</th>
            <th>机构名称</th>
            <th>门诊住院</th>
            <th>机构处方号</th>
            <th>病人姓名</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>处方金额</th>
            <th>送货方式</th>
            <th>订单状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!order" class="legacy-main-info">
            <td colspan="12" class="legacy-empty">
              {{ orderLoading ? '正在查询处方订单' : '请输入平台订单号后查询' }}
            </td>
          </tr>
          <tr v-else class="legacy-main-info">
            <td>{{ rowValue(order.orderNo) }}</td>
            <td>{{ formatDate(orderProgress?.updatedAt) }}</td>
            <td>良益堂煎药中心</td>
            <td>{{ rowValue(institution || order.externalOrderNo) }}</td>
            <td>门诊</td>
            <td>{{ rowValue(order.externalOrderNo) }}</td>
            <td>{{ rowValue(patientName) }}</td>
            <td>{{ rowValue(prescriptionType || '代煎') }}</td>
            <td>{{ rowValue(orderProgress?.prescriptions.length) }}</td>
            <td>-</td>
            <td>{{ rowValue(deliveryType) }}</td>
            <td>
              <StatusPill :value="order.status" :tone="statusTone(order.status)" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="order" class="detail-grid">
      <div>
        <span>订单 ID</span>
        <strong>{{ order.orderId }}</strong>
      </div>
      <div>
        <span>平台订单号</span>
        <strong>{{ order.orderNo }}</strong>
      </div>
      <div>
        <span>外部订单号</span>
        <strong>{{ order.externalOrderNo }}</strong>
      </div>
      <div>
        <span>订单状态</span>
        <StatusPill :value="order.status" :tone="statusTone(order.status)" />
      </div>
      <div>
        <span>是否重复推单</span>
        <strong>{{ order.duplicated ? '是' : '否' }}</strong>
      </div>
    </div>

    <div v-if="orderProgress" class="progress-block">
      <h2>履约进度</h2>
      <div class="detail-grid">
        <div>
          <span>处方数</span>
          <strong>{{ orderProgress.prescriptions.length }}</strong>
        </div>
        <div>
          <span>调剂记录</span>
          <strong>{{ orderProgress.dispenseRecords.length }}</strong>
        </div>
        <div>
          <span>煎煮任务</span>
          <strong>{{ orderProgress.decoctionTasks.length }}</strong>
        </div>
        <div>
          <span>物流单</span>
          <strong>{{ orderProgress.shipments.length }}</strong>
        </div>
        <div>
          <span>回调记录</span>
          <strong>{{ orderProgress.callbacks.length }}</strong>
        </div>
        <div>
          <span>最近更新</span>
          <strong>{{ formatDate(orderProgress.updatedAt) }}</strong>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>处方号</th>
              <th>外部处方号</th>
              <th>处方状态</th>
              <th>明细数</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="orderProgress.prescriptions.length === 0">
              <td colspan="5" class="empty">暂无处方</td>
            </tr>
            <tr v-for="item in orderProgress.prescriptions" :key="item.prescriptionId">
              <td>{{ item.prescriptionNo }}</td>
              <td>{{ item.externalPrescriptionNo }}</td>
              <td><StatusPill :value="item.prescriptionStatus" :tone="statusTone(item.prescriptionStatus)" /></td>
              <td>{{ item.detailCount }}</td>
              <td>{{ formatDate(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>流程节点</th>
              <th>状态</th>
              <th>处理人</th>
              <th>意见</th>
              <th>完成时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="orderProgress.workflowTasks.length === 0">
              <td colspan="5" class="empty">暂无流程任务</td>
            </tr>
            <tr v-for="task in orderProgress.workflowTasks" :key="task.taskId">
              <td>{{ task.taskType }}</td>
              <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
              <td>{{ task.operator || '-' }}</td>
              <td>{{ task.comment || '-' }}</td>
              <td>{{ formatDate(task.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>履约记录</th>
              <th>状态/结果</th>
              <th>操作人/对象</th>
              <th>补充信息</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in orderProgress.dispenseRecords" :key="record.recordId">
              <td>调剂</td>
              <td><StatusPill :value="record.printStatus" :tone="statusTone(record.printStatus)" /></td>
              <td>{{ record.dispenser }}</td>
              <td>{{ record.dispenseComment || '-' }}</td>
              <td>{{ formatDate(record.dispensedAt) }}</td>
            </tr>
            <tr v-for="task in orderProgress.decoctionTasks" :key="task.taskId">
              <td>煎煮 {{ task.taskNo }}</td>
              <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
              <td>{{ task.operator }}</td>
              <td>{{ task.deviceCode }} / {{ task.pailNo || '-' }}</td>
              <td>{{ formatDate(task.finishedAt || task.startedAt || task.createdAt) }}</td>
            </tr>
            <tr v-for="shipment in orderProgress.shipments" :key="shipment.shipmentId">
              <td>物流 {{ shipment.logisticsNo }}</td>
              <td><StatusPill :value="shipment.logisticsStatus" :tone="statusTone(shipment.logisticsStatus)" /></td>
              <td>{{ shipment.logisticsCompany }}</td>
              <td>{{ shipment.latestTraceStatus || '-' }} {{ shipment.latestTraceContent || '' }}</td>
              <td>{{ formatDate(shipment.latestTraceTime) }}</td>
            </tr>
            <tr
              v-if="
                orderProgress.dispenseRecords.length === 0 &&
                orderProgress.decoctionTasks.length === 0 &&
                orderProgress.shipments.length === 0
              "
            >
              <td colspan="5" class="empty">暂无调剂、煎煮或物流记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
