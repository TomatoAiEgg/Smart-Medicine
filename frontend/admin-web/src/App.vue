<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from './app/AppLayout.vue';
import type { LayoutTab } from './app/AppLayout.vue';
import {
  isViewKey,
  menuItems,
  routeByKey,
  standaloneRouteItems,
  type AppRouteItem,
  type ImplementedViewKey,
  type ViewKey,
} from './app/views';
import DashboardHome from './features/dashboard/DashboardHome.vue';
import DecoctionWorkspace from './features/decoction/DecoctionWorkspace.vue';
import IntegrationConsole from './features/integration/IntegrationConsole.vue';
import LogisPrint from './features/logistics/LogisPrint.vue';
import LabelPrint from './features/label/LabelPrint.vue';
import LogisticsFulfillment from './features/logistics/LogisticsFulfillment.vue';
import LogisticsInfo from './features/logistics/LogisticsInfo.vue';
import UnreceivedFollowup from './features/logistics/UnreceivedFollowup.vue';
import AddressModify from './features/orders/AddressModify.vue';
import ManualProcess from './features/orders/ManualProcess.vue';
import OrderCenter from './features/orders/OrderCenter.vue';
import OrderWarehouse from './features/orders/OrderWarehouse.vue';
import OrderManageAction from './features/orders/OrderManageAction.vue';
import OrderReceipt from './features/orders/OrderReceipt.vue';
import PrescriptionModify from './features/orders/PrescriptionModify.vue';
import PrescriptionReprint from './features/orders/PrescriptionReprint.vue';
import ExceptionLogList from './features/ops/ExceptionLogList.vue';
import OrderObservabilityPanel from './features/ops/OrderObservabilityPanel.vue';
import OpsConsole from './features/ops/OpsConsole.vue';
import PendingMenuPage from './features/pending/PendingMenuPage.vue';
import PortalLookup from './features/portal/PortalLookup.vue';
import AuditPerformance from './features/reports/AuditPerformance.vue';
import AuditPerformanceDetails from './features/reports/AuditPerformanceDetails.vue';
import DecoctionPerformance from './features/reports/DecoctionPerformance.vue';
import DecoctionPerformanceDetails from './features/reports/DecoctionPerformanceDetails.vue';
import DispensePerformance from './features/reports/DispensePerformance.vue';
import DispensePerformanceDetails from './features/reports/DispensePerformanceDetails.vue';
import HerbDosage from './features/reports/HerbDosage.vue';
import InstitutionHerbReconciliation from './features/reports/InstitutionHerbReconciliation.vue';
import InstitutionPrescriptionCounts from './features/reports/InstitutionPrescriptionCounts.vue';
import PrescriptionHerbDetails from './features/reports/PrescriptionHerbDetails.vue';
import PrescriptionReconciliation from './features/reports/PrescriptionReconciliation.vue';
import RecheckPerformance from './features/reports/RecheckPerformance.vue';
import RecheckPerformanceDetails from './features/reports/RecheckPerformanceDetails.vue';
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
const auditPerformanceRef = ref<InstanceType<typeof AuditPerformance> | null>(null);
const auditPerformanceDetailsRef = ref<InstanceType<typeof AuditPerformanceDetails> | null>(null);
const decoctionPerformanceRef = ref<InstanceType<typeof DecoctionPerformance> | null>(null);
const decoctionPerformanceDetailsRef = ref<InstanceType<typeof DecoctionPerformanceDetails> | null>(null);
const dispensePerformanceRef = ref<InstanceType<typeof DispensePerformance> | null>(null);
const dispensePerformanceDetailsRef = ref<InstanceType<typeof DispensePerformanceDetails> | null>(null);
const herbDosageRef = ref<InstanceType<typeof HerbDosage> | null>(null);
const institutionHerbReconciliationRef = ref<InstanceType<typeof InstitutionHerbReconciliation> | null>(null);
const prescriptionHerbDetailsRef = ref<InstanceType<typeof PrescriptionHerbDetails> | null>(null);
const recheckPerformanceRef = ref<InstanceType<typeof RecheckPerformance> | null>(null);
const recheckPerformanceDetailsRef = ref<InstanceType<typeof RecheckPerformanceDetails> | null>(null);
const institutionPrescriptionCountsRef = ref<InstanceType<typeof InstitutionPrescriptionCounts> | null>(null);
const opsConsoleRef = ref<InstanceType<typeof OpsConsole> | null>(null);
const portalLookupRef = ref<InstanceType<typeof PortalLookup> | null>(null);
const integrationConsoleRef = ref<InstanceType<typeof IntegrationConsole> | null>(null);
const logisPrintRef = ref<InstanceType<typeof LogisPrint> | null>(null);
const logisticsFulfillmentRef = ref<InstanceType<typeof LogisticsFulfillment> | null>(null);
const logisticsInfoRef = ref<InstanceType<typeof LogisticsInfo> | null>(null);
const unreceivedFollowupRef = ref<InstanceType<typeof UnreceivedFollowup> | null>(null);
const exceptionLogRef = ref<InstanceType<typeof ExceptionLogList> | null>(null);
const labelPrintRef = ref<InstanceType<typeof LabelPrint> | null>(null);
const decoctionWorkspaceRef = ref<InstanceType<typeof DecoctionWorkspace> | null>(null);
const orderObservabilityRef = ref<InstanceType<typeof OrderObservabilityPanel> | null>(null);
const prescriptionReconciliationRef = ref<InstanceType<typeof PrescriptionReconciliation> | null>(null);
const addressModifyRef = ref<InstanceType<typeof AddressModify> | null>(null);
const prescriptionModifyRef = ref<InstanceType<typeof PrescriptionModify> | null>(null);
const orderManageActionRef = ref<InstanceType<typeof OrderManageAction> | null>(null);
const prescriptionReprintRef = ref<InstanceType<typeof PrescriptionReprint> | null>(null);
const manualProcessRef = ref<InstanceType<typeof ManualProcess> | null>(null);
const orderWarehouseRef = ref<InstanceType<typeof OrderWarehouse> | null>(null);
const orderReceiptRef = ref<InstanceType<typeof OrderReceipt> | null>(null);
const reportTotalOrders = ref(0);
const auditPerformanceCount = ref(0);
const auditPerformanceDetailsCount = ref(0);
const decoctionPerformanceCount = ref(0);
const decoctionPerformanceDetailsCount = ref(0);
const dispensePerformanceCount = ref(0);
const dispensePerformanceDetailsCount = ref(0);
const herbDosageCount = ref(0);
const institutionHerbReconciliationCount = ref(0);
const prescriptionHerbDetailsCount = ref(0);
const recheckPerformanceCount = ref(0);
const recheckPerformanceDetailsCount = ref(0);
const institutionPrescriptionCountsCount = ref(0);
const reportActivationKey = ref(0);
const auditPerformanceActivationKey = ref(0);
const auditPerformanceDetailsActivationKey = ref(0);
const decoctionPerformanceActivationKey = ref(0);
const decoctionPerformanceDetailsActivationKey = ref(0);
const dispensePerformanceActivationKey = ref(0);
const dispensePerformanceDetailsActivationKey = ref(0);
const herbDosageActivationKey = ref(0);
const institutionHerbReconciliationActivationKey = ref(0);
const prescriptionHerbDetailsActivationKey = ref(0);
const recheckPerformanceActivationKey = ref(0);
const recheckPerformanceDetailsActivationKey = ref(0);
const institutionPrescriptionCountsActivationKey = ref(0);
const opsCount = ref(0);
const integrationCount = ref(0);
const logisPrintCount = ref(0);
const logisticsCount = ref(0);
const logisticsInfoCount = ref(0);
const unreceivedFollowupCount = ref(0);
const exceptionLogCount = ref(0);
const labelPrintCount = ref(0);
const prescriptionReconciliationCount = ref(0);
const decoctionCount = ref(0);
const observabilityCount = ref(0);
const opsActivationKey = ref(0);
const integrationActivationKey = ref(0);
const logisPrintActivationKey = ref(0);
const logisticsActivationKey = ref(0);
const logisticsInfoActivationKey = ref(0);
const unreceivedFollowupActivationKey = ref(0);
const exceptionLogActivationKey = ref(0);
const labelPrintActivationKey = ref(0);
const prescriptionReconciliationActivationKey = ref(0);
const decoctionActivationKey = ref(0);
const observabilityActivationKey = ref(0);
const addressModifyActivationKey = ref(0);
const prescriptionModifyActivationKey = ref(0);
const orderManageActionActivationKey = ref(0);
const prescriptionReprintActivationKey = ref(0);
const manualProcessActivationKey = ref(0);
const orderWarehouseActivationKey = ref(0);
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
const navigationItems = computed<readonly AppRouteItem[]>(() => [
  ...standaloneRouteItems.filter((item) => item.key !== 'dashboard'),
  ...menuItems,
]);
const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => ({
  reviews: workflowCounts.value.reviews,
  dispenses: workflowCounts.value.dispenses,
  rechecks: workflowCounts.value.rechecks,
  orderRechecksMulti: workflowCounts.value.rechecks,
  orderRecheckRecords: workflowCounts.value.rechecks,
  decoction: decoctionCount.value,
  logistics: logisticsCount.value,
  logisticsPrint: logisPrintCount.value,
  logisticsTraces: logisticsInfoCount.value,
  logisticsUnreceivedFollowups: unreceivedFollowupCount.value,
  maintenanceExceptionLogs: exceptionLogCount.value,
  labelPrints: labelPrintCount.value,
  reportAuditPerformance: auditPerformanceCount.value,
  reportAuditPerformanceDetails: auditPerformanceDetailsCount.value,
  reportDecoctionPerformance: decoctionPerformanceCount.value,
  reportDecoctionPerformanceDetails: decoctionPerformanceDetailsCount.value,
  reportDispensePerformance: dispensePerformanceCount.value,
  reportDispensePerformanceDetails: dispensePerformanceDetailsCount.value,
  reportHerbDosage: herbDosageCount.value,
  reportInstitutionHerbReconciliation: institutionHerbReconciliationCount.value,
  reportPrescriptionHerbDetails: prescriptionHerbDetailsCount.value,
  reportRecheckPerformance: recheckPerformanceCount.value,
  reportRecheckPerformanceDetails: recheckPerformanceDetailsCount.value,
  reportInstitutionPrescriptionCounts: institutionPrescriptionCountsCount.value,
  reportPrescriptionReconciliation: prescriptionReconciliationCount.value,
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
  if (componentKey === 'auditPerformance') auditPerformanceActivationKey.value += 1;
  if (componentKey === 'auditPerformanceDetails') auditPerformanceDetailsActivationKey.value += 1;
  if (componentKey === 'decoctionPerformance') decoctionPerformanceActivationKey.value += 1;
  if (componentKey === 'decoctionPerformanceDetails') decoctionPerformanceDetailsActivationKey.value += 1;
  if (componentKey === 'dispensePerformance') dispensePerformanceActivationKey.value += 1;
  if (componentKey === 'dispensePerformanceDetails') dispensePerformanceDetailsActivationKey.value += 1;
  if (componentKey === 'herbDosage') herbDosageActivationKey.value += 1;
  if (componentKey === 'institutionHerbReconciliation') institutionHerbReconciliationActivationKey.value += 1;
  if (componentKey === 'prescriptionHerbDetails') prescriptionHerbDetailsActivationKey.value += 1;
  if (componentKey === 'recheckPerformance') recheckPerformanceActivationKey.value += 1;
  if (componentKey === 'recheckPerformanceDetails') recheckPerformanceDetailsActivationKey.value += 1;
  if (componentKey === 'institutionPrescriptionCounts') institutionPrescriptionCountsActivationKey.value += 1;
  if (componentKey === 'ops') opsActivationKey.value += 1;
  if (componentKey === 'observability') observabilityActivationKey.value += 1;
  if (componentKey === 'integration') integrationActivationKey.value += 1;
  if (componentKey === 'logisticsPrint') logisPrintActivationKey.value += 1;
  if (componentKey === 'logistics') logisticsActivationKey.value += 1;
  if (componentKey === 'logisticsInfo') logisticsInfoActivationKey.value += 1;
  if (componentKey === 'logisticsUnreceivedFollowups') unreceivedFollowupActivationKey.value += 1;
  if (componentKey === 'exceptionLogs') exceptionLogActivationKey.value += 1;
  if (componentKey === 'labelPrints') labelPrintActivationKey.value += 1;
  if (componentKey === 'prescriptionReconciliation') prescriptionReconciliationActivationKey.value += 1;
  if (componentKey === 'decoction') decoctionActivationKey.value += 1;
  if (componentKey === 'addressModify') addressModifyActivationKey.value += 1;
  if (componentKey === 'prescriptionModify') prescriptionModifyActivationKey.value += 1;
  if (componentKey === 'orderManageAction') orderManageActionActivationKey.value += 1;
  if (componentKey === 'prescriptionReprint') prescriptionReprintActivationKey.value += 1;
  if (componentKey === 'manualProcess') manualProcessActivationKey.value += 1;
  if (componentKey === 'orderWarehouse') orderWarehouseActivationKey.value += 1;
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
  if (componentKey === 'auditPerformance') {
    await auditPerformanceRef.value?.refreshAuditPerformance();
    return;
  }
  if (componentKey === 'auditPerformanceDetails') {
    await auditPerformanceDetailsRef.value?.refreshAuditPerformanceDetails();
    return;
  }
  if (componentKey === 'decoctionPerformance') {
    await decoctionPerformanceRef.value?.refreshDecoctionPerformance();
    return;
  }
  if (componentKey === 'decoctionPerformanceDetails') {
    await decoctionPerformanceDetailsRef.value?.refreshDecoctionPerformanceDetails();
    return;
  }
  if (componentKey === 'dispensePerformance') {
    await dispensePerformanceRef.value?.refreshDispensePerformance();
    return;
  }
  if (componentKey === 'dispensePerformanceDetails') {
    await dispensePerformanceDetailsRef.value?.refreshDispensePerformanceDetails();
    return;
  }
  if (componentKey === 'herbDosage') {
    await herbDosageRef.value?.refreshHerbDosage();
    return;
  }
  if (componentKey === 'institutionHerbReconciliation') {
    await institutionHerbReconciliationRef.value?.refreshInstitutionHerbReconciliation();
    return;
  }
  if (componentKey === 'prescriptionHerbDetails') {
    await prescriptionHerbDetailsRef.value?.refreshPrescriptionHerbDetails();
    return;
  }
  if (componentKey === 'recheckPerformance') {
    await recheckPerformanceRef.value?.refreshRecheckPerformance();
    return;
  }
  if (componentKey === 'recheckPerformanceDetails') {
    await recheckPerformanceDetailsRef.value?.refreshRecheckPerformanceDetails();
    return;
  }
  if (componentKey === 'institutionPrescriptionCounts') {
    await institutionPrescriptionCountsRef.value?.refreshInstitutionPrescriptionCounts();
    return;
  }
  if (componentKey === 'prescriptionReconciliation') {
    await prescriptionReconciliationRef.value?.refreshPrescriptionReconciliation();
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
  if (componentKey === 'logisticsPrint') {
    await logisPrintRef.value?.refreshLogisPrints();
    return;
  }
  if (componentKey === 'logisticsInfo') {
    await logisticsInfoRef.value?.refreshLogisticsInfos();
    return;
  }
  if (componentKey === 'logisticsUnreceivedFollowups') {
    await unreceivedFollowupRef.value?.refreshUnreceivedFollowups();
    return;
  }
  if (componentKey === 'exceptionLogs') {
    await exceptionLogRef.value?.refreshExceptionLogs();
    return;
  }
  if (componentKey === 'labelPrints') {
    await labelPrintRef.value?.refreshLabelPrints();
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
  if (componentKey === 'orderManageAction') {
    await orderManageActionRef.value?.refreshOrderManageActions();
    return;
  }
  if (componentKey === 'prescriptionReprint') {
    await prescriptionReprintRef.value?.refreshPrescriptionReprints();
    return;
  }
  if (componentKey === 'manualProcess') {
    await manualProcessRef.value?.refreshManualProcessOrders();
    return;
  }
  if (componentKey === 'orderWarehouse') {
    await orderWarehouseRef.value?.refreshOrderWarehouses();
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
    :menu-items="navigationItems"
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

    <AuditPerformance
      v-show="currentComponentKey === 'auditPerformance'"
      ref="auditPerformanceRef"
      :active="currentComponentKey === 'auditPerformance'"
      :activation-key="auditPerformanceActivationKey"
      @count-changed="auditPerformanceCount = $event"
      @notice="showNotice"
    />

    <AuditPerformanceDetails
      v-show="currentComponentKey === 'auditPerformanceDetails'"
      ref="auditPerformanceDetailsRef"
      :active="currentComponentKey === 'auditPerformanceDetails'"
      :activation-key="auditPerformanceDetailsActivationKey"
      @count-changed="auditPerformanceDetailsCount = $event"
      @notice="showNotice"
    />

    <DecoctionPerformance
      v-show="currentComponentKey === 'decoctionPerformance'"
      ref="decoctionPerformanceRef"
      :active="currentComponentKey === 'decoctionPerformance'"
      :activation-key="decoctionPerformanceActivationKey"
      @count-changed="decoctionPerformanceCount = $event"
      @notice="showNotice"
    />

    <DecoctionPerformanceDetails
      v-show="currentComponentKey === 'decoctionPerformanceDetails'"
      ref="decoctionPerformanceDetailsRef"
      :active="currentComponentKey === 'decoctionPerformanceDetails'"
      :activation-key="decoctionPerformanceDetailsActivationKey"
      @count-changed="decoctionPerformanceDetailsCount = $event"
      @notice="showNotice"
    />

    <DispensePerformance
      v-show="currentComponentKey === 'dispensePerformance'"
      ref="dispensePerformanceRef"
      :active="currentComponentKey === 'dispensePerformance'"
      :activation-key="dispensePerformanceActivationKey"
      @count-changed="dispensePerformanceCount = $event"
      @notice="showNotice"
    />

    <DispensePerformanceDetails
      v-show="currentComponentKey === 'dispensePerformanceDetails'"
      ref="dispensePerformanceDetailsRef"
      :active="currentComponentKey === 'dispensePerformanceDetails'"
      :activation-key="dispensePerformanceDetailsActivationKey"
      @count-changed="dispensePerformanceDetailsCount = $event"
      @notice="showNotice"
    />

    <HerbDosage
      v-show="currentComponentKey === 'herbDosage'"
      ref="herbDosageRef"
      :active="currentComponentKey === 'herbDosage'"
      :activation-key="herbDosageActivationKey"
      @count-changed="herbDosageCount = $event"
      @notice="showNotice"
    />

    <InstitutionHerbReconciliation
      v-show="currentComponentKey === 'institutionHerbReconciliation'"
      ref="institutionHerbReconciliationRef"
      :active="currentComponentKey === 'institutionHerbReconciliation'"
      :activation-key="institutionHerbReconciliationActivationKey"
      @count-changed="institutionHerbReconciliationCount = $event"
      @notice="showNotice"
    />

    <PrescriptionHerbDetails
      v-show="currentComponentKey === 'prescriptionHerbDetails'"
      ref="prescriptionHerbDetailsRef"
      :active="currentComponentKey === 'prescriptionHerbDetails'"
      :activation-key="prescriptionHerbDetailsActivationKey"
      @count-changed="prescriptionHerbDetailsCount = $event"
      @notice="showNotice"
    />

    <RecheckPerformance
      v-show="currentComponentKey === 'recheckPerformance'"
      ref="recheckPerformanceRef"
      :active="currentComponentKey === 'recheckPerformance'"
      :activation-key="recheckPerformanceActivationKey"
      @count-changed="recheckPerformanceCount = $event"
      @notice="showNotice"
    />

    <RecheckPerformanceDetails
      v-show="currentComponentKey === 'recheckPerformanceDetails'"
      ref="recheckPerformanceDetailsRef"
      :active="currentComponentKey === 'recheckPerformanceDetails'"
      :activation-key="recheckPerformanceDetailsActivationKey"
      @count-changed="recheckPerformanceDetailsCount = $event"
      @notice="showNotice"
    />

    <PrescriptionReconciliation
      v-show="currentComponentKey === 'prescriptionReconciliation'"
      ref="prescriptionReconciliationRef"
      :active="currentComponentKey === 'prescriptionReconciliation'"
      :activation-key="prescriptionReconciliationActivationKey"
      @count-changed="prescriptionReconciliationCount = $event"
      @notice="showNotice"
    />

    <InstitutionPrescriptionCounts
      v-show="currentComponentKey === 'institutionPrescriptionCounts'"
      ref="institutionPrescriptionCountsRef"
      :active="currentComponentKey === 'institutionPrescriptionCounts'"
      :activation-key="institutionPrescriptionCountsActivationKey"
      @count-changed="institutionPrescriptionCountsCount = $event"
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

    <LogisPrint
      v-show="currentComponentKey === 'logisticsPrint'"
      ref="logisPrintRef"
      :active="currentComponentKey === 'logisticsPrint'"
      :activation-key="logisPrintActivationKey"
      @count-changed="logisPrintCount = $event"
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

    <OrderManageAction
      v-else-if="currentComponentKey === 'orderManageAction'"
      ref="orderManageActionRef"
      active
      :activation-key="orderManageActionActivationKey"
      @notice="showNotice"
    />

    <PrescriptionReprint
      v-else-if="currentComponentKey === 'prescriptionReprint'"
      ref="prescriptionReprintRef"
      active
      :activation-key="prescriptionReprintActivationKey"
      @notice="showNotice"
    />

    <ManualProcess
      v-else-if="currentComponentKey === 'manualProcess'"
      ref="manualProcessRef"
      active
      :activation-key="manualProcessActivationKey"
      @notice="showNotice"
    />

    <OrderWarehouse
      v-else-if="currentComponentKey === 'orderWarehouse'"
      ref="orderWarehouseRef"
      active
      :activation-key="orderWarehouseActivationKey"
      @notice="showNotice"
    />

    <OrderReceipt
      v-else-if="currentComponentKey === 'orderReceipt'"
      ref="orderReceiptRef"
      active
      :activation-key="orderReceiptActivationKey"
      @notice="showNotice"
    />

    <LogisticsInfo
      v-else-if="currentComponentKey === 'logisticsInfo'"
      ref="logisticsInfoRef"
      active
      :activation-key="logisticsInfoActivationKey"
      @count-changed="logisticsInfoCount = $event"
      @notice="showNotice"
    />

    <UnreceivedFollowup
      v-else-if="currentComponentKey === 'logisticsUnreceivedFollowups'"
      ref="unreceivedFollowupRef"
      v-model:operation-operator="operationOperator"
      active
      :activation-key="unreceivedFollowupActivationKey"
      @count-changed="unreceivedFollowupCount = $event"
      @notice="showNotice"
    />

    <ExceptionLogList
      v-else-if="currentComponentKey === 'exceptionLogs'"
      ref="exceptionLogRef"
      v-model:operation-operator="operationOperator"
      active
      :activation-key="exceptionLogActivationKey"
      @count-changed="exceptionLogCount = $event"
      @notice="showNotice"
    />

    <LabelPrint
      v-else-if="currentComponentKey === 'labelPrints'"
      ref="labelPrintRef"
      active
      :activation-key="labelPrintActivationKey"
      @count-changed="labelPrintCount = $event"
      @notice="showNotice"
    />

    <PendingMenuPage v-else :item="currentRouteItem" />
  </AppLayout>
</template>
