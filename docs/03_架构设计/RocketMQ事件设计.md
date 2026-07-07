# RocketMQ 事件设计

## 1. 设计目标

RocketMQ 作为业务事件总线，不只是替代旧 ActiveMQ 队列。

目标：

- 解耦订单、处方、煎煮、物流、回调、通知。
- 支持削峰和重试。
- 支持消费幂等。
- 支持业务链路追踪。
- 避免业务事务成功但消息发送失败。

## 2. 旧队列映射

| 旧 ActiveMQ 队列 | 新事件 | 新 Topic |
| --- | --- | --- |
| `createOrderSuccess.service.queue` | `OrderCreated` | `zhyf-order-event` |
| `reviewFinishPushRecipe.service.queue` | `PrescriptionReviewed` | `zhyf-prescription-event` |
| `sendMsgMessageMQ.service.queue` | `NotificationRequested` | `zhyf-notification-event` |
| `orderInfoValidationMQ.service.queue` | `OrderValidationRequested` | `zhyf-order-event` |
| `threeAudit.service.queue` | `InstitutionAuditCallbackRequested` | `zhyf-callback-event` |
| `orderStatusChange.service.queue` | `OrderStatusChanged` | `zhyf-order-event` |

## 3. Topic 规划

| Topic | 用途 |
| --- | --- |
| `zhyf-order-event` | 订单事件 |
| `zhyf-prescription-event` | 处方事件 |
| `zhyf-decoction-event` | 煎煮事件 |
| `zhyf-logistics-event` | 物流事件 |
| `zhyf-callback-event` | 回调事件 |
| `zhyf-notification-event` | 短信和通知事件 |
| `zhyf-integration-event` | 社康、地址补录等外围集成事件 |
| `zhyf-dead-letter-event` | 业务死信事件 |

## 4. 事件结构

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "tenantId": "tenant-id",
  "aggregateType": "ORDER",
  "aggregateId": "order-id",
  "occurredAt": "2026-06-18T20:00:00+08:00",
  "traceId": "trace-id",
  "source": "order-service",
  "payload": {}
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `eventId` | 全局唯一事件 ID |
| `eventType` | 事件类型 |
| `tenantId` | 租户 |
| `aggregateType` | 聚合类型 |
| `aggregateId` | 聚合 ID |
| `occurredAt` | 事件发生时间 |
| `traceId` | 链路追踪 ID |
| `source` | 事件来源服务 |
| `payload` | 业务负载 |

## 5. Outbox 模式

所有业务事件必须先写 `event_outbox`。

流程：

```text
业务请求
 -> 开启事务
 -> 写业务表
 -> 写状态日志
 -> 写 event_outbox
 -> 提交事务
 -> Outbox 发布器扫描待发布事件
 -> 发送 RocketMQ
 -> 更新 event_outbox 状态
```

要求：

- 不允许业务代码在事务中直接发送 MQ 后就结束。
- `event_outbox.event_id` 唯一。
- 发布失败可重试。
- 超过最大次数进入死信。

## 6. 消费幂等

每个消费者必须写 `message_consume_log`。

幂等键：

```text
consumer_group + event_id
```

流程：

```text
收到消息
 -> 查询消费日志
 -> 已成功：忽略
 -> 未成功：执行业务
 -> 成功：写成功日志
 -> 失败：写失败日志并抛异常
```

## 7. 订单事件

Topic：`zhyf-order-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `OrderCreated` | 订单创建成功 | 校验、审方、通知、报表 |
| `OrderReviewed` | 后台审核通过 | 回调、通知、报表 |
| `OrderRechecked` | 全部处方复核完成 | 回调、设备推方 |
| `OrderStatusChanged` | 订单状态变化 | 回调服务 |
| `OrderPacked` | 打包完成 | 回调、通知 |
| `OrderShipped` | 物流发货 | 回调、通知 |
| `OrderSigned` | 签收 | 回调、报表 |
| `OrderCancelled` | 取消 | 回调、社康退费 |
| `OrderManualCorrected` | 人工修正 | 审计、报表 |
| `OrderValidationRequested` | 订单修改后重新校验 | 校验消费者 |

## 8. 处方事件

Topic：`zhyf-prescription-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `PrescriptionCreated` | 处方创建 | 药品匹配、校验 |
| `PrescriptionAuditUpdated` | 审方结果写入 | 后台提醒 |
| `PrescriptionDispensed` | 调剂完成 | 复核列表刷新 |
| `PrescriptionReviewed` | 复核完成 | 设备推方、回调 |
| `PrescriptionCancelled` | 处方取消 | 订单状态判断 |
| `PrescriptionModified` | 后台修改处方 | 重新校验 |

