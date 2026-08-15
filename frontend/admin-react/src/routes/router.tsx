import { createBrowserRouter, Navigate } from 'react-router-dom';
import { MigrationNoticePage } from '../components/MigrationNoticePage';
import { LoginPage } from '../features/auth/LoginPage';
import { AdminShell } from '../shell/AdminShell';
import { menuItems } from './menu';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <AdminShell />,
    children: [
      {
        index: true,
        element: <Navigate to="/system/users" replace />,
      },
      ...menuItems.map((item) => ({
        path: item.path.slice(1),
        element: <MigrationNoticePage item={item} />,
      })),
    ],
  },
  {
    path: '*',
    element: <Navigate to="/system/users" replace />,
  },
]);
