# Trae Sources Index

## 目标

- 提供 Skill/Agent/MCP 的一站式来源索引，避免来回查找。
- 以“本机实测路径优先，外部文档次之”为原则。

## 本机来源（优先级 P0）

| 类型 | 路径 | 用途 | 维护人 |
|---|---|---|---|
| Skill 安装目录 | `~/.trae/skills/` | 查看已安装 Skill 定义（`SKILL.md`） | TL/平台 |
| Skill 开关配置 | `~/.trae/skill-config.json` | 管理禁用 Skill | TL/平台 |
| MCP 注册目录 | `~/.trae/mcps/` | 查看场景下可用 MCP 与工具清单 | TL/平台 |
| MCP 元信息 | `~/.trae/mcps/<scene>/solo_coder/<mcp>/SERVER_METADATA.json` | 校验服务名与可用性 | TL/平台 |
| MCP 工具清单 | `~/.trae/mcps/<scene>/solo_coder/<mcp>/tools/*.json` | 校验具体工具能力 | TL/平台 |

## 项目来源（优先级 P1）

| 类型 | 路径 | 用途 | 更新触发 |
|---|---|---|---|
| 操作总文档 | `docs/TRAE_AGENT_SKILL_应用指南_聊天记录.md` | 使用规范与流程 | 流程变更 |
| 任务证据 | `docs/tasks/` | Agent 执行分工与留痕 | 任务完成 |
| 运行手册 | `docs/runbooks/` | 发布/回滚/演练步骤 | 演练后 |
| MCP 基线清单 | `.trae/checklists/MCP_BASELINE.md` | 场景必选 MCP 约束 | 每周巡检 |
| Agent 任务卡模板 | `.trae/templates/AGENT_TASK_CARD.md` | 统一输入输出格式 | 模板升级 |

## 外部来源（优先级 P2）

- Trae-Ralph 配置说明：
  - [CONFIGURATION.md](https://github.com/ylubi/Trae-Ralph/blob/main/docs/CONFIGURATION.md)
- Trae-Ralph 文档导航：
  - [README.md](https://github.com/ylubi/Trae-Ralph/blob/main/docs/README.md)

## 使用规则

1. 先查 P0（本机实际可用），再查 P1（项目规范），最后查 P2（外部资料）。
2. 任何“新增/禁用 Skill”都要同步记录到本索引和周报。
3. 任何“场景 MCP 能力变化”都要同步更新 `MCP_BASELINE.md`。
