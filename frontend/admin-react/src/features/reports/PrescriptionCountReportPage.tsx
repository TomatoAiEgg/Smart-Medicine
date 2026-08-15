import { ExportOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button } from 'antd';
import { QueryTableShell } from '../../components/QueryTableShell';

interface PrescriptionCountRecord {
  date: string;
  prescriptionTotal: number;
  piecesCount: number;
  decoctionCount: number;
  pasteCount: number;
  pillCount: number;
  powderCount: number;
  otherCount: number;
  doseTotal: number;
  piecesDoseCount: number;
  decoctionDoseCount: number;
  pasteDoseCount: number;
  pillDoseCount: number;
  powderDoseCount: number;
  otherDoseCount: number;
}

const columns: ProColumns<PrescriptionCountRecord>[] = [
  {
    title: '日期',
    dataIndex: 'date',
    width: 130,
    valueType: 'date',
  },
  {
    title: '处方合计',
    dataIndex: 'prescriptionTotal',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '饮片',
    dataIndex: 'piecesCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '代煎',
    dataIndex: 'decoctionCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '膏方',
    dataIndex: 'pasteCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '丸剂',
    dataIndex: 'pillCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '散剂',
    dataIndex: 'powderCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '其他',
    dataIndex: 'otherCount',
    width: 90,
    hideInSearch: true,
  },
  {
    title: '剂数合计',
    dataIndex: 'doseTotal',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '饮片剂数',
    dataIndex: 'piecesDoseCount',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '代煎剂数',
    dataIndex: 'decoctionDoseCount',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '膏方剂数',
    dataIndex: 'pasteDoseCount',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '丸剂剂数',
    dataIndex: 'pillDoseCount',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '散剂剂数',
    dataIndex: 'powderDoseCount',
    width: 110,
    hideInSearch: true,
  },
  {
    title: '其他剂数',
    dataIndex: 'otherDoseCount',
    width: 110,
    hideInSearch: true,
  },
];

export function PrescriptionCountReportPage() {
  return (
    <QueryTableShell
      title="处方数量统计"
      subtitle="按时间、机构和煎煮中心统计处方数量与剂数。"
      actions={
        <Button disabled icon={<ExportOutlined />} title="报表导出接口将在后续迁移阶段接入">
          导出报表
        </Button>
      }
      filters={null}
      table={
        <ProTable<PrescriptionCountRecord>
          rowKey="date"
          columns={columns}
          options={false}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          scroll={{ x: 1540 }}
          request={async () => ({ data: [], success: true, total: 0 })}
          locale={{ emptyText: '暂无处方数量统计数据，请选择统计条件后查询。' }}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
