<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  enabled: boolean;
}>();

const theme = computed(() => (props.enabled ? 'success' : 'default'));
</script>

<template>
  <t-tag class="admin-status-tag" :theme variant="light" size="small">
    {{ enabled ? '启用' : '停用' }}
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
