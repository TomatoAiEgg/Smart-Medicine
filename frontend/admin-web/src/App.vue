<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from './app/AppLayout.vue';
import type { LayoutTab } from './app/AppLayout.vue';
import { isViewKey, menuItems, routeByKey, type ImplementedViewKey, type ViewKey } from './app/views';
import DashboardHome from './features/dashboard/DashboardHome.vue';
import DecoctionWorkspace from './features/decoction/DecoctionWorkspace.vue';
import IntegrationConsole from './features/integration/IntegrationConsole.vue';
import LogisticsFulfillment from './features/logistics/LogisticsFulfillment.vue';
import AddressModify from './features/orders/AddressModify.vue';
import OrderCenter from './features/orders/OrderCenter.vue';
import OrderReceipt from './features/orders/OrderReceipt.vue';
import PrescriptionModify from './features/orders/PrescriptionModify.vue';
import OrderObservabilityPanel from './features/ops/OrderObservabilityPanel.vue';
import OpsConsole from './features/ops/OpsConsole.vue';
import PendingMenuPage from './features/pending/PendingMenuPage.vue';
import PortalLookup from './features/portal/PortalLookup.vue';
import ReportOverview from './features/reports/ReportOverview.vue';
import DispensePrintWorkspace from './features/workflow/DispensePrintWorkspace.vue';
import RecheckScanWorkspace from './features/workflow/RecheckScanWorkspace.vue';
import WorkflowTasks from './features/workflow/WorkflowTasks.vue';

type NoticeTone = 'info' | 'success' | 'error';
type WorkflowCounts = { reviews: number; dispenses: number; rechecks: number };
type WorkflowViewKey = Extract<ViewKey, 'reviews' | 'dispenses' | 'rechecks'>;
type RecheckScanMode = 'single' | 'multi';

const route = useRoute();
const router = useRouter();

const operationOperator = ref('admin');
const workflowCounts = ref<WorkflowCounts>({ reviews: 0, dispenses: 0, rechecks: 0 });
const dashboardHomeRef = ref<InstanceType<typeof DashboardHome> | null>(null);
const workflowTasksRef = ref<InstanceType<typeof WorkflowTasks> | null>(null);
const dispensePrintRef = ref<InstanceType<typeof DispensePrintWorkspace> | null>(null);
const recheckScanRef = ref<InstanceType<typeof RecheckScanWorkspace> | null>(null);
const reportOverviewRef = ref<InstanceType<typeof ReportOverview> | null>(null);
const opsConsoleRef = ref<InstanceType<typeof OpsConsole> | null>(null);
const portalLookupRef = ref<InstanceType<typeof PortalLookup> | null>(null);
const integrationConsoleRef = ref<InstanceType<typeof IntegrationConsole> | null>(null);
const logisticsFulfillmentRef = ref<InstanceType<typeof LogisticsFulfillment> | null>(null);
const decoctionWorkspaceRef = ref<InstanceType<typeof DecoctionWorkspace> | null>(null);
const orderObservabilityRef = ref<InstanceType<typeof OrderObservabilityPanel> | null>(null);
const addressModifyRef = ref<InstanceType<typeof AddressModify> | null>(null);
const prescriptionModifyRef = ref<InstanceType<typeof PrescriptionModify> | null>(null);
const orderReceiptRef = ref<InstanceType<typeof OrderReceipt> | null>(null);
const reportTotalOrders = ref(0);
const reportActivationKey = ref(0);
const opsCount = ref(0);
const integrationCount = ref(0);
const logisticsCount = ref(0);
const decoctionCount = ref(0);
const observabilityCount = ref(0);
const opsActivationKey = ref(0);
const integrationActivationKey = ref(0);
const logisticsActivationKey = ref(0);
const decoctionActivationKey = ref(0);
const observabilityActivationKey = ref(0);
const addressModifyActivationKey = ref(0);
const prescriptionModifyActivationKey = ref(0);
const orderReceiptActivationKey = ref(0);
const notice = ref<{ tone: NoticeTone; text: string } | null>(null);
const openTabs = ref<ViewKey[]>([]);

function routeKeyFromMeta(value: unknown): ViewKey {
  return typeof value === 'string' && isViewKey(value) ? value : 'dashboard';
}

