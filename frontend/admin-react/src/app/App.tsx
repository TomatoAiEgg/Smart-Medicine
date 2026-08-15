import { Spin } from 'antd';
import { Suspense } from 'react';
import { RouterProvider } from 'react-router-dom';
import { router } from '../routes/router';

export function App() {
  return (
    <Suspense
      fallback={
        <div className="admin-route-loading">
          <Spin tip="正在加载页面" />
        </div>
      }
    >
      <RouterProvider router={router} />
    </Suspense>
  );
}
