<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { menuItems, standaloneRouteItems, type AppRouteItem } from '../../app/views';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminPanel from '../../components/admin/AdminPanel.vue';
import AdminTableShell from '../../components/admin/AdminTableShell.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type StatusFilter = 'all' | 'implemented' | 'pending';

interface MenuStat {
  label: string;
  value: string;
}

interface GroupedMenuRows {
  group: string;
  rows: AppRouteItem[];
}

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

const allRoutes = computed<readonly AppRouteItem[]>(() => [...standaloneRouteItems, ...menuItems]);
const groups = computed(() => Array.from(new Set(allRoutes.value.map((item) => item.group))).sort());
const priorities = computed(() => Array.from(new Set(allRoutes.value.map((item) => item.priority))).sort());
const implementedCount = computed(() => allRoutes.value.filter((item) => item.implemented).length);
const pendingCount = computed(() => allRoutes.value.length - implementedCount.value);
const groupedRows = computed<GroupedMenuRows[]>(() => {
  const term = keyword.value.trim().toLowerCase();
  const filtered = allRoutes.value.filter((item) => {
    const matchesKeyword = term
      ? [
          item.label,
          item.group,
          item.path,
          item.key,
          item.subtitle,
          item.legacyRoute ?? '',
          item.plannedComponent,
          item.priority,
          ...item.coreActions,
          ...item.apiDependencies,
        ]
          .join(' ')
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
const stats = computed<MenuStat[]>(() => [
  { label: '入口总数', value: formatNumber(allRoutes.value.length) },
  { label: '已接入', value: formatNumber(implementedCount.value) },
  { label: '待接入', value: formatNumber(pendingCount.value) },
  { label: '当前结果', value: formatNumber(filteredCount.value) },
]);
const listState = computed<'loading' | 'empty' | null>(() => {
  if (!loaded.value) return 'loading';
  if (filteredCount.value === 0) return 'empty';
  return null;
});

function downloadMenuCsv() {
  if (filteredCount.value === 0) return;
  downloadCsv(
    'menu-registry.csv',
    ['菜单', '分组', '路径', '旧系统路由', '优先级', '状态', '组件', '核心动作', '接口依赖'],
    exportRows.value.map((row) => [
      row.label,
      row.group,
      row.path,
      row.legacyRoute ?? '',
      row.priority,
      row.implemented ? '已挂载组件' : '未挂载组件',
      row.plannedComponent,
      row.coreActions.join('、'),
      row.apiDependencies.join('、'),
    ]),
  );
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
  <section class="menu-registry-page">
    <AdminToolbar>
      <label class="menu-field menu-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="menu-input"
          placeholder="菜单 / 路径 / 旧路由 / 组件 / 动作 / 接口"
        >
      </label>
      <label class="menu-field menu-field--group">
        <span>分组</span>
        <select v-model="groupFilter" class="menu-input">
          <option value="">全部</option>
          <option v-for="group in groups" :key="group" :value="group">{{ group }}</option>
        </select>
      </label>
      <label class="menu-field menu-field--status">
        <span>状态</span>
        <select v-model="statusFilter" class="menu-input">
          <option value="all">全部</option>
          <option value="implemented">已挂载组件</option>
          <option value="pending">未挂载组件</option>
        </select>
      </label>
      <label class="menu-field menu-field--priority">
        <span>优先级</span>
        <select v-model="priorityFilter" class="menu-input">
          <option value="">全部</option>
          <option v-for="priority in priorities" :key="priority" :value="priority">{{ priority }}</option>
        </select>
      </label>
      <template #actions>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          @click="resetFilters"
        >
          清空
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="filteredCount === 0"
          @click="downloadMenuCsv"
        >
          导出当前结果
        </t-button>
      </template>
    </AdminToolbar>

    <div class="menu-stats" aria-label="菜单统计">
      <article
        v-for="stat in stats"
        :key="stat.label"
        class="menu-stat"
      >
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="menu-panel">
      <template #title>菜单注册表</template>
      <template #description>
        主层级按菜单名称、分组、路径和接入状态组织，旧路由与计划组件作为次级技术信息保留。
      </template>
      <template #actions>
        <span class="menu-panel-note">表格仅在容器内横向滚动，页面本身不产生横向滚动。</span>
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在加载菜单注册表。"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有符合条件的菜单入口。"
      />
      <template v-else>
        <AdminTableShell>
          <table class="menu-table">
            <thead>
              <tr>
                <th>菜单</th>
                <th>分组</th>
                <th>路径</th>
                <th>状态</th>
                <th>核心动作</th>
                <th>接口依赖</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="entry in groupedRows" :key="entry.group">
                <tr class="menu-group-row">
                  <td colspan="6">
                    <strong>{{ entry.group }}</strong>
                    <span>{{ formatNumber(entry.rows.length) }} 项</span>
                  </td>
                </tr>
                <tr
                  v-for="row in entry.rows"
                  :key="row.key"
                >
                  <td>
                    <div class="menu-primary-cell">
                      <strong>{{ row.label }}</strong>
                      <small>{{ row.subtitle }}</small>
                      <small>键值：{{ row.key }}</small>
                    </div>
                  </td>
                  <td>
                    <div class="menu-secondary-cell">
                      <strong>{{ row.group }}</strong>
                      <small>优先级：{{ row.priority }}</small>
                    </div>
                  </td>
                  <td>
                    <div class="menu-secondary-cell">
                      <strong>{{ row.path }}</strong>
                      <small>旧路由：{{ displayValue(row.legacyRoute) }}</small>
                      <small>计划组件：{{ row.plannedComponent }}</small>
                    </div>
                  </td>
                  <td>
                    <div class="menu-status-cell">
                      <t-tag
                        :theme="row.implemented ? 'success' : 'warning'"
                        variant="light"
                        size="small"
                      >
                        {{ row.implemented ? '已挂载组件' : '未挂载组件' }}
                      </t-tag>
                    </div>
                  </td>
                  <td>
                    <div class="menu-list-cell">
                      <span
                        v-for="action in row.coreActions"
                        :key="`${row.key}-${action}`"
                      >
                        {{ action }}
                      </span>
                    </div>
                  </td>
                  <td>
                    <div class="menu-list-cell">
                      <span
                        v-for="dependency in row.apiDependencies"
                        :key="`${row.key}-${dependency}`"
                      >
                        {{ dependency }}
                      </span>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </AdminTableShell>
      </template>
    </AdminPanel>
  </section>
</template>

<style scoped>
.menu-registry-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.menu-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.menu-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.menu-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.menu-stat span,
.menu-panel-note,
.menu-primary-cell small,
.menu-secondary-cell small,
.menu-list-cell span,
.menu-group-row span {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.menu-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.menu-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.menu-field--keyword {
  flex: 1 1 320px;
}

.menu-field--group,
.menu-field--status,
.menu-field--priority {
  flex: 0 0 160px;
}

.menu-input {
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #d7deea;
  border-radius: 6px;
  color: #1f2937;
  background: #ffffff;
  font-size: 13px;
  line-height: 20px;
}

.menu-table {
  min-width: 1240px;
}

.menu-group-row td {
  padding: 10px 12px;
  background: #f8fafc;
}

.menu-group-row td strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.menu-group-row td span {
  margin-left: 8px;
}

.menu-primary-cell,
.menu-secondary-cell,
.menu-status-cell,
.menu-list-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.menu-primary-cell strong,
.menu-secondary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.menu-list-cell {
  gap: 6px;
}
</style>
