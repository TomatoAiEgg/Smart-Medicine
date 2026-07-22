# HIS接入批量测试与回调验收设计方案

## 1. 背景

本项目是医院 HIS、线下扫码系统等外部系统的下游。订单不是后台手工创建，而是由外部系统调用机构接入接口推送处方和订单后进入内部履约链路。

现有系统已经具备以下基础：

- gateway 暴露 `/api/institution/createOrder`，负责机构应用、IP 白名单、签名校验和转发。
- order-service 落库订单、处方、处方明细、状态日志和 Outbox。
- workflow-service 处理审核、调剂、复核任务。
- decoction-service 提供 PDA/MES 模拟煎煮接口。
- logistics-service 处理打包、发货、签收，并创建回调记录。
- callback-service 通过 `dispatch-due` 派发待回调记录到机构配置的 `callback_url`。
- admin-web 和 ops-service 已经能查看业务状态、失败消息、死信和回调异常。

因此测试体系不能只依赖前端页面点击，必须补齐外部系统模拟、批量推单、回调接收、链路统计和故障验收能力。

## 2. 当前重构阶段判断

项目主体重构已经进入测试验收阶段，但不能理解为“全部完成”。

已经基本完成的部分：

- 核心服务拆分和订单主链路。
- Outbox、RocketMQ 消费、死信补偿的基础能力。
- 前端管理台的主要业务页面。
- Docker 镜像化和云服务器单机部署。
- SkyWalking、Prometheus、Grafana 等观测基础。

还需要继续补齐的部分：

- HIS 接入自动化测试和 Apifox 单链路验收。
- 批量推单、幂等、异常签名、重复请求、并发请求验收。
- 回调接收、回调失败、回调重试、回调死信验收。
- 监控闭环验收：链路追踪、慢 SQL、MQ 积压、服务资源、告警。
- 压测和故障演练：RocketMQ 停机、callback-service 停机、workflow-service 停机后恢复。
- K8s 部署和 K8s 监控不是当前阶段完成项，属于后续云原生部署阶段。

结论：当前应进入“系统测试、压测、回调验收、监控闭环”阶段。

## 3. 测试工具定位

### 3.1 Apifox

Apifox 用于手工调试和演示一条订单全链路。

适合覆盖：

- HIS 创建订单接口的请求示例。
- 签名前置脚本。
- 环境变量管理。
- 单条订单链路演示。
- 错误签名、缺字段、重复外部订单号等单接口用例。

Apifox 不作为批量压测和回调统计的主工具，因为它不适合长时间接收服务端回调，也不适合沉淀稳定的批量统计报告。

### 3.2 自动化脚本

自动化脚本用于批量推单、推进业务链路、接收项目回调和输出验收统计。

适合覆盖：

- 批量创建订单。
- 并发推单。
- 幂等验证。
- 自动审核、调剂、复核、煎煮、打包、发货。
- 触发 callback-service 派发回调。
- 启动 HTTP 回调接收器并展示回调内容。
- 输出成功数、失败数、P95/P99、回调收到数、回调缺失数、死信数、积压数。

## 4. 推荐文件规划

```text
test/integration/create-order-smoke.ps1
test/integration/his-batch-callback.py
test/integration/apifox-signature-pre-request.js
test/reports/
```

说明：

- `create-order-smoke.ps1` 保留为最小冒烟脚本。
- `his-batch-callback.py` 负责批量推单、链路推进、回调接收和统计。
- `apifox-signature-pre-request.js` 负责 Apifox 签名前置脚本。
- `test/reports/` 只保存必要的测试报告，不提交本地临时日志和敏感信息。

## 5. 批量测试脚本设计

### 5.1 运行位置

推荐先把脚本跑在云服务器宿主机上。

原因：

- callback-service 容器需要能访问脚本监听的 HTTP 地址。
- 宿主机脚本可以使用 Docker 网络网关地址接收容器回调。
- 不需要把本机电脑暴露到公网，也不依赖内网穿透。

推荐回调地址：

```text
http://172.30.0.1:19081/callback
```

如果 Docker 网络不是 `172.30.0.0/24`，脚本应支持通过参数覆盖回调地址。

### 5.2 参数设计

```text
--base-url http://127.0.0.1
--gateway-url http://127.0.0.1/api/institution
--callback-url http://172.30.0.1:19081/callback
--callback-listen 0.0.0.0:19081
--app-key demo-app
--app-secret 从环境变量读取
--count 10
--concurrency 5
--mode full-chain
--dispatch-callbacks true
--restore-callback-url true
--report test/reports/his-batch-YYYYMMDD-HHmmss.json
```

注意：`appSecret` 必须从环境变量读取，不写入命令行历史、报告、日志和 Git。

### 5.3 运行模式

