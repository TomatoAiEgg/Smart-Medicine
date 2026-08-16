export interface PageResult<TRecord> {
  records: TRecord[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CommonListQuery {
  keyword?: string;
  enabled?: boolean | string;
  page?: number;
  pageSize?: number;
}

export interface AdminOperatorRecord {
  id: string;
  tenantId: string;
  username: string;
  displayName: string;
  roleCode: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AdminOperatorPage = PageResult<AdminOperatorRecord>;

export interface AdminOperatorCommand {
  username?: string;
  displayName: string;
  roleCode?: string;
  enabled?: boolean;
}

export type AdminRbacDataScopeType = 'TENANT' | 'INSTITUTION';

export interface AdminRbacRoleRecord {
  id: string;
  tenantId: string;
  roleCode: string;
  roleName: string;
  dataScopeType: AdminRbacDataScopeType;
  builtIn: boolean;
  enabled: boolean;
  version: number;
  operatorCount: number;
  permissionCodes: string[];
  institutionIds: string[];
  createdAt: string;
  updatedAt: string;
}

export type AdminRbacRolePage = PageResult<AdminRbacRoleRecord>;

export interface AdminRbacPermissionOption {
  permissionCode: string;
  permissionName: string;
  resourceType: string;
  httpMethod: string | null;
  resourcePattern: string | null;
}

export interface AdminRbacInstitutionOption {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  status: string;
}

export interface AdminRbacCatalog {
  permissions: AdminRbacPermissionOption[];
  institutions: AdminRbacInstitutionOption[];
}

export interface AdminRbacRoleCommand {
  roleCode: string;
  roleName: string;
  dataScopeType: AdminRbacDataScopeType;
  enabled: boolean;
  version?: number;
  permissionCodes: string[];
  institutionIds: string[];
}

export interface AdminDictTypeRecord {
  id: string;
  tenantId: string;
  typeCode: string;
  typeName: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AdminDictTypePage = PageResult<AdminDictTypeRecord>;

export interface AdminDictTypeCommand {
  typeCode?: string;
  typeName: string;
  enabled?: boolean;
}

export interface AdminDictItemRecord {
  id: string;
  tenantId: string;
  typeId: string;
  typeCode: string;
  typeName: string;
  itemCode: string;
  itemName: string;
  itemValue: string | null;
  sortNo: number;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AdminDictItemPage = PageResult<AdminDictItemRecord>;

export interface AdminDictItemCommand {
  typeId?: string;
  itemCode?: string;
  itemName: string;
  itemValue?: string;
  sortNo?: number;
  enabled?: boolean;
  remark?: string;
}

export interface AdminSystemConfigRecord {
  id: string;
  tenantId: string;
  configKey: string;
  configName: string;
  configValue: string;
  valueType: string;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AdminSystemConfigPage = PageResult<AdminSystemConfigRecord>;

export interface AdminSystemConfigCommand {
  configKey?: string;
  configName: string;
  configValue: string;
  valueType?: string;
  enabled?: boolean;
  remark?: string;
}

export interface AdminDecoctCenterRecord {
  id: string;
  tenantId: string;
  centerCode: string;
  centerName: string;
  contactName: string | null;
  contactPhone: string | null;
  address: string | null;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AdminDecoctCenterPage = PageResult<AdminDecoctCenterRecord>;

export interface AdminDecoctCenterCommand {
  centerCode?: string;
  centerName: string;
  contactName?: string;
  contactPhone?: string;
  address?: string;
  enabled?: boolean;
  remark?: string;
}

export interface AdminInstitutionRecord {
  id: string;
  tenantId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  status: string;
  storageType: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AdminInstitutionPage = PageResult<AdminInstitutionRecord>;

export interface AdminInstitutionCommand {
  institutionCode?: string;
  institutionName: string;
  institutionType?: string;
  status?: string;
  storageType?: string;
}

export interface AdminInstitutionApiRecord {
  id: string;
  apiCode: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  description: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AdminInstitutionApiPage = PageResult<AdminInstitutionApiRecord>;

export interface AdminInstitutionApiCommand {
  apiCode?: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  description?: string;
  enabled?: boolean;
}

export interface AdminInstitutionApiPermissionRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  apiId: string;
  apiCode: string;
  apiName: string;
  requestMethod: string;
  requestPath: string;
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AdminInstitutionApiPermissionPage = PageResult<AdminInstitutionApiPermissionRecord>;

export interface AdminInstitutionApiPermissionCommand {
  institutionId?: string;
  apiId?: string;
  remark?: string;
  enabled?: boolean;
}

export interface AdminInstitutionIpWhitelistRecord {
  id: string;
  tenantId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  institutionType: string;
  ipRange: string;
  enabled: boolean;
  createdAt: string;
}

export type AdminInstitutionIpWhitelistPage = PageResult<AdminInstitutionIpWhitelistRecord>;

export interface AdminInstitutionIpWhitelistCommand {
  institutionId?: string;
  ipRange: string;
  enabled?: boolean;
}
