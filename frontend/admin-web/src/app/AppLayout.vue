<script setup lang="ts">
import { computed, ref } from 'vue';
import type { AdminUserSession } from '../api/adminSession';
import type { AppRouteItem, ViewKey } from './views';

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

const groupedMenuItems = computed(() => {
  const groups: Array<{ name: string; items: AppRouteItem[] }> = [];
  for (const item of props.menuItems) {
    let group = groups.find((entry) => entry.name === item.group);
    if (!group) {
      group = { name: item.group, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
});

const activeGroupNames = computed(() => {
  const names = new Set<string>();
  for (const item of props.menuItems) {
    if (item.key === props.activeView) names.add(item.group);
  }
  return names;
});

const expandedGroupNames = ref<Set<string>>(new Set(['订单管理']));
const mobileNavigationOpen = ref(false);

const groupIndexMap = computed(() => {
  const entries: Array<[string, string]> = [];
  groupedMenuItems.value.forEach((group, index) => {
    entries.push([group.name, String(index + 1).padStart(2, '0')]);
  });
  return Object.fromEntries(entries);
});

function groupIndex(groupName: string) {
  return groupIndexMap.value[groupName] ?? '00';
}

function isGroupOpen(groupName: string) {
  return activeGroupNames.value.has(groupName) || expandedGroupNames.value.has(groupName);
}

function toggleGroup(groupName: string) {
  const nextGroupNames = new Set(expandedGroupNames.value);
  if (nextGroupNames.has(groupName)) {
    nextGroupNames.delete(groupName);
  } else {
    nextGroupNames.add(groupName);
  }
  expandedGroupNames.value = nextGroupNames;
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
  <div class="cloud-console-shell" :class="{ 'mobile-nav-open': mobileNavigationOpen }">
    <header class="cloud-console-header">
      <div class="cloud-console-brand-block">
        <button
          type="button"
          class="cloud-console-menu-trigger"
          :aria-expanded="mobileNavigationOpen"
          aria-controls="admin-sidebar-navigation"
          @click="openMobileNavigation"
        >
          菜单
        </button>
        <div class="cloud-console-brand">
          <span class="cloud-console-brand-mark">YF</span>
          <div>
            <strong>智能药房 SaaS 平台</strong>
            <small>运营控制台</small>
          </div>
        </div>
      </div>

      <div class="cloud-console-header-actions">
        <t-tag class="cloud-console-environment" theme="warning" variant="light">开发环境</t-tag>
        <span class="cloud-console-tenant">{{ adminUser.tenantName }}</span>
        <strong>{{ adminUser.displayName || adminUser.username }}</strong>
        <button type="button" class="cloud-console-logout" @click="$emit('logout')">退出</button>
      </div>
    </header>

    <button
      v-if="mobileNavigationOpen"
      type="button"
      class="cloud-console-nav-backdrop"
      aria-label="关闭导航菜单"
      @click="closeMobileNavigation"
    />

    <aside id="admin-sidebar-navigation" class="cloud-console-sidebar">
      <div class="cloud-console-sidebar-head">
        <div>
          <span>功能导航</span>
          <strong>按业务分组进入页面</strong>
        </div>
        <button type="button" class="cloud-console-sidebar-close" @click="closeMobileNavigation">关闭</button>
      </div>

      <RouterLink v-slot="{ navigate }" :to="homePath" custom>
        <button
          type="button"
          class="cloud-console-home"
          :class="{ active: activeView === 'dashboard' }"
          @click="navigateAndClose(navigate)"
        >
          <span>首页</span>
          <b>HOME</b>
        </button>
      </RouterLink>

      <nav class="cloud-console-nav">
        <section
          v-for="group in groupedMenuItems"
          :key="group.name"
          class="cloud-console-menu-group"
          :class="{ open: isGroupOpen(group.name) }"
        >
          <button type="button" class="cloud-console-menu-title" @click="toggleGroup(group.name)">
            <span class="cloud-console-menu-index">{{ groupIndex(group.name) }}</span>
            <span>{{ group.name }}</span>
            <b>{{ isGroupOpen(group.name) ? '收起' : '展开' }}</b>
          </button>

          <div class="cloud-console-menu-items">
            <RouterLink
              v-for="item in group.items"
              :key="item.key"
              v-slot="{ navigate }"
              :to="item.path"
              custom
            >
              <button
                type="button"
                :class="{ active: activeView === item.key }"
                @click="navigateAndClose(navigate)"
              >
                <span>{{ item.label }}</span>
                <t-tag v-if="item.showCount" theme="primary" variant="light" size="small">
                  {{ counts[item.key as ViewKey] ?? 0 }}
                </t-tag>
              </button>
            </RouterLink>
          </div>
        </section>
      </nav>
    </aside>

    <main class="cloud-console-main">
      <div class="cloud-console-tabs">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="cloud-console-tab"
          :class="{ active: activeView === tab.key }"
        >
          <RouterLink v-slot="{ navigate }" :to="tab.path" custom>
            <button type="button" class="cloud-console-tab-main" @click="navigate">
              {{ tab.label }}
            </button>
          </RouterLink>
          <button
            v-if="tab.closable"
            type="button"
            class="cloud-console-tab-close"
            :title="`关闭${tab.label}`"
            @click.stop="$emit('closeTab', tab.key)"
          >
            x
          </button>
        </div>
      </div>

      <section class="cloud-console-content">
        <header class="cloud-console-page-header topbar">
          <div>
            <p>{{ subtitle }}</p>
            <h1>{{ title }}</h1>
          </div>
          <t-button theme="primary" variant="outline" size="small" @click="$emit('refresh')">
            刷新
          </t-button>
        </header>

        <t-alert
          v-if="notice"
          class="cloud-console-notice"
          :theme="notice.tone === 'error' ? 'error' : notice.tone"
          :message="notice.text"
          close
        />

        <section class="cloud-console-page-frame page-frame">
          <slot />
        </section>
      </section>
    </main>
  </div>
</template>
