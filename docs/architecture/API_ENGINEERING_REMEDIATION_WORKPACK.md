# API 工程化整改工作包

> 目标：将当前 API 工程化问题收敛成一个可执行、可验收、可回归的整改包，避免分散修补。
> 范围：`gateway`、`aiot-common`、`aiot-home-service`、`aiot-device-service`、`aiot-auth-service`

---

## 1. Premise

- 当前系统已经具备真实接口、真实数据、真实链路测试能力。
- 当前 API 还未达到“全维度工程化完成”，主要缺口集中在：
  - 契约/状态码语义不一致
  - OpenAPI/Swagger 暴露面治理不足
  - 分页契约不统一
  - 写接口幂等缺少平台级统一标准

---

## 2. Boundaries

- 本轮只处理 **API 工程化**，不扩散到业务功能重构。
- 本轮优先关闭 `P0/P1`，只在 `aiot-device-service` 做分页标准收口。
- 不改动用户现有业务流程，不改协议型接口 `EMQX` 的原始字符串响应模型。

---

## 3. Workstreams

| Workstream | 优先级 | 目标 | 负责人 |
|---|---|---|---|
| WS-01 契约语义修复 | P0 | 资源不存在必须返回 `404`，创建接口文档与真实状态码一致 | backend |
| WS-02 API 暴露面治理 | P0 | 生产默认不暴露 Swagger / OpenAPI，dev/test 保持可用 | backend/gateway |
| WS-03 分页标准收口 | P1 | 外部 API 不再直接暴露 `IPage`，统一返回分页 DTO | backend |
| WS-04 回归验证 | P0 | 真实接口 + OpenAPI + 测试脚本 + 编译验证通过 | qa/owner |

---

## 3.1 System Analysis

### Problem Cluster

1. 契约层漂移
   - 资源不存在被折叠成 `400`
   - `@ResponseStatus` 与 OpenAPI `@ApiResponse` 不一致
2. 暴露面漂移
   - 网关白名单、Gateway Route、Springdoc 开关分别生效，容易出现“代码关闭但运行态误判未关闭”
3. DTO 漂移
   - Public/Admin 分页出口双标准并存
4. 验收口径漂移
   - `dev/test` 环境默认允许联调文档
   - `prod` 默认应关闭公开文档
   - 若不区分环境直接测 `/swagger-ui.html`，会把 `dev` 的正常 `302` 误判为治理失败

### Architectural Premise

- 本轮只做 API 工程化收口，不扩散到业务功能重构。
- 所有整改必须同时满足 `代码 -> 测试 -> 运行态` 三层一致。
- OpenAPI/Swagger 的验收必须按环境分层：
  - `dev/test`: 允许访问，`302 -> /webjars/swagger-ui/index.html` 视为正常
  - `prod/default-closed`: 不允许匿名访问，`401` 或等价拒绝策略视为达标

---

## 3.2 Task Packages And Agent Allocation

| Package | Workstream | Owner | Agent Role | Deliverable | Exit Gate |
|---|---|---|---|---|---|
| PKG-A 契约语义修复 | WS-01 | backend | `api_contract_semantics_fix` | `404` 语义落地 + 注解一致性测试 | 模块测试 + 运行态 `404` |
| PKG-B 暴露面治理 | WS-02 | gateway | `openapi_exposure_governance` / `openapi_302_rootcause` | 白名单/路由/配置开关收口 | `prod` 默认 `401` |
| PKG-C 分页标准收口 | WS-03 | backend | `pagination_standardization` | 统一 `PageResp` 出口 | 运行态结构统一 |
| PKG-D 回归矩阵 | WS-04 | qa | `runtime_regression_matrix` | 最小完整回归命令集 + 判定规则 | 真机/真容器回归通过 |

### Execution Order

1. 先完成 `PKG-A/B/C` 代码与单测
2. 再执行 `PKG-D` 运行态验收
3. 任何一条 `P0` 不通过，整体不得宣告闭环完成

---

## 3.3 Acceptance Matrix

| Gate | Scenario | Command / Evidence | Expected Result | Status |
|---|---|---|---|---|
| G-01 | 不存在设备查询 | `GET /api/v1/devices/not-exist-device-id` | `404`，业务码 `40401` | Passed |
| G-02 | Admin 分页结构 | `GET /api/v1/admin-console/devices/page` | `data.total/pageNo/pageSize/records` | Passed |
| G-03 | Dev 文档入口 | `dev` 容器请求 `/swagger-ui.html` | `302 -> /webjars/swagger-ui/index.html` | Passed |
| G-04 | Prod 默认关闭 | `prod` 探针容器请求 `/swagger-ui.html` | `401` | Passed |
| G-05 | Prod OpenAPI 聚合入口 | `prod` 探针容器请求 `/v3/api-docs/device` | `401` | Passed |

### Evidence Notes

- 当前本地主栈为 `SPRING_PROFILES_ACTIVE=dev`，因此 Swagger `302` 为正常联调行为，不构成暴露面治理失败。
- 已通过独立 `prod` 探针容器验证默认关闭口径：
  - `/swagger-ui.html` -> `401`
  - `/v3/api-docs/device` -> `401`
- 因此 WS-02 当前结论是：**代码无需继续修改，问题根因是验收环境口径混淆。**

---

## 4. Tasks

### WS-01 契约语义修复

1. 修复 `RequireHomePermissionAspect`
   - `DEVICE` 资源不存在时返回 `DEVICE_NOT_FOUND`
   - `OTA_TASK` 资源不存在时返回 `RESOURCE_NOT_FOUND` 或专用 not-found 码
   - 不再把“资源不存在”错误折叠成 `VALIDATE_FAILED`
2. 对齐创建接口的 OpenAPI 注解
   - 所有 `@ResponseStatus(HttpStatus.CREATED)` 的接口，`@ApiResponse` 必须标注 `201`
3. 增补测试
   - aspect 资源不存在分支
   - 关键 controller 的 swagger/annotation 规则

**验收标准**
- `GET/DELETE /api/v1/devices/{deviceId}` 查询不存在资源返回 `404`
- OpenAPI 文档里的创建接口响应码与真实响应码一致

### WS-02 API 暴露面治理

1. 为网关增加 OpenAPI 暴露配置开关
   - 默认：关闭公开文档入口
   - dev/test：显式开启
2. 收口白名单
   - `/v3/api-docs/**`
   - `/swagger-ui/**`
3. 保留开发联调能力
   - 配置示例
   - 最小测试覆盖

**验收标准**
- 默认配置下文档入口不可匿名访问
- dev/test 配置下 Swagger 可继续访问

### WS-03 分页标准收口

1. 引入统一分页响应 DTO
2. public page API 改为统一 DTO 返回
3. admin page API 与 public page API 保持字段一致
4. 更新测试与注解

**验收标准**
- 外部 API 不直接暴露 `IPage`
- 分页字段统一：`total/pageNo/pageSize/records`

### WS-04 回归验证

1. 编译验证
2. 模块级测试验证
3. 真实生命周期脚本回归

**验收标准**
- 相关模块测试通过
- 生命周期脚本不回退

---

## 5. Exit Criteria

- `P0` 问题全部关闭
- `P1` 分页标准收口完成
- 无新增 API 契约回归
- 文档、代码、测试三者一致