| 模式 | 目标 |
| --- | --- |
| `create-only` | 只批量创建订单，验证 HIS 推单吞吐、签名、幂等和落库 |
| `full-chain` | 创建订单后推进审核、调剂、复核、煎煮、物流、回调 |
| `callback-only` | 不新建订单，只派发已有待回调记录并接收回调 |
| `idempotency` | 同一个外部订单号重复提交，验证不会生成重复订单 |
| `invalid-signature` | 错误签名、过期时间戳、非法 appKey 等安全用例 |
| `load` | 按指定并发和持续时间推单，输出压测指标 |

## 6. 全链路流程

### 6.1 初始化

脚本启动时执行：

```text
1. 启动 HTTP 回调监听器
2. 记录当前 institution_app.callback_url
3. 将测试机构 callback_url 更新为脚本监听地址
4. 检查 gateway、order-service、workflow-service、decoction-service、logistics-service、callback-service 健康状态
5. 打印本次测试批次号 batchId
```

`callback_url` 更新只允许在开发测试环境执行，生产环境禁止脚本直接改数据库。

### 6.2 创建订单

对每个订单生成唯一外部订单号：

```text
externalOrderNo = HIS-BATCH-{batchId}-{index}
externalPrescriptionNo = RX-{batchId}-{index}
```

签名规则沿用现有服务：

```text
bodyHash = sha256(rawBody)
source = appKey + "\n" + timestamp + "\n" + bodyHash
signature = hmacSha256(appSecret, source)
```

请求入口：

```text
POST /api/institution/createOrder
```

验收点：

- HTTP 2xx。
- 返回 `code=SUCCESS`。
- `data.duplicate=false`。
- 返回 `orderId`、`orderNo`、`externalOrderNo`。

### 6.3 推进 workflow

脚本轮询并处理任务：

```text
GET /workflow-api/api/admin/workflow/review-tasks
PATCH /workflow-api/api/admin/workflow/review-tasks/{taskId}/approve

GET /workflow-api/api/admin/workflow/dispense-tasks
PATCH /workflow-api/api/admin/workflow/dispense-tasks/{taskId}/complete

GET /workflow-api/api/admin/workflow/recheck-tasks
PATCH /workflow-api/api/admin/workflow/recheck-tasks/{taskId}/complete
```

验收点：

- 每个订单最终完成审核、调剂、复核。
- 重复处理不会造成重复状态推进。
- 失败任务能打印任务 ID、订单号、失败原因。

### 6.4 推进煎煮

脚本通过 PDA 或 MES 模拟接口完成煎煮。

推荐先走 PDA 接口：

```text
GET /decoction-api/simulator/pda/prescriptions/can-operate
GET /decoction-api/simulator/pda/decoction/devices
POST /decoction-api/simulator/pda/bind-prescription
POST /decoction-api/simulator/pda/decoction/start
POST /decoction-api/simulator/pda/decoction/finish
```

验收点：

- 每个复核后的处方都能绑定煎煮任务。
- 煎煮任务状态能从待处理推进到完成。
- 订单状态能推进到可物流处理状态。

### 6.5 推进物流

脚本处理待发货订单：

```text
GET /logistics-api/api/admin/logistics/orders/ready
POST /logistics-api/api/admin/logistics/shipments/pack
PATCH /logistics-api/api/admin/logistics/shipments/{shipmentId}/ship
PATCH /logistics-api/api/admin/logistics/shipments/{shipmentId}/sign
```

验收点：

- 打包后生成 `ORDER_PACKED` 回调记录。
- 发货后生成 `SHIPPED` 相关回调记录。
- 签收后生成 `SIGNED` 相关回调记录。
- 物流状态和订单状态一致。

### 6.6 派发并接收回调

callback-service 默认可以关闭定时派发，因此脚本应主动触发：

```text
POST /callback-api/api/admin/callback-records/dispatch-due?limit=200
```

脚本本地监听：

```text
POST /callback
```

收到回调后记录：

```text
callbackId
orderId
orderNo
callbackType
businessId
businessStatus
source
createdAt
receiveAt
rawBody
```

验收点：

- 回调 HTTP 状态返回 200。
- 脚本控制台实时打印回调摘要。
- 回调数量和 callback_record 成功数量能对齐。
- 未收到回调的订单能列出订单号、回调类型和 callback_record 状态。

## 7. 控制台输出设计

脚本运行中应持续输出结构化进度：

```text
[batch] id=20260722-001 mode=full-chain count=10 concurrency=5
[callback] listening=http://0.0.0.0:19081/callback public=http://172.30.0.1:19081/callback
[create] ok externalOrderNo=HIS-BATCH-001-0001 orderNo=ZHYF...
[workflow] approved orderNo=ZHYF... taskId=...
[workflow] dispensed orderNo=ZHYF... taskId=...
[workflow] rechecked orderNo=ZHYF... taskId=...
[decoction] finished orderNo=ZHYF... taskNo=...
[logistics] shipped orderNo=ZHYF... shipmentId=...
[callback] received orderNo=ZHYF... type=ORDER_PACKED status=PACKED
[summary] created=10 failed=0 callbacksReceived=30 callbacksMissing=0 p95CreateMs=...
```

