import { useQuery } from '@tanstack/react-query';
import { Tag, type TableColumnsType } from 'antd';
import { managementApi } from '../../api/management';
import type {
  AdminDecoctCenterRecord,
  AdminInstitutionApiPermissionRecord,
  AdminInstitutionApiRecord,
  AdminInstitutionIpWhitelistRecord,
  AdminInstitutionRecord,
  AdminOperatorRecord,
  AdminRbacRoleRecord,
  AdminSystemConfigRecord,
  CommonListQuery,
} from '../../api/management.types';
import { EntityListPage, type EntityFormField } from '../../components/EntityListPage';
import { StatusTag } from '../../components/StatusTag';
import { useAdminPermission } from '../../hooks/useAdminPermission';
import { formatDate } from '../../utils/formatters';
import { maskPhone } from '../../utils/masking';

const enabledLabels = { ENABLED: '已启用', DISABLED: '已停用' };

function enabledTag(enabled: boolean) {
  return <StatusTag value={enabled} labels={enabledLabels} />;
}

interface OperatorForm {
  username: string;
  displayName: string;
  roleCode: string;
  enabled: boolean;
}

const operatorColumns: TableColumnsType<AdminOperatorRecord> = [
  { title: '工号', dataIndex: 'username', width: 150 },
  { title: '姓名', dataIndex: 'displayName', width: 160 },
  { title: '角色', dataIndex: 'roleCode', width: 180, render: (value: string | null) => value || '未分配角色' },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => enabledTag(value) },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (value: string) => formatDate(value) },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

interface OperatorManagementPageProps {
  title?: string;
  subtitle?: string;
}

export function OperatorManagementPage({
  title = '工号管理',
  subtitle = '维护后台操作人员账号、角色和启停状态。',
}: OperatorManagementPageProps = {}) {
  const canWrite = useAdminPermission('system:write');
  const roles = useQuery({
    queryKey: ['management-role-options'],
    queryFn: () => managementApi.listRoles({ page: 1, pageSize: 100 }),
  });
  const fields: EntityFormField<OperatorForm>[] = [
    { name: 'username', label: '工号', required: true, disabledWhenEditing: true, placeholder: '请输入登录工号' },
    { name: 'displayName', label: '姓名', required: true, placeholder: '请输入操作员姓名' },
    {
      name: 'roleCode',
      label: '角色',
      kind: 'select',
      placeholder: '请选择角色',
      options: roles.data?.records.map((role: AdminRbacRoleRecord) => ({
        label: `${role.roleName}（${role.roleCode}）`,
        value: role.roleCode,
        disabled: !role.enabled,
      })) ?? [],
      wide: true,
    },
    { name: 'enabled', label: '状态', kind: 'switch', wide: true },
  ];
  return (
    <EntityListPage<AdminOperatorRecord, OperatorForm>
      title={title}
      subtitle={subtitle}
      entityName="工号"
      columns={operatorColumns}
      fields={fields}
      initialValues={{ username: '', displayName: '', roleCode: '', enabled: true }}
      valuesFromRecord={(record) => ({
        username: record.username,
        displayName: record.displayName,
        roleCode: record.roleCode ?? '',
        enabled: record.enabled,
      })}
      load={managementApi.listOperators}
      create={managementApi.createOperator}
      update={managementApi.updateOperator}
      canWrite={canWrite}
      csvColumns={[
        { title: '工号', value: (record) => record.username },
        { title: '姓名', value: (record) => record.displayName },
        { title: '角色', value: (record) => record.roleCode },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
        { title: '更新时间', value: (record) => record.updatedAt },
      ]}
    />
  );
}

interface SystemConfigForm {
  configKey: string;
  configName: string;
  configValue: string;
  valueType: string;
  enabled: boolean;
  remark: string;
}

