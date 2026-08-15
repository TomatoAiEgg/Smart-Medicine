import { Card, Descriptions, Result, Tag } from 'antd';
import type { AdminMenuItem } from '../routes/menu';

interface MigrationNoticePageProps {
  item: AdminMenuItem;
}

export function MigrationNoticePage({ item }: MigrationNoticePageProps) {
  return (
    <Card>
      <Result
        status="info"
        title={`${item.title}正在迁移到 React`}
        subTitle="该入口已按老项目菜单恢复。完整业务页面会在后续阶段按截图和接口逐页补齐。"
      />
      <Descriptions
        bordered
        size="small"
        column={1}
        items={[
          {
            key: 'parent-menu',
            label: '父级菜单',
            children: item.parentLabel,
          },
          {
            key: 'business-entry',
            label: '业务入口',
            children: item.label,
          },
          {
            key: 'old-route',
            label: '老项目路由',
            children: item.legacyRoute,
          },
          {
            key: 'migration-status',
            label: '迁移状态',
            children: (
              <Tag color={item.implemented ? 'green' : 'blue'}>
                {item.implemented ? '代表页面已实现' : '入口已恢复，页面待迁移'}
              </Tag>
            ),
          },
        ]}
      />
    </Card>
  );
}
