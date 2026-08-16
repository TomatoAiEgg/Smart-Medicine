import { request } from './client';
import type {
  AdminDecoctCenterCommand,
  AdminDecoctCenterPage,
  AdminDecoctCenterRecord,
  AdminDictItemCommand,
  AdminDictItemPage,
  AdminDictItemRecord,
  AdminDictTypeCommand,
  AdminDictTypePage,
  AdminDictTypeRecord,
  AdminInstitutionApiCommand,
  AdminInstitutionApiPage,
  AdminInstitutionApiPermissionCommand,
  AdminInstitutionApiPermissionPage,
  AdminInstitutionApiPermissionRecord,
  AdminInstitutionApiRecord,
  AdminInstitutionCommand,
  AdminInstitutionIpWhitelistCommand,
  AdminInstitutionIpWhitelistPage,
  AdminInstitutionIpWhitelistRecord,
  AdminInstitutionPage,
  AdminInstitutionRecord,
  AdminOperatorCommand,
  AdminOperatorPage,
  AdminOperatorRecord,
  AdminRbacCatalog,
  AdminRbacRoleCommand,
  AdminRbacRolePage,
  AdminRbacRoleRecord,
  AdminSystemConfigCommand,
  AdminSystemConfigPage,
  AdminSystemConfigRecord,
  CommonListQuery,
} from './management.types';

interface InstitutionQuery extends CommonListQuery {
  status?: string;
  institutionType?: string;
}

interface InstitutionChildQuery extends CommonListQuery {
  institutionId?: string;
}

interface ApiPermissionQuery extends InstitutionChildQuery {
  apiId?: string;
}

interface DictItemQuery extends CommonListQuery {
  typeId?: string;
}

interface SystemConfigQuery extends CommonListQuery {
  valueType?: string;
}

function buildQuery(params: object) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
  });
  return query.toString();
}

function listUrl(path: string, params: object) {
  const query = buildQuery(params);
  return query ? `${path}?${query}` : path;
}

function createResource<TRecord, TCommand>(path: string, command: TCommand) {
  return request<TRecord>(path, { method: 'POST', body: JSON.stringify(command) });
}

function updateResource<TRecord, TCommand>(path: string, id: string, command: TCommand, method = 'PATCH') {
  return request<TRecord>(`${path}/${encodeURIComponent(id)}`, { method, body: JSON.stringify(command) });
}

