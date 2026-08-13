<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  page: number;
  pageSize: number;
  total: number;
  loading?: boolean;
}

interface Emits {
  previous: [];
  next: [];
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<Emits>();

const totalPages = computed(() => {
  if (props.total <= 0 || props.pageSize <= 0) return 0;
  return Math.ceil(props.total / props.pageSize);
});

const displayedTotalPages = computed(() => Math.max(totalPages.value, 1));

const hasValidPagination = computed(
  () => props.total > 0 && props.pageSize > 0 && totalPages.value > 0,
);

const previousDisabled = computed(
  () => props.loading || !hasValidPagination.value || props.page <= 1,
);

const nextDisabled = computed(
  () =>
    props.loading ||
    !hasValidPagination.value ||
    props.page >= totalPages.value,
);

function handlePrevious() {
  if (previousDisabled.value) return;
  emit('previous');
}

function handleNext() {
  if (nextDisabled.value) return;
  emit('next');
}
</script>

<template>
  <nav class="admin-pagination" aria-label="分页">
    <span class="admin-pagination__summary">
      共 {{ total }} 条，第 {{ page }} / {{ displayedTotalPages }} 页
    </span>
    <div class="admin-pagination__actions">
      <t-button
        theme="default"
        variant="outline"
        size="small"
        :disabled="previousDisabled"
        @click="handlePrevious"
      >
        上一页
      </t-button>
      <t-button
        theme="default"
        variant="outline"
        size="small"
        :disabled="nextDisabled"
        @click="handleNext"
      >
        下一页
      </t-button>
    </div>
  </nav>
</template>

<style scoped>
.admin-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  padding-top: 10px;
}

.admin-pagination__summary {
  color: var(--admin-text-secondary, #5e5e5e);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
}

.admin-pagination__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.admin-pagination__actions :deep(.t-button) {
  min-width: 72px;
  height: 32px;
}

@media (max-width: 639px) {
  .admin-pagination {
    gap: 8px;
  }

  .admin-pagination__actions {
    margin-left: auto;
  }
}

@media (max-width: 420px) {
  .admin-pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-pagination__actions {
    justify-content: flex-end;
  }
}
</style>
