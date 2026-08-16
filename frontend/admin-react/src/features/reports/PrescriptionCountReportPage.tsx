import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Descriptions, Input, Space, Table, type TableColumnsType } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { request } from '../../api/client';
import { QueryTableShell } from '../../components/QueryTableShell';
import { downloadCsv } from '../../utils/downloadCsv';

type OverviewRecord = Record<string, unknown>;

interface OverviewFilters {
  from: string;
  to: string;
  trendDays: string;
}

function buildQuery(filters: OverviewFilters) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value.trim()) query.set(key, value.trim());
  });
  return query.toString();
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

function isRecord(value: unknown): value is OverviewRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function scalarEntries(record: OverviewRecord) {
  return Object.entries(record).filter(([, value]) => !Array.isArray(value));
}

function arraySections(record: OverviewRecord) {
  const sections: [string, unknown[]][] = [];
  Object.entries(record).forEach(([key, value]) => {
    if (Array.isArray(value)) sections.push([key, value]);
  });
  return sections;
}

function columnsForRows(rows: unknown[]): TableColumnsType<OverviewRecord> {
  const keys = [...new Set(rows.filter(isRecord).flatMap((row) => Object.keys(row)))].slice(0, 8);
  return keys.map((key) => ({
    title: key,
    dataIndex: key,
    key,
    width: 150,
    render: (value: unknown) => displayValue(value),
  }));
}

export function PrescriptionCountReportPage() {
  const [filters, setFilters] = useState<OverviewFilters>({ from: '', to: '', trendDays: '30' });
  const [overview, setOverview] = useState<OverviewRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const query = buildQuery(filters);
      const result = await request<unknown>(`/report-api/api/admin/reports/overview${query ? `?${query}` : ''}`);
      setOverview(isRecord(result) ? result : { value: result });
    } catch (loadError) {
      setOverview(null);
      setError(loadError instanceof Error ? loadError.message : '处方数量统计加载失败');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const summary = useMemo(() => (overview ? scalarEntries(overview) : []), [overview]);
  const sections = useMemo(() => (overview ? arraySections(overview) : []), [overview]);

  const exportOverview = () => {
    if (!overview) return;
    downloadCsv(
      '处方数量统计.csv',
      ['字段', '值'],
      scalarEntries(overview).map(([key, value]) => [key, displayValue(value)]),
    );
  };

  return (
    <QueryTableShell
      title="处方数量统计"
      subtitle="读取报表概览，展示订单、处方、回调状态和每日趋势等统计结果。"
      actions={
        <Button icon={<DownloadOutlined />} disabled={!overview} onClick={exportOverview}>
          导出当前汇总
        </Button>
      }
      filters={
        <div className="operation-filters">
          <Input placeholder="开始日期" value={filters.from} onChange={(event) => setFilters((previous) => ({ ...previous, from: event.target.value }))} />
          <Input placeholder="结束日期" value={filters.to} onChange={(event) => setFilters((previous) => ({ ...previous, to: event.target.value }))} />
          <Input placeholder="趋势天数" value={filters.trendDays} onChange={(event) => setFilters((previous) => ({ ...previous, trendDays: event.target.value }))} />
          <Space>
            <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={() => void refresh()}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} disabled={loading} onClick={() => void refresh()}>
              刷新
            </Button>
          </Space>
        </div>
      }
      table={
        <div className="report-overview">
          {error ? <Alert className="entity-list__alert" type="error" showIcon message={error} action={<Button onClick={() => void refresh()}>重试</Button>} /> : null}
          <Descriptions column={{ xs: 1, sm: 2, lg: 4 }} size="small" bordered>
            {summary.slice(0, 24).map(([key, value]) => (
              <Descriptions.Item key={key} label={key}>
                {displayValue(value)}
              </Descriptions.Item>
            ))}
          </Descriptions>
          {sections.map(([key, rows]) => (
            <Card key={key} className="report-overview__section" size="small" title={key}>
              <Table<OverviewRecord>
                rowKey={(_, index) => `${key}-${index ?? 0}`}
                size="small"
                columns={columnsForRows(rows)}
                dataSource={rows.filter(isRecord)}
                scroll={{ x: 'max-content' }}
                pagination={false}
                loading={loading}
              />
            </Card>
          ))}
        </div>
      }
    />
  );
}
