# AIoT Cloud Platform (AIOT-java)

![Build Status](https://github.com/aidencck/AIOT_Project/actions/workflows/ci-cd.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen.svg)
![EMQX](https://img.shields.io/badge/EMQX-5.x-success.svg)

AIOT-java 是一个基于 Java 17、Spring Boot 3、Maven 多模块的 AIoT 微服务后端。当前仓库已落地网关、认证、设备、家庭等核心能力，并可通过 Docker Compose 以单节点方式快速部署。

## 项目状态

- 已落地：`aiot-gateway`、`aiot-auth-service`、`aiot-device-service`、`aiot-home-service`
- 骨架阶段：`aiot-shadow-service`、`aiot-rule-engine`、`aiot-mqtt-adapter`、`aiot-data-parser`
- 统一公共模块：`aiot-common`

## 文档与 Wiki

- 文档总览（本地 Wiki）：[`docs/wiki/README.md`](docs/wiki/README.md)
- 现有设计与规范文档：[`docs/`](docs)
- 变更记录：[`CHANGELOG.md`](CHANGELOG.md)

## 核心模块

- `aiot-gateway`：统一网关，负责路由与统一入口。
- `aiot-auth-service`：设备认证（EMQX `/auth`）、Webhook 事件接收、在线状态更新。
- `aiot-device-service`：产品管理、设备管理、设备影子、配网令牌。
- `aiot-home-service`：用户注册登录、家庭与房间管理、家庭成员角色校验。
- `aiot-common`：统一响应、异常、常量和公共工具。

## 技术栈

- 语言与构建：Java 17、Maven
- 核心框架：Spring Boot 3.2.x、Spring Cloud 2023、Spring Cloud Alibaba
- 数据与缓存：MySQL 8、Redis
- 设备接入：EMQX 5.x
- 服务注册：Nacos
- 部署与交付：Docker Compose、GitHub Actions、GHCR

## 快速开始

### 环境依赖

- JDK 17+
- Maven 3.8+
- Docker + Docker Compose

### 方式 A：容器一键部署（推荐）

```bash
./start_services.sh
```

说明：该脚本会从 GHCR 拉取镜像并启动中间件 + 网关/设备/认证服务（默认 `IMAGE_TAG=main`）。

### 方式 B：本地开发（中间件容器 + IDE 启动服务）

1. 启动中间件与容器化服务：

```bash
docker compose up -d
```

2. 本地构建：

```bash
mvn clean install -DskipTests
```

3. 按需在 IDE 启动 `*Application.java`（例如设备/家庭服务）。

## API 文档

- `aiot-gateway`：`http://localhost:8080`
- `aiot-device-service`：`http://localhost:8081/swagger-ui.html`
- `aiot-auth-service`：`http://localhost:8082/swagger-ui.html`
- `aiot-home-service`：本地运行后默认 `http://localhost:8083/swagger-ui.html`

OpenAPI JSON：

- `aiot-device-service`：`http://localhost:8081/v3/api-docs`
- `aiot-auth-service`：`http://localhost:8082/v3/api-docs`
- `aiot-home-service`：`http://localhost:8083/v3/api-docs`

## 服务间通信安全配置

- `AIOT_INTERNAL_TOKEN`：服务间内部调用令牌（`home/auth -> device` 的 `/api/v1/internal/**` 必填）。
- `AIOT_EMQX_WEBHOOK_SECRET`：EMQX webhook 签名密钥（用于防伪造与防重放）。

## 集成测试脚本

- 配网并发 + webhook 重放测试：
  - [`scripts/test_provision_and_webhook.sh`](scripts/test_provision_and_webhook.sh)
- 服务通信契约 + 内部接口鉴权测试：
  - [`scripts/test_service_communication.sh`](scripts/test_service_communication.sh)

## CI/CD

GitHub Actions 工作流位于 [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml)，包含：

1. Pull Request 构建校验（编译、测试、镜像试构建）
2. 主分支镜像构建并推送到 GHCR
3. 远程环境拉取镜像并执行增量部署

## 贡献指南

1. 遵循 [`docs/development_standards.md`](docs/development_standards.md)。
2. 从 `main` 拉取 `feature/*` 分支开发。
3. 提交前确保本地构建通过。
4. 通过 Pull Request 发起评审。
