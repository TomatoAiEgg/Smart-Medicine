<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ApiError } from '../../api/client';
import { getOpsHealthOverview } from '../../api/ops';
import type { OpsHealthOverview } from '../../api/types';
import { formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const healthLoading = ref(false);
const healthError = ref('');
const health = ref<OpsHealthOverview | null>(null);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

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

onMounted(() => {
  void refreshDashboard();
});

defineExpose({
  refreshDashboard,
});
</script>

<template>
  <section class="workspace">
    <div class="toolbar">
      <button class="primary" type="button" :disabled="healthLoading" @click="refreshDashboard">
        {{ healthLoading ? '刷新中' : '刷新' }}
      </button>
    </div>

    <p v-if="healthError" class="error-line">{{ healthError }}</p>

    <div v-if="health" class="detail-grid">
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
