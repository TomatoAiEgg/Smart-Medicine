import { createBrowserRouter, Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthGate } from '../app/AuthGate';
import { MigrationNoticePage } from '../components/MigrationNoticePage';
import { LoginPage } from '../features/auth/LoginPage';
import { EquipmentListPage } from '../features/decoction/EquipmentListPage';
import { PrescriptionListPage } from '../features/orders/PrescriptionListPage';
import { PrescriptionRecheckPage } from '../features/orders/PrescriptionRecheckPage';
import { PrescriptionCountReportPage } from '../features/reports/PrescriptionCountReportPage';
import { UserManagementPage } from '../features/system/UserManagementPage';
import { AdminShell } from '../shell/AdminShell';
import { menuItems } from './menu';

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
