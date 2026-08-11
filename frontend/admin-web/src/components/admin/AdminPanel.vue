<script setup lang="ts">
defineProps<{
  title?: string;
  description?: string;
}>();
</script>

<template>
  <section class="admin-panel">
    <header v-if="title || description || $slots.actions" class="admin-panel__header">
      <div class="admin-panel__heading">
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="admin-panel__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="admin-panel__content">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.admin-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.admin-panel__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 0 0 10px;
  border-bottom: 1px solid var(--admin-line, #e3e8f0);
}

.admin-panel__heading {
  min-width: 0;
}

.admin-panel__heading h2,
.admin-panel__heading p {
  margin: 0;
  letter-spacing: 0;
}

.admin-panel__heading h2 {
  color: var(--admin-text, #1f2937);
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
}

.admin-panel__heading p {
  margin-top: 3px;
  color: var(--admin-muted, #667085);
  font-size: 13px;
  line-height: 20px;
}

.admin-panel__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.admin-panel__content {
  min-width: 0;
}

@media (max-width: 640px) {
  .admin-panel__header {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .admin-panel__actions {
    justify-content: flex-start;
  }
}
</style>
