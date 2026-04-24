# 能力矩阵

## 当前状态总览

| 领域 | 能力项 | 状态 | 说明 |
| --- | --- | --- | --- |
| 网关 | API 统一入口 | current | `aiot-gateway` 已可运行并注册到 Nacos |
| 认证 | EMQX 设备认证 | current | `/api/v1/emqx/auth` |
| 认证 | EMQX Webhook 处理 | current | `/api/v1/emqx/webhook`，含签名校验 |
| 设备 | 产品管理 | current | 产品创建、查询、物模型更新 |
| 设备 | 设备管理 | current | 设备 CRUD、按家庭分页查询 |
| 设备 | 配网令牌 | current | `POST /api/v1/provision/token`（推荐）、`GET /api/v1/provision/token`（兼容）、`/exchange` |
| 设备 | 设备影子 | current | Shadow 查询、Desired/Reported 更新 |
| 家庭 | 用户与家庭管理 | current | 注册登录、家庭创建/删除/查询 |
| 家庭 | 房间管理 | current | 房间创建/删除/按家庭查询 |
| 跨服务 | 家庭角色鉴权 | current | 设备域调用家庭域权限校验 |
| 跨服务 | 家庭删除补偿解绑设备 | current | 家庭域调用设备域内部补偿接口 |
| 影子服务 | 独立影子服务化 | in-progress | 模块存在，业务能力骨架化 |
| 规则引擎 | 规则执行闭环 | in-progress | 模块存在，核心逻辑待完善 |
| MQTT 适配 | 独立消息适配 | in-progress | 模块存在，场景能力待补齐 |
| 数据解析 | Payload 解析引擎 | in-progress | 模块存在，解析策略待补齐 |
| 时序存储 | TSDB 落地 | planned | 文档提及目标态，代码未完整落地 |
| 实时计算 | Kafka/Flink 链路 | planned | 目标态能力 |

## API 入口（current）

- 设备域：`/api/v1/products`、`/api/v1/devices`、`/api/v1/devices/{deviceId}/shadow`、`/api/v1/provision`
- 认证域：`/api/v1/emqx/auth`、`/api/v1/emqx/webhook`
- 家庭域：`/api/v1/users`、`/api/v1/homes`、`/api/v1/rooms`

## 后续补齐建议

- 给每个 `in-progress` 模块补充“最小可验证用例”（启动、接口、验收标准）
- 在 PR 模板中增加“是否影响能力矩阵”勾选项
- 每次迭代更新本页状态，避免 README 与 docs 口径不一致
