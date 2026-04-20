# AIoT 架构整改任务拆解表（P0/P1/P2）

> 文档目标：将架构评审问题转成可执行任务池，直接映射到代码文件与接口，便于 Sprint 排期、Issue 拆分和统一跟踪。  
> 适用范围：`gateway`、`auth-service`、`device-service`、`home-service`、`common`、`CI/CD`、`deploy`。

---

## 0. 使用规则

- 任务状态：`todo` / `doing` / `blocked` / `done`
- 优先级定义：
  - `P0`：安全与可用性红线，必须优先关闭
  - `P1`：架构一致性与可维护性关键项
  - `P2`：演进优化项（事件化、治理增强、平台化）
- 建议字段：每条任务在 GitHub Issue 中统一携带 `id`、`priority`、`domain`、`owner`、`due`、`status`

---

## 1. P0（必须先完成）

| ID | 领域 | 问题点 | 整改动作（可执行） | 涉及文件/接口 | 验收标准 |
|---|---|---|---|---|---|
| P0-01 | 设备安全 | 设备域缺少资源归属二次校验，存在跨家庭越权风险 | 所有设备读写接口统一做 `resource.homeId == request.homeId` 权限校验，角色要求：读 `Member`，写 `Admin` | `aiot-device-service/src/main/java/com/aiot/device/controller/DeviceController.java`、`DeviceShadowController.java`、`security/HomePermissionService.java`；接口：`/api/v1/devices/**`、`/api/v1/devices/{deviceId}/shadow/**` | 使用非本家庭用户访问设备与影子接口，返回 `403`；同家庭对应角色可正常访问 |
| P0-02 | 配网安全 | 配网 token 仅绑定 `homeId`，可被重放或参数替换利用 | token 绑定 `homeId+productKey+deviceName`，兑换时强一致校验并一次性消费 | `aiot-device-service/src/main/java/com/aiot/device/service/impl/ProvisionServiceImpl.java`；接口：`GET /api/v1/provision/token`、`POST /api/v1/provision/exchange` | 旧 token 重放失败；替换 `productKey/deviceName` 失败；合法流程成功 |
| P0-03 | 服务可用性 | `docker-compose` 健康检查依赖 `/actuator/health`，但服务依赖未统一补齐 | 在 `gateway/auth/device/home` 增加 `actuator` 依赖与健康端点配置；Compose 健康检查按 readiness 调整 | `aiot-gateway/pom.xml`、`aiot-auth-service/pom.xml`、`aiot-device-service/pom.xml`、`aiot-home-service/pom.xml`、各服务 `application.yml`、`docker-compose.yml` | 容器启动后健康检查均为 `healthy`，无误判重启 |
| P0-04 | 构建一致性 | Dockerfile 使用 layertools，但服务可执行 Jar 规范不统一 | 统一 `spring-boot-maven-plugin` 到各服务模块，固定可执行 Jar 产物规范 | 根 `pom.xml`、各服务 `pom.xml`、`Dockerfile`、`.github/workflows/ci-cd.yml` | CI 构建与本地构建产物一致，镜像构建不再出现 Jar 结构不兼容 |
| P0-05 | 密钥治理 | JWT/DB/EMQX 存在可运行默认弱配置 | 移除生产可用默认值，改为“未注入即启动失败”；通过环境变量或 Secret 注入 | `aiot-device-service/src/main/resources/application.yml`、`aiot-auth-service/src/main/resources/application.yml`、`aiot-home-service/src/main/resources/application.yml`、`docker-compose.yml` | 未提供关键 secret 时服务拒绝启动；生产环境密钥不落仓库 |
| P0-06 | API 面暴露 | 生产环境 Swagger 暴露扩大攻击面 | 对 `swagger-ui` 与 `api-docs` 增加 profile 限制（仅 dev/test）或网关白名单保护 | 各服务 `application.yml`，网关安全过滤器（如新增） | 生产 profile 不暴露 Swagger；测试环境仍可访问 |

---

## 2. P1（高价值一致性整改）