const configColumns: TableColumnsType<AdminSystemConfigRecord> = [
  { title: '参数名称', dataIndex: 'configName', width: 180 },
  { title: '参数键', dataIndex: 'configKey', width: 220, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '参数值', dataIndex: 'configValue', width: 240, ellipsis: true },
  { title: '值类型', dataIndex: 'valueType', width: 110 },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => enabledTag(value) },
  { title: '备注', dataIndex: 'remark', width: 220, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

export function SystemConfigPage() {
  const canWrite = useAdminPermission('system:write');
  const fields: EntityFormField<SystemConfigForm>[] = [
    { name: 'configKey', label: '参数键', required: true, disabledWhenEditing: true, wide: true },
    { name: 'configName', label: '参数名称', required: true },
    {
      name: 'valueType', label: '值类型', kind: 'select', required: true,
      options: ['STRING', 'NUMBER', 'BOOLEAN', 'JSON'].map((value) => ({ label: value, value })),
    },
    { name: 'configValue', label: '参数值', kind: 'textarea', required: true, wide: true },
    { name: 'remark', label: '备注', kind: 'textarea', wide: true },
    { name: 'enabled', label: '状态', kind: 'switch', wide: true },
  ];
  return (
    <EntityListPage<AdminSystemConfigRecord, SystemConfigForm>
      title="参数配置"
      subtitle="查询和维护系统运行参数；参数键创建后不可修改。"
      entityName="参数"
      columns={configColumns}
      fields={fields}
      initialValues={{ configKey: '', configName: '', configValue: '', valueType: 'STRING', enabled: true, remark: '' }}
      valuesFromRecord={(record) => ({
        configKey: record.configKey,
        configName: record.configName,
        configValue: record.configValue,
        valueType: record.valueType,
        enabled: record.enabled,
        remark: record.remark ?? '',
      })}
      load={managementApi.listSystemConfigs}
      create={managementApi.createSystemConfig}
      update={managementApi.updateSystemConfig}
      canWrite={canWrite}
      csvColumns={configColumns.slice(0, 6).map((column, index) => ({
        title: String(column.title),
        value: (record) => [record.configName, record.configKey, record.configValue, record.valueType, record.enabled, record.remark][index],
      }))}
    />
  );
}

interface CenterForm {
  centerCode: string;
  centerName: string;
  contactName: string;
  contactPhone: string;
  address: string;
  enabled: boolean;
  remark: string;
}

const centerColumns: TableColumnsType<AdminDecoctCenterRecord> = [
  { title: '中心编码', dataIndex: 'centerCode', width: 150 },
  { title: '中心名称', dataIndex: 'centerName', width: 200 },
  { title: '联系人', dataIndex: 'contactName', width: 130 },
  { title: '联系电话', dataIndex: 'contactPhone', width: 150, render: (value: string | null) => maskPhone(value ?? '') },
  { title: '地址', dataIndex: 'address', width: 260, ellipsis: true },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => enabledTag(value) },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

export function DecoctCenterPage() {
  const canWrite = useAdminPermission('system:write');
  return (
    <EntityListPage<AdminDecoctCenterRecord, CenterForm>
      title="煎煮中心配置"
      subtitle="维护煎煮中心基础档案、联系方式和启停状态。"
      entityName="煎煮中心"
      columns={centerColumns}
      fields={[
        { name: 'centerCode', label: '中心编码', required: true, disabledWhenEditing: true },
        { name: 'centerName', label: '中心名称', required: true },
        { name: 'contactName', label: '联系人' },
        { name: 'contactPhone', label: '联系电话' },
        { name: 'address', label: '地址', kind: 'textarea', wide: true },
        { name: 'remark', label: '备注', kind: 'textarea', wide: true },
        { name: 'enabled', label: '状态', kind: 'switch', wide: true },
      ]}
      initialValues={{ centerCode: '', centerName: '', contactName: '', contactPhone: '', address: '', enabled: true, remark: '' }}
      valuesFromRecord={(record) => ({
        centerCode: record.centerCode,
        centerName: record.centerName,
        contactName: record.contactName ?? '',
        contactPhone: record.contactPhone ?? '',
        address: record.address ?? '',
        enabled: record.enabled,
        remark: record.remark ?? '',
      })}
      load={managementApi.listDecoctCenters}
      create={managementApi.createDecoctCenter}
      update={managementApi.updateDecoctCenter}
      canWrite={canWrite}
      csvColumns={[
        { title: '中心编码', value: (record) => record.centerCode },
        { title: '中心名称', value: (record) => record.centerName },
        { title: '联系人', value: (record) => record.contactName },
        { title: '联系电话', value: (record) => maskPhone(record.contactPhone ?? '') },
        { title: '地址', value: (record) => record.address },
      ]}
    />
  );
}

interface InstitutionForm {
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  status: string;
  storageType: string;
}

const institutionColumns: TableColumnsType<AdminInstitutionRecord> = [
  {
    title: '机构', key: 'institution', width: 240,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.institutionName}</strong><span>{record.institutionCode}</span></div>,
  },
  { title: '机构类型', dataIndex: 'institutionType', width: 140 },
  { title: '存储类型', dataIndex: 'storageType', width: 140, render: (value: string | null) => value || '-' },
  { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '已启用' : value}</Tag> },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (value: string) => formatDate(value) },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

function loadInstitutions(params: CommonListQuery) {
  return managementApi.listInstitutions({ keyword: params.keyword, page: params.page, pageSize: params.pageSize });
}

