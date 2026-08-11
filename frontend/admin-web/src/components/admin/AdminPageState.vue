<script setup lang="ts">
type AdminPageStateType = 'loading' | 'empty' | 'error' | 'forbidden';

defineProps<{
  state: AdminPageStateType;
  message: string;
}>();
</script>

<template>
  <div class="admin-page-state" :class="`admin-page-state--${state}`">
    <t-alert
      v-if="state === 'error'"
      class="admin-page-state__error"
      theme="error"
      :message="message"
    />
    <p v-else role="status">{{ message }}</p>
  </div>
</template>

<style scoped>
.admin-page-state {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 144px;
  padding: 24px;
  border: 1px solid var(--admin-line, #e3e8f0);
  border-radius: 6px;
  color: var(--admin-muted, #667085);
  background: #ffffff;
}

.admin-page-state p {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
}

.admin-page-state--loading {
  border-left-color: var(--admin-primary, #0052d9);
  color: var(--admin-primary, #0052d9);
}

.admin-page-state--error {
  align-items: flex-start;
}

.admin-page-state--forbidden {
  color: var(--admin-text, #1f2937);
}

.admin-page-state__error {
  width: 100%;
}
</style>
