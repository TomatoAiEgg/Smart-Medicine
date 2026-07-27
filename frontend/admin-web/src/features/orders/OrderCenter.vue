<script setup lang="ts">
import { computed, ref } from 'vue';
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

const WAITING_API = '待接口';
const EMPTY_VALUE = '-';

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const orderStatus = ref('');
const decoctionCenter = ref('');
const deliveryType = ref('');
const logisticsCompany = ref('');
const province = ref('');
const orderNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const receiverPhone = ref('');
const order = ref<OrderCreateResult | null>(null);
const orderProgress = ref<OrderProgressSnapshot | null>(null);
const orderLoading = ref(false);
const orderError = ref('');

const prescriptions = computed(() => orderProgress.value?.prescriptions ?? []);
const workflowTasks = computed(() => orderProgress.value?.workflowTasks ?? []);
const dispenseRecords = computed(() => orderProgress.value?.dispenseRecords ?? []);
const decoctionTasks = computed(() => orderProgress.value?.decoctionTasks ?? []);
const shipments = computed(() => orderProgress.value?.shipments ?? []);
const callbacks = computed(() => orderProgress.value?.callbacks ?? []);
const statusLogs = computed(() => orderProgress.value?.statusLogs ?? []);
const resultCount = computed(() => (order.value ? 1 : 0));
const primaryOrderStatus = computed(() => orderProgress.value?.orderStatus ?? order.value?.status ?? '');
const prescriptionNos = computed(() => joinValues(prescriptions.value.map((item) => item.prescriptionNo)));
const externalPrescriptionNos = computed(() => joinValues(prescriptions.value.map((item) => item.externalPrescriptionNo)));
const orderCreatedAt = computed(() => formatDate(orderProgress.value?.createdAt));
const orderUpdatedAt = computed(() => formatDate(orderProgress.value?.updatedAt));
const pageSummary = computed(() => (
  `显示第 ${resultCount.value} 至 ${resultCount.value} 项记录，共 ${resultCount.value} 项`
));

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function rowValue(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
}

function waitingValue() {
  return WAITING_API;
}

function joinValues(values: string[]) {
  const validValues = values.filter((value) => value.trim().length > 0);
  return validValues.length > 0 ? validValues.join('、') : EMPTY_VALUE;
}

function statusText(status: string | null | undefined) {
  if (!status) return EMPTY_VALUE;
  const labels: Record<string, string> = {
    CREATED: '已创建',
    PENDING: '待处理',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    TERMINATED: '已终止',
    COMPLETED: '已完成',
    SUCCESS: '成功',
    FAILED: '失败',
    DEAD: '死信',
    NEW: '新建',
    RETRYING: '重试中',
    PACKED: '已打包',
    SHIPPED: '已发货',
    IN_TRANSIT: '运输中',
    SIGNED: '已签收',
    BOUND: '已绑定',
    DECOCTING: '煎煮中',
    DECOCTED: '已煎煮',
    AUDIT_PASSED: '审核通过',
    AUDIT_FAILED: '审核失败',
    RECHECKED: '已复核',
    PASSED: '已通过',
    SENT: '已发送',
    OK: '正常',
  };
  return labels[status] ?? status;
}

function taskTypeText(type: string) {
  const labels: Record<string, string> = {
    REVIEW: '审核',
    DISPENSE: '调剂',
    RECHECK: '复核',
    ORDER_REVIEW: '订单审核',
    PRESCRIPTION_DISPENSE: '处方调剂',
    PRESCRIPTION_RECHECK: '处方复核',
  };
  return labels[type] ?? type;
}

function callbackTypeText(type: string) {
  const labels: Record<string, string> = {
    ORDER_CREATED: '订单创建',
    ORDER_APPROVED: '订单审核',
    ORDER_SHIPPED: '订单发货',
    ORDER_SIGNED: '订单签收',
    PRESCRIPTION_STATUS: '处方状态',
  };
  return labels[type] ?? type;
}

