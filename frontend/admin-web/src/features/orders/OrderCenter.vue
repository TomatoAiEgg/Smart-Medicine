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

const orderNo = ref('');
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
  <section class="workspace">
    <div class="toolbar">
      <label class="grow">
        <span>订单号</span>
        <input v-model="orderNo" placeholder="例如 ZHYF1782395865216" @keyup.enter="queryOrder" />
      </label>
      <button class="primary" type="button" :disabled="orderLoading" @click="queryOrder">
        {{ orderLoading ? '查询中' : '查询' }}
      </button>
    </div>

    <p v-if="orderError" class="error-line">{{ orderError }}</p>

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
