<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from './app/AppLayout.vue';
import type { LayoutTab } from './app/AppLayout.vue';
import { logoutAdmin, restoreAdminSession } from './api/auth';
import type { AdminUserSession } from './api/adminSession';
import {
  canAccessRoute,
  isViewKey,
  menuItems,
  routeByKey,
  type AppRouteItem,
  type ImplementedViewKey,
  type ViewKey,
} from './app/views';
import DashboardHome from './features/dashboard/DashboardHome.vue';
import LoginView from './features/auth/LoginView.vue';
import DecoctionWorkspace from './features/decoction/DecoctionWorkspace.vue';
import HerbAreaManage from './features/drug/HerbAreaManage.vue';
import HerbImport from './features/drug/HerbImport.vue';
import HerbIndexList from './features/drug/HerbIndexList.vue';
import HerbIndexImport from './features/drug/HerbIndexImport.vue';
import HerbIndexOperationLog from './features/drug/HerbIndexOperationLog.vue';
import HerbList from './features/drug/HerbList.vue';
import ExportTaskCenter from './features/exports/ExportTaskCenter.vue';
import IntegrationConsole from './features/integration/IntegrationConsole.vue';
import ApiInfoList from './features/institution/ApiInfoList.vue';
import ApiPermissionList from './features/institution/ApiPermissionList.vue';
import IpWhitelist from './features/institution/IpWhitelist.vue';
import InstitutionApps from './features/institution/InstitutionApps.vue';
import InstitutionList from './features/institution/InstitutionList.vue';
import AddressCost from './features/logistics/AddressCost.vue';
import LogisSpecialRule from './features/logistics/LogisSpecialRule.vue';
import LogisPrint from './features/logistics/LogisPrint.vue';
import LabelPrint from './features/label/LabelPrint.vue';
import LabelTemplate from './features/label/LabelTemplate.vue';
import SmsTemplate from './features/sms/SmsTemplate.vue';
import SingleSmsSend from './features/sms/SingleSmsSend.vue';
import SmsRecordList from './features/sms/SmsRecordList.vue';
import LogisticsFulfillment from './features/logistics/LogisticsFulfillment.vue';
import LogisticsInfo from './features/logistics/LogisticsInfo.vue';
import OrderMergeList from './features/logistics/OrderMergeList.vue';
import UnreceivedFollowup from './features/logistics/UnreceivedFollowup.vue';
import AddressModify from './features/orders/AddressModify.vue';
import ManualProcess from './features/orders/ManualProcess.vue';
import OrderInterceptRule from './features/orders/OrderInterceptRule.vue';
import OrderCenter from './features/orders/OrderCenter.vue';
import OrderWarehouse from './features/orders/OrderWarehouse.vue';
import OrderManageAction from './features/orders/OrderManageAction.vue';
import OrderReceipt from './features/orders/OrderReceipt.vue';
import OrderReviewRecords from './features/orders/OrderReviewRecords.vue';
import PrescriptionModify from './features/orders/PrescriptionModify.vue';
import PrescriptionReprint from './features/orders/PrescriptionReprint.vue';
import RecheckRecords from './features/orders/RecheckRecords.vue';
import ExceptionLogList from './features/ops/ExceptionLogList.vue';
import OrderObservabilityPanel from './features/ops/OrderObservabilityPanel.vue';
import OpsConsole from './features/ops/OpsConsole.vue';
import ProblemRegistration from './features/ops/ProblemRegistration.vue';
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
import LogisticsPerformance from './features/reports/LogisticsPerformance.vue';
import LogisticsPerformanceDetails from './features/reports/LogisticsPerformanceDetails.vue';
import PrescriptionHerbDetails from './features/reports/PrescriptionHerbDetails.vue';
import PrescriptionReconciliation from './features/reports/PrescriptionReconciliation.vue';
import RecheckPerformance from './features/reports/RecheckPerformance.vue';
import RecheckPerformanceDetails from './features/reports/RecheckPerformanceDetails.vue';
import ReportOverview from './features/reports/ReportOverview.vue';
import DecoctCenterConfig from './features/settings/DecoctCenterConfig.vue';
import DictList from './features/settings/DictList.vue';
import OperatorManage from './features/settings/OperatorManage.vue';
import SystemConfig from './features/settings/SystemConfig.vue';
import MenuRegistry from './features/system/MenuRegistry.vue';
import RoleManage from './features/system/RoleManage.vue';
import DispensePrintWorkspace from './features/workflow/DispensePrintWorkspace.vue';
import RecheckScanWorkspace from './features/workflow/RecheckScanWorkspace.vue';
import WorkflowTasks from './features/workflow/WorkflowTasks.vue';

type NoticeTone = 'info' | 'success' | 'error';
type WorkflowCounts = { reviews: number; dispenses: number; rechecks: number };
type WorkflowViewKey = Extract<ViewKey, 'reviews' | 'rechecks'>;
type RecheckScanMode = 'single' | 'multi';

const route = useRoute();
const router = useRouter();

