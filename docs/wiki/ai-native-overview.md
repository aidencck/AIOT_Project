# AI-native 规划总览

状态：`planned`

## 目标
- 把当前 AIoT 后端从“已具备设备事务能力”升级为“具备事件理解、知识增强、规则协同、智能辅助”的 AI-native 平台。
- 统一 Wiki 入口，避免产品路线图、实施蓝图、Sprint Backlog 分散在不同目录。
- 作为 GitHub Projects 中 Epic、Milestone、Sprint 任务的文档锚点。

## 文档导航
- 产品路线图：[`../product/AIoT_AI_Native_Product_Roadmap.md`](../product/AIoT_AI_Native_Product_Roadmap.md)
- M0 实施蓝图：[`ai-native-m0-blueprint.md`](ai-native-m0-blueprint.md)
- 交付拆解与 Backlog：[`ai-native-delivery-backlog.md`](ai-native-delivery-backlog.md)

## 建设范围
- `M0`：事件模型、规则引擎最小化产品化、知识切片、设备诊断 Copilot
- `M1`：告警联动、工单联动、知识反馈、规则审批
- `M2`：半自动执行、灰度治理、群组与场景级分析
- `M3`：多角色 Copilot、策略推荐、平台级智能运营

## 统一交付主链路
`设备事件` -> `标准化事件` -> `规则命中 / AI 诊断` -> `建议 / 草案 / 告警` -> `审批 / 执行` -> `通知 / 工单 / 影子回写` -> `知识沉淀`

## GitHub Projects 管理建议
- `Epic`：按能力域拆分，如事件中心、规则中心、知识中心、Copilot、审批执行
- `Milestone`：按版本拆分，当前优先 `AI-native M0`
- `Sprint`：按双周迭代拆分，形成 `Sprint 1 ~ Sprint 4`
- `Issue 类型`：Epic、Story、Task、Spike、Bug
- `文档关联`：每个 Epic 与 Story 都附对应 Wiki 链接，确保“任务”和“设计”双向可追溯
