# AIoT 后端全局开发与协同规范 (Development Standards)

> 作为架构师，建立统一的开发规范是降低沟通成本、提升系统可维护性与代码质量的“第一性原理”。本规范旨在为 AIoT 后端研发团队提供多维度的行为准则与技术约束。

## 1. 架构与工程结构规约 (Project Structure)

### 1.1 包结构分层 (Package Layering)
所有微服务均应遵循严格的 MVC + DDD 演进的分层架构，职责清晰，杜绝跨层调用（例如 Controller 直接调用 Mapper）：
*   `controller`: 暴露 RESTful 接口，只处理参数校验、协议转换与异常捕获。
*   `service`: 核心业务逻辑实现，编排领域对象或调用第三方接口。
*   `mapper` / `repository`: 负责数据库或底层持久化的访问。
*   `dto`: 数据传输对象，分为 `XxxReq` (请求入参) 和 `XxxResp` (响应出参)。
*   `entity` / `model`: 与数据库表结构严格一一映射的实体类。
*   `common`: 模块内的公共常量、工具类、枚举定义。

### 1.2 依赖管理准则
*   **单一收口**：所有外部依赖（Spring Boot、Spring Cloud、Alibaba、第三方工具等）版本必须由 `aiot-cloud-parent` 的 `<dependencyManagement>` 统一收口。子模块中**严禁**硬编码指定 `<version>`。

---

## 2. API 设计与响应规约 (API Standards)

### 2.1 RESTful 资源化设计
*   **版本控制**：API URL 必须包含版本号，如 `/api/v1/devices`。
*   **动作映射**：
    *   `GET`：获取资源（幂等）。
    *   `POST`：创建资源或触发非幂等复杂动作。
    *   `PUT`：全量更新资源（幂等）。
    *   `PATCH`：局部更新资源（幂等）。
    *   `DELETE`：删除资源。
*   **命名规范**：URL 路径全小写，多个单词用中划线 `-` 分隔，资源使用名词复数（如 `/api/v1/product-models`）。

### 2.2 统一响应与异常 (Unified Response)
*   **Controller 瘦身**：Controller 方法直接返回业务 DTO 或原对象，由框架层的 `ResponseBodyAdvice` 统一封装为 `Result<T>`。
    *   **【VibeCoding 强制】空返回处理**：当一个接口操作成功但无数据返回时（例如 DELETE 操作），**必须**将方法返回类型声明为 `Void`，并在方法末尾 `return null;`。**严禁**返回硬编码的中文提示字符串（如 `"删除成功"`），这不利于国际化（I18n）以及前端的状态处理。
*   **禁止隐式吞异常**：业务发生异常时，直接 `throw new BusinessException(ResultCode.XXX)`，由 `GlobalExceptionHandler` 统一捕获。严禁在 Controller 中编写大段 `try-catch`。

---

## 3. 数据库与缓存规约 (Database & Cache)

### 3.1 MySQL 规约
*   **表与字段命名**：全小写，下划线分隔（如 `device_info`）。
*   **必备字段**：每张表必须包含 `id` (主键), `created_at` (创建时间), `updated_at` (更新时间)。
*   **软删除**：必须使用 `is_deleted` (TINYINT, 0:未删除, 1:已删除) 实现软删除。
*   **索引规约**：索引命名 `idx_字段名` (普通索引) 或 `uk_字段名` (唯一索引)。禁止使用外键（Foreign Key），关联关系由应用层代码维护。

### 3.2 Redis 缓存规约
*   **Key 命名空间隔离**：必须使用冒号 `:` 分隔，格式为 `项目名:模块名:业务实体:唯一ID`。
    *   *示例*：`aiot:device:status:DEV-12345`。
*   **禁止长 Key 与大 Value**：Key 长度尽量控制在 64 字节以内；Value 超过 10KB 必须进行压缩或拆分。
*   **强制 TTL**：所有写入 Redis 的业务数据（除基础元数据外）必须设置合理的过期时间，防止内存泄漏。
*   **【VibeCoding 强制】缓存一致性模式 (Cache Aside Pattern)**：在涉及到数据库写事务（Insert/Update/Delete）时，**仅允许**在事务内或事务后调用 `redis.delete(key)` 让缓存失效。**严禁**在写事务中同步查询数据库并重构缓存（这会大幅增加长事务的概率并在高并发下产生脏数据），缓存的重构应交给下一次读请求（懒加载）。

---

## 4. 日志与可观测性规约 (Logging & Observability)

### 4.1 日志级别与输出
*   `INFO`：记录系统启动、核心业务流程节点（如设备注册成功、指令下发）、关键配置加载。
*   `WARN`：记录可预期的异常或不影响主流程的错误（如密码重试次数过多、外部接口偶尔超时）。
*   `ERROR`：记录不可预期的系统崩溃、数据库异常、空指针等（必须伴随完整的 Exception Stack Trace）。
*   `DEBUG` / `TRACE`：仅在开发或测试环境开启，生产环境严禁输出 DEBUG 日志。

### 4.2 链路追踪 (MDC TraceID)
在分布式架构下，为便于排查问题，所有日志输出必须携带 `traceId`。
网关层生成唯一 `traceId`，通过 HTTP Header 传递给下游微服务，微服务拦截器将其放入 SLF4J MDC 中。日志输出格式强制包含 `[%X{traceId}]`。
*   **【强制卡点】跨线程与跨服务 TraceId**：当使用 `@Async`、自定义线程池时，必须使用 MDC `TaskDecorator` 将 `traceId` 传递给子线程。使用 `FeignClient` 时，必须配置 `RequestInterceptor` 透传 `X-Trace-Id` 请求头。

---

## 5. 微服务集成与组件装配规范 (Microservice Integration)

