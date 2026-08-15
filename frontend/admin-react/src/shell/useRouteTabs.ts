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

function createRouteTab(item: AdminMenuItem, path: string): RouteTab {
  return {
    key: item.key,
    title: item.title,
    path,
    closable: item.key !== initialRouteTab.key,
  };
}

function createInitialTabs(current: AdminMenuItem, pathname: string, fullPath: string): RouteTab[] {
  if (current.path !== pathname) {
    return [initialRouteTab];
  }

  const currentTab = createRouteTab(current, fullPath);

  return currentTab.key === initialRouteTab.key ? [currentTab] : [initialRouteTab, currentTab];
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
  const fullPath = useMemo(
    () => `${location.pathname}${location.search}${location.hash}`,
    [location.hash, location.pathname, location.search],
  );
  const current = useMemo(() => findMenuItemByPath(location.pathname), [location.pathname]);
  const [tabs, setTabs] = useState<RouteTab[]>(() =>
    createInitialTabs(current, location.pathname, fullPath),
  );
  const activeKey = current.key;

  useEffect(() => {
    if (current.path !== location.pathname) {
      return;
    }

    setTabs((previousTabs) => {
      if (previousTabs.some((tab) => tab.key === current.key)) {
        return previousTabs.map((tab) =>
          tab.key === current.key ? { ...tab, title: current.title, path: fullPath } : tab,
        );
      }

      return [...previousTabs, createRouteTab(current, fullPath)];
    });
  }, [current, fullPath, location.pathname]);

  const closeTab = useCallback(
    (targetKey: string) => {
      const targetIndex = tabs.findIndex((tab) => tab.key === targetKey);
      const targetTab = tabs[targetIndex];

      if (!targetTab?.closable) {
        return;
      }

      const remainingTabs = tabs.filter((tab) => tab.key !== targetKey);
      const fallbackPath =
        tabs[targetIndex - 1]?.path ?? tabs[targetIndex + 1]?.path ?? initialRouteTab.path;

      setTabs(remainingTabs);

      if (targetKey === activeKey) {
        navigate(fallbackPath);
      }
    },
    [activeKey, navigate, tabs],
  );

  return { tabs, activeKey, current, navigate, closeTab };
}