const activeView = computed<ViewKey>(() => routeKeyFromMeta(route.meta.routeKey));
const currentRouteItem = computed(() => routeByKey[activeView.value]);
const currentViewTitle = computed(() => ({
  title: currentRouteItem.value.label,
  subtitle: currentRouteItem.value.subtitle,
}));
const currentComponentKey = computed<ImplementedViewKey | undefined>(() => currentRouteItem.value.componentKey);
const workflowRouteKey = computed<WorkflowViewKey | null>(() => {
  const key = currentComponentKey.value;
  if (key === 'reviews' || key === 'dispenses' || key === 'rechecks') return key;
  return null;
});
const recheckScanMode = computed<RecheckScanMode>(() => (activeView.value === 'orderRechecksMulti' ? 'multi' : 'single'));
const homePath = routeByKey.dashboard.path;

const layoutTabs = computed<LayoutTab[]>(() => [
  { key: 'dashboard', label: '首页', closable: false, path: routeByKey.dashboard.path },
  ...openTabs.value.map((key) => ({
    key,
    label: routeByKey[key].label,
    closable: true,
    path: routeByKey[key].path,
  })),
]);
const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => ({
  reviews: workflowCounts.value.reviews,
  dispenses: workflowCounts.value.dispenses,
  rechecks: workflowCounts.value.rechecks,
  orderRechecksMulti: workflowCounts.value.rechecks,
  orderRecheckRecords: workflowCounts.value.rechecks,
  decoction: decoctionCount.value,
  logistics: logisticsCount.value,
  reports: reportTotalOrders.value,
  integration: integrationCount.value,
  observability: observabilityCount.value,
  ops: opsCount.value,
}));

function showNotice(tone: NoticeTone, text: string) {
  notice.value = { tone, text };
}

function updateRecheckCount(count: number) {
  workflowCounts.value = { ...workflowCounts.value, rechecks: count };
}

function updateDispenseCount(count: number) {
  workflowCounts.value = { ...workflowCounts.value, dispenses: count };
}

function ensureOpenTab(view: ViewKey) {
  if (view !== 'dashboard' && !openTabs.value.includes(view)) {
    openTabs.value = [...openTabs.value, view];
  }
}

watch(activeView, (view) => {
  ensureOpenTab(view);
}, { immediate: true });

watch(currentComponentKey, (componentKey) => {
  if (componentKey === 'reports') reportActivationKey.value += 1;
  if (componentKey === 'ops') opsActivationKey.value += 1;
  if (componentKey === 'observability') observabilityActivationKey.value += 1;
  if (componentKey === 'integration') integrationActivationKey.value += 1;
  if (componentKey === 'logistics') logisticsActivationKey.value += 1;
  if (componentKey === 'decoction') decoctionActivationKey.value += 1;
  if (componentKey === 'addressModify') addressModifyActivationKey.value += 1;
  if (componentKey === 'prescriptionModify') prescriptionModifyActivationKey.value += 1;
  if (componentKey === 'orderReceipt') orderReceiptActivationKey.value += 1;
}, { immediate: true });