*   **【强制卡点】包扫描范围**：新建微服务模块时，其 Spring Boot 启动类必须显式声明 `@ComponentScan(basePackages = {"com.aiot"})`，否则 `aiot-common` 中的核心组件（如全局异常处理器、拦截器）将由于包路径不一致而失效。
*   **【强制卡点】异常 HTTP 状态码同步**：在 `GlobalExceptionHandler` 中拦截异常时，必须使用 `@ResponseStatus` 或返回 `ResponseEntity` 以确保 HTTP 状态码的准确性（如业务参数错误返回 400，系统崩溃返回 500）。禁止在 500 崩溃时依然返回 HTTP 200 OK，这会导致前端网关和监控告警系统失效。
*   **【强制卡点】异常拦截覆盖面**：必须显式拦截 `HttpRequestMethodNotSupportedException` 和 `ConstraintViolationException` 等 Spring MVC 基础异常。
*   **【VibeCoding 强制】声明式鉴权与 RBAC**：微服务内涉及跨切面（Cross-Cutting）的逻辑（如：判断用户角色是否为 Admin、是否拥有某接口权限），**严禁**在 Service 的业务代码中散落硬编码的 `if (role != x)` 判断。**必须**通过自定义注解（如 `@RequireHomeRole`）结合 Spring AOP 切面实现声明式拦截。

---

## 6. 需求验证驱动开发机制 (RDV - Requirement-Driven Verification)
*   **【VibeCoding 强制】无验收单不编码**：在进行任何新特性（Sprint）开发前，必须在 `docs/sprints/` 下创建基于 `SPRINT_REQUIREMENT_TEMPLATE.md` 模板的验收单。明确 `[AC-1]` 等验收标准（Acceptance Criteria）。
*   **【VibeCoding 强制】测试先行 (TDD)**：AI 在编写业务 Service 之前，**必须**先编写对应的 JUnit/Mockito 单元测试框架，并将边界异常（Null、超长、越权）纳入测试覆盖范围。
*   **【VibeCoding 强制】代码溯源**：在实现核心业务方法的注释中，应当标明 `// Implements [AC-ID]`，以确保代码逻辑直接受业务需求约束，防止 AI 产生逻辑漂移。

---

## 7. Git 协作与代码提交规约 (Git Workflow)

### 7.1 分支管理模型
*   `main` / `master`：生产环境稳定版本，保护分支，仅允许通过 Merge Request (PR) 合并。
*   `develop`：开发环境的主干分支。
*   `feat/xxx`：新功能特性分支，从 develop 拉取。
*   `bugfix/xxx`：日常 Bug 修复分支。
*   `hotfix/xxx`：生产环境紧急修复分支，从 main 拉取。

### 7.2 Commit Message 规范 (Angular 规范)
提交信息必须遵循 `<type>(<scope>): <subject>` 格式：
*   `feat`: 新增功能 (feature)
*   `fix`: 修复 Bug
*   `docs`: 修改文档
*   `style`: 代码格式调整（不影响逻辑）
*   `refactor`: 代码重构（既不是新增功能也不是修复 bug）
*   `perf`: 性能优化
*   `test`: 增加或修改测试用例
*   `chore`: 构建过程或辅助工具变动
    *   *示例*：`feat(device): 增加设备注册接口及鉴权逻辑`

---

## 8. 代码格式化与风格 (Code Style)

遵循《阿里巴巴 Java 开发手册》。
*   **缩进**：统一使用 4 个空格 (Spaces) 进行缩进，严禁使用 Tab。
*   **换行**：统一使用 LF (Unix) 换行符，禁止使用 CRLF (Windows)。
*   **文件编码**：全局强制 `UTF-8`。
为保证跨 IDE (IDEA / VSCode) 格式统一，项目根目录需提供 `.editorconfig` 配置文件。

---

## 9. 持久化与安全红线（新增，强制执行）

### 9.1 越权与资源归属校验
*   **【P0 强制】资源归属二次校验**：凡是涉及 `DELETE/UPDATE` 且入参包含 `resourceId` 与 `homeId`（或 tenantId）的接口，除 AOP 鉴权外，Service 层必须再次校验 `resource.homeId == request.homeId`，防止参数伪造越权操作。
*   **【P0 强制】配网令牌签发鉴权**：任何下发配网 Token/凭证的接口必须要求用户 JWT，并校验当前用户是否具备目标家庭（或租户）权限后才允许签发。

### 9.2 密码学与密钥治理
*   **【P0 强制】密码哈希**：严禁新增 MD5/SHA1 存储密码，统一使用 BCrypt/Argon2。
*   **【P1 强制】平滑迁移**：对历史弱哈希用户，允许登录后自动升级为 BCrypt，避免强制重置导致业务中断。
*   **【P0 强制】密钥配置来源**：JWT Secret、AK/SK、第三方凭证必须来自环境变量或密钥管理系统，禁止硬编码在 Java/YAML 中。

### 9.3 Redis 与反序列化安全
*   **【P1 强制】禁止危险多态反序列化**：禁止 `ObjectMapper.activateDefaultTyping(...) + LaissezFaireSubTypeValidator` 组合。
*   **【P1 推荐】安全序列化策略**：统一采用 `GenericJackson2JsonRedisSerializer` 或显式 DTO 序列化。

### 9.4 数据库变更与索引治理
*   **【P0 强制】DDL 版本化**：所有表结构/索引/约束变更必须通过 Flyway/Liquibase 脚本交付，禁止手工改线上库。
*   **【P1 强制】索引设计评审**：新增分页或高频查询接口，必须同步提交 `where + order by` 对齐的索引设计，不满足则不允许合并。
*   **【P1 强制】唯一约束前置**：关键业务键必须有唯一约束或幂等键策略，避免脏数据反复出现。
