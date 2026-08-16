import { SendOutlined } from '@ant-design/icons';
import { App, Button, Form, Input } from 'antd';
import { createOperationRecord, listOperationPage } from '../../api/operations';
import type { OperationColumn, OperationFilter } from '../../components/OperationalListPage';
import { OperationalListPage } from '../../components/OperationalListPage';
import { QueryTableShell } from '../../components/QueryTableShell';

const dateFilters: OperationFilter[] = [
  { name: 'from', label: '开始日期' },
  { name: 'to', label: '结束日期' },
];

const keywordStatusFilters: OperationFilter[] = [
  { name: 'keyword', label: '关键字' },
  { name: 'status', label: '状态' },
];

const labelColumns: OperationColumn[] = [
  { title: '编码', dataIndex: 'templateCode', fallbackKeys: ['code', 'labelCode'], width: 160, kind: 'code' },
  { title: '名称', dataIndex: 'templateName', fallbackKeys: ['name', 'labelName'], width: 180 },
  { title: '机构', dataIndex: 'institutionName', width: 180 },
  { title: '处方类型', dataIndex: 'prescriptionType', width: 120 },
  { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 110, kind: 'status' },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
];

const smsColumns: OperationColumn[] = [
  { title: '模板编码', dataIndex: 'templateCode', width: 160, kind: 'code' },
  { title: '模板名称', dataIndex: 'templateName', width: 180 },
  { title: '类型', dataIndex: 'templateType', fallbackKeys: ['type'], width: 120 },
  { title: '签名', dataIndex: 'signature', width: 140 },
  { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 110, kind: 'status' },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
];

const smsRecordColumns: OperationColumn[] = [
  { title: '模板', dataIndex: 'templateName', width: 180 },
  { title: '手机号', dataIndex: 'phone', fallbackKeys: ['mobile'], width: 140 },
  { title: '接收人', dataIndex: 'receiverName', width: 120 },
  { title: '关联订单', dataIndex: 'orderNo', width: 180, kind: 'code' },
  { title: '状态', dataIndex: 'status', width: 110, kind: 'status' },
  { title: '重试次数', dataIndex: 'retryCount', width: 100, kind: 'count' },
  { title: '登记时间', dataIndex: 'createdAt', width: 180, kind: 'date' },
];

const herbColumns: OperationColumn[] = [
  { title: '药品编码', dataIndex: 'herbCode', fallbackKeys: ['code'], width: 160, kind: 'code' },
  { title: '药品名称', dataIndex: 'herbName', fallbackKeys: ['name'], width: 180 },
  { title: '规格', dataIndex: 'specification', width: 160 },
  { title: '产地', dataIndex: 'origin', width: 140 },
  { title: '单位', dataIndex: 'unit', width: 90 },
  { title: '零售价', dataIndex: 'retailPrice', width: 100, kind: 'money' },
  { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 110, kind: 'status' },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
];

const reportSummaryColumns: OperationColumn[] = [
  { title: '人员/机构', dataIndex: 'operatorName', fallbackKeys: ['institutionName', 'logisticsCompany', 'herbName'], width: 180 },
  { title: '订单数', dataIndex: 'orderCount', width: 100, kind: 'count' },
  { title: '处方数', dataIndex: 'prescriptionCount', width: 100, kind: 'count' },
  { title: '剂数', dataIndex: 'doseCount', width: 100, kind: 'count' },
  { title: '金额', dataIndex: 'totalAmount', fallbackKeys: ['settlementAmount', 'salesAmount'], width: 120, kind: 'money' },
  { title: '开始时间', dataIndex: 'firstAt', fallbackKeys: ['startTime'], width: 180, kind: 'date' },
  { title: '结束时间', dataIndex: 'lastAt', fallbackKeys: ['endTime'], width: 180, kind: 'date' },
];

