import { listOperationPage, createOperationRecord, updateOperationRecord } from '../../api/operations';
import type { OperationColumn, OperationFilter } from '../../components/OperationalListPage';
import { OperationalListPage } from '../../components/OperationalListPage';
import { EntityListPage, type EntityFormField } from '../../components/EntityListPage';
import { StatusTag } from '../../components/StatusTag';
import { useAdminPermission } from '../../hooks/useAdminPermission';
import { formatDate } from '../../utils/formatters';
import type { TableColumnsType } from 'antd';

interface LogisticsRuleRecord {
  id: string;
  institutionId?: string;
  institutionCode?: string;
  institutionName?: string;
  ruleName?: string;
  logisticsCompany?: string;
  baseFee?: number;
  extraFee?: number;
  freeThreshold?: number;
  enabled?: boolean;
  remark?: string;
  updatedAt?: string;
}

interface LogisticsRuleForm {
  institutionId: string;
  ruleName: string;
  logisticsCompany: string;
  baseFee: number;
  extraFee: number;
  freeThreshold: number;
  enabled: boolean;
  remark: string;
}

const enabledLabels = { ENABLED: '已启用', DISABLED: '已停用' };

const logisticsFilters: OperationFilter[] = [
  { name: 'startTime', label: '开始时间' },
  { name: 'endTime', label: '结束时间' },
  { name: 'institution', label: '机构' },
  { name: 'orderNo', label: '订单号' },
  { name: 'receiverPhone', label: '收货电话' },
  { name: 'logisticsNo', label: '物流单号' },
  {
    name: 'status',
    label: '状态',
    options: [
      { label: '全部状态', value: '' },
      { label: '待发货', value: 'PENDING' },
      { label: '已发货', value: 'SHIPPED' },
      { label: '已签收', value: 'SIGNED' },
      { label: '异常', value: 'FAILED' },
    ],
  },
];