## 9. 煎煮事件

Topic：`zhyf-decoction-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `DecoctionDeviceBound` | 处方绑定煎煮机 | 设备状态、轨迹 |
| `WaterPailBound` | 复核绑定水桶 | 轨迹 |
| `WaterFinished` | 加水完成 | 轨迹 |
| `DecoctionStarted` | 开始煎煮 | 订单状态、回调 |
| `DecoctionFinished` | 煎煮完成 | 订单状态、回调 |
| `DecoctionCancelled` | 取消煎煮 | 设备释放、审计 |
| `DecoctionTerminated` | 终止煎煮 | 设备释放、审计 |
| `DeviceReleased` | 设备释放 | 设备模块 |

## 10. 物流事件

Topic：`zhyf-logistics-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `ShipmentCreated` | 物流单创建 | 面单、报表 |
| `OrderPacked` | 打包完成 | 回调、通知 |
| `ShipmentTraceReceived` | 物流轨迹回调 | 状态机、轨迹 |
| `ShipmentShipped` | 发货 | 回调、通知 |
| `ShipmentInTransit` | 运输中 | 轨迹 |
| `ShipmentSigned` | 签收 | 订单状态、回调 |
| `ShipmentCancelled` | 取消物流 | 审计 |

## 11. 回调事件

Topic：`zhyf-callback-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `CallbackRequested` | 需要回调机构 | callback-service |
| `CallbackSucceeded` | 回调成功 | 报表/审计 |
| `CallbackFailed` | 回调失败 | 重试调度 |
| `CallbackDeadLettered` | 超过重试次数 | 后台人工处理 |
| `CallbackReplayed` | 人工重放 | callback-service |

## 12. 通知事件

Topic：`zhyf-notification-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `NotificationRequested` | 需要短信/通知 | message-service |
| `NotificationSucceeded` | 发送成功 | 记录 |
| `NotificationFailed` | 发送失败 | 重试 |

## 13. 集成事件

Topic：`zhyf-integration-event`

| 事件 | 触发点 | 消费方 |
| --- | --- | --- |
| `CommunityMessagePulled` | 拉到社康消息 | 社康处理器 |
| `CommunityOrderFetched` | 拉到社康订单详情 | 转换器 |
| `CommunityOrderUploaded` | 上传新系统成功 | 社康状态回写 |
| `CommunityStatusUpdateFailed` | 回写社康失败 | 重试任务 |
| `AddressSubmitted` | 患者提交地址 | 地址保存和回推 |
| `AddressPushRequested` | 需要回推医院 | 地址适配器 |
| `AddressPushFailed` | 地址回推失败 | 重试/人工处理 |

## 14. 顺序策略

需要局部有序的事件：

| 事件类型 | 顺序键 |
| --- | --- |
| 订单状态事件 | `order_id` |
| 处方作业事件 | `prescription_id` |
| 煎煮任务事件 | `decoction_task_id` |
| 物流轨迹事件 | `shipment_id` |

## 15. 重试策略

| 场景 | 策略 |
| --- | --- |
| 消费者临时异常 | RocketMQ 重试 |
| 第三方回调失败 | callback-service 自管重试 |
| 社康回写失败 | integration-service 自管重试 |
| 地址回推失败 | integration-service 自管重试 |
| 数据校验失败 | 不无限重试，写异常等待人工处理 |

## 16. 死信策略

进入死信条件：

- 超过最大重试次数。
- 业务数据缺失且无法自动恢复。
- 第三方长期不可用。
- 签名/配置错误无法自动修复。

死信处理：

- 写 `dead_letter_record` 或 `callback_record.status=DEAD`。
- 发布 `CallbackDeadLettered` 或 `IntegrationDeadLettered`。
- 后台支持人工重放。

## 17. 监控指标

必须监控：

- Outbox 待发布数量。
- MQ 堆积数量。
- 消费失败数量。
- 回调失败数量。
- 死信数量。
- 社康回写失败数量。
- 地址回推失败数量。
- 消费耗时。

## 18. 当前结论

第一版必须落实 Outbox、消费幂等和回调补偿。

如果只是把 ActiveMQ 队列换成 RocketMQ Topic，而不改事务边界和幂等设计，旧系统的问题会继续存在。
