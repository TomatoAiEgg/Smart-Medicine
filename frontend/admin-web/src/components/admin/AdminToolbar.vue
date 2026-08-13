<script setup lang="ts">
defineSlots<{
  default?: () => unknown;
  actions?: () => unknown;
}>();
</script>

<template>
  <div class="admin-toolbar">
    <div class="admin-toolbar__filters">
      <slot />
    </div>
    <div v-if="$slots.actions" class="admin-toolbar__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.admin-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 8px 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--admin-border, #dcdcdc);
  border-radius: 3px 3px 0 0;
  background: var(--admin-surface-subtle, #f2f3f5);
}

.admin-toolbar__filters {
  display: flex;
  flex: 1 1 360px;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 8px 12px;
  min-width: 0;
}

.admin-toolbar__actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

@media (max-width: 1023px) {
  .admin-toolbar__actions {
    flex-basis: 100%;
    justify-content: flex-end;
    margin-left: 0;
  }
}

@media (max-width: 639px) {
  .admin-toolbar {
    align-items: stretch;
    flex-direction: column;
    flex-wrap: nowrap;
  }

  .admin-toolbar__filters,
  .admin-toolbar__actions {
    width: 100%;
  }

  .admin-toolbar__filters {
    flex: 0 0 auto;
    align-items: stretch;
    flex-direction: column;
  }

  .admin-toolbar > .admin-toolbar__filters > :deep(*) {
    flex: none;
    width: 100%;
    max-width: none;
  }

  .admin-toolbar__actions {
    flex: 0 0 auto;
    justify-content: flex-start;
    margin-left: 0;
  }
}
</style>
