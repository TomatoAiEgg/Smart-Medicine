# workflow-service 审核过渡能力迁移方案

## 1. 现状

第一阶段真实端到端链路已经跑通：

```text
gateway 推单
 -> order-service 订单/处方落库
 -> event_outbox 写 ORDER_CREATED
 -> message-service 发布 RocketMQ
 -> message-service 消费 ORDER_CREATED
 -> order_validation_record 写 PASSED/REJECTED
 -> 校验通过创建 workflow_task: ORDER_REVIEW/PENDING
 -> order-service 查询待审核任务
 -> order-service 审核通过/拒绝并推进 order_main.status
```

当前审核相关职责分散在两个服务：

- `message-service`
  - `OrderCreatedEventHandler` 完成 `ORDER_CREATED` 基础校验。
  - 校验通过后通过 `WorkflowTaskRepository` 直接写 `workflow_task`。
- `order-service`
  - `OrderReviewTaskService` 查询 `workflow_task`。
  - 审核通过推进 `order_main.status=AUDIT_PASSED`。
  - 审核拒绝推进 `order_main.status=AUDIT_FAILED`。
  - 写 `order_status_log`。

`workflow_task` 当前是第一阶段过渡表，逻辑归属已经在架构文档中标为后续 `workflow-service`。真实联调已证明链路可用，但服务职责仍然偏重 `order-service` 和 `message-service`。

## 2. 目标

本次目标是把审核过渡能力从 `order-service` 和 `message-service` 中迁移或抽象到真正的 `workflow-service`，让职责边界更接近目标架构：

- `workflow-service` 承接工作流任务创建、查询、审核通过、审核拒绝。
- `message-service` 只负责消息消费、幂等和事件路由，不直接写工作流任务表。
- `order-service` 只负责订单状态持久化和状态变更能力，不再对外暴露审核任务接口。
- 第一阶段继续复用 `workflow_task` 表，不新增复杂流程引擎。
- 保持现有端到端链路可跑通。

## 3. 变更范围

### 3.1 后端模块

新增 Maven 模块：

```text
backend/workflow-service
```

建议包结构：

```text
com.zhyf.workflow
├── api
├── application
├── consumer
├── domain
├── infrastructure
└── WorkflowServiceApplication
```

### 3.2 workflow-service 第一版能力

第一版只承接订单审核任务：

- 消费或接收 `ORDER_CREATED` 校验通过后的任务创建请求。
- 创建 `workflow_task: ORDER_REVIEW/PENDING`。
- 查询待审核任务。
- 审核通过任务：标记 `workflow_task.APPROVED`。
- 审核拒绝任务：标记 `workflow_task.REJECTED`。
- 调用 `order-service` 内部接口推进订单状态。

### 3.3 order-service 调整

保留订单状态变更的内部能力，新增或明确内部接口：

- `PATCH /internal/orders/{orderId}/status`

该接口只允许内部服务调用，第一阶段先不引入完整鉴权网关，但要明确它不是外部机构接口。

迁移后，下列接口从 `order-service` 移除或标记废弃：

- `GET /api/admin/review-tasks`
- `PATCH /api/admin/review-tasks/{taskId}/approve`
- `PATCH /api/admin/review-tasks/{taskId}/reject`

为了降低联调风险，可以先保留旧接口一段时间，但文档标明由 `workflow-service` 接管。

### 3.4 message-service 调整

`message-service` 不再直接依赖 `WorkflowTaskRepository` 写 `workflow_task`。

可选落地方式：

1. 推荐：`message-service` 校验通过后发布内部事件或调用 `workflow-service` 内部接口创建审核任务。
2. 过渡：`workflow-service` 自己消费 `ORDER_CREATED`，并复用 `message_consume_log` 形成独立消费组。

第一版建议选择第 2 种：`workflow-service` 独立消费 `ORDER_CREATED`。

原因：

- 消息事实仍来自 `event_outbox` 和 RocketMQ。
- `message-service` 继续负责 Outbox 发布和消费幂等验证链路。
- `workflow-service` 对工作流任务有自己的消费组和幂等记录，服务边界更清楚。

需要注意：如果 `message-service` 继续做 `ORDER_CREATED` 基础校验，`workflow-service` 不能重复执行相同校验并产生冲突。第一版可以让 `workflow-service` 查询 `order_validation_record`，只在 `PASSED` 时创建任务。

### 3.5 数据库

本次不新增主表，继续复用：

- `workflow_task`
- `order_validation_record`
- `message_consume_log`
- `order_main`
- `order_status_log`

如需服务独立 migration，第一版可以暂不移动已有 migration，避免重复创建表。后续再在迁移窗口把 `workflow_task` 的 migration 从 `order-service` 归档到 `workflow-service`。

## 4. 不变范围

本次不做：

- 不引入完整 BPMN / 工作流引擎。
- 不新增调剂、复核、煎煮、物流流程。
- 不修改机构推单外部协议。
- 不修改 `ORDER_CREATED` 事件结构。
- 不清理已有 E2E 测试数据。
- 不改变 `gateway`、`auth-institution` 的机构鉴权链路。
- 不上传 jar 到服务器运行。
- 不启动本地 Docker Desktop。

## 5. 接口建议

### 5.1 workflow-service

