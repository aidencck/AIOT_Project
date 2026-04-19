# AIoT Cloud Platform (AIOT-java)

![Build Status](https://github.com/aidencck/AIOT_Project/actions/workflows/ci-cd.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen.svg)
![EMQX](https://img.shields.io/badge/EMQX-5.x-success.svg)

AIOT-java 是一个基于 Java 17 和 Spring Boot 3 构建的工业级物联网（AIoT）云平台后端。该平台旨在提供海量设备的接入、设备管理、物模型（Thing Model）定义、数据流转、设备影子以及安全认证等核心物联网能力。当前场景已针对“智能制冰机”等智能家电产品进行了深度适配。

## 📚 详细文档 (Wiki)

项目的设计理念、架构图、技术选型以及开发规范等详细资料，请参阅 [项目 Wiki](https://github.com/aidencck/AIOT_Project/wiki)。

*   [系统架构设计](https://github.com/aidencck/AIOT_Project/wiki/architecture_design)
*   [制冰机产品定义](https://github.com/aidencck/AIOT_Project/wiki/AIoT_IceMaker_Product_Definition)
*   [API与DDD规范](https://github.com/aidencck/AIOT_Project/wiki/ddd_and_api_contract)
*   [全局开发规范](https://github.com/aidencck/AIOT_Project/wiki/development_standards)

## 🏗️ 核心模块 (Modules)

平台采用微服务架构设计，基于 Maven 多模块进行管理：

*   **`aiot-gateway`**: 统一 API 网关，负责请求路由、鉴权和限流。
*   **`aiot-auth-service`**: 认证服务，负责设备鉴权（如 EMQX Webhook 认证）与用户权限管理。
*   **`aiot-device-service`**: 设备管理服务，提供产品定义、物模型管理、设备生命周期管理以及指令下发。
*   **`aiot-shadow-service`**: 设备影子服务，缓存和同步设备的预期状态（Desired）与报告状态（Reported）。
*   **`aiot-rule-engine`**: 规则引擎，基于条件过滤设备数据，触发告警或数据转发。
*   **`aiot-mqtt-adapter`**: MQTT 适配器，处理与 EMQX Broker 的消息交互和订阅。
*   **`aiot-data-parser`**: 数据解析服务，负责将设备上报的自定义/二进制 Payload 解析为标准 JSON 格式。
*   **`aiot-common`**: 公共组件包，包含统一异常处理、统一响应封装（`Result`）、常量以及通用工具类。

## 🛠️ 技术栈 (Tech Stack)

*   **编程语言**: Java 17
*   **核心框架**: Spring Boot 3.2.x, Spring Cloud Gateway
*   **持久层框架**: MyBatis-Plus 3.5.x
*   **数据库**: MySQL 8.x (业务数据), TDengine (时序数据/遥测数据 - 规划中)
*   **缓存与状态**: Redis
*   **消息中间件/IoT Broker**: EMQX 5.x
*   **容器化与部署**: Docker, Docker Compose
*   **CI/CD**: GitHub Actions

## 🚀 快速开始 (Getting Started)

### 环境依赖
*   JDK 17+
*   Maven 3.8+
*   Docker & Docker Compose (用于启动中间件)

### 1. 启动基础环境 (中间件)
通过 Docker Compose 启动 MySQL, Redis, EMQX 等依赖：
```bash
docker-compose up -d
```

### 2. 编译与构建
在项目根目录下执行 Maven 构建：
```bash
mvn clean install -DskipTests
```

### 3. 启动微服务
你可以通过 IDE 直接运行各个模块的 `*Application.java` 启动类，或者使用根目录提供的启动脚本批量启动服务：
```bash
./start_services.sh
```

## 🔄 CI/CD 流水线

项目内置了完整的 GitHub Actions 流水线（`.github/workflows/ci-cd.yml`），支持全生命周期管理：
1.  **PR 检查**: 在 Pull Request 阶段自动执行代码编译、单元测试以及 Docker 镜像的试构建。
2.  **构建与推送**: 合并到 `main` 分支后，使用矩阵构建（Matrix）并行打包所有微服务，并推送至 GitHub Container Registry (GHCR)。
3.  **自动化部署**: 镜像推送成功后，通过 SSH 自动连接目标服务器拉取新镜像并重启容器。

## 🤝 贡献指南
1.  遵循项目中定义的 [全局开发规范](https://github.com/aidencck/AIOT_Project/wiki/development_standards)。
2.  新功能开发请从 `main` 分支拉取 `feature/*` 分支。
3.  提交代码前确保通过本地的 Checkstyle 和单元测试。
4.  提交 Pull Request 并请求 Code Review。

---
*Built with ❤️ for the AIoT Ecosystem.*