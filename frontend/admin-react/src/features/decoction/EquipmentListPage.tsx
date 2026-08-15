import { PlusOutlined } from '@ant-design/icons';
import { ProTable, type ProColumns } from '@ant-design/pro-components';
import { Button, Space } from 'antd';
import { listAdminDecoctionDevices, type DeviceRecord } from '../../api/decoction';
import { QueryTableShell } from '../../components/QueryTableShell';
import { StatusTag } from '../../components/StatusTag';
import { formatDate } from '../../utils/formatters';

interface EquipmentRecord {
  id: string;
  equipmentType: string;
  equipmentCode: string;
  equipmentName: string;
  serialNo: string;
  ipAddress: string;
  groupName: string;
  enabled: boolean;
  used: boolean;
  decoctionCenter: string;
  operatorName: string;
  createdAt: string;
  updatedAt: string;
}

interface EquipmentQueryParams {
  current?: number;
  pageSize?: number;
  id?: string;
  equipmentType?: string;
  equipmentCode?: string;
  equipmentName?: string;
  serialNo?: string;
  ipAddress?: string;
  groupName?: string;
  enabled?: string | boolean;
  decoctionCenter?: string;
}

const enabledLabels: Record<string, string> = {
  ENABLED: '已启用',
  DISABLED: '已停用',
};

const usedLabels: Record<string, string> = {
  ACTIVE: '使用中',
  INACTIVE: '未使用',
};

function mapDeviceRecord(record: DeviceRecord): EquipmentRecord {
  return {
    id: record.deviceId ?? record.deviceCode,
    equipmentType: record.deviceType,
    equipmentCode: record.deviceCode,
    equipmentName: record.deviceName,
    serialNo: record.remark ?? '',
    ipAddress: record.ipAddress ?? '',
    groupName: record.deviceGroup ?? '',
    enabled: record.enabled,
    used: Boolean(record.activeTaskNo || record.activePrescriptionNo),
    decoctionCenter: record.decoctionCenter ?? '',
    operatorName: '',
    createdAt: record.createdAt ?? '',
    updatedAt: record.updatedAt ?? '',
  };
}

const columns: ProColumns<EquipmentRecord>[] = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 100,
    ellipsis: true,
  },
  {
    title: '设备类型',
    dataIndex: 'equipmentType',
    width: 140,
    valueType: 'select',
    valueEnum: {
      decoctionMachine: { text: '煎药机' },
      packagingMachine: { text: '包装机' },
      waterPail: { text: '加水桶' },
      printer: { text: '打印机' },
    },
  },
  {
    title: '设备编号',
    dataIndex: 'equipmentCode',
    width: 150,
    ellipsis: true,
  },
  {
    title: '设备名称',
    dataIndex: 'equipmentName',
    width: 160,
    ellipsis: true,
  },
  {
    title: '设备序列号',
    dataIndex: 'serialNo',
    width: 180,
    ellipsis: true,
  },
  {
    title: '设备IP',
    dataIndex: 'ipAddress',
    width: 140,
    ellipsis: true,
  },
  {
    title: '设备组别',
    dataIndex: 'groupName',
    width: 140,
    ellipsis: true,
  },
  {
    title: '状态',
    dataIndex: 'enabled',
    width: 100,
    valueType: 'select',
    valueEnum: {
      true: { text: '已启用' },
      false: { text: '已停用' },
    },
    render: (_, row) => <StatusTag value={row.enabled} labels={enabledLabels} />,
  },
  {
    title: '使用状态',
    dataIndex: 'used',
    width: 110,
    valueType: 'select',
    valueEnum: {
      true: { text: '使用中' },
      false: { text: '未使用' },
    },
    render: (_, row) => <StatusTag value={row.used ? 'ACTIVE' : 'INACTIVE'} labels={usedLabels} />,
  },
  {
    title: '煎煮中心',
    dataIndex: 'decoctionCenter',
    width: 160,
    ellipsis: true,
  },
  {
    title: '操作人',
    dataIndex: 'operatorName',
    width: 120,
    ellipsis: true,
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    width: 170,
    valueType: 'dateTime',
    render: (_, row) => formatDate(row.createdAt),
  },
  {
    title: '修改时间',
    dataIndex: 'updatedAt',
    width: 170,
    valueType: 'dateTime',
    render: (_, row) => formatDate(row.updatedAt),
  },
  {
    title: '操作',
    valueType: 'option',
    width: 90,
    fixed: 'right',
    render: () => (
      <Space size={4}>
        <Button type="link" size="small">
          修改
        </Button>
      </Space>
    ),
  },
];

export function EquipmentListPage() {
  return (
    <QueryTableShell
      title="设备列表查询"
      subtitle="煎煮设备档案、状态和绑定关系维护。"
      actions={
        <Button type="primary" icon={<PlusOutlined />}>
          添加设备
        </Button>
      }
      filters={null}
      table={
        <ProTable<EquipmentRecord, EquipmentQueryParams>
          rowKey="id"
          columns={columns}
          options={false}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          scroll={{ x: 1910 }}
          request={async (params) => {
            const page = await listAdminDecoctionDevices({
              deviceId: params.id,
              deviceCode: params.equipmentCode,
              deviceName: params.equipmentName,
              deviceType: params.equipmentType,
              deviceGroup: params.groupName,
              ipAddress: params.ipAddress,
              decoctionCenter: params.decoctionCenter,
              enabled: params.enabled,
            });
            return {
              data: page.records.map(mapDeviceRecord),
              success: true,
              total: page.total,
            };
          }}
          locale={{ emptyText: '暂无煎煮设备数据，请调整查询条件或添加设备。' }}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      }
    />
  );
}
