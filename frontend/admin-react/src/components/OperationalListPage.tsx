import { DownloadOutlined, EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Drawer, Input, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { OperationListQuery, OperationRecord, OperationValue } from '../api/operations';
import { downloadCsv } from '../utils/downloadCsv';
import { formatDate } from '../utils/formatters';
import { QueryTableShell } from './QueryTableShell';

export interface OperationColumn {
  title: string;
  dataIndex: string;
  width?: number;
  fallbackKeys?: string[];
  kind?: 'text' | 'code' | 'date' | 'status' | 'money' | 'count';
}

export interface OperationFilter {
  name: string;
  label: string;
  placeholder?: string;
  options?: { label: string; value: string }[];
}

interface OperationalListPageProps {
  title: string;
  subtitle: string;
  columns: OperationColumn[];
  load: (params: OperationListQuery) => Promise<{ records: OperationRecord[]; total: number; page: number; pageSize: number }>;
  filters?: OperationFilter[];
  rowActions?: (record: OperationRecord, refresh: () => Promise<void>) => React.ReactNode;
}

function readValue(record: OperationRecord, column: OperationColumn) {
  const keys = [column.dataIndex, ...(column.fallbackKeys ?? [])];
  for (const key of keys) {
    const value = record[key];
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return null;
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (Array.isArray(value)) return value.length ? value.map(displayValue).join('、') : '-';
  return JSON.stringify(value);
}

function renderValue(value: unknown, kind: OperationColumn['kind']) {
  const text = displayValue(value);
  if (kind === 'date') return text === '-' ? text : formatDate(text);
  if (kind === 'code') return <code className="entity-code">{text}</code>;
  if (kind === 'money') return text === '-' ? text : `¥${text}`;
  if (kind === 'status') {
    const color = /SUCCESS|ENABLE|DONE|SIGNED|CLOSED|已/.test(text) ? 'success' : /FAIL|ERROR|CANCEL|REJECT|异常|失败/.test(text) ? 'error' : 'processing';
    return <Tag color={color}>{text}</Tag>;
  }
  return text;
}

export function OperationalListPage({
  title,
  subtitle,
  columns,
  filters = [],
  load,
  rowActions,
}: OperationalListPageProps) {
  const [rows, setRows] = useState<OperationRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [filterValues, setFilterValues] = useState<Record<string, OperationValue>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<OperationRecord | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await load({ ...filterValues, page, pageSize });
      setRows(result.records);
      setTotal(result.total);
      if (result.page !== page) setPage(result.page);
      if (result.pageSize !== pageSize) setPageSize(result.pageSize);
    } catch (loadError) {
      setRows([]);
      setTotal(0);
      setError(loadError instanceof Error ? loadError.message : `${title}加载失败`);
    } finally {
      setLoading(false);
    }
  }, [filterValues, load, page, pageSize, title]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const tableColumns = useMemo<TableColumnsType<OperationRecord>>(
    () => [
      ...columns.map((column) => ({
        title: column.title,
        dataIndex: column.dataIndex,
        key: column.dataIndex,
        width: column.width,
        render: (_: unknown, record: OperationRecord) => renderValue(readValue(record, column), column.kind),
      })),
      {
        title: '操作',
        key: 'actions',
        fixed: 'right',
        width: rowActions ? 180 : 92,
        render: (_, record) => (
          <Space size={0}>
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setDetail(record)}>
              详情
            </Button>
            {rowActions?.(record, refresh)}
          </Space>
        ),
      },
    ],
    [columns, refresh, rowActions],
  );

  const exportRows = () => {
    downloadCsv(
      `${title}-第${page}页.csv`,
      columns.map((column) => column.title),
      rows.map((record) => columns.map((column) => displayValue(readValue(record, column)))),
    );
  };

  return (
    <>
      <QueryTableShell
        title={title}
        subtitle={subtitle}
        actions={
          <Button icon={<DownloadOutlined />} disabled={rows.length === 0} onClick={exportRows}>
            导出当前页
          </Button>
        }
        filters={
          <div className="operation-filters">
            {filters.map((filter) => (
              filter.options ? (
                <Select
                  key={filter.name}
                  allowClear
                  placeholder={filter.placeholder ?? filter.label}
                  options={filter.options}
                  value={filterValues[filter.name] as string | undefined}
                  onChange={(value) => {
                    setPage(1);
                    setFilterValues((previous) => ({ ...previous, [filter.name]: value }));
                  }}
                />
              ) : (
                <Input
                  key={filter.name}
                  allowClear
                  placeholder={filter.placeholder ?? filter.label}
                  value={(filterValues[filter.name] as string | undefined) ?? ''}
                  onChange={(event) => setFilterValues((previous) => ({ ...previous, [filter.name]: event.target.value }))}
                  onPressEnter={() => {
                    setPage(1);
                    void refresh();
                  }}
                />
              )
            ))}
            <Space>
              <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={() => {
                setPage(1);
                void refresh();
              }}>
                查询
              </Button>
              <Button icon={<ReloadOutlined />} disabled={loading} onClick={() => void refresh()}>
                刷新
              </Button>
            </Space>
          </div>
        }
        table={
          <>
            {error ? <Alert className="entity-list__alert" type="error" showIcon message={error} action={<Button onClick={() => void refresh()}>重试</Button>} /> : null}
            <Table<OperationRecord>
              rowKey="id"
              size="middle"
              loading={loading}
              columns={tableColumns}
              dataSource={rows}
              scroll={{ x: 'max-content' }}
              pagination={{
                current: page,
                pageSize,
                total,
                showSizeChanger: true,
                showTotal: (count) => `共 ${count} 条`,
                onChange: (nextPage, nextPageSize) => {
                  setPage(nextPageSize === pageSize ? nextPage : 1);
                  setPageSize(nextPageSize);
                },
              }}
            />
          </>
        }
      />
      <Drawer open={Boolean(detail)} title={`${title}详情`} width={680} onClose={() => setDetail(null)}>
        <Descriptions column={1} size="small" bordered>
          {detail ? Object.entries(detail).map(([key, value]) => (
            <Descriptions.Item key={key} label={key}>
              {displayValue(value)}
            </Descriptions.Item>
          )) : null}
        </Descriptions>
      </Drawer>
    </>
  );
}
