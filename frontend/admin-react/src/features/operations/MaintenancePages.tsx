import { listOperationPage, createOperationRecord, updateOperationRecord } from '../../api/operations';
import type { OperationColumn, OperationFilter } from '../../components/OperationalListPage';
import { OperationalListPage } from '../../components/OperationalListPage';
import { EntityListPage, type EntityFormField } from '../../components/EntityListPage';
import { StatusTag } from '../../components/StatusTag';
import { useAdminPermission } from '../../hooks/useAdminPermission';
import { formatDate } from '../../utils/formatters';
import type { TableColumnsType } from 'antd';

const opsFilters: OperationFilter[] = [
  { name: 'status', label: '状态' },
  { name: 'eventId', label: '事件 ID' },
  { name: 'topic', label: 'Topic' },
  { name: 'orderNo', label: '订单号' },
  { name: 'businessKey', label: '业务键' },
  { name: 'limit', label: '条数' },
];

const deadLetterColumns: OperationColumn[] = [
  { title: '事件 ID', dataIndex: 'eventId', fallbackKeys: ['id'], width: 220, kind: 'code' },
  { title: 'Topic', dataIndex: 'topic', width: 180, kind: 'code' },
  { title: '消费者', dataIndex: 'consumerGroup', width: 180 },
  { title: '状态', dataIndex: 'status', width: 120, kind: 'status' },
  { title: '重试次数', dataIndex: 'retryCount', width: 100, kind: 'count' },
  { title: '错误信息', dataIndex: 'errorMessage', fallbackKeys: ['lastError'], width: 360 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
];

const outboxColumns: OperationColumn[] = [
  { title: '事件 ID', dataIndex: 'eventId', fallbackKeys: ['id'], width: 220, kind: 'code' },
  { title: '事件类型', dataIndex: 'eventType', width: 180 },
  { title: '业务键', dataIndex: 'businessKey', width: 180, kind: 'code' },
  { title: '状态', dataIndex: 'status', width: 120, kind: 'status' },
  { title: '重试次数', dataIndex: 'retryCount', width: 100, kind: 'count' },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, kind: 'date' },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
];

export function MaintenanceOrderProcessPage() {
  return (
    <OperationalListPage
      title="订单流程查询"
      subtitle="按平台订单号或外部订单号聚合查询订单、任务、事件、回调和操作日志。"
      filters={[{ name: 'orderNo', label: '平台订单号' }, { name: 'externalOrderNo', label: '外部订单号' }, { name: 'limit', label: '条数' }]}
      columns={[
        { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
        { title: '外部订单号', dataIndex: 'externalOrderNo', width: 180, kind: 'code' },
        { title: '状态', dataIndex: 'orderStatus', fallbackKeys: ['status'], width: 120, kind: 'status' },
        { title: '事件', dataIndex: 'eventType', width: 180 },
        { title: '结果', dataIndex: 'resultCode', width: 120, kind: 'status' },
        { title: '更新时间', dataIndex: 'updatedAt', fallbackKeys: ['createdAt'], width: 180, kind: 'date' },
      ]}
      load={(params) => listOperationPage('/ops-api/api/admin/ops/order-observability', params)}
    />
  );
}

export function MaintenanceExceptionLogPage() {
  return <OperationalListPage title="异常日志信息查询" subtitle="查询死信、回调异常和集成重试问题。" filters={opsFilters} columns={deadLetterColumns} load={(params) => listOperationPage('/ops-api/api/admin/ops/dead-letters', params)} />;
}

export function MaintenanceMqMessagePage() {
  return <OperationalListPage title="MQ 消息查询列表" subtitle="查询 Outbox、消费日志和消息状态。" filters={opsFilters} columns={outboxColumns} load={(params) => listOperationPage('/ops-api/api/admin/ops/outbox', params)} />;
}

interface ProblemRecord {
  id: string;
  orderNo?: string;
  institutionName?: string;
  problemType?: string;
  status?: string;
  reason?: string;
  solution?: string;
  amount?: number;
  operator?: string;
  updatedAt?: string;
}

interface ProblemForm {
  orderNo: string;
  problemType: string;
  status: string;
  reason: string;
  solution: string;
  amount: number;
  operator: string;
}

const problemColumns: TableColumnsType<ProblemRecord> = [
  { title: '订单号', dataIndex: 'orderNo', width: 180 },
  { title: '机构', dataIndex: 'institutionName', width: 180 },
  { title: '类型', dataIndex: 'problemType', width: 140 },
  { title: '状态', dataIndex: 'status', width: 120, render: (value: string) => <StatusTag value={value} /> },
  { title: '登记原因', dataIndex: 'reason', width: 260 },
  { title: '处理方案', dataIndex: 'solution', width: 260 },
  { title: '金额', dataIndex: 'amount', width: 100 },
  { title: '操作人', dataIndex: 'operator', width: 120 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDate(value) },
];

const problemFields: EntityFormField<ProblemForm>[] = [
  { name: 'orderNo', label: '订单号', required: true, disabledWhenEditing: true },
  { name: 'problemType', label: '问题类型', required: true },
  { name: 'status', label: '状态', required: true },
  { name: 'amount', label: '金额', kind: 'number' },
  { name: 'operator', label: '操作人' },
  { name: 'reason', label: '登记原因', kind: 'textarea', required: true, wide: true },
  { name: 'solution', label: '处理方案', kind: 'textarea', wide: true },
];

export function MaintenanceProblemRegistrationPage() {
  const canWrite = useAdminPermission('ops:write');
  const path = '/ops-api/api/admin/ops/problem-registrations';
  return (
    <EntityListPage<ProblemRecord, ProblemForm>
      title="问题件登记"
      subtitle="登记、查询和更新订单问题件处理状态。"
      entityName="问题件"
      columns={problemColumns}
      fields={problemFields}
      initialValues={{ orderNo: '', problemType: '', status: 'OPEN', reason: '', solution: '', amount: 0, operator: '' }}
      valuesFromRecord={(record) => ({
        orderNo: record.orderNo ?? '',
        problemType: record.problemType ?? '',
        status: record.status ?? 'OPEN',
        reason: record.reason ?? '',
        solution: record.solution ?? '',
        amount: record.amount ?? 0,
        operator: record.operator ?? '',
      })}
      load={(params) => listOperationPage(path, { ...params }).then((result) => ({ ...result, records: result.records.map((record) => record as unknown as ProblemRecord) }))}
      create={(values) => createOperationRecord(path, values)}
      update={(id, values) => updateOperationRecord(path, id, values)}
      canWrite={canWrite}
      showEnabledFilter={false}
      csvColumns={[
        { title: '订单号', value: (record) => record.orderNo },
        { title: '类型', value: (record) => record.problemType },
        { title: '状态', value: (record) => record.status },
        { title: '登记原因', value: (record) => record.reason },
      ]}
    />
  );
}