```text
GET /api/admin/workflow/review-tasks
PATCH /api/admin/workflow/review-tasks/{taskId}/approve
PATCH /api/admin/workflow/review-tasks/{taskId}/reject
```

请求体沿用当前审核命令：

```json
{
  "reviewer": "reviewer1",
  "reviewComment": "ok"
}
```

响应沿用当前审核结果字段：

```json
{
  "taskId": "uuid",
  "orderId": "uuid",
  "orderNo": "string",
  "taskStatus": "APPROVED",
  "orderStatus": "AUDIT_PASSED",
  "reviewer": "reviewer1",
  "reviewComment": "ok",
  "completedAt": "datetime"
}
```

### 5.2 order-service 内部接口

```text
PATCH /internal/orders/{orderId}/status
```

请求体：

```json
{
  "targetStatus": "AUDIT_PASSED",
  "operatorType": "AUDIT",
  "source": "workflow-service-review-approve"
}
```

第一版只允许以下状态推进：

- `CREATED -> AUDIT_PASSED`
- `CREATED -> AUDIT_FAILED`

其它状态推进后续交给订单状态机封装。

## 6. 落地顺序

1. 新增 `workflow-service` Maven 模块和启动类。
2. 复制并收敛当前 `WorkflowTaskSnapshot`、审核命令、审核结果模型到 `workflow-service`。
3. 在 `workflow-service` 增加 `workflow_task` 查询和更新仓储。
4. 在 `order-service` 增加内部订单状态推进接口，第一版只支持审核通过/拒绝两个目标状态。
5. 在 `workflow-service` 审核通过/拒绝时调用 `order-service` 内部接口，再更新 `workflow_task`。
6. 将 `order-service` 当前审核接口标记为过渡废弃，或在确认后删除。
7. 让 `workflow-service` 独立消费 `ORDER_CREATED`：
   - 读取事件 payload。
   - 查询 `order_validation_record`。
   - 仅在校验结果为 `PASSED` 时创建 `ORDER_REVIEW/PENDING`。
   - 使用独立消费组避免和 `message-service` 幂等记录混淆。
8. 移除 `message-service` 直接创建 `workflow_task` 的逻辑。
9. 补单元测试：
   - `workflow-service` 创建任务幂等。
   - 查询待审核任务。
   - 审核通过。
   - 审核拒绝。
   - `order-service` 内部状态推进只允许合法审核状态。
10. 做真实端到端联调：
    - `gateway` 推单。
    - `message-service` 写校验结果。
    - `workflow-service` 创建待审核任务。
    - `workflow-service` 审核通过。
    - 核对 `order_main`、`workflow_task`、`order_status_log`。
11. 更新项目记录、联调记录和上下文管理。

## 7. 风险与处理

| 风险 | 影响 | 处理方式 |
| --- | --- | --- |
| 两个服务同时创建 `workflow_task` | 重复任务或唯一键冲突 | 先保留唯一键 `(source_event_id, task_type)`，迁移完成后移除 `message-service` 创建逻辑 |
| `workflow-service` 消费早于校验记录落库 | 查不到 `PASSED`，任务不创建 | 第一版可以在未查到校验记录时抛异常让 RocketMQ 重试，或延迟重试 |
| 审核任务更新和订单状态更新跨服务事务不一致 | 任务已完成但订单未更新，或相反 | 第一版先采用“先推进订单状态，成功后更新任务”；失败时任务保持 `PENDING` 便于重试 |
| 内部接口缺少鉴权 | 被误调用造成状态变更 | 本地第一阶段先限制路径为 `/internal` 并记录文档；后续补内部签名或网关隔离 |
| 旧接口调用方未切换 | 管理端或脚本仍调用 `order-service` | 过渡期保留旧接口或明确废弃窗口，联调脚本同步改为调用 `workflow-service` |
| migration 归属调整过早 | 已执行 Flyway 历史不可随意移动 | 第一版不移动已执行 migration，只在文档标注逻辑归属 |

## 8. 验收标准

- `mvn test -q` 通过。
- `workflow-service` 可以本地启动，健康检查返回 `UP`。
- `gateway` 推单后，`message-service` 能写 `order_validation_record.PASSED`。
- `workflow-service` 能创建 `workflow_task: ORDER_REVIEW/PENDING`。
- `GET /api/admin/workflow/review-tasks` 能查询到待审核任务。
- `PATCH /api/admin/workflow/review-tasks/{taskId}/approve` 能推进：
  - `workflow_task.task_status=APPROVED`
  - `order_main.status=AUDIT_PASSED`
  - `order_status_log` 写入 `CREATED -> AUDIT_PASSED`
- 审核拒绝路径能推进：
  - `workflow_task.task_status=REJECTED`
  - `order_main.status=AUDIT_FAILED`
  - `order_status_log` 写入 `CREATED -> AUDIT_FAILED`
- 重复消费 `ORDER_CREATED` 不重复生成任务。
- 本次变更后真实端到端 HTTP 联调通过。

## 9. 建议结论

建议按“小步迁移，不引入流程引擎”的方式落地：

1. 先创建 `workflow-service` 并迁移审核接口。
2. 再让 `workflow-service` 接管 `ORDER_CREATED` 后的任务创建。
3. 最后移除 `message-service` 和 `order-service` 中的过渡审核职责。

这样能保持第一阶段已跑通链路不被大幅打散，同时把职责边界推进到目标架构。
