---
name: "aiot-system-boundary"
description: "分析 AIoT 系统与外部系统（EMQX/MySQL/Redis/Nacos/Ollama/可观测性栈）的边界关系、依赖矩阵、约束与改进建议。当用户询问系统依赖、边界分析、架构关系时触发。"
---

# AIoT 系统边界与依赖分析（System Boundary）

## 定位
只读边界/依赖分析，绑定项目 Agent [`system-boundary-analyzer`](../agents/system-boundary-analyzer.md)。产出系统清单、依赖矩阵、逐边界六维分析，识别依赖声明 vs 实现的 drift 与 top risks。

## 事实源（权威依赖声明，先读）
- `compose/compose.base.yml`（depends_on / env / ports）
- `k8s/helm/*.yaml`（services + infra-emqx/mysql/nacos/redis + observability）
- `docs/architecture/*.md`（EMQX_ARCHITECTURE_AND_CORE_SEQUENCES、OBSERVABILITY_DATA_FLOW、database-entities-and-relations、microservice-communication-governance）

## 内部系统（本项目）
- Java：aiot-gateway(8080)、aiot-auth-service(8082)、aiot-device-service(8081)、aiot-home-service(8083)、aiot-rule-engine(8084)、aiot-mqtt-adapter(8085)、aiot-data-parser(8086)、aiot-shadow-service(8087)、aiot-common、aiot-db-migrator
- Web：aiot-admin-web（Vue3 + nginx）
- Python：edge/aiot-edge-agent、edge/device-simulator、training（SFT pipeline）

## 外部系统（中间件/基础设施）
- EMQX 5.3（MQTT broker，auth webhook → auth-service）
- MySQL 8.0、Redis 7.0（Streams）、Nacos 2.3（registry/config）
- Ollama（LLM qwen2.5:1.5b，CPU 推理）
- Observability：Prometheus、Alertmanager、Loki、Promtail、Tempo、Grafana、cAdvisor、exporters

## 执行门禁（硬性）
1. 委托 `system-boundary-analyzer` Agent（read-only），回传格式：`结论 / 证据 / 改动建议 / 阻塞项`
2. 逐关系覆盖六维：`关系 / 边界 / 约束 / 价值 / 优缺点 / 改进建议`
3. 末尾必须给：top risks + 优先级改进 backlog

## 验收口径
- 系统清单（Internal vs External，含 ports/protocols）+ 依赖矩阵（source→target→protocol→purpose）
- 每边界六维分析 6 项齐全（核心交付物）
- 每条关系 cite 依赖声明（compose/k8s/架构文档）与源码路径；区分 verified 与 inference
- 外部系统能力超出 repo 证据时，显式声明假设
