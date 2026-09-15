# AIOT-java 微服务通信与治理文档

> 文档性质：基于代码事实的微服务治理现状快照 + 架构债务收敛路线
> 生成日期：2026-08-27
> 技术底座：Spring Boot 3.2.3 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0 / Java 17
> 范围：服务发现、通信三通道、管理面（配置中心 / 限流熔断 / 分层鉴权 / 可观测），以及三条架构债务（P0/P1/P2 收敛）

---

## 0. 服务清单

| 服务 | 应用名（spring.application.name） | 端口 | 主要职责 |
| --- | --- | --- | --- |
| aiot-gateway | aiot-gateway | 8080 | 统一入口：路由、限流、熔断、JWT 鉴权、链路透传 |
| aiot-auth-service | aiot-auth-service | 8082 | 设备/用户认证、EMQX webhook、设备上下线事件发布 |
| aiot-device-service | aiot-device-service | 8081 | 设备生命周期、OTA、影子、知识库重建触发、后台聚合 |
| aiot-home-service | aiot-home-service | 8083 | 家庭/房间/成员、用户、家庭删除补偿 |
| aiot-rule-engine | aiot-rule-engine | 8084 | 规则引擎、AI 诊断/评估、运维工单 |
| aiot-mqtt-adapter | aiot-mqtt-adapter | 8085 | MQTT 消息接入，转发 data-parser |
| aiot-data-parser | aiot-data-parser | 8086 | 设备数据解析、知识库检索/重建 |
| aiot-shadow-service | aiot-shadow-service | 8087 | 设备影子状态快照 |

公共模块：`aiot-common` 承载跨服务鉴权、负载均衡 HTTP 客户端、Redis Stream 容器工厂、JWT、跨服务调用执行器。

---

## 1. 服务发现（Nacos）

### 1.1 代码事实

| 维度 | 事实 | 依据 |
| --- | --- | --- |
| 依赖 | `spring-cloud-starter-alibaba-nacos-discovery` 仅声明于 `aiot-common/pom.xml`，通过传递依赖覆盖全部服务 | `aiot-common/pom.xml:56-59` |
| 版本 | Spring Cloud Alibaba `2023.0.1.0`（BOM 由父 POM 管理） | `pom.xml:97-98` |
| 注册地址 | 各服务 `spring.cloud.nacos.discovery.server-addr = ${NACOS_SERVER_ADDR:127.0.0.1:8848}` | 各服务 `application.yml` |
| 显式注解 | **未使用 `@EnableDiscoveryClient`** —— Spring Cloud 2023 由 starter 自动配置启用，无需显式注解 | 全库 Grep 无 `EnableDiscoveryClient` |
| `config.import` | **未配置 `spring.config.import`，未引入 `nacos-config` 依赖，`spring.cloud.nacos.config` 不存在** → **Nacos 配置中心未接入** | 全库 Grep 无 `config.import` / `nacos-config` / `spring.cloud.nacos.config` |
| 负载均衡 | `spring-cloud-starter-loadbalancer`（`lb://` 由 Nacos 解析实例列表） | `aiot-common/pom.xml:51-54`、`LoadBalancedRestClientConfig` |
| 网关发现路由 | `spring.cloud.gateway.discovery.locator.enabled=false`，采用手动路由 | `aiot-gateway/application.yml:26-27` |

### 1.2 结论

- 服务发现（Nacos Discovery）已作为统一底座落地：所有服务注册到 Nacos，网关与东西向 HTTP 客户端均具备 `lb://` 解析能力。
- **Nacos 配置中心当前为空白**：配置仍分散于各服务本地 `application.yml` + 环境变量，未收敛到 Nacos Config（详见 §4 债务 D1 与 §5 收敛路线）。

---

## 2. 通信三通道

### 2.1 通道总览

