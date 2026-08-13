<script setup lang="ts">
defineSlots<{
  title?: () => unknown;
  description?: () => unknown;
  actions?: () => unknown;
  default?: () => unknown;
}>();
</script>

<template>
  <section class="admin-panel">
    <header
      v-if="$slots.title || $slots.description || $slots.actions"
      class="admin-panel__header"
    >
      <div class="admin-panel__heading">
        <h2 v-if="$slots.title">
          <slot name="title" />
        </h2>
        <p v-if="$slots.description">
          <slot name="description" />
        </p>
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
  border: 0;
  background: transparent;
}

.admin-panel__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  min-height: 40px;
  min-width: 0;
  padding: 6px 0 8px;
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
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.admin-panel__heading p {
  margin-top: 2px;
  color: var(--admin-muted, #667085);
  font-size: 12px;
  line-height: 18px;
}

.admin-panel__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.admin-panel__content {
  min-width: 0;
}

@media (max-width: 640px) {
  .admin-panel__header {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-panel__actions {
    justify-content: flex-start;
  }
}
</style>
