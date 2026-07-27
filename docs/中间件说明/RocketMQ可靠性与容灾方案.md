# RocketMQ 可靠性与容灾方案

## 1. 定位

RocketMQ 在本项目中不是单纯的异步工具，而是订单、处方、煎煮、物流、回调、通知和外围集成之间的业务事件总线。

第一版目标不是做多机生产级高可用集群，而是在开发测试服务器上把消息可靠性、丢失补偿、未消费发现、积压治理、死信重放和单机容灾恢复能力设计完整。

部署目标主机：

```text
47.120.55.53
```

## 2. 可靠性目标

消息链路至少要回答这些问题：

- 业务事务成功后，消息有没有生成。
- 消息生成后，有没有发布到 RocketMQ。
- 消息发布后，有没有被目标消费组消费。
- 消费失败后，是否进入重试或死信。
- 长时间未消费时，是否能告警和补偿。
- MQ 或服务器异常后，是否能恢复业务事实。

本项目采用：

```text
本地事务 + event_outbox + RocketMQ + message_consume_log + 对账任务 + 死信重放 + 人工补偿
```

## 3. 消息状态模型

### 3.1 Outbox 状态

`event_outbox` 建议状态：

| 状态 | 说明 |
| --- | --- |
| `NEW` | 业务事务内新建，等待发布 |
| `PUBLISHING` | 发布器已锁定，正在发送 |
| `PUBLISHED` | 已成功发送到 RocketMQ |
| `PUBLISH_FAILED` | 发布失败，等待重试 |
| `WAIT_CONSUME_TIMEOUT` | 已发布但长时间无消费记录 |
| `DEAD` | 超过发布或补偿阈值，进入人工处理 |
| `CLOSED` | 人工确认关闭 |

关键字段：

```text
event_id
event_type
topic
tag
aggregate_type
aggregate_id
tenant_id
payload
status
retry_count
max_retry_count
next_retry_at
last_error
published_at
created_at
updated_at
```

### 3.2 消费日志状态

`message_consume_log` 建议状态：

| 状态 | 说明 |
| --- | --- |
| `PROCESSING` | 已收到消息，正在处理 |
| `SUCCESS` | 消费成功 |
| `FAILED_RETRYABLE` | 可重试失败 |
| `FAILED_FATAL` | 不可自动恢复 |
| `DEAD` | 超过重试阈值，进入人工处理 |
| `SKIPPED` | 幂等跳过或业务状态已推进 |

唯一键：

```text
consumer_group + event_id
```

辅助字段：

```text
topic
tag
aggregate_id
consume_started_at
consume_finished_at
retry_count
last_error
trace_endpoint
```

链路追踪由 SkyWalking 承载，表里只保留业务排查所需的 endpoint、订单号、事件 ID 和消费组。

## 4. 防消息丢失

### 4.1 业务事务到消息生成

业务写库和 Outbox 写入必须在同一个本地事务中完成。

示例：

```text
创建订单事务
 -> 写 order_main
 -> 写 prescription
 -> 写 prescription_detail
 -> 写 order_status_log
 -> 写 event_outbox(OrderCreated)
 -> 提交
```

如果事务回滚，业务数据和事件都不存在。
如果事务提交，事件一定在 `event_outbox` 中可见。

### 4.2 Outbox 到 RocketMQ

发布器从 `event_outbox` 扫描 `NEW`、`PUBLISH_FAILED` 且到达 `next_retry_at` 的事件。

处理流程：

```text
扫描待发布事件
 -> 用条件更新锁定为 PUBLISHING
 -> 发送 RocketMQ
 -> 成功后更新为 PUBLISHED
 -> 失败后更新为 PUBLISH_FAILED 并写 last_error
 -> 超过阈值更新为 DEAD
```

发布器必须支持重复运行，不能依赖单次进程内状态。

### 4.3 RocketMQ 到消费者

RocketMQ 按至少一次投递处理。消费者必须认为消息可能重复，不能认为只会消费一次。

消费流程：

```text
收到消息
 -> 插入或检查 message_consume_log
 -> 已 SUCCESS：直接返回成功
 -> 未 SUCCESS：执行业务处理
 -> 业务成功：更新 SUCCESS
 -> 可恢复失败：记录失败并抛异常，让 MQ 重试
 -> 不可恢复失败：记录 FAILED_FATAL 或 DEAD，进入人工处理
```

## 5. 对账补偿

只靠 MQ 重试不够，还要有定时对账任务，防止业务事实和消息状态不一致。

### 5.1 业务事实缺事件

检查逻辑：

```text
订单状态已创建/审方/调剂/复核/发货
 -> 查不到对应 event_outbox
 -> 生成补偿事件
 -> 标记 source=RECONCILE
 -> 进入发布流程
```

适用场景：

- 历史数据迁移。
- 代码缺陷导致少写事件。
- 人工修正业务状态后需要补发事件。

### 5.2 事件已生成但未发布

检查逻辑：

```text
event_outbox.status in (NEW, PUBLISH_FAILED, PUBLISHING)
且 updated_at 超过阈值
 -> 重置为 PUBLISH_FAILED
 -> 重新计算 next_retry_at
 -> 告警
```