| 通道 | 方向 | 协议/载体 | 身份/路由 | 典型调用方 |
| --- | --- | --- | --- | --- |
| C1 网关路由 | 南北向（外部 → 服务） | HTTP via Spring Cloud Gateway | `lb://service-name` + JWT | aiot-gateway |
| C2 服务间直连 | 东西向（服务 → 服务） | HTTP（RestClient / WebClient / RestTemplate） | `base-url` + `X-Internal-Token` | 8 个东西向客户端（见 §4 D1） |
| C3 事件总线 | 东西向（异步解耦） | Redis Stream | stream `aiot:stream:device-event` + Consumer Group | auth-service → device/rule/shadow |

### 2.2 通道 C1 —— 网关 `lb://` 路由

| 路由 id | uri | predicates | 过滤器 |
| --- | --- | --- | --- |
| aiot-device-service | lb://aiot-device-service | /api/v1/devices、/api/v1/ota、/api/v1/provision、/api/v1/products、/api/v1/admin-console、/admin | CircuitBreaker(deviceCircuitBreaker) |
| aiot-auth-service | lb://aiot-auth-service | /api/v1/emqx、/api/v1/auth | RequestRateLimiter(60/120) + CircuitBreaker(authCircuitBreaker) |
| aiot-rule-engine | lb://aiot-rule-engine | /api/v1/ai、/api/v1/admin | CircuitBreaker(ruleEngineCircuitBreaker) |
| aiot-home-service | lb://aiot-home-service | /api/v1/users、/api/v1/homes、/api/v1/rooms | RequestRateLimiter(30/60) + CircuitBreaker(homeCircuitBreaker) |
| openapi-*（4 条） | lb://aiot-*-service | /v3/api-docs/* | RewritePath 聚合 |

依据：`aiot-gateway/src/main/resources/application.yml:32-98`。

### 2.3 通道 C2 —— 服务间 `http://<service-name>` + `X-Internal-Token` 直连

| 要素 | 事实 | 依据 |
| --- | --- | --- |
| base-url 注入 | 8 个客户端均用 `@Value("${aiot.<svc>.base-url:lb://aiot-<svc>}")`，**代码默认值为 `lb://`** | 见 §4 D1 客户端清单 |
| 部署态覆盖 | `docker-compose.yml` 用环境变量 `AIOT_*_URL=http://aiot-<svc>:<port>` **覆盖为 http 直连**，绕过 Nacos | `docker-compose.yml:133-135,175,213,249-250,285` |
| 鉴权头 | 客户端显式注入 `X-Internal-Token`（来自 `aiot.internal.token`） | `KnowledgeSearchClient.java:56`、`MqttIngressServiceImpl.java:54` 等 |
| 身份透传 | `X-User-Id / X-Global-User-Id / X-User-Phone` 由调用方透传（`HomePermissionService`、`AdminConsoleRemoteQueryFacadeImpl`） | 上述类 `defaultHeader(...)` |
| 链路透传 | `X-Trace-Id` 统一由 `TracePropagation` / `TracePropagationRequestInterceptor` 处理 | `aiot-common/http/TracePropagation.java` |
| 韧性 | `CrossServiceHttpExecutor` 提供超时/重试/自研简单熔断（`aiot.cross-service.http.*`） | `aiot-common/http/CrossServiceHttpExecutor.java` |
| 客户端装配 | `LoadBalancedRestClientConfig`（@LoadBalanced RestClient.Builder）、`LoadBalancedWebClientConfig`（WebClient.Builder） | `aiot-common/config/*` |

> 关键事实：代码态已统一为 `lb://`，但**部署态（docker-compose）用 `http://` 直连覆盖**，且 CI 校验脚本以正则 `base-url:\s*\$\{[^:}]+:http://...` 固化直连（`.github/workflows/ci-cd.yml:740-751`）。这构成 §4 债务 D1。

### 2.4 通道 C3 —— Redis Stream 事件总线

