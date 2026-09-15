# EMQX 鉴权与 Webhook 安全配置模板

## 1. 必填环境变量

- `AIOT_EMQX_WEBHOOK_SECRET`：Webhook HMAC 密钥，生产环境必须设置。
- `AIOT_EMQX_WEBHOOK_MAX_SKEW_SECONDS`：请求时间窗容忍秒数，默认 `300`。
- `AIOT_INTERNAL_TOKEN`：服务间调用令牌，Auth->Device 状态同步必填。
- `AIOT_DEVICE_STATUS_SYNC_TIMEOUT_MS`：状态回写超时，默认 `2000`。
- `AIOT_DEVICE_STATUS_SYNC_RETRY_TIMES`：状态回写重试次数，默认 `1`。
- `AIOT_DEVICE_STATUS_SYNC_RETRY_BACKOFF_MS`：重试退避毫秒，默认 `200`。

## 2. 签名规则

- 参与签名字符串：`{action}.{clientId}.{username}.{timestamp}`。
- 签名算法：`HmacSHA256(payload, webhookSecret)`。
- 头部字段：`x-emqx-signature`。

## 3. 防重放要求

- 对签名值生成重放键：`aiot:auth:webhook:replay:{signature}`。
- 使用 Redis `SETNX + TTL` 判重，TTL 建议等于时间窗。
- 已出现过的签名直接拒绝并记录审计日志。

## 4. 验收脚本建议

- 正常请求：签名正确 + 时间窗内，返回 `200`。
- 非法签名：签名错误，返回 `401`。
- 重放请求：同签名重复发送，第二次返回 `401`。
- 过期请求：时间戳超窗，返回 `401`。
