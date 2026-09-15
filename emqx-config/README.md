# EMQX → 云端桥接配置（基础设施层）

本目录承载 EMQX 5.x 把真实 MQTT 设备事件（上下线）桥接到云端 Webhook 的声明式配置。

## 文件

- `emqx_bridges.conf`：设备上下线事件的 Webhook 桥接 + 规则 SQL 参考。

## 挂载关系

- 根 `docker-compose.yml`：`./emqx-config -> /opt/emqx/etc/emqx-config:ro`
- `compose/compose.base.yml`：`../emqx-config -> /opt/emqx/etc/emqx-config:ro`

> 挂载目标是 `emqx-config` 子目录而非 `/opt/emqx/etc` 本身，避免覆盖镜像自带的
> `emqx.conf`。EMQX 5.x 不会从该目录自动加载桥接/规则，见下。

## 落地方式（EMQX 5.x）

EMQX 5.x 的规则/桥接对象（Webhook、Rule）持久化在 Mnesia，不随 `emqx.conf` 加载。
本目录作为声明式参考，实际启用二选一：

1. Dashboard：Integration → Webhook 创建桥接；Rules 中粘贴 `emqx_bridges.conf` 的 SQL。
2. REST API（示例）：

```bash
# 1) 创建 Webhook 桥接
curl -s -u "${EMQX_API_USER:-admin}:${EMQX_API_PASSWORD}" -X POST \
  http://localhost:18083/api/v5/bridges -H 'Content-Type: application/json' \
  -d "{\"type\":\"webhook\",\"name\":\"aiot_device_status_webhook\",\"url\":\"${AUTH_WEBHOOK_URL:-http://aiot-auth-service:8082/api/v1/emqx/webhook}\",\"method\":\"post\",\"headers\":{\"content-type\":\"application/json\",\"x-internal-token\":\"${AIOT_INTERNAL_TOKEN}\"}}"

# 2) 创建上线规则（下线规则同法，SQL 见 emqx_bridges.conf）
curl -s -u "${EMQX_API_USER:-admin}:${EMQX_API_PASSWORD}" -X POST \
  http://localhost:18083/api/v5/rules -H 'Content-Type: application/json' \
  -d '{"name":"aiot_device_connected","sql":"SELECT event AS action, clientid, username, timestamp / 1000 AS timestamp FROM \"$events/client_connected\"","actions":["webhook:aiot_device_status_webhook"]}'
```

## 目标端点契约（与 aiot-auth-service 对齐）

- `POST {AUTH_WEBHOOK_URL}`，默认 `http://aiot-auth-service:8082/api/v1/emqx/webhook`
- Header `X-Internal-Token`: 静态内部 token（`AIOT_INTERNAL_TOKEN`），auth-service 匹配 `aiot.internal.token` 后跳过 HMAC 校验直接放行
- Body: `{"action":"client.connected|client.disconnected","clientid":"...","username":"...","timestamp":<秒>}`

## 环境变量

- `AUTH_WEBHOOK_URL`：Webhook 目标 URL（已写入 `compose/env/*/.env.example`）。
- `AIOT_INTERNAL_TOKEN`：内部信任 token，EMQX 桥接出站头与 aiot-auth-service 的 `aiot.internal.token` 必须一致。

## 方案说明（方案 B 已落地）

EMQX 5.x 内置 SQL 仅提供 `md5/sha/sha256` 等散列函数，不提供 HMAC 计算，无法原生生成
`x-emqx-signature` 头。现改为「内部网络信任」：Webhook 桥接携带静态 `X-Internal-Token` 头，
aiot-auth-service 匹配 `aiot.internal.token` 后跳过 HMAC 签名校验直接放行；仅当该头缺失或
不匹配时才回退到原 HMAC 校验（`x-emqx-signature`）。
