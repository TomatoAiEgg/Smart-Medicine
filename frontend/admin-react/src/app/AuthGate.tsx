import { Spin } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { readAdminSession } from '../api/adminSession';
import { restoreAdminSession } from '../api/auth';

type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated';

function fullLocationPath(location: ReturnType<typeof useLocation>) {
  return `${location.pathname}${location.search}${location.hash}`;
}

export function AuthGate({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [status, setStatus] = useState<AuthStatus>(() =>
    readAdminSession() ? 'checking' : 'unauthenticated',
  );

  useEffect(() => {
    let cancelled = false;

    async function restore() {
      if (!readAdminSession()) {
        setStatus('unauthenticated');
        return;
      }

      const user = await restoreAdminSession();

      if (!cancelled) {
        setStatus(user ? 'authenticated' : 'unauthenticated');
      }
    }

    const handleExpired = () => setStatus('unauthenticated');
    const handleRefreshed = () => setStatus('authenticated');

    void restore();
    window.addEventListener('admin-auth-expired', handleExpired);
    window.addEventListener('admin-auth-refreshed', handleRefreshed);

    return () => {
      cancelled = true;
      window.removeEventListener('admin-auth-expired', handleExpired);
      window.removeEventListener('admin-auth-refreshed', handleRefreshed);
    };
  }, []);

  if (status === 'checking') {
    return (
      <div className="admin-auth-loading">
        <Spin tip="正在恢复登录状态" />
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: fullLocationPath(location) }} />;
  }

  return children;
}
