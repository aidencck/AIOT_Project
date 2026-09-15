# API Availability Incident Runbook

## 适用告警

- `ApiAvailabilityBurnRateFast`

## 判定口径

- 核心入口：`aiot-gateway`
- SLO：`SLO-API-01`
- 目标：30 天可用性 `>= 99.95%`

## 处置步骤

1. 打开 Grafana `aiot-api-slo-overview`，确认 5xx、限流、熔断器和 readiness 曲线。
2. 检查 `aiot-gateway`、`aiot-auth-service`、`aiot-device-service`、`aiot-home-service` 的 `/actuator/health/readiness`。
3. 若发现最近发布，先执行单服务回滚或停止继续灰度。
4. 若 5xx 来自下游，转入对应服务 Runbook，并在告警系统保留主故障单。
5. 恢复后登记 `MTTD/MTTA/MTTR` 与根因，并回写 SLO 复盘。

## 关键证据

- `gateway` 5xx 占比
- 网关限流命中率
- 熔断器打开次数
- 下游 readiness 和 Nacos 可用性
