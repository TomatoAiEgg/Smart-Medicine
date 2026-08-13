<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  enabled: boolean;
}>();

const theme = computed(() => (props.enabled ? 'success' : 'default'));
const label = computed(() => (props.enabled ? '启用' : '停用'));
const toneClass = computed(() =>
  props.enabled ? 'admin-status-tag--enabled' : 'admin-status-tag--disabled',
);
</script>

<template>
  <t-tag
    class="admin-status-tag"
    :class="toneClass"
    :theme="theme"
    variant="light"
    size="small"
  >
    {{ label }}
  </t-tag>
</template>

<style scoped>
.admin-status-tag {
  min-height: 24px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 600;
  line-height: 22px;
}

.admin-status-tag--enabled {
  border-color: #ccebd8;
  color: var(--admin-green, #157347);
  background: var(--admin-green-soft, #eaf8f0);
}

.admin-status-tag--disabled {
  border-color: #e1e5ea;
  color: #5d6878;
  background: #f1f3f5;
}
</style>
