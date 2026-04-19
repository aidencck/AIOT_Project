# AIoT 后端领域驱动设计 (DDD) 与 API 契约定义

## 1. 核心领域划分 (Bounded Contexts)

在 AIoT 平台中，我们将系统划分为以下几个核心限界上下文：

*   **设备领域 (Device Domain)**：核心领域。负责设备生命周期管理、物模型定义、产品分类。
*   **接入领域 (Access Domain)**：支撑领域。负责设备与云端的连接协议（MQTT/HTTP/CoAP）、凭证发放、数据编解码。
*   **影子领域 (Shadow Domain)**：支撑领域。负责维护设备的最新状态缓存（期望状态 vs 报告状态），解决设备离线时的指令下发问题。
*   **规则领域 (Rule Domain)**：核心领域。负责数据流转规则、告警触发条件配置。

---

## 2. 物模型 (Thing Model) 设计规范

物模型是对物理世界中真实设备的数字化抽象，我们将其分为三大核心要素（属性、事件、服务）：

### 2.1 属性 (Properties)
设备运行时的状态信息，通常是可读写的（如温度、开关状态）。

### 2.2 事件 (Events)
设备在运行过程中产生的通知，通常包含特定数据（如高温告警、固件升级完成）。
*   级别：`info` (信息), `alert` (告警), `error` (故障)

### 2.3 服务 (Services)
设备可被调用的复杂功能，通常涉及请求和响应（如重启设备、校准传感器）。

### 2.4 物模型 TSL (Thing Specification Language) JSON 结构示例
我们采用标准化的 JSON 格式定义产品物模型：

```json
{
  "profile": {
    "productId": "PRD-2026-001",
    "productName": "Smart Thermostat V1"
  },
  "properties": [
    {
      "identifier": "temperature",
      "name": "Current Temperature",
      "dataType": {
        "type": "float",
        "specs": {
          "min": "-50.0",
          "max": "100.0",
          "unit": "Celsius"
        }
      },
      "accessMode": "r"
    },
    {
      "identifier": "powerSwitch",
      "name": "Power Switch",
      "dataType": {
        "type": "bool",
        "specs": {
          "0": "Off",
          "1": "On"
        }
      },
      "accessMode": "rw"
    }
  ],
  "events": [
    {
      "identifier": "highTempAlert",
      "name": "High Temperature Alert",
      "type": "alert",
      "outputData": [
        {
          "identifier": "currentTemp",
          "dataType": { "type": "float" }
        }
      ]
    }
  ],
  "services": [
    {
      "identifier": "reboot",
      "name": "Reboot Device",
      "callType": "async",
      "inputData": [
        {
          "identifier": "delaySeconds",
          "dataType": { "type": "int" }
        }
      ],
      "outputData": []
    }
  ]
}
```

---

## 3. 核心 API 契约定义 (RESTful)

针对应用层（App/Web）与设备服务交互，定义以下核心 RESTful API。

### 3.1 产品管理接口

*   **创建产品**
    *   `POST /api/v1/products`
    *   **Body**: `{"name": "...", "description": "...", "nodeType": "direct"}`
    *   **Response**: `201 Created`

*   **更新产品物模型**
    *   `PUT /api/v1/products/{productId}/thing-model`
    *   **Body**: *(见上述 TSL JSON)*
    *   **Response**: `200 OK`

### 3.2 设备生命周期管理接口

*   **注册设备 (生成三元组)**
    *   `POST /api/v1/devices`
    *   **Body**: `{"productId": "...", "deviceName": "Thermostat-01"}`
    *   **Response**: `{"deviceId": "...", "deviceSecret": "..."}`

*   **获取设备详情与在线状态**
    *   `GET /api/v1/devices/{deviceId}`
    *   **Response**: `{"deviceId": "...", "status": "online", "lastActiveTime": "..."}`

### 3.3 设备交互与影子控制接口

*   **获取设备当前属性 (来自设备影子)**
    *   `GET /api/v1/devices/{deviceId}/properties`
    *   **Response**: `{"temperature": 24.5, "powerSwitch": 1}`

*   **设置设备属性 (下发指令)**
    *   `PUT /api/v1/devices/{deviceId}/properties`
    *   **Body**: `{"powerSwitch": 0}`
    *   **Response**: `202 Accepted` *(由于网络延迟，通常返回异步接受状态)*

*   **调用设备服务**
    *   `POST /api/v1/devices/{deviceId}/services/{serviceIdentifier}`
    *   **Body**: `{"delaySeconds": 10}`
    *   **Response**: `{"messageId": "msg-12345", "status": "pending"}`

---

## 4. MQTT 侧设备端 Topic 契约

设备端与云端 Broker 交互的 Topic 规范定义：

*   **属性上报**：
    *   `Topic`: `/sys/{productId}/{deviceName}/thing/event/property/post`
    *   `Payload`: `{"id":"123", "version":"1.0", "params":{"temperature":25.1}, "method":"thing.event.property.post"}`
*   **属性设置 (云端下发)**：
    *   `Topic`: `/sys/{productId}/{deviceName}/thing/service/property/set`
    *   `Payload`: `{"id":"124", "version":"1.0", "params":{"powerSwitch":1}, "method":"thing.service.property.set"}`
*   **事件上报**：
    *   `Topic`: `/sys/{productId}/{deviceName}/thing/event/{eventId}/post`
    *   `Payload`: `{"id":"125", "version":"1.0", "params":{"currentTemp": 45.0}, "method":"thing.event.highTempAlert.post"}`
