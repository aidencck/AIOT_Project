---
status: current
owner: 架构组
fact_source: 代码 + docker-compose.yml + pom.xml
updated_at: 2026-09-09
---

# 当前架构（current）

## 一句话概览

AIOT-java 当前为 Maven 多模块 + Spring Boot 微服务架构，主运行面为 `Gateway + Auth + Device + Home`，并已在 Compose 中纳入 `Rule/Shadow/MQTT Adapter/Data Parser` 作为同网段运行服务；中间件依赖为 `Nacos + MySQL + Redis + EMQX`。

## 运行面与职责

- `aiot-gateway`：统一流量入口、JWT 鉴权过滤、统一鉴权失败返回
- `aiot-auth-service`：设备认证、EMQX Webhook 验签、在线状态事件接入
- `aiot-device-service`：产品/设备/设备影子/配网令牌/设备域接口与内部补偿接口
- `aiot-home-service`：用户、家庭、房间、家庭角色与设备域权限协同
- `aiot-rule-engine`：事件消费、规则执行、DLQ 与 pending 回收
- `aiot-shadow-service`：影子事件消费、影子态处理骨架、DLQ 与 pending 回收
- `aiot-mqtt-adapter`：MQTT 适配骨架（可运行，能力待产品化）
- `aiot-data-parser`：数据解析骨架（可运行，能力待产品化）
- `aiot-common`：`Result` 统一响应、全局异常处理、TraceId、跨服务内部鉴权组件
- `aiot-db-migrator`：Flyway 数据库迁移模块，负责 schema 版本化迁移（独立 Maven 模块）

## 部署拓扑（当前）

- 基础依赖：MySQL、Redis、Nacos、EMQX
- Compose 已编排服务：
  - 核心服务：`aiot-gateway`、`aiot-auth-service`、`aiot-device-service`、`aiot-home-service`
  - 演进服务：`aiot-rule-engine`、`aiot-shadow-service`、`aiot-mqtt-adapter`、`aiot-data-parser`
- 端口暴露策略：
  - 对宿主机显式暴露：中间件端口 + `gateway:8080`
  - 其余业务服务默认仅在 `aiot-net` 内部互通（通过服务名访问）
- 服务注册策略：
  - `gateway/auth/device/home` 按微服务模式参与服务发现
  - `rule-engine/shadow/mqtt-adapter/data-parser` 当前以 Compose 内部服务协同为主，存在“可运行但未完全产品化”的阶段特征
- 典型开发形态：
  - 形态 A：全量 Compose 联调（网关对外，其他服务走容器内网络）
  - 形态 B：中间件容器化 + 局部服务本地启动（用于断点调试）

## 关键链路

1. 设备接入认证链路  
设备连接 EMQX，EMQX 调用 `auth-service` 的 `/api/v1/emqx/auth`，鉴权通过后放行连接。

2. 设备上下线事件链路  
EMQX 回调 `/api/v1/emqx/webhook`，服务端验签并写入事件流，消费者更新在线状态与相关域状态。

3. 家庭权限校验链路  
设备域在关键写操作前调用家庭域权限接口，按最小角色权限做拦截与放行。

4. 家庭删除补偿链路  
家庭域删除家庭后调用设备域内部补偿接口执行解绑，作为跨服务一致性补偿。

## 可靠性与治理现状

- 统一返回契约：API 与网关异常路径统一为 `Result{code,message,data}`
- 内部接口治理：`/api/v1/internal/**` 走内部令牌校验，减少越权访问风险
- 事件可靠性：Redis Stream 消费链路已具备 `ACK + pending 回收 + DLQ`
- 发布治理：CI/CD 已支持增量构建部署，并补齐单服务回滚工作流与脚本

## 状态边界

- `current`：网关、认证、设备、家庭主流程可运行；Rule/Shadow 事件消费链路可运行；统一响应与异常链路可用
- `in-progress`：`rule-engine`、`shadow-service`、`mqtt-adapter`、`data-parser` 仍处于“可运行但能力未完全产品化”阶段
- `planned`：Flink/Kafka/TSDB/AI 推理为目标态能力，当前不视为完整落地

## 事实来源

- 以 `pom.xml` 模块清单、`docker-compose.yml` 编排、各服务 `application.yml` 与运行脚本为事实基线
