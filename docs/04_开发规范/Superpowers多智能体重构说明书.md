# Superpowers 多智能体重构说明书

## 1. 使用定位

本项目可以使用 Superpowers 多智能体加速重构，但不建议让多个智能体同时自由修改整个仓库。

推荐模式：

```text
主控窗口统一决策
 -> 多个 explorer 并行只读审查
 -> 主控统一公共契约
 -> 少量 worker 按模块并行实现
 -> 主控验收、联调、解决冲突
```

核心原则：

- 多智能体用来加速审查、拆解、局部实现和验证。
- 主控窗口负责统一数据库、API、状态机、MQ 事件和部署口径。
- 公共契约不能让多个 worker 各自发挥。
- 每个 worker 必须有清晰的文件和模块边界。
- 不把密码、token、密钥、真实患者信息写入代码、文档、日志或提交。

## 2. 适合使用多智能体的任务

适合：

- 并行审查老项目后端、前端、数据库和部署文档。
- 分别梳理订单、处方、审方、调剂、复核、煎煮、物流、回调模块。
- 分别检查数据库设计、Outbox、MQ 事件、消费幂等、补偿和容灾。
- 分别实现边界清晰的后端服务、前端页面、压测脚本、监控配置。
- 并行做代码 review、文档审查、接口契约检查、压测结果分析。

不适合：

- 多个 worker 同时修改同一批数据库 migration。
- 多个 worker 同时改同一个后端服务。
- 多个 worker 同时改 API 字段、订单状态机、MQ 事件定义。
- 没有主控验收就直接合并多个 worker 的结果。
- 让 worker 直接连接生产或真实业务环境操作。

## 3. 角色分工

### 3.1 主控窗口

职责：

- 读取总体方案和当前工作区状态。
- 拆分任务。
- 派发 explorer 和 worker。
- 统一公共契约。
- 检查每个 worker 是否越界。
- 运行最终验证。
- 汇总进度和风险。

主控窗口不应该频繁大范围改代码。主控更适合做协调、评审、合并和验收。

### 3.2 explorer

职责：

- 只读审查。
- 回答明确问题。
- 输出现状、缺口、风险和建议。
- 不修改文件。

适用场景：

```text
检查后端核心链路缺口。
检查老前端和新前端功能差异。
检查数据库、MQ、Outbox、消费日志设计。
检查监控、压测、容灾方案是否完整。
```

### 3.3 worker

职责：

- 在明确边界内改代码或文档。
- 只修改自己负责的文件或模块。
- 不回滚他人的改动。
- 完成后说明改动文件、验证命令和风险。

适用场景：

```text
只改 order-service。
只改 admin-web 的订单中心页面。
只改 Flyway migration。
只改 k6 压测脚本。
只改 Grafana/Prometheus 配置。
```

## 4. 推荐执行流程

### 4.1 第一轮：只读审查

先派 4 个 explorer，不改代码。

| explorer | 审查范围 | 输出 |
| --- | --- | --- |
| 后端 explorer | 后端服务、订单链路、事务、MQ | 后端缺口和实现顺序 |
| 前端 explorer | 新旧前端、页面工作流、接口需求 | 页面差异和前端优先级 |
| 数据/MQ explorer | 表结构、索引、Outbox、消费日志、RocketMQ | 数据和消息契约清单 |
| 监控 explorer | SkyWalking、Prometheus、Grafana、k6、容灾 | 监控压测落地清单 |

主控汇总后，必须先统一：

- 数据库核心表。
- API 契约。
- 订单状态机。
- MQ Topic、事件和消费组。
- Outbox 和消费日志字段。
- 前端页面优先级。
- 验收命令。

### 4.2 第二轮：公共契约落地

公共契约建议由主控或单个数据 worker 处理，避免多人冲突。

优先级：

```text
数据库 migration
 -> API DTO / OpenAPI / 接口文档
 -> MQ 事件结构
 -> 状态机枚举
 -> 前后端联调说明
```

这一步完成前，不建议让前端和后端大规模并行实现。

### 4.3 第三轮：模块并行实现

每轮最多 2 到 3 个 worker 并行。

推荐第一批：

| worker | 写入范围 | 目标 |
| --- | --- | --- |
| 后端 worker | 后端核心服务 | 订单创建、Outbox、审方/调剂/复核 |
| 前端 worker | admin-web | 工作台、订单中心、任务页 |

推荐第二批：

| worker | 写入范围 | 目标 |
| --- | --- | --- |
| 数据/MQ worker | migration、中间件配置 | Outbox、消费日志、死信、Topic |
| 监控 worker | monitoring、tests/performance | 指标、k6、演练脚本 |

每个 worker 返回后，主控必须检查：

