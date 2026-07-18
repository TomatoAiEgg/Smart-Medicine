<script setup lang="ts">
import { computed, ref } from 'vue';
import AppLayout from './app/AppLayout.vue';
import { menuItems, viewTitles, type ViewKey } from './app/views';
import DashboardHome from './features/dashboard/DashboardHome.vue';
import DecoctionWorkspace from './features/decoction/DecoctionWorkspace.vue';
import IntegrationConsole from './features/integration/IntegrationConsole.vue';
import LogisticsFulfillment from './features/logistics/LogisticsFulfillment.vue';
import OrderCenter from './features/orders/OrderCenter.vue';
import OpsConsole from './features/ops/OpsConsole.vue';
import PortalLookup from './features/portal/PortalLookup.vue';
import ReportOverview from './features/reports/ReportOverview.vue';
import WorkflowTasks from './features/workflow/WorkflowTasks.vue';

type NoticeTone = 'info' | 'success' | 'error';
type WorkflowCounts = { reviews: number; dispenses: number; rechecks: number };

const activeView = ref<ViewKey>('dashboard');

const operationOperator = ref('admin');
const workflowCounts = ref<WorkflowCounts>({ reviews: 0, dispenses: 0, rechecks: 0 });
const dashboardHomeRef = ref<InstanceType<typeof DashboardHome> | null>(null);
const workflowTasksRef = ref<InstanceType<typeof WorkflowTasks> | null>(null);
const reportOverviewRef = ref<InstanceType<typeof ReportOverview> | null>(null);
const opsConsoleRef = ref<InstanceType<typeof OpsConsole> | null>(null);
const portalLookupRef = ref<InstanceType<typeof PortalLookup> | null>(null);
const integrationConsoleRef = ref<InstanceType<typeof IntegrationConsole> | null>(null);
const logisticsFulfillmentRef = ref<InstanceType<typeof LogisticsFulfillment> | null>(null);
const decoctionWorkspaceRef = ref<InstanceType<typeof DecoctionWorkspace> | null>(null);
const reportTotalOrders = ref(0);
const reportActivationKey = ref(0);
const opsCount = ref(0);
const integrationCount = ref(0);
const logisticsCount = ref(0);
const decoctionCount = ref(0);
const opsActivationKey = ref(0);
const integrationActivationKey = ref(0);
const logisticsActivationKey = ref(0);
const decoctionActivationKey = ref(0);
const notice = ref<{ tone: NoticeTone; text: string } | null>(null);

const currentViewTitle = computed(() => viewTitles[activeView.value]);
const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => ({
  reviews: workflowCounts.value.reviews,
  dispenses: workflowCounts.value.dispenses,
  rechecks: workflowCounts.value.rechecks,
  decoction: decoctionCount.value,
  logistics: logisticsCount.value,
  reports: reportTotalOrders.value,
  integration: integrationCount.value,
  ops: opsCount.value,
}));


function showNotice(tone: NoticeTone, text: string) {
  notice.value = { tone, text };
}

async function refreshCurrentTasks() {
  if (activeView.value === 'dashboard') {
    await dashboardHomeRef.value?.refreshDashboard();
    return;
  }
  if (activeView.value === 'reviews' || activeView.value === 'dispenses' || activeView.value === 'rechecks') {
    await workflowTasksRef.value?.refreshCurrentTasks();
    return;
  }
  if (activeView.value === 'integration') {
    await integrationConsoleRef.value?.refreshIntegrationMessages();
    return;
  }
  if (activeView.value === 'reports') {
    await reportOverviewRef.value?.refreshReports();
    return;
  }
  if (activeView.value === 'portal') {
    await portalLookupRef.value?.handlePortalQuery();
    return;
  }
  if (activeView.value === 'logistics') {
    await logisticsFulfillmentRef.value?.refreshLogisticsRecords();
    return;
  }
  if (activeView.value === 'ops') {
    await opsConsoleRef.value?.refreshOpsConsole();
    return;
  }
  if (activeView.value === 'decoction') {
    await decoctionWorkspaceRef.value?.refreshDecoctionSimulator();
    return;
  }
}

function switchView(view: ViewKey) {
  activeView.value = view;
  if (view === 'reports') reportActivationKey.value += 1;
  if (view === 'ops') opsActivationKey.value += 1;
  if (view === 'integration') integrationActivationKey.value += 1;
  if (view === 'logistics') logisticsActivationKey.value += 1;
  if (view === 'decoction') decoctionActivationKey.value += 1;
}
</script>

<template>
  <AppLayout
    :active-view="activeView"
    :title="currentViewTitle.title"
    :subtitle="currentViewTitle.subtitle"
    :menu-items="menuItems"
    :counts="menuCounts"
    :notice="notice"
    @refresh="refreshCurrentTasks"
    @switch-view="switchView"
  >
      <ReportOverview
        v-show="activeView === 'reports'"
        ref="reportOverviewRef"
        :active="activeView === 'reports'"
        :activation-key="reportActivationKey"
        @count-changed="reportTotalOrders = $event"
        @notice="showNotice"
      />

      <OpsConsole
        v-show="activeView === 'ops'"
        ref="opsConsoleRef"
        :active="activeView === 'ops'"
        :activation-key="opsActivationKey"
        @count-changed="opsCount = $event"
        @notice="showNotice"
      />

      <PortalLookup v-show="activeView === 'portal'" ref="portalLookupRef" @notice="showNotice" />

      <IntegrationConsole
        v-show="activeView === 'integration'"
        ref="integrationConsoleRef"
        :active="activeView === 'integration'"
        :activation-key="integrationActivationKey"
        @count-changed="integrationCount = $event"
        @notice="showNotice"
      />

      <LogisticsFulfillment
        v-show="activeView === 'logistics'"
        ref="logisticsFulfillmentRef"
        v-model:operation-operator="operationOperator"
        :active="activeView === 'logistics'"
        :activation-key="logisticsActivationKey"
        @count-changed="logisticsCount = $event"
        @notice="showNotice"
      />

      <DecoctionWorkspace
        v-show="activeView === 'decoction'"
        ref="decoctionWorkspaceRef"
        v-model:operation-operator="operationOperator"
        :active="activeView === 'decoction'"
        :activation-key="decoctionActivationKey"
        @count-changed="decoctionCount = $event"
        @notice="showNotice"
      />

      <DashboardHome v-if="activeView === 'dashboard'" ref="dashboardHomeRef" @notice="showNotice" />

      <WorkflowTasks
        ref="workflowTasksRef"
        v-else-if="activeView === 'reviews' || activeView === 'dispenses' || activeView === 'rechecks'"
        :active-view="activeView"
        @counts-changed="workflowCounts = $event"
        @notice="showNotice"
      />

      <OrderCenter v-else-if="activeView === 'orders'" @notice="showNotice" />

  </AppLayout>
</template>
