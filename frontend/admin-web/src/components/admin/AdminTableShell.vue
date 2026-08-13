<script setup lang="ts">
defineSlots<{
  default?: () => unknown;
}>();
</script>

<template>
  <div class="admin-table-shell">
    <slot />
  </div>
</template>

<style scoped>
.admin-table-shell {
  min-width: 0;
  overflow: auto;
  border: 1px solid var(--admin-line, #e3e8f0);
  border-radius: 0 0 3px 3px;
  background: #ffffff;
  -webkit-overflow-scrolling: touch;
}

:global(.admin-toolbar + .admin-table-shell) {
  border-top: 0;
}

.admin-table-shell :deep(table) {
  width: 100%;
  min-width: 100%;
  margin: 0;
  border: 0;
  border-collapse: collapse;
}

.admin-table-shell :deep(th),
.admin-table-shell :deep(td) {
  height: 40px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--admin-line-soft, #edf1f6);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  text-align: left;
  vertical-align: middle;
}

.admin-table-shell :deep(th) {
  position: sticky;
  z-index: 1;
  top: 0;
  color: var(--admin-muted, #667085);
  background: var(--admin-surface-soft, #f6f8fa);
  font-weight: 600;
}

.admin-table-shell :deep(tbody tr:hover > td) {
  background: #f8fafc;
}

.admin-table-shell :deep([data-align='number']) {
  text-align: right;
}

.admin-table-shell :deep([data-align='status']) {
  text-align: center;
}

.admin-table-shell :deep(tbody tr:last-child td) {
  border-bottom: 0;
}
</style>
