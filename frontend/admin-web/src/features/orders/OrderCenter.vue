<script setup lang="ts">
import { computed, ref } from 'vue';
import { ApiError } from '../../api/client';
import {
  getAdminOrderDetail,
  getOrder,
  getOrderProgress,
  listAdminOrders,
  updateAdminOrderAddress,
} from '../../api/order';
import type {
  AdminOrderDetail,
  AdminOrderDetailDrug,
  AdminOrderListItem,
  AdminOrderPage,
  AdminOrderAddressUpdateCommand,
  OrderCreateResult,
  OrderProgressSnapshot,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
interface DetailDrugRow {
  prescriptionNo: string;
  externalPrescriptionNo: string;
  detail: AdminOrderDetailDrug;
}
interface AddressForm {
  receiverName: string;
  receiverPhone: string;
  receiverProvince: string;
  receiverCity: string;
  receiverZone: string;
  receiverAddress: string;
  addressType: string;
  deliveryTime: string;
  operator: string;
  reason: string;
}
type NumericValue = string | number | null | undefined;

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
const orderDetail = ref<AdminOrderDetail | null>(null);
const orderPage = ref<AdminOrderPage | null>(null);
const orderLoading = ref(false);
const detailLoading = ref(false);
const orderError = ref('');
const selectedOrderNo = ref('');
const page = ref(1);
const pageSize = ref(20);
const addressModalOpen = ref(false);
const addressSubmitting = ref(false);
const addressForm = ref<AddressForm>({
  receiverName: '',
  receiverPhone: '',
  receiverProvince: '',
  receiverCity: '',
  receiverZone: '',
  receiverAddress: '',
  addressType: '',
  deliveryTime: '',
  operator: 'admin',
  reason: '',
});

const orderRows = computed(() => orderPage.value?.records ?? []);
const detailPrescriptions = computed(() => orderDetail.value?.prescriptions ?? []);
const detailDrugRows = computed<DetailDrugRow[]>(() => detailPrescriptions.value.flatMap((prescription) => (
  prescription.details.map((detail) => ({
    prescriptionNo: prescription.prescriptionNo,
    externalPrescriptionNo: prescription.externalPrescriptionNo,
    detail,
  }))
)));
const detailAmountSummary = computed(() => {
  const prescriptionAmount = sumNumbers(detailPrescriptions.value.map((item) => item.totalAmount));
  const drugAmount = sumNumbers(detailDrugRows.value.map((row) => row.detail.totalPrice));
  const decoctionAmount = sumNumbers(detailPrescriptions.value.map((item) => item.decoctionTotalPrice));
  const settlementDetailAmount = sumNumbers(detailDrugRows.value.map((row) => row.detail.settlementTotalPrice));
  return {
    prescriptionAmount,
    drugAmount,
    decoctionAmount,
    receivableAmount: prescriptionAmount ?? settlementDetailAmount ?? (
      drugAmount !== null || decoctionAmount !== null ? (drugAmount ?? 0) + (decoctionAmount ?? 0) : null
    ),
  };
});
const prescriptions = computed(() => orderProgress.value?.prescriptions ?? []);
const workflowTasks = computed(() => orderProgress.value?.workflowTasks ?? []);
const dispenseRecords = computed(() => orderProgress.value?.dispenseRecords ?? []);
const decoctionTasks = computed(() => orderProgress.value?.decoctionTasks ?? []);
const shipments = computed(() => orderProgress.value?.shipments ?? []);
const callbacks = computed(() => orderProgress.value?.callbacks ?? []);
const statusLogs = computed(() => orderProgress.value?.statusLogs ?? []);
const resultCount = computed(() => orderPage.value?.total ?? 0);
const primaryOrderStatus = computed(() => orderProgress.value?.orderStatus ?? order.value?.status ?? '');
const orderCreatedAt = computed(() => formatDate(orderProgress.value?.createdAt));
const orderUpdatedAt = computed(() => formatDate(orderProgress.value?.updatedAt));
const hasPreviousPage = computed(() => page.value > 1 && !orderLoading.value);
const hasNextPage = computed(() => (
  !orderLoading.value && page.value * pageSize.value < resultCount.value
));
const canEditAddress = computed(() => !!orderDetail.value && !detailLoading.value && !addressSubmitting.value);
const pageSummary = computed(() => {
  const total = resultCount.value;
  if (total === 0) return '显示第 0 至 0 项记录，共 0 项';
  const start = (page.value - 1) * pageSize.value + 1;
  const end = Math.min(start + orderRows.value.length - 1, total);
  return `显示第 ${start} 至 ${end} 项记录，共 ${total} 项`;
});

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

function numericValue(value: NumericValue) {
  if (value === null || value === undefined || value === '') return null;
  const nextValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(nextValue) ? nextValue : null;
}

function sumNumbers(values: NumericValue[]) {
  let total = 0;
  let hasValue = false;
  for (const value of values) {
    const nextValue = numericValue(value);
    if (nextValue !== null) {
      total += nextValue;
      hasValue = true;
    }
  }
  return hasValue ? total : null;
}

function moneyValue(value: NumericValue) {
  const nextValue = numericValue(value);
  return nextValue === null ? EMPTY_VALUE : nextValue.toFixed(2);
}

function amountValue(value: NumericValue) {
  const nextValue = numericValue(value);
  if (nextValue === null) return EMPTY_VALUE;
  return Number.isInteger(nextValue) ? String(nextValue) : String(Number(nextValue.toFixed(4)));
}

function hospitalTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    1: '门诊',
    2: '住院',
    3: '其他',
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    OTHER: '其他',
    门诊: '门诊',
    住院: '住院',
    其他: '其他',
  };
  return type ? labels[type] ?? type : EMPTY_VALUE;
}

