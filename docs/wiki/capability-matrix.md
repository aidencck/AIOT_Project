# 能力矩阵

最后更新：`2026-04-30`

## 当前状态总览

| 领域 | 能力项 | 状态 | 说明 |
| --- | --- | --- | --- |
| 网关 | API 统一入口 | current | `aiot-gateway` 已可运行并注册到 Nacos |
| 网关 | 鉴权失败统一返回 | current | 未通过鉴权时返回统一 `Result` 结构 JSON |
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
| 跨服务 | 内部接口令牌鉴权 | current | `/api/v1/internal/**` 需携带 `X-Internal-Token` |
| 平台基线 | 统一响应与异常处理 | current | `Result{code,message,data}` + 全局异常处理 |
| 事件流 | Redis Stream 可靠消费 | current | consumer group + ACK + pending 回收 + DLQ |
| 交付治理 | 增量构建与增量部署 | current | CI 按受影响服务执行构建和部署 |
| 交付治理 | 单服务回滚流程 | in-progress | 回滚 workflow/runbook 已有，需持续演练留档 |
| 可观测性 | 指标导出基线 | current | Actuator + Micrometer + Prometheus endpoint 已接入 |
| 可观测性 | 告警平台落地 | in-progress | Prometheus/Grafana 与规则联动需在部署层补齐 |
| 影子服务 | 事件消费链路（Stream + DLQ + 回收） | current | `shadow-service` 已具备消费、重试、ACK、DLQ、pending 回收能力 |
| 影子服务 | 独立影子服务产品化 | in-progress | 影子域接口与完整业务闭环仍在演进 |
| 规则引擎 | 事件消费链路（Stream + DLQ + 回收） | current | `rule-engine` 已具备消费、重试、ACK、DLQ、pending 回收能力 |
| 规则引擎 | 规则执行闭环产品化 | in-progress | 控制面与执行策略仍在持续补齐 |
| MQTT 适配 | 独立消息适配 | in-progress | 模块存在，场景能力待补齐 |
| 数据解析 | Payload 解析引擎 | in-progress | 模块存在，解析策略待补齐 |
| 时序存储 | TSDB 落地 | planned | 文档提及目标态，代码未完整落地 |
| 实时计算 | Kafka/Flink 链路 | planned | 目标态能力 |

## API 入口（current）

- 设备域：`/api/v1/products`、`/api/v1/devices`、`/api/v1/devices/{deviceId}/shadow`、`/api/v1/provision`
- 认证域：`/api/v1/emqx/auth`、`/api/v1/emqx/webhook`
- 家庭域：`/api/v1/users`、`/api/v1/homes`、`/api/v1/rooms`

## 后续补齐建议

- 给每个 `in-progress` 模块补充“最小可验证用例”（启动、接口、验收标准），并把“可运行”和“可产品化”分开验收
- 把“回滚演练记录”和“告警规则变更”纳入每次迭代必填交付物
- 在 PR 模板增加“是否影响能力矩阵”勾选项，减少状态漏更