const reportDetailColumns: OperationColumn[] = [
  { title: '时间', dataIndex: 'createdAt', fallbackKeys: ['operateTime', 'eventTime'], width: 180, kind: 'date' },
  { title: '人员/公司', dataIndex: 'operatorName', fallbackKeys: ['logisticsCompany'], width: 160 },
  { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
  { title: '外部订单号', dataIndex: 'externalOrderNo', width: 180, kind: 'code' },
  { title: '机构', dataIndex: 'institutionName', width: 180 },
  { title: '患者', dataIndex: 'patientName', width: 120 },
  { title: '结果/状态', dataIndex: 'result', fallbackKeys: ['status'], width: 120, kind: 'status' },
  { title: '备注', dataIndex: 'remark', width: 240 },
];

function listPage(title: string, subtitle: string, path: string, columns: OperationColumn[], filters = keywordStatusFilters, notice?: string) {
  return <OperationalListPage title={title} subtitle={subtitle} filters={filters} columns={columns} notice={notice} load={(params) => listOperationPage(path, params)} />;
}

function reportPage(title: string, path: string, detail = false) {
  return listPage(title, '按日期范围查询并导出当前页数据。', path, detail ? reportDetailColumns : reportSummaryColumns, dateFilters);
}

export function LabelTemplatePage() {
  return listPage('处方标签设置', '查询标签模板、机构范围、处方类型和启停状态。', '/order-api/api/admin/label-templates', labelColumns);
}

export function LabelPrintPage() {
  return listPage('处方标签打印', '查询可打印处方、模板和打印记录。', '/order-api/api/admin/prescription-reprints', [
    { title: '处方号', dataIndex: 'prescriptionNo', width: 180, kind: 'code' },
    { title: '患者', dataIndex: 'patientName', width: 120 },
    { title: '机构', dataIndex: 'institutionName', width: 180 },
    { title: '处方类型', dataIndex: 'prescriptionType', width: 120 },
    { title: '剂数', dataIndex: 'doseCount', width: 90, kind: 'count' },
    { title: '批次', dataIndex: 'batchNo', width: 120 },
    { title: '接单时间', dataIndex: 'createdAt', width: 180, kind: 'date' },
  ], [{ name: 'startTime', label: '开始时间' }, { name: 'endTime', label: '结束时间' }, { name: 'prescriptionNo', label: '处方号' }], '当前仅接入可打印处方查询，打印载荷获取和打印记录登记动作仍待迁移。');
}

export function SmsTemplatePage() {
  return listPage('短信模板管理', '查询短信模板、模板类型、签名和启停状态。', '/message-api/api/admin/sms/templates', smsColumns);
}

export function SmsRecordPage() {
  return listPage('短信列表查询', '查询短信发送登记、状态和重试次数。', '/message-api/api/admin/sms/records', smsRecordColumns);
}

interface SingleSmsForm {
  templateCode: string;
  phone: string;
  receiverName: string;
  orderNo: string;
  operator: string;
  variables: string;
}

export function SmsSingleSendPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<SingleSmsForm>();

  const submit = async () => {
    const values = await form.validateFields();
    await createOperationRecord('/message-api/api/admin/sms/send-single', values);
    message.success('短信发送请求已登记');
    form.resetFields();
  };

  return (
    <QueryTableShell
      title="单发短信"
      subtitle="按模板、手机号和模板变量登记单条短信发送请求。"
      filters={null}
      table={
        <Form<SingleSmsForm> className="single-operation-form" form={form} layout="vertical" initialValues={{ templateCode: '', phone: '', receiverName: '', orderNo: '', operator: '', variables: '{}' }}>
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="receiverName" label="接收人">
            <Input />
          </Form.Item>
          <Form.Item name="orderNo" label="关联订单">
            <Input />
          </Form.Item>
          <Form.Item name="operator" label="操作人">
            <Input />
          </Form.Item>
          <Form.Item name="variables" label="模板变量 JSON">
            <Input.TextArea rows={5} />
          </Form.Item>
          <Button type="primary" icon={<SendOutlined />} onClick={() => void submit()}>
            登记发送
          </Button>
        </Form>
      }
    />
  );
}

export function HerbListPage() {
  return listPage('药品目录列表', '查询平台药品目录、规格、产地、价格和启停状态。', '/order-api/api/admin/herbs', herbColumns);
}

