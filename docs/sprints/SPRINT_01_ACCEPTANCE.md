# Sprint 1 需求定义与验收单

## 功能模块：空间与用户域 MVP (Space & User)

### 1. 业务背景
搭建 AIoT 平台的用户鉴权体系，并构建“家庭(Home)”与“房间(Room)”的层级拓扑，实现基于角色的家庭成员权限控制(RBAC)。

### 2. 验收标准 (Acceptance Criteria)

#### 2.1 用户鉴权 (Auth)
*   **[AC-1.1]**: 当用户使用正确手机号与密码调用 `/api/v1/users/login` 时，必须返回包含 userId 和签名的 JWT Token。
*   **[AC-1.2]**: Token Secret 的长度必须大于 32 字节，以防弱密钥被暴力破解。

#### 2.2 家庭管理 (Home)
*   **[AC-2.1]**: 当用户创建家庭时，系统必须自动将该用户设为该家庭的 `Owner (Role=1)`。
*   **[AC-2.2]**: 只有角色为 `Owner (1)` 的成员才能删除家庭，Admin 或 Member 删除必须抛出 `FORBIDDEN` 异常。

#### 2.3 房间管理与 RBAC (Room & RBAC)
*   **[AC-3.1]**: 当角色为 `Member (3)` 的用户尝试创建或删除房间时，AOP 鉴权拦截器必须拒绝请求并返回 403。
*   **[AC-3.2]**: 只有属于该家庭的成员（无论角色）才能查询该家庭下的房间列表。

#### 2.4 缓存一致性 (Cache)
*   **[AC-4.1]**: 当触发删除家庭等写操作时，必须使用“失效模式” (`redis.delete(key)`) 清理对应家庭成员的权限缓存，严禁在事务内同步重建缓存。

### 3. 验证结果 (Verification / Sign-off)

*   [x] `[AC-1.1]` - 已实现
*   [x] `[AC-1.2]` - 已实现 (`JwtUtils.java` 包含 `@PostConstruct` 校验)
*   [x] `[AC-2.1]` - 已实现
*   [x] `[AC-2.2]` - 已通过 `@RequireHomeRole` 注解拦截
*   [x] `[AC-3.1]` - 已通过 `@RequireHomeRole` 注解拦截
*   [x] `[AC-3.2]` - 已实现
*   [x] `[AC-4.1]` - 已实现 (重构后的 Cache Aside 模式)
