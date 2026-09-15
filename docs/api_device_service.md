# AIoT Device Service API 文档

该服务主要处理 `设备与配网域` 的相关业务，包含产品物模型定义、设备拓扑关联（网关与子设备）、一键配网及设备影子管理。

## 0. 统一鉴权说明（网关 + 服务内授权）

- 网关统一校验 JWT，鉴权通过后透传 `X-User-Id`、`X-User-Phone` 到下游服务。
- `aiot-device-service` 与 `aiot-home-service` 均复用 `aiot-common` 的统一请求鉴权拦截器，不再各自维护重复鉴权逻辑。
- 设备服务内资源授权收敛为注解方式：`@RequireHomePermission`，由 AOP 自动完成 homeId 提取与权限校验。
- `/api/v1/provision/exchange` 为设备激活接口，网关白名单放行；其余 `/api/v1/provision/**` 接口默认要求登录用户身份。
- 内部服务调用继续使用 `X-Internal-Token`，仅允许 `/api/v1/internal/**` 路径访问。

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
    "globalDeviceId": "GDID_001",
    "deviceSn": "SN_001",
    "authIdentity": "AUTH_001",
    "provisionToken": "3d5f..."
  }
  ```
- **说明**: `globalDeviceId`、`deviceSn`、`authIdentity` 为外部兼容字段，均为可选；未传时系统会将 `globalDeviceId` 回退为 `deviceId`，`deviceSn` 保持为空，`authIdentity` 按 `authIdentity > deviceSn > globalDeviceId > deviceId` 规则补齐。
- **Response**:
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "deviceId": "160...",
      "globalDeviceId": "GDID_001",
      "deviceSn": "SN_001",
      "authIdentity": "AUTH_001",
      "deviceSecret": "8c4...",
      "mqttHost": "mqtt.aiot.com",
      "mqttPort": 1883
    }
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
    "globalDeviceId": "GDID_001",
    "deviceSn": "SN_001",
    "authIdentity": "AUTH_001",
    "homeId": "home_123",
    "gatewayId": "gw_456" 
  }
  ```
  *(注：若 `nodeType` 为 3，则 `gatewayId` 必填，且后台会校验父设备必须是网关类型)*
- **Response 关键字段**:
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "id": "160...",
      "deviceId": "160...",
      "globalDeviceId": "GDID_001",
      "deviceSn": "SN_001",
      "authIdentity": "AUTH_001",
      "deviceSecret": "8c4..."
    }
  }
  ```
- **说明**: 为兼容历史调用，`id` 字段继续保留；新增 `deviceId/globalDeviceId/deviceSn/authIdentity` 供外部系统逐步迁移使用。

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
