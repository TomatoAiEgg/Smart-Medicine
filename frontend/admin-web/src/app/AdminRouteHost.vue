<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import type { AdminUserSession } from '../api/adminSession';
import PendingMenuPage from '../features/pending/PendingMenuPage.vue';
import { routeComponentByKey } from './route-components';
import { isViewKey, routeByKey, type ImplementedViewKey, type ViewKey } from './views';

type NoticeTone = 'info' | 'success' | 'error';
type RecheckScanMode = 'single' | 'multi';
type PageInstance = Record<string, unknown>;

export interface RouteCountPayload {
  routeKey: ViewKey;
  componentKey: ImplementedViewKey;
  count: number;
}

const props = defineProps<{
  adminUser: AdminUserSession;
  canManageSystem: boolean;
  canViewHealth: boolean;
  operationOperator: string;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [payload: RouteCountPayload];
  cloudPrintCountChanged: [count: number];
  'update:operationOperator': [value: string];
}>();

const route = useRoute();
const pageRef = ref<unknown>(null);

const routeKey = computed<ViewKey>(() => {
  const value = route.meta.routeKey;
  return typeof value === 'string' && isViewKey(value) ? value : 'dashboard';
});
const routeItem = computed(() => routeByKey[routeKey.value]);
const componentKey = computed(() => routeItem.value.componentKey);
const routeComponent = computed(() => (componentKey.value ? routeComponentByKey[componentKey.value] : null));
const recheckMode = computed<RecheckScanMode>(() => (routeKey.value === 'orderRechecksMulti' ? 'multi' : 'single'));

const activeActivationComponents = new Set<ImplementedViewKey>([
  'reports',
  'auditPerformance',
  'auditPerformanceDetails',
  'decoctionPerformance',
  'decoctionPerformanceDetails',
  'dispensePerformance',
  'dispensePerformanceDetails',
  'herbDosage',
  'institutionHerbReconciliation',
  'logisticsPerformance',
  'logisticsPerformanceDetails',
  'prescriptionHerbDetails',
  'recheckPerformance',
  'recheckPerformanceDetails',
  'institutionPrescriptionCounts',
  'prescriptionReconciliation',
  'operatorManage',
  'menuRegistry',
  'roleManage',
  'settingDicts',
  'settingSystemConfigs',
  'settingDecoctCenters',
  'institutionList',
  'institutionApps',
  'institutionIpWhitelist',
  'institutionApis',
  'institutionApiPermissions',
  'ops',
  'observability',
  'integration',
  'exportTasks',
  'logisticsAddressCosts',
  'logisticsSpecialRules',
  'logisticsPrint',
  'logistics',
  'logisticsInfo',
  'logisticsMerges',
  'logisticsUnreceivedFollowups',
  'exceptionLogs',
  'problemRegistrations',
  'labelTemplates',
  'labelPrints',
  'smsTemplates',
  'smsSendSingle',
  'smsRecords',
  'drugHerbs',
  'drugHerbImports',
  'drugHerbAreas',
  'drugHerbIndexes',
  'drugHerbIndexImports',
  'drugIndexOperationLogs',
  'decoction',
  'addressModify',
  'prescriptionModify',
  'orderManageAction',
  'prescriptionReprint',
  'orderReviewRecords',
  'recheckRecords',
  'orderInterceptRules',
  'manualProcess',
  'orderWarehouse',
  'orderReceipt',
]);