export function InstitutionListPage() {
  const canWrite = useAdminPermission('institution:write');
  return (
    <EntityListPage<AdminInstitutionRecord, InstitutionForm>
      title="机构列表"
      subtitle="维护医院和合作机构档案及接入状态。"
      entityName="机构"
      columns={institutionColumns}
      fields={[
        { name: 'institutionCode', label: '机构编码', required: true, disabledWhenEditing: true },
        { name: 'institutionName', label: '机构名称', required: true },
        { name: 'institutionType', label: '机构类型', kind: 'select', required: true, options: [
          { label: '医院', value: 'HOSPITAL' }, { label: '药房', value: 'PHARMACY' }, { label: '平台', value: 'PLATFORM' },
        ] },
        { name: 'status', label: '状态', kind: 'select', required: true, options: [
          { label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' },
        ] },
        { name: 'storageType', label: '存储类型', kind: 'select', wide: true, options: [
          { label: '平台存储', value: 'PLATFORM' }, { label: '机构存储', value: 'INSTITUTION' },
        ] },
      ]}
      initialValues={{ institutionCode: '', institutionName: '', institutionType: 'HOSPITAL', status: 'ENABLED', storageType: 'PLATFORM' }}
      valuesFromRecord={(record) => ({
        institutionCode: record.institutionCode,
        institutionName: record.institutionName,
        institutionType: record.institutionType,
        status: record.status,
        storageType: record.storageType ?? 'PLATFORM',
      })}
      load={loadInstitutions}
      create={managementApi.createInstitution}
      update={managementApi.updateInstitution}
      canWrite={canWrite}
      showEnabledFilter={false}
      csvColumns={[
        { title: '机构编码', value: (record) => record.institutionCode },
        { title: '机构名称', value: (record) => record.institutionName },
        { title: '机构类型', value: (record) => record.institutionType },
        { title: '状态', value: (record) => record.status },
      ]}
    />
  );
}

interface IpForm {
  institutionId: string;
  ipRange: string;
  enabled: boolean;
}

const ipColumns: TableColumnsType<AdminInstitutionIpWhitelistRecord> = [
  {
    title: '机构', key: 'institution', width: 240,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.institutionName}</strong><span>{record.institutionCode}</span></div>,
  },
  { title: 'IP / 网段', dataIndex: 'ipRange', width: 220, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '机构类型', dataIndex: 'institutionType', width: 140 },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => enabledTag(value) },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (value: string) => formatDate(value) },
];

export function InstitutionIpWhitelistPage() {
  const canWrite = useAdminPermission('institution:write');
  const institutions = useQuery({ queryKey: ['institution-options'], queryFn: () => managementApi.listInstitutions({ page: 1, pageSize: 100 }) });
  return (
    <EntityListPage<AdminInstitutionIpWhitelistRecord, IpForm>
      title="机构 IP 白名单列表"
      subtitle="限制机构接口调用来源；支持单个 IP 和 CIDR 网段。"
      entityName="IP 白名单"
      columns={ipColumns}
      fields={[
        { name: 'institutionId', label: '机构', kind: 'select', required: true, disabledWhenEditing: true, wide: true,
          options: institutions.data?.records.map((record) => ({ label: `${record.institutionName}（${record.institutionCode}）`, value: record.id })) ?? [] },
        { name: 'ipRange', label: 'IP / CIDR', required: true, placeholder: '例如 10.0.0.8 或 10.0.0.0/24', wide: true },
        { name: 'enabled', label: '状态', kind: 'switch', wide: true },
      ]}
      initialValues={{ institutionId: '', ipRange: '', enabled: true }}
      valuesFromRecord={(record) => ({ institutionId: record.institutionId, ipRange: record.ipRange, enabled: record.enabled })}
      load={managementApi.listIpWhitelists}
      create={managementApi.createIpWhitelist}
      update={managementApi.updateIpWhitelist}
      canWrite={canWrite}
      csvColumns={[
        { title: '机构', value: (record) => record.institutionName },
        { title: '机构编码', value: (record) => record.institutionCode },
        { title: 'IP / 网段', value: (record) => record.ipRange },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
      ]}
    />
  );
}

interface ApiForm {
  apiCode: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  description: string;
  enabled: boolean;
}

const apiColumns: TableColumnsType<AdminInstitutionApiRecord> = [
  {
    title: '接口', key: 'api', width: 240,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.apiName}</strong><span>{record.apiCode}</span></div>,
  },
  { title: '请求方法', dataIndex: 'requestMethod', width: 110, render: (value: string) => <Tag color="blue">{value}</Tag> },
  { title: '请求路径', dataIndex: 'requestPath', width: 360, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '接口描述', dataIndex: 'description', width: 240, ellipsis: true },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => enabledTag(value) },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

