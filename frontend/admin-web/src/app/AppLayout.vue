<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { AdminUserSession } from '../api/adminSession';
import {
  menuGroupOrder,
  type MenuGroupName,
  type MenuItem,
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
  menuItems: readonly MenuItem[];
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

const activeGroupName = computed<MenuGroupName | null>(() => activeMenuItem.value?.group ?? null);

const expandedGroupName = ref<MenuGroupName | null>(null);
const mobileNavigationOpen = ref(false);
const compactViewport = ref(false);
const menuTriggerRef = ref<HTMLButtonElement | null>(null);
let compactViewportQuery: MediaQueryList | null = null;

const mobileNavigationHidden = computed(() => compactViewport.value && !mobileNavigationOpen.value);

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

function closeMobileNavigation(restoreFocus = true) {
  const wasOpen = mobileNavigationOpen.value;
  mobileNavigationOpen.value = false;
  if (restoreFocus && wasOpen) {
    void nextTick(() => menuTriggerRef.value?.focus());
  }
}

function toggleMobileNavigation() {
  if (mobileNavigationOpen.value) {
    closeMobileNavigation();
    return;
  }
  mobileNavigationOpen.value = true;
}

function navigateAndClose(navigate: RouteNavigate) {
  void navigate();
  closeMobileNavigation(false);
}

function handleCompactViewportChange(event: MediaQueryListEvent) {
  compactViewport.value = event.matches;
  if (!event.matches) closeMobileNavigation(false);
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !mobileNavigationOpen.value) return;
  event.preventDefault();
  closeMobileNavigation();
}

onMounted(() => {
  compactViewportQuery = window.matchMedia('(max-width: 1023px)');
  compactViewport.value = compactViewportQuery.matches;
  compactViewportQuery.addEventListener('change', handleCompactViewportChange);
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  compactViewportQuery?.removeEventListener('change', handleCompactViewportChange);
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <div class="admin-shell" :class="{ 'admin-shell--nav-open': mobileNavigationOpen }">
    <header class="admin-shell__header">
      <button
        ref="menuTriggerRef"
        type="button"
        class="admin-shell__menu-trigger"
        :aria-label="mobileNavigationOpen ? '关闭导航菜单' : '打开导航菜单'"
        :aria-expanded="mobileNavigationOpen"
        aria-controls="admin-sidebar-navigation"
        @click="toggleMobileNavigation"
      >
        <t-icon :name="mobileNavigationOpen ? 'close' : 'menu'" />
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
      @click="closeMobileNavigation()"
    />

    <aside
      id="admin-sidebar-navigation"
      class="admin-shell__sidebar"
      aria-label="后台功能导航"
      :aria-hidden="mobileNavigationHidden ? 'true' : undefined"
      :inert="mobileNavigationHidden"
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
        >
          <RouterLink
            :to="tab.path"
            class="admin-shell__tab-main"
            :aria-current="activeView === tab.key ? 'page' : undefined"
          >
            {{ tab.label }}
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
