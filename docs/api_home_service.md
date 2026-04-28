# AIoT Home Service API 文档

该服务主要处理 `空间与用户域` 的相关业务，包含用户鉴权、家庭管理、房间拓扑管理及基于 RBAC 的成员权限控制。

## 1. 用户鉴权 (User Authentication)

### 1.1 注册接口
- **URL**: `/api/v1/users/register`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "phone": "13800138000",
    "password": "password123",
    "nickname": "Aiden"
  }
  ```

### 1.2 登录接口
- **URL**: `/api/v1/users/login`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "phone": "13800138000",
    "password": "password123"
  }
  ```
- **Response**:
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1...",
      "userId": "18034...",
      "nickname": "Aiden"
    }
  }
  ```

---

## 2. 家庭管理 (Home Management)

*注意：以下接口均需在 Header 中携带 `Authorization: Bearer <token>`*

### 2.1 创建家庭
- **URL**: `/api/v1/homes`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "name": "我的智能家",
    "location": "深圳市南山区"
  }
  ```

### 2.2 查询当前用户的家庭列表
- **URL**: `/api/v1/homes`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": [
      {
        "id": "home_123",
        "name": "我的智能家",
        "location": "深圳市南山区",
        "role": 1
      }
    ]
  }
  ```

### 2.3 删除家庭
- **URL**: `/api/v1/homes/{homeId}`
- **Method**: `DELETE`
- **说明**: 仅当用户角色为 `1 (Owner)` 时允许删除。

---

## 3. 房间管理 (Room Management)

### 3.1 创建房间
- **URL**: `/api/v1/rooms`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "homeId": "home_123",
    "name": "主卧"
  }
  ```
- **说明**: 仅当用户角色为 `1 (Owner)` 或 `2 (Admin)` 时允许创建。

### 3.2 查询家庭下的房间列表
- **URL**: `/api/v1/rooms?homeId=home_123`
- **Method**: `GET`

### 3.3 删除房间
- **URL**: `/api/v1/rooms/{roomId}`
- **Method**: `DELETE`
