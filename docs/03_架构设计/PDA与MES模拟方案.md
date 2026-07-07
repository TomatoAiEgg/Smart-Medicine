# PDA 与 MES 模拟方案

## 1. 背景

第一版重构需要验证 PDA、煎煮机、水桶、打码机、MES 状态回写等链路。

当前开发阶段不应依赖真实硬件，因此需要模拟器支撑：

- PDA 模拟器。
- MES/煎药机模拟器。
- 物流模拟回调。
- 外部机构回调接收模拟器。

## 2. 目标

模拟器用于开发、联调、压测和验收。

必须覆盖：

- 正常作业流程。
- 重复提交。
- 并发绑定。
- 非法状态跳转。
- 网络超时后重试。
- 乱序上报。
- 设备占用和释放。

## 3. PDA 模拟器

### 3.1 功能

- PDA 登录。
- 查询可操作处方。
- 查询煎煮设备。
- 扫码绑定处方和设备。
- 标签打印初始化。
- 标签打印记录。
- 查询处方详情。
- 上报煎煮开始。
- 上报煎煮完成。
- 上报取消/终止。
- PDA 出库。

### 3.2 兼容旧接口

PDA 模拟器应优先调用新系统的旧协议兼容接口：

| 旧接口 | 模拟动作 |
| --- | --- |
| `/pdaUserLogin` | 登录 |
| `/pdaGetDecoctEquip` | 查询设备 |
| `/pdaBindPrescription` | 绑定处方和煎煮机 |
| `/pdaOpDecoctStatus` | 上报煎煮状态 |
| `/pdaGetCanOpRecipeList` | 查询可操作处方 |
| `/pdaLabelPrintInit` | 标签初始化 |
| `/pdaRecipeQuery` | 处方查询 |
| `/pdaLabelPrint` | 标签打印记录 |
| `/pdaLogisticsOutbound` | PDA 出库 |

### 3.3 模拟器自有接口

用于快速测试：

```text
POST /simulator/pda/login
GET  /simulator/pda/prescriptions/can-operate
GET  /simulator/pda/decoction/devices
POST /simulator/pda/bind-prescription
POST /simulator/pda/decoction/start
POST /simulator/pda/decoction/finish
POST /simulator/pda/decoction/cancel
POST /simulator/pda/decoction/terminate
POST /simulator/pda/outbound
```

### 3.4 请求幂等

模拟器每次操作必须带：

- `operationId`
- `deviceCode`
- `prescriptionNo`
- `operator`
- `timestamp`
- `sign`

新系统使用 `operationId` 防重复。

## 4. MES/煎药机模拟器

### 4.1 功能

- 获取待煎煮处方。
- 按水桶号查询加水信息。
- 上报加水完成。
- 获取处方煎煮信息。
- 上报煎煮开始。
- 上报煎煮完成。
- 上报温度曲线。
- 上报设备作业记录。
- 模拟异常和乱序。

### 4.2 兼容旧设备接口

| 旧接口 | 模拟动作 |
| --- | --- |
| `/getWaterByPailNo` | 按水桶号查询加水信息 |
| `/waterEndNotice` | 加水完成通知 |
| `/getRecipeInfoById` | 按处方号取方 |
| `/recipeBoilInfoCallback` | 煎煮信息回调 |
| `/reviewFinishPushRecipe` | 复核完成推方 |
| `/decoctingStatusNotice` | 煎煮状态通知 |

### 4.3 模拟器自有接口

```text
GET  /simulator/mes/tasks/pending
GET  /simulator/mes/tasks/active
POST /simulator/mes/tasks/{taskNo}/water-finish
POST /simulator/mes/tasks/{taskNo}/start
POST /simulator/mes/tasks/{taskNo}/finish
POST /simulator/mes/tasks/{taskNo}/cancel
POST /simulator/mes/tasks/{taskNo}/terminate
POST /simulator/mes/tasks/{taskNo}/error
POST /simulator/mes/tasks/{taskNo}/temperature
GET  /simulator/mes/tasks/{taskNo}/events
GET  /simulator/mes/tasks/{taskNo}/work-records
```

当前已落地第一版接口：

