# AIoT 可观察性数据流全景（Docker Compose 环境）

> 范围：仅覆盖 docker compose 环境（`docker-compose.yml` + `docker-compose.local.yml`，项目名 `aiot-java`，网桥 `aiot-java_aiot-net`）。不涉及 K8s。

## 一、四支柱与端点矩阵

| 支柱 | 组件 | 端点/端口 | 采集/接收方式 | 落地配置 |
|------|------|-----------|---------------|----------|
| Metrics | Prometheus | `9090` | 静态抓取（15s）+ Recording Rules | [prometheus.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/prometheus/prometheus.yml) |
| Logs | Promtail → Loki | `9080` / `3100` | docker_sd 抓容器 json-log | [promtail.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/promtail/promtail.yml) |
| Traces | Tempo | `4317/4318(OTLP)` / `3200(query)` | OTLP gRPC+HTTP | [tempo.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/tempo/tempo.yml) |
| Alerts | Alertmanager | `9093` | Prometheus 推送 → webhook 路由 | [alertmanager.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/alertmanager/alertmanager.yml) |
| UI | Grafana | `3000` | Provisioning 自动导入 | [datasources.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/grafana/provisioning/datasources/datasources.yml) |

## 二、四条核心数据流（起点 → 中转 → 终点）

### 1. Metrics 指标流

```
[8 业务服务 /actuator/prometheus]  ──┐
[5 中间件 exporter]                 ──┼──► Prometheus ──► Recording Rules ──► Grafana
   ├ mysqld-exporter :9104             │    (external_labels: env/cluster/region)
   ├ redis-exporter :9121             │    (规则预计算: 网关可用率/RPS/P99/事件积压)
   ├ EMQX /api/v5/prometheus/stats    │
   ├ Nacos /nacos/actuator/prometheus │
   └ Alertmanager /metrics            │
```

- **16 个 job 全部 `health=up`**：8 业务（gateway/auth/device/home/rule/shadow/mqtt-adapter/data-parser）+ 8 观测/中间件（prometheus/loki/tempo/mysql/redis/emqx/nacos/alertmanager）。
- 每个 job 携带 `service` + `domain` 标签，`domain` 用于自愈告警分流（`middleware|observability|gateway|auth|device|home|rule|shadow|adapter|parser`）。
- Recording Rules 见 [aiot-recording-rules.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/prometheus/rules/aiot-recording-rules.yml)。

### 2. Logs 日志流

```
容器 stdout/stderr (json-file)
   └─► Promtail docker_sd (unix:///var/run/docker.sock, 10s)
         └─► relabel 打标签: container/service/compose_project/stream
               └─► Loki /loki/api/v1/push ──► Grafana (uid=loki-main)
```

- 实测 17 个容器日志流入（8 业务 + 观测组件 + 中间件），近 1h 总量约 128k 行。
- 标签契约：`container`（容器名）、`service`（compose service）、`compose_project`（固定 `aiot-java`）、`stream`（stdout/stderr）。

### 3. Traces 链路流

```
业务服务 Micrometer Tracing + OpenTelemetry
   └─► OTLP gRPC(4317) / HTTP(4318) ──► Tempo (local 存储, retention 48h)
         └─► metrics_generator: service-graphs + span-metrics
               └─► Grafana (uid=tempo, tracesToLogsV2 → loki-main)
```

- 关键约束：`X-Trace-Id` 必须在 `Gateway → Service → Redis Stream → Consumer` 全链路透传，否则消费端链路断裂。
- Tempo 关联 Loki 通过 `tracesToLogsV2`（`service.name` → `service` 标签，±5m 时间窗）。

### 4. Alerts 告警流

```
Prometheus Alert Rules ──► Alertmanager (group_by: alertname/service/team/severity/env)
                            └─► 5 receiver 路由 ──► ops-router webhook
                                  ├ default  → /webhook/default
                                  ├ sre-p1   → /webhook/p1     (severity=critical)
                                  ├ auth-team→ /webhook/auth    (team=auth)
                                  ├ event-team→ /webhook/event  (team=event)
                                  └ platform-team→ /webhook/platform (team=platform)
```