- 是否只改了授权范围。
- 是否破坏公共契约。
- 是否有验证命令。
- 是否影响其他 worker。
- 是否需要补文档或通知其他窗口。

阶段验收清单见：

```text
docs/05_测试压测/阶段验收清单.md
```

### 4.4 第四轮：联调和压测

核心链路跑通后，再安排监控和压测。

顺序：

```text
本地构建
 -> 服务启动
 -> 外部下单
 -> 订单列表
 -> 审方
 -> 调剂
 -> 复核
 -> Outbox 发布
 -> MQ 消费
 -> 回调记录
 -> SkyWalking 链路
 -> Grafana 指标
 -> k6 压测
 -> 故障演练
```

## 5. 文件边界建议

后端 worker：

```text
backend/
服务模块目录
DTO / Controller / Service / Mapper / Entity
```

前端 worker：

```text
frontend/admin-web/
src/views
src/components
src/api
src/stores
src/router
```

数据/MQ worker：

```text
database/
db/migration/
docker-compose.yml
rocketmq/
docs/数据设计/
docs/中间件说明/
```

监控 worker：

```text
monitoring/
prometheus/
grafana/
skywalking/
tests/performance/
docs/监控说明/
```

如果实际仓库目录不同，以当前仓库真实结构为准。worker 开始前必须先列出自己准备修改的路径。

## 6. 公共契约管理

以下内容必须由主控统一确认：

| 契约 | 管理方式 |
| --- | --- |
| 数据库字段 | Flyway migration 和数据设计文档 |
| API 字段 | DTO、接口文档、前后端联调文档 |
| 订单状态机 | 后端枚举、数据库状态、前端状态文案 |
| MQ 事件 | RocketMQ 事件设计文档和事件类 |
| 错误码 | 后端统一异常和前端提示 |
| 权限点 | 后端权限校验和前端菜单 |
| 监控指标 | actuator、Prometheus、Grafana 看板 |

变更公共契约时，主控需要通知相关 worker：

```text
字段变更 -> 通知后端和前端。
表结构变更 -> 通知后端、数据和监控。
MQ 事件变更 -> 通知后端、数据/MQ、监控。
状态机变更 -> 通知后端、前端和压测。
```

## 7. 提示词模板

### 7.1 第一轮只读审查

```text
按 Superpowers 多智能体方式执行第一轮，只读审查，不改代码。

请派 4 个 explorer：
1. 后端 explorer：审查后端核心链路、事务、Outbox、MQ、审方/调剂/复核缺口。
2. 前端 explorer：审查新旧前端差异、核心页面、工作流和接口需求。
3. 数据/MQ explorer：审查数据库表、索引、Outbox、消费日志、RocketMQ、补偿和容灾。
4. 监控 explorer：审查 SkyWalking、Prometheus、Grafana、k6、压测和故障演练。

所有 explorer 只读，不修改文件。
主控等待结果后统一汇总实施顺序、公共契约和风险。
```

### 7.2 后端 worker

```text
你是后端 worker，只负责后端核心链路。

写入范围：
只允许修改后端核心服务相关文件。不要改前端、部署、监控和无关文档。

目标：
实现或补齐订单创建、处方落库、状态日志、event_outbox、RocketMQ 发布、审方/调剂/复核任务、message_consume_log 幂等。

要求：
- 使用 MyBatis-Plus + MyBatis XML，不手写原始 JDBC。
- 单服务内用 Spring @Transactional。
- 跨服务用 Outbox + MQ + 幂等 + 重试 + 补偿。
- 状态推进使用条件更新或乐观锁。
- 外部 HTTP 不放入数据库事务。
- 不写入任何密码、token、密钥、真实患者信息。
- 不回滚他人的改动。

完成后报告：
改动文件、验证命令、验证结果、风险和需要主控确认的契约。
```

### 7.3 前端 worker

```text
你是前端 worker，只负责 admin-web 前端。

写入范围：
只允许修改前端项目目录。不要改后端、数据库 migration、部署和中间件配置。

目标：
实现工作台、订单中心、审核任务、调剂任务、复核任务、物流/回调/运维入口。

要求：
- 管理后台风格，清晰、密集、可操作。
- 保留老系统核心工作流。
- 不自己猜后端字段；接口不明确时列出需要主控确认的契约。
- 不把链路追踪 ID 当业务字段。
- 前端展示业务状态文案、订单号、处方号、失败原因和补偿入口。
- 不写入任何密码、token、密钥、真实患者信息。
- 不回滚他人的改动。

完成后报告：
改动文件、构建/类型检查结果、页面验证结果、需要后端提供的接口。
```

### 7.4 数据/MQ worker

