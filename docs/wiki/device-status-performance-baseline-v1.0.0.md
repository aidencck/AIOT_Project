# 设备状态上报链路性能基线报告 v1.0.0

状态：`baseline-defined`
版本：`v1.0.0`
范围：`Auth Webhook -> Redis Stream -> Device Consumer -> MySQL(device_info)`

## Premise / Constraints / Boundaries / Endgame

- Premise：
  - 当前项目运行形态以 Docker Compose 单节点为主，核心链路基于 Spring Boot + Redis Stream + MySQL。
  - 设备状态是高频事件，吞吐与尾延迟直接影响在线态准确性和规则引擎时效。
- Constraints：
  - 当前执行环境未安装 `mvn`，无法在本地完成 Maven 编译验证。
  - 本地 `aiot-auth-service` 健康检查返回 `500`，无法产出可信在线压测数据。
  - 因此本报告先冻结“压测口径 + 优化动作 + 采集方法”，实测结果在环境就绪后回填。
- Boundaries：
  - 本轮仅覆盖“设备上下线状态上报链路”，不覆盖遥测数据、规则引擎复杂规则执行与影子读写链路。
  - 本轮只做后端服务优化，不改动设备端固件重连策略。
- Endgame：
  - 建立可复现的性能基线，形成版本化迭代闭环：`基线 -> 瓶颈定位 -> 优化 -> 回归压测 -> 路线图发布`。

## 核心链路模型

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#d1fae5', 'primaryBorderColor': '#34d399', 'lineColor': '#34d399', 'secondaryColor': '#111827', 'tertiaryColor': '#1f2937'}}}%%
flowchart LR
classDef edge fill:#0b1220,stroke:#34d399,stroke-width:1.5px,color:#d1fae5,font-family:monospace;
classDef io fill:#111827,stroke:#60a5fa,stroke-width:1.5px,color:#dbeafe,font-family:monospace;
classDef db fill:#111827,stroke:#f59e0b,stroke-width:1.5px,color:#fef3c7,font-family:monospace;

A["\"EMQX Webhook\""]:::io --> B["\"aiot-auth-service /webhook\""]:::edge
B --> C["\"Redis Stream: aiot:stream:device-event\""]:::io
C --> D["\"DeviceStatusStreamSubscriber\""]:::edge
D --> E["\"DeviceStatusBufferService\""]:::edge
E --> F["\"MySQL device_info(status,update_time)\""]:::db
```

## 压测口径与执行方案

### 口径定义

- 测试入口：`POST /api/v1/emqx/webhook`
- 成功标准：HTTP `2xx` 且 `device_info.status` 与预期一致
- 核心指标：
  - 吞吐：`QPS`
  - 可用性：`2xx 比例`、`5xx 比例`
  - 数据一致性：`状态写入成功数 / 事件总数`
  - 刷盘性能：`aiot_device_status_flush_duration_*`
  - 失败面：`aiot_device_status_flush_failed_total`、`DLQ 增量`

### 压测脚本

- 新增脚本：`scripts/perf_device_status_webhook.sh`
- 使用方式：

```bash
AIOT_EMQX_WEBHOOK_SECRET=your_secret \
bash scripts/perf_device_status_webhook.sh 5000 100 2000 /tmp/device_status_perf_report.txt
```

参数说明：
- 参数1：总请求数（默认 `2000`）
- 参数2：并发数（默认 `50`）
- 参数3：设备池规模（默认 `500`）
- 参数4：报告输出文件（默认 `/tmp/device_status_perf_report.txt`）

## 本轮瓶颈定位

- 瓶颈1：`DeviceStatusBufferService.flush()` 原实现逐条执行 `UPDATE`，高峰下 DB 往返次数随事件线性增长。
- 瓶颈2：`flush` 轮询前会拷贝整个 `statusBuffer.entrySet()`，在积压场景下带来额外内存和 CPU 开销。
- 瓶颈3：缺少链路级自定义指标，不利于压测阶段快速归因（是消费慢、刷盘慢还是写库失败）。

## 已落地优化（本次提交）

- 优化A：按状态分组后批量更新（`IN (...)`）替代逐条更新，显著降低 SQL 往返。
- 优化B：`flush` 仅抽取 `max-batch-size` 批次样本，避免全量快照复制。
- 优化C：新增性能指标：
  - `aiot.device.status.flush.success.total`
  - `aiot.device.status.flush.failed.total`
  - `aiot.device.status.flush.dropped.total`
  - `aiot.device.status.flush.duration`

## 基线结果（待回填）

| 场景 | 请求量 | 并发 | 设备池 | QPS | 2xx | 5xx | flush p95 | 备注 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Baseline-v1-SMOKE | 2,000 | 50 | 500 | TBA | TBA | TBA | TBA | 环境就绪后执行 |
| Baseline-v1-LOAD | 20,000 | 200 | 5,000 | TBA | TBA | TBA | TBA | 环境就绪后执行 |
| Baseline-v1-STRESS | 100,000 | 500 | 20,000 | TBA | TBA | TBA | TBA | 风险评估后执行 |

## 技术债治理清单（关联链路）

- 技术债1：缺少 webhook 入站限流与排队保护，峰值流量容易放大下游压力。
- 技术债2：当前刷盘按状态分组，后续可升级为 `CASE WHEN` 单 SQL 批量更新，进一步减少 SQL 数量。
- 技术债3：缺少“端到端时延”指标（从 webhook 入站到 DB 落库完成）。
- 技术债4：缺少压测环境固定数据集和一键回归流水线。

## 回归与验收建议

- 验收门槛（建议）：
  - 2xx 成功率 `>= 99.9%`
  - 5xx 比例 `<= 0.1%`
  - `flush` p95 可控（目标阈值按环境能力设定）
  - 压测后 `pending` 无持续堆积，DLQ 增量可解释
- 发布策略：
  - 先灰度启用（小流量环境）观察 `flush.failed` 与 `DLQ`
  - 再放量至全链路压测并回填本报告的 TBA 指标
