<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getOpsHealthOverview } from '../../api/ops';
import type { OpsHealthOverview } from '../../api/types';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import { downloadCsv } from '../../domain/csv';
import { errorMessage } from '../../domain/errors';
import { formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

interface DashboardMetric {
  label: string;
  value: string;
  hint?: string;
}

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const props = defineProps<{
  canViewHealth: boolean;
}>();

const healthLoading = ref(false);
const healthError = ref('');
const health = ref<OpsHealthOverview | null>(null);

const metrics = computed<DashboardMetric[]>(() => {
  const current = health.value;
  if (!current) return [];
  return [
    {
      label: 'Outbox 待发',
      value: formatNumber(current.pendingOutbox),
    },
    {
      label: 'Outbox 失败',
      value: formatNumber(current.failedOutbox),
    },
    {
      label: '消费失败',
      value: formatNumber(current.failedConsumes),
    },
    {
      label: '订单校验拒绝',
      value: formatNumber(current.rejectedValidations),
    },
    {
      label: '回调失败/死信',
      value: `${formatNumber(current.failedCallbacks)} / ${formatNumber(current.deadCallbacks)}`,
    },
    {
      label: '集成失败/死信',
      value: `${formatNumber(current.failedIntegrationRetries)} / ${formatNumber(current.deadIntegrationRetries)}`,
    },
    {
      label: '最近访问量',
      value: formatNumber(current.recentAccessCount),
      hint: `${current.recentHours} 小时`,
    },
  ];
});

async function refreshDashboard() {
  if (!props.canViewHealth) return;
  if (healthLoading.value) return;
  healthLoading.value = true;
  healthError.value = '';
  try {
    health.value = await getOpsHealthOverview({ recentHours: 24 });
    emit('notice', 'info', '已刷新工作台健康概览');
  } catch (error) {
    health.value = null;
    healthError.value = errorMessage(error);
  } finally {
    healthLoading.value = false;
  }
}

function downloadHealthCsv() {
  const current = health.value;
  if (!current) return;
  downloadCsv(
    `工作台健康概览-${current.recentHours}小时.csv`,
    ['窗口小时', 'Outbox待发', 'Outbox失败', '消费失败', '订单校验拒绝', '回调失败', '回调死信', '集成失败', '集成死信', '最近访问量'],
    [[
      current.recentHours,
      current.pendingOutbox,
      current.failedOutbox,
      current.failedConsumes,
      current.rejectedValidations,
      current.failedCallbacks,
      current.deadCallbacks,
      current.failedIntegrationRetries,
      current.deadIntegrationRetries,
      current.recentAccessCount,
    ]],
  );
  emit('notice', 'success', '工作台健康概览已导出');
}

onMounted(() => {
  if (props.canViewHealth) void refreshDashboard();
});

defineExpose({
  refreshDashboard,
});
</script>

<template>
  <section class="dashboard-home-page">
    <AdminToolbar v-if="canViewHealth">
      <div class="dashboard-toolbar-copy">
        <span class="dashboard-toolbar-copy__label">监控窗口</span>
        <strong>{{ health?.recentHours ?? 24 }} 小时</strong>
      </div>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="healthLoading"
          @click="refreshDashboard"
        >
          {{ healthLoading ? '刷新中' : '刷新' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="healthLoading || !health"
          @click="downloadHealthCsv"
        >
          导出概览
        </t-button>
      </template>
    </AdminToolbar>

    <AdminPageState
      v-if="!canViewHealth"
      state="forbidden"
      message="当前账号无运维概览权限，请从左侧菜单进入已授权功能。"
    />
    <AdminPageState
      v-else-if="healthError"
      state="error"
      :message="healthError"
    />
    <AdminPageState
      v-else-if="healthLoading && !health"
      state="loading"
      message="正在加载工作台健康概览。"
    />
    <div v-else-if="health" class="dashboard-metric-grid">
      <article
        v-for="metric in metrics"
        :key="metric.label"
        class="dashboard-metric"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small v-if="metric.hint">{{ metric.hint }}</small>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-home-page {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.dashboard-toolbar-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.dashboard-toolbar-copy__label {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.dashboard-toolbar-copy strong {
  color: #1f2937;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.dashboard-metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  min-width: 0;
}

.dashboard-metric {
  display: grid;
  gap: 6px;
  min-height: 112px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.dashboard-metric span,
.dashboard-metric small {
  color: #667085;
}

.dashboard-metric span {
  font-size: 13px;
  line-height: 20px;
}

.dashboard-metric strong {
  color: #111827;
  font-size: 24px;
  font-weight: 700;
  line-height: 30px;
  font-variant-numeric: tabular-nums;
}

.dashboard-metric small {
  font-size: 12px;
  line-height: 18px;
}
</style>
