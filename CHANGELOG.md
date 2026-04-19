# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v1.0.0-MVP] - 2026-04-19

### 🎯 目标 (Objectives)
完成 AIoT 云平台 MVP 阶段的核心骨架与基础业务闭环，涵盖设备从注册、安全接入到基础指令下发的完整链路，建立标准化的 CI/CD 流程与运维规范。

### ✨ 新特性 (Features)

#### 1. 核心业务与基础设施
- **架构初始化**：搭建基于 Spring Boot 3 + JDK 17 的多模块架构（`aiot-gateway`, `aiot-device-service`, `aiot-auth-service` 等）。
- **统一响应与异常处理**：在 `aiot-common` 模块实现全局 `Result` 封装和 `GlobalExceptionHandler`。
- **设备管理 (Device Service)**：实现真实接入 MySQL 的产品（Product）与设备（Device）的增删改查业务。
- **设备鉴权 (Auth Service)**：实现供 EMQX 调用的设备接入鉴权接口（HMAC_SHA256）。
- **在线状态与影子 (Shadow)**：实现设备在线状态通过 EMQX Webhook 同步至 Redis；实现 Redis 的设备影子缓存（Desired/Reported）结构操作。
- **云端指令下发**：在设备服务中，实现通过 WebClient 调用 EMQX HTTP API 向设备下发 MQTT 控制指令。
- **超前探索**：在 `aiot-home-service` 中超前实现了用户注册登录（JWT）及家庭/房间管理模型。

#### 2. DevOps 与自动化运维
- **容器化部署 (Docker)**：
  - 构建通用且高度优化的 [Dockerfile](file:///Users/aiden/Projects/AIOT-java/Dockerfile)，支持 Spring Boot 3 `layertools` 分层构建，切换为非特权用户 (`springuser`) 运行以提升安全性。
  - 完善 [docker-compose.yml](file:///Users/aiden/Projects/AIOT-java/docker-compose.yml)，分离中间件（MySQL, Redis, Nacos, EMQX）和业务服务，引入健康检查（Healthcheck）、启动顺序依赖、资源限制（CPU/Memory）和日志滚动截断策略。
- **全生命周期 CI/CD**：编写完整的 GitHub Actions 流水线，实现拉取代码、Maven 构建、Docker 矩阵构建并推送镜像至 GHCR，以及自动部署到服务器。
- **分布式可观测性**：引入全局 `TraceIdFilter`，使用 MDC 实现微服务间请求与日志的链路追踪。
- **安全加固**：通过环境变量 (`${MYSQL_PASSWORD}`) 等形式移除了所有容器编排与配置中的敏感硬编码信息。
- **知识库构建**：整理了架构设计、产品定义和开发规范等完整文档，并同步至 GitHub 远程 Wiki。

### 🛠 待办与遗留 (Known Gaps / Planned for Next Sprint)
- 缺少单元测试（Coverage未达标）及 E2E 端到端自动化验证脚本（已被追踪在 Issue #36）。
- 遥测数据（Telemetry）目前仍为 Mock，尚未对接真实的 TDengine 时序数据库；数据解析模块有待完善（已被追踪在 Issue #35）。