export const managementApi = {
  listOperators: (params: CommonListQuery = {}) =>
    request<AdminOperatorPage>(listUrl('/order-api/api/admin/operators', params)),
  createOperator: (command: AdminOperatorCommand) =>
    createResource<AdminOperatorRecord, AdminOperatorCommand>('/order-api/api/admin/operators', command),
  updateOperator: (id: string, command: AdminOperatorCommand) =>
    updateResource<AdminOperatorRecord, AdminOperatorCommand>('/order-api/api/admin/operators', id, command),

  listRoles: (params: CommonListQuery = {}) =>
    request<AdminRbacRolePage>(listUrl('/order-api/api/admin/rbac/roles', params)),
  getRoleCatalog: () => request<AdminRbacCatalog>('/order-api/api/admin/rbac/catalog'),
  createRole: (command: AdminRbacRoleCommand) =>
    createResource<AdminRbacRoleRecord, AdminRbacRoleCommand>('/order-api/api/admin/rbac/roles', command),
  updateRole: (id: string, command: AdminRbacRoleCommand) =>
    updateResource<AdminRbacRoleRecord, AdminRbacRoleCommand>('/order-api/api/admin/rbac/roles', id, command, 'PUT'),
  deleteRole: (id: string) =>
    request<void>(`/order-api/api/admin/rbac/roles/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  listDictTypes: (params: CommonListQuery = {}) =>
    request<AdminDictTypePage>(listUrl('/order-api/api/admin/dict-types', params)),
  createDictType: (command: AdminDictTypeCommand) =>
    createResource<AdminDictTypeRecord, AdminDictTypeCommand>('/order-api/api/admin/dict-types', command),
  updateDictType: (id: string, command: AdminDictTypeCommand) =>
    updateResource<AdminDictTypeRecord, AdminDictTypeCommand>('/order-api/api/admin/dict-types', id, command),
  listDictItems: (params: DictItemQuery = {}) =>
    request<AdminDictItemPage>(listUrl('/order-api/api/admin/dict-items', params)),
  createDictItem: (command: AdminDictItemCommand) =>
    createResource<AdminDictItemRecord, AdminDictItemCommand>('/order-api/api/admin/dict-items', command),
  updateDictItem: (id: string, command: AdminDictItemCommand) =>
    updateResource<AdminDictItemRecord, AdminDictItemCommand>('/order-api/api/admin/dict-items', id, command),

  listSystemConfigs: (params: SystemConfigQuery = {}) =>
    request<AdminSystemConfigPage>(listUrl('/order-api/api/admin/system-configs', params)),
  createSystemConfig: (command: AdminSystemConfigCommand) =>
    createResource<AdminSystemConfigRecord, AdminSystemConfigCommand>('/order-api/api/admin/system-configs', command),
  updateSystemConfig: (id: string, command: AdminSystemConfigCommand) =>
    updateResource<AdminSystemConfigRecord, AdminSystemConfigCommand>('/order-api/api/admin/system-configs', id, command),

  listDecoctCenters: (params: CommonListQuery = {}) =>
    request<AdminDecoctCenterPage>(listUrl('/order-api/api/admin/decoct-centers', params)),
  createDecoctCenter: (command: AdminDecoctCenterCommand) =>
    createResource<AdminDecoctCenterRecord, AdminDecoctCenterCommand>('/order-api/api/admin/decoct-centers', command),
  updateDecoctCenter: (id: string, command: AdminDecoctCenterCommand) =>
    updateResource<AdminDecoctCenterRecord, AdminDecoctCenterCommand>('/order-api/api/admin/decoct-centers', id, command),

  listInstitutions: (params: InstitutionQuery = {}) =>
    request<AdminInstitutionPage>(listUrl('/order-api/api/admin/institutions', params)),
  createInstitution: (command: AdminInstitutionCommand) =>
    createResource<AdminInstitutionRecord, AdminInstitutionCommand>('/order-api/api/admin/institutions', command),
  updateInstitution: (id: string, command: AdminInstitutionCommand) =>
    updateResource<AdminInstitutionRecord, AdminInstitutionCommand>('/order-api/api/admin/institutions', id, command),

  listIpWhitelists: (params: InstitutionChildQuery = {}) =>
    request<AdminInstitutionIpWhitelistPage>(listUrl('/order-api/api/admin/institution-ip-whitelists', params)),
  createIpWhitelist: (command: AdminInstitutionIpWhitelistCommand) =>
    createResource<AdminInstitutionIpWhitelistRecord, AdminInstitutionIpWhitelistCommand>(
      '/order-api/api/admin/institution-ip-whitelists', command,
    ),
  updateIpWhitelist: (id: string, command: AdminInstitutionIpWhitelistCommand) =>
    updateResource<AdminInstitutionIpWhitelistRecord, AdminInstitutionIpWhitelistCommand>(
      '/order-api/api/admin/institution-ip-whitelists', id, command,
    ),

  listInstitutionApis: (params: CommonListQuery = {}) =>
    request<AdminInstitutionApiPage>(listUrl('/order-api/api/admin/institution-apis', params)),
  createInstitutionApi: (command: AdminInstitutionApiCommand) =>
    createResource<AdminInstitutionApiRecord, AdminInstitutionApiCommand>('/order-api/api/admin/institution-apis', command),
  updateInstitutionApi: (id: string, command: AdminInstitutionApiCommand) =>
    updateResource<AdminInstitutionApiRecord, AdminInstitutionApiCommand>('/order-api/api/admin/institution-apis', id, command),

  listApiPermissions: (params: ApiPermissionQuery = {}) =>
    request<AdminInstitutionApiPermissionPage>(listUrl('/order-api/api/admin/institution-api-permissions', params)),
  createApiPermission: (command: AdminInstitutionApiPermissionCommand) =>
    createResource<AdminInstitutionApiPermissionRecord, AdminInstitutionApiPermissionCommand>(
      '/order-api/api/admin/institution-api-permissions', command,
    ),
  updateApiPermission: (id: string, command: AdminInstitutionApiPermissionCommand) =>
    updateResource<AdminInstitutionApiPermissionRecord, AdminInstitutionApiPermissionCommand>(
      '/order-api/api/admin/institution-api-permissions', id, command,
    ),
};