const operationOperator = ref('admin');
const adminSession = ref<AdminUserSession | null>(null);
const authRestoring = ref(true);
const workflowCounts = ref<WorkflowCounts>({ reviews: 0, dispenses: 0, rechecks: 0 });
const dashboardHomeRef = ref<InstanceType<typeof DashboardHome> | null>(null);
const workflowTasksRef = ref<InstanceType<typeof WorkflowTasks> | null>(null);
const orderCenterRef = ref<InstanceType<typeof OrderCenter> | null>(null);
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
const logisticsPerformanceRef = ref<InstanceType<typeof LogisticsPerformance> | null>(null);
const logisticsPerformanceDetailsRef = ref<InstanceType<typeof LogisticsPerformanceDetails> | null>(null);
const prescriptionHerbDetailsRef = ref<InstanceType<typeof PrescriptionHerbDetails> | null>(null);
const recheckPerformanceRef = ref<InstanceType<typeof RecheckPerformance> | null>(null);
const recheckPerformanceDetailsRef = ref<InstanceType<typeof RecheckPerformanceDetails> | null>(null);
const institutionPrescriptionCountsRef = ref<InstanceType<typeof InstitutionPrescriptionCounts> | null>(null);
const dictListRef = ref<InstanceType<typeof DictList> | null>(null);
const systemConfigRef = ref<InstanceType<typeof SystemConfig> | null>(null);
const decoctCenterRef = ref<InstanceType<typeof DecoctCenterConfig> | null>(null);
const operatorManageRef = ref<InstanceType<typeof OperatorManage> | null>(null);
const menuRegistryRef = ref<InstanceType<typeof MenuRegistry> | null>(null);
const roleManageRef = ref<InstanceType<typeof RoleManage> | null>(null);
const institutionListRef = ref<InstanceType<typeof InstitutionList> | null>(null);
const institutionAppsRef = ref<InstanceType<typeof InstitutionApps> | null>(null);
const institutionIpWhitelistRef = ref<InstanceType<typeof IpWhitelist> | null>(null);
const institutionApisRef = ref<InstanceType<typeof ApiInfoList> | null>(null);
const institutionApiPermissionsRef = ref<InstanceType<typeof ApiPermissionList> | null>(null);
const opsConsoleRef = ref<InstanceType<typeof OpsConsole> | null>(null);
const portalLookupRef = ref<InstanceType<typeof PortalLookup> | null>(null);
const integrationConsoleRef = ref<InstanceType<typeof IntegrationConsole> | null>(null);
const exportTaskCenterRef = ref<InstanceType<typeof ExportTaskCenter> | null>(null);
const addressCostRef = ref<InstanceType<typeof AddressCost> | null>(null);
const logisSpecialRuleRef = ref<InstanceType<typeof LogisSpecialRule> | null>(null);
const logisPrintRef = ref<InstanceType<typeof LogisPrint> | null>(null);
const logisticsFulfillmentRef = ref<InstanceType<typeof LogisticsFulfillment> | null>(null);
const logisticsInfoRef = ref<InstanceType<typeof LogisticsInfo> | null>(null);
const orderMergeListRef = ref<InstanceType<typeof OrderMergeList> | null>(null);
const unreceivedFollowupRef = ref<InstanceType<typeof UnreceivedFollowup> | null>(null);
const exceptionLogRef = ref<InstanceType<typeof ExceptionLogList> | null>(null);
const problemRegistrationRef = ref<InstanceType<typeof ProblemRegistration> | null>(null);
const labelPrintRef = ref<InstanceType<typeof LabelPrint> | null>(null);
const labelTemplateRef = ref<InstanceType<typeof LabelTemplate> | null>(null);
const smsTemplateRef = ref<InstanceType<typeof SmsTemplate> | null>(null);
const singleSmsSendRef = ref<InstanceType<typeof SingleSmsSend> | null>(null);
const smsRecordListRef = ref<InstanceType<typeof SmsRecordList> | null>(null);
const herbListRef = ref<InstanceType<typeof HerbList> | null>(null);
const herbImportRef = ref<InstanceType<typeof HerbImport> | null>(null);
const herbAreaManageRef = ref<InstanceType<typeof HerbAreaManage> | null>(null);
const herbIndexListRef = ref<InstanceType<typeof HerbIndexList> | null>(null);
const herbIndexImportRef = ref<InstanceType<typeof HerbIndexImport> | null>(null);
const herbIndexOperationLogRef = ref<InstanceType<typeof HerbIndexOperationLog> | null>(null);
const decoctionWorkspaceRef = ref<InstanceType<typeof DecoctionWorkspace> | null>(null);
const orderObservabilityRef = ref<InstanceType<typeof OrderObservabilityPanel> | null>(null);
const prescriptionReconciliationRef = ref<InstanceType<typeof PrescriptionReconciliation> | null>(null);
const addressModifyRef = ref<InstanceType<typeof AddressModify> | null>(null);
const prescriptionModifyRef = ref<InstanceType<typeof PrescriptionModify> | null>(null);
const orderManageActionRef = ref<InstanceType<typeof OrderManageAction> | null>(null);
const prescriptionReprintRef = ref<InstanceType<typeof PrescriptionReprint> | null>(null);
const orderReviewRecordsRef = ref<InstanceType<typeof OrderReviewRecords> | null>(null);
const recheckRecordsRef = ref<InstanceType<typeof RecheckRecords> | null>(null);
const orderInterceptRuleRef = ref<InstanceType<typeof OrderInterceptRule> | null>(null);
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
const logisticsPerformanceCount = ref(0);
const logisticsPerformanceDetailsCount = ref(0);
const prescriptionHerbDetailsCount = ref(0);
const recheckPerformanceCount = ref(0);
const recheckPerformanceDetailsCount = ref(0);
const institutionPrescriptionCountsCount = ref(0);
const dictListCount = ref(0);
const systemConfigCount = ref(0);
const decoctCenterCount = ref(0);
const operatorManageCount = ref(0);
const menuRegistryCount = ref(0);
const roleManageCount = ref(0);
const institutionListCount = ref(0);
const institutionAppsCount = ref(0);
const institutionIpWhitelistCount = ref(0);
const institutionApisCount = ref(0);
const institutionApiPermissionsCount = ref(0);
const reportActivationKey = ref(0);
const auditPerformanceActivationKey = ref(0);
const auditPerformanceDetailsActivationKey = ref(0);
const decoctionPerformanceActivationKey = ref(0);
const decoctionPerformanceDetailsActivationKey = ref(0);
const dispensePerformanceActivationKey = ref(0);
const dispensePerformanceDetailsActivationKey = ref(0);
const herbDosageActivationKey = ref(0);
const institutionHerbReconciliationActivationKey = ref(0);
const logisticsPerformanceActivationKey = ref(0);
const logisticsPerformanceDetailsActivationKey = ref(0);
const prescriptionHerbDetailsActivationKey = ref(0);
const recheckPerformanceActivationKey = ref(0);
const recheckPerformanceDetailsActivationKey = ref(0);
const institutionPrescriptionCountsActivationKey = ref(0);
const dictListActivationKey = ref(0);
const systemConfigActivationKey = ref(0);
const decoctCenterActivationKey = ref(0);
const operatorManageActivationKey = ref(0);
const menuRegistryActivationKey = ref(0);
const roleManageActivationKey = ref(0);
const institutionListActivationKey = ref(0);
const institutionAppsActivationKey = ref(0);
const institutionIpWhitelistActivationKey = ref(0);
const institutionApisActivationKey = ref(0);
const institutionApiPermissionsActivationKey = ref(0);
const opsCount = ref(0);
const integrationCount = ref(0);
const exportTaskCount = ref(0);
const addressCostCount = ref(0);
const logisSpecialRuleCount = ref(0);
const logisPrintCount = ref(0);
const logisticsCount = ref(0);
const logisticsInfoCount = ref(0);
const orderMergeCount = ref(0);
const unreceivedFollowupCount = ref(0);
const exceptionLogCount = ref(0);
const problemRegistrationCount = ref(0);
const labelPrintCount = ref(0);
const labelTemplateCount = ref(0);
const smsTemplateCount = ref(0);
const smsRecordCount = ref(0);
const herbListCount = ref(0);
const herbImportCount = ref(0);
const herbAreaManageCount = ref(0);
const herbIndexListCount = ref(0);
const herbIndexImportCount = ref(0);
const herbIndexOperationLogCount = ref(0);
const prescriptionReconciliationCount = ref(0);
const orderReviewRecordsCount = ref(0);
const orderRecheckRecordsCount = ref(0);
const orderInterceptRuleCount = ref(0);
const decoctionCount = ref(0);
const decoctionCloudPrintCount = ref(0);
const observabilityCount = ref(0);
const opsActivationKey = ref(0);
const integrationActivationKey = ref(0);
const exportTaskActivationKey = ref(0);
const addressCostActivationKey = ref(0);
const logisSpecialRuleActivationKey = ref(0);
const logisPrintActivationKey = ref(0);
const logisticsActivationKey = ref(0);
const logisticsInfoActivationKey = ref(0);
const orderMergeActivationKey = ref(0);
const unreceivedFollowupActivationKey = ref(0);
const exceptionLogActivationKey = ref(0);
const problemRegistrationActivationKey = ref(0);
const labelPrintActivationKey = ref(0);
const labelTemplateActivationKey = ref(0);
const smsTemplateActivationKey = ref(0);
const singleSmsSendActivationKey = ref(0);
const smsRecordActivationKey = ref(0);
const herbListActivationKey = ref(0);
const herbImportActivationKey = ref(0);
const herbAreaManageActivationKey = ref(0);
const herbIndexListActivationKey = ref(0);
const herbIndexImportActivationKey = ref(0);
const herbIndexOperationLogActivationKey = ref(0);
const prescriptionReconciliationActivationKey = ref(0);
const decoctionActivationKey = ref(0);
const observabilityActivationKey = ref(0);
const addressModifyActivationKey = ref(0);
const prescriptionModifyActivationKey = ref(0);
const orderManageActionActivationKey = ref(0);
const prescriptionReprintActivationKey = ref(0);
const orderReviewRecordsActivationKey = ref(0);
const recheckRecordsActivationKey = ref(0);
const orderInterceptRuleActivationKey = ref(0);
const manualProcessActivationKey = ref(0);
const orderWarehouseActivationKey = ref(0);
const orderReceiptActivationKey = ref(0);
const notice = ref<{ tone: NoticeTone; text: string } | null>(null);
const openTabs = ref<ViewKey[]>([]);

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

