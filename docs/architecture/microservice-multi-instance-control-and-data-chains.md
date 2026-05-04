# 单微服务多实例：请求与数据库控制链路梳理

## 1. Premise / Constraints / Boundaries / Endgame
- Premise：单个微服务横向扩容为多个实例，共享同一套 MySQL 与 Redis，入口统一经 Gateway。
- Constraints：现状以 Spring Cloud Gateway + Nacos + MyBatis-Plus + Redis Stream 为主；未引入分布式强一致事务框架。
- Boundaries：本文聚焦“请求如何在多实例中处理”与“数据库如何保证并发一致性”，不展开业务字段细节。
- Endgame：形成可用于评审和上线核对的控制链路图谱，明确可靠性与风险点。

## 2. 总览结论
- 请求分发：Gateway 使用 `lb://service-name` 通过注册中心实例列表分发请求到多个实例。
- 实例处理：实例内按统一鉴权拦截器和业务 Service 执行，事务边界主要在 Service 层 `@Transactional`。
- 数据一致性：采用“本地事务 + 幂等 + 唯一约束 + 补偿/重试 + 事件最终一致”，非分布式强一致。
- 异常恢复：同步链路通过超时/重试/熔断，异步链路通过 Redis Stream ACK + DLQ + pending reclaim。

## 3. 请求控制链路（Control Plane）
### 3.1 入口到实例
1. 客户端请求进入 Gateway。
2. Gateway 执行 JWT 全局过滤、白名单放行判断、Token 解析与 Claims 校验。
3. Gateway 移除客户端可能伪造的内部头，再注入可信身份头。
4. Gateway 按路由 `lb://aiot-xxx-service` 选择某个实例转发。
5. 目标实例执行 `GatewayHeaderAuthInterceptor` 二次校验后进入 Controller/Service。

### 3.2 关键控制点
- 防伪头：Gateway 默认过滤器移除 `X-User-Id`、`X-User-Phone`、`X-Internal-Token`，避免客户端伪造。
- 内外接口分治：`/api/v1/internal/**` 依赖 `X-Internal-Token`；普通业务接口依赖 `X-User-Id`。
- 失败保护：Gateway 路由有熔断与限流配置，服务间调用统一执行器支持超时、重试、熔断。

## 4. 数据库与并发链路（Data Plane）
### 4.1 单请求写路径
1. 请求落到某实例后进入 Service。
2. Service 在本地事务中执行 MySQL CRUD（Repository/Mapper）。
3. 成功则提交事务，失败抛异常并回滚。
4. 若涉及跨服务变更，走补偿接口调用，语义为最终一致。

### 4.2 多实例并发保障机制
- 机制 A（业务锁/幂等键）：Redis `setIfAbsent`、`getAndDelete` 防并发抢占与重复消费。
- 机制 B（数据库唯一约束）：如 `uk_home_user`、`uk_device_id`、`uk_task_device` 兜底防重。
- 机制 C（调用治理）：跨服务统一超时+重试+熔断，降低级联故障。
- 机制 D（事件可靠性）：Redis Stream 消费成功 ACK，失败重试，超过阈值入 DLQ，并由 pending reclaim 处理遗留消息。

## 5. 典型链路样例
### 5.1 配网并发链路（同设备并发请求）
1. 多个实例同时收到同一设备配网请求。
2. 实例先用 Redis 分布式锁（按 `productKey:deviceName`）抢占处理权。
3. 抢锁成功实例继续写库并下发凭证；失败实例快速返回“处理中，请稍后重试”。
4. MySQL 唯一约束作为最终防线，防止重复创建设备或凭证。

### 5.2 家庭删除补偿链路
1. Home 服务执行删除家庭。
2. 先调用 Device 内部补偿接口解绑设备，再删除家庭与成员关系。
3. 调用失败依赖重试/熔断与业务补偿策略，保障最终一致，非全局原子提交。

## 6. 现状风险与优化建议
### 6.1 已识别风险
- 部分服务间调用仍是 `base-url` 直连（如 `127.0.0.1:8081/8083`），不具备注册中心级实例负载能力。
- MyBatis-Plus 当前只看到分页插件，未见乐观锁插件与版本字段策略；热点更新冲突主要依赖业务幂等与唯一键。
- Redis Stream consumer 默认值常为应用名；多实例若不区分 consumer 名称，会影响 pending 可观测与排障效率。

### 6.2 建议优先级
- P0：服务间调用改造为基于服务发现的负载调用（避免直连单点）。
- P0：统一幂等键规范（键生成、TTL、冲突行为、审计日志）。
- P1：为高并发写热点引入乐观锁版本策略，明确冲突重试规则。
- P1：统一 consumer 命名（`appName-instanceId`）并补齐积压/重试/DLQ 告警。

## 7. 上线核对清单（多实例）
- 路由：所有外部入口均经 Gateway，且 `lb://` 指向正确服务名。
- 鉴权：网关头清洗开启，服务侧 internal token 校验开启。
- 配置：跨服务超时/重试/熔断参数按环境区分并压测验证。
- 数据：关键表唯一索引齐全，逻辑删除字段与查询条件一致。
- 事件：Stream group/consumer 命名规范化，ACK/DLQ/回收任务已开启并可观测。
- 运维：已定义请求错误率、熔断次数、DLQ 增长、pending 堆积告警阈值。

## 8. 参考代码入口
- Gateway 路由与过滤：`aiot-gateway/src/main/resources/application.yml`，`JwtAuthGlobalFilter`
- 服务侧鉴权：`aiot-common/src/main/java/com/aiot/common/security/GatewayHeaderAuthInterceptor.java`
- 跨服务调用治理：`aiot-common/src/main/java/com/aiot/common/http/CrossServiceHttpExecutor.java`
- 事务与补偿样例：`HomeServiceImpl`，`HomeDeviceCompensationService`
- 幂等与并发样例：`ProvisionServiceImpl`
- 事件消费可靠性：`aiot-rule-engine` / `aiot-shadow-service` 的 `DeviceEventSubscriber` 与 `DeviceEventPendingRecoveryScheduler`