`PUBLISHING` 不能永久卡住。发布器宕机后，超时任务应把卡住的记录释放。

### 5.3 已发布但未消费

检查逻辑：

```text
event_outbox.status = PUBLISHED
且 published_at 超过阈值
且目标 consumer_group 没有 SUCCESS 记录
 -> 标记 WAIT_CONSUME_TIMEOUT
 -> 告警
 -> 支持重投或人工补偿
```

是否需要每个事件配置目标消费组，取决于事件重要程度。核心事件必须配置，例如：

| 事件 | 目标消费组 |
| --- | --- |
| `OrderCreated` | `workflow-service`、`report-service` |
| `PrescriptionReviewed` | `decoction-service`、`callback-service` |
| `OrderShipped` | `callback-service`、`notification-service` |
| `CallbackFailed` | `ops-service` |

### 5.4 消费失败后业务未完成

检查逻辑：

```text
message_consume_log.status in (FAILED_RETRYABLE, DEAD)
 -> 根据 aggregate_id 查询业务状态
 -> 已完成：标记 SKIPPED 或 SUCCESS
 -> 未完成：进入重试、死信或人工补偿
```

## 6. 未消费和积压治理

### 6.1 未消费分类

未消费不能简单归为 MQ 问题，要分类型处理：

| 类型 | 判断依据 | 处理 |
| --- | --- | --- |
| 消费者未启动 | 消费组无实例 | 拉起消费者，告警 |
| 消费者订阅错误 | Topic 有消息但消费组无消费 | 检查 topic/tag/group 配置 |
| 消费慢 | 消费中但 lag 增长 | 扩容消费者，优化处理逻辑 |
| 下游慢 | 消费耗时集中在 DB/HTTP | 优化 SQL、限流外部调用 |
| 毒消息 | 单条消息反复失败 | 进入死信，人工处理 |
| 顺序阻塞 | 同一顺序键前序消息失败 | 修复前序消息后重试 |

### 6.2 积压处理流程

```text
发现 Topic 积压
 -> 判断是否单 Topic、单消费组、单队列积压
 -> 检查消费者实例是否存活
 -> 检查消费失败日志和 dead_letter_record
 -> 检查数据库慢 SQL 和连接池
 -> 检查外部 HTTP 调用耗时
 -> 临时扩容消费者
 -> 暂停低优先级消费者
 -> 恢复后观察追平时间
```

优先级策略：

| 优先级 | 事件 |
| --- | --- |
| P0 | 订单创建、审方、调剂、复核、发货 |
| P1 | 回调、物流轨迹、设备状态 |
| P2 | 通知、报表、审计同步 |

积压严重时，优先保证 P0 链路，P2 可以延后。

### 6.3 消费者扩容

消费者服务要保持无状态，支持多副本部署。

扩容注意点：

- 消费逻辑必须幂等。
- 顺序消息只能按队列内顺序消费，不能简单通过无限扩实例解决。
- 外部接口消费者要设置并发上限，避免把压力打到第三方。
- 消费者扩容后要观察数据库连接池和锁等待。

## 7. 死信和人工重放

进入死信条件：

- 超过最大重试次数。
- 业务数据缺失且无法自动恢复。
- 签名、配置、机构信息错误。
- 外部系统长期不可用。
- 毒消息导致固定异常。

`dead_letter_record` 建议字段：

```text
dead_id
event_id
topic
consumer_group
aggregate_id
payload_snapshot
error_message
retry_count
status
created_at
updated_at
operator
remark
```

后台能力：

- 查询死信。
- 查看原始 payload 摘要。
- 查看失败原因。
- 查看关联订单和状态日志。
- 手动重放。
- 手动标记已处理。
- 手动关闭并写原因。

人工重放必须写操作日志，不能静默修改。

## 8. 容灾设计

### 8.1 第一版单机容灾

当前开发测试环境是单台云服务器，第一版容灾重点是可备份、可恢复、可重建。

需要备份：

| 资源 | 备份内容 |
| --- | --- |
| PostgreSQL | 业务库、Outbox、消费日志、回调记录、死信记录 |
| RocketMQ | broker 存储目录、namesrv/broker 配置 |
| Redis | 配置缓存可重建，必要时保留 RDB/AOF |
| Nginx | 配置和前端产物 |
| 观测组件 | Prometheus/Grafana/SkyWalking 配置 |
| 部署配置 | docker-compose、`.env`、secrets、镜像 tag |

`.env`、secrets 只能在服务器侧保存，不能写入仓库。

### 8.2 RPO 和 RTO

开发测试环境建议目标：

| 资源 | RPO | RTO |
| --- | --- | --- |
| PostgreSQL | 24 小时以内 | 2 小时内恢复 |
| RocketMQ | 允许从 Outbox 补发 | 1 小时内恢复 |
| Redis | 可重建缓存 | 30 分钟内恢复 |
| 前端和后端镜像 | 可重新部署 | 30 分钟内恢复 |
| Grafana/Prometheus 配置 | 24 小时以内 | 1 小时内恢复 |