export function InstitutionApiListPage() {
  const canWrite = useAdminPermission('institution:write');
  return (
    <EntityListPage<AdminInstitutionApiRecord, ApiForm>
      title="接口列表"
      subtitle="维护外部 API 定义、请求方法、路径和启停状态。"
      entityName="接口"
      columns={apiColumns}
      fields={[
        { name: 'apiCode', label: '接口编码', required: true, disabledWhenEditing: true },
        { name: 'apiName', label: '接口名称', required: true },
        { name: 'requestMethod', label: '请求方法', kind: 'select', required: true, options: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((value) => ({ label: value, value })) },
        { name: 'requestPath', label: '请求路径', required: true, placeholder: '/api/...' },
        { name: 'description', label: '接口描述', kind: 'textarea', wide: true },
        { name: 'enabled', label: '状态', kind: 'switch', wide: true },
      ]}
      initialValues={{ apiCode: '', apiName: '', requestMethod: 'POST', requestPath: '', description: '', enabled: true }}
      valuesFromRecord={(record) => ({
        apiCode: record.apiCode,
        apiName: record.apiName,
        requestMethod: record.requestMethod,
        requestPath: record.requestPath,
        description: record.description ?? '',
        enabled: record.enabled,
      })}
      load={managementApi.listInstitutionApis}
      create={managementApi.createInstitutionApi}
      update={managementApi.updateInstitutionApi}
      canWrite={canWrite}
      csvColumns={[
        { title: '接口编码', value: (record) => record.apiCode },
        { title: '接口名称', value: (record) => record.apiName },
        { title: '请求方法', value: (record) => record.requestMethod },
        { title: '请求路径', value: (record) => record.requestPath },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
      ]}
    />
  );
}

interface ApiPermissionForm {
  institutionId: string;
  apiId: string;
  remark: string;
  enabled: boolean;
}

const permissionColumns: TableColumnsType<AdminInstitutionApiPermissionRecord> = [
  {
    title: '机构', key: 'institution', width: 220,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.institutionName}</strong><span>{record.institutionCode}</span></div>,
  },
  {
    title: '接口', key: 'api', width: 220,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.apiName}</strong><span>{record.apiCode}</span></div>,
  },
  { title: '方法', dataIndex: 'requestMethod', width: 90, render: (value: string) => <Tag color="blue">{value}</Tag> },
  { title: '路径', dataIndex: 'requestPath', width: 320, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => enabledTag(value) },
  { title: '备注', dataIndex: 'remark', width: 220, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

export function InstitutionApiPermissionPage() {
  const canWrite = useAdminPermission('institution:write');
  const institutions = useQuery({ queryKey: ['institution-options'], queryFn: () => managementApi.listInstitutions({ page: 1, pageSize: 100 }) });
  const apis = useQuery({ queryKey: ['institution-api-options'], queryFn: () => managementApi.listInstitutionApis({ page: 1, pageSize: 100 }) });
  return (
    <EntityListPage<AdminInstitutionApiPermissionRecord, ApiPermissionForm>
      title="机构接口权限列表"
      subtitle="维护机构可访问的 API 范围和授权状态。"
      entityName="接口授权"
      columns={permissionColumns}
      fields={[
        { name: 'institutionId', label: '机构', kind: 'select', required: true, disabledWhenEditing: true, wide: true,
          options: institutions.data?.records.map((record) => ({ label: `${record.institutionName}（${record.institutionCode}）`, value: record.id })) ?? [] },
        { name: 'apiId', label: '接口', kind: 'select', required: true, disabledWhenEditing: true, wide: true,
          options: apis.data?.records.map((record) => ({ label: `${record.apiName}（${record.apiCode}）`, value: record.id, disabled: !record.enabled })) ?? [] },
        { name: 'remark', label: '备注', kind: 'textarea', wide: true },
        { name: 'enabled', label: '状态', kind: 'switch', wide: true },
      ]}
      initialValues={{ institutionId: '', apiId: '', remark: '', enabled: true }}
      valuesFromRecord={(record) => ({ institutionId: record.institutionId, apiId: record.apiId, remark: record.remark ?? '', enabled: record.enabled })}
      load={managementApi.listApiPermissions}
      create={managementApi.createApiPermission}
      update={managementApi.updateApiPermission}
      canWrite={canWrite}
      csvColumns={[
        { title: '机构', value: (record) => record.institutionName },
        { title: '接口', value: (record) => record.apiName },
        { title: '方法', value: (record) => record.requestMethod },
        { title: '路径', value: (record) => record.requestPath },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
      ]}
    />
  );
}