async function refreshCurrentTasks() {
  const componentKey = currentComponentKey.value;
  if (componentKey === 'dashboard') {
    await dashboardHomeRef.value?.refreshDashboard();
    return;
  }
  if (workflowRouteKey.value) {
    await workflowTasksRef.value?.refreshCurrentTasks();
    return;
  }
  if (componentKey === 'dispensePrint') {
    await dispensePrintRef.value?.refreshDispenseTasks();
    return;
  }
  if (componentKey === 'recheckScan') {
    await recheckScanRef.value?.refreshRecheckScanTasks();
    return;
  }
  if (componentKey === 'integration') {
    await integrationConsoleRef.value?.refreshIntegrationMessages();
    return;
  }
  if (componentKey === 'reports') {
    await reportOverviewRef.value?.refreshReports();
    return;
  }
  if (componentKey === 'portal') {
    await portalLookupRef.value?.handlePortalQuery();
    return;
  }
  if (componentKey === 'logistics') {
    await logisticsFulfillmentRef.value?.refreshLogisticsRecords();
    return;
  }
  if (componentKey === 'addressModify') {
    await addressModifyRef.value?.refreshAddressOrders();
    return;
  }
  if (componentKey === 'prescriptionModify') {
    await prescriptionModifyRef.value?.refreshPrescriptionOrders();
    return;
  }
  if (componentKey === 'orderReceipt') {
    await orderReceiptRef.value?.refreshOrderReceipts();
    return;
  }
  if (componentKey === 'ops') {
    await opsConsoleRef.value?.refreshOpsConsole();
    return;
  }
  if (componentKey === 'observability') {
    await orderObservabilityRef.value?.refreshOrderObservability();
    return;
  }
  if (componentKey === 'decoction') {
    await decoctionWorkspaceRef.value?.refreshDecoctionSimulator();
    return;
  }
  showNotice('info', `${currentRouteItem.value.label} 页面待实现，暂无可刷新数据`);
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
  <AppLayout
    :active-view="activeView"
    :title="currentViewTitle.title"
    :subtitle="currentViewTitle.subtitle"
    :home-path="homePath"
    :menu-items="menuItems"
    :counts="menuCounts"
    :notice="notice"
    :tabs="layoutTabs"
    @close-tab="closeTab"
    @refresh="refreshCurrentTasks"
  >
    <ReportOverview
      v-show="currentComponentKey === 'reports'"
      ref="reportOverviewRef"
      :active="currentComponentKey === 'reports'"
      :activation-key="reportActivationKey"
      @count-changed="reportTotalOrders = $event"
      @notice="showNotice"
    />

    <OpsConsole
      v-show="currentComponentKey === 'ops'"
      ref="opsConsoleRef"
      :active="currentComponentKey === 'ops'"
      :activation-key="opsActivationKey"
      @count-changed="opsCount = $event"
      @notice="showNotice"
    />

    <OrderObservabilityPanel
      v-show="currentComponentKey === 'observability'"
      ref="orderObservabilityRef"
      :active="currentComponentKey === 'observability'"
      :activation-key="observabilityActivationKey"
      @count-changed="observabilityCount = $event"
      @notice="showNotice"
    />

    <PortalLookup
      v-show="currentComponentKey === 'portal'"
      ref="portalLookupRef"
      @notice="showNotice"
    />

    <IntegrationConsole
      v-show="currentComponentKey === 'integration'"
      ref="integrationConsoleRef"
      :active="currentComponentKey === 'integration'"
      :activation-key="integrationActivationKey"
      @count-changed="integrationCount = $event"
      @notice="showNotice"
    />

    <LogisticsFulfillment
      v-show="currentComponentKey === 'logistics'"
      ref="logisticsFulfillmentRef"
      v-model:operation-operator="operationOperator"
      :active="currentComponentKey === 'logistics'"
      :activation-key="logisticsActivationKey"
      @count-changed="logisticsCount = $event"
      @notice="showNotice"
    />

    <DecoctionWorkspace
      v-show="currentComponentKey === 'decoction'"
      ref="decoctionWorkspaceRef"
      v-model:operation-operator="operationOperator"
      :active="currentComponentKey === 'decoction'"
      :activation-key="decoctionActivationKey"
      :route-key="activeView"
      @count-changed="decoctionCount = $event"
      @notice="showNotice"
    />

    <DashboardHome
      v-if="currentComponentKey === 'dashboard'"
      ref="dashboardHomeRef"
      @notice="showNotice"
    />

    <RecheckScanWorkspace
      v-else-if="currentComponentKey === 'recheckScan'"
      ref="recheckScanRef"
      :active="currentComponentKey === 'recheckScan'"
      :mode="recheckScanMode"
      @count-changed="updateRecheckCount"
      @notice="showNotice"
    />

    <DispensePrintWorkspace
      v-else-if="currentComponentKey === 'dispensePrint'"
      ref="dispensePrintRef"
      @count-changed="updateDispenseCount"
      @notice="showNotice"
    />

    <WorkflowTasks
      v-else-if="workflowRouteKey !== null"
      ref="workflowTasksRef"
      :active-view="workflowRouteKey"
      @counts-changed="workflowCounts = $event"
      @notice="showNotice"
    />

    <OrderCenter v-else-if="currentComponentKey === 'orders'" @notice="showNotice" />

    <AddressModify
      v-else-if="currentComponentKey === 'addressModify'"
      ref="addressModifyRef"
      active
      :activation-key="addressModifyActivationKey"
      @notice="showNotice"
    />

    <PrescriptionModify
      v-else-if="currentComponentKey === 'prescriptionModify'"
      ref="prescriptionModifyRef"
      active
      :activation-key="prescriptionModifyActivationKey"
      @notice="showNotice"
    />

    <OrderReceipt
      v-else-if="currentComponentKey === 'orderReceipt'"
      ref="orderReceiptRef"
      active
      :activation-key="orderReceiptActivationKey"
      @notice="showNotice"
    />

    <PendingMenuPage v-else :item="currentRouteItem" />
  </AppLayout>
</template>
