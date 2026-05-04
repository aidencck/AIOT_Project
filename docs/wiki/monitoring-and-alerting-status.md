# 监控与告警现状（current）

最后更新：`2026-04-30`

## 目标

统一说明“哪些监控能力已经在代码中落地、哪些告警规则已可用、哪些仍是缺口”，避免文档和运行面脱节。

## 已落地（代码事实）

- 指标导出：各服务已接入 Actuator + Micrometer + Prometheus endpoint（`/actuator/prometheus`）
- 事件链路指标：`rule-engine`、`shadow-service`、`device-service` 已埋点消费/失败/重试/DLQ/pending 回收指标
- 健康探针：readiness/liveness 已在主要服务启用
- 追踪基线：`TraceIdFilter` 已在公共模块接入

## 告警规则状态

规则文件：`monitoring/prometheus/alerts/aiot-observability-rules.yml`

- `AuthFailureRateHigh`：基于 `http_server_requests_seconds_count`（`/api/v1/emqx/auth`）计算失败率
- `StreamConsumeFailureRateHigh`：基于 `aiot_stream_consume_failed_total / aiot_stream_consume_total`
- `DlqGrowthRateHigh`：基于 `aiot_stream_dlq_published_total` 增长率
- `PendingRecoveryFailureRateHigh`：基于 `aiot_stream_pending_recovery_failed_total`

## 抓取配置状态

抓取文件：`monitoring/prometheus/prometheus.yml`

已覆盖服务：

- `aiot-gateway:8080`
- `aiot-auth-service:8082`
- `aiot-device-service:8081`
- `aiot-home-service:8083`
- `aiot-rule-engine:8084`
- `aiot-shadow-service:8087`
- `aiot-mqtt-adapter:8085`
- `aiot-data-parser:8086`

## 当前缺口（in-progress）

- 部署层仍未形成标准化 Prometheus + Alertmanager + Grafana 一体化运行手册
- 业务指标仍需补齐：
  - 鉴权失败细分维度（设备/租户/来源）
  - 事件积压深度 Gauge（当前以失败率和 DLQ 增长替代）
- 需要一次“告警触发 -> 通知 -> 定位 -> 复盘”实战演练留档

## 验收建议

- 先在测试环境验证 4 条核心告警均可触发并恢复
- 每次规则变更同步更新本页与 `testing-and-troubleshooting.md`
- 把告警演练记录纳入迭代验收产物