| ID | 领域 | 问题点 | 整改动作（可执行） | 涉及文件/接口 | 验收标准 |
|---|---|---|---|---|---|
| P1-01 | 领域边界 | `DeviceCredential` 在 `auth` 与 `device` 双份模型，数据主权不清 | 明确“凭证主服务”（建议 `auth-service`）；非主服务改为内部 API/事件访问 | `aiot-auth-service/src/main/java/com/aiot/auth/entity/DeviceCredential.java`、`aiot-device-service/src/main/java/com/aiot/device/entity/DeviceCredential.java`、相关 mapper/service | 凭证写路径唯一；跨服务无重复写入 |
| P1-02 | 服务调用治理 | `device -> home` 直接 URL 调用，缺少超时/重试/熔断与身份治理 | 引入统一内部调用客户端（Feign/WebClient 封装），配置 timeout/retry/circuit-breaker | `ProvisionServiceImpl.java`、新建 client 配置类；`application.yml` | home 不可用时调用可降级/快速失败；不出现线程堆积 |
| P1-03 | 内部接口安全 | 内部调用仅透传用户 JWT，缺少服务身份校验 | 增加服务签名头或 mTLS，区分“用户态 token”与“服务态信任” | `home-service` 鉴权入口、`device-service` 调用层、网关配置 | 非法内部调用被拒绝；合法服务调用可追踪 |
| P1-04 | 状态一致性 | 在线状态 Redis 与设备表状态可能漂移 | 定义单一真相源（建议在线态以 Redis 为准）并补同步策略 | `aiot-auth-service/src/main/java/com/aiot/auth/service/impl/AuthServiceImpl.java`、`aiot-device-service/src/main/java/com/aiot/device/service/impl/DeviceServiceImpl.java` | Redis 与对外状态查询结果一致，漂移问题可复现并关闭 |
| P1-05 | 网关边界 | Gateway 动态发现路由可能扩大暴露面 | 关闭或收敛 discovery 自动暴露，改显式路由 + 统一鉴权/限流 | `aiot-gateway/src/main/resources/application.yml`、网关过滤器 | 未配置路由的服务不可直接暴露；鉴权策略统一 |
| P1-06 | 配置标准化 | 环境变量命名不统一导致运维复杂 | 统一配置命名（`MYSQL_PASSWORD`/`REDIS_PASSWORD` 等），沉淀配置规范文档 | 各服务 `application.yml`、`docs/development_standards.md` | 所有服务配置命名一致；部署脚本无需分支逻辑 |

---

## 3. P2（架构演进与平台化）

| ID | 领域 | 问题点 | 整改动作（可执行） | 涉及文件/接口 | 验收标准 |
|---|---|---|---|---|---|
| P2-01 | 事件驱动 | 当前以同步 HTTP/Webhook 为主，耦合高 | 落地消息总线，设备上下线、影子变更、规则触发全部事件化 | `aiot-mqtt-adapter`、`aiot-rule-engine`、`aiot-data-parser`、`docs/architecture_design.md` | 至少 2 条核心链路改为异步事件驱动并稳定运行 |
| P2-02 | 影子子系统 | 影子实现简化为 Redis Hash，缺少版本控制 | 演进为独立 shadow 服务（版本号/CAS/冲突处理） | `aiot-shadow-service`、`DeviceShadowServiceImpl.java` | 并发写入可控，无覆盖丢失 |
| P2-03 | 可观测性 | 链路、指标、告警未形成统一闭环 | 统一日志规范、Trace、Metrics、告警规则，形成 SLO 基线 | `aiot-common`、各服务配置、部署监控配置 | 可观测面板可见核心业务链路与错误率 |
| P2-04 | 部署架构 | 目标态 K8s 与现状 Compose 脱节 | 形成“现状架构 vs 目标架构”双轨文档与迁移路线 | `docs/deployment_and_performance.md`、新增迁移文档 | 团队可按阶段迁移，避免目标态误导当前交付 |
| P2-05 | CI/CD 闭环 | 模块、构建列表、部署列表不一致 | 对齐“模块清单-构建清单-部署清单”，补齐漏构建服务 | `.github/workflows/ci-cd.yml`、`docker-compose.yml`、根 `pom.xml` | 新增服务默认纳入构建与部署闭环 |

---

## 4. 建议执行节奏（3 个 Sprint）

### Sprint-A（安全与可用性封板）
- 范围：`P0-01` ~ `P0-06`
- 目标：先消除越权、重放、健康检查和弱配置风险

### Sprint-B（边界与一致性）
- 范围：`P1-01` ~ `P1-06`
- 目标：收敛领域主权，降低服务耦合，统一网关与配置治理

### Sprint-C（事件化演进）
- 范围：`P2-01` ~ `P2-05`
- 目标：将系统从“分布式单体”演进为“可扩展事件驱动平台”

---

## 5. 统一管理建议（远程仓库）

- 建议在 GitHub 使用以下 Label：
  - `arch-remediation`、`priority:P0/P1/P2`、`domain:security`、`domain:data`、`domain:platform`
- 建议每条任务以 `ID` 建立 Issue，标题格式：`[P0-01] 设备域资源归属二次校验`
- 建议按 Sprint 建立 Milestone：`Sprint-A`、`Sprint-B`、`Sprint-C`
- 建议将本文件作为唯一任务来源（SSOT），所有周会状态只引用该文件的任务 ID
