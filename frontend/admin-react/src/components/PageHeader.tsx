import { Space, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Text, Title } = Typography;

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="query-page__header">
      <div className="query-page__header-main">
        <Title className="query-page__header-title" level={3}>
          {title}
        </Title>
        {subtitle ? (
          <Text className="query-page__header-subtitle" type="secondary">
            {subtitle}
          </Text>
        ) : null}
      </div>
      {actions ? (
        <Space className="query-page__header-actions" wrap>
          {actions}
        </Space>
      ) : null}
    </div>
  );
}
