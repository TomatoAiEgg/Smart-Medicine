import { ExportOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button } from 'antd';
import {
  listInstitutionPrescriptionCounts,
  type InstitutionPrescriptionCountRecord,
} from '../../api/report';
import { QueryTableShell } from '../../components/QueryTableShell';

interface PrescriptionCountRecord {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  orderCount: number;
  prescriptionTotal: number;
  doseTotal: number;
  totalAmount: number | string | null;
}

interface PrescriptionCountQueryParams {
  current?: number;
  pageSize?: number;
  dateRange?: string[];
}

function formatAmount(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }

  const numericValue = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(numericValue)) {
    return String(value);
  }

  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(numericValue);
}

function readDateRange(value: string[] | undefined) {
  if (!Array.isArray(value) || value.length < 2) {
    return {};
  }
  return { from: value[0], to: value[1] };
}

function mapPrescriptionCountRecord(record: InstitutionPrescriptionCountRecord): PrescriptionCountRecord {
  return {
    institutionId: record.institutionId,
    institutionCode: record.institutionCode,
    institutionName: record.institutionName,
    orderCount: record.orderCount,
    prescriptionTotal: record.prescriptionCount,
    doseTotal: record.doseCount,
    totalAmount: record.totalAmount,
  };
}

const columns: ProColumns<PrescriptionCountRecord>[] = [
  {
    title: '统计时间',
    dataIndex: 'dateRange',
    valueType: 'dateRange',
    hideInTable: true,
  },
  {
    title: '机构编码',
    dataIndex: 'institutionCode',
    width: 160,
    ellipsis: true,
  },
  {
    title: '机构名称',
    dataIndex: 'institutionName',
    width: 220,
    ellipsis: true,
  },
  {
    title: '订单数',
    dataIndex: 'orderCount',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '处方合计',
    dataIndex: 'prescriptionTotal',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '剂数合计',
    dataIndex: 'doseTotal',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '金额合计',
    dataIndex: 'totalAmount',
    width: 130,
    hideInSearch: true,
    render: (_, row) => formatAmount(row.totalAmount),
  },
];

export function PrescriptionCountReportPage() {
  return (
    <QueryTableShell
      title="处方数量统计"
      subtitle="按时间和机构统计处方数量与剂数。"
      actions={
        <Button icon={<ExportOutlined />}>
          导出报表
        </Button>
      }
      filters={null}
      table={
        <ProTable<PrescriptionCountRecord, PrescriptionCountQueryParams>
          rowKey="institutionId"
          columns={columns}
          options={false}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          scroll={{ x: 840 }}
          request={async (params) => {
            const records = await listInstitutionPrescriptionCounts(readDateRange(params.dateRange));
            return {
              data: records.map(mapPrescriptionCountRecord),
              success: true,
              total: records.length,
            };
          }}
          locale={{ emptyText: '暂无处方数量统计数据，请选择统计条件后查询。' }}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
