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
  'orders-prescriptions': <PrescriptionListPage />,
  'orders-recheck': <PrescriptionRecheckPage />,
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
