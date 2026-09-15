# EMQX 架构图与核心链路时序图

## Premise
- 当前仓库中，`EMQX` 主要承担两类职责：`MQTT 设备接入鉴权`、`设备上下线事件入口`。
- 设备业务真相源不在 `EMQX`，而在业务服务与数据层：`device-service`、`auth-service`、`MySQL`、`Redis Stream`。
- 当前实现以 `HTTP Auth/Webhook` 方式与 `EMQX` 对接，接口为 `/api/v1/emqx/auth` 与 `/api/v1/emqx/webhook`。

## Constraints
- `/api/v1/emqx/auth` 必须返回 `200 allow` 或 `401 deny`，保持与 `EMQX HTTP Auth` 契约兼容。
- `/api/v1/emqx/webhook` 必须同时通过 `签名校验`、`时间窗校验`、`防重放校验`。
- 状态事件必须经过 `Redis Stream` 进入下游消费链路，并具备 `重试` 与 `DLQ` 兜底。

## Boundaries
- In Scope：设备配网换凭证、设备连接 `EMQX` 鉴权、上下线事件同步、状态流消费、失败兜底。
- Out of Scope：`EMQX Dashboard` 手工运维流程、用户体系登录/JWT 签发、完整设备遥测消息处理链路。

## Endgame
- 形成“`设备可接入`、`事件可追踪`、`状态可落地`、`异常可兜底`、`验收可量化`”的 `EMQX` 工程闭环。

## 1) 组件架构图
```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "background": "#0b1020",
    "primaryColor": "#111827",
    "primaryTextColor": "#e5e7eb",
    "primaryBorderColor": "#38bdf8",
    "lineColor": "#94a3b8",
    "secondaryColor": "#0f172a",
    "tertiaryColor": "#1e293b",
    "fontFamily": "JetBrains Mono, Menlo, monospace"
  }
}}%%
flowchart LR
  classDef ext fill:#0f172a,stroke:#38bdf8,color:#e2e8f0,stroke-width:1.2px;
  classDef core fill:#111827,stroke:#22d3ee,color:#e2e8f0,stroke-width:1.4px;
  classDef data fill:#1f2937,stroke:#a78bfa,color:#f3e8ff,stroke-width:1.2px;
  classDef risk fill:#3f1d2e,stroke:#fb7185,color:#ffe4e6,stroke-width:1.2px;

  subgraph CLIENT["Client Domain"]
    APP["App / Installer"]:::ext
    DEV["Device"]:::ext
  end

  subgraph EDGE["Access Domain"]
    EMQX["EMQX Broker"]:::ext
    GW["aiot-gateway"]:::core
  end

  subgraph CORE["Core Services"]
    DS["aiot-device-service"]:::core
    AS["aiot-auth-service"]:::core
  end

  subgraph DATA["Data & Event"]
    MYSQL["MySQL: device / device_credential"]:::data
    REDISKV["Redis KV: token / replay / status"]:::data
    STREAM["Redis Stream: aiot:stream:device-event"]:::data
    DLQ["Redis Stream DLQ"]:::risk
  end

  APP -->|"POST /api/v1/provision/token"| GW
  GW --> DS
  DEV -->|"POST /api/v1/provision/exchange"| GW
  GW --> DS
  DS -->|"query/create device"| MYSQL
  DS -->|"token + lock"| REDISKV
  DS -->|"return deviceId/globalDeviceId/deviceSn/authIdentity/deviceSecret/mqttHost/mqttPort"| DEV

  DEV -->|"MQTT CONNECT"| EMQX
  EMQX -->|"POST /api/v1/emqx/auth"| GW
  GW --> AS
  AS -->|"load credential"| MYSQL

  EMQX -->|"POST /api/v1/emqx/webhook"| GW
  GW --> AS
  AS -->|"anti-replay + device-status"| REDISKV
  AS -->|"XADD event"| STREAM
  AS -->|"retry exhausted"| DLQ

  STREAM -->|"consume device status event"| DS
```

## 2) 核心链路一：配网到 MQTT 接入
```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "background": "#0b1020",
    "primaryColor": "#111827",
    "primaryTextColor": "#e5e7eb",
    "primaryBorderColor": "#22d3ee",
    "lineColor": "#94a3b8",
    "fontFamily": "JetBrains Mono, Menlo, monospace"
  }
}}%%
sequenceDiagram
  autonumber
  participant A as "App"
  participant G as "Gateway"
  participant D as "DeviceService"
  participant R as "Redis"
  participant M as "MySQL"
  participant X as "Device"
  participant E as "EMQX"
  participant S as "AuthService"

  A->>G: "POST /api/v1/provision/token"
  G->>D: "issue token"
  D->>R: "SET provision-token TTL"
  D-->>G: "token"
  G-->>A: "token"

  X->>G: "POST /api/v1/provision/exchange"
  G->>D: "exchange token"
  D->>R: "GETDEL provision-token"
  D->>R: "SETNX provision-lock"
  D->>M: "find/create device + credential"
  D-->>G: "deviceId, globalDeviceId, deviceSn, authIdentity, deviceSecret, mqttHost, mqttPort"
  G-->>X: "provision response"

  X->>E: "MQTT CONNECT(clientId, username=authIdentity|globalDeviceId|deviceSn|deviceId, password=HMAC(clientId, deviceSecret))"
  E->>G: "POST /api/v1/emqx/auth"
  G->>S: "authenticateDevice(req)"
  S->>M: "select credential by deviceId"
  S->>S: "verify HMAC"
  alt "auth pass"
    S-->>G: "200 allow"
    G-->>E: "allow"
    E-->>X: "CONNACK success"
  else "auth fail"
    S-->>G: "401 deny"
    G-->>E: "deny"
    E-->>X: "connect rejected"
  end
```

