<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from './app/AppLayout.vue';
import type { LayoutTab } from './app/AppLayout.vue';
import type { RouteCountPayload } from './app/AdminRouteHost.vue';
import type { AdminUserSession } from './api/adminSession';
import { logoutAdmin, restoreAdminSession } from './api/auth';
import LoginView from './features/auth/LoginView.vue';
import {
  canAccessRoute,
  isViewKey,
  menuItems,
  routeByKey,
  routeItems,
  type AppRouteItem,
  type ImplementedViewKey,
  type MenuItem,
  type ViewKey,
} from './app/views';

type NoticeTone = 'info' | 'success' | 'error';
type RouteHostExpose = {
  refreshCurrent: () => Promise<void>;
};

const route = useRoute();
const router = useRouter();

const operationOperator = ref('admin');
const adminSession = ref<AdminUserSession | null>(null);
const authRestoring = ref(true);
const notice = ref<{ tone: NoticeTone; text: string } | null>(null);
const openTabs = ref<ViewKey[]>([]);
const routeActivationKey = ref(0);
const routeCounts = ref<Partial<Record<ViewKey, number>>>({});
const componentCounts = ref<Partial<Record<ImplementedViewKey, number>>>({});
const decoctionCloudPrintCount = ref(0);
const routeHostRef = ref<RouteHostExpose | null>(null);

function applyAdminSession(session: AdminUserSession) {
  adminSession.value = session;
  operationOperator.value = session.username;
  authRestoring.value = false;
}

async function initializeAdminSession() {
  authRestoring.value = true;
  const session = await restoreAdminSession();
  if (session) applyAdminSession(session);
  authRestoring.value = false;
}

function clearRuntimeState() {
  notice.value = null;
  openTabs.value = [];
  routeCounts.value = {};
  componentCounts.value = {};
  decoctionCloudPrintCount.value = 0;
}

function handleAuthExpired() {
  adminSession.value = null;
  clearRuntimeState();
}

function handleAuthRefreshed(event: Event) {
  const session = (event as CustomEvent<AdminUserSession>).detail;
  if (session) applyAdminSession(session);
}

async function handleLogout() {
  await logoutAdmin();
  handleAuthExpired();
}

onMounted(() => {
  window.addEventListener('admin-auth-expired', handleAuthExpired);
  window.addEventListener('admin-auth-refreshed', handleAuthRefreshed);
  void initializeAdminSession();
});

onBeforeUnmount(() => {
  window.removeEventListener('admin-auth-expired', handleAuthExpired);
  window.removeEventListener('admin-auth-refreshed', handleAuthRefreshed);
});

function routeKeyFromMeta(value: unknown): ViewKey {
  return typeof value === 'string' && isViewKey(value) ? value : 'dashboard';
}

const activeView = computed<ViewKey>(() => routeKeyFromMeta(route.meta.routeKey));
const currentRouteItem = computed(() => routeByKey[activeView.value]);
const currentViewTitle = computed(() => ({
  title: currentRouteItem.value.label,
  subtitle: currentRouteItem.value.subtitle,
}));
const homePath = routeByKey.dashboard.path;
const sessionPermissions = computed(() => new Set(adminSession.value?.permissions ?? []));
const canManageSystem = computed(() => Boolean(adminSession.value?.tenantWide) && sessionPermissions.value.has('system:write'));
const canViewHealth = computed(() => sessionPermissions.value.has('ops:read'));

const layoutTabs = computed<LayoutTab[]>(() => [
  { key: 'dashboard', label: '首页', closable: false, path: routeByKey.dashboard.path },
  ...openTabs.value.filter((key) => canAccessRoute(routeByKey[key], sessionPermissions.value)).map((key) => ({
    key,
    label: routeByKey[key].label,
    closable: true,
    path: routeByKey[key].path,
  })),
]);

const navigationItems = computed<readonly MenuItem[]>(() => [
  ...menuItems.filter((item) => canAccessRoute(item, sessionPermissions.value)),
]);

function countForRoute(item: AppRouteItem) {
  if (item.key === 'decoctionCloudPrintRecords') return decoctionCloudPrintCount.value;
  return routeCounts.value[item.key as ViewKey] ?? (item.componentKey ? componentCounts.value[item.componentKey] : undefined) ?? 0;
}

const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => {
  const counts: Partial<Record<ViewKey, number>> = {};
  for (const item of routeItems) {
    if ('showCount' in item && item.showCount) counts[item.key as ViewKey] = countForRoute(item);
  }
  return counts;
});

function showNotice(tone: NoticeTone, text: string) {
  notice.value = { tone, text };
}

function handleRouteCountChanged(payload: RouteCountPayload) {
  routeCounts.value = {
    ...routeCounts.value,
    [payload.routeKey]: payload.count,
  };
  componentCounts.value = {
    ...componentCounts.value,
    [payload.componentKey]: payload.count,
  };
}

function ensureOpenTab(view: ViewKey) {
  if (view !== 'dashboard' && canAccessRoute(routeByKey[view], sessionPermissions.value) && !openTabs.value.includes(view)) {
    openTabs.value = [...openTabs.value, view];
  }
}

watch(activeView, (view) => {
  routeActivationKey.value += 1;
  ensureOpenTab(view);
}, { immediate: true });

watch([adminSession, activeView], ([session, view]) => {
  if (!session || canAccessRoute(routeByKey[view], new Set(session.permissions))) return;
  openTabs.value = openTabs.value.filter((key) => canAccessRoute(routeByKey[key], new Set(session.permissions)));
  void router.replace(routeByKey.dashboard.path);
}, { immediate: true });

async function refreshCurrentTasks() {
  await routeHostRef.value?.refreshCurrent();
}

function closeTab(view: ViewKey) {
  const index = openTabs.value.indexOf(view);
  if (index < 0) return;

  openTabs.value = openTabs.value.filter((key) => key !== view);
  if (activeView.value !== view) return;

  const nextView = openTabs.value[index - 1] ?? openTabs.value[index] ?? 'dashboard';
  void router.push(routeByKey[nextView].path);
}
</script>

<template>
  <LoginView
    v-if="authRestoring || !adminSession"
    :restoring="authRestoring"
    @authenticated="applyAdminSession"
  />
  <AppLayout
    v-else
    :active-view="activeView"
    :title="currentViewTitle.title"
    :subtitle="currentViewTitle.subtitle"
    :home-path="homePath"
    :menu-items="navigationItems"
    :counts="menuCounts"
    :notice="notice"
    :tabs="layoutTabs"
    :admin-user="adminSession"
    @close-tab="closeTab"
    @logout="handleLogout"
    @refresh="refreshCurrentTasks"
  >
    <RouterView v-slot="{ Component }">
      <component
        :is="Component"
        :key="activeView"
        ref="routeHostRef"
        v-model:operation-operator="operationOperator"
        :admin-user="adminSession"
        :can-manage-system="canManageSystem"
        :can-view-health="canViewHealth"
        :activation-key="routeActivationKey"
        @notice="showNotice"
        @count-changed="handleRouteCountChanged"
        @cloud-print-count-changed="decoctionCloudPrintCount = $event"
      />
    </RouterView>
  </AppLayout>
</template>
