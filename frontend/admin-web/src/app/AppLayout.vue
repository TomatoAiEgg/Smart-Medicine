<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { AdminUserSession } from '../api/adminSession';
import {
  menuGroupOrder,
  type AppRouteItem,
  type MenuGroupName,
  type ViewKey,
} from './views';

export interface LayoutTab {
  key: ViewKey;
  label: string;
  closable: boolean;
  path: string;
}

type RouteNavigate = (event?: MouseEvent) => Promise<unknown> | unknown;

const props = defineProps<{
  activeView: ViewKey;
  title: string;
  subtitle: string;
  homePath: string;
  menuItems: readonly AppRouteItem[];
  counts: Partial<Record<ViewKey, number>>;
  notice: { tone: 'info' | 'success' | 'error'; text: string } | null;
  tabs: LayoutTab[];
  adminUser: AdminUserSession;
}>();

defineEmits<{
  refresh: [];
  closeTab: [view: ViewKey];
  logout: [];
}>();

const groupIconNames: Record<MenuGroupName, string> = {
  系统管理: 'system-application',
  参数管理: 'setting',
  机构管理: 'city',
  物流管理: 'vehicle',
  订单管理: 'order-descending',
  维护管理: 'tools',
  标签管理: 'tag',
  短信管理: 'mail',
  药品管理: 'hospital',
  报表管理: 'chart-bar',
  煎煮管理: 'drink',
};

const activeMenuItem = computed(() => (
  props.menuItems.find((item) => item.key === props.activeView) ?? null
));

const activeGroupName = computed<MenuGroupName | null>(() => {
  const groupName = activeMenuItem.value?.group;
  return menuGroupOrder.find((name) => name === groupName) ?? null;
});

const expandedGroupName = ref<string | null>(null);
const mobileNavigationOpen = ref(false);

watch(() => props.activeView, () => {
  expandedGroupName.value = activeGroupName.value;
}, { immediate: true });

const groupedMenuItems = computed(() => menuGroupOrder
  .map((name) => ({
    name,
    items: props.menuItems.filter((item) => item.group === name),
  }))
  .filter((group) => group.items.length > 0));

function groupPanelId(groupName: MenuGroupName) {
  return `admin-menu-group-${groupName}`;
}

function isGroupOpen(groupName: MenuGroupName) {
  return expandedGroupName.value === groupName;
}

function toggleGroup(groupName: MenuGroupName) {
  expandedGroupName.value = isGroupOpen(groupName) ? null : groupName;
}

function openMobileNavigation() {
  mobileNavigationOpen.value = true;
}

function closeMobileNavigation() {
  mobileNavigationOpen.value = false;
}

function navigateAndClose(navigate: RouteNavigate) {
  void navigate();
  closeMobileNavigation();
}
</script>

<template>
  <div class="admin-shell" :class="{ 'admin-shell--nav-open': mobileNavigationOpen }">
    <header class="admin-shell__header">
      <button
        type="button"
        class="admin-shell__menu-trigger"
        aria-label="打开导航菜单"
        :aria-expanded="mobileNavigationOpen"
        aria-controls="admin-sidebar-navigation"
        @click="openMobileNavigation"
      >
        <t-icon name="menu" />
      </button>

      <strong class="admin-shell__brand">智能药房 SaaS</strong>

      <div class="admin-shell__account">
        <span>{{ adminUser.tenantName }}</span>
        <strong>{{ adminUser.displayName || adminUser.username }}</strong>
        <t-button theme="default" variant="text" size="small" @click="$emit('logout')">
          <template #icon>
            <t-icon name="logout" />
          </template>
          退出
        </t-button>
      </div>
    </header>

    <button
      v-if="mobileNavigationOpen"
      type="button"
      class="admin-shell__backdrop"
      aria-label="关闭导航菜单"
      @click="closeMobileNavigation"
    />

    <aside
      id="admin-sidebar-navigation"
      class="admin-shell__sidebar"
      aria-label="后台功能导航"
    >
      <RouterLink v-slot="{ navigate }" :to="homePath" custom>
        <button
          type="button"
          class="admin-shell__home"
          :class="{ active: activeView === 'dashboard' }"
          :aria-current="activeView === 'dashboard' ? 'page' : undefined"
          @click="navigateAndClose(navigate)"
        >
          <t-icon name="home" />
          <span>首页</span>
        </button>
      </RouterLink>

      <nav aria-label="业务菜单">
        <section
          v-for="group in groupedMenuItems"
          :key="group.name"
          class="admin-shell__menu-group"
          :class="{ open: isGroupOpen(group.name) }"
        >
          <button
            type="button"
            class="admin-shell__menu-title"
            :aria-expanded="isGroupOpen(group.name)"
            :aria-controls="groupPanelId(group.name)"
            @click="toggleGroup(group.name)"
          >
            <span>
              <t-icon :name="groupIconNames[group.name]" />
              <span>{{ group.name }}</span>
            </span>
            <t-icon :name="isGroupOpen(group.name) ? 'chevron-down' : 'chevron-right'" />
          </button>

          <div :id="groupPanelId(group.name)" class="admin-shell__menu-items">
            <RouterLink
              v-for="item in group.items"
              :key="item.key"
              v-slot="{ navigate }"
              :to="item.path"
              custom
            >
              <button
                type="button"
                class="admin-shell__menu-item"
                :class="{ active: activeView === item.key }"
                :aria-current="activeView === item.key ? 'page' : undefined"
                @click="navigateAndClose(navigate)"
              >
                <span>{{ item.label }}</span>
                <t-tag
                  v-if="item.showCount"
                  class="admin-shell__menu-count"
                  theme="default"
                  variant="light"
                  size="small"
                >
                  {{ counts[item.key as ViewKey] ?? 0 }}
                </t-tag>
              </button>
            </RouterLink>
          </div>
        </section>
      </nav>
    </aside>

    <main class="admin-shell__main">
      <nav class="admin-shell__tabs" aria-label="已打开页面">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="admin-shell__tab"
          :class="{ active: activeView === tab.key }"
          :aria-current="activeView === tab.key ? 'page' : undefined"
        >
          <RouterLink v-slot="{ navigate }" :to="tab.path" custom>
            <button
              type="button"
              class="admin-shell__tab-main"
              role="tab"
              :aria-selected="activeView === tab.key"
              @click="navigate"
            >
              {{ tab.label }}
            </button>
          </RouterLink>
          <button
            v-if="tab.closable"
            type="button"
            class="admin-shell__tab-close"
            :title="`关闭${tab.label}`"
            :aria-label="`关闭${tab.label}`"
            @click.stop="$emit('closeTab', tab.key)"
          >
            <t-icon name="close" />
          </button>
        </div>
      </nav>

      <section class="admin-shell__workspace">
        <header class="admin-page-heading">
          <div>
            <p>{{ activeGroupName ?? '工作台' }}</p>
            <h1>{{ title }}</h1>
            <span class="admin-page-heading__subtitle">{{ subtitle }}</span>
          </div>
          <t-button theme="primary" variant="outline" size="small" @click="$emit('refresh')">
            <template #icon>
              <t-icon name="refresh" />
            </template>
            刷新
          </t-button>
        </header>

        <t-alert
          v-if="notice"
          class="admin-page-notice"
          :theme="notice.tone"
          :message="notice.text"
          close
        />

        <section class="admin-page-content">
          <slot />
        </section>
      </section>
    </main>
  </div>
</template>
