# AIoT 后端项目开发任务与人员分配清单 (Project Status)

> **项目状态**: 🚧 MVP 阶段架构与骨架已完成，进入“从 1 到 100”的核心业务全功能研发阶段。
> **目标**: 将当前基于内存的 Mock 服务，落地为真正基于高可用中间件（MySQL, Redis, EMQX, TDengine）的分布式微服务。

---

## 🎯 里程碑 1：持久化层与设备基础中心落地 (Persistence & Core Service)
**目标**：打通 `aiot-device-service` 与 MySQL / Redis 的真实连接，废弃 ConcurrentHashMap 的 Mock 机制。

### 📌 分配团队：**后端业务研发组 (Backend Team)** & **DBA**

*   [x] **任务 1.1：数据库 DDL 设计与建表 (DBA / Backend)**
    *   **内容**：根据架构文档设计并执行 `tenant` (租户表)、`product` (产品表，含 JSON 类型的 `thing_model`)、`device` (设备表) 和 `device_credential` (设备凭证表) 的建表 SQL。
    *   **规范**：严格遵循《全局开发规范》中的软删除 (`is_deleted`) 和审计字段 (`created_at`, `updated_at`)。
*   [x] **任务 1.2：引入 MyBatis-Plus/Flex 框架 (Backend)**
    *   **内容**：在 `aiot-cloud-parent` 和 `aiot-device-service` 中引入持久化层框架依赖，配置多数据源与代码生成器。
*   [x] **任务 1.3：设备与产品核心 API 真实落地 (Backend)**
    *   **内容**：重构 `DeviceServiceImpl`，实现真实的注册逻辑（写入 MySQL）和在线状态查询（从 Redis 获取）。
*   [x] **任务 1.4：设备影子 (Device Shadow) 缓存结构落地 (Backend)**
    *   **内容**：在 `aiot-shadow-service` 中实现 Redis 的 Hash 结构操作，维护设备的 `reported` (上报状态) 和 `desired` (期望状态)。

---

## 🎯 里程碑 2：设备接入网关与安全校验 (Access & Security)
**目标**：真实打通设备端 (Device) 通过 MQTT 协议连接到 EMQX Broker，并与微服务体系联动鉴权。

### 📌 分配团队：**中间件团队 (Middleware Team)** & **安全研发组 (Security Team)**

*   [x] **任务 2.1：EMQX Broker 部署与调优 (Middleware)**
    *   **内容**：配置 EMQX 的集群模式，开启 Webhook 和 HTTP Auth 认证插件。
*   [x] **任务 2.2：设备接入鉴权接口开发 (Security)**
    *   **内容**：在 `aiot-auth-service` 中开发供 EMQX 回调的 HTTP Auth 接口。实现算法：校验 `Username=DeviceId` 和 `Password=HMAC_SHA256(DeviceId, DeviceSecret)`。
*   [x] **任务 2.3：设备上下线事件流转 (Middleware)**
    *   **内容**：订阅 EMQX 的系统 Topic (`$SYS/brokers/+/clients/+/connected` 等)，或者通过 Webhook 将上下线事件推送到 Kafka。
*   [x] **任务 2.4：设备状态实时更新服务 (Backend)**
    *   **内容**：消费上下线 Kafka 事件，更新 Redis 中设备的在线状态，并写入设备活动日志。

---

## 🎯 里程碑 3：数据解析与规则引擎流计算 (Data Parsing & Rule Engine)
**目标**：设备上报的遥测数据 (Telemetry) 能被成功解析、持久化，并触发告警规则。

### 📌 分配团队：**大数据/流计算组 (Data & Stream Team)** & **后端研发组 (Backend)**

*   [ ] **任务 3.1：MQTT 协议适配与数据桥接 (Middleware)**
    *   **内容**：在 `aiot-mqtt-adapter` 中，将设备发布到 `/sys/{productId}/{deviceName}/thing/event/property/post` 的 Payload，完整转发至 Kafka Raw Topic。
*   [ ] **任务 3.2：物模型解析服务开发 (Data Team)**
    *   **内容**：在 `aiot-data-parser` 中，消费 Kafka Raw Topic，结合缓存中的产品物模型 (TSL)，将二进制/JSON 转化为标准时序结构，推送到 Kafka Clean Topic。
*   [ ] **任务 3.3：时序数据库 (TDengine) 写入开发 (Data Team)**
    *   **内容**：对接 TDengine，自动为设备创建超级表 (Super Table) 的子表，并将遥测数据高频写入。
*   [ ] **任务 3.4：基础规则引擎触发 (Backend / Stream Team)**
    *   **内容**：在 `aiot-rule-engine` 中，消费清洗后的数据，支持配置类似 `if temperature > 50 then send alert` 的阈值规则，并推送告警信息到 App 端。

---

## 🎯 里程碑 4：指令下发与全链路测试 (Command & E2E Testing)
**目标**：完成云端 API 到边缘设备的控制下发，完成端到端闭环。

### 📌 分配团队：**测试团队 (QA)** & **全栈研发 (Fullstack)**

*   [ ] **任务 4.1：云端指令下发通道建立 (Backend)**
    *   **内容**：`DeviceService` 接收到 API 控制指令后，通过 MQTT 客户端或 EMQX HTTP API 将 Payload 下发至 `/sys/{productId}/{deviceName}/thing/service/property/set`。
*   [ ] **任务 4.2：指令回执与影子状态同步 (Backend)**
    *   **内容**：处理设备的执行 ACK 回执，更新 Redis 设备影子的 `reported` 状态，并通知调用方。
*   [ ] **任务 4.3：端到端 (E2E) 自动化集成测试 (QA)**
    *   **内容**：编写自动化测试脚本，模拟 1000 个设备并发接入、上报数据，以及并发指令下发，输出压测与正确性报告。
