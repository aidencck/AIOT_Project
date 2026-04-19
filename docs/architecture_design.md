# AIoT 后端架构设计文档

## 1. 架构设计核心定义

在进入具体模块划分前，我们需要明确本次 AIoT 后端架构的四大基石：

*   **前提 (Premise)**：构建一个高可用、可扩展的 AIoT 云端平台，能够支撑海量设备的并发连接、海量时序数据的实时接入，并提供基于 AI 的设备分析预警与反向实时控制能力。
*   **约束 (Constraints)**：
    *   **技术栈**：严格基于 Java Spring Boot 构建微服务，使用 Spring Cloud Alibaba (Nacos, Sentinel等) 作为服务治理底座。
    *   **并发要求**：接入层必须能支撑百万级设备的 MQTT/CoAP 并发保活；控制指令下发延迟须在百毫秒级。
    *   **安全合规**：设备接入须进行 TLS 加密及一机一密双向认证，业务 API 采用 RBAC 权限控制。
*   **边界 (Boundaries)**：
    *   **属于后端范围**：设备网关集群对接、物模型 (Thing Model) 管理、规则引擎、数据处理流、AI 推理调度、应用层 OpenAPI 暴露。
    *   **不属于后端范围**：边缘端设备固件 (Firmware) 开发、App/Web 端前端 UI 渲染（但后端需提供完善的契约 API）。
*   **终局 (Endgame)**：形成一套云原生、多租户的 AIoT 基础设施，不仅能实现基础的“物联”（连接与数据采集），更能实现真正的“智联”（通过流计算和 AI 模型实现设备的自主决策与预测性维护）。

---

## 2. 产品与系统架构图 (Component Architecture)

基于 Spring Cloud 的微服务体系，我们将架构划分为应用层、接入层、核心服务层、数据与 AI 层以及存储层。

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#00ff00', 'primaryBorderColor': '#00ff00', 'lineColor': '#00ff00', 'secondaryColor': '#1a1a1a', 'tertiaryColor': '#2a2a2a'}}}%%
graph TD
classDef default fill:#000,stroke:#00ff00,stroke-width:2px,color:#00ff00,font-family:monospace;
classDef layer fill:#111,stroke:#00aa00,stroke-width:2px,color:#00aa00,font-style:bold;

subgraph AppLayer ["应用层 (Application Layer)"]
    A1["Web Dashboard"]
    A2["Mobile App"]
    A3["OpenAPI / 3rd Party"]
end

subgraph AccessLayer ["接入层 (Access Layer)"]
    B1["Spring Cloud Gateway (HTTP/REST)"]
    B2["MQTT Broker (EMQX/HiveMQ集群)"]
    B3["CoAP/LwM2M Server"]
end

subgraph CoreServices ["核心微服务层 (Spring Cloud)"]
    C1["Auth Service (认证鉴权)"]
    C2["Device Service (物模型与设备管理)"]
    C3["Rule Engine (规则引擎与路由)"]
    C4["OTA Service (固件升级)"]
    C5["Shadow Service (设备影子)"]
end

subgraph AIDataLayer ["数据与AI引擎层 (Data & AI Layer)"]
    D1["Apache Kafka (消息总线)"]
    D2["Apache Flink (流计算与CEP)"]
    D3["AI Inference (模型推理服务)"]
end

subgraph StorageLayer ["存储层 (Storage Layer)"]
    E1["MySQL (业务数据与元数据)"]
    E2["Redis (会话缓存与分布式锁)"]
    E3["TDengine/InfluxDB (时序数据)"]
    E4["MinIO (固件与对象存储)"]
end

AppLayer --> B1
B2 --> D1
B3 --> D1
B1 --> CoreServices
CoreServices --> D1
D1 --> D2
D2 --> D3
D2 --> StorageLayer
CoreServices --> StorageLayer

class AppLayer,AccessLayer,CoreServices,AIDataLayer,StorageLayer layer;
```

---

## 3. 核心业务流程图 (Business Flow)

此图展示了设备从接入、数据上报，到经过规则引擎触发 AI 分析，最终推送预警到业务端的核心链路。

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#00ff00', 'primaryBorderColor': '#00ff00', 'lineColor': '#00ff00'}}}%%
graph LR
classDef default fill:#000,stroke:#00ff00,stroke-width:2px,color:#00ff00,font-family:monospace;

D["IoT Device"] -->|"1. Request Auth"| Auth["Auth Service"]
Auth -->|"2. Return Token/Cert"| D
D -->|"3. Publish Telemetry"| Broker["MQTT Broker"]
Broker -->|"4. Route via Kafka"| Rule["Rule Engine"]
Rule -->|"5. Trigger Condition"| AI["AI Inference"]
AI -->|"6. Generate Alert"| Alert["Alert Service"]
Alert -->|"7. Push Notification"| App["Mobile App"]
```

---

## 4. 数据流向与处理架构图 (Data Flow)

AIoT 系统的核心在于数据的流转。这里我们采用典型的 Lambda/Kappa 变种架构，利用 Flink 进行实时流计算清洗与分发。

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#00ff00', 'primaryBorderColor': '#00ff00', 'lineColor': '#00ff00'}}}%%
graph TD
classDef default fill:#000,stroke:#00ff00,stroke-width:2px,color:#00ff00,font-family:monospace;

Raw["Raw Payload (JSON/Protobuf)"] -->|"MQTT Topic"| Broker["EMQX Broker"]
Broker -->|"Rule Forward"| Kafka["Kafka (Raw Topic)"]
Kafka -->|"Consume"| Parser["Data Parser (Spring Boot)"]
Parser -->|"Clean/Transform"| KafkaClean["Kafka (Clean Topic)"]
KafkaClean -->|"Stream Process"| Flink["Apache Flink"]
Flink -->|"Time-series Sink"| TSDB["TDengine (时序库)"]
Flink -->|"Event Sink"| ES["ElasticSearch (日志/事件库)"]
Flink -->|"Feature Extraction"| AIModel["AI Model Serving"]
AIModel -->|"Insights/Tags"| MySQL["MySQL (业务数据库)"]
```

---

## 5. 核心交互时序图 (Core Interaction Sequence)

以“App 端下发控制指令给设备，并同步设备影子状态”这一高频场景为例：

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#00ff00', 'primaryBorderColor': '#00ff00', 'lineColor': '#00ff00', 'actorBkg': '#000', 'actorBorder': '#00ff00', 'actorTextColor': '#00ff00', 'signalColor': '#00ff00', 'signalTextColor': '#00ff00', 'noteBkg': '#111', 'noteBorderColor': '#00ff00', 'noteTextColor': '#00ff00'}}}%%
sequenceDiagram
autonumber
participant App as "Mobile App"
participant API as "Spring Cloud Gateway"
participant Cmd as "Command Service"
participant Shadow as "Device Shadow"
participant Broker as "MQTT Broker"
participant Dev as "IoT Device"

App->>API: "POST /api/v1/device/{id}/cmd"
API->>Cmd: "Route Request"
Cmd->>Shadow: "Check Online Status & Desired State"
Shadow-->>Cmd: "Status: Online"
Cmd->>Broker: "Publish to cmd/topic"
Broker->>Dev: "Push Command"
Dev-->>Broker: "ACK / Result"
Broker-->>Shadow: "Update Reported State"
Shadow-->>Cmd: "State Synced"
Cmd-->>API: "HTTP 200 OK"
API-->>App: "Command Executed"
```
