import {
  ApartmentOutlined,
  BarChartOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  HomeOutlined,
  MessageOutlined,
  MenuOutlined,
  SettingOutlined,
  ShopOutlined,
  ToolOutlined,
  TruckOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Drawer, Layout, Menu, Tabs, Typography, type MenuProps } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { readAdminSession, type AdminUserSession } from '../api/adminSession';
import { dashboardMenuItem, menuGroups } from '../routes/menu';
import { useRouteTabs } from './useRouteTabs';

const { Header, Sider, Content } = Layout;

const parentIcons: Record<string, ReactNode> = {
  system: <UserOutlined />,
  settings: <SettingOutlined />,
  institutions: <ApartmentOutlined />,
  logistics: <TruckOutlined />,
  orders: <FileTextOutlined />,
  maintenance: <ToolOutlined />,
  labels: <DatabaseOutlined />,
  sms: <MessageOutlined />,
  drugs: <ExperimentOutlined />,
  reports: <BarChartOutlined />,
  decoction: <ShopOutlined />,
};

function ensureCurrentParentOpen(keys: string[], currentParentKey: string): string[] {
  return keys.includes(currentParentKey) ? keys : [...keys, currentParentKey];
}

export function AdminShell() {
  const menuNavigate = useNavigate();
  const { tabs, activeKey, current, navigate, closeTab } = useRouteTabs();
  const [openKeys, setOpenKeys] = useState<string[]>([current.parentKey]);
  const [collapsed, setCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [user, setUser] = useState<AdminUserSession | null>(() => readAdminSession()?.user ?? null);

  useEffect(() => {
    setOpenKeys((keys) => ensureCurrentParentOpen(keys, current.parentKey));
  }, [current.parentKey]);

  useEffect(() => {
    const handleRefreshed = (event: Event) => {
      if (event instanceof CustomEvent) {
        setUser(event.detail as AdminUserSession);
      }
    };
    const handleExpired = () => setUser(null);

    window.addEventListener('admin-auth-refreshed', handleRefreshed);
    window.addEventListener('admin-auth-expired', handleExpired);

    return () => {
      window.removeEventListener('admin-auth-refreshed', handleRefreshed);
      window.removeEventListener('admin-auth-expired', handleExpired);
    };
  }, []);

  const handleOpenChange: MenuProps['onOpenChange'] = (keys) => {
    setOpenKeys([...keys]);
  };

  const navigateToMenuItem = (path: string) => {
    menuNavigate(path);
    setMobileMenuOpen(false);
  };

  const menuItems: MenuProps['items'] = menuGroups.map((group) => ({
    key: group.key,
    icon: parentIcons[group.key],
    label: group.label,
    children: group.children.map((item) => ({
      key: item.key,
      label: item.label,
      onClick: () => navigateToMenuItem(item.path),
    })),
  }));

  const renderNavigationMenu = (inlineCollapsed = false) => (
    <>
      <button
        className={`admin-shell__home${current.key === dashboardMenuItem.key ? ' admin-shell__home--active' : ''}`}
        type="button"
        aria-current={current.key === dashboardMenuItem.key ? 'page' : undefined}
        title={inlineCollapsed ? dashboardMenuItem.title : undefined}
        onClick={() => navigateToMenuItem(dashboardMenuItem.path)}
      >
        <HomeOutlined aria-hidden />
        {!inlineCollapsed ? <span>{dashboardMenuItem.title}</span> : null}
      </button>
      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={current.key === dashboardMenuItem.key ? [] : [current.key]}
        openKeys={inlineCollapsed ? undefined : openKeys}
        inlineCollapsed={inlineCollapsed}
        items={menuItems}
        onOpenChange={handleOpenChange}
      />
    </>
  );

  return (
    <Layout className="admin-shell">
      <Sider
        width={224}
        collapsedWidth={64}
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        className="admin-shell__sider"
      >
        <div className="admin-shell__brand">
          <span className="admin-shell__brand-mark">药</span>
          <span className="admin-shell__brand-name">智能药房 SaaS</span>
        </div>
        {renderNavigationMenu(collapsed)}
      </Sider>

      <Drawer
        className="admin-shell__mobile-drawer"
        placement="left"
        open={mobileMenuOpen}
        width={224}
        closable={false}
        onClose={() => setMobileMenuOpen(false)}
      >
        <div className="admin-shell__brand">
          <span className="admin-shell__brand-mark">药</span>
          <span className="admin-shell__brand-name">智能药房 SaaS</span>
        </div>
        {renderNavigationMenu()}
      </Drawer>

      <Layout className="admin-shell__body">
        <Header className="admin-shell__header">
          <Button
            className="admin-shell__mobile-trigger"
            type="text"
            icon={<MenuOutlined />}
            aria-label="打开导航菜单"
            onClick={() => setMobileMenuOpen(true)}
          />
          <div className="admin-shell__breadcrumb">
            <Typography.Text className="admin-shell__parent-title">
              {current.parentLabel}
            </Typography.Text>
            <Typography.Text className="admin-shell__page-title">
              {current.title}
            </Typography.Text>
          </div>
          <div className="admin-shell__user">
            <Typography.Text className="admin-shell__user-name">
              {user?.displayName || user?.username || user?.tenantName || '平台运营中心'}
            </Typography.Text>
            <span className="admin-shell__avatar">管</span>
          </div>
        </Header>

        <div className="admin-shell__tabs">
          <Tabs
            type="editable-card"
            size="small"
            activeKey={activeKey}
            hideAdd
            items={tabs.map((tab) => ({
              key: tab.key,
              label: tab.title,
              closable: tab.closable,
            }))}
            onChange={(key) => {
              const targetTab = tabs.find((tab) => tab.key === key);

              if (targetTab) {
                navigate(targetTab.path);
              }
            }}
            onEdit={(targetKey, action) => {
              if (action === 'remove' && typeof targetKey === 'string') {
                closeTab(targetKey);
              }
            }}
          />
        </div>

        <Content className="admin-shell__content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