function handleAuthExpired() {
  adminSession.value = null;
  notice.value = null;
  openTabs.value = [];
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
const currentComponentKey = computed<ImplementedViewKey | undefined>(() => currentRouteItem.value.componentKey);
const workflowRouteKey = computed<WorkflowViewKey | null>(() => {
  const key = currentComponentKey.value;
  if (key === 'rechecks') return key;
  return null;
});
const recheckScanMode = computed<RecheckScanMode>(() => (activeView.value === 'orderRechecksMulti' ? 'multi' : 'single'));
const homePath = routeByKey.dashboard.path;
const sessionPermissions = computed(() => new Set(adminSession.value?.permissions ?? []));
const canManageSystem = computed(() => Boolean(adminSession.value?.tenantWide) && sessionPermissions.value.has('system:write'));

const layoutTabs = computed<LayoutTab[]>(() => [
  { key: 'dashboard', label: '首页', closable: false, path: routeByKey.dashboard.path },
  ...openTabs.value.filter((key) => canAccessRoute(routeByKey[key], sessionPermissions.value)).map((key) => ({
    key,
    label: routeByKey[key].label,
    closable: true,
    path: routeByKey[key].path,
  })),
]);
const navigationItems = computed<readonly AppRouteItem[]>(() => [
  ...menuItems.filter((item) => item.key !== 'institutionApps' && canAccessRoute(item, sessionPermissions.value)),
]);
const menuCounts = computed<Partial<Record<ViewKey, number>>>(() => ({
  reviews: orderReviewRecordsCount.value,
  dispenses: workflowCounts.value.dispenses,
  rechecks: workflowCounts.value.rechecks,
  orderRechecksMulti: workflowCounts.value.rechecks,
  orderRecheckRecords: orderRecheckRecordsCount.value,
  decoction: decoctionCount.value,
  decoctionPdaPrinterRelations: decoctionCount.value,
  decoctionPrescriptionBindings: decoctionCount.value,
  decoctionCloudPrintRecords: decoctionCloudPrintCount.value,
  logisticsAddressCosts: addressCostCount.value,
  logisticsSpecialRules: logisSpecialRuleCount.value,
  logistics: logisticsCount.value,
  logisticsPrint: logisPrintCount.value,
  logisticsMerges: orderMergeCount.value,
  logisticsTraces: logisticsInfoCount.value,
  logisticsUnreceivedFollowups: unreceivedFollowupCount.value,
  institutionList: institutionListCount.value,
  institutionApps: institutionAppsCount.value,
  institutionIpWhitelist: institutionIpWhitelistCount.value,
  institutionApis: institutionApisCount.value,
  institutionApiPermissions: institutionApiPermissionsCount.value,
  maintenanceExceptionLogs: exceptionLogCount.value,
  systemUsers: operatorManageCount.value,
  systemRoles: roleManageCount.value,
  systemMenus: menuRegistryCount.value,
  settingOperators: operatorManageCount.value,
  labelTemplates: labelTemplateCount.value,
  labelPrints: labelPrintCount.value,
  smsTemplates: smsTemplateCount.value,
  smsRecords: smsRecordCount.value,
  drugHerbs: herbListCount.value,
  drugHerbImports: herbImportCount.value,
  drugHerbAreas: herbAreaManageCount.value,
  drugHerbIndexes: herbIndexListCount.value,
  drugHerbIndexImports: herbIndexImportCount.value,
  drugIndexOperationLogs: herbIndexOperationLogCount.value,
  reportAuditPerformance: auditPerformanceCount.value,
  reportAuditPerformanceDetails: auditPerformanceDetailsCount.value,
  reportDecoctionPerformance: decoctionPerformanceCount.value,
  reportDecoctionPerformanceDetails: decoctionPerformanceDetailsCount.value,
  reportDispensePerformance: dispensePerformanceCount.value,
  reportDispensePerformanceDetails: dispensePerformanceDetailsCount.value,
  reportHerbDosage: herbDosageCount.value,
  reportInstitutionHerbReconciliation: institutionHerbReconciliationCount.value,
  reportLogisticsPerformance: logisticsPerformanceCount.value,
  reportLogisticsPerformanceDetails: logisticsPerformanceDetailsCount.value,
  reportPrescriptionHerbDetails: prescriptionHerbDetailsCount.value,
  reportRecheckPerformance: recheckPerformanceCount.value,
  reportRecheckPerformanceDetails: recheckPerformanceDetailsCount.value,
  reportInstitutionPrescriptionCounts: institutionPrescriptionCountsCount.value,
  reportPrescriptionReconciliation: prescriptionReconciliationCount.value,
  settingDicts: dictListCount.value,
  settingSystemConfigs: systemConfigCount.value,
  settingDecoctCenters: decoctCenterCount.value,
  orderInterceptRules: orderInterceptRuleCount.value,
  maintenanceProblemRegistrations: problemRegistrationCount.value,
  reports: reportTotalOrders.value,
  integration: integrationCount.value,
  exportTasks: exportTaskCount.value,
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
  if (view !== 'dashboard' && canAccessRoute(routeByKey[view], sessionPermissions.value) && !openTabs.value.includes(view)) {
    openTabs.value = [...openTabs.value, view];
  }
}

watch(activeView, (view) => {
  ensureOpenTab(view);
}, { immediate: true });

watch([adminSession, activeView], ([session, view]) => {
  if (!session || canAccessRoute(routeByKey[view], new Set(session.permissions))) return;
  openTabs.value = openTabs.value.filter((key) => canAccessRoute(routeByKey[key], new Set(session.permissions)));
  void router.replace(routeByKey.dashboard.path);
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
  if (componentKey === 'logisticsPerformance') logisticsPerformanceActivationKey.value += 1;
  if (componentKey === 'logisticsPerformanceDetails') logisticsPerformanceDetailsActivationKey.value += 1;
  if (componentKey === 'prescriptionHerbDetails') prescriptionHerbDetailsActivationKey.value += 1;
  if (componentKey === 'recheckPerformance') recheckPerformanceActivationKey.value += 1;
  if (componentKey === 'recheckPerformanceDetails') recheckPerformanceDetailsActivationKey.value += 1;
  if (componentKey === 'institutionPrescriptionCounts') institutionPrescriptionCountsActivationKey.value += 1;
  if (componentKey === 'settingDicts') dictListActivationKey.value += 1;
  if (componentKey === 'settingSystemConfigs') systemConfigActivationKey.value += 1;
  if (componentKey === 'settingDecoctCenters') decoctCenterActivationKey.value += 1;
  if (componentKey === 'operatorManage') operatorManageActivationKey.value += 1;
  if (componentKey === 'menuRegistry') menuRegistryActivationKey.value += 1;
  if (componentKey === 'roleManage') roleManageActivationKey.value += 1;
  if (componentKey === 'institutionList') institutionListActivationKey.value += 1;
  if (componentKey === 'institutionApps') institutionAppsActivationKey.value += 1;
  if (componentKey === 'institutionIpWhitelist') institutionIpWhitelistActivationKey.value += 1;
  if (componentKey === 'institutionApis') institutionApisActivationKey.value += 1;
  if (componentKey === 'institutionApiPermissions') institutionApiPermissionsActivationKey.value += 1;
  if (componentKey === 'ops') opsActivationKey.value += 1;
  if (componentKey === 'observability') observabilityActivationKey.value += 1;
  if (componentKey === 'integration') integrationActivationKey.value += 1;
  if (componentKey === 'exportTasks') exportTaskActivationKey.value += 1;
  if (componentKey === 'logisticsAddressCosts') addressCostActivationKey.value += 1;
  if (componentKey === 'logisticsSpecialRules') logisSpecialRuleActivationKey.value += 1;
  if (componentKey === 'logisticsPrint') logisPrintActivationKey.value += 1;
  if (componentKey === 'logistics') logisticsActivationKey.value += 1;
  if (componentKey === 'logisticsInfo') logisticsInfoActivationKey.value += 1;
  if (componentKey === 'logisticsMerges') orderMergeActivationKey.value += 1;
  if (componentKey === 'logisticsUnreceivedFollowups') unreceivedFollowupActivationKey.value += 1;
  if (componentKey === 'exceptionLogs') exceptionLogActivationKey.value += 1;
  if (componentKey === 'problemRegistrations') problemRegistrationActivationKey.value += 1;
  if (componentKey === 'labelTemplates') labelTemplateActivationKey.value += 1;
  if (componentKey === 'labelPrints') labelPrintActivationKey.value += 1;
  if (componentKey === 'smsTemplates') smsTemplateActivationKey.value += 1;
  if (componentKey === 'smsSendSingle') singleSmsSendActivationKey.value += 1;
  if (componentKey === 'smsRecords') smsRecordActivationKey.value += 1;
  if (componentKey === 'drugHerbs') herbListActivationKey.value += 1;
  if (componentKey === 'drugHerbImports') herbImportActivationKey.value += 1;
  if (componentKey === 'drugHerbAreas') herbAreaManageActivationKey.value += 1;
  if (componentKey === 'drugHerbIndexes') herbIndexListActivationKey.value += 1;
  if (componentKey === 'drugHerbIndexImports') herbIndexImportActivationKey.value += 1;
  if (componentKey === 'drugIndexOperationLogs') herbIndexOperationLogActivationKey.value += 1;
  if (componentKey === 'prescriptionReconciliation') prescriptionReconciliationActivationKey.value += 1;
  if (componentKey === 'decoction') decoctionActivationKey.value += 1;
  if (componentKey === 'addressModify') addressModifyActivationKey.value += 1;
  if (componentKey === 'prescriptionModify') prescriptionModifyActivationKey.value += 1;
  if (componentKey === 'orderManageAction') orderManageActionActivationKey.value += 1;
  if (componentKey === 'prescriptionReprint') prescriptionReprintActivationKey.value += 1;
  if (componentKey === 'orderReviewRecords') orderReviewRecordsActivationKey.value += 1;
  if (componentKey === 'recheckRecords') recheckRecordsActivationKey.value += 1;
  if (componentKey === 'orderInterceptRules') orderInterceptRuleActivationKey.value += 1;
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
  if (componentKey === 'orders') {
    await orderCenterRef.value?.refreshOrders();
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
  if (componentKey === 'exportTasks') {
    await exportTaskCenterRef.value?.refreshExportTasks();
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
  if (componentKey === 'logisticsPerformance') {
    await logisticsPerformanceRef.value?.refreshLogisticsPerformance();
    return;
  }
  if (componentKey === 'logisticsPerformanceDetails') {
    await logisticsPerformanceDetailsRef.value?.refreshLogisticsPerformanceDetails();
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
  if (componentKey === 'settingDicts') {
    await dictListRef.value?.refreshDictTypes();
    return;
  }
  if (componentKey === 'settingSystemConfigs') {
    await systemConfigRef.value?.refreshSystemConfigs();
    return;
  }
  if (componentKey === 'settingDecoctCenters') {
    await decoctCenterRef.value?.refreshDecoctCenters();
    return;
  }
  if (componentKey === 'operatorManage') {
    await operatorManageRef.value?.refreshOperators();
    return;
  }
  if (componentKey === 'menuRegistry') {
    menuRegistryRef.value?.refreshMenus();
    return;
  }
  if (componentKey === 'roleManage') {
    await roleManageRef.value?.refreshRoles();
    return;
  }
  if (componentKey === 'institutionList') {
    await institutionListRef.value?.refreshInstitutions();
    return;
  }
  if (componentKey === 'institutionApps') {
    await institutionAppsRef.value?.refreshInstitutionApps();
    return;
  }
  if (componentKey === 'institutionIpWhitelist') {
    await institutionIpWhitelistRef.value?.refreshIpWhitelists();
    return;
  }
  if (componentKey === 'institutionApis') {
    await institutionApisRef.value?.refreshInstitutionApis();
    return;
  }
  if (componentKey === 'institutionApiPermissions') {
    await institutionApiPermissionsRef.value?.refreshApiPermissions();
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
  if (componentKey === 'logisticsSpecialRules') {
    await logisSpecialRuleRef.value?.refreshLogisticsSpecialRules();
    return;
  }
  if (componentKey === 'logisticsAddressCosts') {
    await addressCostRef.value?.refreshAddressCosts();
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
  if (componentKey === 'logisticsMerges') {
    await orderMergeListRef.value?.refreshOrderMerges();
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
  if (componentKey === 'problemRegistrations') {
    await problemRegistrationRef.value?.refreshProblemRegistrations();
    return;
  }
  if (componentKey === 'labelTemplates') {
    await labelTemplateRef.value?.refreshLabelTemplates();
    return;
  }
  if (componentKey === 'labelPrints') {
    await labelPrintRef.value?.refreshLabelPrints();
    return;
  }
  if (componentKey === 'smsTemplates') {
    await smsTemplateRef.value?.refreshSmsTemplates();
    return;
  }
  if (componentKey === 'smsSendSingle') {
    await singleSmsSendRef.value?.refreshSingleSmsSend();
    return;
  }
  if (componentKey === 'smsRecords') {
    await smsRecordListRef.value?.refreshSmsRecords();
    return;
  }
  if (componentKey === 'drugHerbs') {
    await herbListRef.value?.refreshHerbs();
    return;
  }
  if (componentKey === 'drugHerbImports') {
    herbImportRef.value?.resetImport();
    return;
  }
  if (componentKey === 'drugHerbAreas') {
    await herbAreaManageRef.value?.refreshHerbAreas();
    return;
  }
  if (componentKey === 'drugHerbIndexes') {
    await herbIndexListRef.value?.refreshHerbIndexes();
    return;
  }
  if (componentKey === 'drugHerbIndexImports') {
    herbIndexImportRef.value?.resetImport();
    return;
  }
  if (componentKey === 'drugIndexOperationLogs') {
    await herbIndexOperationLogRef.value?.refreshHerbIndexOperationLogs();
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
  if (componentKey === 'orderReviewRecords') {
    await orderReviewRecordsRef.value?.refreshOrderReviews();
    return;
  }
  if (componentKey === 'recheckRecords') {
    await recheckRecordsRef.value?.refreshOrderRechecks();
    return;
  }
  if (componentKey === 'orderInterceptRules') {
    await orderInterceptRuleRef.value?.refreshOrderInterceptRules();
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
  showNotice('info', `${currentRouteItem.value.label} 当前未配置页面刷新方法`);
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

    <LogisticsPerformance
      v-show="currentComponentKey === 'logisticsPerformance'"
      ref="logisticsPerformanceRef"
      :active="currentComponentKey === 'logisticsPerformance'"
      :activation-key="logisticsPerformanceActivationKey"
      @count-changed="logisticsPerformanceCount = $event"
      @notice="showNotice"
    />

    <LogisticsPerformanceDetails
      v-show="currentComponentKey === 'logisticsPerformanceDetails'"
      ref="logisticsPerformanceDetailsRef"
      :active="currentComponentKey === 'logisticsPerformanceDetails'"
      :activation-key="logisticsPerformanceDetailsActivationKey"
      @count-changed="logisticsPerformanceDetailsCount = $event"
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

    <OperatorManage
      v-show="currentComponentKey === 'operatorManage'"
      ref="operatorManageRef"
      :active="currentComponentKey === 'operatorManage'"
      :activation-key="operatorManageActivationKey"
      :can-manage="canManageSystem"
      :current-user-id="adminSession?.userId ?? ''"
      @count-changed="operatorManageCount = $event"
      @notice="showNotice"
    />

    <MenuRegistry
      v-show="currentComponentKey === 'menuRegistry'"
      ref="menuRegistryRef"
      :active="currentComponentKey === 'menuRegistry'"
      :activation-key="menuRegistryActivationKey"
      @count-changed="menuRegistryCount = $event"
      @notice="showNotice"
    />

    <RoleManage
      v-show="currentComponentKey === 'roleManage'"
      ref="roleManageRef"
      :active="currentComponentKey === 'roleManage'"
      :activation-key="roleManageActivationKey"
      :can-manage="canManageSystem"
      @count-changed="roleManageCount = $event"
      @notice="showNotice"
    />

    <DictList
      v-show="currentComponentKey === 'settingDicts'"
      ref="dictListRef"
      :active="currentComponentKey === 'settingDicts'"
      :activation-key="dictListActivationKey"
      @count-changed="dictListCount = $event"
      @notice="showNotice"
    />

    <SystemConfig
      v-show="currentComponentKey === 'settingSystemConfigs'"
      ref="systemConfigRef"
      :active="currentComponentKey === 'settingSystemConfigs'"
      :activation-key="systemConfigActivationKey"
      @count-changed="systemConfigCount = $event"
      @notice="showNotice"
    />

    <DecoctCenterConfig
      v-show="currentComponentKey === 'settingDecoctCenters'"
      ref="decoctCenterRef"
      :active="currentComponentKey === 'settingDecoctCenters'"
      :activation-key="decoctCenterActivationKey"
      @count-changed="decoctCenterCount = $event"
      @notice="showNotice"
    />

    <InstitutionList
      v-show="currentComponentKey === 'institutionList'"
      ref="institutionListRef"
      :active="currentComponentKey === 'institutionList'"
      :activation-key="institutionListActivationKey"
      @count-changed="institutionListCount = $event"
      @notice="showNotice"
    />

    <InstitutionApps
      v-show="currentComponentKey === 'institutionApps'"
      ref="institutionAppsRef"
      :active="currentComponentKey === 'institutionApps'"
      :activation-key="institutionAppsActivationKey"
      @count-changed="institutionAppsCount = $event"
      @notice="showNotice"
    />

    <IpWhitelist
      v-show="currentComponentKey === 'institutionIpWhitelist'"
      ref="institutionIpWhitelistRef"
      :active="currentComponentKey === 'institutionIpWhitelist'"
      :activation-key="institutionIpWhitelistActivationKey"
      @count-changed="institutionIpWhitelistCount = $event"
      @notice="showNotice"
    />

    <ApiInfoList
      v-show="currentComponentKey === 'institutionApis'"
      ref="institutionApisRef"
      :active="currentComponentKey === 'institutionApis'"
      :activation-key="institutionApisActivationKey"
      @count-changed="institutionApisCount = $event"
      @notice="showNotice"
    />

    <ApiPermissionList
      v-show="currentComponentKey === 'institutionApiPermissions'"
      ref="institutionApiPermissionsRef"
      :active="currentComponentKey === 'institutionApiPermissions'"
      :activation-key="institutionApiPermissionsActivationKey"
      @count-changed="institutionApiPermissionsCount = $event"
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

    <ExportTaskCenter
      v-show="currentComponentKey === 'exportTasks'"
      ref="exportTaskCenterRef"
      :active="currentComponentKey === 'exportTasks'"
      :activation-key="exportTaskActivationKey"
      @count-changed="exportTaskCount = $event"
      @notice="showNotice"
    />

    <LogisSpecialRule
      v-show="currentComponentKey === 'logisticsSpecialRules'"
      ref="logisSpecialRuleRef"
      :active="currentComponentKey === 'logisticsSpecialRules'"
      :activation-key="logisSpecialRuleActivationKey"
      @count-changed="logisSpecialRuleCount = $event"
      @notice="showNotice"
    />

    <AddressCost
      v-show="currentComponentKey === 'logisticsAddressCosts'"
      ref="addressCostRef"
      :active="currentComponentKey === 'logisticsAddressCosts'"
      :activation-key="addressCostActivationKey"
      @count-changed="addressCostCount = $event"
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

    <OrderMergeList
      v-show="currentComponentKey === 'logisticsMerges'"
      ref="orderMergeListRef"
      :active="currentComponentKey === 'logisticsMerges'"
      :activation-key="orderMergeActivationKey"
      @count-changed="orderMergeCount = $event"
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
      @cloud-print-count-changed="decoctionCloudPrintCount = $event"
      @notice="showNotice"
    />

    <DashboardHome
      v-if="currentComponentKey === 'dashboard'"
      ref="dashboardHomeRef"
      :can-view-health="sessionPermissions.has('ops:read')"
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

    <OrderCenter v-else-if="currentComponentKey === 'orders'" ref="orderCenterRef" @notice="showNotice" />

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

    <OrderReviewRecords
      v-else-if="currentComponentKey === 'orderReviewRecords'"
      ref="orderReviewRecordsRef"
      active
      :activation-key="orderReviewRecordsActivationKey"
      @count-changed="orderReviewRecordsCount = $event"
      @notice="showNotice"
    />

    <RecheckRecords
      v-else-if="currentComponentKey === 'recheckRecords'"
      ref="recheckRecordsRef"
      active
      :activation-key="recheckRecordsActivationKey"
      @count-changed="orderRecheckRecordsCount = $event"
      @notice="showNotice"
    />

    <OrderInterceptRule
      v-else-if="currentComponentKey === 'orderInterceptRules'"
      ref="orderInterceptRuleRef"
      active
      :activation-key="orderInterceptRuleActivationKey"
      @count-changed="orderInterceptRuleCount = $event"
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

    <ProblemRegistration
      v-else-if="currentComponentKey === 'problemRegistrations'"
      ref="problemRegistrationRef"
      v-model:operation-operator="operationOperator"
      active
      :activation-key="problemRegistrationActivationKey"
      @count-changed="problemRegistrationCount = $event"
      @notice="showNotice"
    />

    <LabelTemplate
      v-else-if="currentComponentKey === 'labelTemplates'"
      ref="labelTemplateRef"
      active
      :activation-key="labelTemplateActivationKey"
      @count-changed="labelTemplateCount = $event"
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

    <SmsTemplate
      v-else-if="currentComponentKey === 'smsTemplates'"
      ref="smsTemplateRef"
      active
      :activation-key="smsTemplateActivationKey"
      @count-changed="smsTemplateCount = $event"
      @notice="showNotice"
    />

    <SingleSmsSend
      v-else-if="currentComponentKey === 'smsSendSingle'"
      ref="singleSmsSendRef"
      active
      :activation-key="singleSmsSendActivationKey"
      @notice="showNotice"
    />

    <SmsRecordList
      v-else-if="currentComponentKey === 'smsRecords'"
      ref="smsRecordListRef"
      active
      :activation-key="smsRecordActivationKey"
      @count-changed="smsRecordCount = $event"
      @notice="showNotice"
    />

    <HerbList
      v-else-if="currentComponentKey === 'drugHerbs'"
      ref="herbListRef"
      active
      :activation-key="herbListActivationKey"
      @count-changed="herbListCount = $event"
      @notice="showNotice"
    />

    <HerbImport
      v-else-if="currentComponentKey === 'drugHerbImports'"
      ref="herbImportRef"
      active
      :activation-key="herbImportActivationKey"
      @count-changed="herbImportCount = $event"
      @notice="showNotice"
    />

    <HerbAreaManage
      v-else-if="currentComponentKey === 'drugHerbAreas'"
      ref="herbAreaManageRef"
      active
      :activation-key="herbAreaManageActivationKey"
      @count-changed="herbAreaManageCount = $event"
      @notice="showNotice"
    />

    <HerbIndexList
      v-else-if="currentComponentKey === 'drugHerbIndexes'"
      ref="herbIndexListRef"
      active
      :activation-key="herbIndexListActivationKey"
      @count-changed="herbIndexListCount = $event"
      @notice="showNotice"
    />

    <HerbIndexImport
      v-else-if="currentComponentKey === 'drugHerbIndexImports'"
      ref="herbIndexImportRef"
      active
      :activation-key="herbIndexImportActivationKey"
      @count-changed="herbIndexImportCount = $event"
      @notice="showNotice"
    />

    <HerbIndexOperationLog
      v-else-if="currentComponentKey === 'drugIndexOperationLogs'"
      ref="herbIndexOperationLogRef"
      active
      :activation-key="herbIndexOperationLogActivationKey"
      @count-changed="herbIndexOperationLogCount = $event"
      @notice="showNotice"
    />

    <PendingMenuPage v-else :item="currentRouteItem" />
  </AppLayout>
</template>
