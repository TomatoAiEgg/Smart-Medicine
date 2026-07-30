<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  getAdminOrderDetail,
  listAdminOrders,
  updateAdminPrescription,
} from '../../api/order';
import type {
  AdminOrderDetail,
  AdminOrderDetailPrescription,
  AdminOrderListItem,
  AdminOrderPage,
  AdminOrderQueryParams,
  AdminPrescriptionUpdateCommand,
} from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';
type FormNumberValue = number | '' | null;

interface PrescriptionForm {
  prescriptionId: string;
  prescriptionType: string;
  hospitalType: string;
  doseCount: FormNumberValue;
  decoctionCount: FormNumberValue;
  boilTimes: FormNumberValue;
  isWithin: FormNumberValue;
  perPackNum: FormNumberValue;
  perPackDose: FormNumberValue;
  medicationMethod: string;
  medicationInstruction: string;
  prescriptionRemark: string;
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
const prescriptionNo = ref('');
const hospitalPrescriptionNo = ref('');
const patientName = ref('');
const prescriptionType = ref('');
const hospitalType = ref('');
const page = ref(1);
const pageSize = ref(20);
const orderPage = ref<AdminOrderPage | null>(null);
const selectedOrder = ref<AdminOrderDetail | null>(null);
const selectedPrescriptionId = ref('');
const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const errorLine = ref('');
const prescriptionForm = ref<PrescriptionForm>({
  prescriptionId: '',
  prescriptionType: '',
  hospitalType: '',
  doseCount: null,
  decoctionCount: null,
  boilTimes: null,
  isWithin: null,
  perPackNum: null,
  perPackDose: null,
  medicationMethod: '',
  medicationInstruction: '',
  prescriptionRemark: '',
  operator: 'admin',
  reason: '',
});

const rows = computed(() => orderPage.value?.records ?? []);
const total = computed(() => orderPage.value?.total ?? 0);
const prescriptions = computed(() => selectedOrder.value?.prescriptions ?? []);
const selectedPrescription = computed(() => (
  prescriptions.value.find((item) => item.prescriptionId === selectedPrescriptionId.value) ?? null
));
const calculatedDecoctionCount = computed(() => {
  const doseCount = formNumber(prescriptionForm.value.doseCount);
  const boilTimes = formNumber(prescriptionForm.value.boilTimes);
  if (doseCount !== null && boilTimes !== null) return doseCount * boilTimes;
  return formNumber(prescriptionForm.value.decoctionCount);
});
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function rowValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function formNumber(value: FormNumberValue) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function isNonNegativeInteger(value: number | null) {
  return value !== null && Number.isInteger(value) && value >= 0;
}

function numericValue(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return null;
  const nextValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(nextValue) ? nextValue : null;
}

function amountValue(value: string | number | null | undefined) {
  const nextValue = numericValue(value);
  if (nextValue === null) return '-';
  return Number.isInteger(nextValue) ? String(nextValue) : String(Number(nextValue.toFixed(4)));
}

function prescriptionTypeText(value: string | null | undefined) {
  const labels: Record<string, string> = {
    DECOCTION: '代煎',
    SELF_DECOCTION: '自煎',
    OTHER: '其他',
    HERBAL_PIECE: '饮片',
    CREAM: '膏方',
    PILL: '丸剂',
    POWDER: '散剂',
    1: '饮片',
    2: '代煎',
    3: '膏方',
    4: '丸剂',
    5: '散剂',
  };
  return value ? labels[value] ?? value : '-';
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
  return value ? labels[value] ?? value : '-';
}

function isWithinText(value: number | null | undefined) {
  if (value === 0) return '内服';
  if (value === 1) return '外用';
  return '-';
}

function queryParams(): AdminOrderQueryParams {
  return {
    startTime: startTime.value,
    endTime: endTime.value,
    institution: institution.value,
    keyword: prescriptionNo.value,
    hospitalPrescriptionNo: hospitalPrescriptionNo.value,
    patientName: patientName.value,
    prescriptionType: prescriptionType.value,
    hospitalType: hospitalType.value,
    page: page.value,
    pageSize: pageSize.value,
  };
}

function downloadPrescriptionCsv() {
  downloadCsv(
    `处方修改-第${page.value}页.csv`,
    ['平台处方号', '平台订单号', '机构名称', '机构处方号', '病人信息', '处方类型', '剂数', '门诊住院', '订单时间', '订单状态'],
    rows.value.map((row) => [
      row.prescriptionNos,
      row.orderNo,
      row.institutionName,
      row.externalPrescriptionNos,
      row.patientName,
      prescriptionTypeText(row.prescriptionTypes),
      row.doseCount,
      hospitalTypeText(row.hospitalTypes),
      formatDate(row.createdAt),
      row.orderStatus,
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条处方修改记录`);
}

function fillPrescriptionForm(prescription: AdminOrderDetailPrescription) {
  selectedPrescriptionId.value = prescription.prescriptionId;
  prescriptionForm.value = {
    prescriptionId: prescription.prescriptionId,
    prescriptionType: prescription.prescriptionType ?? '',
    hospitalType: prescription.hospitalType ?? '',
    doseCount: prescription.doseCount,
    decoctionCount: prescription.decoctionCount,
    boilTimes: prescription.boilTimes,
    isWithin: prescription.isWithin,
    perPackNum: prescription.perPackNum,
    perPackDose: prescription.perPackDose,
    medicationMethod: prescription.medicationMethod ?? '',
    medicationInstruction: prescription.medicationInstruction ?? '',
    prescriptionRemark: prescription.prescriptionRemark ?? '',
    operator: prescriptionForm.value.operator || 'admin',
    reason: '',
  };
}

async function refreshPrescriptionOrders() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminOrders(queryParams());
    orderPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('notice', 'success', `已查询到 ${nextPage.total} 条处方修改记录`);
  } catch (error) {
    orderPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshPrescriptionOrders();
}

async function loadOrder(row: AdminOrderListItem) {
  detailLoading.value = true;
  errorLine.value = '';
  try {
    const detail = await getAdminOrderDetail(row.orderNo);
    selectedOrder.value = detail;
    const firstPrescription = detail.prescriptions.find((item) => item.prescriptionStatus !== 'CANCELLED')
      ?? detail.prescriptions[0];
    if (firstPrescription) {
      fillPrescriptionForm(firstPrescription);
    } else {
      selectedPrescriptionId.value = '';
    }
    emit('notice', 'success', `已加载 ${detail.orderNo} 处方信息`);
  } catch (error) {
    selectedOrder.value = null;
    selectedPrescriptionId.value = '';
    errorLine.value = errorMessage(error);
  } finally {
    detailLoading.value = false;
  }
}

function changePrescriptionForm() {
  const prescription = selectedPrescription.value;
  if (prescription) fillPrescriptionForm(prescription);
}

async function submitPrescriptionUpdate() {
  if (!selectedOrder.value) {
    errorLine.value = '请先选择一条订单';
    return;
  }
  const prescription = selectedPrescription.value;
  if (!prescription) {
    errorLine.value = '请选择要修改的处方';
    return;
  }
  const doseCount = formNumber(prescriptionForm.value.doseCount);
  const boilTimes = formNumber(prescriptionForm.value.boilTimes);
  const command: AdminPrescriptionUpdateCommand = {
    prescriptionType: prescriptionForm.value.prescriptionType,
    hospitalType: prescriptionForm.value.hospitalType,
    doseCount,
    decoctionCount: calculatedDecoctionCount.value,
    boilTimes,
    isWithin: formNumber(prescriptionForm.value.isWithin),
    perPackNum: formNumber(prescriptionForm.value.perPackNum),
    perPackDose: formNumber(prescriptionForm.value.perPackDose),
    medicationMethod: prescriptionForm.value.medicationMethod.trim(),
    medicationInstruction: prescriptionForm.value.medicationInstruction.trim(),
    prescriptionRemark: prescriptionForm.value.prescriptionRemark.trim(),
    operator: prescriptionForm.value.operator.trim() || 'admin',
    reason: prescriptionForm.value.reason.trim(),
  };
  if (!command.prescriptionType) {
    errorLine.value = '处方类型不能为空';
    return;
  }
  if (command.prescriptionType === 'DECOCTION' && (!command.boilTimes || command.boilTimes <= 0)) {
    errorLine.value = '代煎处方的几煎必须大于 0';
    return;
  }
  if (command.isWithin !== 0 && command.isWithin !== 1) {
    errorLine.value = '服用方式必须选择内服或外用';
    return;
  }
  if (!isNonNegativeInteger(command.perPackNum ?? null) || (command.perPackNum ?? 0) > 9) {
    errorLine.value = '每剂包数必须为 0-9 的整数';
    return;
  }
  if (!isNonNegativeInteger(command.perPackDose ?? null)) {
    errorLine.value = '每剂剂量必须为非负整数';
    return;
  }
  submitting.value = true;
  errorLine.value = '';
  try {
    await updateAdminPrescription(selectedOrder.value.orderNo, prescription.prescriptionId, command);
    const detail = await getAdminOrderDetail(selectedOrder.value.orderNo);
    selectedOrder.value = detail;
    const nextPrescription = detail.prescriptions.find((item) => item.prescriptionId === prescription.prescriptionId)
      ?? detail.prescriptions[0];
    if (nextPrescription) fillPrescriptionForm(nextPrescription);
    await refreshPrescriptionOrders();
    emit('notice', 'success', `处方 ${prescription.prescriptionNo} 已更新`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshPrescriptionOrders();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshPrescriptionOrders();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) void refreshPrescriptionOrders();
  },
  { immediate: true },
);

defineExpose({
  refreshPrescriptionOrders,
});
</script>

<template>
  <section class="legacy-page order-list-page prescription-modify-page">
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
        平台处方号/订单号：
        <input v-model="prescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        病人姓名：
        <input v-model="patientName" class="legacy-input" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构处方号：
        <input v-model="hospitalPrescriptionNo" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input">
          <option value="">请选择</option>
          <option value="DECOCTION">代煎</option>
          <option value="SELF_DECOCTION">自煎</option>
          <option value="OTHER">其他</option>
          <option value="HERBAL_PIECE">饮片</option>
          <option value="CREAM">膏方</option>
          <option value="PILL">丸剂</option>
          <option value="POWDER">散剂</option>
        </select>
      </li>
      <li>
        门诊住院：
        <select v-model="hospitalType" class="legacy-input">
          <option value="">请选择</option>
          <option value="OUTPATIENT">门诊</option>
          <option value="INPATIENT">住院</option>
          <option value="OTHER">其他</option>
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
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadPrescriptionCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <div class="legacy-panel">
      <table class="legacy-main-table order-main-table prescription-main-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台处方号</th>
            <th>机构名称</th>
            <th>机构处方号</th>
            <th>病人信息</th>
            <th>处方类型</th>
            <th>剂数</th>
            <th>代煎剂数</th>
            <th>门诊住院</th>
            <th>订单时间</th>
            <th>订单状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="legacy-main-info">
            <td colspan="11" class="legacy-empty">正在查询处方</td>
          </tr>
          <tr v-else-if="rows.length === 0" class="legacy-main-info">
            <td colspan="11" class="legacy-empty">暂无处方记录</td>
          </tr>
          <tr
            v-for="row in rows"
            :key="`${row.orderId}-${row.prescriptionNos}`"
            class="legacy-main-info"
            :class="{ active: selectedOrder?.orderNo === row.orderNo }"
          >
            <td>{{ rowValue(row.prescriptionNos) }}</td>
            <td>{{ rowValue(row.institutionName) }}</td>
            <td>{{ rowValue(row.externalPrescriptionNos) }}</td>
            <td>{{ rowValue(row.patientName) }}</td>
            <td>{{ prescriptionTypeText(row.prescriptionTypes) }}</td>
            <td>{{ rowValue(row.doseCount) }}</td>
            <td>-</td>
            <td>{{ hospitalTypeText(row.hospitalTypes) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
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

    <section v-if="selectedOrder" class="legacy-panel prescription-edit-panel">
      <div class="legacy-section-title">
        <h2>处方信息修改：{{ selectedOrder.orderNo }}</h2>
      </div>

      <div class="legacy-detail-grid">
        <span>机构</span>
        <strong>{{ selectedOrder.institutionName }}</strong>
        <span>病人</span>
        <strong>{{ rowValue(selectedOrder.patientName) }} / {{ rowValue(selectedOrder.patientPhone) }}</strong>
        <span>订单状态</span>
        <strong><StatusPill :value="selectedOrder.orderStatus" :tone="statusTone(selectedOrder.orderStatus)" /></strong>
        <span>处方数量</span>
        <strong>{{ prescriptions.length }}</strong>
      </div>

      <table class="legacy-main-table prescription-detail-table">
        <thead>
          <tr class="legacy-main-head">
            <th>平台处方号</th>
            <th>机构处方号</th>
            <th>类型</th>
            <th>剂数</th>
            <th>几煎</th>
            <th>服用方式</th>
            <th>每剂包数</th>
            <th>每剂剂量</th>
            <th>用药方法</th>
            <th>用药指导</th>
            <th>医院备注</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in prescriptions"
            :key="item.prescriptionId"
            class="legacy-main-info"
            :class="{ active: selectedPrescriptionId === item.prescriptionId }"
            @click="fillPrescriptionForm(item)"
          >
            <td>{{ item.prescriptionNo }}</td>
            <td>{{ item.externalPrescriptionNo }}</td>
            <td>{{ prescriptionTypeText(item.prescriptionType) }}</td>
            <td>{{ rowValue(item.doseCount) }}</td>
            <td>{{ rowValue(item.boilTimes) }}</td>
            <td>{{ isWithinText(item.isWithin) }}</td>
            <td>{{ rowValue(item.perPackNum) }}</td>
            <td>{{ rowValue(item.perPackDose) }}</td>
            <td class="legacy-left">{{ rowValue(item.medicationMethod) }}</td>
            <td class="legacy-left">{{ rowValue(item.medicationInstruction) }}</td>
            <td class="legacy-left">{{ rowValue(item.prescriptionRemark) }}</td>
            <td><StatusPill :value="item.prescriptionStatus" :tone="statusTone(item.prescriptionStatus)" /></td>
          </tr>
        </tbody>
      </table>

      <ul class="legacy-search prescription-edit-form">
        <li class="prescription-wide-input">
          处方：
          <select v-model="prescriptionForm.prescriptionId" class="legacy-input input-large" @change="changePrescriptionForm">
            <option
              v-for="item in prescriptions"
              :key="item.prescriptionId"
              :value="item.prescriptionId"
            >
              {{ item.prescriptionNo }} / {{ item.externalPrescriptionNo }} / {{ item.prescriptionStatus }}
            </option>
          </select>
        </li>
        <li>
          处方类型：
          <select v-model="prescriptionForm.prescriptionType" class="legacy-input">
            <option value="">请选择</option>
            <option value="DECOCTION">代煎</option>
            <option value="SELF_DECOCTION">自煎</option>
            <option value="OTHER">其他</option>
            <option value="HERBAL_PIECE">饮片</option>
            <option value="CREAM">膏方</option>
            <option value="PILL">丸剂</option>
            <option value="POWDER">散剂</option>
          </select>
        </li>
        <li>
          门诊住院：
          <select v-model="prescriptionForm.hospitalType" class="legacy-input">
            <option value="">请选择</option>
            <option value="OUTPATIENT">门诊</option>
            <option value="INPATIENT">住院</option>
            <option value="OTHER">其他</option>
          </select>
        </li>
        <li>
          剂数：
          <input v-model.number="prescriptionForm.doseCount" class="legacy-input input-small" type="number" min="0" />
        </li>
        <li>
          几煎：
          <input v-model.number="prescriptionForm.boilTimes" class="legacy-input input-small" type="number" min="0" />
        </li>
        <li>
          代煎剂数：
          <input class="legacy-input input-small" :value="amountValue(calculatedDecoctionCount)" disabled />
        </li>
        <li>
          服用方式：
          <select v-model.number="prescriptionForm.isWithin" class="legacy-input">
            <option :value="null">请选择</option>
            <option :value="0">内服</option>
            <option :value="1">外用</option>
          </select>
        </li>
        <li>
          每剂包数：
          <input v-model.number="prescriptionForm.perPackNum" class="legacy-input input-small" type="number" min="0" />
        </li>
        <li>
          每剂剂量：
          <input v-model.number="prescriptionForm.perPackDose" class="legacy-input input-small" type="number" min="0" />
        </li>
        <li>
          操作人：
          <input v-model="prescriptionForm.operator" class="legacy-input" />
        </li>
        <li class="prescription-wide-input">
          修改原因：
          <input v-model="prescriptionForm.reason" class="legacy-input input-large" placeholder="处方核对/医院要求/异常纠正" />
        </li>
        <li class="prescription-wide-input">
          用药方法：
          <input v-model="prescriptionForm.medicationMethod" class="legacy-input input-large" />
        </li>
        <li class="prescription-wide-input">
          用药指导：
          <input v-model="prescriptionForm.medicationInstruction" class="legacy-input input-large" />
        </li>
        <li class="prescription-wide-input">
          医院备注：
          <input v-model="prescriptionForm.prescriptionRemark" class="legacy-input input-large" />
        </li>
        <li>
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="submitting" @click="submitPrescriptionUpdate">
            {{ submitting ? '保存中' : '保存处方' }}
          </button>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.prescription-edit-panel {
  margin-top: 14px;
}

.prescription-detail-table {
  margin-top: 12px;
}

.prescription-edit-form {
  margin-top: 12px;
}

.prescription-wide-input {
  min-width: 360px;
}

@media (max-width: 780px) {
  .prescription-wide-input {
    min-width: 100%;
  }
}
</style>
