# 智能药房SaaS平台

## 目标
将现有智慧药房相关系统重构为多租户SaaS平台，覆盖机构接入、处方履约、PDA作业、煎煮MES对接、物流回调、消息补偿和监控告警。

## 技术栈
- Java 21
- Spring Boot 3.x
- Spring Cloud Gateway
- PostgreSQL
- Redis
- RocketMQ
- Vue3 + TypeScript
- Flyway
- OpenTelemetry / Prometheus / Grafana
- JMeter / k6

## 模块规划
- gateway
- auth-institution
- order-service
- prescription-service
- workflow-service
- decoction-service
- logistics-service
- callback-service
- integration-service
- device-service
- portal-service
- message-service
- ops-service
- report-service
- admin-web

## 设备模拟
当前无真实 PDA 和煎煮设备，第一阶段通过模拟器完成：
- PDA 扫码/绑定/煎煮状态上报模拟
- MES 接口模拟
- RocketMQ 事件模拟
- 回调重试模拟

## 第一版交付物
- 架构总蓝图
- 总体架构落地设计
- 重构方案文档
- 模块划分说明
- 表结构设计
- 事件清单
- 状态机设计
- 压测方案
- 本地开发环境说明
- 部署运维说明

## 文档入口

- 新对话交接：`docs/00_项目总览/上下文管理.md`
- 旧系统分析：`docs/01_旧系统分析`
- 重构规格：`docs/02_重构规格`
- 架构设计：`docs/03_架构设计`
- 开发规范：`docs/04_开发规范`
- 测试压测：`docs/05_测试压测`
- 部署运维：`docs/06_部署运维`
- 4C8G 单机试点部署参考：`docs/06_部署运维/云服务器环境.md`
- 项目记录：`docs/99_项目记录`

## 当前开发口径

- 先看 `docs/03_架构设计/架构总蓝图.md`，再看 `docs/03_架构设计/总体架构落地设计.md`，最后进入功能规格和模块划分。
- 阶段划分只表示交付顺序，不拆出另一套阶段架构；阶段一目标不是只做演示 Demo，而是先完整替代旧系统核心生产闭环。
- 外部机构、社康、医院查单、地址补录、物流回调等协议优先兼容旧系统。
- 内部代码按多租户 SaaS、领域服务、状态机、Outbox、RocketMQ 可靠消息重构。
- AI 能力已经独立规划，等原功能重构完成后再扩展。
