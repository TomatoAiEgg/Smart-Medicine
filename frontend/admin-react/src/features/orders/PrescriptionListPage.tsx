import { ExportOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Space } from 'antd';
import { listAdminOrders, type AdminOrderRecord } from '../../api/order';
import { QueryTableShell } from '../../components/QueryTableShell';
import { StatusTag } from '../../components/StatusTag';
import { formatDate } from '../../utils/formatters';
import { maskName, maskPhone } from '../../utils/masking';

interface PrescriptionRecord {
  prescriptionNo: string;
  orderedAt: string;
  decoctionCenter: string;
  institutionName: string;
  patientName: string;
  receiverPhone: string;
  prescriptionType: string;
  doseCount: number;
  amount: number;
  deliveryMethod: string;
  receiverAddress: string;
  deliveryTime: string | null;
  status: string;
  remark: string | null;
}

interface PrescriptionQueryParams {
  current?: number;
  pageSize?: number;
  prescriptionNo?: string;
  decoctionCenter?: string;
  institutionName?: string;
  patientName?: string;
  receiverPhone?: string;
  prescriptionType?: string;
  deliveryMethod?: string;
  deliveryTime?: string;
  status?: string;
}

const statusLabels: Record<string, string> = {
  PENDING: '待处理',
  SUCCESS: '已完成',
  FAILED: '异常',
  ACTIVE: '处理中',
  INACTIVE: '已取消',
};

function formatAmount(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }

  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(value);
}

function maskAddress(value: string | null | undefined) {
  const trimmedValue = value?.trim();

  if (!trimmedValue) {
    return '-';
  }

  const chars = Array.from(trimmedValue);

  if (chars.length === 1) {
    return '*';
  }

  if (chars.length <= 4) {
    return `${chars[0]}${'*'.repeat(chars.length - 1)}`;
  }

  const visibleLength = Math.min(chars.length - 1, 6);

  return `${chars.slice(0, visibleLength).join('')}****`;
}

function toNumber(value: number | string | null | undefined) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsedValue = Number(value);
    return Number.isFinite(parsedValue) ? parsedValue : 0;
  }
  return 0;
}

function mapOrderRecord(record: AdminOrderRecord): PrescriptionRecord {
  return {
    prescriptionNo: record.prescriptionNos || record.orderNo,
    orderedAt: record.createdAt,
    decoctionCenter: record.decoctionCenter ?? '',
    institutionName: record.institutionName,
    patientName: record.patientName ?? record.receiverName ?? '',
    receiverPhone: record.receiverPhone ?? record.patientPhone ?? '',
    prescriptionType: record.prescriptionTypes,
    doseCount: record.doseCount ?? 0,
    amount: toNumber(record.totalAmount),
    deliveryMethod: record.deliveryMethod ?? record.addressType ?? '',
    receiverAddress: [record.receiverProvince, record.receiverCity, record.receiverZone, record.receiverAddress]
      .filter(Boolean)
      .join(''),
    deliveryTime: record.deliveryTime,
    status: record.prescriptionStatus || record.orderStatus,
    remark: record.orderRemark,
  };
}

const columns: ProColumns<PrescriptionRecord>[] = [
  {
    title: '平台处方号',
    dataIndex: 'prescriptionNo',
    width: 180,
    ellipsis: true,
  },
  {
    title: '平台订单时间',
    dataIndex: 'orderedAt',
    width: 170,
    valueType: 'dateTime',
    render: (_, row) => formatDate(row.orderedAt),
  },
  {
    title: '煎煮中心',
    dataIndex: 'decoctionCenter',
    width: 160,
    ellipsis: true,
  },
  {
    title: '机构名称',
    dataIndex: 'institutionName',
    width: 200,
    ellipsis: true,
  },
  {
    title: '病人姓名',
    dataIndex: 'patientName',
    width: 120,
    ellipsis: true,
    render: (_, row) => maskName(row.patientName),
  },
  {
    title: '收货电话',
    dataIndex: 'receiverPhone',
    width: 140,
    ellipsis: true,
    render: (_, row) => maskPhone(row.receiverPhone),
  },
  {
    title: '处方类型',
    dataIndex: 'prescriptionType',
    width: 120,
    valueType: 'select',
    valueEnum: {
      decoction: { text: '代煎' },
      pieces: { text: '饮片' },
      paste: { text: '膏方' },
      pill: { text: '丸剂' },
      powder: { text: '散剂' },
    },
  },
  {
    title: '剂数',
    dataIndex: 'doseCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '处方金额',
    dataIndex: 'amount',
    width: 120,
    hideInSearch: true,
    render: (_, row) => formatAmount(row.amount),
  },
  {
    title: '送货方式',
    dataIndex: 'deliveryMethod',
    width: 120,
    valueType: 'select',
    valueEnum: {
      express: { text: '快递配送' },
      selfPickup: { text: '到店自取' },
      institution: { text: '机构配送' },
    },
  },
  {
    title: '收货信息',
    dataIndex: 'receiverAddress',
    width: 260,
    ellipsis: true,
    hideInSearch: true,
    render: (_, row) => maskAddress(row.receiverAddress),
  },
  {
    title: '送货时间',
    dataIndex: 'deliveryTime',
    width: 170,
    valueType: 'dateTime',
    render: (_, row) => formatDate(row.deliveryTime),
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 110,
    valueType: 'select',
    valueEnum: {
      PENDING: { text: '待处理' },
      ACTIVE: { text: '处理中' },
      SUCCESS: { text: '已完成' },
      FAILED: { text: '异常' },
      INACTIVE: { text: '已取消' },
    },
    render: (_, row) => <StatusTag value={row.status} labels={statusLabels} />,
  },
  {
    title: '订单备注',
    dataIndex: 'remark',
    width: 220,
    ellipsis: true,
    hideInSearch: true,
  },
  {
    title: '操作',
    valueType: 'option',
    width: 90,
    fixed: 'right',
    render: () => (
      <Space size={4}>
        <Button type="link" size="small">
          查看
        </Button>
      </Space>
    ),
  },
];

export function PrescriptionListPage() {
  return (
    <QueryTableShell
      title="处方列表"
      subtitle="查询订单详情、处方和履约进度。"
      actions={
        <Button icon={<ExportOutlined />}>
          导出
        </Button>
      }
      filters={null}
      table={
        <ProTable<PrescriptionRecord, PrescriptionQueryParams>
          rowKey="prescriptionNo"
          columns={columns}
          options={false}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          scroll={{ x: 2190 }}
          request={async (params) => {
            const page = await listAdminOrders({
              pageNo: params.current,
              pageSize: params.pageSize,
              prescriptionNo: params.prescriptionNo,
              decoctionCenter: params.decoctionCenter,
              institutionName: params.institutionName,
              patientName: params.patientName,
              receiverPhone: params.receiverPhone,
              prescriptionType: params.prescriptionType,
              deliveryMethod: params.deliveryMethod,
              deliveryTime: params.deliveryTime,
              status: params.status,
            });
            return {
              data: page.records.map(mapOrderRecord),
              success: true,
              total: page.total,
            };
          }}
          locale={{ emptyText: '暂无处方订单数据，请调整查询条件后重试。' }}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
