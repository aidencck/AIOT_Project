---
name: "aiot-test-coverage"
description: "分析 AIoT 测试覆盖缺口与缺失的边界/错误分支用例（Java JUnit、Python pytest、Vue Vitest）。当用户询问测试覆盖、缺失测试、测试完整性时触发。"
---

# AIoT 测试覆盖分析（Test Coverage）

## 定位
只读覆盖分析，绑定项目 Agent [`test-coverage-analyzer`](../agents/test-coverage-analyzer.md)。定位缺失的 happy path、边界条件、错误分支、Outbox `dead_letter`、schema 归一化用例。

## 测试布局（事实源）
- Java：`aiot-*/src/test`（JUnit）— services、rule-engine、shadow-service、mqtt-adapter
- Python：`edge/aiot-edge-agent/tests`、`edge/device-simulator/tests`、`training/tests`（pytest）
- Vue：`aiot-admin-web/src/**/*.test.ts`（Vitest）

## 执行门禁（硬性）
1. 委托 `test-coverage-analyzer` Agent（read-only），回传格式：`结论 / 证据 / 改动建议 / 阻塞项`
2. 输出覆盖矩阵：`Module | Source | Test file | Coverage | Gaps`
3. 末尾必须给：覆盖摘要 + Top N 高风险未测区域 + 优先级测试 backlog（P0/P1/P2）

## 验收口径
- 每个核心模块必须有对应测试文件（Java/Python/Vue 三类齐全）
- 缺失项按「模块是否在活跃链路（Gateway/Auth/Device/Home/Rule/Shadow）」定优先级，活跃链路缺失 = P0
- 关键边界（鉴权失败、超时重试、异常返回一致性、死信）必须有用例