- 告警规则见 [aiot-observability-rules.yml](file:///Users/aiden/Projects/AIOT-java/monitoring/prometheus/alerts/aiot-observability-rules.yml)：6 条业务告警 + 3 条自愈告警。
- 自愈告警组（`aiot-observability-self-healing`）：
  - `ObservabilityScrapeTargetDown`：`up{domain=~"middleware|observability"} == 0`
  - `ObservabilityComponentMissing`：8 个 `absent(up{job=...})`
  - `ObservabilityPipelineWatchdog`：`vector(1)` 恒触发，用于验证告警链路闭环。

## 三、业务栈与观测栈整合点

| 整合点 | 业务侧 | 观测侧 | 闭环产物 |
|--------|--------|--------|----------|
| 设备接入 | EMQX → auth-service（HMAC Webhook）→ device-service（MySQL）→ Redis Stream | gateway/auth/device 指标 + 链路 | API SLO 看板 + Webhook Rejects 面板 |
| 事件流 | Redis Stream → rule-engine/shadow-service 消费 | 消费成功率/积压/DLQ 指标 | Event Pipeline 看板 |
| 影子同步 | shadow-service Redis Hash + 乐观锁 | shadow 指标 + 事件流 | Runtime Ops 看板 |
| 跨服务调用 | Gateway `lb://` → 各服务 | Micrometer Tracing + OTLP | Tempo 链路 + tracesToLogs |

## 四、四方对账实测数据（本次闭环验证）

| 维度 | 验证方式 | 结果 |
|------|----------|------|
| Metrics | `GET /api/v1/targets` | 16/16 target `up` |
| Logs | Loki `count_over_time` by container | 8 业务 + 观测组件全部有流 |
| Traces | Tempo `/api/search`（近 1h） | 20 条 root traces |
| Alerts | Alertmanager `/api/v2/alerts` | 1 条 `active`（Watchdog，severity=none） |

Grafana UI 实测（浏览器登录 admin，5 张看板全部渲染）：
- **AIOT Overview**：Gateway Availability 100.000%、Requests 46、Active Targets 16、Event Backlog 0。
- **AIOT API SLO**：Availability 100.0000%、P99 169ms、Top Error Endpoints 含 `429/504/503`（历史压测瓶颈）。
- **AIOT Event Pipeline**：Consumer Success 100.00%、Backlog 0、DLQ Rate 0.000。
- **AIOT Runtime Ops**：12 target availability=1、JVM/CPU/HTTP Latency 全服务有值。

## 五、数据流断裂风险点与自愈约束

| 风险点 | 症状 | 自愈手段 | 历史根因 |
|--------|------|----------|----------|
| mysqld-exporter 崩溃 | `mysql_up=0` | `ObservabilityScrapeTargetDown` | v0.15.0 移除 `DATA_SOURCE_NAME`，改用 `--mysqld.username/address` |
| Nacos 缓存写失败 | `lb://` 偶发 504 | 服务发现抖动告警 | 容器 `user.home` 指向只读 `/home`，已加 `-Duser.home=/app` |
| 网关限流桶 | 大量 429 | 速率/5xx 告警 | Webhook 突发容量不足，需按压测数据调突发值 |
| Redis 序列化不一致 | 消费端反序列化失败 | DLQ 增长率告警 | 生产者/消费者序列化策略不一致 |
| 观测组件缺失 | 目标 absent | `ObservabilityComponentMissing` | 8 个 `absent(up{job=...})` |

## 六、落地约束（不可违背）

1. Prometheus `external_labels` 必须定义在 `global` 下（`env/cluster/region`）。
2. `observability up` 必须通过容器标签/名称精确控制，严禁误伤/重启业务容器。
3. Alertmanager 所有 receiver 必须指向真实存在的服务（`ops-router`）。
4. Grafana 采用 Provisioning 模式，通过 UID（`prometheus`/`loki-main`/`tempo`）自动关联，严禁手动在 UI 配置。
5. 中间件 exporter 用 CLI `command` 传参（mysqld-exporter v0.15+ 无 `DATA_SOURCE_NAME`）。
