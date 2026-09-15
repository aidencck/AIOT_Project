# Sprint [X] 需求定义与验收单

## 功能模块：[填写模块名称，例如：智能场景联动引擎]

### 1. 业务背景
*   [描述为什么需要这个功能，解决什么痛点。]

### 2. 验收标准 (Acceptance Criteria)

*   **[AC-1]**: 当... (Given) ... 触发... (When) ... 应该... (Then)。
*   **[AC-2]**: 对于边界异常情况 (例如网络超时或参数为 null)，系统应当...。
*   **[AC-3]**: 必须满足安全性约束，例如：非本家庭成员 (role > 2) 无权操作...。

### 3. 验证结果 (Verification / Sign-off)

*   [ ] `[AC-1]` 自动化测试已通过 (Test Class: `xxxServiceTest.java#testAC1()`)
*   [ ] `[AC-2]` 边界测试与全局异常处理已拦截
*   [ ] `[AC-3]` 声明式 RBAC 注解 `@RequireHomeRole` 已在 Controller 生效
*   [ ] 人工 Review 代码架构与 VibeCoding 规范对齐

---
> ⚠️ **AI Developer 注意**：在上述 [AC] 对应的自动化测试未全部编写并运行通过之前，禁止进入下一阶段开发！
