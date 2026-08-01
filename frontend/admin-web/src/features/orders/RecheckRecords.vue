<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { listAdminOrderRechecks } from '../../api/order';
import type {
  AdminOrderRecheckItem,
  AdminOrderRecheckPage,
  AdminOrderRecheckQueryParams,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { boundedPositiveInteger, currentIsoDate, displayValue, formatDate, joinDisplayParts, labelFromMap } from '../../domain/formatters';
import { errorMessage } from '../../domain/errors';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  'count-changed': [count: number];
}>();

const startTime = ref('');
const endTime = ref('');
const institution = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const deliveryType = ref('');
const isWithin = ref('');
const recheckStatus = ref('PENDING');
const batchNo = ref('');
const prescriptionNo = ref('');
const dispenser = ref('');
const rechecker = ref('');
const page = ref(1);
const pageSize = ref(20);
const recheckPage = ref<AdminOrderRecheckPage | null>(null);
const loading = ref(false);
const errorLine = ref('');

const rows = computed(() => recheckPage.value?.records ?? []);
const total = computed(() => recheckPage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

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
  return labelFromMap(value, labels);
}

function deliveryTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    DEFAULT: '默认',
    HOSPITAL: '送医院',
    HOME: '送个人',
    0: '默认',
    1: '送医院',
    2: '送个人',
  };
  return labelFromMap(value, labels);
}

function withinText(value: number | null | undefined) {
  if (value === 0) return '内服';
  if (value === 1) return '外用';
  return '-';
}

function batchText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    1: '早批次',
    2: '午批次',
    3: '晚批次',
    MORNING: '早批次',
    NOON: '午批次',
    EVENING: '晚批次',
  };
  return labelFromMap(value, labels);
}

function patientInfo(row: AdminOrderRecheckItem) {
  return joinDisplayParts([row.patientName, row.patientPhone], ' / ');
}

function queryParams(): AdminOrderRecheckQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    prescriptionType: prescriptionType.value,
    hospitalType: hospitalType.value,
    isWithin: isWithin.value,
    deliveryType: deliveryType.value,
    recheckStatus: recheckStatus.value,
    batchNo: batchNo.value,
    prescriptionNo: prescriptionNo.value,
    dispenser: dispenser.value,
    rechecker: rechecker.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function downloadRecheckCsv() {
  downloadCsv(
    `复核管理列表-${currentIsoDate()}.csv`,
    ['平台处方号', '机构名称', '审核批次', '送医院', '送货时间', '病人信息', '处方类型', '服用方法', '门诊住院', '接单时间', '调剂时间', '调剂员工号', '复核时间', '复核员工号', '加水桶', '订单备注', '订单状态'],
    rows.value.map((row) => [
      row.prescriptionNo,
      row.institutionName,
      batchText(row.batchNo),
      deliveryTypeText(row.addressType),
      formatDate(row.deliveryTime),
      patientInfo(row),
      prescriptionTypeText(row.prescriptionType),
      withinText(row.isWithin),
      hospitalTypeText(row.hospitalType),
      formatDate(row.orderCreatedAt),
      formatDate(row.dispensedAt),
      row.dispenser,
      formatDate(row.recheckedAt),
      row.rechecker,
      row.pailNos,
      row.orderRemark,
      row.orderStatus,
    ]),
  );
  emit('notice', 'success', `已导出本页 ${rows.value.length} 条复核管理记录`);
}

async function refreshOrderRechecks() {
  loading.value = true;
  errorLine.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminOrderRechecks(queryParams());
    recheckPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('count-changed', nextPage.total);
    emit('notice', 'success', `已查询到 ${nextPage.total} 条复核管理记录`);
  } catch (error) {
    recheckPage.value = null;
    emit('count-changed', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderRechecks();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderRechecks();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderRechecks();
}

function showRecheckHint(row: AdminOrderRecheckItem) {
  emit('notice', 'info', `处方 ${row.prescriptionNo} 请在“处方复核”或“处方复核（多桶）”页面完成复核`);
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshOrderRechecks();
  },
  { immediate: true },
);

defineExpose({
  refreshOrderRechecks,
});
</script>

