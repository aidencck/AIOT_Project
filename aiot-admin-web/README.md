# AIoT Admin Web

## 方案

- 选型：`SoybeanAdmin` 架构方案
- 落地形态：`Vue 3 + TypeScript + Vite + Pinia + Vue Router`
- 集成边界：前端只调用 `admin-console` 聚合接口，不直连领域服务

## 模块职责

- `src/views/dashboard`：工作台闭环总览
- `src/views/devices`：设备台账
- `src/views/ota`：OTA 治理
- `src/views/ai`：AI 门禁与持久化治理

## 本地运行

1. 启动后端最小联调栈

```bash
./aiotctl admin up
```

2. 启动前端模块

```bash
cd aiot-admin-web
npm install
npm run dev
```

3. 访问

- `http://127.0.0.1:5173`

## 环境变量

- `VITE_API_BASE_URL`：默认留空，开发模式通过 Vite 代理转发到 `http://127.0.0.1:8080`
