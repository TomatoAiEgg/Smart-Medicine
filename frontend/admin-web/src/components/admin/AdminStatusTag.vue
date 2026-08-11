<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  enabled: boolean;
}>();

const theme = computed(() => (props.enabled ? 'success' : 'default'));
const label = computed(() => (props.enabled ? '启用' : '停用'));
</script>

<template>
  <t-tag class="admin-status-tag" :theme="theme" variant="light" size="small">
    {{ label }}
  </t-tag>
</template>

<style scoped>
.admin-status-tag {
  min-height: 22px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 650;
}
</style>
