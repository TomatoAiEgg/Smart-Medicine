import { Tag } from 'antd';

interface StatusTagProps {
  value: string | boolean | null | undefined;
  labels?: Record<string, string>;
}

const statusColors: Record<string, string> = {
  ENABLED: 'blue',
  DISABLED: 'default',
  ACTIVE: 'green',
  INACTIVE: 'default',
  PENDING: 'gold',
  SUCCESS: 'green',
  FAILED: 'red',
};

const statusLabels: Record<string, string> = {
  ENABLED: '已启用',
  DISABLED: '已停用',
  ACTIVE: '正常',
  INACTIVE: '停用',
  PENDING: '待处理',
  SUCCESS: '成功',
  FAILED: '失败',
};

export function StatusTag({ value, labels }: StatusTagProps) {
  if (
    value === null ||
    value === undefined ||
    value === '' ||
    (typeof value === 'string' && value.trim() === '')
  ) {
    return <Tag>-</Tag>;
  }

  const rawValue = typeof value === 'boolean' ? (value ? 'ENABLED' : 'DISABLED') : value;
  const statusKey = rawValue.trim().toUpperCase();
  const label = labels?.[statusKey] ?? labels?.[rawValue] ?? statusLabels[statusKey] ?? rawValue;

  return <Tag color={statusColors[statusKey]}>{label}</Tag>;
}
