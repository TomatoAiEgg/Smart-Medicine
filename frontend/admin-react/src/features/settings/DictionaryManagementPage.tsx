import { useQuery } from '@tanstack/react-query';
import { Select, Tabs, type TableColumnsType } from 'antd';
import { useCallback, useState } from 'react';
import { managementApi } from '../../api/management';
import type {
  AdminDictItemRecord,
  AdminDictTypeRecord,
  CommonListQuery,
} from '../../api/management.types';
import { EntityListPage } from '../../components/EntityListPage';
import { StatusTag } from '../../components/StatusTag';
import { useAdminPermission } from '../../hooks/useAdminPermission';
import { formatDate } from '../../utils/formatters';

interface DictTypeForm {
  typeCode: string;
  typeName: string;
  enabled: boolean;
}

interface DictItemForm {
  typeId: string;
  itemCode: string;
  itemName: string;
  itemValue: string;
  sortNo: number;
  enabled: boolean;
  remark: string;
}

const typeColumns: TableColumnsType<AdminDictTypeRecord> = [
  { title: '字典编码', dataIndex: 'typeCode', width: 200, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '字典名称', dataIndex: 'typeName', width: 220 },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => <StatusTag value={value} labels={{ ENABLED: '已启用', DISABLED: '已停用' }} /> },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (value: string) => formatDate(value) },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

const itemColumns: TableColumnsType<AdminDictItemRecord> = [
  {
    title: '字典类型', key: 'type', width: 220,
    render: (_, record) => <div className="entity-primary-cell"><strong>{record.typeName}</strong><span>{record.typeCode}</span></div>,
  },
  { title: '项目编码', dataIndex: 'itemCode', width: 180, render: (value: string) => <code className="entity-code">{value}</code> },
  { title: '项目名称', dataIndex: 'itemName', width: 180 },
  { title: '项目值', dataIndex: 'itemValue', width: 200, ellipsis: true },
  { title: '排序', dataIndex: 'sortNo', width: 90 },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => <StatusTag value={value} labels={{ ENABLED: '已启用', DISABLED: '已停用' }} /> },
  { title: '备注', dataIndex: 'remark', width: 220, ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

export function DictionaryManagementPage() {
  const canWrite = useAdminPermission('system:write');
  const [selectedTypeId, setSelectedTypeId] = useState('');
  const typeOptions = useQuery({ queryKey: ['dict-type-options'], queryFn: () => managementApi.listDictTypes({ page: 1, pageSize: 100 }) });
  const loadItems = useCallback(
    (params: CommonListQuery) => managementApi.listDictItems({ ...params, typeId: selectedTypeId }),
    [selectedTypeId],
  );
  const types = typeOptions.data?.records ?? [];

  return (
    <Tabs
      className="management-page-tabs"
      defaultActiveKey="types"
      items={[
        {
          key: 'types',
          label: '字典类型',
          children: (
            <EntityListPage<AdminDictTypeRecord, DictTypeForm>
              title="字典列表"
              subtitle="维护业务字典类型；字典编码创建后不可修改。"
              entityName="字典类型"
              columns={typeColumns}
              fields={[
                { name: 'typeCode', label: '字典编码', required: true, disabledWhenEditing: true },
                { name: 'typeName', label: '字典名称', required: true },
                { name: 'enabled', label: '状态', kind: 'switch', wide: true },
              ]}
              initialValues={{ typeCode: '', typeName: '', enabled: true }}
              valuesFromRecord={(record) => ({ typeCode: record.typeCode, typeName: record.typeName, enabled: record.enabled })}
              load={managementApi.listDictTypes}
              create={managementApi.createDictType}
              update={managementApi.updateDictType}
              canWrite={canWrite}
              csvColumns={[
                { title: '字典编码', value: (record) => record.typeCode },
                { title: '字典名称', value: (record) => record.typeName },
                { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
              ]}
            />
          ),
        },
        {
          key: 'items',
          label: '字典项目',
          children: (
            <EntityListPage<AdminDictItemRecord, DictItemForm>
              title="字典详情"
              subtitle="按字典类型维护编码、显示名称、业务值和排序。"
              entityName="字典项目"
              columns={itemColumns}
              fields={[
                { name: 'typeId', label: '字典类型', kind: 'select', required: true, disabledWhenEditing: true, wide: true,
                  options: types.map((record) => ({ label: `${record.typeName}（${record.typeCode}）`, value: record.id, disabled: !record.enabled })) },
                { name: 'itemCode', label: '项目编码', required: true, disabledWhenEditing: true },
                { name: 'itemName', label: '项目名称', required: true },
                { name: 'itemValue', label: '项目值', wide: true },
                { name: 'sortNo', label: '排序', kind: 'number' },
                { name: 'enabled', label: '状态', kind: 'switch' },
                { name: 'remark', label: '备注', kind: 'textarea', wide: true },
              ]}
              initialValues={{ typeId: selectedTypeId, itemCode: '', itemName: '', itemValue: '', sortNo: 0, enabled: true, remark: '' }}
              valuesFromRecord={(record) => ({
                typeId: record.typeId,
                itemCode: record.itemCode,
                itemName: record.itemName,
                itemValue: record.itemValue ?? '',
                sortNo: record.sortNo,
                enabled: record.enabled,
                remark: record.remark ?? '',
              })}
              load={loadItems}
              create={managementApi.createDictItem}
              update={managementApi.updateDictItem}
              canWrite={canWrite}
              extraFilters={
                <Select
                  allowClear
                  value={selectedTypeId || undefined}
                  placeholder="筛选字典类型"
                  options={types.map((record) => ({ label: record.typeName, value: record.id }))}
                  onChange={(value) => setSelectedTypeId(value ?? '')}
                />
              }
              csvColumns={[
                { title: '字典类型', value: (record) => record.typeName },
                { title: '项目编码', value: (record) => record.itemCode },
                { title: '项目名称', value: (record) => record.itemName },
                { title: '项目值', value: (record) => record.itemValue },
                { title: '排序', value: (record) => record.sortNo },
              ]}
            />
          ),
        },
      ]}
    />
  );
}
