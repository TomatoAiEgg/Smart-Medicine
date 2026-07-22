# Apifox 本机单链路测试说明

## 1. 目的

Apifox 只用于本机手工测试和演示一条订单链路，不承担批量压测、回调接收和自动化验收。

适合用 Apifox 做：

- HIS 推处方创建订单接口调试。
- 签名、时间戳、请求体字段校验。
- 单条订单从创建到履约完成的人工演示。
- 错误签名、重复订单号、缺少字段等单接口用例。

不建议用 Apifox 做：

- 大批量并发推单。
- 长时间接收 callback-service 回调。
- 压测统计、P95/P99、MQ 积压对账。
- 故障演练自动恢复验证。

这些由 `test/integration/his-batch-callback.py` 负责。

## 2. 环境变量

在 Apifox 环境里配置：

```text
baseUrl=http://127.0.0.1
gatewayUrl={{baseUrl}}/api/institution
appKey=demo-app
appSecret=本机测试密钥
timestamp=
signature=
```

注意：

- `appSecret` 只放在本机 Apifox 环境里，不写入仓库。
- 不使用真实医院生产密钥。
- 不使用真实患者姓名、手机号、身份证号。

## 3. 签名前置脚本

在 Apifox 的请求前置脚本里放入：

```javascript
const appKey = pm.environment.get('appKey') || 'demo-app';
const appSecret = pm.environment.get('appSecret');

if (!appSecret) {
  throw new Error('Missing Apifox environment variable: appSecret');
}

const timestamp = Math.floor(Date.now() / 1000).toString();
const rawBody = pm.request.body && pm.request.body.raw ? pm.request.body.raw : '';
const bodyHash = CryptoJS.SHA256(rawBody).toString(CryptoJS.enc.Hex);
const source = `${appKey}\n${timestamp}\n${bodyHash}`;
const signature = CryptoJS.HmacSHA256(source, appSecret).toString(CryptoJS.enc.Hex);

pm.environment.set('timestamp', timestamp);
pm.environment.set('signature', signature);
pm.request.headers.upsert({ key: 'X-App-Key', value: appKey });
pm.request.headers.upsert({ key: 'X-Timestamp', value: timestamp });
pm.request.headers.upsert({ key: 'X-Signature', value: signature });
pm.request.headers.upsert({ key: 'Content-Type', value: 'application/json' });
```

签名规则必须和服务端一致：

```text
bodyHash = sha256(rawBody)
source = appKey + "\n" + timestamp + "\n" + bodyHash
signature = hmacSha256(appSecret, source)
```

## 4. 创建订单请求

请求：

```text
POST {{gatewayUrl}}/createOrder
```

请求头由前置脚本自动生成：

```text
X-App-Key
X-Timestamp
X-Signature
Content-Type: application/json
```

请求体示例：

```json
{
  "externalOrderNo": "APIFOX-ORDER-{{$timestamp}}",
  "patientName": "测试患者",
  "patientPhone": "13800000000",
  "receiverName": "测试收件人",
  "receiverPhone": "13800000000",
  "receiverAddress": "测试地址",
  "prescriptions": [
    {
      "externalPrescriptionNo": "APIFOX-RX-{{$timestamp}}",
      "doctorName": "测试医生",
      "diagnosis": "测试诊断",
      "details": [
        {
          "drugCode": "DRUG001",
          "drugName": "测试饮片",
          "dose": "10",
          "unit": "g"
        }
      ]
    }
  ]
}
```

通过标准：

```text
HTTP 2xx
code=SUCCESS
data.orderId 非空
data.orderNo 非空
data.duplicate=false
```

## 5. 单链路建议顺序

Apifox 手工测试按这个顺序：

```text
1. HIS 推处方创建订单
2. 查询订单进度
3. 查询待审核任务并审核通过
4. 查询待调剂任务并完成调剂
5. 查询待复核任务并完成复核
6. PDA/MES 模拟煎煮
7. 物流打包
8. 物流发货
9. 触发 callback-service dispatch-due
10. 查询回调记录
11. 查询订单完整进度
```

批量执行这些步骤时不要用 Apifox，改用自动化脚本。

## 6. 常见失败

| 现象 | 排查 |
| --- | --- |
| `INVALID_SIGNATURE` | 检查请求体是否被格式化后才签名，签名必须使用实际 rawBody |
| `APP_NOT_FOUND` | 检查 `appKey` 是否和数据库机构应用一致 |
| IP 白名单失败 | 检查 gateway 获取到的客户端 IP 是否在机构白名单里 |
| 重复订单 | 检查 `externalOrderNo` 是否复用 |
| 请求超时 | 检查云服务器防火墙、安全组、本机 IP 放行 |
| 创建成功但后台无任务 | 检查 message-service、RocketMQ、workflow-service 是否正常 |

## 7. 与自动化脚本分工

Apifox 验证“单条订单能不能通”。

自动化脚本验证：

```text
能不能批量推单
能不能并发推单
能不能自动推进全链路
能不能收到回调
失败能不能定位在哪个阶段
监控里能不能看到链路、SQL、MQ 和资源指标
```
