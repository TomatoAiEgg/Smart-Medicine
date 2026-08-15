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