<template>
  <section class="legacy-page order-list-page order-recheck-records-page">
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
        <select v-model="prescriptionType" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="2">代煎</option>
          <option value="1">饮片</option>
          <option value="3">膏方</option>
          <option value="4">丸剂</option>
          <option value="5">散剂</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="1">门诊</option>
          <option value="2">住院</option>
          <option value="3">其他</option>
        </select>
      </li>
      <li>
        送医院：
        <select v-model="deliveryType" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="0">默认</option>
          <option value="1">送医院</option>
          <option value="2">送个人</option>
        </select>
      </li>
      <li>
        服用方法：
        <select v-model="isWithin" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="0">内服</option>
          <option value="1">外用</option>
        </select>
      </li>
      <li>
        复核状态：
        <select v-model="recheckStatus" class="legacy-input input-small">
          <option value="PENDING">未复核</option>
          <option value="RECHECKED">已复核</option>
        </select>
      </li>
      <li>
        审核批次：
        <select v-model="batchNo" class="legacy-input input-small">
          <option value="">请选择</option>
          <option value="1">早批次</option>
          <option value="3">晚批次</option>
        </select>
      </li>
      <li>
        平台处方号：
        <input v-model="prescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        调剂工号：
        <input v-model="dispenser" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        复核工号：
        <input v-model="rechecker" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        条数：
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="5" max="100" step="5" />
      </li>
      <li class="legacy-actions">
        <button type="button" class="legacy-btn primary" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
        <button type="button" class="legacy-btn" :disabled="rows.length === 0" @click="downloadRecheckCsv">
          导出
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="legacy-error">{{ errorLine }}</p>

    <div class="legacy-table-wrap">
      <table class="legacy-table main_table">
        <thead>
          <tr>
            <th>平台处方号</th>
            <th>机构名称</th>
            <th>审核批次</th>
            <th>送医院</th>
            <th>送货时间</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>服用方法</th>
            <th>门诊住院</th>
            <th>接单时间</th>
            <th>调剂时间</th>
            <th>调剂员工号</th>
            <th>复核时间</th>
            <th>复核员工号</th>
            <th>加水桶</th>
            <th>订单备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0" class="legacy-main-info">
            <td colspan="17" class="legacy-empty">{{ loading ? '正在查询复核记录' : '没有相关数据' }}</td>
          </tr>
          <tr v-for="row in rows" :key="`${row.orderId}-${row.prescriptionId}`" class="legacy-main-info">
            <td>{{ displayValue(row.prescriptionNo) }}</td>
            <td>{{ displayValue(row.institutionName) }}</td>
            <td>{{ batchText(row.batchNo) }}</td>
            <td>{{ deliveryTypeText(row.addressType) }}</td>
            <td>{{ formatDate(row.deliveryTime) }}</td>
            <td>{{ patientInfo(row) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionType) }}</td>
            <td>{{ withinText(row.isWithin) }}</td>
            <td>{{ hospitalTypeText(row.hospitalType) }}</td>
            <td>{{ formatDate(row.orderCreatedAt) }}</td>
            <td>{{ formatDate(row.dispensedAt) }}</td>
            <td>{{ displayValue(row.dispenser) }}</td>
            <td>{{ formatDate(row.recheckedAt) }}</td>
            <td>{{ displayValue(row.rechecker) }}</td>
            <td>{{ displayValue(row.pailNos) }}</td>
            <td class="legacy-left">{{ displayValue(row.orderRemark) }}</td>
            <td>
              <StatusPill v-if="row.recheckedAt" value="已复核" :tone="statusTone('RECHECKED')" />
              <button v-else type="button" class="legacy-btn tiny" @click="showRecheckHint(row)">复核</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <span>显示 {{ rows.length > 0 ? (page - 1) * pageSize + 1 : 0 }} 至 {{ Math.min(page * pageSize, total) }} 项记录，共 {{ total }} 项</span>
      <button type="button" class="legacy-btn" :disabled="!hasPreviousPage" @click="goPreviousPage">上一页</button>
      <button type="button" class="legacy-btn" :disabled="!hasNextPage" @click="goNextPage">下一页</button>
    </div>
  </section>
</template>
