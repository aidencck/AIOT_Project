# AI-native M0 实施蓝图

状态：`planned`

## 1. 目标定义
- 在 `6-8 周` 内完成 AI-native 第一阶段最小闭环。
- 让平台先具备“看懂设备、解释状态、生成规则草案、支撑审批执行”的能力。
- 第一阶段重点验证 `设备诊断 Copilot` 和 `规则草案生成` 两个高价值场景。

## 2. 范围定义

### 包含
- 统一 `DeviceEvent` 事件模型
- `aiot-rule-engine` 规则定义、规则执行、命中日志
- `aiot-data-parser` 知识切片与索引构建
- `aiot-device-service` 设备数字体聚合查询
- `AI Copilot` 对话、诊断、规则草案接口
- 审批式执行和审计日志

### 不包含
- 端侧模型推理
- 复杂多模型编排
- 全自动高风险动作执行
- 跨产品线的大规模预测性维护

## 3. M0 成功标准
- 能查询设备上下文并给出可解释诊断结果
- 能基于设备事件或自然语言生成规则草案
- 能对规则草案进行预览、审批和发布
- 能将诊断、规则、执行结果与文档链接关联到统一项目管理视图

## 4. 里程碑拆解

### Milestone 1：事件底座
- 目标：统一事件入口与标准结构
- 输出：
- `DeviceEvent` 领域对象
- 设备上下线事件接入
- 影子变化事件接入
- 事件落表与查询接口
- 验收：
- 关键事件可追踪
- 事件带 `device_id/home_id/trace_id/event_type`

### Milestone 2：规则中心
- 目标：补齐最小可用规则引擎
- 输出：
- 规则定义表与发布状态
- 阈值、离线、频发三类规则
- 规则执行器与命中日志
- 规则预览接口
- 验收：
- 样例事件可稳定命中规则
- 每次命中有可追踪日志

### Milestone 3：知识中心
- 目标：把设备知识变成可检索上下文
- 输出：
- `thingModelJson` 解析器
- FAQ / SOP 文档导入
- 切片与索引构建任务
- 检索调试接口
- 验收：
- 诊断请求能返回相关知识片段
- 检索结果可带来源信息

### Milestone 4：Copilot 与审批
- 目标：形成最小智能闭环
- 输出：
- 会话接口
- 诊断接口
- 规则草案生成接口
- 审批与执行接口
- 验收：
- 能对高频问题输出结构化结论
- 高风险动作不能绕过审批

## 5. 模块落点

### `aiot-auth-service`
- 接入设备上下线 Webhook 事件投递
- 标准化事件负载并写入事件中心

### `aiot-device-service`
- 增加 `GET /api/v1/devices/{deviceId}/digital-profile`
- 聚合产品物模型、在线状态、设备影子、最近事件

### `aiot-rule-engine`
- 新增规则定义、规则执行、规则草案、审批编排
- 成为第一阶段 AI-native 中心模块

### `aiot-data-parser`
- 负责知识切片、索引构建、结构化上下文输出

## 6. 关键接口
- `POST /api/v1/ai/copilot/sessions`
- `POST /api/v1/ai/copilot/sessions/{sessionId}/messages`
- `POST /api/v1/ai/rule-drafts`
- `POST /api/v1/ai/rule-drafts/{draftId}/preview`
- `POST /api/v1/ai/rule-drafts/{draftId}/submit`
- `POST /api/v1/ai/approvals/{approvalId}/approve`
- `GET /api/v1/devices/{deviceId}/digital-profile`
- `GET /api/v1/devices/{deviceId}/events`

## 7. 风险与控制
- 知识命中质量不足：先限定设备品类和高频问题域
- 规则误触发：先做预览和灰度，不直接自动执行
- 权限越界：所有 Copilot 会话强制绑定 `user_id/home_id`
- 输出不可信：诊断结果必须带引用来源和风险等级

## 8. 验收口径
- 10 个典型问题中，诊断结果可读且可引用来源
- 至少 3 类规则支持草案生成与预览
- 审批流具备完整审计日志
- GitHub Projects 中可按 Epic/Milestone/Sprint 跟踪工作项进度

## 9. 关联文档
- 产品路线图：[`../product/AIoT_AI_Native_Product_Roadmap.md`](../product/AIoT_AI_Native_Product_Roadmap.md)
- 交付拆解：[`ai-native-delivery-backlog.md`](ai-native-delivery-backlog.md)
