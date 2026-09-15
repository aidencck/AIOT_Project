# Auth Service Incident Runbook

## 适用告警

- `AuthFailureRateHigh`

## 判定口径

- 服务：`aiot-auth-service`
- SLO：`SLO-AUTH-01`
- 关键链路：EMQX 鉴权、Webhook 入站、内部令牌调用

## 处置步骤

1. 检查最近 15 分钟鉴权失败率、HTTP 状态分布和 Redis/MySQL readiness。
2. 校验 `AIOT_EMQX_WEBHOOK_SECRET`、`AIOT_INTERNAL_TOKEN`、`MYSQL_PASSWORD` 是否发生变更。
3. 对照日志确认是否为签名失败、重放保护、下游超时或数据库异常。
4. 如由新版本引起，立即暂停灰度并执行单服务回滚。
5. 恢复后补录失败原因 TopN，并评估是否调整签名与重放保护阈值。

## 关键证据

- `/api/v1/emqx/auth` 失败率
- `/api/v1/emqx/webhook` 失败率
- Redis/MySQL/Nacos readiness
- 最近发布与配置变更记录
