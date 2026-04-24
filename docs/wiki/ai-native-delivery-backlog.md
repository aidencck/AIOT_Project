# AI-native 交付拆解与 Backlog

状态：`planned`

## 1. 管理模型
- `Epic`：能力域
- `Milestone`：版本目标
- `Sprint`：双周迭代
- `Story/Task`：具体交付项

## 2. 当前版本
- 目标版本：`AI-native M0`
- 周期建议：`4 个 Sprint / 每个 Sprint 2 周`
- 主目标：形成 `事件 -> 诊断 -> 规则草案 -> 审批发布` 最小闭环

## 3. Epic 列表

| Epic ID | Epic 名称 | 目标 | 关联文档 |
| --- | --- | --- | --- |
| EPIC-AIN-01 | 事件中心 | 建立统一 `DeviceEvent` 模型与事件接入 | [`ai-native-m0-blueprint.md`](ai-native-m0-blueprint.md) |
| EPIC-AIN-02 | 规则中心 | 建立规则定义、执行、预览与命中日志 | [`ai-native-m0-blueprint.md`](ai-native-m0-blueprint.md) |
| EPIC-AIN-03 | 知识中心 | 建立知识切片、索引与检索能力 | [`../product/AIoT_AI_Native_Product_Roadmap.md`](../product/AIoT_AI_Native_Product_Roadmap.md) |
| EPIC-AIN-04 | Copilot | 建立设备诊断问答与规则草案生成 | [`ai-native-overview.md`](ai-native-overview.md) |
| EPIC-AIN-05 | 审批执行 | 建立审批流、执行记录、审计追踪 | [`ai-native-m0-blueprint.md`](ai-native-m0-blueprint.md) |

## 4. Milestone 列表

| Milestone ID | 名称 | Sprint | 验收标准 |
| --- | --- | --- | --- |
| M0-MS1 | 事件底座完成 | Sprint 1 | 设备事件可统一落表与查询 |
| M0-MS2 | 规则中心可用 | Sprint 2 | 三类规则可定义、可命中、可追踪 |
| M0-MS3 | 知识检索可用 | Sprint 3 | 诊断请求可获取有效知识片段 |
| M0-MS4 | Copilot 闭环上线 | Sprint 4 | 设备诊断与规则草案可走审批流程 |

## 5. Sprint Backlog

### Sprint 1：事件底座
- Story：设计 `DeviceEvent` 数据结构与状态流转
- Task：新增 `ai_device_event` 表
- Task：实现设备上下线事件接入
- Task：实现影子变化事件接入
- Task：提供事件查询 API
- Task：补 TraceId 与审计字段

### Sprint 2：规则中心
- Story：建立规则定义与版本管理
- Task：新增 `ai_rule_definition`
- Task：新增 `ai_rule_execution_log`
- Task：实现阈值规则执行器
- Task：实现离线规则执行器
- Task：实现频发规则执行器
- Task：实现规则预览接口

### Sprint 3：知识中心
- Story：把产品物模型转成知识片段
- Task：解析 `thingModelJson`
- Task：导入 FAQ / SOP 文档
- Task：新增 `ai_knowledge_document`
- Task：新增 `ai_knowledge_chunk`
- Task：实现切片与索引构建任务
- Task：实现知识检索调试接口

### Sprint 4：Copilot 与审批
- Story：实现设备诊断 Copilot MVP
- Task：新增 `ai_copilot_session`
- Task：新增 `ai_copilot_message`
- Task：实现会话创建与消息接口
- Task：实现诊断编排与答案结构化输出
- Task：实现规则草案生成
- Task：实现审批接口与执行记录

## 6. GitHub Projects 字段映射建议

| 字段 | 建议值 |
| --- | --- |
| Title | 使用 `Epic/Story/Task` 前缀 + 编号 + 简短描述 |
| Status | `Todo / In Progress / In Review / Done` |
| Priority | `P0 / P1 / P2` |
| Iteration | `Sprint 1 / Sprint 2 / Sprint 3 / Sprint 4` |
| Milestone | `AI-native M0` |
| Labels | `ai-native`, `epic`, `story`, `task`, `backend`, `wiki-linked` |
| Repository | `aidencck/AIOT_Project` |
| Body | 包含目标、验收标准、关联 Wiki 文档链接、子任务清单 |

## 7. GitHub Issue 标题建议
- `[Epic][AIN-01] 事件中心与 DeviceEvent 统一建模`
- `[Epic][AIN-02] 规则中心最小化产品化`
- `[Epic][AIN-03] 设备知识中心与检索构建`
- `[Epic][AIN-04] 设备诊断 Copilot MVP`
- `[Epic][AIN-05] 审批执行与审计闭环`

## 8. GitHub Issue Body 模板

```md
## 目标
- 

## 范围
- 

## 验收标准
- 

## 关联文档
- Wiki:
- Roadmap:

## 子任务
- [ ] 
- [ ] 
```

## 9. 推荐创建顺序
1. 创建 `Milestone: AI-native M0`
2. 创建 5 个 Epic Issue
3. 创建 Sprint 1~4 对应 Story/Task
4. 将 Issue 加入 GitHub Projects
5. 为每个 Issue 关联 Wiki 文档链接

## 10. 自动化脚本
- 远程同步脚本：[`../../scripts/sync_ai_native_m0_github_project.py`](../../scripts/sync_ai_native_m0_github_project.py)
- 默认行为：创建/复用 `AI-native M0` Milestone、创建标签、同步 Epic 与 Sprint Story
- 可选参数：
- `GITHUB_REPO`：默认 `aidencck/AIOT_Project`
- `GITHUB_OWNER`：默认 `aidencck`
- `GITHUB_PROJECT_NUMBER`：可选，提供后会尝试把 Issue 加入 GitHub Projects
