# MyBatis-Plus 基础表结构与 CRUD 进阶开发梳理

## 1. 当前基线

### 1.1 已接入能力
- MyBatis-Plus 已统一接入，包含逻辑删除配置（`isDeleted`）与分页插件。
- 公共自动填充已启用：`createTime`、`updateTime`、`isDeleted`。
- 核心模块 `device/home/auth` 已具备基础 CRUD 与业务校验能力。

### 1.2 已落地表结构
- 设备域：`product_info`、`device_info`、`device_credential`
- 家庭域：`user_info`、`home_info`、`room_info`、`home_member`

### 1.3 本次已完成的进阶样板（device 模块）
- 新增设备分页查询能力：支持 `homeId/productKey/status` 条件过滤与分页。
- 新增设备更新接口：支持 `deviceName/roomId/gatewayId` 局部更新。
- 保持既有分层：Controller -> Service -> Mapper，不在 Controller 写业务逻辑。

## 2. 表结构进阶建议

### 2.1 必加唯一约束（防脏数据）
- `device_info` 建议增加唯一键：`uk_home_device_name(home_id, device_name, is_deleted)`，避免同家庭重复设备名。
- `room_info` 建议增加唯一键：`uk_home_room_name(home_id, name, is_deleted)`，避免同家庭重复房间名。

### 2.2 必加查询索引（支撑分页）
- `device_info` 建议增加复合索引：`idx_home_status_create(home_id, status, create_time)`。
- `device_info` 建议增加复合索引：`idx_product_status_create(product_key, status, create_time)`。
- `home_member` 建议增加索引：`idx_user_role(user_id, role)`，优化用户家庭列表查询。

### 2.3 DDL 管理规范
- 生产环境数据库变更必须通过 Flyway/Liquibase 版本化，不允许手工改表。
- DDL 与代码变更保持同一迭代发布，避免应用版本与表结构不匹配。

## 3. CRUD 进阶规范

### 3.1 C（创建）
- 在 Service 层做幂等校验和唯一性校验（不是在 Controller）。
- 创建后返回最小必要字段，敏感信息仅一次返回（如 `deviceSecret`）。

### 3.2 R（查询）
- 列表接口统一分页，默认 `pageNo=1`、`pageSize=20`、上限 `pageSize<=200`。
- 查询条件统一支持可选字段，严禁拼接 SQL 字符串。

### 3.3 U（更新）
- 使用“局部更新”策略，只更新显式传入字段。
- 关键状态字段（如设备状态）与普通字段分离，避免误更新。

### 3.4 D（删除）
- 统一逻辑删除，删除前后处理关联资源（如设备凭证、子设备绑定）要在事务内完成。
- 软删后数据仍可追溯，后续可扩展归档任务进行冷数据迁移。

## 4. 下一步开发任务（建议按 Sprint 执行）

### Sprint A：`device` 模块增强
- 增加设备重命名唯一性校验（同家庭不可重名）。
- 增加分页排序白名单（`createTime/status/deviceName`）。
- 补齐设备更新与分页接口的单元测试和集成测试。

### Sprint B：`home` 模块平移
- 为 `home/room/user` 增加统一分页查询接口。
- 补齐“局部更新”能力（例如家庭信息、房间名称更新）。
- 完善 `home_member` 的唯一性和角色边界校验。

### Sprint C：`auth` 模块强化
- 为凭证表增加审计字段查询接口（分页+条件）。
- 增加凭证轮换（rotate secret）流程与审计日志。

## 5. 验收标准
- 功能：分页、更新、逻辑删除与关联处理符合预期。
- 性能：`device` 列表分页接口在 10w 数据量下 P95 < 200ms（有索引前提）。
- 质量：新增接口必须有单测，关键链路补充集成测试脚本。
- 发布：DB 变更脚本、应用镜像、回滚方案三者同时可用。
