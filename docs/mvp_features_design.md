# AIoT 后端 MVP (最小可行性产品) 核心功能梳理

## 1. 架构定义与 MVP 边界 (Premise & Boundaries)

在庞大的 AIoT 体系中，为了快速验证商业模式并打通端到端链路，我们需要定义一个 **MVP (Minimum Viable Product, 最小可行性产品)**。

*   **Premise (前提)**：实现设备从出厂、激活、数据上报到云端，以及云端指令下发到设备的完整双向通信闭环。
*   **Boundaries (边界)**：
    *   **包含**：设备基础接入鉴权、物模型解析、设备在线状态维护、简单的属性数据存储、基于 HTTP 的云端控制 API。
    *   **暂不包含 (后续迭代)**：复杂的 AI 预警模型、固件 OTA 升级、边缘计算网关协同、多级租户权限等。
*   **Endgame (终局目标)**：构建一个高内聚低耦合的基础 IoT 引擎，确保“设备连得上、数据存得下、指令下得去”。

---

## 2. 最小闭环核心功能模块拆解

为了实现上述目标，MVP 版本必须包含以下 4 个核心功能模块：

### 2.1 产品与设备管理 (Device Management)
这是 IoT 平台的基石，负责“物理实体”的数字化注册。
*   **产品管理**：创建产品，定义产品的物模型 (TSL，即设备具备哪些属性、事件和服务)。
*   **设备注册**：在指定产品下注册具体设备，生成设备唯一的凭证（如 DeviceKey 和 DeviceSecret，即一机一密三元组）。
*   **设备生命周期管理**：查看设备详情，支持设备的禁用/启用。

### 2.2 接入与安全认证 (Access & Auth)
解决设备“如何安全连上云端”的问题。
*   **MQTT 协议接入**：设备通过 MQTT 协议连接云端 Broker（如 EMQX）。
*   **设备鉴权 (Auth)**：云端通过 Hook 或鉴权接口，校验设备连接时携带的 ClientID、Username (通常为 DeviceID)、Password (通常为基于 DeviceSecret 的签名哈希)。

### 2.3 设备上下线与影子管理 (Shadow & Status)
解决“设备当前在哪里、是什么状态”的问题。
*   **在线状态维系**：监听 MQTT 的 `client.connected` 和 `client.disconnected` 系统事件，在 Redis 和 MySQL 中实时更新设备在线/离线状态。
*   **属性上报处理 (Telemetry)**：设备通过指定 Topic 上报数据，云端解析 JSON Payload，记录最新值。
*   **期望状态存储 (Desired State)**：云端应用想改变设备状态时，先写入影子，等设备在线时再同步给设备。

### 2.4 数据存储与查询 (Data Persistence)
*   **实时快照缓存**：将设备的最新属性数据缓存在 Redis，供前端 App/Web 快速查询显示。
*   **历史数据落库**：将解析后的遥测数据写入时序数据库（TDengine）或 MySQL，支持简单的历史趋势图查询。

---

## 3. MVP 最小核心闭环流程 (Business Flow)

下面这个图展示了 MVP 阶段设备端与云端交互的**最小业务闭环**：

```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#0a0a0a', 'primaryTextColor': '#00ff00', 'primaryBorderColor': '#00ff00', 'lineColor': '#00ff00', 'secondaryColor': '#1a1a1a', 'tertiaryColor': '#2a2a2a'}}}%%
graph TD
classDef default fill:#000,stroke:#00ff00,stroke-width:2px,color:#00ff00,font-family:monospace;

subgraph 1. 注册阶段
    A["Web端创建产品与物模型"] --> B["注册设备生成凭证(DeviceSecret)"]
end

subgraph 2. 接入阶段
    C["IoT设备发起MQTT连接"] --> D["Auth服务校验密码签名"]
    D -- "认证通过" --> E["更新设备状态为在线(Redis/DB)"]
end

subgraph 3. 数据上报闭环 (上行)
    F["IoT设备上报属性 (MQTT Topic)"] --> G["Adapter/Parser服务解析JSON"]
    G --> H["更新设备最新影子缓存(Redis)"]
    G --> I["历史遥测数据持久化(TSDB/DB)"]
end

subgraph 4. 指令下发闭环 (下行)
    J["App/Web调用控制API"] --> K["Device服务校验权限并生成Payload"]
    K --> L["MQTT Broker下发指令给设备"]
    L --> M["设备执行并ACK确认"]
end

B -. "烧录凭证" .-> C
E -. "准备就绪" .-> F
H -. "前端轮询查询" .-> J
```

---

## 4. MVP 阶段的 API 契约清单 (最小集)

为支撑上述流程，我们需要优先实现以下几个核心 RESTful API：

1.  `POST /api/v1/products` - 创建产品及物模型
2.  `POST /api/v1/devices` - 注册设备，返回认证凭证
3.  `GET /api/v1/devices/{deviceId}/status` - 获取设备在线状态与最新影子数据
4.  `POST /api/v1/devices/{deviceId}/commands` - 下发控制指令给设备
5.  `GET /api/v1/devices/{deviceId}/telemetry` - 查询设备的历史遥测数据