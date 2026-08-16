import { listOperationPage, createOperationRecord, updateOperationRecord } from '../../api/operations';
import type { OperationColumn, OperationFilter } from '../../components/OperationalListPage';
import { OperationalListPage } from '../../components/OperationalListPage';
import { EntityListPage, type EntityFormField } from '../../components/EntityListPage';
import { StatusTag } from '../../components/StatusTag';
import { useAdminPermission } from '../../hooks/useAdminPermission';
import { formatDate } from '../../utils/formatters';
import type { TableColumnsType } from 'antd';

const orderFilters: OperationFilter[] = [
  { name: 'startTime', label: '开始时间' },
  { name: 'endTime', label: '结束时间' },
  { name: 'institution', label: '机构' },
  { name: 'orderNo', label: '订单号' },
  { name: 'hospitalPrescriptionNo', label: '机构处方号' },
  { name: 'patientName', label: '患者' },
  { name: 'receiverPhone', label: '收货电话' },
  { name: 'prescriptionType', label: '处方类型' },
  { name: 'orderStatus', label: '订单状态' },
];

const orderColumns: OperationColumn[] = [
  { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
  { title: '机构', dataIndex: 'institutionName', fallbackKeys: ['institution'], width: 180 },
  { title: '患者', dataIndex: 'patientName', width: 120 },
  { title: '处方类型', dataIndex: 'prescriptionType', fallbackKeys: ['prescriptionTypes'], width: 120 },
  { title: '剂数', dataIndex: 'doseCount', width: 90, kind: 'count' },
  { title: '金额', dataIndex: 'totalAmount', width: 110, kind: 'money' },
  { title: '收货电话', dataIndex: 'receiverPhone', width: 140 },
  { title: '订单状态', dataIndex: 'orderStatus', fallbackKeys: ['status'], width: 130, kind: 'status' },
  { title: '接单时间', dataIndex: 'createdAt', fallbackKeys: ['orderTime', 'receivedAt'], width: 180, kind: 'date' },
];

const workflowColumns: OperationColumn[] = [
  { title: '任务 ID', dataIndex: 'taskId', fallbackKeys: ['id'], width: 180, kind: 'code' },
  { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
  { title: '机构', dataIndex: 'institutionName', width: 180 },
  { title: '患者', dataIndex: 'patientName', width: 120 },
  { title: '处方类型', dataIndex: 'prescriptionType', width: 120 },
  { title: '剂数', dataIndex: 'doseCount', width: 90, kind: 'count' },
  { title: '任务状态', dataIndex: 'status', fallbackKeys: ['taskStatus'], width: 120, kind: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, kind: 'date' },
];

function orderPage(title: string, subtitle: string, path: string, columns = orderColumns, filters = orderFilters) {
  return <OperationalListPage title={title} subtitle={subtitle} filters={filters} columns={columns} load={(params) => listOperationPage(path, params)} />;
}

export function OrderAuditPage() {
  return orderPage('订单审核', '查询待审核和已审核订单，保留审核记录、患者、机构和处方核对信息。', '/order-api/api/admin/review-records', workflowColumns);
}

export function OrderDispensePage() {
  return orderPage('调剂打印', '查询调剂任务、批次、处方和打印相关信息。', '/workflow-api/api/admin/workflow/dispense-tasks', workflowColumns, []);
}

export function OrderRecheckMultiPage() {
  return orderPage('处方复核（多单）', '查询复核任务并保留多任务复核入口。', '/workflow-api/api/admin/workflow/recheck-tasks', workflowColumns, []);
}

export function OrderRecheckRecordsPage() {
  return orderPage('复核管理', '查询处方复核记录、调剂员、复核员、批次和复核状态。', '/order-api/api/admin/recheck-records');
}

export function OrderAddressModifyPage() {
  return orderPage('订单地址修改', '查询可核对收货信息的订单，详情中保留地址字段和配送信息。', '/order-api/api/admin/orders');
}

export function OrderPrescriptionModifyPage() {
  return orderPage('处方修改', '查询可核对处方内容的订单，详情中保留处方、剂数和患者字段。', '/order-api/api/admin/orders');
}

export function OrderManageActionPage() {
  return orderPage('订单操作', '查询可初始化或取消的处方订单，操作类 mutation 后续按权限逐项开放。', '/order-api/api/admin/orders');
}

export function OrderPrescriptionReprintPage() {
  return orderPage('处方重打', '查询可重打处方和打印载荷信息。', '/order-api/api/admin/prescription-reprints');
}

export function OrderWarehousePage() {
  return orderPage('订单仓库', '查询仓库维度订单、机构、收货和处方统计信息。', '/order-api/api/admin/order-warehouses');
}

interface InterceptRecord {
  id: string;
  ruleName?: string;
  scene?: string;
  matchType?: string;
  priority?: number;
  enabled?: boolean;
  reason?: string;
  updatedAt?: string;
}

interface InterceptForm {
  ruleName: string;
  scene: string;
  matchType: string;
  priority: number;
  enabled: boolean;
  reason: string;
}

const interceptColumns: TableColumnsType<InterceptRecord> = [
  { title: '规则', dataIndex: 'ruleName', width: 180 },
  { title: '场景', dataIndex: 'scene', width: 140 },
  { title: '匹配方式', dataIndex: 'matchType', width: 140 },
  { title: '优先级', dataIndex: 'priority', width: 100 },
  { title: '状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => <StatusTag value={value} labels={{ ENABLED: '已启用', DISABLED: '已停用' }} /> },
  { title: '原因', dataIndex: 'reason', width: 240 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

const interceptFields: EntityFormField<InterceptForm>[] = [
  { name: 'ruleName', label: '规则名称', required: true },
  { name: 'scene', label: '拦截场景', required: true },
  { name: 'matchType', label: '匹配方式', required: true },
  { name: 'priority', label: '优先级', kind: 'number' },
  { name: 'enabled', label: '状态', kind: 'switch' },
  { name: 'reason', label: '拦截原因', kind: 'textarea', wide: true },
];

export function OrderInterceptRulePage() {
  const canWrite = useAdminPermission('order:write');
  const path = '/order-api/api/admin/order-intercept-rules';
  return (
    <EntityListPage<InterceptRecord, InterceptForm>
      title="订单拦截配置"
      subtitle="维护订单拦截规则、场景、优先级和启停状态。"
      entityName="拦截规则"
      columns={interceptColumns}
      fields={interceptFields}
      initialValues={{ ruleName: '', scene: '', matchType: '', priority: 0, enabled: true, reason: '' }}
      valuesFromRecord={(record) => ({
        ruleName: record.ruleName ?? '',
        scene: record.scene ?? '',
        matchType: record.matchType ?? '',
        priority: record.priority ?? 0,
        enabled: record.enabled ?? true,
        reason: record.reason ?? '',
      })}
      load={(params) => listOperationPage(path, { ...params }).then((result) => ({ ...result, records: result.records.map((record) => record as unknown as InterceptRecord) }))}
      create={(values) => createOperationRecord(path, values)}
      update={(id, values) => updateOperationRecord(path, id, values)}
      canWrite={canWrite}
      csvColumns={[
        { title: '规则', value: (record) => record.ruleName },
        { title: '场景', value: (record) => record.scene },
        { title: '状态', value: (record) => record.enabled ? '启用' : '停用' },
      ]}
    />
  );
}

export function OrderManualProcessPage() {
  return orderPage('订单走流程', '查询需人工推进的订单，详情中保留审核、调剂、复核、煎煮、出库和签收节点。', '/order-api/api/admin/manual-process-orders');
}

export function OrderReceiptPage() {
  return orderPage('订单签收', '查询待签收和签收异常订单，保留物流和收货信息。', '/order-api/api/admin/order-receipts');
}
