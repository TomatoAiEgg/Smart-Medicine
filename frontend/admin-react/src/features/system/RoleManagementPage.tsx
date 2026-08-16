import { useQuery } from '@tanstack/react-query';
import { Tag, type TableColumnsType } from 'antd';
import { managementApi } from '../../api/management';
import type { AdminRbacRoleRecord } from '../../api/management.types';
import { EntityListPage, type EntityFormField } from '../../components/EntityListPage';
import { StatusTag } from '../../components/StatusTag';
import { useAdminPermission } from '../../hooks/useAdminPermission';
import { formatDate } from '../../utils/formatters';

interface RoleForm {
  roleCode: string;
  roleName: string;
  dataScopeType: 'TENANT' | 'INSTITUTION';
  enabled: boolean;
  version?: number;
  permissionCodes: string[];
  institutionIds: string[];
}

const columns: TableColumnsType<AdminRbacRoleRecord> = [
  {
    title: '角色',
    key: 'role',
    width: 220,
    render: (_, record) => (
      <div className="entity-primary-cell">
        <strong>{record.roleName}</strong>
        <span>{record.roleCode}</span>
      </div>
    ),
  },
  {
    title: '数据范围',
    dataIndex: 'dataScopeType',
    width: 120,
    render: (value: RoleForm['dataScopeType']) => value === 'TENANT' ? '全部机构' : '指定机构',
  },
  { title: '成员数', dataIndex: 'operatorCount', width: 100 },
  {
    title: '权限数',
    dataIndex: 'permissionCodes',
    width: 100,
    render: (value: string[]) => value.length,
  },
  {
    title: '类型',
    dataIndex: 'builtIn',
    width: 100,
    render: (value: boolean) => <Tag color={value ? 'blue' : 'default'}>{value ? '内置' : '自定义'}</Tag>,
  },
  {
    title: '状态',
    dataIndex: 'enabled',
    width: 100,
    render: (value: boolean) => <StatusTag value={value} labels={{ ENABLED: '已启用', DISABLED: '已停用' }} />,
  },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

export function RoleManagementPage() {
  const canWrite = useAdminPermission('system:write');
  const catalog = useQuery({ queryKey: ['rbac-catalog'], queryFn: managementApi.getRoleCatalog });
  const fields: EntityFormField<RoleForm>[] = [
    { name: 'roleCode', label: '角色编码', required: true, disabledWhenEditing: true },
    { name: 'roleName', label: '角色名称', required: true },
    {
      name: 'dataScopeType',
      label: '数据范围',
      kind: 'select',
      required: true,
      options: [
        { label: '全部机构', value: 'TENANT' },
        { label: '指定机构', value: 'INSTITUTION' },
      ],
    },
    { name: 'enabled', label: '状态', kind: 'switch' },
    {
      name: 'permissionCodes',
      label: '功能权限',
      kind: 'multiselect',
      required: true,
      wide: true,
      options: catalog.data?.permissions.map((permission) => ({
        label: `${permission.permissionName}（${permission.permissionCode}）`,
        value: permission.permissionCode,
      })) ?? [],
    },
    {
      name: 'institutionIds',
      label: '授权机构',
      kind: 'multiselect',
      wide: true,
      options: catalog.data?.institutions.map((institution) => ({
        label: `${institution.institutionName}（${institution.institutionCode}）`,
        value: institution.institutionId,
        disabled: institution.status !== 'ENABLED',
      })) ?? [],
    },
  ];

  return (
    <EntityListPage<AdminRbacRoleRecord, RoleForm>
      title="角色管理"
      subtitle="维护角色、功能权限与机构数据范围；内置角色不可删除。"
      entityName="角色"
      columns={columns}
      fields={fields}
      initialValues={{
        roleCode: '',
        roleName: '',
        dataScopeType: 'TENANT',
        enabled: true,
        permissionCodes: [],
        institutionIds: [],
      }}
      valuesFromRecord={(record) => ({
        roleCode: record.roleCode,
        roleName: record.roleName,
        dataScopeType: record.dataScopeType,
        enabled: record.enabled,
        version: record.version,
        permissionCodes: record.permissionCodes,
        institutionIds: record.institutionIds,
      })}
      load={(query) => managementApi.listRoles({
        keyword: query.keyword,
        page: query.page,
        pageSize: query.pageSize,
      })}
      showEnabledFilter={false}
      create={managementApi.createRole}
      update={managementApi.updateRole}
      remove={managementApi.deleteRole}
      canRemove={(record) => !record.builtIn && record.operatorCount === 0}
      canWrite={canWrite}
      csvColumns={[
        { title: '角色编码', value: (record) => record.roleCode },
        { title: '角色名称', value: (record) => record.roleName },
        { title: '数据范围', value: (record) => record.dataScopeType },
        { title: '成员数', value: (record) => record.operatorCount },
        { title: '权限数', value: (record) => record.permissionCodes.length },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
      ]}
    />
  );
}
