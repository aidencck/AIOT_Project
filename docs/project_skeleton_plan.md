# AIoT 后端 Spring Cloud Alibaba 工程骨架规划

## 1. 工程模块划分 (Maven Multi-Module)

基于我们在第一阶段设计的核心架构，我们采用 Maven 多模块工程来管理整个 AIoT 微服务体系。

### 根目录: `aiot-cloud-parent`
作为父工程，统一管理所有子模块的依赖版本 (Dependency Management)，包含 Spring Boot, Spring Cloud, Spring Cloud Alibaba 的版本控制。

### 1.1 基础设施层 (Infrastructure Layer)
*   **`aiot-gateway`**: 统一 API 网关，基于 Spring Cloud Gateway，负责路由转发、限流降级（Sentinel）、统一跨域处理。
*   **`aiot-common`**: 公共组件包，包含全局异常处理、统一返回值封装、公共 Utils 工具类、常量定义等，被其他业务模块引用。

### 1.2 核心业务服务层 (Core Services Layer)
*   **`aiot-auth-service`**: 认证鉴权服务，基于 Spring Security + JWT（或 OAuth2），负责租户/用户登录以及设备一机一密验证。
*   **`aiot-device-service`**: 设备中心服务，管理产品、物模型定义、设备生命周期（注册、激活、上下线）。
*   **`aiot-shadow-service`**: 影子服务，负责缓存和同步设备的最新状态（Desired vs Reported）。
*   **`aiot-rule-engine`**: 规则引擎服务，负责配置数据流转规则，对接 Kafka 消费原始数据，并触发下游告警或流转到时序库。

### 1.3 接入与数据处理层 (Access & Data Layer)
*   **`aiot-mqtt-adapter`**: 协议适配层，如果使用外部 Broker（如 EMQX），此模块作为 Webhook 接收端或 Kafka 消费端，将 MQTT Payload 转换为内部标准模型。
*   **`aiot-data-parser`**: 数据解析服务，负责将设备上报的各种格式（JSON, Protobuf, Hex）基于物模型解析为标准时序数据。

---

## 2. 核心技术栈与版本选型

为保证架构的稳定性和前瞻性，建议采用以下版本组合（以 Spring Boot 3.x 为主）：

| 组件 | 技术选型 | 备注说明 |
| :--- | :--- | :--- |
| 核心框架 | Spring Boot 3.x | JDK 17+ 支持，Native Image 友好 |
| 微服务底座 | Spring Cloud 2022.x | 微服务核心组件 |
| 服务治理 | Spring Cloud Alibaba 2022.x | 包含 Nacos, Sentinel 等 |
| 注册与配置中心 | Nacos 2.x | 统一管理服务发现与动态配置 |
| API 网关 | Spring Cloud Gateway | 高性能异步非阻塞网关 |
| 熔断限流 | Sentinel | 保障网关和核心接口高可用 |
| 消息队列 | Apache Kafka | 支撑高吞吐遥测数据接入 |
| 数据库访问 | MyBatis-Plus / MyBatis-Flex | 支持多租户、动态数据源、分库分表 |

---

## 3. 基础依赖管理 (Parent POM 骨架)

父工程 `pom.xml` 的核心依赖管理配置示例：

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
    <spring-cloud.version>2023.0.0</spring-cloud.version>
    <spring-cloud-alibaba.version>2022.0.0.0-RC2</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Cloud -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Cloud Alibaba -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 4. 后续实施路径规划 (Implementation Roadmap)

架构设计完成后，正式编码阶段的推荐路径：

1.  **基础设施搭建**：
    *   部署 MySQL, Redis, Kafka, Nacos 单机/集群环境。
    *   创建 `aiot-cloud-parent` 及各个子 Module，完成基础的 POM 依赖配置。
2.  **公共组件与网关**：
    *   完善 `aiot-common`，统一 API 返回格式、全局异常拦截器。
    *   启动 `aiot-gateway`，配置 Nacos 动态路由，确保请求能正确转发。
3.  **设备核心领域开发 (Device Service)**：
    *   实现产品与物模型（Thing Model）的 CRUD 接口。
    *   实现设备注册与凭证生成的业务逻辑。
4.  **设备接入与协议适配 (MQTT Adapter)**：
    *   部署 EMQX Broker。
    *   编写 `aiot-mqtt-adapter` 监听 Kafka 或配置 EMQX Webhook，接收设备上报报文。
5.  **数据解析与存储 (Data Parser & Shadow)**：
    *   实现 Payload 解析逻辑，将数据存入 Redis (影子) 和 TDengine (时序库)。
