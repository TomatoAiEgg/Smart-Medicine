import { PlusOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Space } from 'antd';
import { QueryTableShell } from '../../components/QueryTableShell';
import { StatusTag } from '../../components/StatusTag';
import { formatDate } from '../../utils/formatters';
import { maskPhone } from '../../utils/masking';

interface UserRecord {
  id: string;
  username: string;
  realName: string;
  roleName: string;
  tenantName: string;
  phone: string;
  createdAt: string;
  lastLoginAt: string | null;
  enabled: boolean;
}

const statusLabels: Record<string, string> = {
  ENABLED: '已启用',
  DISABLED: '已停用',
};

const columns: ProColumns<UserRecord>[] = [
  {
    title: '用户ID',
    dataIndex: 'id',
    width: 120,
    ellipsis: true,
  },
  {
    title: '用户名',
    dataIndex: 'username',
    width: 140,
    ellipsis: true,
  },
  {
    title: '姓名',
    dataIndex: 'realName',
    width: 120,
    ellipsis: true,
  },
  {
    title: '角色',
    dataIndex: 'roleName',
    width: 160,
    ellipsis: true,
  },
  {
    title: '租户',
    dataIndex: 'tenantName',
    width: 180,
    ellipsis: true,
  },
  {
    title: '手机号',
    dataIndex: 'phone',
    width: 140,
    ellipsis: true,
    render: (_, row) => maskPhone(row.phone),
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    width: 170,
    valueType: 'dateTime',
    render: (_, row) => formatDate(row.createdAt),
  },
  {
    title: '最近登录时间',
    dataIndex: 'lastLoginAt',
    width: 170,
    valueType: 'dateTime',
    render: (_, row) => formatDate(row.lastLoginAt),
  },
  {
    title: '状态',
    dataIndex: 'enabled',
    width: 100,
    valueType: 'select',
    valueEnum: {
      true: { text: '已启用' },
      false: { text: '已停用' },
    },
    render: (_, row) => <StatusTag value={row.enabled} labels={statusLabels} />,
  },
  {
    title: '操作',
    valueType: 'option',
    width: 150,
    fixed: 'right',
    render: () => (
      <Space size={4}>
        <Button type="link" size="small">
          修改
        </Button>
        <Button type="link" size="small">
          查看权限
        </Button>
      </Space>
    ),
  },
];

export function UserManagementPage() {
  return (
    <QueryTableShell
      title="用户管理"
      subtitle="后台操作人员账号、角色标识和启停状态维护。"
      actions={
        <>
          <Button type="primary" icon={<PlusOutlined />}>
            新增
          </Button>
          <Button>导出</Button>
        </>
      }
      filters={null}
      table={
        <ProTable<UserRecord>
          rowKey="id"
          columns={columns}
          options={false}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          scroll={{ x: 1450 }}
          request={async () => ({ data: [], success: true, total: 0 })}
          locale={{ emptyText: '暂无用户账号数据，请调整查询条件或新增用户。' }}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
