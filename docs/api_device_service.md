# AIoT Device Service API 文档

该服务主要处理 `设备与配网域` 的相关业务，包含产品物模型定义、设备拓扑关联（网关与子设备）、一键配网及设备影子管理。

## 1. 产品管理 (Product Management)

### 1.1 创建产品
- **URL**: `/api/v1/products`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "name": "智能温湿度传感器",
    "description": "基于 Zigbee 协议的温湿度传感器",
    "nodeType": 3, 
    "thingModelJson": "{\"properties\":[{\"identifier\":\"temperature\",\"dataType\":\"double\"}]}"
  }
  ```
  *(注：`nodeType`: 1-直连设备, 2-网关, 3-网关子设备)*

### 1.2 查询产品详情
- **URL**: `/api/v1/products/{productKey}`
- **Method**: `GET`

### 1.3 更新物模型
- **URL**: `/api/v1/products/{productKey}/thing-model`
- **Method**: `PUT`
- **Body**: (直接传递 JSON 字符串)

---

## 2. 设备拓扑与配网 (Device Provisioning & Topology)

### 2.1 APP 端获取配网 Token（推荐）
- **URL**: `/api/v1/provision/token`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "productKey": "PK_XXX",
    "deviceName": "Sensor1",
    "homeId": "home_123"
  }
  ```
- **说明**: 接口返回一个临时的 UUID Token，有效期 10 分钟，App 可通过蓝牙或局域网将其下发给设备。

### 2.1.1 兼容接口（待迁移）
- **URL**: `/api/v1/provision/token?productKey=PK_XXX&deviceName=Sensor1&homeId=home_123`
- **Method**: `GET`
- **说明**: 仅兼容历史调用，建议迁移至 `POST /api/v1/provision/token`。

### 2.2 设备端换取密钥 (Exchange Token)
- **URL**: `/api/v1/provision/exchange`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "productKey": "PK_XXX",
    "deviceName": "Sensor1",
    "provisionToken": "3d5f..."
  }
  ```
- **Response**:
  ```json
  {
    "deviceId": "160...",
    "deviceSecret": "8c4...",
    "mqttHost": "mqtt.aiot.com",
    "mqttPort": 1883
  }
  ```

---

## 3. 设备管理 (Device Management)

### 3.1 预创建/手动注册设备
- **URL**: `/api/v1/devices`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "productKey": "PK_XXX",
    "deviceName": "MySensor",
    "homeId": "home_123",
    "gatewayId": "gw_456" 
  }
  ```
  *(注：若 `nodeType` 为 3，则 `gatewayId` 必填，且后台会校验父设备必须是网关类型)*

### 3.2 删除设备
- **URL**: `/api/v1/devices/{deviceId}`
- **Method**: `DELETE`
- **说明**: 级联删除设备凭证，并解绑关联的子设备。

---

## 4. 设备影子 (Device Shadow)

### 4.1 获取设备完整影子
- **URL**: `/api/v1/devices/{deviceId}/shadow`
- **Method**: `GET`
- **说明**: 返回 `reported` (设备上报) 和 `desired` (云端期望) 两个 JSON Map。

### 4.2 更新期望状态 (云端下发)
- **URL**: `/api/v1/devices/{deviceId}/shadow/desired`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "target_temperature": 25.5
  }
  ```

### 4.3 更新上报状态 (设备上报)
- **URL**: `/api/v1/devices/{deviceId}/shadow/reported`
- **Method**: `POST`