const shipmentColumns: OperationColumn[] = [
  { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
  { title: '机构', dataIndex: 'institutionName', fallbackKeys: ['institution'], width: 180 },
  { title: '患者', dataIndex: 'patientName', width: 120 },
  { title: '收货人', dataIndex: 'receiverName', width: 120 },
  { title: '收货电话', dataIndex: 'receiverPhone', width: 140 },
  { title: '物流公司', dataIndex: 'logisticsCompany', width: 150 },
  { title: '物流单号', dataIndex: 'logisticsNo', width: 180, kind: 'code' },
  { title: '状态', dataIndex: 'status', fallbackKeys: ['shipmentStatus', 'orderStatus'], width: 110, kind: 'status' },
  { title: '发货时间', dataIndex: 'shippedAt', fallbackKeys: ['shipTime'], width: 180, kind: 'date' },
  { title: '签收时间', dataIndex: 'signedAt', fallbackKeys: ['signTime'], width: 180, kind: 'date' },
];

const traceColumns: OperationColumn[] = [
  { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
  { title: '物流单号', dataIndex: 'logisticsNo', width: 180, kind: 'code' },
  { title: '物流公司', dataIndex: 'logisticsCompany', width: 150 },
  { title: '手机号', dataIndex: 'receiverPhone', fallbackKeys: ['phone'], width: 140 },
  { title: '状态', dataIndex: 'status', width: 110, kind: 'status' },
  { title: '物流信息', dataIndex: 'content', fallbackKeys: ['traceContent', 'logisticsInfo'], width: 360 },
  { title: '操作时间', dataIndex: 'operateTime', fallbackKeys: ['eventTime', 'createdAt'], width: 180, kind: 'date' },
];

function ruleLoad(path: string) {
  return (params: { keyword?: string; enabled?: string | boolean; page?: number; pageSize?: number }) =>
    listOperationPage(path, params).then((result) => ({
      ...result,
      records: result.records.map((record) => record as unknown as LogisticsRuleRecord),
    }));
}

const ruleFields: EntityFormField<LogisticsRuleForm>[] = [
  { name: 'institutionId', label: '机构 ID', required: true, disabledWhenEditing: true },
  { name: 'ruleName', label: '规则名称', required: true },
  { name: 'logisticsCompany', label: '物流公司', required: true },
  { name: 'baseFee', label: '基础费用', kind: 'number' },
  { name: 'extraFee', label: '附加费用', kind: 'number' },
  { name: 'freeThreshold', label: '免邮阈值', kind: 'number' },
  { name: 'enabled', label: '状态', kind: 'switch' },
  { name: 'remark', label: '备注', kind: 'textarea', wide: true },
];

const ruleColumns: TableColumnsType<LogisticsRuleRecord> = [
  { title: '机构', dataIndex: 'institutionName', width: 180 },
  { title: '规则', dataIndex: 'ruleName', width: 180 },
  { title: '物流公司', dataIndex: 'logisticsCompany', width: 150 },
  { title: '基础费用', dataIndex: 'baseFee', width: 110 },
  { title: '附加费用', dataIndex: 'extraFee', width: 110 },
  { title: '免邮阈值', dataIndex: 'freeThreshold', width: 110 },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => <StatusTag value={value} labels={enabledLabels} /> },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

function valuesFromRule(record: LogisticsRuleRecord): LogisticsRuleForm {
  return {
    institutionId: record.institutionId ?? '',
    ruleName: record.ruleName ?? '',
    logisticsCompany: record.logisticsCompany ?? '',
    baseFee: record.baseFee ?? 0,
    extraFee: record.extraFee ?? 0,
    freeThreshold: record.freeThreshold ?? 0,
    enabled: record.enabled ?? true,
    remark: record.remark ?? '',
  };
}

function LogisticsRulePage({
  title,
  subtitle,
  path,
}: {
  title: string;
  subtitle: string;
  path: string;
}) {
  const canWrite = useAdminPermission('logistics:write');
  return (
    <EntityListPage<LogisticsRuleRecord, LogisticsRuleForm>
      title={title}
      subtitle={subtitle}
      entityName="规则"
      columns={ruleColumns}
      fields={ruleFields}
      initialValues={{ institutionId: '', ruleName: '', logisticsCompany: '', baseFee: 0, extraFee: 0, freeThreshold: 0, enabled: true, remark: '' }}
      valuesFromRecord={valuesFromRule}
      load={ruleLoad(path)}
      create={(values) => createOperationRecord(path, values)}
      update={(id, values) => updateOperationRecord(path, id, values)}
      canWrite={canWrite}
      csvColumns={[
        { title: '机构', value: (record) => record.institutionName },
        { title: '规则', value: (record) => record.ruleName },
        { title: '物流公司', value: (record) => record.logisticsCompany },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
      ]}
    />
  );
}

export function LogisticsSpecialRulePage() {
  return <LogisticsRulePage title="机构物流费规则配置" subtitle="维护机构级物流计费规则、物流公司和启停状态。" path="/order-api/api/admin/logistics-special-rules" />;
}

export function LogisticsAddressCostPage() {
  return <LogisticsRulePage title="机构地址物流费配置" subtitle="维护机构、地址区域与物流费用配置。" path="/order-api/api/admin/logistics-address-costs" />;
}

export function LogisticsDeliveryPage() {
  return <OperationalListPage title="订单发货查询" subtitle="查询待发货、已发货、签收和回调相关物流记录。" filters={logisticsFilters} columns={shipmentColumns} load={(params) => listOperationPage('/logistics-api/api/admin/logistics/shipments', params)} />;
}

export function LogisticsInfoPage() {
  return <OperationalListPage title="物流信息查询" subtitle="查询物流轨迹、物流单号、手机号和轨迹状态。" filters={logisticsFilters} columns={traceColumns} load={(params) => listOperationPage('/logistics-api/api/admin/logistics/infos', params)} />;
}

export function LogisticsPrintPage() {
  return <OperationalListPage title="物流打单" subtitle="按订单和物流信息查询可打印的发货记录。" filters={logisticsFilters} columns={shipmentColumns} load={(params) => listOperationPage('/logistics-api/api/admin/logistics/shipments', params)} />;
}

export function LogisticsMergePage() {
  return <OperationalListPage title="物流合并列表" subtitle="查询订单合并记录、合单状态和物流信息。" filters={[{ name: 'keyword', label: '关键字' }, { name: 'status', label: '状态' }]} columns={[{ title: '合单订单', dataIndex: 'mergeNo', fallbackKeys: ['orderNo'], width: 180, kind: 'code' }, { title: '机构', dataIndex: 'institutionName', width: 180 }, { title: '物流公司', dataIndex: 'logisticsCompany', width: 150 }, { title: '状态', dataIndex: 'status', width: 110, kind: 'status' }, { title: '备注', dataIndex: 'remark', width: 240 }, { title: '创建时间', dataIndex: 'createdAt', width: 180, kind: 'date' }]} load={(params) => listOperationPage('/order-api/api/admin/order-merges', params)} />;
}

export function LogisticsUnreceivedPage() {
  return <OperationalListPage title="未签收跟进" subtitle="查询未签收订单并保留轨迹和收货信息核对入口。" filters={logisticsFilters} columns={shipmentColumns} load={(params) => listOperationPage('/logistics-api/api/admin/logistics/shipments', params)} />;
}
