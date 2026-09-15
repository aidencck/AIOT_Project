# AIoT 架构链路地图（Architecture Map）

> 单一事实源入口。与 `.trae/agents/system-boundary-analyzer.md`、`compose/compose.base.yml`、`k8s/helm/*.yaml` 保持对齐，漂移以代码为准。

## 1. 内部系统清单（8 微服务 + 1 Web + 1 Python 边缘）

| 服务 | 端口 | 职责 | 成熟度 |
|---|---|---|---|
| aiot-gateway | 8080 | 统一入口/路由/鉴权 | 稳定 |
| aiot-device-service | 8081 | 设备全生命周期 | 稳定 |
| aiot-auth-service | 8082 | 账号/鉴权/JWT | 稳定 |
| aiot-home-service | 8083 | 家庭/房间 | 稳定 |
| aiot-rule-engine | 8084 | 事件消费/AI 诊断/规则/DLQ | 稳定 |
| aiot-mqtt-adapter | 8085 | MQTT 接入 | 演进中 |
| aiot-data-parser | 8086 | 数据清洗 | 演进中 |
| aiot-shadow-service | 8087 | 设备影子 | 演进中 |
| aiot-admin-web | — | Vue3 管理后台 | 演进中 |
| edge/aiot-edge-agent | — | Python 边缘诊断 Agent | 演进中 |

## 2. 外部系统（中间件/基础设施）

| 系统 | 版本 | 用途 |
|---|---|---|
| EMQX | 5.3 | MQTT broker，auth webhook → auth-service |
| MySQL | 8.0 | 业务库 + Outbox |
| Redis | 7.0 | Streams（`aiot:stream:device-event`） |
| Nacos | 2.3 | 注册/配置中心 |
| Ollama | qwen2.5:1.5b | CPU 推理（AI 诊断） |
| 观测栈 | Prometheus/Alertmanager/Loki/Promtail/Tempo/Grafana | 指标/日志/链路 |

## 3. AI 诊断全链路（核心价值链路）

> 双事件生产路径并存：EMQX 上下线 webhook 直连 auth-service，消息转发走 mqtt-adapter→data-parser；二者均写入同一 `aiot:stream:device-event`。

```
设备/Edge-Agent ──(MQTT)──> EMQX
                              │
              ┌───────────────┴────────────────┐
              ▼ (上下线 webhook)                 ▼ (消息转发)
         Auth-Service                     MQTT Adapter ──> Data Parser
              │                                    │
              └──────────────┬─────────────────────┘
                             ▼
              Redis Stream (aiot:stream:device-event)
                             │
                             ▼
              Rule-Engine 消费（重试3次 + DLQ + ACK）
                             │
                             ▼
              AI 诊断: fallback(确定性兜底) ──异步 refine──> Cloud AI (Ollama)
                             │                                    │
                             │                          AiSchemaValidator 归一化
                             ▼                                    │
                        持久化诊断记录 <────────────────────────────┘
                             │
                             ▼
              Feedback ──> Case Library (案例库) ──回喂──> 下次推理
```

## 4. 关键数据流约束
- Outbox 双写一致性（MySQL Outbox 模式），含 `dead_letter` 死信列
- Redis Stream 积压直接影响 AI 诊断时效性，需消费者积压监控
- AI 诊断结果必须通过 `AiSchemaValidator`（schema + 枚举归一化）
- 数据面门禁：上下文完整度 <4/8 时拒绝调 LLM，保持确定性兜底（`AiDiagnosisService.contextCompletenessScore`）
