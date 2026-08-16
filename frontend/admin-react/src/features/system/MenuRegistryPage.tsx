import { SearchOutlined } from '@ant-design/icons';
import { Input, Select, Table, Tag, type TableColumnsType } from 'antd';
import { useMemo, useState } from 'react';
import { QueryTableShell } from '../../components/QueryTableShell';
import { menuGroups, type AdminMenuItem } from '../../routes/menu';

interface MenuRegistryRow extends AdminMenuItem {
  order: number;
}

const columns: TableColumnsType<MenuRegistryRow> = [
  { title: '序号', dataIndex: 'order', width: 80 },
  { title: '父级菜单', dataIndex: 'parentLabel', width: 140 },
  {
    title: '菜单名称',
    key: 'menu',
    width: 220,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.label}</strong><span>{record.key}</span></div>,
  },
  { title: 'React 路由', dataIndex: 'path', width: 260, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '旧项目路由', dataIndex: 'legacyRoute', width: 300, render: (value: string) => <code className="entity-code">{value}</code> },
  {
    title: '迁移状态',
    dataIndex: 'implemented',
    width: 120,
    render: (value: boolean) => <Tag color={value ? 'success' : 'warning'}>{value ? '已迁移' : '待完善'}</Tag>,
  },
];

export function MenuRegistryPage() {
  const [keyword, setKeyword] = useState('');
  const [parentKey, setParentKey] = useState('');
  const rows = useMemo(() => menuGroups.flatMap((group) => group.children).map((item, index) => ({ ...item, order: index + 1 })), []);
  const filteredRows = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return rows.filter((row) => {
      if (parentKey && row.parentKey !== parentKey) return false;
      if (!normalized) return true;
      return [row.label, row.key, row.path, row.legacyRoute].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [keyword, parentKey, rows]);

  return (
    <QueryTableShell
      title="菜单管理"
      subtitle="核对父子菜单、React 路由、旧项目路由和实际迁移状态。"
      filters={
        <div className="entity-filters">
          <Input allowClear prefix={<SearchOutlined />} value={keyword} placeholder="搜索名称或路由" onChange={(event) => setKeyword(event.target.value)} />
          <Select
            value={parentKey}
            options={[{ label: '全部父菜单', value: '' }, ...menuGroups.map((group) => ({ label: group.label, value: group.key }))]}
            onChange={setParentKey}
          />
        </div>
      }
      table={
        <Table<MenuRegistryRow>
          rowKey="key"
          size="middle"
          columns={columns}
          dataSource={filteredRows}
          scroll={{ x: 'max-content' }}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
        />
      }
    />
  );
}
