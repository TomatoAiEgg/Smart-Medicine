<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { cancelAdminOrderMerge, createAdminOrderMerge, listAdminOrderMerges } from '../../api/order';
import type { AdminOrderMergeCommand, AdminOrderMergePage, AdminOrderMergeRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

interface MergeForm {
  orderNosText: string;
  logisticsCompany: string;
  logisticsNo: string;
  remark: string;
}

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const keyword = ref('');
const status = ref('');
const page = ref(1);
const pageSize = ref(20);
const mergePage = ref<AdminOrderMergePage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref<MergeForm>({
  orderNosText: '',
  logisticsCompany: '',
  logisticsNo: '',
  remark: '',
});

const rows = computed(() => mergePage.value?.records ?? []);
const total = computed(() => mergePage.value?.total ?? 0);
const activeCount = computed(() => rows.value.filter((row) => row.status === 'ACTIVE').length);
const cancelledCount = computed(() => rows.value.filter((row) => row.status === 'CANCELLED').length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    ACTIVE: '有效',
    CANCELLED: '已取消',
  };
  return labels[value] ?? value;
}

function orderNoList(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item !== '');
}

function parseOrderNos() {
  return Array.from(
    new Set(
      form.value.orderNosText
        .split(/[\n,，;；\s]+/)
        .map((item) => item.trim())
        .filter((item) => item !== ''),
    ),
  );
}

function commandFromForm(): AdminOrderMergeCommand {
  return {
    orderNos: parseOrderNos(),
    logisticsCompany: form.value.logisticsCompany.trim(),
    logisticsNo: form.value.logisticsNo.trim(),
    remark: form.value.remark.trim(),
  };
}

function downloadMergeCsv() {
  downloadCsv(
    `合单记录-第${page.value}页.csv`,
    ['合单号', '订单数量', '订单号', '机构', '物流公司', '运单号', '状态', '备注', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.mergeNo,
      row.orderCount,
      row.orderNos,
      row.institutionNames,
      row.logisticsCompany,
      row.logisticsNo,
      statusLabel(row.status),
      row.remark,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 条合单记录`);
}

async function refreshOrderMerges() {
  loading.value = true;
  errorLine.value = '';
  try {
    const nextPage = await listAdminOrderMerges({
      keyword: keyword.value,
      status: status.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    mergePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'success', `已查询 ${formatNumber(nextPage.total)} 条合单记录`);
  } catch (error) {
    mergePage.value = null;
    loaded.value = false;
    errorLine.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshOrderMerges();
}

function resetForm() {
  form.value = {
    orderNosText: '',
    logisticsCompany: '',
    logisticsNo: '',
    remark: '',
  };
}

async function createMerge() {
  const orderNos = parseOrderNos();
  if (orderNos.length < 2) {
    errorLine.value = '至少输入两个订单号';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    const created = await createAdminOrderMerge(commandFromForm());
    emit('notice', 'success', `合单 ${created.mergeNo} 已创建`);
    resetForm();
    await refreshOrderMerges();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function cancelMerge(row: AdminOrderMergeRecord) {
  if (row.status === 'CANCELLED') return;
  saving.value = true;
  errorLine.value = '';
  try {
    await cancelAdminOrderMerge(row.id, { remark: row.remark ?? '' });
    emit('notice', 'success', `合单 ${row.mergeNo} 已取消`);
    await refreshOrderMerges();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshOrderMerges();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshOrderMerges();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshOrderMerges();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshOrderMerges,
});
</script>

<template>
  <section class="legacy-page order-merge-page">
    <ul class="legacy-search order-merge-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="合单号 / 订单号 / 物流单 / 机构"
          @keyup.enter="searchFirstPage"
        />
      </li>
      <li>
        状态：
        <select v-model="status" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="ACTIVE">有效</option>
          <option value="CANCELLED">已取消</option>
        </select>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          {{ loading ? '查询中' : '查询' }}
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadMergeCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <p v-if="errorLine" class="error-line">{{ errorLine }}</p>

    <ul class="legacy-stats order-merge-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>合单总数</span>
      </li>
      <li>
        <strong>{{ formatNumber(activeCount) }}</strong>
        <span>本页有效</span>
      </li>
      <li>
        <strong>{{ formatNumber(cancelledCount) }}</strong>
        <span>本页取消</span>
      </li>
    </ul>

    <div class="order-merge-edit legacy-panel">
      <div class="order-merge-form-grid">
        <label class="order-nos-field">
          订单号
          <textarea
            v-model="form.orderNosText"
            class="legacy-input order-nos-textarea"
            placeholder="每行一个订单号，或用逗号/空格分隔"
          />
        </label>
        <label>
          物流公司
          <input v-model="form.logisticsCompany" class="legacy-input" placeholder="SF / EMS" />
        </label>
        <label>
          运单号
          <input v-model="form.logisticsNo" class="legacy-input" placeholder="可留空" />
        </label>
        <label>
          备注
          <input v-model="form.remark" class="legacy-input" placeholder="合单说明" />
        </label>
      </div>
      <div class="order-merge-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="createMerge">
          {{ saving ? '保存中' : '新增合单' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table order-merge-table">
        <thead>
          <tr>
            <th>合单号</th>
            <th>订单</th>
            <th>机构</th>
            <th>物流</th>
            <th>状态</th>
            <th>备注</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8">正在加载合单记录...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="8">暂无合单记录</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>
              <strong>{{ row.mergeNo }}</strong>
              <small>{{ formatNumber(row.orderCount) }} 单</small>
            </td>
            <td>
              <span v-for="orderNo in orderNoList(row.orderNos)" :key="orderNo" class="order-no-chip">
                {{ orderNo }}
              </span>
            </td>
            <td>{{ displayValue(row.institutionNames) }}</td>
            <td>
              <strong>{{ displayValue(row.logisticsCompany) }}</strong>
              <small>{{ displayValue(row.logisticsNo) }}</small>
            </td>
            <td>
              <span class="legacy-status" :class="row.status === 'ACTIVE' ? 'status-success' : 'status-muted'">
                {{ statusLabel(row.status) }}
              </span>
            </td>
            <td>{{ displayValue(row.remark) }}</td>
            <td>{{ formatDate(row.createdAt) }}</td>
            <td>
              <button
                class="legacy-link"
                type="button"
                :disabled="saving || row.status === 'CANCELLED'"
                @click="cancelMerge(row)"
              >
                取消合单
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <span>第 {{ formatNumber(page) }} 页 / 共 {{ formatNumber(total) }} 条</span>
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="previousPage">上一页</button>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="nextPage">下一页</button>
    </div>
  </section>
</template>

<style scoped>
.order-merge-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-merge-search,
.order-merge-stats {
  margin: 0;
}

.order-merge-edit {
  padding: 14px;
}

.order-merge-form-grid {
  display: grid;
  grid-template-columns: 2fr repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.order-merge-form-grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.order-nos-field {
  grid-row: span 2;
}

.order-nos-textarea {
  min-height: 96px;
  resize: vertical;
}

.order-merge-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.order-merge-table td {
  vertical-align: top;
}

.order-merge-table strong,
.order-merge-table small {
  display: block;
}

.order-merge-table small {
  margin-top: 3px;
  color: #6b7280;
}

.order-no-chip {
  display: inline-block;
  margin: 0 5px 5px 0;
  padding: 2px 6px;
  border: 1px solid #d1d5db;
  background: #f9fafb;
  color: #374151;
}

@media (max-width: 1100px) {
  .order-merge-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .order-merge-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
