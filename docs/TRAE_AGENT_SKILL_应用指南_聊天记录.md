# Trae 入门到专家：配置与使用指南（AIOT-java）

## 0. Trae 工具系统基线（V1）

> 本节基于当前机器与仓库的实际扫描结果，目标是把“配置现状”升级为“可执行工具系统”。

### 0.1 系统分层与职责

| 层级 | 组件 | 当前状态 | 系统职责 |
|---|---|---|---|
| L1 策略层 | Skill | 已启用 2 个 | 统一方法论、任务拆解、验收口径 |
| L2 执行层 | Agent 子类型 | 可用 | 按目标并行执行（检索、实现、测试、审计） |
| L3 工具层 | MCP Servers | 多场景可用 | 提供浏览器、容器、K8s、抓取、记忆等能力 |
| L4 治理层 | 项目级 `.trae/` | 未落库 | 模板、知识、清单、门禁与复盘沉淀 |

### 0.2 Skill 子系统（实测 -> 标准化）

- 实测路径：`~/.trae/skills/`
- 已安装：
  - `architecture-architect`
  - `project-manager`
- 禁用清单（`~/.trae/skill-config.json`）：
  - `youtube-downloader`
  - `linkedin-content`
  - `twitter-automation`
- 标准化定义：
  - `architecture-architect` 负责前置边界（Premise/Constraints/Boundaries/Endgame）与架构约束。
  - `project-manager` 负责里程碑、任务卡、状态推进与交付留痕。
  - 缺口是“专项 Skill 模板化”不足（安全、发布、测试、性能尚未项目化沉淀）。
- 安装与下载核心来源（通用）：
  - 本机已安装来源（最高优先）：`~/.trae/skills/*/SKILL.md`
  - 运行时可调用来源：Trae 会在会话中暴露 `available_skills` 列表（以会话实时能力为准）
  - 扩展来源（参考）：团队自建 Skill 仓库或模板目录，建议镜像到项目 `.trae/templates/skills/`

### 0.3 Agent/MCP 子系统（实测 -> 基线）

- 实测路径：`~/.trae/mcps/`
- 场景实例：已检测到 `s_ai_workflow-*`、`s_open_new-*`、`s_macinit-*`、`s_smarthome_APP-*` 等。
- 常见 MCP 组合（多数场景）：
  - `integrated_browser`
  - `mcp_Docker`
  - `mcp_Firecrawl`
  - `mcp_Kubernetes`
  - `mcp_Playwright`
  - `mcp_Memory`
  - `mcp_Sequential_Thinking`
- 差异组件（部分场景）：
  - `mcp_GitHub`
  - `mcp_feishu`
- 基线结论：
  - 工具广度足够构成“研发执行系统”。
  - 最大风险在于“场景能力不一致”，会导致同类任务在不同场景下输出不可预测。
