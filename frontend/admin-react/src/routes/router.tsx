import { createBrowserRouter, Navigate } from 'react-router-dom';
import { lazy, type ReactNode } from 'react';
import { AuthGate } from '../app/AuthGate';
import { MigrationNoticePage } from '../components/MigrationNoticePage';
import { menuItems } from './menu';

const AdminShell = lazy(() => import('../shell/AdminShell').then(({ AdminShell }) => ({ default: AdminShell })));
const LoginPage = lazy(() => import('../features/auth/LoginPage').then(({ LoginPage }) => ({ default: LoginPage })));
const UserManagementPage = lazy(() =>
  import('../features/system/UserManagementPage').then(({ UserManagementPage }) => ({ default: UserManagementPage })),
);
const RoleManagementPage = lazy(() =>
  import('../features/system/RoleManagementPage').then(({ RoleManagementPage }) => ({ default: RoleManagementPage })),
);
const MenuRegistryPage = lazy(() =>
  import('../features/system/MenuRegistryPage').then(({ MenuRegistryPage }) => ({ default: MenuRegistryPage })),
);
const DictionaryManagementPage = lazy(() =>
  import('../features/settings/DictionaryManagementPage').then(({ DictionaryManagementPage }) => ({ default: DictionaryManagementPage })),
);
const BasicManagementPages = {
  SystemConfigPage: lazy(() => import('../features/management/BasicManagementPages').then(({ SystemConfigPage }) => ({ default: SystemConfigPage }))),
  DecoctCenterPage: lazy(() => import('../features/management/BasicManagementPages').then(({ DecoctCenterPage }) => ({ default: DecoctCenterPage }))),
  OperatorManagementPage: lazy(() => import('../features/management/BasicManagementPages').then(({ OperatorManagementPage }) => ({ default: OperatorManagementPage }))),
  InstitutionListPage: lazy(() => import('../features/management/BasicManagementPages').then(({ InstitutionListPage }) => ({ default: InstitutionListPage }))),
  InstitutionIpWhitelistPage: lazy(() => import('../features/management/BasicManagementPages').then(({ InstitutionIpWhitelistPage }) => ({ default: InstitutionIpWhitelistPage }))),
  InstitutionApiListPage: lazy(() => import('../features/management/BasicManagementPages').then(({ InstitutionApiListPage }) => ({ default: InstitutionApiListPage }))),
  InstitutionApiPermissionPage: lazy(() => import('../features/management/BasicManagementPages').then(({ InstitutionApiPermissionPage }) => ({ default: InstitutionApiPermissionPage }))),
};
const LogisticsPages = {
  LogisticsSpecialRulePage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsSpecialRulePage }) => ({ default: LogisticsSpecialRulePage }))),
  LogisticsAddressCostPage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsAddressCostPage }) => ({ default: LogisticsAddressCostPage }))),
  LogisticsDeliveryPage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsDeliveryPage }) => ({ default: LogisticsDeliveryPage }))),
  LogisticsInfoPage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsInfoPage }) => ({ default: LogisticsInfoPage }))),
  LogisticsPrintPage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsPrintPage }) => ({ default: LogisticsPrintPage }))),
  LogisticsMergePage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsMergePage }) => ({ default: LogisticsMergePage }))),
  LogisticsUnreceivedPage: lazy(() => import('../features/operations/LogisticsPages').then(({ LogisticsUnreceivedPage }) => ({ default: LogisticsUnreceivedPage }))),
};
const OrderOperationPages = {
  OrderAuditPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderAuditPage }) => ({ default: OrderAuditPage }))),
  OrderDispensePage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderDispensePage }) => ({ default: OrderDispensePage }))),
  OrderRecheckMultiPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderRecheckMultiPage }) => ({ default: OrderRecheckMultiPage }))),
  OrderRecheckRecordsPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderRecheckRecordsPage }) => ({ default: OrderRecheckRecordsPage }))),
  OrderAddressModifyPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderAddressModifyPage }) => ({ default: OrderAddressModifyPage }))),
  OrderPrescriptionModifyPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderPrescriptionModifyPage }) => ({ default: OrderPrescriptionModifyPage }))),
  OrderManageActionPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderManageActionPage }) => ({ default: OrderManageActionPage }))),
  OrderPrescriptionReprintPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderPrescriptionReprintPage }) => ({ default: OrderPrescriptionReprintPage }))),
  OrderWarehousePage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderWarehousePage }) => ({ default: OrderWarehousePage }))),
  OrderInterceptRulePage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderInterceptRulePage }) => ({ default: OrderInterceptRulePage }))),
  OrderManualProcessPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderManualProcessPage }) => ({ default: OrderManualProcessPage }))),
  OrderReceiptPage: lazy(() => import('../features/operations/OrderOperationPages').then(({ OrderReceiptPage }) => ({ default: OrderReceiptPage }))),
};
const MaintenancePages = {
  MaintenanceOrderProcessPage: lazy(() => import('../features/operations/MaintenancePages').then(({ MaintenanceOrderProcessPage }) => ({ default: MaintenanceOrderProcessPage }))),
  MaintenanceExceptionLogPage: lazy(() => import('../features/operations/MaintenancePages').then(({ MaintenanceExceptionLogPage }) => ({ default: MaintenanceExceptionLogPage }))),
  MaintenanceMqMessagePage: lazy(() => import('../features/operations/MaintenancePages').then(({ MaintenanceMqMessagePage }) => ({ default: MaintenanceMqMessagePage }))),
  MaintenanceProblemRegistrationPage: lazy(() => import('../features/operations/MaintenancePages').then(({ MaintenanceProblemRegistrationPage }) => ({ default: MaintenanceProblemRegistrationPage }))),
};
const PrescriptionListPage = lazy(() =>
  import('../features/orders/PrescriptionListPage').then(({ PrescriptionListPage }) => ({
    default: PrescriptionListPage,
  })),
);
const PrescriptionRecheckPage = lazy(() =>
  import('../features/orders/PrescriptionRecheckPage').then(({ PrescriptionRecheckPage }) => ({
    default: PrescriptionRecheckPage,
  })),
);
const EquipmentListPage = lazy(() =>
  import('../features/decoction/EquipmentListPage').then(({ EquipmentListPage }) => ({ default: EquipmentListPage })),
);
const PrescriptionCountReportPage = lazy(() =>
  import('../features/reports/PrescriptionCountReportPage').then(({ PrescriptionCountReportPage }) => ({
    default: PrescriptionCountReportPage,
  })),
);

