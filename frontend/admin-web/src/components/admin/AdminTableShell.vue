<template>
  <div class="admin-table-shell">
    <slot />
  </div>
</template>

<style scoped>
.admin-table-shell {
  min-width: 0;
  overflow-x: auto;
  border: 1px solid var(--admin-line, #e3e8f0);
  border-radius: 6px;
  background: var(--admin-surface, #ffffff);
  -webkit-overflow-scrolling: touch;
}

.admin-table-shell :deep(table) {
  width: 100%;
  margin: 0;
  border: 0;
  border-collapse: collapse;
}

.admin-table-shell :deep(th),
.admin-table-shell :deep(td) {
  padding: 8px 12px;
  border-bottom: 1px solid var(--admin-line-soft, #edf1f6);
  font-size: 13px;
  line-height: 20px;
  text-align: left;
  vertical-align: middle;
}

.admin-table-shell :deep(th) {
  color: var(--admin-muted, #667085);
  background: #f7f7f8;
  font-weight: 650;
}

.admin-table-shell :deep(tbody tr:last-child td) {
  border-bottom: 0;
}
</style>