## 3) 核心链路二：上下线事件同步
```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "background": "#0b1020",
    "primaryColor": "#111827",
    "primaryTextColor": "#e5e7eb",
    "primaryBorderColor": "#22d3ee",
    "lineColor": "#94a3b8",
    "fontFamily": "JetBrains Mono, Menlo, monospace"
  }
}}%%
sequenceDiagram
  autonumber
  participant E as "EMQX"
  participant G as "Gateway"
  participant S as "AuthService"
  participant R as "Redis KV"
  participant T as "Redis Stream"
  participant Q as "DLQ"
  participant C as "DeviceService Consumer"
  participant B as "DeviceStatusBufferService"

  E->>G: "POST /api/v1/emqx/webhook"
  G->>S: "verifyWebhookSignature()"
  S->>S: "verify HMAC(action.clientId.username.timestamp)"
  S->>S: "check timestamp skew"
  S->>R: "SETNX replay-key + TTL"
  alt "signature / timestamp / replay invalid"
    S-->>G: "401 invalid signature"
    G-->>E: "401"
  else "request valid"
    S->>S: "handleDeviceStatusWebhook()"
    alt "client.connected"
      S->>R: "SET device-status=online TTL=120s"
      S->>T: "XADD DEVICE_ONLINE"
    else "client.disconnected"
      S->>R: "DEL device-status"
      S->>T: "XADD DEVICE_OFFLINE"
    end
    S-->>G: "200 success"
    G-->>E: "200"
  end

  T-->>C: "consume event"
  C->>C: "map ONLINE->1 / OFFLINE->2"
  C->>B: "enqueue(deviceId, status)"
  alt "consume success"
    C->>T: "XACK"
  else "consume fail"
    C->>Q: "write DLQ"
    alt "DLQ write success"
      C->>T: "XACK"
    else "DLQ write fail"
      C-->>T: "keep pending"
    end
  end
```

## 4) 核心链路三：事件发布失败与降级
```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "background": "#0b1020",
    "primaryColor": "#111827",
    "primaryTextColor": "#e5e7eb",
    "primaryBorderColor": "#38bdf8",
    "lineColor": "#94a3b8",
    "fontFamily": "JetBrains Mono, Menlo, monospace"
  }
}}%%
sequenceDiagram
  autonumber
  participant E as "EMQX"
  participant S as "AuthService"
  participant T as "Redis Stream"
  participant Q as "DLQ"

  E->>S: "webhook connected/disconnected"
  S->>T: "publishWithRetry()"
  alt "first publish success"
    T-->>S: "ok"
  else "first publish fail"
    S->>T: "retry"
    alt "retry success"
      T-->>S: "ok"
    else "retry exhausted"
      S->>Q: "publishToDlq()"
      alt "DLQ publish success"
        Q-->>S: "recorded"
      else "DLQ publish fail"
        S-->>S: "error log + event lost risk"
      end
    end
  end
```

## 5) 实现映射
- `EMQX` 容器编排：`docker-compose.yml`
- `Gateway` 路由 `/api/v1/emqx/**` 与 `/api/v1/provision/**`：`aiot-gateway/src/main/resources/application.yml`
- `EMQX Auth/Webhook` 控制器：`aiot-auth-service/src/main/java/com/aiot/auth/controller/EmqxAuthController.java`
- `鉴权 + 验签 + 防重放 + Stream 发布`：`aiot-auth-service/src/main/java/com/aiot/auth/service/impl/AuthServiceImpl.java`
- `配网换凭证并返回 MQTT 接入点`：`aiot-device-service/src/main/java/com/aiot/device/service/impl/ProvisionServiceImpl.java`
- `设备状态事件消费`：`aiot-device-service/src/main/java/com/aiot/device/listener/DeviceStatusStreamSubscriber.java`

## 6) 验收卡点
- `配网闭环`：`/api/v1/provision/exchange` 返回 `deviceId/globalDeviceId/deviceSn/authIdentity/deviceSecret/mqttHost/mqttPort`
- `鉴权闭环`：`/api/v1/emqx/auth` 成功为 `200 allow`，失败为 `401 deny`
- `安全闭环`：Webhook 同时通过 `签名`、`时间窗`、`防重放`
- `状态闭环`：`connected/disconnected` 进入 `Redis Stream` 并被 `device-service` 消费
- `兜底闭环`：发布失败进入 `DLQ`，消费失败优先写 `DLQ`，否则保留 `pending`
- `性能闭环`：`2xx >= 99.9%`、`5xx <= 0.1%`、`pending` 不持续上升、`DLQ` 增量可解释

## 7) 快速阅读顺序
1. 看 `EmqxAuthController`，确认 `EMQX` 外部契约。
2. 看 `AuthServiceImpl`，确认鉴权、验签、防重放、事件发布主流程。
3. 看 `ProvisionServiceImpl`，确认设备如何拿到 `MQTT` 接入信息。
4. 看 `DeviceStatusStreamSubscriber`，确认状态事件如何在设备域落地。
