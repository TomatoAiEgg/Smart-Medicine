import { ArrowRightOutlined, CheckCircleOutlined, DatabaseOutlined } from '@ant-design/icons';
import { Button, Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuGroups, type AdminMenuItem } from '../../routes/menu';

const { Text, Title } = Typography;

const primaryEntryPaths = [
  '/orders/prescriptions',
  '/institutions',
  '/logistics/deliveries',
  '/decoction/equipment',
] as const;

function countImplemented(items: AdminMenuItem[]) {
  return items.filter((item) => item.implemented).length;
}

export function DashboardHomePage() {
  const navigate = useNavigate();
  const allMenuItems = useMemo(() => menuGroups.flatMap((group) => group.children), []);
  const quickEntries = useMemo(
    () =>
      primaryEntryPaths
        .map((path) => allMenuItems.find((item) => item.path === path))
        .filter((item): item is AdminMenuItem => Boolean(item)),
    [allMenuItems],
  );

  const totalMenus = allMenuItems.length;
  const implementedMenus = countImplemented(allMenuItems);

  return (
    <section className="dashboard-home">
      <header className="dashboard-home__header">
        <div>
          <Title level={2} className="dashboard-home__title">
            首页
          </Title>
          <Text type="secondary">查看后台功能入口、迁移覆盖和常用业务页面。</Text>
        </div>
        <Tag color="blue">
          已接入 {implementedMenus}/{totalMenus}
        </Tag>
      </header>

      <section className="dashboard-home__section" aria-labelledby="dashboard-quick-title">
        <div className="dashboard-home__section-heading">
          <Title id="dashboard-quick-title" level={3}>
            常用入口
          </Title>
          <Text type="secondary">保留老系统业务入口结构，按高频操作优先进入。</Text>
        </div>
        <div className="dashboard-home__quick-grid">
          {quickEntries.map((item) => (
            <button
              className="dashboard-home__quick-item"
              key={item.key}
              type="button"
              onClick={() => navigate(item.path)}
            >
              <span>
                <strong>{item.label}</strong>
                <small>{item.parentLabel}</small>
              </span>
              <ArrowRightOutlined aria-hidden />
            </button>
          ))}
        </div>
      </section>

      <section className="dashboard-home__section" aria-labelledby="dashboard-menu-title">
        <div className="dashboard-home__section-heading">
          <Title id="dashboard-menu-title" level={3}>
            菜单覆盖
          </Title>
          <Text type="secondary">用于核对 React 入口和老系统父级菜单覆盖情况。</Text>
        </div>
        <div className="dashboard-home__menu-list">
          {menuGroups.map((group) => {
            const implemented = countImplemented(group.children);
            return (
              <article className="dashboard-home__menu-row" key={group.key}>
                <DatabaseOutlined aria-hidden />
                <div>
                  <strong>{group.label}</strong>
                  <span>{group.children.length} 个子菜单</span>
                </div>
                <Tag icon={<CheckCircleOutlined />} color={implemented === group.children.length ? 'success' : 'warning'}>
                  {implemented}/{group.children.length}
                </Tag>
              </article>
            );
          })}
        </div>
      </section>

      <Button className="dashboard-home__primary-action" type="primary" onClick={() => navigate('/system/users')}>
        进入用户管理
      </Button>
    </section>
  );
}
