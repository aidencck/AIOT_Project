# 当前架构（current）

## 一句话概览

AIOT-java 当前是 Maven 多模块 + Spring Boot 微服务架构，主要运行面为 `Gateway + Auth + Device + Home`，依赖 `Nacos + MySQL + Redis + EMQX`，以 Docker Compose 单节点部署为主。

## 模块与职责

- `aiot-gateway`：统一流量入口与路由分发
- `aiot-auth-service`：设备认证、EMQX Webhook 事件接收、在线状态处理
- `aiot-device-service`：产品、设备、设备影子、配网令牌能力
- `aiot-home-service`：用户、家庭、房间及家庭角色能力
- `aiot-common`：统一响应/异常/常量等公共组件

## 部署拓扑（当前）

- 基础依赖：MySQL、Redis、Nacos、EMQX
- 容器化服务：`aiot-gateway`、`aiot-device-service`、`aiot-auth-service`
- 本地开发常见方式：中间件容器化 + IDE 启动其他服务（如 `aiot-home-service`）

## 关键数据流

1. 设备认证链路  
设备通过 EMQX 触发 `auth-service` 的 `/api/v1/emqx/auth`，认证通过后允许连接。

2. 设备上下线链路  
EMQX 回调 `auth-service` 的 `/api/v1/emqx/webhook`，服务完成签名校验后更新 Redis 在线状态，并回调设备域刷新设备状态。

3. 家庭权限链路  
设备域在执行增删改查前，通过家庭域权限接口校验用户是否拥有目标家庭的最小角色权限。

## 状态边界

- `current`：网关、设备、认证、家庭主流程可运行
- `in-progress`：影子服务、规则引擎、MQTT 适配器、数据解析器独立能力仍在骨架阶段
- `planned`：文档中涉及的 Flink/Kafka/TSDB/OTA/AI 推理为目标态，不应视为已完整落地