```text
你是数据/MQ worker，只负责数据库、Flyway、Redis、RocketMQ 和中间件配置。

写入范围：
只允许修改 migration、数据设计、中间件配置、Docker/RocketMQ 相关文件。不要改前端页面和后端业务大逻辑。

目标：
落地核心表、索引、event_outbox、message_consume_log、dead_letter_record、callback_record、RocketMQ Topic/ConsumerGroup、Redis key 规范。

要求：
- PostgreSQL 是业务事实源。
- Redis 不能作为唯一事实源。
- Outbox 支持 NEW、PUBLISHING、PUBLISHED、PUBLISH_FAILED、WAIT_CONSUME_TIMEOUT、DEAD。
- 消费日志使用 consumer_group + event_id 幂等。
- migration 要可追踪，错误变更用补偿 migration。
- 不写入任何密码、token、密钥、真实患者信息。
- 不回滚他人的改动。

完成后报告：
改动文件、SQL/Flyway 校验结果、字段变更、需要通知后端/前端/监控的契约。
```

### 7.5 监控压测 worker

```text
你是监控压测 worker，只负责可观测性、压测和故障演练。

写入范围：
只允许修改 monitoring、tests/performance、监控说明文档和必要的指标配置。不要改前端主页面、后端核心业务和数据库核心结构。

目标：
补齐 SkyWalking、Prometheus、Grafana、SQL 慢查询、RocketMQ 积压、Outbox、消费失败、死信、k6 压测和故障演练。

要求：
- 不包装成生产百万 QPS。
- 压测结论只写测试环境模拟结果。
- 覆盖 20、50、100、200 并发档位。
- 覆盖重复提交、重复消费、并发审方、消费者停机、RocketMQ Broker 停机、Outbox 补发。
- 不写入任何密码、token、密钥、真实患者信息。
- 不回滚他人的改动。

完成后报告：
改动文件、脚本校验结果、看板/指标清单、演练步骤和风险。
```

## 8. 主控验收清单

每个 worker 完成后，主控执行：

```text
git status --short
git diff --check
```

按模块选择验证：

```text
mvn test
mvn clean package
npm run type-check
npm run build
k6 run tests/performance/scripts/order-full-chain.js
docker compose config
```

还要人工检查：

- 是否改了非授权文件。
- 是否引入敏感信息。
- 是否破坏数据库/API/MQ/状态机契约。
- 是否缺少幂等、重试、补偿和审计。
- 是否遗漏前后端联调说明。
- 是否有可复现验证结果。

## 9. 冲突处理

发现冲突时，不直接互相覆盖。

处理顺序：

```text
确认冲突文件
 -> 判断属于哪个契约
 -> 由主控选择统一口径
 -> 通知相关 worker 调整
 -> 重新验证
```

常见冲突：

| 冲突 | 处理 |
| --- | --- |
| 前后端字段不一致 | 以 API DTO / 接口文档为准 |
| migration 和实体不一致 | 以 migration 为事实源，后端调整 |
| MQ 事件名不一致 | 以 RocketMQ 事件设计文档为准 |
| 状态码不一致 | 以状态机设计为准 |
| 前端状态文案不一致 | 以功能方案和后端状态枚举映射为准 |

## 10. 分支和工作区建议

如果只在一个工作区内使用多智能体：

- 同一时间最多 2 到 3 个 worker。
- 每个 worker 写入范围必须不重叠。
- 主控频繁检查 `git status --short`。

如果要更安全：

```text
backend-core 分支或 worktree
admin-web 分支或 worktree
infra-data 分支或 worktree
observability 分支或 worktree
```

每个分支完成后由主控合并，避免直接在同一个工作区互相覆盖。

## 11. 推荐节奏

第一天：

- 多 explorer 只读审查。
- 主控统一公共契约。
- 落地数据库、状态机和 MQ 事件。

第二天：

- 后端 worker 做订单核心链路。
- 前端 worker 做工作台和订单中心。
- 主控做接口契约校验。

第三天：

- 后端补审方、调剂、复核、回调。
- 前端补任务页和运维入口。
- 数据/MQ worker 补 Outbox、消费日志、死信。

第四天：

- 监控压测 worker 接 SkyWalking、Prometheus、Grafana、k6。
- 跑 20/50/100 并发。
- 做 MQ 积压、消费者停机、Broker 停机演练。

第五天：

- 主控做全链路验收。
- 整理压测报告、故障演练记录、简历口径和剩余风险。

## 12. 当前项目建议

本项目更适合：

```text
主控窗口 + Superpowers 多智能体
```

不建议：

```text
手动开很多窗口同时自由重构
```

原因：

- 智能药房链路公共契约多。
- 数据库、API、MQ、状态机容易互相影响。
- 多智能体能加速审查和局部实现，但必须由主控统一验收。

第一轮建议从只读审查开始，先确认真实缺口，再安排 worker 分模块实现。
