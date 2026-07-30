<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { errorMessage } from '../../domain/errors';
import { getOpsHealthOverview } from '../../api/ops';
import type { OpsHealthOverview } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const healthLoading = ref(false);
const healthError = ref('');
const health = ref<OpsHealthOverview | null>(null);

async function refreshDashboard() {
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
  void refreshDashboard();
});

defineExpose({
  refreshDashboard,
});
</script>

<template>
  <section class="legacy-page dashboard-home-page">
    <div class="legacy-search-panel dashboard-action-panel">
      <button class="primary" type="button" :disabled="healthLoading" @click="refreshDashboard">
        {{ healthLoading ? '刷新中' : '刷新' }}
      </button>
      <button class="secondary" type="button" :disabled="healthLoading || !health" @click="downloadHealthCsv">
        导出概览
      </button>
    </div>

    <p v-if="healthError" class="error-line">{{ healthError }}</p>

    <div v-if="health" class="legacy-detail-grid dashboard-health-summary">
      <div>
        <span>Outbox 待发</span>
        <strong>{{ formatNumber(health.pendingOutbox) }}</strong>
      </div>
      <div>
        <span>Outbox 失败</span>
        <strong>{{ formatNumber(health.failedOutbox) }}</strong>
      </div>
      <div>
        <span>消费失败</span>
        <strong>{{ formatNumber(health.failedConsumes) }}</strong>
      </div>
      <div>
        <span>订单校验拒绝</span>
        <strong>{{ formatNumber(health.rejectedValidations) }}</strong>
      </div>
      <div>
        <span>回调失败/死信</span>
        <strong>{{ formatNumber(health.failedCallbacks) }} / {{ formatNumber(health.deadCallbacks) }}</strong>
      </div>
      <div>
        <span>集成失败/死信</span>
        <strong>{{ formatNumber(health.failedIntegrationRetries) }} / {{ formatNumber(health.deadIntegrationRetries) }}</strong>
      </div>
      <div>
        <span>最近访问量</span>
        <strong>{{ formatNumber(health.recentAccessCount) }}</strong>
        <small>{{ health.recentHours }} 小时</small>
      </div>
    </div>
  </section>
</template>
