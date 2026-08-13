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
  border: 1px solid var(--admin-border, #dcdcdc);
  border-top: 0;
  border-radius: 0 0 3px 3px;
  background: var(--admin-surface, #ffffff);
  -webkit-overflow-scrolling: touch;
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
  box-sizing: border-box;
  height: 40px;
  border-bottom: 1px solid var(--admin-border, #dcdcdc);
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
  height: 40px;
  padding: 0 10px;
  color: var(--admin-text-secondary, #5e5e5e);
  background: var(--admin-surface-subtle, #f2f3f5);
  font-weight: 600;
}

.admin-table-shell :deep(td) {
  padding: 8px 10px;
}

.admin-table-shell :deep(tbody tr:hover > td) {
  background: var(--admin-surface-subtle, #f2f3f5);
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