export function HerbIndexOperationLogPage() {
  return listPage('药品索引操作日志', '查询药品索引创建、更新和启停操作日志。', '/order-api/api/admin/herb-index-operation-logs', [
    { title: '机构', dataIndex: 'institutionName', width: 180 },
    { title: '药品', dataIndex: 'herbName', width: 180 },
    { title: '操作', dataIndex: 'operationType', width: 140 },
    { title: '结果', dataIndex: 'result', fallbackKeys: ['status'], width: 120, kind: 'status' },
    { title: '操作人', dataIndex: 'operator', width: 120 },
    { title: '操作时间', dataIndex: 'createdAt', width: 180, kind: 'date' },
  ]);
}

export function HerbIndexPage() {
  return listPage('药品索引列表', '查询机构药品与平台药品的映射索引。', '/order-api/api/admin/herb-indexes', [
    { title: '机构', dataIndex: 'institutionName', width: 180 },
    { title: '机构药品', dataIndex: 'institutionHerbName', width: 180 },
    { title: '平台药品', dataIndex: 'herbName', width: 180 },
    { title: '匹配类型', dataIndex: 'matchType', width: 120 },
    { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 110, kind: 'status' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
  ]);
}

export function HerbImportPage() {
  return listPage('药品目录导入', '导入前查询药品目录，用于核对编码和覆盖策略。', '/order-api/api/admin/herbs', herbColumns);
}

export function HerbIndexImportPage() {
  return listPage('药品索引导入', '导入前查询药品索引，用于核对机构药品和平台药品映射。', '/order-api/api/admin/herb-indexes', [
    { title: '机构', dataIndex: 'institutionName', width: 180 },
    { title: '机构药品编码', dataIndex: 'institutionHerbCode', width: 160, kind: 'code' },
    { title: '机构药品', dataIndex: 'institutionHerbName', width: 180 },
    { title: '平台药品', dataIndex: 'herbName', width: 180 },
    { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 110, kind: 'status' },
  ]);
}

export function HerbAreaPage() {
  return listPage('药材区域管理', '查询药材区域编码、名称、备注和启停状态。', '/order-api/api/admin/herb-areas', [
    { title: '区域编码', dataIndex: 'areaCode', width: 160, kind: 'code' },
    { title: '区域名称', dataIndex: 'areaName', width: 180 },
    { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 110, kind: 'status' },
    { title: '备注', dataIndex: 'remark', width: 260 },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
  ]);
}

export function InstitutionPrescriptionCountsPage() {
  return reportPage('机构处方数量统计', '/report-api/api/admin/reports/institution-prescription-counts');
}

export function AuditPerformancePage() {
  return reportPage('审核员业绩统计', '/report-api/api/admin/reports/audit-performance');
}

export function AuditPerformanceDetailPage() {
  return reportPage('审核员业绩明细', '/report-api/api/admin/reports/audit-performance-details', true);
}

export function DispensePerformancePage() {
  return reportPage('调剂员业绩统计', '/report-api/api/admin/reports/dispense-performance');
}

export function DispensePerformanceDetailPage() {
  return reportPage('调剂员业绩明细', '/report-api/api/admin/reports/dispense-performance-details', true);
}

export function RecheckPerformancePage() {
  return reportPage('复核员业绩统计', '/report-api/api/admin/reports/recheck-performance');
}

export function RecheckPerformanceDetailPage() {
  return reportPage('复核员业绩明细', '/report-api/api/admin/reports/recheck-performance-details', true);
}

export function DecoctionPerformancePage() {
  return reportPage('煎煮员业绩统计', '/report-api/api/admin/reports/decoction-performance');
}

export function DecoctionPerformanceDetailPage() {
  return reportPage('煎煮员业绩明细', '/report-api/api/admin/reports/decoction-performance-details', true);
}

export function LogisticsPerformancePage() {
  return reportPage('物流员业绩统计', '/report-api/api/admin/reports/logistics-performance');
}

export function LogisticsPerformanceDetailPage() {
  return reportPage('物流员业绩明细', '/report-api/api/admin/reports/logistics-performance-details', true);
}

export function InstitutionHerbReconciliationPage() {
  return reportPage('机构药材统计（对账）', '/report-api/api/admin/reports/institution-herb-reconciliation');
}

export function PrescriptionReconciliationPage() {
  return listPage('处方对账列表（对账）', '按订单分页查询处方对账数据。', '/order-api/api/admin/orders', [
    { title: '处方号', dataIndex: 'prescriptionNos', fallbackKeys: ['prescriptionNo'], width: 180, kind: 'code' },
    { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
    { title: '机构', dataIndex: 'institutionName', width: 180 },
    { title: '患者', dataIndex: 'patientName', width: 120 },
    { title: '处方类型', dataIndex: 'prescriptionTypes', fallbackKeys: ['prescriptionType'], width: 120 },
    { title: '剂数', dataIndex: 'doseCount', width: 90, kind: 'count' },
    { title: '金额', dataIndex: 'totalAmount', width: 120, kind: 'money' },
    { title: '订单状态', dataIndex: 'orderStatus', width: 120, kind: 'status' },
  ], [{ name: 'startTime', label: '开始时间' }, { name: 'endTime', label: '结束时间' }, { name: 'institution', label: '机构' }, { name: 'orderNo', label: '订单/处方' }]);
}

export function PrescriptionHerbDetailPage() {
  return reportPage('药材明细列表（对账）', '/report-api/api/admin/reports/prescription-herb-details', true);
}

export function HerbDosagePage() {
  return reportPage('药材用量统计', '/report-api/api/admin/reports/herb-dosage');
}

export function DecoctionBindingPage() {
  return listPage('处方设备绑定列表', '查询可操作处方、设备绑定和 MES/PDA 任务状态。', '/decoction-api/simulator/pda/prescriptions/can-operate', [
    { title: '处方号', dataIndex: 'prescriptionNo', width: 180, kind: 'code' },
    { title: '订单号', dataIndex: 'orderNo', width: 180, kind: 'code' },
    { title: '设备', dataIndex: 'deviceCode', width: 160, kind: 'code' },
    { title: '水桶', dataIndex: 'pailNo', width: 120, kind: 'code' },
    { title: '状态', dataIndex: 'status', fallbackKeys: ['taskStatus'], width: 120, kind: 'status' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
  ], keywordStatusFilters, '当前仅接入可操作处方查询，设备绑定、开始、完成、取消等 PDA 动作仍待迁移。');
}

export function DecoctionPrinterPage() {
  return listPage('打码机打印配置', '查询设备、PDA、打印机和模板配置。', '/decoction-api/admin/decoction/devices', [
    { title: '设备编码', dataIndex: 'deviceCode', width: 160, kind: 'code' },
    { title: '设备名称', dataIndex: 'deviceName', width: 180 },
    { title: 'PDA', dataIndex: 'pdaCode', width: 140, kind: 'code' },
    { title: '打印机', dataIndex: 'printerCode', width: 140, kind: 'code' },
    { title: '模板', dataIndex: 'printTemplateCode', width: 160, kind: 'code' },
    { title: '状态', dataIndex: 'enabled', fallbackKeys: ['deviceStatus'], width: 120, kind: 'status' },
  ]);
}

export function DecoctionWaterPailPage() {
  return listPage('加水桶管理', '查询加水桶、中心、组别和启停状态。', '/decoction-api/admin/decoction/water-pails', [
    { title: '水桶号', dataIndex: 'pailNo', width: 160, kind: 'code' },
    { title: '中心', dataIndex: 'decoctionCenter', width: 180 },
    { title: '组别', dataIndex: 'deviceGroup', width: 120 },
    { title: '容量', dataIndex: 'capacity', width: 100 },
    { title: '状态', dataIndex: 'enabled', fallbackKeys: ['status'], width: 120, kind: 'status' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, kind: 'date' },
  ]);
}

export function DecoctionCloudPrintRecordPage() {
  return reportPage('云打印记录列表', '/report-api/api/admin/reports/decoction-performance-details', true);
}
