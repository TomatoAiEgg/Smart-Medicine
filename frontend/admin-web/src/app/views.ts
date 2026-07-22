export type ViewKey =
  | 'dashboard'
  | 'orders'
  | 'reviews'
  | 'dispenses'
  | 'rechecks'
  | 'decoction'
  | 'logistics'
  | 'portal'
  | 'reports'
  | 'integration'
  | 'observability'
  | 'ops';

export interface MenuItem {
  key: ViewKey;
  label: string;
  group: string;
  showCount?: boolean;
}

export const menuItems: MenuItem[] = [
  { key: 'dashboard', label: '工作台', group: '系统管理' },
  { key: 'portal', label: '门户查单', group: '机构管理' },
  { key: 'orders', label: '处方列表', group: '订单管理' },
  { key: 'reviews', label: '订单审核', group: '订单管理', showCount: true },
  { key: 'dispenses', label: '调剂打印', group: '订单管理', showCount: true },
  { key: 'rechecks', label: '处方复核', group: '订单管理', showCount: true },
  { key: 'observability', label: '订单流程', group: '订单管理', showCount: true },
  { key: 'logistics', label: '物流发货', group: '物流管理', showCount: true },
  { key: 'decoction', label: '煎煮作业', group: '煎煮管理', showCount: true },
  { key: 'integration', label: '集成任务', group: '维护管理', showCount: true },
  { key: 'ops', label: '运维审计', group: '维护管理', showCount: true },
  { key: 'reports', label: '报表统计', group: '报表管理', showCount: true },
];

export const viewTitles: Record<ViewKey, { title: string; subtitle: string }> = {
  dashboard: { title: '工作台', subtitle: '查看核心待处理事项和系统健康概览' },
  orders: { title: '处方列表', subtitle: '查询订单详情、处方和履约进度' },
  reviews: { title: '订单审核', subtitle: '处理待审核订单' },
  dispenses: { title: '调剂打印', subtitle: '处理待调剂处方任务' },
  rechecks: { title: '处方复核', subtitle: '处理待复核处方任务' },
  decoction: { title: '煎煮作业', subtitle: '处理处方绑定、煎煮状态和设备作业记录' },
  logistics: { title: '物流发货', subtitle: '处理打包、发货、签收和回调补偿' },
  portal: { title: '门户查单', subtitle: '医院查单和地址补录申请' },
  integration: { title: '集成任务', subtitle: '外围消息、地址回推和重试任务' },
  reports: { title: '报表统计', subtitle: '查看核心指标、状态分布和趋势导出' },
  observability: { title: '订单流程', subtitle: '按订单聚合访问、状态、MQ、回调、死信和补偿证据' },
  ops: { title: '运维审计', subtitle: '查询事件、消费、校验、访问和失败任务' },
};