function scrollToOrderDetail() {
  document.getElementById('order-detail-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function queryOrder() {
  const trimmed = orderNo.value.trim();
  if (!trimmed) {
    orderError.value = '请输入平台订单号。当前后端只支持按平台订单号查询。';
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
    orderNo.value = trimmed;
    emit('notice', 'success', `已查询到订单 ${nextOrder.orderNo}`);
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
        处方类型：
        <select v-model="prescriptionType" class="legacy-input">
          <option value="">请选择</option>
          <option value="代煎">代煎</option>
          <option value="自煎">自煎</option>
        </select>
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
        <select v-model="orderStatus" class="legacy-input">
          <option value="">请选择</option>
          <option value="CREATED">已创建</option>
          <option value="PENDING">待处理</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
          <option value="PACKED">已打包</option>
          <option value="SHIPPED">已发货</option>
          <option value="SIGNED">已签收</option>
        </select>
      </li>
      <li>
        煎煮中心：
        <select v-model="decoctionCenter" class="legacy-input input-large">
          <option value="">请选择</option>
          <option value="良益堂煎药中心">良益堂煎药中心</option>
          <option value="良益堂煎煮中心">良益堂煎煮中心</option>
        </select>
      </li>
      <li>
        送货方式：
        <select v-model="deliveryType" class="legacy-input">
          <option value="">请选择</option>
          <option value="送医院">送医院</option>
          <option value="送个人">送个人</option>
          <option value="自提">自提</option>
        </select>
      </li>
      <li>
        物流公司：
        <select v-model="logisticsCompany" class="legacy-input">
          <option value="">请选择</option>
          <option value="顺丰">顺丰</option>
          <option value="EMS">EMS</option>
          <option value="自配送">自配送</option>
        </select>
      </li>
      <li>
        省份：
        <input v-model="province" class="legacy-input input-large" />
      </li>
      <li>
        平台订单号/处方号：
        <input
          v-model="orderNo"
          class="legacy-input input-large"
          placeholder="当前仅平台订单号可查询"
          @keyup.enter="queryOrder"
        />
      </li>
      <li>
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" />
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
        <button class="legacy-btn legacy-btn-export" type="button" disabled title="等待后端导出契约">
          导出
        </button>
      </li>
    </ul>

    <p class="order-contract-hint">
      当前后端只支持“平台订单号”查询；开始/结束时间、机构、处方类型、门诊住院、订单状态、煎煮中心、送货方式、物流公司、省份、处方号、机构处方号、病人姓名、收货电话等待后端分页查询契约。
    </p>

    <div class="order-action-bar">
      <button class="legacy-btn" type="button" disabled title="等待后端地址修改契约">地址修改</button>
      <button class="legacy-btn" type="button" disabled title="等待后端处方修改契约">处方修改</button>
      <button class="legacy-btn" type="button" disabled title="等待后端订单初始化契约">初始化</button>
      <button class="legacy-btn" type="button" disabled title="等待后端订单取消契约">取消</button>
      <button class="legacy-btn" type="button" disabled title="等待后端手工走流程契约">走流程</button>
      <button class="legacy-btn" type="button" disabled title="等待后端签收契约">签收</button>
    </div>

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
            <th>收货信息</th>
            <th>送货时间</th>
            <th>状态</th>
            <th>批次</th>
            <th>订单备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!order" class="legacy-main-info">
            <td colspan="17" class="legacy-empty">
              {{ orderLoading ? '正在查询处方订单' : '请输入平台订单号后查询' }}
            </td>
          </tr>
          <tr v-else class="legacy-main-info">
            <td>{{ prescriptionNos }}</td>
            <td>{{ orderCreatedAt }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ externalPrescriptionNos }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td class="legacy-left">{{ waitingValue() }}</td>
            <td>{{ waitingValue() }}</td>
            <td>
              <StatusPill :value="statusText(primaryOrderStatus)" :tone="statusTone(primaryOrderStatus)" />
            </td>
            <td>{{ waitingValue() }}</td>
            <td class="legacy-left">{{ waitingValue() }}</td>
            <td>
              <button class="legacy-link-btn" type="button" @click="scrollToOrderDetail">查看详情</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="legacy-page-summary">{{ pageSummary }}</p>

    <section id="order-detail-panel" class="order-detail-workbench">
      <section class="order-detail-section">
        <div class="order-section-title">
          <h2>提示信息</h2>
        </div>
        <p class="order-detail-note">
          本页已按老订单详情拆分为只读工作台。当前 API 只返回订单基础字段和履约进度；药品明细、金额、收货地址、患者、医生、机构、分页筛选和导出动作等待后端契约。
        </p>
      </section>

      <template v-if="order">
        <section class="order-detail-section">
          <div class="order-section-title">
            <h2>订单信息</h2>
          </div>
          <div class="order-detail-grid">
            <div>
              <span>平台订单号</span>
              <strong>{{ rowValue(order.orderNo) }}</strong>
            </div>
            <div>
              <span>订单 ID</span>
              <strong>{{ rowValue(order.orderId) }}</strong>
            </div>
            <div>
              <span>外部订单号</span>
              <strong>{{ rowValue(order.externalOrderNo) }}</strong>
            </div>
            <div>
              <span>订单状态</span>
              <StatusPill :value="statusText(primaryOrderStatus)" :tone="statusTone(primaryOrderStatus)" />
            </div>
            <div>
              <span>是否重复推单</span>
              <strong>{{ rowValue(order.duplicated) }}</strong>
            </div>
            <div>
              <span>创建时间</span>
              <strong>{{ orderCreatedAt }}</strong>
            </div>
            <div>
              <span>最近更新</span>
              <strong>{{ orderUpdatedAt }}</strong>
            </div>
            <div>
              <span>机构名称</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>患者信息</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>收货地址</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
          </div>
        </section>

        <section class="order-detail-section">
          <div class="order-section-title">
            <h2>处方信息</h2>
          </div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>处方状态</th>
                  <th>处方类型</th>
                  <th>门诊住院</th>
                  <th>医生</th>
                  <th>患者</th>
                  <th>明细数</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="prescriptions.length === 0">
                  <td colspan="9" class="empty">暂无处方信息</td>
                </tr>
                <tr v-for="item in prescriptions" :key="item.prescriptionId">
                  <td>{{ rowValue(item.prescriptionNo) }}</td>
                  <td>{{ rowValue(item.externalPrescriptionNo) }}</td>
                  <td>
                    <StatusPill :value="statusText(item.prescriptionStatus)" :tone="statusTone(item.prescriptionStatus)" />
                  </td>
                  <td>{{ waitingValue() }}</td>
                  <td>{{ waitingValue() }}</td>
                  <td>{{ waitingValue() }}</td>
                  <td>{{ waitingValue() }}</td>
                  <td>{{ rowValue(item.detailCount) }}</td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="order-detail-section">
          <div class="order-section-title">
            <h2>药品信息</h2>
          </div>
          <div class="order-placeholder-panel">
            药品明细、药品规格、数量、单价、用法用量等待后端详情接口。
          </div>
        </section>

        <section class="order-detail-section">
          <div class="order-section-title">
            <h2>金额汇总</h2>
          </div>
          <div class="order-detail-grid amount-grid">
            <div>
              <span>处方金额</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>药品金额</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>煎煮费</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>物流费</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>优惠金额</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
            <div>
              <span>应收金额</span>
              <strong>{{ waitingValue() }}</strong>
            </div>
          </div>
        </section>

        <section class="order-detail-section">
          <div class="order-section-title">
            <h2>履约进度/状态日志</h2>
          </div>

          <div class="order-detail-grid progress-summary-grid">
            <div>
              <span>处方数</span>
              <strong>{{ prescriptions.length }}</strong>
            </div>
            <div>
              <span>流程任务</span>
              <strong>{{ workflowTasks.length }}</strong>
            </div>
            <div>
              <span>调剂记录</span>
              <strong>{{ dispenseRecords.length }}</strong>
            </div>
            <div>
              <span>煎煮任务</span>
              <strong>{{ decoctionTasks.length }}</strong>
            </div>
            <div>
              <span>物流单</span>
              <strong>{{ shipments.length }}</strong>
            </div>
            <div>
              <span>回调记录</span>
              <strong>{{ callbacks.length }}</strong>
            </div>
            <div>
              <span>状态日志</span>
              <strong>{{ statusLogs.length }}</strong>
            </div>
          </div>

          <div class="order-subsection-title">流程任务</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>流程节点</th>
                  <th>状态</th>
                  <th>处理人</th>
                  <th>意见</th>
                  <th>创建时间</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="workflowTasks.length === 0">
                  <td colspan="6" class="empty">暂无流程任务</td>
                </tr>
                <tr v-for="task in workflowTasks" :key="task.taskId">
                  <td>{{ taskTypeText(task.taskType) }}</td>
                  <td><StatusPill :value="statusText(task.taskStatus)" :tone="statusTone(task.taskStatus)" /></td>
                  <td>{{ rowValue(task.operator) }}</td>
                  <td class="legacy-left">{{ rowValue(task.comment) }}</td>
                  <td>{{ formatDate(task.createdAt) }}</td>
                  <td>{{ formatDate(task.completedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="order-subsection-title">调剂/煎煮/物流</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>业务编号</th>
                  <th>状态/结果</th>
                  <th>操作人/对象</th>
                  <th>补充信息</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in dispenseRecords" :key="record.recordId">
                  <td>调剂</td>
                  <td>{{ rowValue(record.taskId) }}</td>
                  <td><StatusPill :value="statusText(record.printStatus)" :tone="statusTone(record.printStatus)" /></td>
                  <td>{{ rowValue(record.dispenser) }}</td>
                  <td class="legacy-left">{{ rowValue(record.dispenseComment) }}</td>
                  <td>{{ formatDate(record.dispensedAt) }}</td>
                </tr>
                <tr v-for="task in decoctionTasks" :key="task.taskId">
                  <td>煎煮</td>
                  <td>{{ rowValue(task.taskNo) }}</td>
                  <td><StatusPill :value="statusText(task.taskStatus)" :tone="statusTone(task.taskStatus)" /></td>
                  <td>{{ rowValue(task.operator) }}</td>
                  <td class="legacy-left">
                    处方 {{ rowValue(task.prescriptionNo) }}；设备 {{ rowValue(task.deviceCode) }}；桶号 {{ rowValue(task.pailNo) }}
                  </td>
                  <td>{{ formatDate(task.finishedAt || task.startedAt || task.createdAt) }}</td>
                </tr>
                <tr v-for="shipment in shipments" :key="shipment.shipmentId">
                  <td>物流</td>
                  <td>{{ rowValue(shipment.logisticsNo) }}</td>
                  <td><StatusPill :value="statusText(shipment.logisticsStatus)" :tone="statusTone(shipment.logisticsStatus)" /></td>
                  <td>{{ rowValue(shipment.logisticsCompany) }}</td>
                  <td class="legacy-left">
                    {{ rowValue(shipment.latestTraceStatus) }} {{ shipment.latestTraceContent || '' }}
                  </td>
                  <td>{{ formatDate(shipment.latestTraceTime) }}</td>
                </tr>
                <tr v-if="dispenseRecords.length === 0 && decoctionTasks.length === 0 && shipments.length === 0">
                  <td colspan="6" class="empty">暂无调剂、煎煮或物流记录</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="order-subsection-title">回调记录</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>回调类型</th>
                  <th>业务 ID</th>
                  <th>状态</th>
                  <th>重试次数</th>
                  <th>下次重试</th>
                  <th>更新时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="callbacks.length === 0">
                  <td colspan="6" class="empty">暂无回调记录</td>
                </tr>
                <tr v-for="callback in callbacks" :key="callback.callbackId">
                  <td>{{ callbackTypeText(callback.callbackType) }}</td>
                  <td>{{ rowValue(callback.businessId) }}</td>
                  <td><StatusPill :value="statusText(callback.callbackStatus)" :tone="statusTone(callback.callbackStatus)" /></td>
                  <td>{{ rowValue(callback.retryCount) }}</td>
                  <td>{{ formatDate(callback.nextRetryAt) }}</td>
                  <td>{{ formatDate(callback.updatedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="order-subsection-title">状态日志</div>
          <div class="table-wrap">
            <table class="order-detail-table">
              <thead>
                <tr>
                  <th>原状态</th>
                  <th>目标状态</th>
                  <th>操作类型</th>
                  <th>来源</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="statusLogs.length === 0">
                  <td colspan="5" class="empty">暂无状态日志</td>
                </tr>
                <tr v-for="log in statusLogs" :key="log.logId">
                  <td>{{ statusText(log.fromStatus) }}</td>
                  <td><StatusPill :value="statusText(log.toStatus)" :tone="statusTone(log.toStatus)" /></td>
                  <td>{{ rowValue(log.operatorType) }}</td>
                  <td>{{ rowValue(log.source) }}</td>
                  <td>{{ formatDate(log.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <section v-else class="order-detail-section">
        <div class="order-section-title">
          <h2>订单详情</h2>
        </div>
        <p class="legacy-empty">查询平台订单号后展示订单信息、处方信息、药品信息、金额汇总、履约进度和状态日志。</p>
      </section>
    </section>
  </section>
</template>

<style scoped>
.order-contract-hint {
  margin: -4px 0 10px;
  color: #6f7d91;
  font-size: 13px;
  line-height: 1.6;
}

.order-action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.order-result-table {
  min-width: 1860px;
}

.order-detail-workbench {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.order-detail-section {
  padding: 12px;
  border: 1px solid #d8e0ea;
  background: #fff;
}

.order-section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.order-section-title h2 {
  margin: 0;
  color: #1f5fa3;
  font-size: 15px;
}

.order-detail-note {
  margin: 0;
  color: #6f7d91;
  font-size: 13px;
  line-height: 1.6;
}

.order-detail-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.order-detail-grid div {
  min-width: 0;
  padding: 8px;
  border: 1px solid #edf1f5;
  background: #fbfcfe;
}

.order-detail-grid span {
  display: block;
  margin-bottom: 4px;
  color: #667085;
  font-size: 12px;
}

.order-detail-grid strong {
  display: block;
  overflow-wrap: anywhere;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.4;
}

.amount-grid,
.progress-summary-grid {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.order-placeholder-panel {
  padding: 14px;
  border: 1px dashed #98a2b3;
  background: #fcfcfd;
  color: #667085;
  font-size: 13px;
}

.order-subsection-title {
  margin: 14px 0 8px;
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
}

.order-detail-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  font-size: 12px;
}

.order-detail-table th,
.order-detail-table td {
  padding: 7px 8px;
  border: 1px solid #d0d5dd;
  text-align: center;
  vertical-align: top;
}

.order-detail-table th {
  background: #f2f4f7;
  color: #1f2937;
  font-weight: 700;
}

.order-detail-table td {
  color: #344054;
}

@media (max-width: 1180px) {
  .order-detail-grid,
  .amount-grid,
  .progress-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .order-detail-grid,
  .amount-grid,
  .progress-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
