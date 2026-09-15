# 可观测与故障处理工程师分派执行单

## 1. Premise / Constraints / Boundaries / Endgame

- `Premise`：在一周内完成可观测最小闭环，覆盖业务指标、告警、看板与一次故障演练复盘。
- `Constraints`：不新增微服务；优先在现有 `aiot-auth-service`、`aiot-device-service`、`aiot-rule-engine` 内补齐指标与告警资产。
- `Boundaries`：本期只聚焦三类业务指标：鉴权失败率、事件积压、DLQ 增长率，不扩展其他业务域。
- `Endgame`：形成可复制的观测交付模板（指标命名、告警规则、看板口径、演练复盘），可直接复用于后续版本。

## 2. 团队角色与责任

| 角色 | 人员代号 | 责任范围 | 交付物 |
|---|---|---|---|
| 架构负责人 | TL | 闸口决策、跨组依赖清障、最终验收 | 评审结论、上线签字 |
| 平台工程师 | DEV-PLAT | Prometheus 抓取与配置发布 | `monitoring/prometheus/prometheus.yml` |
| 后端鉴权工程师 | DEV-AUTH | 鉴权成功/失败业务指标埋点 | 指标代码与口径说明 |
| 后端事件工程师 | DEV-EVT | 事件积压与 DLQ 指标埋点 | 指标代码与验证记录 |
| SRE 工程师 | DEV-SRE | 告警规则、通知路由、值班联动 | `monitoring/prometheus/alerts/*.yml` |
| 前端可视化工程师 | DEV-FE | 服务看板与业务指标看板 | 看板页面与字段说明 |
| 值班负责人 | DEV-OPS | 故障演练组织与复盘沉淀 | 演练复盘文档 |

## 3. 工作包拆分（直接派发）

| WP | 工作包 | Owner | 协作 | 预计工时 | 验收标准（DoD） |
|---|---|---|---|---|---|
| WP-01 | 指标命名与标签规范冻结 | TL | DEV-AUTH/DEV-EVT/SRE | 0.5天 | 指标命名、标签和查询口径冻结 |
| WP-02 | 鉴权失败率指标埋点 | DEV-AUTH | TL | 1天 | 可产出 `auth_total` 与 `auth_failed_total` |
| WP-03 | 事件积压指标埋点 | DEV-EVT | TL | 1天 | 可产出 `event_backlog_depth`（Gauge） |
| WP-04 | DLQ 增长指标埋点 | DEV-EVT | DEV-SRE | 1天 | 可产出 `dlq_published_total` 与 `dlq_size` |
| WP-05 | Prometheus 抓取配置发布 | DEV-PLAT | DEV-SRE | 0.5天 | 三个核心服务可稳定抓取 |
| WP-06 | 告警规则发布与联调 | DEV-SRE | DEV-PLAT | 0.5天 | 三类告警可触发并可通知 |
| WP-07 | 服务看板与业务看板上线 | DEV-FE | DEV-SRE | 1天 | 看板可展示阈值态与趋势 |
| WP-08 | 故障演练与复盘 | DEV-OPS | 全员 | 1天 | 形成可审阅复盘并落改进项 |

## 4. D1-D5 执行节奏

| 日程 | 关键目标 | 责任人 | 出口物 |
|---|---|---|---|
| D1 | 口径冻结 + Prometheus 抓取配置合并 | TL/DEV-PLAT | 规范文档 + 配置 MR |
| D2 | 鉴权失败率、事件积压指标合并 | DEV-AUTH/DEV-EVT | 指标 MR + 自测记录 |
| D3 | DLQ 指标与告警规则合并 | DEV-EVT/DEV-SRE | 告警规则 + 触发记录 |
| D4 | 服务看板与业务看板发布 | DEV-FE | 看板链接 + 字段文档 |
| D5 | 故障演练、复盘与终验收 | DEV-OPS/TL | 复盘文档 + 改进清单 |

## 5. 模块到人分配

- `DEV-AUTH`：`aiot-auth-service`（鉴权业务指标埋点与接口口径）。
- `DEV-EVT`：`aiot-device-service`、`aiot-rule-engine`（积压与 DLQ 指标埋点）。
- `DEV-PLAT`：`monitoring/prometheus/prometheus.yml`（抓取目标与标签）。
- `DEV-SRE`：`monitoring/prometheus/alerts/aiot-observability-rules.yml`（告警规则与阈值）。
- `DEV-FE`：管理后台看板页面（服务状态 + 业务指标）。
- `DEV-OPS`：`docs/sprints/INCIDENT_DRILL_ROUND1_RETRO.md`（演练复盘沉淀）。

## 6. 上线闸口（未达标禁止通过）

- 业务指标闸口：三类指标都可抓取、可查询、可解释。
- 告警闸口：三类告警均完成触发演示并送达值班通道。
- 看板闸口：服务可用性与业务指标同屏可观测。
- 演练闸口：完成一次真实演练并沉淀复盘与改进 Owner。

## 7. 阻塞升级机制

- `4小时` 内跨组阻塞未解除：Owner 升级至 TL。
- `24小时` 内环境或发布阻塞未解除：TL 升级到管理层协调资源。
- 任一 P0 告警规则无法触发：停止发布并回退到上一稳定版本。
