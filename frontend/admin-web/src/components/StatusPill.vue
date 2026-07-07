<script setup lang="ts">
defineProps<{
  value: string | null | undefined;
  tone?: 'neutral' | 'success' | 'warning' | 'danger';
}>();
</script>

<template>
  <span class="status-pill" :class="tone || 'neutral'">{{ value || '-' }}</span>
</template>