function batchText(batchNo: string | null | undefined) {
  const labels: Record<string, string> = {
    1: '早批次',
    2: '午批次',
    3: '晚批次',
    MORNING: '早批次',
    NOON: '午批次',
    EVENING: '晚批次',
    早批次: '早批次',
    午批次: '午批次',
    晚批次: '晚批次',
  };
  return batchNo ? labels[batchNo] ?? batchNo : EMPTY_VALUE;
}

function legacyDateTimeInput(value: string | null | undefined) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (nextValue: number) => String(nextValue).padStart(2, '0');
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function receiverSummary(row: AdminOrderListItem) {
  const address = [row.receiverProvince, row.receiverCity, row.receiverZone, row.receiverAddress]
    .filter((item): item is string => !!item && item.trim().length > 0)
    .join('');
  const pieces = [row.receiverName, row.receiverPhone, address]
    .filter((item): item is string => !!item && item.trim().length > 0);
  return pieces.length > 0 ? pieces.join(' / ') : EMPTY_VALUE;
}

function detailPatientSummary() {
  if (!orderDetail.value) return EMPTY_VALUE;
  const pieces = [orderDetail.value.patientName, orderDetail.value.patientPhone]
    .filter((item): item is string => !!item && item.trim().length > 0);
  return pieces.length > 0 ? pieces.join(' / ') : EMPTY_VALUE;
}

function detailReceiverSummary() {
  if (!orderDetail.value) return EMPTY_VALUE;
  const address = [
    orderDetail.value.receiverProvince,
    orderDetail.value.receiverCity,
    orderDetail.value.receiverZone,
    orderDetail.value.receiverAddress,
  ].filter((item): item is string => !!item && item.trim().length > 0).join('');
  const pieces = [orderDetail.value.receiverName, orderDetail.value.receiverPhone, address]
    .filter((item): item is string => !!item && item.trim().length > 0);
  return pieces.length > 0 ? pieces.join(' / ') : EMPTY_VALUE;
}

function validationSummary() {
  if (!orderDetail.value?.validationStatus && !orderDetail.value?.validationMessage) return EMPTY_VALUE;
  const pieces = [
    statusText(orderDetail.value.validationStatus),
    orderDetail.value.validationMessage,
    formatDate(orderDetail.value.validationCreatedAt),
  ].filter((item): item is string => !!item && item !== EMPTY_VALUE && item.trim().length > 0);
  return pieces.join(' / ');
}

function deliveryTypeText(type: string | null | undefined) {
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    PATIENT: '送个人',
    PICKUP: '自提',
    '送医院': '送医院',
    '送个人': '送个人',
    '自提': '自提',
  };
  return type ? labels[type] ?? type : EMPTY_VALUE;
}