- 安装与下载核心来源（通用）：
  - 本机已注册来源（最高优先）：`~/.trae/mcps/<scene>/solo_coder/<mcp_name>/tools/*.json`
  - 能力发现来源：`SERVER_METADATA.json` + `tools/*.json`（可直接判断该场景是否可用）
  - 外部扩展来源（参考）：
    - Trae-Ralph 配置指南：[CONFIGURATION.md](https://github.com/ylubi/Trae-Ralph/blob/main/docs/CONFIGURATION.md)
    - Trae-Ralph 文档导航：[README.md](https://github.com/ylubi/Trae-Ralph/blob/main/docs/README.md)

### 0.4 最小可执行制度（先跑起来）

1. 每次任务必须先选 Skill：架构类走 `architecture-architect`，推进类走 `project-manager`。
2. 每次并行 Agent 不超过 4 个，且按服务边界切分，禁止同文件区并发修改。
3. 每个 Agent 回传统一四段：`结论 / 证据 / 改动建议 / 阻塞项`。
4. 每次任务结束必须有验收证据：测试输出、脚本日志或诊断结果之一。
5. 每周一次场景健康检查：核对 MCP 必选清单是否齐备。

### 0.5 一站式来源管理（减少来回查找）

| 类型 | 一站式入口 | 主要用途 | 维护动作 |
|---|---|---|---|
| Skill 安装清单 | `~/.trae/skills/` | 查看本机已安装 Skill | 每周核对新增/失效 Skill |
| Skill 开关清单 | `~/.trae/skill-config.json` | 管理禁用项 | 与团队规范对齐禁用原因 |
| MCP 注册清单 | `~/.trae/mcps/` | 查看各场景 Agent 工具能力 | 统一必选 MCP 基线 |
| 项目操作入口 | `docs/TRAE_AGENT_SKILL_应用指南_聊天记录.md` | 统一方法与流程 | 每次流程变更后同步更新 |
| 项目任务证据 | `docs/tasks/` + `docs/runbooks/` | 留痕与复盘 | 与脚本输出绑定归档 |

已落地统一基线（项目内）：
1. `.trae/sources/INDEX.md`：集中登记 Skill/MCP 来源与负责人。
2. `.trae/checklists/MCP_BASELINE.md`：定义“所有场景必须启用”的 MCP 列表。
3. `.trae/templates/AGENT_TASK_CARD.md`：统一任务输入输出格式。

### 0.3 与 AIOT-java 项目的落地现状

- 项目内尚未发现仓库级 `.trae/` 目录（即缺少项目级 Skill/Agent 运行约束与模板库）。
- 已存在 Agent 化协作文档：`docs/tasks/DELIVERY_ROLLBACK_AGENT_ASSIGNMENT.md`，说明流程层有“Team Agent 分工”实践。
- `docs/` 内 Trae 相关文档目前集中在本文件，尚未形成“配置基线 + 操作手册 + 复盘模板”的成套结构。

### 0.4 分析框架（先搭骨架，后填数据）

- 维度 A：配置完整性
  - Skill 安装/禁用/版本
  - MCP 场景覆盖率与一致性
- 维度 B：流程落地度
  - 是否有统一任务卡、验收模板、复盘模板
  - 是否落到发布、回滚、安全、性能等关键链路
- 维度 C：执行闭环度
  - 是否有证据产物（脚本输出、测试报告、演练记录）
  - 是否形成“问题->修复->验证->沉淀”循环
- 维度 D：治理与复用度
  - 是否建立项目级 `.trae/knowledge`、`.trae/templates`、`.trae/checklists`
  - 是否能在新任务中直接复用，减少重复沟通

### 0.5 当前判断（V0）

- 优势：本机 Agent 工具面强、项目已有 Agent 分工实践。
- 短板：项目级 Trae 约束目录缺失，Skill 体系偏薄，场景 MCP 能力不统一。
- 立即动作（最小可落地）：
  - 在仓库创建 `.trae/knowledge`、`.trae/templates`、`.trae/checklists` 三层骨架；
  - 将发布回滚场景先沉淀为标准模板（任务卡 + 验收 + 复盘）；
  - 统一约定“默认场景必须启用的 MCP 清单”。

## 文档目标

- 本文档用于把 Trae 的使用从“会聊”升级到“可交付”。
- 覆盖三部分：`基础配置`、`文档位置`、`核心原理`。
- 所有方法默认适配当前仓库：`AIOT-java`（Maven 多模块微服务）。

## 一、基础配置

### 1) 本地环境基线

- JDK：`17+`（项目主线）。
- Maven：`3.8+`，建议统一团队 `~/.m2/settings.xml`（镜像、仓库、本地仓路径）。
- Docker：用于 `docker compose` 中间件与联调环境。
- IDE：确保 Trae 能读取工作区并执行工具链（Read/Grep/RunCommand 等）。

### 2) Trae 相关配置位置（常用）

- 项目文档主目录：`/Users/aiden/Projects/AIOT-java/docs/`
- 项目 Wiki 镜像目录：`/Users/aiden/Projects/AIOT-java/docs/wiki/`
- 如果使用 Trae-Ralph 的独立配置：
  - Mac：`/Users/<用户名>/.trae-ralph/config.json`
  - Windows：`C:\Users\<用户名>\.trae-ralph\config.json`
  - Linux：`/home/<用户名>/.trae-ralph/config.json`

### 3) 项目内建议补充的 Trae 约定

- 建议维护一个仓库级约定目录：`.trae/`
- 推荐最小结构：
  - `.trae/knowledge/`：沉淀排障经验、链路地图、常见风险。
  - `.trae/templates/`：统一任务卡、提示词模板、验收模板。
  - `.trae/checklists/`：发布、回滚、安全、性能门禁清单。
- 目的：减少重复沟通，让 Agent 输出直接可执行。

## 二、文档位置（本仓库）

### 1) 路线与现状入口

- 项目总览：`README.md`
- 项目状态：`docs/PROJECT_STATUS.md`
- Wiki 主入口：`docs/wiki/README.md`

### 2) 设计与治理文档

- 架构设计：`docs/architecture_design.md`
- 开发规范：`docs/development_standards.md`
- 发布与回滚：`docs/runbooks/`、`docs/tasks/DELIVERY_ROLLBACK_AGENT_ASSIGNMENT.md`

### 3) 自动化脚本入口

- 发布：`scripts/deploy_single_service.sh`
- 回滚：`scripts/rollback_single_service.sh`
- 健康检查：`scripts/verify_release_health.sh`
- 服务通信联调：`scripts/test_service_communication.sh`
- 配网与 webhook 联测：`scripts/test_provision_and_webhook.sh`

## 三、核心原理（Trae 使用模型）

### 1) Skill 与 Agent 的分工

- `Skill`：方法论模板，负责“定边界、定验收、定节奏”。
- `Agent`：执行单元，负责“找证据、做改动、跑验证、回结果”。
- 主线程（你）：负责“并行编排、冲突消解、最终验收”。

### 2) 三段式执行闭环

1. `先定标准`：目标、范围、禁区、验收口径。
2. `再并行执行`：按服务边界并行调度 Agent（最多 4 个）。
3. `最后收敛验收`：统一输出变更证据、风险残留、下一步。

### 3) 高质量输出规则

- 每个 Agent 只接一个清晰目标，不做“全仓库泛分析”。
- 回传格式固定：`结论 / 证据 / 改动建议 / 阻塞项`。
- 证据必须可复核：文件、脚本、测试结果、诊断输出。
- 没有证据的结论，一律视为假设，不可直接决策。

## 四、AIOT-java 的实战调度模板

### 1) 场景 A：安全链路加固（Gateway/Auth/Home/Device）

- 目标：校验并补齐 `JWT`、`X-Internal-Token`、Webhook 签名与重放防护。
- Skill：`architecture-architect` 输出入口清单与边界约束。
- Agent：
  - `search`：定位配置键、过滤器、`/api/v1/internal/**`。
  - `backend-architect`：修复默认弱配置与校验不一致。
  - `api-test-pro`：鉴权通过/失败/重放攻击用例回归。
- 验收：失败路径统一 `Result`，且测试结果可复现。

### 2) 场景 B：事件流可靠性回归（Device/Rule/Shadow）

- 目标：验证 `Redis Stream + ACK + pending recovery + DLQ` 可恢复性。
- Skill：`project-manager` 拆成故障注入、观测、恢复、结论四步。
- Agent：
  - `search`：定位 Scheduler/Subscriber/DLQ 指标代码。
  - `backend-architect`：补齐异常 ACK 与重试策略。
  - `performance-expert`：输出积压与恢复耗时基线。
- 验收：pending 回落、DLQ 增长可解释、恢复耗时可量化。

### 3) 场景 C：发布回滚演练闭环

- 目标：把“有脚本”升级为“可重复演练并留档”。
- Skill：`project-manager` 定义演练 DoD 和留档模板。
- Agent：
  - `devops-architect`：串联发布、回滚、健康验证脚本。
  - `search`：核对脚本、runbook、workflow 一致性。
  - `comprehensive-code-analyzer`：检查门禁缺口。
- 验收：完成一次“发布失败->回滚->恢复”全流程记录。

## 五、可直接复制的提示词

### 1) Skill 启动词

```text
请基于 AIOT-java 当前状态，输出本次任务的里程碑、依赖、风险、验收标准；
要求可执行，不要泛化建议。
```

### 2) Agent 任务卡模板

```text
目标：{一句话}
范围：{服务/模块}
禁区：{不允许改动项}
输出：结论 / 证据 / 改动建议 / 阻塞项
停止条件：完成 {验收标准} 即停止
```

### 3) 收敛模板

```text
请汇总所有子任务结果，按 P0/P1/P2 排序，并输出：
1) 变更摘要
2) 风险残留
3) 验收结果
4) 下一步行动
```

## 六、常见问题与避坑

- 问题：Agent 输出很多观点但不可落地。
- 处理：强制要求“证据 + 可执行动作 + 停止条件”。

- 问题：并行冲突导致反复合并。
- 处理：按服务边界切分，避免同文件区域并发改动。

- 问题：只改代码不回归。
- 处理：至少覆盖鉴权失败、超时重试、异常返回一致性。

- 问题：文档与代码漂移。
- 处理：变更时同步更新 `docs/` 与 `docs/wiki/` 对应页面。

## 七、执行清单（入门到专家）

1. 对齐环境：JDK/Maven/Docker/Trae 可用。
2. 对齐入口：先读 `README.md`、`docs/PROJECT_STATUS.md`、`docs/wiki/README.md`。
3. 对齐边界：先调 Skill 拆任务，再调 Agent 并行执行。
4. 对齐证据：所有结论必须绑定代码或测试证据。
5. 对齐复盘：每次任务完成后沉淀到 `.trae/knowledge/` 和项目文档。