const implementedPages: Record<string, ReactNode> = {
  'system-users': <UserManagementPage />,
  'system-roles': <RoleManagementPage />,
  'system-menus': <MenuRegistryPage />,
  'settings-dicts': <DictionaryManagementPage />,
  'settings-configs': <BasicManagementPages.SystemConfigPage />,
  'settings-decoct-centers': <BasicManagementPages.DecoctCenterPage />,
  'settings-operators': <BasicManagementPages.OperatorManagementPage />,
  'institutions-list': <BasicManagementPages.InstitutionListPage />,
  'institutions-ip': <BasicManagementPages.InstitutionIpWhitelistPage />,
  'institutions-apis': <BasicManagementPages.InstitutionApiListPage />,
  'institutions-api-permissions': <BasicManagementPages.InstitutionApiPermissionPage />,
  'logistics-special-rules': <LogisticsPages.LogisticsSpecialRulePage />,
  'logistics-address-costs': <LogisticsPages.LogisticsAddressCostPage />,
  'logistics-delivery': <LogisticsPages.LogisticsDeliveryPage />,
  'logistics-info': <LogisticsPages.LogisticsInfoPage />,
  'logistics-print': <LogisticsPages.LogisticsPrintPage />,
  'logistics-merges': <LogisticsPages.LogisticsMergePage />,
  'logistics-unreceived': <LogisticsPages.LogisticsUnreceivedPage />,
  'orders-prescriptions': <PrescriptionListPage />,
  'orders-audit': <OrderOperationPages.OrderAuditPage />,
  'orders-dispense': <OrderOperationPages.OrderDispensePage />,
  'orders-recheck': <PrescriptionRecheckPage />,
  'orders-recheck-multi': <OrderOperationPages.OrderRecheckMultiPage />,
  'orders-recheck-records': <OrderOperationPages.OrderRecheckRecordsPage />,
  'orders-address': <OrderOperationPages.OrderAddressModifyPage />,
  'orders-prescription-modify': <OrderOperationPages.OrderPrescriptionModifyPage />,
  'orders-actions': <OrderOperationPages.OrderManageActionPage />,
  'orders-reprint': <OrderOperationPages.OrderPrescriptionReprintPage />,
  'orders-warehouse': <OrderOperationPages.OrderWarehousePage />,
  'orders-intercept': <OrderOperationPages.OrderInterceptRulePage />,
  'orders-manual-process': <OrderOperationPages.OrderManualProcessPage />,
  'orders-receipts': <OrderOperationPages.OrderReceiptPage />,
  'maintenance-order-processes': <MaintenancePages.MaintenanceOrderProcessPage />,
  'maintenance-exception-logs': <MaintenancePages.MaintenanceExceptionLogPage />,
  'maintenance-mq': <MaintenancePages.MaintenanceMqMessagePage />,
  'maintenance-problems': <MaintenancePages.MaintenanceProblemRegistrationPage />,
  'decoction-equipment': <EquipmentListPage />,
  'reports-prescription-counts': <PrescriptionCountReportPage />,
};

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <AuthGate>
        <AdminShell />
      </AuthGate>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/system/users" replace />,
      },
      ...menuItems.map((item) => ({
        path: item.path.slice(1),
        element: implementedPages[item.key] ?? <MigrationNoticePage item={item} />,
      })),
    ],
  },
  {
    path: '*',
    element: <Navigate to="/system/users" replace />,
  },
]);