- `decoction-service` 默认本地端口 `18087`。
- PDA 已支持登录、查询可操作处方、查询设备、绑定处方、上报开始和完成。
- MES 已支持查询待开始任务、查询活动任务、上报开始和完成。
- 成功开始会调用 `order-service` 推进订单到 `DECOCTING`；成功完成会推进到 `DECOCTED`。
- `admin-web` 已提供“煎煮模拟”入口，通过 Vite proxy 调用 `decoction-service`。
- 2026-06-26 已完成真实本地联调，六个临时服务 `19080/19081/19082/19083/19085/19087` 跑通主链路，测试订单 `ZHYF1782473402454`、煎煮任务 `DCT-6C049A30` 最终均到 `DECOCTED`。
- 2026-06-26 已新增 `decoction_task_event` 事件表，支持加水完成、温度、异常记录和任务事件查询；`admin-web` 已提供对应操作入口。
- 2026-06-26 已新增取消/终止状态流转和设备释放规则：`BOUND` 可取消为 `CANCELLED`，订单保持 `RECHECKED` 以便重新绑定；`DECOCTING` 可终止为 `TERMINATED`，订单推进到 `CANCELLED`；成功取消/终止写 `decoction_task_event`。
- 2026-06-27 已新增非法状态/乱序上报拒绝记录：开始、完成、取消、终止、加水完成、温度上报在状态不匹配时仍拒绝业务操作，但写 `decoction_task_event.event_type=REJECTED` 供后台排查。
- 2026-06-27 已新增设备作业明细模型：`decoction_device_work_record` 记录绑定、开始、完成、取消、终止、加水完成、温度、异常和拒绝动作；`admin-web` 已支持按任务查看作业明细。
- 2026-07-07 已新增真实设备 adapter 入口第一版：`DeviceAdapter`、`MesAdapter` 抽象复用同一套煎煮业务服务，旧 PDA/MES 兼容接口可直接映射到绑定、开始、完成、取消、终止、加水完成、温度和推方查询能力。

已落地旧协议兼容接口：

```text
GET|POST /pdaUserLogin
GET|POST /pdaGetDecoctEquip
GET|POST /pdaGetCanOpRecipeList
GET|POST /pdaBindPrescription
GET|POST /pdaOpDecoctStatus
GET|POST /getWaterByPailNo
GET|POST /waterEndNotice
GET|POST /getRecipeInfoById
GET|POST /recipeBoilInfoCallback
GET|POST /reviewFinishPushRecipe
GET|POST /decoctingStatusNotice
```

旧协议入口支持 query/form 参数和 JSON body。第一版兼容字段别名：

| 新内部字段 | 兼容旧字段 |
| --- | --- |
| `operationId` | `operationId`、`opId`、`requestId`、`serialNo`、`logNo`、`traceId` |
| `prescriptionNo` | `prescriptionNo`、`recipeNo`、`recipeId`、`recipelId`、`recipeCode` |
| `deviceCode` | `deviceCode`、`equipCode`、`equipNo`、`equipmentCode`、`machineCode` |
| `pailNo` | `pailNo`、`bucketNo`、`barrelNo`、`pailCode`、`waterBucketNo` |
| `status` | `operStatus`、`opStatus`、`status`、`decoctingStatus`、`boilStatus` |
| `operator` | `operator`、`account`、`userName`、`user`、`opUser`、`operUser` |

### 4.4 设备作业明细口径

`decoction_task_event` 用于保存设备上报和系统控制的原始事件轨迹。
`decoction_device_work_record` 用于保存后台排查和页面展示需要的结构化作业明细。

| 字段 | 说明 |
| --- | --- |
| `action_type` | 动作类型，如 `BIND`、`START`、`FINISH`、`CANCELLED`、`TERMINATED`、`WATER_FINISHED`、`TEMPERATURE`、`ERROR`、`REJECTED`。 |
| `action_result` | `SUCCESS` 或 `REJECTED`。 |
| `task_status_before` | 动作前任务状态。 |
| `task_status_after` | 动作后任务状态。 |
| `operation_id` | 作业明细幂等键；成功动作使用原始 `operationId`，拒绝动作使用内部派生 `WORK-REJECTED-*`。 |
| `source` | 动作来源，如 `mes-event`、`mes-decoction-start`、`pda-bind-prescription`。 |
| `detail_payload` | 结构化明细内容，用于展示水量、温度、原因、备注等。 |

### 4.5 取消/终止口径

| 动作 | 允许任务状态 | 任务结果 | 订单结果 | 设备释放 | 事件 |
| --- | --- | --- | --- | --- | --- |
| 取消 | `BOUND` | `CANCELLED` | 保持 `RECHECKED` | 是 | `CANCELLED` |
| 终止 | `DECOCTING` | `TERMINATED` | `DECOCTING -> CANCELLED` | 是 | `TERMINATED` |
| 取消已开始任务 | `DECOCTING` | 拒绝 | 不变 | 否 | `REJECTED` |
| 终止未开始任务 | `BOUND` | 拒绝 | 不变 | 否 | `REJECTED` |
| 已完成任务取消/终止 | `DECOCTED` | 拒绝 | 不变 | 否 | `REJECTED` |

