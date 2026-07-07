# 模块清单

本文档记录新系统模块边界。模块划分以 `docs/03_架构设计/架构总蓝图.md` 和 `docs/03_架构设计/总体架构落地设计.md` 为准，先定义核心模块，再定义扩展模块。

## 1. 核心模块

| 模块 | 类型 | 核心职责 | 旧系统来源 |
| --- | --- | --- | --- |
| `zhyf-common` | Java 公共库 | 通用枚举、异常、签名、响应模型、状态机基础类型、审计字段 | `zhyf-common` 中可保留的公共能力 |
| `gateway` | Spring Cloud Gateway | 统一入口、路由、机构签名校验、IP 白名单、限流、访问日志 | `zhyf-gateway` |
| `auth-institution` | Spring Boot 服务 | 租户、机构、应用密钥、回调配置、白名单、用户权限 | `zhyf_institutions`、`zhyf_api_permission`、`zhyf_white_ip` |
| `order-service` | Spring Boot 服务 | 订单创建、订单状态机、后台订单查询、订单状态日志、人工修正入口 | `zhyf_order`、后台订单 Controller |
| `message-service` | Spring Boot 服务 | Outbox 扫描、RocketMQ 生产、消费幂等、消息补偿、短信事件 | `zhyf-async` ActiveMQ 消费者 |
| `workflow-service` | Spring Boot 服务 | 后台审核、调剂、复核、订单锁、人工流程控制、状态推进 | 后台审核、复核、流程控制能力 |
| `ops-service` | Spring Boot 服务 | 操作日志、异常日志、审计、链路追踪、监控聚合、联调记录 | 后台日志、运维散落能力 |

## 2. 扩展模块

| 模块 | 技术 | 核心职责 |
| --- | --- | --- |
| `prescription-service` | Spring Boot 服务 | 处方、处方明细、处方修改、审方摘要、处方状态机 |
| `decoction-service` | Spring Boot 服务 | PDA 登录、扫码绑定、水桶/设备、加水、煎煮、MES 适配、设备模拟 |
| `logistics-service` | Spring Boot 服务 | 打包出库、面单、物流轨迹、签收、自提、顺丰/EMS 适配；当前已落地最小物流单、轨迹和回调入口 |
| `callback-service` | Spring Boot 服务 | 机构状态回调、社康回写、地址补录回推、失败重试、死信、人工重放；当前已落地回调记录和人工补偿骨架 |
| `integration-service` | Spring Boot 服务 | 社康、医院、地址补录等外围适配 |
| `device-service` | Spring Boot 服务 | 设备档案、设备占用、关系管理 |
| `portal-service` | Spring Boot 服务 | 医院查单、地址补录页面 |
| `report-service` | Spring Boot 服务 | 报表、统计、导出 |

## 3. 前端模块

| 模块 | 技术 | 核心页面 |
| --- | --- | --- |
| `admin-web` | Vue3 + TypeScript + Vite | 订单中心、处方中心、审核/调剂/复核、PDA/MES 作业监控、物流中心、回调补偿、机构配置、设备管理、系统监控；当前已包含订单查询、审核/复核任务、煎煮模拟、物流回调和运维排错入口 |
| `pda-simulator-web` | Vue3 或轻量页面 | PDA 登录、扫码绑定、加水、煎煮状态上报、重复/并发/超时模拟 |
| `integration-simulator` | 后端或轻量页面 | 机构推单、医院查单、社康拉单、物流轨迹、地址补录、回调失败模拟 |

## 4. 基础设施模块

| 目录 | 内容 |
| --- | --- |
| `infra/docker-compose` | 本地或云端开发用 PostgreSQL、Redis、RocketMQ、监控组件编排 |
| `infra/flyway` | PostgreSQL 表结构、索引、分区、初始化字典数据 |
| `infra/rocketmq` | Topic、ConsumerGroup、Dashboard、Broker 配置 |
| `test/performance` | JMeter / k6 压测脚本与报告模板 |
| `test/integration` | 机构、PDA、MES、物流、回调端到端联调用例 |

## 5. 调用方向

```text
外部机构 / 后台 / PDA / 模拟器
  -> gateway
  -> 领域服务
  -> PostgreSQL + event_outbox
  -> message-service
  -> RocketMQ
  -> callback-service / logistics-service / ops-service
```

规则：

- 外部请求统一进 `gateway`，领域服务不直接暴露公网入口。
- 核心业务写库和事件写入使用同一事务。
- 跨领域同步调用只保留必要查询，状态推进优先通过明确的应用服务和事件完成。
- 任何旧系统差异通过 adapter 或配置隔离，不写死在订单主流程里。
