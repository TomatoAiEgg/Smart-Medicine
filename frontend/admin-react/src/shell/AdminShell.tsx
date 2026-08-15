import {
  ApartmentOutlined,
  BarChartOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  MessageOutlined,
  SettingOutlined,
  ShopOutlined,
  ToolOutlined,
  TruckOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Layout, Menu, Tabs, Typography, type MenuProps } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { menuGroups } from '../routes/menu';
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

  useEffect(() => {
    setOpenKeys((keys) => ensureCurrentParentOpen(keys, current.parentKey));
  }, [current.parentKey]);

  const handleOpenChange: MenuProps['onOpenChange'] = (keys) => {
    setOpenKeys(ensureCurrentParentOpen(keys, current.parentKey));
  };

  const menuItems: MenuProps['items'] = menuGroups.map((group) => ({
    key: group.key,
    icon: parentIcons[group.key],
    label: group.label,
    children: group.children.map((item) => ({
      key: item.key,
      label: item.label,
      onClick: () => menuNavigate(item.path),
    })),
  }));

  return (
    <Layout className="admin-shell">
      <Sider width={224} className="admin-shell__sider">
        <div className="admin-shell__brand">
          <span className="admin-shell__brand-mark">药</span>
          <span className="admin-shell__brand-name">智能药房 SaaS</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[current.key]}
          openKeys={openKeys}
          items={menuItems}
          onOpenChange={handleOpenChange}
        />
      </Sider>

      <Layout className="admin-shell__body">
        <Header className="admin-shell__header">
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
              平台运营中心
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
