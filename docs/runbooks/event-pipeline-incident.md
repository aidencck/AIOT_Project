# Event Pipeline Incident Runbook

## 适用告警

- `StreamConsumeFailureRateHigh`
- `DlqGrowthRateHigh`
- `PendingRecoveryFailureRateHigh`

## 判定口径

- 服务域：`aiot-auth-service`、`aiot-device-service`、`aiot-rule-engine`、`aiot-shadow-service`
- SLO：`SLO-EVENT-01`、`SLO-EVENT-02`

## 处置步骤

1. 在 Grafana `aiot-event-pipeline` 看消费失败率、DLQ 增量、pending 变化与 flush 耗时。
2. 登录 Redis 检查 Stream、消费组、pending backlog 是否异常增长。
3. 查最近 15 分钟规则执行、序列化、下游 HTTP 调用和数据库刷盘错误。
4. 如确认由新版本引入，优先回滚消费者侧服务，并保留问题样本事件。
5. 恢复后对 DLQ 做分类清洗，并补充缺失的幂等/重试/限流策略。

## 关键证据

- `aiot_stream_consume_failed_total`
- `aiot_stream_dlq_published_total`
- `aiot_stream_pending_recovery_failed_total`
- `aiot.device.status.flush.*`
