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

---

## 5. Git 协作与代码提交规约 (Git Workflow)

### 5.1 分支管理模型
*   `main` / `master`：生产环境稳定版本，保护分支，仅允许通过 Merge Request (PR) 合并。
*   `develop`：开发环境的主干分支。
*   `feat/xxx`：新功能特性分支，从 develop 拉取。
*   `bugfix/xxx`：日常 Bug 修复分支。
*   `hotfix/xxx`：生产环境紧急修复分支，从 main 拉取。

### 5.2 Commit Message 规范 (Angular 规范)
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

## 6. 代码格式化与风格 (Code Style)

遵循《阿里巴巴 Java 开发手册》。
*   **缩进**：统一使用 4 个空格 (Spaces) 进行缩进，严禁使用 Tab。
*   **换行**：统一使用 LF (Unix) 换行符，禁止使用 CRLF (Windows)。
*   **文件编码**：全局强制 `UTF-8`。
为保证跨 IDE (IDEA / VSCode) 格式统一，项目根目录需提供 `.editorconfig` 配置文件。
