import { Card } from 'antd';
import type { ReactNode } from 'react';
import { PageHeader } from './PageHeader';

interface QueryTableShellProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  filters?: ReactNode | null;
  table: ReactNode;
}

export function QueryTableShell({
  title,
  subtitle,
  actions,
  filters,
  table,
}: QueryTableShellProps) {
  return (
    <section className="query-page">
      <PageHeader title={title} subtitle={subtitle} actions={actions} />
      {filters !== null && filters !== undefined ? (
        <Card className="query-page__filters" size="small">
          {filters}
        </Card>
      ) : null}
      <Card className="query-page__table" size="small">
        {table}
      </Card>
    </section>
  );
}
