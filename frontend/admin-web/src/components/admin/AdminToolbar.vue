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
  align-items: flex-end;
  gap: 12px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--admin-line, #e3e8f0);
  border-radius: 6px;
  background: var(--admin-surface, #ffffff);
}

.admin-toolbar__filters {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px 12px;
  min-width: 0;
}

.admin-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 720px) {
  .admin-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-toolbar__actions {
    align-self: stretch;
  }
}
</style>
