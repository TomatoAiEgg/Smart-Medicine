<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  downloadAdminOrderWarehousesCsv,
  listAdminOrderWarehouses,
} from '../../api/order';
import type {
  AdminOrderWarehouseItem,
  AdminOrderWarehousePage,
  AdminOrderWarehouseQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { saveBlob } from '../../domain/download';
import { displayValue, currentIsoDate, formatDate, joinDisplayParts, labelFromMap } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

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
const orderStatus = ref('');
const decoctionCenter = ref('');
const deliveryType = ref('');
const logisticsCompany = ref('');
const province = ref('');
const orderNo = ref('');
const prescriptionNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const receiverPhone = ref('');
const nodeTime = ref('');
const page = ref(1);
const pageSize = ref(20);
const warehousePage = ref<AdminOrderWarehousePage | null>(null);
const loading = ref(false);
const exporting = ref(false);
const errorLine = ref('');

const rows = computed(() => warehousePage.value?.records ?? []);
const total = computed(() => warehousePage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function splitValues(value: string | null | undefined) {
  if (!value) return [];
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function fullAddress(row: AdminOrderWarehouseItem) {
  return joinDisplayParts([
    row.receiverProvince,
    row.receiverCity,
    row.receiverZone,
    row.receiverAddress,
  ]);
}

function deliveryTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    HOSPITAL: '送医院',
    PATIENT: '送个人',
    HOME: '送个人',
    PICKUP: '自提',
    0: '默认',
    1: '送医院',
    2: '送个人',
  };
  return labelFromMap(value, labels);
}

function batchText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    MORNING: '早批次',
    NOON: '午批次',
    EVENING: '晚批次',
    1: '早批次',
    2: '午批次',
    3: '晚批次',
  };
  return labelFromMap(value, labels);
}

function hospitalTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    OUTPATIENT: '门诊',
    INPATIENT: '住院',
    OTHER: '其他',
    1: '门诊',
    2: '住院',
    3: '其他',
  };
  const items = splitValues(value);
  return items.length ? items.map((item) => labelFromMap(item, labels)).join('、') : '-';
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
  return items.length ? items.map((item) => labelFromMap(item, labels)).join('、') : '-';
}

function queryParams(includePaging = true): AdminOrderWarehouseQueryParams {
  return {
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
    orderNo: orderNo.value,
    prescriptionNo: prescriptionNo.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    patientName: patientName.value,
    receiverPhone: receiverPhone.value,
    nodeTime: nodeTime.value,
    ...(includePaging ? { page: page.value, pageSize: pageSize.value } : {}),
  };
}

async function refreshOrderWarehouses() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminOrderWarehouses(queryParams());
    warehousePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('notice', 'success', `已查询到 ${nextPage.total} 条订单仓库记录`);
  } catch (error) {
    warehousePage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderWarehouses();
}

async function exportWarehouseCsv() {
  exporting.value = true;
  errorLine.value = '';
  try {
    const blob = await downloadAdminOrderWarehousesCsv(queryParams(false));
    saveBlob(`订单仓库汇总-${currentIsoDate()}.csv`, blob);
    emit('notice', 'success', '订单仓库汇总已导出');
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    exporting.value = false;
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderWarehouses();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderWarehouses();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshOrderWarehouses();
  },
  { immediate: true },
);

defineExpose({
  refreshOrderWarehouses,
});
</script>

<template>
  <section class="legacy-page order-list-page order-warehouse-page">
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
          <option value="CREAM">膏方</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input input-small">
          <option value="">全部</option>
          <option value="OUTPATIENT">门诊</option>
          <option value="INPATIENT">住院</option>
          <option value="OTHER">其他</option>
        </select>
      </li>
      <li>
        订单状态：
        <select v-model="orderStatus" class="legacy-input input-medium">
          <option value="">全部</option>
          <option value="RECHECKED">已复核</option>
          <option value="DECOCTING">煎煮中</option>
          <option value="DECOCTED">已煎煮</option>
        </select>
      </li>
      <li>
        煎煮中心：
        <input v-model="decoctionCenter" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        送货方式：
        <select v-model="deliveryType" class="legacy-input input-small">
          <option value="">全部</option>
          <option value="HOSPITAL">送医院</option>
          <option value="PATIENT">送个人</option>
          <option value="PICKUP">自提</option>
        </select>
      </li>
      <li>
        物流公司：
        <input v-model="logisticsCompany" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        省份：
        <input v-model="province" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
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
        收货电话：
        <input v-model="receiverPhone" class="legacy-input input-medium" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        节点时间：
        <select v-model="nodeTime" class="legacy-input input-medium">
          <option value="">全部</option>
          <option value="1">今日12点前</option>
          <option value="5">今日12点后</option>
          <option value="21">今日全天</option>
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
        <button class="legacy-btn legacy-btn-purple" type="button" :disabled="exporting" @click="exportWarehouseCsv">
          {{ exporting ? '导出中' : '导出' }}
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-warehouse-table">
        <thead>
          <tr class="legacy-main-head">
            <th>订单号</th>
            <th>订单状态</th>
            <th>接单时间</th>
            <th>批次</th>
            <th>医疗机构</th>
            <th>送货方式</th>
            <th>收货人</th>
            <th>收货电话</th>
            <th>收货时间</th>
            <th>收货地址</th>
            <th>门诊住院</th>
            <th>病人姓名</th>
            <th>病人年龄</th>
            <th>科室</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>包数</th>
            <th>每包剂量</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="18" class="legacy-empty">正在查询订单仓库列表</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="18" class="legacy-empty">没有相关数据</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.orderId" class="legacy-main-info">
            <td>{{ row.orderNo }}</td>
            <td><StatusPill :value="row.orderStatus" :tone="statusTone(row.orderStatus)" /></td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>{{ batchText(row.batchNo) }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td>{{ deliveryTypeText(row.addressType) }}</td>
            <td>{{ displayValue(row.receiverName) }}</td>
            <td>{{ displayValue(row.receiverPhone) }}</td>
            <td>{{ displayValue(formatDate(row.deliveryTime)) }}</td>
            <td class="cell-wide">{{ fullAddress(row) }}</td>
            <td>{{ hospitalTypeText(row.hospitalTypes) }}</td>
            <td>{{ displayValue(row.patientName) }}</td>
            <td>{{ displayValue(row.patientAge) }}</td>
            <td>{{ displayValue(row.departmentNames) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionTypes) }}</td>
            <td>{{ displayValue(row.doseCounts) }}</td>
            <td>{{ displayValue(row.perPackNums) }}</td>
            <td>{{ displayValue(row.perPackDoses) }}</td>
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
  </section>
</template>

<style scoped>
.order-warehouse-page {
  min-width: 1220px;
}

.order-warehouse-table th,
.order-warehouse-table td {
  white-space: nowrap;
}

.order-warehouse-table .cell-wide {
  max-width: 260px;
  white-space: normal;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .order-warehouse-page {
    min-width: 0;
  }
}
</style>