| 维度 | 事实 | 依据 |
| --- | --- | --- |
| 主 stream | `aiot:stream:device-event`（`aiot.events.device-status-stream`） | 各服务 `application.yml` |
| DLQ stream | `aiot:stream:device-event:dlq` | 同上 |
| 生产者 | aiot-auth-service（设备上/下线事件 `DeviceEvent` → stream） | `AuthServiceImpl.publishDeviceEvent` |
| 消费者 group | device-service=`aiot-device-service-group`、rule-engine=`aiot-rule-engine-group`、shadow-service=`aiot-shadow-service-group` | 各服务 `application.yml` |
| 消费者实现 | device=`DeviceStatusStreamSubscriber`、rule=`DeviceEventSubscriber`、shadow=`DeviceEventSubscriber` | `aiot-*/listener/*` |
| 消费韧性 | 重试（max-attempts=3 / backoff=200ms）+ ACK + DLQ 发布 + 指标（consume/success/failed/retried/dlq/ack/latency） | `DeviceEventSubscriber` 两份 |
| 待回收调度 | `DeviceEventPendingRecoveryScheduler`（pending reclaim）在 device/rule/shadow 三处重复 | Grep 命中 3 份 |
| 容器工厂 | 已下沉 `aiot-common`：`RedisStreamListenerContainerFactory` | `aiot-common/config/RedisStreamListenerContainerFactory.java` |

> 关键事实：Stream 监听**容器创建**已下沉 common，但**订阅者消费样板（反序列化/重试/ACK/DLQ/指标）未下沉**，在 rule-engine 与 shadow-service 两份 `DeviceEventSubscriber` 近乎逐行重复。这构成 §4 债务 D3。

---

## 3. 管理面

### 3.1 配置中心

| 项 | 状态 |
| --- | --- |
| Nacos Config | **未接入**（无 `nacos-config` 依赖、无 `config.import`、无 `spring.cloud.nacos.config`） |
| 现状 | 配置分散在各服务本地 `application.yml` + 环境变量（`${ENV:default}` 兜底模式） |
| 影响 | 无法动态刷新、多环境配置漂移、密钥类配置（token/secret）依赖环境变量注入 |

### 3.2 网关限流与熔断