function waitingValue() {
  return WAITING_API;
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

function openAddressModal() {
  if (!orderDetail.value) {
    orderError.value = '请先查看一条订单详情后再修改地址';
    return;
  }
  addressForm.value = {
    receiverName: orderDetail.value.receiverName ?? '',
    receiverPhone: orderDetail.value.receiverPhone ?? '',
    receiverProvince: orderDetail.value.receiverProvince ?? '',
    receiverCity: orderDetail.value.receiverCity ?? '',
    receiverZone: orderDetail.value.receiverZone ?? '',
    receiverAddress: orderDetail.value.receiverAddress ?? '',
    addressType: orderDetail.value.addressType ?? '',
    deliveryTime: legacyDateTimeInput(orderDetail.value.deliveryTime),
    operator: addressForm.value.operator || 'admin',
    reason: '',
  };
  addressModalOpen.value = true;
}

function closeAddressModal() {
  if (addressSubmitting.value) return;
  addressModalOpen.value = false;
}

async function submitAddressUpdate() {
  if (!orderDetail.value) return;
  const command: AdminOrderAddressUpdateCommand = {
    receiverName: addressForm.value.receiverName.trim(),
    receiverPhone: addressForm.value.receiverPhone.trim(),
    receiverProvince: addressForm.value.receiverProvince.trim(),
    receiverCity: addressForm.value.receiverCity.trim(),
    receiverZone: addressForm.value.receiverZone.trim(),
    receiverAddress: addressForm.value.receiverAddress.trim(),
    addressType: addressForm.value.addressType,
    deliveryTime: addressForm.value.deliveryTime.trim(),
    operator: addressForm.value.operator.trim() || 'admin',
    reason: addressForm.value.reason.trim(),
  };
  if (!command.receiverName || !command.receiverPhone || !command.receiverAddress) {
    orderError.value = '收货人、收货电话和详细地址不能为空';
    return;
  }
  addressSubmitting.value = true;
  orderError.value = '';
  try {
    const targetOrderNo = orderDetail.value.orderNo;
    await updateAdminOrderAddress(targetOrderNo, command);
    const [nextOrder, nextDetail, nextProgress] = await Promise.all([
      getOrder(targetOrderNo),
      getAdminOrderDetail(targetOrderNo),
      getOrderProgress(targetOrderNo),
      queryOrder(),
    ]);
    order.value = nextOrder;
    orderDetail.value = nextDetail;
    orderProgress.value = nextProgress;
    selectedOrderNo.value = targetOrderNo;
    addressModalOpen.value = false;
    emit('notice', 'success', `订单 ${targetOrderNo} 地址已更新`);
  } catch (error) {
    orderError.value = errorMessage(error);
  } finally {
    addressSubmitting.value = false;
  }
}

async function queryOrder() {
  orderLoading.value = true;
  orderError.value = '';
  try {
    const nextPage = await listAdminOrders({
      startTime: startTime.value,
      endTime: endTime.value,
      institution: institution.value,
      prescriptionType: prescriptionType.value,
      hospitalType: hospitalType.value,
      orderStatus: orderStatus.value,
      decoctionCenter: decoctionCenter.value,
      deliveryType: deliveryType.value,
      logisticsCompany: logisticsCompany.value,
      province: province.value,
      keyword: orderNo.value,
      hospitalPrescriptionNo: hospitalPrescriptionNo.value,
      patientName: patientName.value,
      receiverPhone: receiverPhone.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    orderPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    order.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    selectedOrderNo.value = '';
    emit('notice', 'success', `已查询到 ${nextPage.total} 条处方订单记录`);
  } catch (error) {
    orderPage.value = null;
    order.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    orderError.value = errorMessage(error);
  } finally {
    orderLoading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await queryOrder();
}

async function loadOrderDetail(row: AdminOrderListItem) {
  detailLoading.value = true;
  orderError.value = '';
  try {
    const [nextOrder, nextDetail, nextProgress] = await Promise.all([
      getOrder(row.orderNo),
      getAdminOrderDetail(row.orderNo),
      getOrderProgress(row.orderNo),
    ]);
    order.value = nextOrder;
    orderDetail.value = nextDetail;
    orderProgress.value = nextProgress;
    selectedOrderNo.value = row.orderNo;
    emit('notice', 'success', `已加载订单 ${nextOrder.orderNo} 详情`);
    scrollToOrderDetail();
  } catch (error) {
    order.value = null;
    orderProgress.value = null;
    orderDetail.value = null;
    selectedOrderNo.value = '';
    orderError.value = errorMessage(error);
  } finally {
    detailLoading.value = false;
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await queryOrder();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await queryOrder();
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
          <option value="1">门诊</option>
          <option value="2">住院</option>
          <option value="3">其他</option>
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
          <option value="HOSPITAL">送医院</option>
          <option value="PATIENT">送个人</option>
          <option value="PICKUP">自提</option>
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
          placeholder="平台订单号/处方号"
          @keyup.enter="searchFirstPage"
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
        条数：
        <select v-model.number="pageSize" class="legacy-input">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="orderLoading" @click="searchFirstPage">
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
      当前列表已接入后端处方维度分页查询，门诊住院、金额、剂数、送货时间、批次和订单备注来自结构化字段。
    </p>

    <div class="order-action-bar">
      <button class="legacy-btn" type="button" :disabled="!canEditAddress" title="先查看订单详情后修改地址" @click="openAddressModal">地址修改</button>
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
          <tr v-if="orderRows.length === 0" class="legacy-main-info">
            <td colspan="17" class="legacy-empty">
              {{ orderLoading ? '正在查询处方订单' : '请输入筛选条件后查询' }}
            </td>
          </tr>
          <tr v-for="row in orderRows" :key="`${row.orderId}-${row.prescriptionNos}`" class="legacy-main-info">
            <td>{{ rowValue(row.prescriptionNos) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ rowValue(row.storageType) }}</td>
            <td>{{ rowValue(row.institutionName) }}</td>
            <td>{{ hospitalTypeText(row.hospitalTypes) }}</td>
            <td>{{ rowValue(row.externalPrescriptionNos) }}</td>
            <td>{{ rowValue(row.patientName) }}</td>
            <td>{{ rowValue(row.prescriptionTypes) }}</td>
            <td>{{ rowValue(row.doseCount) }}</td>
            <td>{{ moneyValue(row.totalAmount) }}</td>
            <td>{{ deliveryTypeText(row.addressType) }}</td>
            <td class="legacy-left">{{ receiverSummary(row) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td>
              <StatusPill :value="statusText(row.orderStatus)" :tone="statusTone(row.orderStatus)" />
            </td>
            <td>{{ batchText(row.batchNo) }}</td>
            <td class="legacy-left">{{ rowValue(row.orderRemark) }}</td>
            <td>
              <button
                class="legacy-link-btn"
                type="button"
                :disabled="detailLoading && selectedOrderNo === row.orderNo"
                @click="loadOrderDetail(row)"
              >
                {{ detailLoading && selectedOrderNo === row.orderNo ? '加载中' : '查看详情' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="order-page-footer">
      <p class="legacy-page-summary">{{ pageSummary }}</p>
      <div class="order-page-actions">
        <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="goPreviousPage">上一页</button>
        <span>第 {{ page }} 页</span>
        <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="goNextPage">下一页</button>
      </div>
    </div>

    <div v-if="addressModalOpen" class="legacy-modal-mask">
      <section class="legacy-modal address-modal">
        <div class="legacy-modal-head">
          <h2>修改订单地址</h2>
          <button class="legacy-link-btn" type="button" :disabled="addressSubmitting" @click="closeAddressModal">关闭</button>
        </div>
        <div class="address-form-grid">
          <label>
            <span>平台订单号</span>
            <input class="legacy-input" :value="orderDetail?.orderNo" disabled />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="addressForm.operator" class="legacy-input" />
          </label>
          <label>
            <span>收货人</span>
            <input v-model="addressForm.receiverName" class="legacy-input" />
          </label>
          <label>
            <span>收货电话</span>
            <input v-model="addressForm.receiverPhone" class="legacy-input" />
          </label>
          <label>
            <span>送货方式</span>
            <select v-model="addressForm.addressType" class="legacy-input">
              <option value="">默认</option>
              <option value="HOSPITAL">送医院</option>
              <option value="PATIENT">送个人</option>
              <option value="PICKUP">自提</option>
            </select>
          </label>
          <label>
            <span>送货时间</span>
            <input v-model="addressForm.deliveryTime" class="legacy-input" placeholder="YYYY-MM-DD HH:mm:ss" />
          </label>
          <label>
            <span>省份</span>
            <input v-model="addressForm.receiverProvince" class="legacy-input" />
          </label>
          <label>
            <span>城市</span>
            <input v-model="addressForm.receiverCity" class="legacy-input" />
          </label>
          <label>
            <span>区县</span>
            <input v-model="addressForm.receiverZone" class="legacy-input" />
          </label>
          <label class="address-form-wide">
            <span>详细地址</span>
            <input v-model="addressForm.receiverAddress" class="legacy-input" />
          </label>
          <label class="address-form-wide">
            <span>修改原因</span>
            <input v-model="addressForm.reason" class="legacy-input" placeholder="可选" />
          </label>
        </div>
        <div class="legacy-modal-actions">
          <button class="legacy-btn" type="button" :disabled="addressSubmitting" @click="closeAddressModal">取消</button>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="addressSubmitting" @click="submitAddressUpdate">
            {{ addressSubmitting ? '保存中' : '保存地址' }}
          </button>
        </div>
      </section>
    </div>

    <section id="order-detail-panel" class="order-detail-workbench">
      <section class="order-detail-section">
        <div class="order-section-title">
          <h2>提示信息</h2>
        </div>
        <p class="order-detail-note">
          本页已按老订单详情拆分为只读工作台。订单基础信息、处方、药品明细、金额、门诊住院、批次和订单备注来自后端详情接口；导出动作等待后端契约。
          <span v-if="orderDetail">最近校验：{{ validationSummary() }}</span>
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
              <strong>{{ rowValue(orderDetail?.externalOrderNo || order.externalOrderNo) }}</strong>
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
              <strong>{{ formatDate(orderDetail?.createdAt || orderProgress?.createdAt) }}</strong>
            </div>
            <div>
              <span>最近更新</span>
              <strong>{{ formatDate(orderDetail?.updatedAt || orderProgress?.updatedAt) }}</strong>
            </div>
            <div>
              <span>机构名称</span>
              <strong>{{ rowValue(orderDetail?.institutionName) }}</strong>
            </div>
            <div>
              <span>患者信息</span>
              <strong>{{ detailPatientSummary() }}</strong>
            </div>
            <div>
              <span>收货地址</span>
              <strong>{{ detailReceiverSummary() }}</strong>
            </div>
            <div>
              <span>送货方式</span>
              <strong>{{ deliveryTypeText(orderDetail?.addressType) }}</strong>
            </div>
            <div>
              <span>煎煮中心</span>
              <strong>{{ rowValue(orderDetail?.storageType) }}</strong>
            </div>
            <div>
              <span>送货时间</span>
              <strong>{{ formatDate(orderDetail?.deliveryTime) }}</strong>
            </div>
            <div>
              <span>批次</span>
              <strong>{{ batchText(orderDetail?.batchNo) }}</strong>
            </div>
            <div>
              <span>订单备注</span>
              <strong>{{ rowValue(orderDetail?.orderRemark) }}</strong>
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
                  <th>剂数</th>
                  <th>处方金额</th>
                  <th>煎煮剂数</th>
                  <th>医生</th>
                  <th>诊断</th>
                  <th>科室/病区/床号</th>
                  <th>用药指导</th>
                  <th>处方备注</th>
                  <th>明细数</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="detailPrescriptions.length === 0">
                  <td colspan="15" class="empty">暂无处方信息</td>
                </tr>
                <tr v-for="item in detailPrescriptions" :key="item.prescriptionId">
                  <td>{{ rowValue(item.prescriptionNo) }}</td>
                  <td>{{ rowValue(item.externalPrescriptionNo) }}</td>
                  <td>
                    <StatusPill :value="statusText(item.prescriptionStatus)" :tone="statusTone(item.prescriptionStatus)" />
                  </td>
                  <td>{{ rowValue(item.prescriptionType) }}</td>
                  <td>{{ hospitalTypeText(item.hospitalType) }}</td>
                  <td>{{ rowValue(item.doseCount) }}</td>
                  <td>{{ moneyValue(item.totalAmount) }}</td>
                  <td>{{ rowValue(item.decoctionCount) }}</td>
                  <td>{{ rowValue(item.doctorName) }}</td>
                  <td class="legacy-left">{{ rowValue(item.diagnosis) }}</td>
                  <td class="legacy-left">{{ rowValue([item.departmentName, item.wardName, item.bedNo].filter(Boolean).join(' / ')) }}</td>
                  <td class="legacy-left">{{ rowValue([item.medicationMethod, item.medicationInstruction].filter(Boolean).join(' / ')) }}</td>
                  <td class="legacy-left">{{ rowValue(item.prescriptionRemark) }}</td>
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
          <div class="table-wrap">
            <table class="order-detail-table drug-detail-table">
              <thead>
                <tr>
                  <th>平台处方号</th>
                  <th>机构处方号</th>
                  <th>机构药品编码</th>
                  <th>机构药品名称</th>
                  <th>平台药品编码</th>
                  <th>平台药品名称</th>
                  <th>规格</th>
                  <th>产地</th>
                  <th>剂量</th>
                  <th>单位</th>
                  <th>数量</th>
                  <th>单价</th>
                  <th>金额</th>
                  <th>结算单价</th>
                  <th>结算金额</th>
                  <th>用法</th>
                  <th>批号</th>
                  <th>备注</th>
                  <th>审方提示</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="detailDrugRows.length === 0">
                  <td colspan="19" class="empty">暂无药品明细</td>
                </tr>
                <tr v-for="row in detailDrugRows" :key="row.detail.detailId">
                  <td>{{ rowValue(row.prescriptionNo) }}</td>
                  <td>{{ rowValue(row.externalPrescriptionNo) }}</td>
                  <td>{{ rowValue(row.detail.drugCode) }}</td>
                  <td class="legacy-left">{{ rowValue(row.detail.drugName) }}</td>
                  <td>{{ rowValue(row.detail.platformDrugCode) }}</td>
                  <td class="legacy-left">{{ rowValue(row.detail.platformDrugName) }}</td>
                  <td>{{ rowValue(row.detail.drugSpecs) }}</td>
                  <td>{{ rowValue(row.detail.drugOrigin) }}</td>
                  <td>{{ rowValue(row.detail.dose) }}</td>
                  <td>{{ rowValue(row.detail.unit) }}</td>
                  <td>{{ amountValue(row.detail.quantity) }}</td>
                  <td>{{ moneyValue(row.detail.unitPrice) }}</td>
                  <td>{{ moneyValue(row.detail.totalPrice) }}</td>
                  <td>{{ moneyValue(row.detail.settlementUnitPrice) }}</td>
                  <td>{{ moneyValue(row.detail.settlementTotalPrice) }}</td>
                  <td class="legacy-left">{{ rowValue(row.detail.specialUsage) }}</td>
                  <td>{{ rowValue(row.detail.batchNo) }}</td>
                  <td class="legacy-left">{{ rowValue(row.detail.remark) }}</td>
                  <td class="legacy-left">{{ rowValue(row.detail.validationTips) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="order-detail-section">
          <div class="order-section-title">
            <h2>金额汇总</h2>
          </div>
          <div class="order-detail-grid amount-grid">
            <div>
              <span>处方金额</span>
              <strong>{{ moneyValue(detailAmountSummary.prescriptionAmount) }}</strong>
            </div>
            <div>
              <span>药品金额</span>
              <strong>{{ moneyValue(detailAmountSummary.drugAmount) }}</strong>
            </div>
            <div>
              <span>煎煮费</span>
              <strong>{{ moneyValue(detailAmountSummary.decoctionAmount) }}</strong>
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
              <strong>{{ moneyValue(detailAmountSummary.receivableAmount) }}</strong>
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

.order-page-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.order-page-footer .legacy-page-summary {
  margin: 0;
}

.order-page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4b5563;
  font-size: 13px;
}

.legacy-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgb(15 23 42 / 38%);
}

.legacy-modal {
  width: min(860px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  padding: 14px;
  border: 1px solid #b9c6d8;
  background: #fff;
  box-shadow: 0 16px 42px rgb(15 23 42 / 22%);
}

.legacy-modal-head,
.legacy-modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.legacy-modal-head {
  margin-bottom: 12px;
}

.legacy-modal-head h2 {
  margin: 0;
  color: #1f5fa3;
  font-size: 15px;
}

.legacy-modal-actions {
  justify-content: flex-end;
  margin-top: 12px;
}

.address-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.address-form-grid label {
  display: grid;
  gap: 4px;
  min-width: 0;
  color: #344054;
  font-size: 12px;
}

.address-form-wide {
  grid-column: span 3;
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

.drug-detail-table {
  min-width: 1720px;
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
  .order-page-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .address-form-grid {
    grid-template-columns: 1fr;
  }

  .address-form-wide {
    grid-column: span 1;
  }

  .order-detail-grid,
  .amount-grid,
  .progress-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
