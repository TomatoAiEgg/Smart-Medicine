<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  value: string | null | undefined;
  tone?: 'neutral' | 'success' | 'warning' | 'danger';
}>();

const tagTheme = computed(() => {
  if (props.tone === 'success') return 'success';
  if (props.tone === 'warning') return 'warning';
  if (props.tone === 'danger') return 'danger';
  return 'default';
});
</script>

<template>
  <t-tag class="status-pill" :theme="tagTheme" variant="light" size="small">
    {{ value || '-' }}
  </t-tag>
</template>
