# 设备状态上报链路性能基线报告 v1.1.0

状态：`baseline-prepared`
版本：`v1.1.0`
范围：`Auth Webhook -> Redis Stream -> Device Consumer -> MySQL(device_info)`

## Premise / Constraints / Boundaries / Endgame

- Premise：
  - 核心链路为 `aiot-auth-service` 入站鉴权与事件发布、`aiot-device-service` 消费与批量刷盘。
  - 压测目标是得到可复现实验数据，并定位瓶颈归因到具体模块（入口、消费、刷盘、存储）。
- Constraints：
  - 当前本地环境探测结果：`http://localhost:8082/actuator/health -> 500`，`http://localhost:8083/actuator/health -> 404`。
  - 在未知真实 `AIOT_EMQX_WEBHOOK_SECRET` 条件下，仅能完成脚本可执行性验证，不能形成业务有效吞吐结论。
- Boundaries：
  - 本报告只覆盖“设备状态上报链路”，不覆盖遥测、规则执行复杂算子、影子读写性能。
- Endgame：
  - 固化“同口径、可复现、可回归”的基线体系，作为 Q3 版本化性能治理输入。

## 链路与测量点

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#d1fae5', 'primaryBorderColor': '#34d399', 'lineColor': '#34d399', 'secondaryColor': '#111827', 'tertiaryColor': '#1f2937'}}}%%
flowchart LR
classDef core fill:#0b1220,stroke:#34d399,stroke-width:1.5px,color:#d1fae5,font-family:monospace;
classDef io fill:#111827,stroke:#60a5fa,stroke-width:1.5px,color:#dbeafe,font-family:monospace;
classDef db fill:#111827,stroke:#f59e0b,stroke-width:1.5px,color:#fef3c7,font-family:monospace;

A["\"POST /api/v1/emqx/webhook\""]:::io --> B["\"AuthServiceImpl.publishDeviceEvent()\""]:::core
B --> C["\"Redis Stream aiot:stream:device-event\""]:::io
C --> D["\"DeviceStatusStreamSubscriber.onMessage()\""]:::core
D --> E["\"DeviceStatusBufferService.flush()\""]:::core
E --> F["\"device_info.status 批量更新\""]:::db
```

## 压测口径与执行命令

- 压测脚本：`scripts/perf_device_status_webhook.sh`
- 核心指标：
  - `throughput_qps`
  - `success_2xx` / `http_5xx` / `network_failed_000`
  - `latency_avg_sec` / `latency_p50_sec` / `latency_p95_sec` / `latency_p99_sec`
  - `status_breakdown`（状态码分布）
- 标准命令：

```bash
AIOT_EMQX_WEBHOOK_SECRET=your_secret \
bash scripts/perf_device_status_webhook.sh 20000 200 5000 /tmp/device_status_perf_report.txt
```

## 本轮已执行结果（2026-04-27）

### 环境可用性探测

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| Auth 健康检查 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health` | `500` |
| Device 健康检查 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/actuator/health` | `404` |

### 脚本可执行性验证（非业务有效基线）

命令：

```bash
AIOT_EMQX_WEBHOOK_SECRET=dummy \
bash scripts/perf_device_status_webhook.sh 10 2 5 /tmp/device_status_perf_report_v110.txt
```

结果：

| 场景 | 总请求 | 并发 | QPS | 2xx | 401 | 5xx | avg(s) | p50(s) | p95(s) | p99(s) | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| smoke-auth-invalid | 10 | 2 | 10 | 0 | 10 | 0 | 0.038388 | 0.005801 | 0.161086 | 0.161086 | 仅证明脚本可用，不能代表链路性能 |

## 瓶颈定位（代码级）

- 入口侧：
  - `AuthServiceImpl` 每次 webhook 都执行签名校验与重放保护，峰值下需要评估 Redis `setIfAbsent` 热键冲击。
- 消费侧：
  - `DeviceStatusStreamSubscriber` 当前在 `enqueue` 后立即 ACK，属于“高吞吐优先”策略，崩溃窗口内存在缓冲区未刷盘即确认风险。
- 刷盘侧：
  - `DeviceStatusBufferService.flush()` 已实现按状态分组批量更新，但仍是“每个状态一条 SQL”；在状态种类扩展时会线性增加 SQL 次数。
  - `statusBuffer` 当前无容量上限与背压策略，需要防止极端堆积带来内存风险。

## 优化计划（本轮建议）

- P0（本周）：
  - 补齐压测环境前置自检（服务健康、密钥、Redis/MySQL可达）并作为压测脚本前置门禁。
  - 在 `DeviceStatusBufferService` 增加 `pending size` Gauge 和 flush 批次大小直方图，提升瓶颈判读速度。
- P1（本月）：
  - 评估“落库成功后再 ACK”或“WAL/本地持久队列”方案，降低 ACK 早于持久化的风险。
  - 将批量更新从“按状态多 SQL”升级为 `CASE WHEN` 单 SQL 批量更新，减少 DB 往返。
- P2（季度）：
  - 把压测与阈值校验接入 CI/CD，形成发布前性能门禁。

## 基线回填模板（待环境就绪后执行）

| 场景 | 请求量 | 并发 | 设备池 | QPS | 2xx | 5xx | p95(s) | p99(s) | flush失败率 | pending变化 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Baseline-v1.1-SMOKE | 2,000 | 50 | 500 | TBA | TBA | TBA | TBA | TBA | TBA | TBA | 环境就绪后回填 |
| Baseline-v1.1-LOAD | 20,000 | 200 | 5,000 | TBA | TBA | TBA | TBA | TBA | TBA | TBA | 环境就绪后回填 |
| Baseline-v1.1-STRESS | 100,000 | 500 | 20,000 | TBA | TBA | TBA | TBA | TBA | TBA | TBA | 风险评估后回填 |

## 验收门槛（建议）

- 可用性：`2xx >= 99.9%`，`5xx <= 0.1%`
- 性能：`p95` 与 `p99` 在版本目标阈值内（按环境等级定义）
- 稳定性：压测后 `pending` 不持续上升，`DLQ` 增量可解释且可追踪
- 工程化：每次压测都有版本化报告与原始输出文件留档
