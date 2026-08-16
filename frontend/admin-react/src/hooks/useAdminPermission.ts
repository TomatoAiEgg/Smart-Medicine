import { useEffect, useState } from 'react';
import { readAdminSession } from '../api/adminSession';

export function useAdminPermission(permission: string) {
  const [allowed, setAllowed] = useState(() => readAdminSession()?.user.permissions.includes(permission) ?? false);

  useEffect(() => {
    const refresh = () => setAllowed(readAdminSession()?.user.permissions.includes(permission) ?? false);
    window.addEventListener('admin-auth-refreshed', refresh);
    window.addEventListener('admin-auth-expired', refresh);
    return () => {
      window.removeEventListener('admin-auth-refreshed', refresh);
      window.removeEventListener('admin-auth-expired', refresh);
    };
  }, [permission]);

  return allowed;
}