失败时必须输出失败分类：

```text
CREATE_FAILED
WORKFLOW_TIMEOUT
DECOCTION_TIMEOUT
LOGISTICS_FAILED
CALLBACK_DISPATCH_FAILED
CALLBACK_NOT_RECEIVED
DEAD_LETTER_CREATED
```

## 8. 报告设计

建议输出 JSON 报告：

```json
{
  "batchId": "20260722-001",
  "mode": "full-chain",
  "count": 10,
  "concurrency": 5,
  "startedAt": "2026-07-22T10:00:00Z",
  "finishedAt": "2026-07-22T10:03:20Z",
  "summary": {
    "created": 10,
    "createFailed": 0,
    "workflowCompleted": 10,
    "decoctionCompleted": 10,
    "logisticsCompleted": 10,
    "callbacksExpected": 30,
    "callbacksReceived": 30,
    "callbacksMissing": 0
  },
  "metrics": {
    "createP95Ms": 120,
    "fullChainP95Ms": 18000
  },
  "orders": []
}
```

报告不得包含：

- 真实患者姓名、手机号、身份证号。
- appSecret、数据库密码、服务器密码。
- 未脱敏的真实医院生产数据。

## 9. Apifox 设计

Apifox 环境变量：

```text
baseUrl
appKey
appSecret
timestamp
signature
rawBody
```

推荐接口集合：

```text
HIS推单/创建订单
HIS推单/重复创建同一订单
HIS推单/错误签名
后台审核/查询待审核任务
后台审核/审核通过
调剂复核/完成调剂
调剂复核/完成复核
煎煮模拟/PDA绑定处方
煎煮模拟/PDA开始煎煮
煎煮模拟/PDA完成煎煮
物流/打包
物流/发货
物流/签收
回调/派发待回调
回调/查询回调记录
```

Apifox 前置脚本只负责生成签名，不负责保存真实密钥到仓库。

## 10. 验收标准

### 10.1 单链路验收

| 检查项 | 通过标准 |
| --- | --- |
| 创建订单 | 返回成功并落库 |
| 幂等 | 同一 `externalOrderNo` 重复提交返回同一订单 |
| 审核 | 订单进入审核通过 |
| 调剂 | 调剂任务完成 |
| 复核 | 复核任务完成 |
| 煎煮 | 煎煮任务完成 |
| 物流 | 打包、发货、签收完成 |
| 回调 | 脚本收到项目回调 |
| 查询 | 订单进度能展示完整链路 |

### 10.2 批量验收

| 档位 | 目标 |
| --- | --- |
| 1 单 | 验证脚本和接口连通 |
| 10 单 | 验证完整链路稳定性 |
| 50 单 | 验证小批量并发和回调接收 |
| 100 单 | 验证 MQ、数据库连接池、服务内存压力 |
| 200 单 | 用于找瓶颈，不作为当前稳定承诺 |

### 10.3 异常验收

必须覆盖：

- 错误签名。
- 过期时间戳。
- 非法 appKey。
- 重复外部订单号。
- callback_url 不可达。
- callback-service 停止后恢复。
- workflow-service 停止后恢复。
- RocketMQ 短暂停机后恢复。

## 11. 风险和处理

| 风险 | 处理 |
| --- | --- |
| 回调地址容器不可达 | 脚本跑在云服务器宿主机，callback_url 使用 Docker 网关地址 |
| callback_url 改错 | 脚本启动时记录原值，结束时恢复 |
| 回调派发默认关闭 | 脚本主动调用 `dispatch-due` |
| 批量推单影响已有测试数据 | 使用独立 `externalOrderNo` 前缀和 batchId |
| 日志泄露密钥 | appSecret 只走环境变量，不输出到报告 |
| 真实患者数据泄露 | 使用模拟患者和模拟处方 |
| 链路推进超时 | 每个阶段设置超时时间并输出失败分类 |

## 12. 实施顺序

第一步：补 Apifox 签名前置脚本和单条创建订单集合。

第二步：实现 `his-batch-callback.py` 的 `create-only` 模式。

第三步：增加 HTTP 回调监听器和 `callback-only` 模式。

第四步：实现 `full-chain`，串起审核、调剂、复核、煎煮、物流、回调。

第五步：增加 `idempotency`、`invalid-signature` 和 `load` 模式。

第六步：把脚本结果和 SkyWalking、Grafana、ops-service 数据对齐，形成最终验收报告。

## 13. 简历表达口径

可以这样描述：

```text
设计并落地 HIS 下游接入自动化验收体系，通过 Apifox 完成单订单接口调试和签名校验，通过批量脚本模拟医院系统推单、自动推进审核调剂复核煎煮物流链路，并启动 HTTP 回调接收器验证 callback-service 的回调派发、失败重试和补偿能力。压测过程中结合 SkyWalking、Prometheus、Grafana 和业务运维台定位接口耗时、MQ 积压、死信和 SQL 瓶颈。
```
