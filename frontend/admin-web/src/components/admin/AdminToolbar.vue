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
  gap: 10px 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--admin-line, #e3e8f0);
  border-radius: 6px;
  background: #ffffff;
}

.admin-toolbar__filters {
  display: flex;
  flex: 1 1 360px;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px 12px;
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

@media (max-width: 720px) {
  .admin-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-toolbar__filters,
  .admin-toolbar__actions {
    width: 100%;
  }

  .admin-toolbar__actions {
    justify-content: flex-start;
    margin-left: 0;
  }
}
</style>
