<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { menuItems, standaloneRouteItems, type AppRouteItem } from '../../app/views';
import { formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type StatusFilter = 'all' | 'implemented' | 'pending';
type CsvExportValue = string | number | boolean | null | undefined;

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const keyword = ref('');
const groupFilter = ref('');
const statusFilter = ref<StatusFilter>('all');
const priorityFilter = ref('');
const loaded = ref(false);

const allRoutes = computed<readonly AppRouteItem[]>(() => [
  ...standaloneRouteItems,
  ...menuItems,
]);
const groups = computed(() => Array.from(new Set(allRoutes.value.map((item) => item.group))).sort());
const priorities = computed(() => Array.from(new Set(allRoutes.value.map((item) => item.priority))).sort());
const implementedCount = computed(() => allRoutes.value.filter((item) => item.implemented).length);
const pendingCount = computed(() => allRoutes.value.length - implementedCount.value);
const groupedRows = computed(() => {
  const term = keyword.value.trim().toLowerCase();
  const filtered = allRoutes.value.filter((item) => {
    const matchesKeyword = term
      ? `${item.label} ${item.group} ${item.path} ${item.legacyRoute ?? ''} ${item.plannedComponent}`
          .toLowerCase()
          .includes(term)
      : true;
    const matchesGroup = groupFilter.value ? item.group === groupFilter.value : true;
    const matchesPriority = priorityFilter.value ? item.priority === priorityFilter.value : true;
    const matchesStatus =
      statusFilter.value === 'all'
        ? true
        : statusFilter.value === 'implemented'
          ? item.implemented
          : !item.implemented;
    return matchesKeyword && matchesGroup && matchesPriority && matchesStatus;
  });

  return groups.value
    .map((group) => ({
      group,
      rows: filtered.filter((item) => item.group === group),
    }))
    .filter((entry) => entry.rows.length > 0);
});
const filteredCount = computed(() => groupedRows.value.reduce((count, entry) => count + entry.rows.length, 0));
const exportRows = computed(() => groupedRows.value.flatMap((entry) => entry.rows));

function rowValue(value: string | null | undefined) {
  if (!value) return '-';
  return value;
}

function escapeCsvCell(value: CsvExportValue) {
  const text = value === null || value === undefined ? '' : String(value);
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

function downloadMenuCsv() {
  const headers = ['菜单', '分组', '路径', '旧系统路由', '优先级', '状态', '组件', '核心动作', '接口依赖'];
  const lines = [
    headers.map(escapeCsvCell).join(','),
    ...exportRows.value.map((row) => [
      row.label,
      row.group,
      row.path,
      row.legacyRoute ?? '',
      row.priority,
      row.implemented ? '已接入' : '待接入',
      row.plannedComponent,
      row.coreActions.join('、'),
      row.apiDependencies.join('、'),
    ].map(escapeCsvCell).join(',')),
  ];
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'menu-registry.csv';
  link.click();
  URL.revokeObjectURL(url);
  emit('notice', 'success', `已导出 ${formatNumber(exportRows.value.length)} 个菜单入口`);
}

function resetFilters() {
  keyword.value = '';
  groupFilter.value = '';
  statusFilter.value = 'all';
  priorityFilter.value = '';
}

function refreshMenus() {
  loaded.value = true;
  emit('countChanged', allRoutes.value.length);
  emit('notice', 'success', `已加载 ${formatNumber(allRoutes.value.length)} 个菜单入口`);
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      refreshMenus();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshMenus,
});
</script>

<template>
  <section class="legacy-page menu-registry-page">
    <ul class="legacy-search menu-registry-search">
      <li>
        关键字：
        <input
          v-model="keyword"
          class="legacy-input input-medium"
          placeholder="菜单 / 路径 / 旧路由 / 组件"
        />
      </li>
      <li>
        分组：
        <select v-model="groupFilter" class="legacy-input input-medium">
          <option value="">全部</option>
          <option v-for="group in groups" :key="group" :value="group">{{ group }}</option>
        </select>
      </li>
      <li>
        状态：
        <select v-model="statusFilter" class="legacy-input input-small">
          <option value="all">全部</option>
          <option value="implemented">已接入</option>
          <option value="pending">待接入</option>
        </select>
      </li>
      <li>
        优先级：
        <select v-model="priorityFilter" class="legacy-input input-small">
          <option value="">全部</option>
          <option v-for="priority in priorities" :key="priority" :value="priority">{{ priority }}</option>
        </select>
      </li>
      <li>
        <button class="legacy-btn" type="button" @click="resetFilters">清空</button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="filteredCount === 0" @click="downloadMenuCsv">导出当前结果</button>
      </li>
    </ul>

    <ul class="legacy-stats menu-registry-stats">
      <li>
        <strong>{{ formatNumber(allRoutes.length) }}</strong>
        <span>入口总数</span>
      </li>
      <li>
        <strong>{{ formatNumber(implementedCount) }}</strong>
        <span>已接入</span>
      </li>
      <li>
        <strong>{{ formatNumber(pendingCount) }}</strong>
        <span>待接入</span>
      </li>
      <li>
        <strong>{{ formatNumber(filteredCount) }}</strong>
        <span>当前筛选</span>
      </li>
    </ul>

    <div class="legacy-panel">
      <table class="legacy-main-table menu-registry-table">
        <thead>
          <tr class="legacy-main-head">
            <th>菜单</th>
            <th>分组</th>
            <th>路径</th>
            <th>旧系统路由</th>
            <th>优先级</th>
            <th>状态</th>
            <th>组件</th>
            <th>动作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredCount === 0" class="legacy-main-info">
            <td colspan="8" class="legacy-empty">没有符合条件的菜单入口</td>
          </tr>
          <template v-for="entry in groupedRows" :key="entry.group">
            <tr class="menu-registry-group">
              <td colspan="8">{{ entry.group }}</td>
            </tr>
            <tr v-for="row in entry.rows" :key="row.key" class="legacy-main-info">
              <td>
                <strong>{{ row.label }}</strong>
                <small>{{ row.subtitle }}</small>
              </td>
              <td>{{ row.group }}</td>
              <td>{{ row.path }}</td>
              <td>{{ rowValue(row.legacyRoute) }}</td>
              <td>{{ row.priority }}</td>
              <td>
                <span :class="['menu-registry-status', row.implemented ? 'is-ready' : 'is-pending']">
                  {{ row.implemented ? '已接入' : '待接入' }}
                </span>
              </td>
              <td>{{ row.plannedComponent }}</td>
              <td>{{ row.coreActions.join('、') }}</td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.menu-registry-search {
  row-gap: 10px;
}

.menu-registry-stats {
  margin-bottom: 10px;
}

.menu-registry-table {
  min-width: 1200px;
}

.menu-registry-table th,
.menu-registry-table td {
  min-width: 100px;
  vertical-align: top;
}

.menu-registry-table small {
  color: #6b7280;
  display: block;
  margin-top: 4px;
  max-width: 240px;
}

.menu-registry-group td {
  background: #f3f4f6;
  color: #374151;
  font-weight: 700;
  padding: 8px 10px;
}

.menu-registry-status {
  border-radius: 4px;
  display: inline-block;
  font-weight: 700;
  padding: 3px 8px;
}

.menu-registry-status.is-ready {
  background: #dcfce7;
  color: #166534;
}

.menu-registry-status.is-pending {
  background: #fef3c7;
  color: #92400e;
}
</style>