const refreshMethodByComponent: Partial<Record<ImplementedViewKey, string>> = {
  dashboard: 'refreshDashboard',
  reviews: 'refreshCurrentTasks',
  rechecks: 'refreshCurrentTasks',
  orders: 'refreshOrders',
  dispensePrint: 'refreshDispenseTasks',
  recheckScan: 'refreshRecheckScanTasks',
  integration: 'refreshIntegrationMessages',
  exportTasks: 'refreshExportTasks',
  reports: 'refreshReports',
  auditPerformance: 'refreshAuditPerformance',
  auditPerformanceDetails: 'refreshAuditPerformanceDetails',
  decoctionPerformance: 'refreshDecoctionPerformance',
  decoctionPerformanceDetails: 'refreshDecoctionPerformanceDetails',
  dispensePerformance: 'refreshDispensePerformance',
  dispensePerformanceDetails: 'refreshDispensePerformanceDetails',
  herbDosage: 'refreshHerbDosage',
  institutionHerbReconciliation: 'refreshInstitutionHerbReconciliation',
  logisticsPerformance: 'refreshLogisticsPerformance',
  logisticsPerformanceDetails: 'refreshLogisticsPerformanceDetails',
  prescriptionHerbDetails: 'refreshPrescriptionHerbDetails',
  recheckPerformance: 'refreshRecheckPerformance',
  recheckPerformanceDetails: 'refreshRecheckPerformanceDetails',
  institutionPrescriptionCounts: 'refreshInstitutionPrescriptionCounts',
  prescriptionReconciliation: 'refreshPrescriptionReconciliation',
  settingDicts: 'refreshDictTypes',
  settingSystemConfigs: 'refreshSystemConfigs',
  settingDecoctCenters: 'refreshDecoctCenters',
  operatorManage: 'refreshOperators',
  menuRegistry: 'refreshMenus',
  roleManage: 'refreshRoles',
  institutionList: 'refreshInstitutions',
  institutionApps: 'refreshInstitutionApps',
  institutionIpWhitelist: 'refreshIpWhitelists',
  institutionApis: 'refreshInstitutionApis',
  institutionApiPermissions: 'refreshApiPermissions',
  portal: 'handlePortalQuery',
  logistics: 'refreshLogisticsRecords',
  logisticsSpecialRules: 'refreshLogisticsSpecialRules',
  logisticsAddressCosts: 'refreshAddressCosts',
  logisticsPrint: 'refreshLogisPrints',
  logisticsInfo: 'refreshLogisticsInfos',
  logisticsMerges: 'refreshOrderMerges',
  logisticsUnreceivedFollowups: 'refreshUnreceivedFollowups',
  exceptionLogs: 'refreshExceptionLogs',
  problemRegistrations: 'refreshProblemRegistrations',
  labelTemplates: 'refreshLabelTemplates',
  labelPrints: 'refreshLabelPrints',
  smsTemplates: 'refreshSmsTemplates',
  smsSendSingle: 'refreshSingleSmsSend',
  smsRecords: 'refreshSmsRecords',
  drugHerbs: 'refreshHerbs',
  drugHerbImports: 'resetImport',
  drugHerbAreas: 'refreshHerbAreas',
  drugHerbIndexes: 'refreshHerbIndexes',
  drugHerbIndexImports: 'resetImport',
  drugIndexOperationLogs: 'refreshHerbIndexOperationLogs',
  addressModify: 'refreshAddressOrders',
  prescriptionModify: 'refreshPrescriptionOrders',
  orderManageAction: 'refreshOrderManageActions',
  prescriptionReprint: 'refreshPrescriptionReprints',
  orderReviewRecords: 'refreshOrderReviews',
  recheckRecords: 'refreshOrderRechecks',
  orderInterceptRules: 'refreshOrderInterceptRules',
  manualProcess: 'refreshManualProcessOrders',
  orderWarehouse: 'refreshOrderWarehouses',
  orderReceipt: 'refreshOrderReceipts',
  ops: 'refreshOpsConsole',
  observability: 'refreshOrderObservability',
  decoction: 'refreshDecoctionSimulator',
};

const routeComponentProps = computed<Record<string, unknown>>(() => {
  const key = componentKey.value;
  if (!key) return {};

  if (key === 'dashboard') {
    return { canViewHealth: props.canViewHealth };
  }
  if (key === 'recheckScan') {
    return { active: true, mode: recheckMode.value };
  }
  if (key === 'reviews' || key === 'rechecks') {
    return { activeView: key };
  }
  if (key === 'operatorManage') {
    return {
      active: true,
      activationKey: props.activationKey,
      canManage: props.canManageSystem,
      currentUserId: props.adminUser.userId,
    };
  }
  if (key === 'roleManage') {
    return {
      active: true,
      activationKey: props.activationKey,
      canManage: props.canManageSystem,
    };
  }
  if (key === 'decoction') {
    return {
      active: true,
      activationKey: props.activationKey,
      operationOperator: props.operationOperator,
      routeKey: routeKey.value,
    };
  }
  if (['logistics', 'logisticsUnreceivedFollowups', 'exceptionLogs', 'problemRegistrations'].includes(key)) {
    return {
      active: true,
      activationKey: props.activationKey,
      operationOperator: props.operationOperator,
    };
  }
  if (activeActivationComponents.has(key)) {
    return {
      active: true,
      activationKey: props.activationKey,
    };
  }
  return {};
});

function handleCountChanged(count: number) {
  const key = componentKey.value;
  if (!key) return;
  emit('countChanged', {
    routeKey: routeKey.value,
    componentKey: key,
    count,
  });
}

function handleNotice(tone: NoticeTone, text: string) {
  emit('notice', tone, text);
}

function handleWorkflowCountsChanged(counts: { reviews: number; dispenses: number; rechecks: number }) {
  emit('countChanged', { routeKey: 'reviews', componentKey: 'reviews', count: counts.reviews });
  emit('countChanged', { routeKey: 'dispenses', componentKey: 'dispensePrint', count: counts.dispenses });
  emit('countChanged', { routeKey: 'rechecks', componentKey: 'rechecks', count: counts.rechecks });
}

function pageInstance() {
  return pageRef.value as PageInstance | null;
}

async function refreshCurrent() {
  const key = componentKey.value;
  const methodName = key ? refreshMethodByComponent[key] : null;
  const page = pageInstance();
  const method = methodName && page ? page[methodName] : null;
  if (typeof method === 'function') {
    await method.call(page);
    return;
  }
  emit('notice', 'info', `${routeItem.value.label} 当前未配置页面刷新方法`);
}

defineExpose({
  refreshCurrent,
});
</script>

<template>
  <component
    :is="routeComponent"
    v-if="routeComponent"
    :key="routeKey"
    ref="pageRef"
    v-bind="routeComponentProps"
    @notice="handleNotice"
    @count-changed="handleCountChanged"
    @counts-changed="handleWorkflowCountsChanged"
    @cloud-print-count-changed="$emit('cloudPrintCountChanged', $event)"
    @update:operation-operator="$emit('update:operationOperator', $event)"
  />
  <PendingMenuPage v-else :item="routeItem" />
</template>
