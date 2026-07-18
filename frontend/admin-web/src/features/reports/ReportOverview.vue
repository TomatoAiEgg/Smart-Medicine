<script setup lang="ts">
import { ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { downloadReportOverviewCsv, getReportOverview } from '../../api/report';
import type { ReportOverview } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { dateInputToIso, defaultDate, formatDate, formatNumber } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const reportFrom = ref(defaultDate(-13));
const reportTo = ref(defaultDate(0));
const reportTrendDays = ref(14);
const reportLoading = ref(false);
const reportExporting = ref(false);
const reportError = ref('');
const reportOverview = ref<ReportOverview | null>(null);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function normalizedReportTrendDays() {
  if (!Number.isFinite(reportTrendDays.value) || reportTrendDays.value <= 0) return 14;
  return Math.min(Math.trunc(reportTrendDays.value), 60);
}

async function refreshReports() {
  if (reportLoading.value) return;
  reportLoading.value = true;
  reportError.value = '';
  const trendDays = normalizedReportTrendDays();
  reportTrendDays.value = trendDays;
  try {
    reportOverview.value = await getReportOverview({
      from: dateInputToIso(reportFrom.value),
      to: dateInputToIso(reportTo.value, true),
      trendDays,
    });
    emit('countChanged', reportOverview.value.totalOrders);
    emit('notice', 'info', `已刷新报表统计：订单 ${formatNumber(reportOverview.value.totalOrders)} 单`);
  } catch (error) {
    reportOverview.value = null;
    reportError.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    reportLoading.value = false;
  }
}

async function exportReports() {
  reportExporting.value = true;
  reportError.value = '';
  const trendDays = normalizedReportTrendDays();
  reportTrendDays.value = trendDays;
  try {
    const blob = await downloadReportOverviewCsv({
      from: dateInputToIso(reportFrom.value),
      to: dateInputToIso(reportTo.value, true),
      trendDays,
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `report-overview-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    emit('notice', 'success', '报表 CSV 已导出');
  } catch (error) {
    reportError.value = errorMessage(error);
  } finally {
    reportExporting.value = false;
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !reportOverview.value) {
      void refreshReports();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshReports,
});
</script>

<template>
  <section class="workspace">
    <div class="toolbar">
      <label>
        <span>开始日期</span>
        <input v-model="reportFrom" type="date" @keyup.enter="refreshReports" />
      </label>
      <label>
        <span>结束日期</span>
        <input v-model="reportTo" type="date" @keyup.enter="refreshReports" />
      </label>
      <label>
        <span>趋势天数</span>
        <input v-model.number="reportTrendDays" type="number" min="1" max="60" step="1" @keyup.enter="refreshReports" />
      </label>
      <button class="primary" type="button" :disabled="reportLoading" @click="refreshReports">
        {{ reportLoading ? '刷新中' : '刷新' }}
      </button>
      <button type="button" :disabled="reportExporting" @click="exportReports">
        {{ reportExporting ? '导出中' : '导出 CSV' }}
      </button>
    </div>

    <p v-if="reportError" class="error-line">{{ reportError }}</p>

    <div v-if="reportOverview" class="detail-grid report-metrics">
      <div>
        <span>订单总数</span>
        <strong>{{ formatNumber(reportOverview.totalOrders) }}</strong>
        <small>{{ reportOverview.from ? formatDate(reportOverview.from) : '-' }} / {{ reportOverview.to ? formatDate(reportOverview.to) : '-' }}</small>
      </div>
      <div>
        <span>处方总数</span>
        <strong>{{ formatNumber(reportOverview.totalPrescriptions) }}</strong>
      </div>
      <div>
        <span>物流单</span>
        <strong>{{ formatNumber(reportOverview.totalShipments) }}</strong>
      </div>
      <div>
        <span>回调记录</span>
        <strong>{{ formatNumber(reportOverview.totalCallbacks) }}</strong>
      </div>
      <div>
        <span>待处理地址补录</span>
        <strong>{{ formatNumber(reportOverview.pendingAddressSupplements) }}</strong>
      </div>
      <div>
        <span>趋势窗口</span>
        <strong>{{ reportOverview.trendDays }} 天</strong>
      </div>
    </div>

    <div v-if="reportOverview" class="table-wrap report-table">
      <table>
        <thead>
          <tr>
            <th>订单状态</th>
            <th>数量</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="reportOverview.orderStatusCounts.length === 0">
            <td colspan="2" class="empty">暂无订单状态统计</td>
          </tr>
          <tr v-for="item in reportOverview.orderStatusCounts" :key="item.status">
            <td><StatusPill :value="item.status" :tone="statusTone(item.status)" /></td>
            <td><strong>{{ formatNumber(item.count) }}</strong></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="reportOverview" class="table-wrap report-table">
      <table>
        <thead>
          <tr>
            <th>回调状态</th>
            <th>数量</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="reportOverview.callbackStatusCounts.length === 0">
            <td colspan="2" class="empty">暂无回调状态统计</td>
          </tr>
          <tr v-for="item in reportOverview.callbackStatusCounts" :key="item.status">
            <td><StatusPill :value="item.status" :tone="statusTone(item.status)" /></td>
            <td><strong>{{ formatNumber(item.count) }}</strong></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="reportOverview" class="table-wrap report-table">
      <table>
        <thead>
          <tr>
            <th>日期</th>
            <th>新增订单</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="reportOverview.dailyOrderCounts.length === 0">
            <td colspan="2" class="empty">趋势窗口内暂无订单</td>
          </tr>
          <tr v-for="item in reportOverview.dailyOrderCounts" :key="item.day">
            <td><strong>{{ item.day }}</strong></td>
            <td><strong>{{ formatNumber(item.count) }}</strong></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
