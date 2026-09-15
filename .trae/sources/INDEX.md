# Trae Sources Index

## 目标

- 提供 Skill/Agent/MCP 的一站式来源索引，避免来回查找。
- 以“本机实测路径优先，外部文档次之”为原则。

## 本机来源（优先级 P0）

| 类型 | 路径 | 用途 | 维护人 |
|---|---|---|---|
| Skill 安装目录 | `~/.trae/skills/` | 查看已安装 Skill 定义（`SKILL.md`） | TL/平台 |
| 项目远程运维 Skill | `.trae/skills/aiot-remote-dev/SKILL.md`（仓库版，**SSOT**）；`~/.trae/skills/aiot-remote-dev/SKILL.md`（本机版，同步副本） | SSH 直连 `/opt/aiot-cloud` 的巡检/发布/回滚/日志 | TL/平台 |
| Skill 开关配置 | `~/.trae/skill-config.json` | 管理禁用 Skill | TL/平台 |
| MCP 注册目录 | `~/.trae/mcps/` | 查看场景下可用 MCP 与工具清单 | TL/平台 |
| MCP 元信息 | `~/.trae/mcps/<scene>/solo_agent/<mcp>/SERVER_METADATA.json` | 校验服务名与可用性 | TL/平台 |
| MCP 工具清单 | `~/.trae/mcps/<scene>/solo_agent/<mcp>/tools/*.json` | 校验具体工具能力 | TL/平台 |

## 项目来源（优先级 P1）

| 类型 | 路径 | 用途 | 更新触发 |
|---|---|---|---|
| 操作总文档 | `docs/TRAE_AGENT_SKILL_应用指南_聊天记录.md` | 使用规范与流程 | 流程变更 |
| 任务证据 | `docs/tasks/` | Agent 执行分工与留痕 | 任务完成 |
| 运行手册 | `docs/runbooks/` | 发布/回滚/演练步骤 | 演练后 |
| MCP 基线清单 | `.trae/checklists/MCP_BASELINE.md` | 场景必选 MCP 约束（已与实际可用对齐） | 每周巡检 |
| Skill 白名单清单 | `.trae/checklists/SKILL_SCOPE.md` | 项目 Skill 白名单/禁用治理 | Skill 增删 |
| 发布回滚闭环清单 | `.trae/checklists/RELEASE_ROLLBACK_LOOP.md` | 5 阶段闭环门禁与判据 | 发布/演练变更 |
| Agent 任务卡模板 | `.trae/templates/AGENT_TASK_CARD.md` | 统一输入输出格式 | 模板升级 |
| 复盘模板 | `.trae/templates/RETROSPECTIVE_TEMPLATE.md` | 任务/事故/演练复盘沉淀 | 复盘 |
| 链路地图 | `.trae/knowledge/architecture-map.md` | 8 服务+中间件+AI 诊断链路 | 架构变更 |
| 已知风险库 | `.trae/knowledge/known-risks.md` | P0/P1 风险与教训沉淀 | 复盘/事故 |
| 基线巡检脚本 | `scripts/check_trae_baseline.sh` | 四子系统基线自动核对 | 每周巡检/CI |
| 进度快照脚本 | `scripts/gen_status_snapshot.sh` | 生成 `.trae/status_snapshot.json` 供编排 Agent 感知进度 | 每轮编排启动前 |
| 运维 Skill（版本化） | `.trae/skills/aiot-remote-dev/SKILL.md` | G0–G7 运维门禁 | Gate 变更 |
| 安全审计 Skill | `.trae/skills/aiot-security-audit/SKILL.md` | 只读安全审计门禁 | 安全策略变更 |
| Schema 校验 Skill | `.trae/skills/aiot-schema-validator/SKILL.md` | AI 诊断 schema/枚举校验 | 契约变更 |
| 测试覆盖 Skill | `.trae/skills/aiot-test-coverage/SKILL.md` | 测试覆盖缺口分析 | 测试策略变更 |
| 系统边界 Skill | `.trae/skills/aiot-system-boundary/SKILL.md` | 系统边界/依赖六维分析 | 架构变更 |
| 产品 Agent Skill | `.trae/skills/product-agent/SKILL.md` | 需求→PRD→闭环矩阵 | 需求方法论变更 |
| 项目文档 Agent Skill | `.trae/skills/project-doc-agent/SKILL.md` | 文档分层(L0–L4)/工程规范/Wiki 同步 | 文档体系变更 |

## 外部来源（优先级 P2）

- Trae-Ralph 配置说明：
  - [CONFIGURATION.md](https://github.com/ylubi/Trae-Ralph/blob/main/docs/CONFIGURATION.md)
- Trae-Ralph 文档导航：
  - [README.md](https://github.com/ylubi/Trae-Ralph/blob/main/docs/README.md)

## 使用规则

1. 先查 P0（本机实际可用），再查 P1（项目规范），最后查 P2（外部资料）。
2. 任何“新增/禁用 Skill”都要同步记录到本索引和周报。
3. 任何“场景 MCP 能力变化”都要同步更新 `MCP_BASELINE.md`。
4. 双份维护的 Skill（如 `aiot-remote-dev`）以**仓库版**为单一事实源，本机版为同步副本；改动先改仓库版，再同步本机版。
