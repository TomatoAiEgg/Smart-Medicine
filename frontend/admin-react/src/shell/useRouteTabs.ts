import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, type NavigateFunction } from 'react-router-dom';
import { findMenuItemByPath, type AdminMenuItem } from '../routes/menu';

export interface RouteTab {
  key: string;
  title: string;
  path: string;
  closable: boolean;
}

const initialRouteTab: RouteTab = {
  key: 'system-users',
  title: '用户管理',
  path: '/system/users',
  closable: false,
};

function createRouteTab(item: AdminMenuItem): RouteTab {
  return {
    key: item.key,
    title: item.title,
    path: item.path,
    closable: item.key !== initialRouteTab.key,
  };
}

export function useRouteTabs(): {
  tabs: RouteTab[];
  activeKey: string;
  current: AdminMenuItem;
  navigate: NavigateFunction;
  closeTab: (targetKey: string) => void;
} {
  const location = useLocation();
  const navigate = useNavigate();
  const [tabs, setTabs] = useState<RouteTab[]>([initialRouteTab]);
  const current = useMemo(() => findMenuItemByPath(location.pathname), [location.pathname]);
  const activeKey = current.key;

  useEffect(() => {
    if (current.path !== location.pathname) {
      return;
    }

    setTabs((previousTabs) => {
      if (previousTabs.some((tab) => tab.key === current.key)) {
        return previousTabs;
      }

      return [...previousTabs, createRouteTab(current)];
    });
  }, [current, location.pathname]);

  const closeTab = useCallback(
    (targetKey: string) => {
      const targetTab = tabs.find((tab) => tab.key === targetKey);

      if (!targetTab?.closable) {
        return;
      }

      const remainingTabs = tabs.filter((tab) => tab.key !== targetKey);
      setTabs(remainingTabs);

      if (targetKey === activeKey) {
        const fallbackTab = remainingTabs.at(-1) ?? remainingTabs[0];

        if (fallbackTab) {
          navigate(fallbackTab.path);
        }
      }
    },
    [activeKey, navigate, tabs],
  );

  return { tabs, activeKey, current, navigate, closeTab };
}