| 能力 | 实现 | 关键参数 |
| --- | --- | --- |
| 限流 | `RequestRateLimiter`（Redis）+ `ipKeyResolver` | auth：60/s、突发 120；home：30/s、突发 60 |
| 限流维度 | `GatewayRateLimitConfig.ipKeyResolver`：优先 `X-User-Id`，回落 `path:ip` | `GatewayRateLimitConfig.java` |
| 熔断 | `CircuitBreaker`（Resilience4j） | 每个业务路由一个实例 |
| 熔断参数 | COUNT_BASED，窗口 50，失败率 50%，最小调用 20，半开等待 10s | `application.yml:133-159` |
| 依赖 | `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | `aiot-gateway/pom.xml:48` |

### 3.3 分层鉴权（JWT + Internal-Token）

| 层 | 组件 | 机制 |
| --- | --- | --- |
| 网关入口 | `GatewaySecurityConfig`（WebFlux Security） | JWT 认证（`JwtReactiveAuthenticationManager` + `NoOpServerSecurityContextRepository`），白名单放行 |
| 网关身份注入 | `GatewayUserHeaderGlobalFilter`（Order -100） | 清空伪造身份头 → 注入 `X-User-Id/X-Global-User-Id/X-User-Phone`；internal 路径注入 `X-Internal-Token`；透传 `X-Trace-Id` |
| 下游同步拦截 | `GatewayHeaderAuthInterceptor`（common，HandlerInterceptor） | 非 internal 路径校验 `X-User-Id`；internal 路径校验 `X-Internal-Token`（`MessageDigest.isEqual` 常量时间） |
| 下游服务装配 | device/home 的 `AuthInterceptor` 继承 `GatewayHeaderAuthInterceptor` | 复用同一实现 |
| auth-service 专用 | `InternalTokenAuthFilter`（OncePerRequestFilter） | 匹配 `/api/v1/internal/**`，校验 `X-Internal-Token`（委托 `InternalTokenUtils.matches`） |

### 3.4 可观测（Prometheus）

| 维度 | 事实 |
| --- | --- |
| 暴露端点 | `management.endpoints.web.exposure.include=health,info,prometheus`（全服务一致） |
| 指标库 | `micrometer-registry-prometheus`（网关）/ `micrometer-core`（common） |
| 指标标签 | `application=${spring.application.name}`、`env=${AIOT_ENV:local}` |
| 延迟直方图 | `percentiles-histogram.http.server.requests=true`（P50/P95/P99） |
| 探针 | liveness/readiness 开启（K8s 探针） |
| 业务指标 | Stream 消费指标（`aiot.stream.consume.*`、`aiot.stream.dlq.*`、`aiot.stream.ack.*`、`aiot.stream.consume.latency`） |
| 监控栈 | `monitoring/`（Prometheus + Grafana + Loki + Tempo） |

---

## 4. 架构债务（基于代码事实）

### 4.1 D1（P0）东西向 base-url 未统一 `lb://`

**现象**：8 个东西向客户端**代码默认值均为 `lb://`**，但部署态 `docker-compose.yml` 用环境变量覆盖为 `http://aiot-<svc>:<port>` 直连，CI 脚本又以正则固化 `http://` 直连。导致东西向调用在容器环境绕过 Nacos 服务发现与负载均衡。

**涉及客户端**：

| 客户端 | 位置 | base-url 属性 | 注入默认值 |
| --- | --- | --- | --- |
| KnowledgeRebuildClient | aiot-device-service/client | aiot.data-parser.base-url | lb://aiot-data-parser |
| KnowledgeSearchClient | aiot-rule-engine/client | aiot.data-parser.base-url | lb://aiot-data-parser |
| AiDeviceContextClient | aiot-rule-engine/client | aiot.device-service.base-url | lb://aiot-device-service |
| HomePermissionService | aiot-device-service/security | aiot.home-service.base-url | lb://aiot-home-service |
| HomeDeviceCompensationService | aiot-home-service/service | aiot.device-service.base-url | lb://aiot-device-service |
| AdminConsoleRemoteQueryFacadeImpl | aiot-device-service/service/impl | home/rule base-url | lb://aiot-home-service / lb://aiot-rule-engine |
| AiPersistenceAdminFacadeImpl | aiot-device-service/service/impl | aiot.rule-service.base-url | lb://aiot-rule-engine |
| MqttIngressServiceImpl | aiot-mqtt-adapter/service/impl | aiot.data-parser.base-url | lb://aiot-data-parser |

**证据**：
- `docker-compose.yml:133-135` `AIOT_HOME_SERVICE_URL/RULE/DATA_PARSER = http://aiot-<svc>:<port>`
- `docker-compose.yml:175,213,249` `AIOT_DEVICE_SERVICE_URL = http://aiot-device-service:8081`
- `docker-compose.yml:250,285` `AIOT_DATA_PARSER_BASE_URL = http://aiot-data-parser:8086`
- `.github/workflows/ci-cd.yml:740-751` 正则 `base-url:\s*\$\{[^:}]+:http://...` 校验 compose 服务名可解析

---

### 4.2 D2（P1）内部鉴权常量时间比较实现不一致

**任务描述 vs 代码事实（已核对）**：

| 描述项 | 任务说法 | 代码事实 |
| --- | --- | --- |
| GatewayHeaderAuthInterceptor | `MessageDigest.isEqual` 常量时间 | ✅ 属实（`GatewayHeaderAuthInterceptor.java:77-82`） |
| InternalTokenAuthFilter | `String.equals` 非常量时间 | ❌ **已不成立**：当前委托 `InternalTokenUtils.matches` → `MessageDigest.isEqual`（常量时间） |
| 残留非常量时间比较 | — | ✅ **真实残留**：`AuthServiceImpl.authenticateDevice` 第 79 行 `expectedPassword.equals(password)`（设备 HMAC 密码比较） |

**结论**：
1. 内部 token 的过滤器比较已基本收敛为常量时间（`InternalTokenUtils`）。
2. 但**同一文件内仍存在两套签名比较实现**：`AuthServiceImpl.authenticateDevice` 用 `String.equals`（非常量时间），`AuthServiceImpl.verifyWebhookSignature` 用 `MessageDigest.isEqual`（常量时间）。
3. 鉴权组件仍分散：`GatewayHeaderAuthInterceptor`（common，同步 HandlerInterceptor）与 `InternalTokenAuthFilter`（auth-service，OncePerRequestFilter）两套路径匹配 / 错误响应 / 常量时间工具，存在重复实现与后续漂移风险。

---

### 4.3 D3（P2）DeviceEventSubscriber 重复实现，未下沉 aiot-common

**现象**：`aiot-rule-engine` 与 `aiot-shadow-service` 各有一份 `DeviceEventSubscriber`，消费样板（反序列化 / 重试 / ACK / DLQ / 指标注册）近乎逐行重复，仅三处差异：consumer group、指标 `service` tag、业务回调。

| 差异点 | rule-engine | shadow-service |
| --- | --- | --- |
| 业务回调 | `RuleLifecycleService.executeByEvent(event)` | 无业务，仅记录 SHADOW 事件日志 |
| group | aiot-rule-engine-group | aiot-shadow-service-group |
| 指标 tag | service=aiot-rule-engine | service=aiot-shadow-service |

**重复面（超出任务点名）**：

| 类 | 重复位置 |
| --- | --- |
| DeviceEventSubscriber | rule-engine、shadow-service（2 份） |
| RedisEventListenerConfig | rule-engine、shadow-service（2 份；device-service 有等价 `RedisDeviceEventStreamConfig`） |
| DeviceEventPendingRecoveryScheduler | rule-engine、shadow-service、device-service（3 份） |
| DeviceStatusStreamSubscriber | device-service（1 份，同构消费样板） |

**已下沉部分**：`RedisStreamListenerContainerFactory`（aiot-common）已统一容器创建，但订阅者消费样板未下沉。

---

## 5. 架构债务收敛路线（P0 / P1 / P2）

### 5.1 D1 —— 东西向 base-url 统一 `lb://`

| 阶段 | 目标 | 具体动作 | 验收标准 |
| --- | --- | --- | --- |
| **P0（立即）** | 部署态收敛 | 1. 将 `docker-compose.yml` 中 `AIOT_*_URL` / `AIOT_DATA_PARSER_BASE_URL` 的 `http://aiot-<svc>:<port>` 改为删除覆盖（回落代码默认 `lb://`）或显式设为 `lb://aiot-<svc>`；2. 移除/改写 `.github/workflows/ci-cd.yml:740-751` 固化 `http://` 的正则，改为断言 base-url 为 `lb://` 或未覆盖 | 容器环境下东西向调用走 Nacos 解析；CI 不再放行 `http://` 直连 |
| **P1（近期）** | 代码态统一 | 1. 新增 `CrossServiceEndpointsProperties`（`@ConfigurationProperties(prefix="aiot.endpoints")`）集中声明服务名 → base-url，8 个客户端改为注入该配置；2. 全量走 `@LoadBalanced` RestClient/WebClient + `CrossServiceHttpExecutor`，禁止 `RestTemplate` 直连（收编 `MqttIngressServiceImpl` 的 RestTemplate 用法） | 8 个客户端无裸 `http://` base-url；直连仅存在于 common 装配层 |
| **P2（中长期）** | 治理门禁 | 1. ArchUnit / CI 规则：任何东西向 base-url 不得以 `http://` 开头；东西向调用必须经 `CrossServiceHttpExecutor` + `lb://`；2. 保留 http 回退开关但默认关闭，仅限本地联调 | 门禁拦截回归；直连仅限显式豁免 |

### 5.2 D2 —— 内部鉴权常量时间比较统一

| 阶段 | 目标 | 具体动作 | 验收标准 |
| --- | --- | --- | --- |
| **P0（立即）** | 消除残留侧信道 | 将 `AuthServiceImpl.authenticateDevice` 第 79 行 `expectedPassword.equals(password)` 改为 `MessageDigest.isEqual`（或复用 `InternalTokenUtils`/签名工具），与 `verifyWebhookSignature` 一致 | 设备认证密码比较为常量时间；补单测覆盖 |
| **P1（近期）** | 组件收敛 | 1. 将 `InternalTokenAuthFilter`（auth-service）与 `GatewayHeaderAuthInterceptor`（common）的 token 校验收敛到 aiot-common 单一 `InternalTokenAuthenticator`（常量时间 + 统一 401 响应 + 统一路径匹配）；2. 删除 auth-service 独立过滤器，复用 common 组件 | 鉴权过滤器单一实现；两处行为（路径/响应/比较）一致 |
| **P2（中长期）** | 门禁 + 扫描 | CI/ArchUnit 禁止业务代码对 secret/token/HMAC 使用 `String.equals`，统一经 `InternalTokenUtils` 或签名工具；纳入安全扫描（时序侧信道规则） | 新增不安全比较被门禁拦截 |

### 5.3 D3 —— 事件订阅样板下沉 aiot-common

| 阶段 | 目标 | 具体动作 | 验收标准 |
| --- | --- | --- | --- |
| **P0（立即）** | 抽公共基类 | 在 aiot-common 新增 `AbstractDeviceEventSubscriber`：封装反序列化 / 重试 / ACK / DLQ / 指标注册，暴露单一 `onEvent(DeviceEvent)` 回调；rule/shadow 订阅者继承并仅实现业务回调；同步收敛 `RedisEventListenerConfig`、`DeviceEventPendingRecoveryScheduler` 重复 | 两个订阅者业务代码减至回调；样板逻辑单点维护 |
| **P1（近期）** | 完成下沉与回归 | 1. rule/shadow/device 三服务切换到 common 订阅者，删除各服务重复类；2. 补充统一消费/重试/DLQ/ACK 全链路单测，验证 consumer group 隔离与指标 `service` tag 不回归 | 三服务订阅样板全部来自 common；单测覆盖消费韧性路径 |
| **P2（中长期）** | 事件总线治理 | 1. 建立 `DeviceEvent` 版本化契约与统一 stream/DLQ/group 命名规范；2. 收敛分散在各服务 application.yml 的消费配置为统一 `aiot.events.*` 模板；3. 事件总线能力以 common SDK 形式提供 | 事件契约/配置/指标三统一；新增消费者零样板 |

---

## 6. 关键文件索引

| 主题 | 文件 |
| --- | --- |
| 网关路由/限流/熔断/可观测 | `aiot-gateway/src/main/resources/application.yml` |
| 网关安全 | `aiot-gateway/src/main/java/com/aiot/gateway/config/GatewaySecurityConfig.java` |
| 网关身份注入 | `aiot-gateway/src/main/java/com/aiot/gateway/security/GatewayUserHeaderGlobalFilter.java` |
| 网关限流维度 | `aiot-gateway/src/main/java/com/aiot/gateway/config/GatewayRateLimitConfig.java` |
| 下游同步鉴权（常量时间） | `aiot-common/src/main/java/com/aiot/common/security/GatewayHeaderAuthInterceptor.java` |
| token 常量时间工具 | `aiot-common/src/main/java/com/aiot/common/security/InternalTokenUtils.java` |
| auth 内部过滤器 | `aiot-auth-service/src/main/java/com/aiot/auth/config/InternalTokenAuthFilter.java` |
| 设备认证（残留 String.equals） | `aiot-auth-service/src/main/java/com/aiot/auth/service/impl/AuthServiceImpl.java` |
| 跨服务执行器（超时/重试/熔断） | `aiot-common/src/main/java/com/aiot/common/http/CrossServiceHttpExecutor.java` |
| 负载均衡 HTTP 客户端 | `aiot-common/src/main/java/com/aiot/common/config/LoadBalancedRestClientConfig.java`、`LoadBalancedWebClientConfig.java` |
| 链路透传 | `aiot-common/src/main/java/com/aiot/common/http/TracePropagation.java` |
| Redis Stream 容器工厂（已下沉） | `aiot-common/src/main/java/com/aiot/common/config/RedisStreamListenerContainerFactory.java` |
| 事件订阅者（重复，待下沉） | `aiot-rule-engine/.../listener/DeviceEventSubscriber.java`、`aiot-shadow-service/.../listener/DeviceEventSubscriber.java` |
| 部署态直连覆盖（D1 证据） | `docker-compose.yml`、`.github/workflows/ci-cd.yml` |