RocketMQ 数据即使丢失，也不能直接导致业务事件永久丢失。核心原因是业务事实和待发布事件保存在 PostgreSQL 的 `event_outbox` 中，可以补发。

### 8.3 RocketMQ 故障恢复

RocketMQ 不可用时：

```text
业务服务继续写本地业务表和 event_outbox
 -> message-service 发布失败
 -> event_outbox 进入 PUBLISH_FAILED
 -> 监控告警
 -> RocketMQ 恢复后自动补发
```

如果 Broker 数据损坏或丢失：

```text
停止 message-service 发布器
 -> 恢复或重建 RocketMQ
 -> 将未确认消费的核心事件重置为 PUBLISH_FAILED
 -> 从 event_outbox 补发
 -> 消费者用 message_consume_log 保证幂等
```

### 8.4 PostgreSQL 故障恢复

PostgreSQL 是业务事实源，优先级高于 MQ。

恢复要求：

- 每天定时备份。
- 升级和迁移前手动备份。
- 恢复后先校验业务表、Outbox、消费日志和回调记录。
- 恢复后执行对账任务，补齐缺失事件。

### 8.5 Redis 故障恢复

Redis 不作为唯一事实源。

故障影响：

- 字典、机构、白名单等缓存短暂不可用。
- 短期幂等标记可能丢失。
- 分布式锁不可用时相关操作应降级或拒绝。

恢复策略：

- 从 PostgreSQL 重新加载缓存。
- 关键幂等仍以数据库唯一约束和业务表状态为准。
- 不把核心订单状态只放 Redis。

### 8.6 整机不可用

整机不可用时的恢复顺序：

```text
新建服务器
 -> 安装 Docker/Compose
 -> 恢复部署目录
 -> 恢复 PostgreSQL
 -> 启动 Redis/RocketMQ
 -> 启动应用服务
 -> 执行 Outbox 对账和补发
 -> 检查 MQ 消费和死信
 -> 开放 Nginx
```

恢复完成后必须跑业务验证：

```text
外部下单 -> 后台订单列表 -> 审方 -> 调剂 -> 复核 -> 物流 -> 回调
```

## 9. 监控和告警

必须监控：

- `event_outbox.NEW` 数量。
- `event_outbox.PUBLISH_FAILED` 数量。
- `event_outbox.PUBLISHING` 超时数量。
- `event_outbox.WAIT_CONSUME_TIMEOUT` 数量。
- `message_consume_log.FAILED_RETRYABLE` 数量。
- `message_consume_log.DEAD` 数量。
- RocketMQ Topic 积压。
- RocketMQ Broker 存活。
- 消费组在线实例数。
- 单条消息消费耗时。
- 死信新增数量。
- 人工重放成功和失败数量。

告警分级：

| 等级 | 条件 | 处理 |
| --- | --- | --- |
| P0 | 订单核心 Topic 长时间积压或 Broker 不可用 | 立即处理 |
| P1 | Outbox 发布失败持续增加 | 检查 RocketMQ 和 message-service |
| P1 | 核心消费组长时间未消费 | 拉起消费者或修复配置 |
| P2 | 通知、报表类事件积压 | 可延后处理 |
| P2 | 单条死信新增 | 白天人工处理 |

## 10. 压测和演练

必须设计故障演练：

| 演练 | 验证点 |
| --- | --- |
| 停止 RocketMQ Broker 5 分钟 | Outbox 堆积，恢复后自动补发 |
| 停止 workflow-service 5 分钟 | 订单事件积压，恢复后消费追平 |
| 制造消费者异常 | 消费重试、死信和人工重放 |
| 重复投递同一消息 | 消费幂等生效 |
| 删除 Redis 缓存 | 缓存可从数据库重建 |
| 恢复 PostgreSQL 备份 | 业务数据和 Outbox 可校验 |

演练报告记录：

```text
演练日期：
故障类型：
持续时间：
最大积压：
恢复后追平时间：
丢失消息数量：
补偿事件数量：
人工处理数量：
结论：
```

## 11. 第一版落地范围

第一版必须落地：

- `event_outbox` 状态机。
- `message_consume_log` 幂等表。
- Outbox 发布重试。
- 消费失败记录。
- 死信记录。
- 运维后台查询和人工重放。
- Outbox 积压和消费失败监控。
- RocketMQ 故障后从 Outbox 补发。
- PostgreSQL 定时备份和恢复演练。

第一版暂不做：

- 多机 RocketMQ 集群。
- 跨机房容灾。
- 多活部署。
- 全局强事务。
- 多 MQ 混用。

## 12. 后续升级

如果未来需要更接近生产高可用，可以逐步升级：

- RocketMQ NameServer 多节点。
- Broker 主从或多副本。
- PostgreSQL 主从复制和备份归档。
- Redis 持久化和哨兵/集群。
- 应用服务多副本部署。
- 备份文件同步到对象存储。
- 跨服务器恢复演练。

升级前提是先把第一版的 Outbox、幂等、死信、补偿和监控做扎实。