说明：
- `CANCELLED` 和 `TERMINATED` 都不是活动任务状态，不参与设备占用唯一索引。
- 取消用于“尚未开始煎煮，解除错误绑定或重新绑定设备”。
- 终止用于“已经开始煎煮，人工停止本次履约”，第一版直接将订单置为 `CANCELLED` 终态。
- 拒绝事件使用内部派生的 `operation_id=REJECTED-*`，原始设备 `operationId` 写入 payload，避免拒绝日志占用后续成功重试的幂等键。
- `REJECTED` payload 必须包含 `action`、`source`、`originalOperationId`、`actualStatus`、`expectedStatus`、`reason`。

暂未落地：

- 真实 PDA/MES 硬件厂商的 HTTP 出站调用和签名验签细节。
- 标签打印、PDA 出库、物流回调等非煎煮主链路入口。

## 5. 设备状态模拟

设备状态：

```text
DISABLED
 -> IDLE
 -> OCCUPIED
 -> WORKING
 -> IDLE
```

异常状态：

- `OFFLINE`
- `ERROR`
- `LOCKED`

并发规则：

- 同一设备同一时间只能绑定一个处方。
- 设备释放必须和任务完成/取消/终止绑定。
- 初始化处方必须释放设备。

## 6. 测试场景

### 6.1 正常链路

```text
机构推单
 -> 审核
 -> 调剂
 -> 复核
 -> PDA 绑定水桶/设备
 -> MES 上报开始
 -> MES 上报完成
 -> 设备释放
 -> 订单到 27
```

### 6.2 重复提交

- 重复绑定同一处方。
- 重复开始煎煮。
- 重复完成煎煮。
- 网络超时后客户端重试。

期望：

- 不产生重复状态日志。
- 不重复回调机构。
- 不重复释放设备。

### 6.3 并发绑定

两个 PDA 同时绑定同一处方或同一设备。

期望：

- 只有一个成功。
- 失败方返回明确错误。

### 6.4 乱序上报

- 完成先于开始。
- 取消后又完成。
- 终止后又开始。

期望：

- 状态机拒绝非法跳转。
- 写 `REJECTED` 事件日志。
- 写 `REJECTED` 作业明细。

### 6.5 人工修正

- 处方初始化后设备释放。
- 已打包订单初始化时取消物流。
- 订单走流程直接到签收。

期望：

- 设备、订单、处方、物流状态一致。
- 人工修正记录完整。

## 7. 模拟数据

至少准备：

- 2 个机构。
- 2 个煎煮中心。
- 5 台煎煮机。
- 3 台 PDA。
- 2 台打码机。
- 20 个水桶。
- 10 个待审核订单。
- 10 个待复核代煎处方。
- 5 个配送订单。
- 5 个自提订单。

## 8. Adapter 设计

```text
DeviceAdapter
  +-- MockDeviceAdapter
  +-- HttpDeviceAdapter

MesAdapter
  +-- MockMesAdapter
  +-- HttpMesAdapter
```

业务层只依赖接口，不依赖真实设备。

当前代码落地：

| 抽象 | 当前实现 | 说明 |
| --- | --- | --- |
| `DeviceAdapter` | `SimulatorDeviceAdapter` | 处理 PDA 登录、设备查询、可操作处方、绑定处方和 `operStatus=1/2/8/9` 状态上报。 |
| `MesAdapter` | `SimulatorMesAdapter` | 处理按水桶取任务、加水完成、取方、温度回调、复核推方查询和 `operStatus=1/2/8/9` 状态通知。 |
| 旧协议 Controller | `LegacyPdaProtocolController`、`LegacyMesProtocolController` | 只做字段别名解析和协议映射，不复制业务状态机。 |

旧状态映射：

| 旧状态 | 新动作 | 新任务状态结果 |
| --- | --- | --- |
| `1` | 开始煎煮 | `BOUND -> DECOCTING` |
| `2` | 完成煎煮 | `DECOCTING -> DECOCTED` |
| `8` | 取消煎煮 | `BOUND -> CANCELLED` |
| `9` | 终止煎煮 | `DECOCTING -> TERMINATED`，订单推进到 `CANCELLED` |

## 9. 验收标准

- PDA 模拟器可跑通从复核到煎煮完成。
- MES 模拟器可跑通设备取方、加水完成、开始、完成。
- 重复提交不产生重复业务结果。
- 并发绑定只有一个成功。
- 非法状态跳转被拒绝。
- 非法状态跳转写 `REJECTED` 事件。
- 后台可查看结构化设备作业明细。
- 旧 PDA/MES 兼容入口可复用同一套煎煮业务状态机。
- 每次成功状态变更都写状态日志和事件。
- 后台可看到完整作业轨迹。

## 10. 当前结论

没有真实 PDA 和 MES 时，模拟器不是可选项。

模拟器必须作为第一版开发和验收工具，否则核心煎煮链路无法稳定验证。
