<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  getAdminOrderDetail,
  listAdminOrders,
  updateAdminOrderAddress,
} from '../../api/order';
import type {
  AdminOrderAddressUpdateCommand,
  AdminOrderDetail,
  AdminOrderListItem,
  AdminOrderPage,
  AdminOrderQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

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
const orderNo = ref('');
const patientName = ref('');
const receiverPhone = ref('');
const deliveryType = ref('');
const page = ref(1);
const pageSize = ref(20);
const orderPage = ref<AdminOrderPage | null>(null);
const selectedOrder = ref<AdminOrderDetail | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const batchSubmitting = ref(false);
const errorLine = ref('');
const selectedOrderNos = ref<string[]>([]);
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

const rows = computed(() => orderPage.value?.records ?? []);
const total = computed(() => orderPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const selectedRows = computed(() => rows.value.filter((row) => selectedOrderNos.value.includes(row.orderNo)));
const selectedCurrentPageAll = computed(() => rows.value.length > 0 && selectedRows.value.length === rows.value.length);

function addressTypeLabel(value: string | null | undefined) {
  if (!value) return '-';
  if (value === '0' || value === 'DEFAULT') return '默认';
  if (value === '1' || value === 'HOSPITAL') return '送医院';
  if (value === '2' || value === 'PERSONAL') return '送个人';
  return value;
}

function fullAddress(row: AdminOrderListItem | AdminOrderDetail) {
  return [
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ].filter(Boolean).join('') || '-';
}

function legacyDateTimeInput(value: string | null | undefined) {
  if (!value) return '';
  return value.replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
}

function queryParams(): AdminOrderQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    keyword: orderNo.value,
    patientName: patientName.value,
    receiverPhone: receiverPhone.value,
    deliveryType: deliveryType.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function buildAddressCommand() {
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
    errorLine.value = '收货人、收货电话和详细地址不能为空';
    return null;
  }
  return command;
}

function isOrderSelected(orderNo: string) {
  return selectedOrderNos.value.includes(orderNo);
}

function toggleOrderSelection(orderNo: string, checked: boolean) {
  selectedOrderNos.value = checked
    ? Array.from(new Set([...selectedOrderNos.value, orderNo]))
    : selectedOrderNos.value.filter((item) => item !== orderNo);
}

function toggleCurrentPageSelection(checked: boolean) {
  selectedOrderNos.value = checked ? rows.value.map((row) => row.orderNo) : [];
}

function downloadAddressCsv() {
  downloadCsv(
    `订单地址修改-第${page.value}页.csv`,
    ['订单号', '机构', '下单时间', '病人姓名', '收货人', '收货电话', '收货地址', '送货方式', '配送日期', '订单状态'],
    rows.value.map((row) => [
      row.orderNo,
      row.institutionName,
      formatDate(row.createdAt),
      row.patientName,
      row.receiverName,
      row.receiverPhone,
      fullAddress(row),
      addressTypeLabel(row.addressType),
      formatDate(row.deliveryTime),
      row.orderStatus,
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条订单地址`);
}

function fillAddressForm(order: AdminOrderDetail) {
  addressForm.value = {
    receiverName: order.receiverName ?? '',
    receiverPhone: order.receiverPhone ?? '',
    receiverProvince: order.receiverProvince ?? '',
    receiverCity: order.receiverCity ?? '',
    receiverZone: order.receiverZone ?? '',
    receiverAddress: order.receiverAddress ?? '',
    addressType: order.addressType ?? '',
    deliveryTime: legacyDateTimeInput(order.deliveryTime),
    operator: addressForm.value.operator || 'admin',
    reason: '',
  };
}

async function refreshAddressOrders() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminOrders(queryParams());
    orderPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    selectedOrderNos.value = [];
    emit('notice', 'success', `已查询到 ${nextPage.total} 条可修改地址订单`);
  } catch (error) {
    orderPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshAddressOrders();
}

async function loadOrder(row: AdminOrderListItem) {
  detailLoading.value = true;
  errorLine.value = '';
  try {
    const detail = await getAdminOrderDetail(row.orderNo);
    selectedOrder.value = detail;
    fillAddressForm(detail);
    emit('notice', 'success', `已加载 ${detail.orderNo} 地址信息`);
  } catch (error) {
    selectedOrder.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    detailLoading.value = false;
  }
}

async function submitAddressUpdate() {
  if (!selectedOrder.value) {
    errorLine.value = '请先选择一条订单';
    return;
  }
  const command = buildAddressCommand();
  if (!command) return;
  submitting.value = true;
  errorLine.value = '';
  try {
    const targetOrderNo = selectedOrder.value.orderNo;
    await updateAdminOrderAddress(targetOrderNo, command);
    const detail = await getAdminOrderDetail(targetOrderNo);
    selectedOrder.value = detail;
    fillAddressForm(detail);
    await refreshAddressOrders();
    emit('notice', 'success', `订单 ${targetOrderNo} 地址已更新`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function submitBatchAddressUpdate() {
  if (selectedRows.value.length === 0) {
    errorLine.value = '请先勾选要批量修改地址的订单';
    return;
  }
  const command = buildAddressCommand();
  if (!command) return;

  batchSubmitting.value = true;
  errorLine.value = '';
  const targets = [...selectedRows.value];
  const failures: string[] = [];
  try {
    for (const row of targets) {
      try {
        await updateAdminOrderAddress(row.orderNo, command);
      } catch (error) {
        failures.push(`${row.orderNo}: ${errorMessage(error)}`);
      }
    }
    await refreshAddressOrders();
    const successCount = targets.length - failures.length;
    if (failures.length > 0) {
      errorLine.value = `批量修改完成：成功 ${successCount} 条，失败 ${failures.length} 条；${failures.join('；')}`;
      emit('notice', 'error', `批量修改地址部分失败：成功 ${successCount} 条，失败 ${failures.length} 条`);
      return;
    }
    emit('notice', 'success', `批量修改地址成功：${successCount} 条`);
  } finally {
    batchSubmitting.value = false;
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshAddressOrders();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshAddressOrders();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshAddressOrders();
  },
  { immediate: true },
);

defineExpose({
  refreshAddressOrders,
});
</script>

<template>
  <section class="legacy-page order-list-page address-modify-page">
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
        <input v-model="institution" class="legacy-input input-large" placeholder="机构名称" />
      </li>
      <li>
        平台订单号/处方号：
        <input v-model="orderNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        病人姓名：
        <input v-model="patientName" class="legacy-input" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        收货电话：
        <input v-model="receiverPhone" class="legacy-input" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        送货方式：
        <select v-model="deliveryType" class="legacy-input">
          <option value="">请选择</option>
          <option value="DEFAULT">默认</option>
          <option value="HOSPITAL">送医院</option>
          <option value="PERSONAL">送个人</option>
          <option value="0">老系统默认</option>
          <option value="1">老系统送医院</option>
          <option value="2">老系统送个人</option>
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadAddressCsv">
          导出当前页
        </button>
      </li>
      <li>
        <button
          class="legacy-btn legacy-btn-export"
          type="button"
          :disabled="batchSubmitting || selectedRows.length === 0"
          title="按当前地址表单逐条修改已勾选订单"
          @click="submitBatchAddressUpdate"
        >
          {{ batchSubmitting ? '批量修改中' : `批量修改地址(${selectedRows.length})` }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-main-table">
        <thead>
          <tr class="legacy-main-head">
            <th>
              <input
                type="checkbox"
                :checked="selectedCurrentPageAll"
                :disabled="rows.length === 0 || loading || submitting || batchSubmitting"
                aria-label="选择当前页订单"
                @change="toggleCurrentPageSelection(($event.target as HTMLInputElement).checked)"
              />
            </th>
            <th>订单号</th>
            <th>机构</th>
            <th>下单时间</th>
            <th>病人姓名</th>
            <th>收货人</th>
            <th>收货电话</th>
            <th>收货人地址</th>
            <th>送货方式</th>
            <th>配送日期</th>
            <th>订单状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="12" class="legacy-empty">正在查询订单地址</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="12" class="legacy-empty">暂无订单地址记录</td>
          </tr>
          <tr
            v-for="row in rows"
            :key="row.orderId"
            class="legacy-main-info"
            :class="{ active: selectedOrder?.orderNo === row.orderNo }"
          >
            <td>
              <input
                type="checkbox"
                :checked="isOrderSelected(row.orderNo)"
                :disabled="submitting || batchSubmitting"
                :aria-label="`选择订单 ${row.orderNo}`"
                @change="toggleOrderSelection(row.orderNo, ($event.target as HTMLInputElement).checked)"
              />
            </td>
            <td>{{ row.orderNo }}</td>
            <td>{{ row.institutionName }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ displayValue(row.patientName) }}</td>
            <td>{{ displayValue(row.receiverName) }}</td>
            <td>{{ displayValue(row.receiverPhone) }}</td>
            <td class="legacy-left">{{ fullAddress(row) }}</td>
            <td>{{ addressTypeLabel(row.addressType) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td><StatusPill :value="row.orderStatus" :tone="statusTone(row.orderStatus)" /></td>
            <td>
              <button class="legacy-link-btn workflow-pass-btn" type="button" :disabled="detailLoading" @click="loadOrder(row)">
                修改
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

    <section v-if="selectedOrder" class="legacy-panel address-edit-panel">
      <div class="legacy-section-title">
        <h2>修改订单地址信息：{{ selectedOrder.orderNo }}</h2>
      </div>

      <div class="legacy-detail-grid">
        <span>旧收货信息</span>
        <strong>{{ displayValue(selectedOrder.receiverName) }}，{{ displayValue(selectedOrder.receiverPhone) }}，{{ fullAddress(selectedOrder) }}</strong>
        <span>机构</span>
        <strong>{{ selectedOrder.institutionName }}</strong>
        <span>病人姓名</span>
        <strong>{{ displayValue(selectedOrder.patientName) }}</strong>
        <span>当前状态</span>
        <strong><StatusPill :value="selectedOrder.orderStatus" :tone="statusTone(selectedOrder.orderStatus)" /></strong>
      </div>

      <ul class="legacy-search address-edit-form">
        <li>
          收货人：
          <input v-model="addressForm.receiverName" class="legacy-input" />
        </li>
        <li>
          收货电话：
          <input v-model="addressForm.receiverPhone" class="legacy-input" />
        </li>
        <li>
          省：
          <input v-model="addressForm.receiverProvince" class="legacy-input" />
        </li>
        <li>
          市：
          <input v-model="addressForm.receiverCity" class="legacy-input" />
        </li>
        <li>
          区：
          <input v-model="addressForm.receiverZone" class="legacy-input" />
        </li>
        <li class="address-detail-input">
          详细地址：
          <input v-model="addressForm.receiverAddress" class="legacy-input input-large" />
        </li>
        <li>
          送货方式：
          <select v-model="addressForm.addressType" class="legacy-input">
            <option value="">请选择</option>
            <option value="DEFAULT">默认</option>
            <option value="HOSPITAL">送医院</option>
            <option value="PERSONAL">送个人</option>
            <option value="0">老系统默认</option>
            <option value="1">老系统送医院</option>
            <option value="2">老系统送个人</option>
          </select>
        </li>
        <li>
          配送日期：
          <input v-model="addressForm.deliveryTime" class="legacy-input input-large" placeholder="YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss" />
        </li>
        <li>
          操作人：
          <input v-model="addressForm.operator" class="legacy-input" />
        </li>
        <li class="address-detail-input">
          修改原因：
          <input v-model="addressForm.reason" class="legacy-input input-large" placeholder="地址核对/客户要求/异常纠正" />
        </li>
        <li>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitAddressUpdate">
            {{ submitting ? '保存中' : '保存地址' }}
          </button>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.address-edit-panel {
  margin-top: 14px;
}

.address-edit-form {
  margin-top: 12px;
}

.address-detail-input {
  min-width: 360px;
}

@media (max-width: 780px) {
  .address-detail-input {
    min-width: 100%;
  }
}
</style>
