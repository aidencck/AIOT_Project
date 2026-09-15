<!--
  Skill 工程化模板（SKILL_TEMPLATE）

  用法：
    1. 复制下方「模板本体」到 .trae/skills/<skill-name>/SKILL.md，替换所有 <...> 占位符。
    2. 同步 .trae/checklists/SKILL_SCOPE.md 白名单 + scripts/check_trae_baseline.sh 的 REQUIRED_SKILLS。
    3. 通过文末「落地门禁（6 项）」后上线。

  附录（绑定语义表 / 门禁清单）仅供评审参考，复制模板本体时忽略。
-->

---
name: "<kebab-case 唯一名，项目专属加 aiot- 前缀>"
description: "<一句话职责>。当用户要求<触发词 A/B/C>时触发。"
---

# <中文标题>

## 定位
<一句话定位 + 是否 read-only>，绑定项目 Agent [`<agent-name>`](../agents/<agent-name>.md)。
（运维执行类 / 编排类无项目 read-only Agent，写：不绑定项目 Agent，执行依赖内置 `<subagent>` 与 `<命令入口>`）

## 触发条件
- 用户要求「<触发词 1> / <触发词 2>」

## 事实源（权威依赖声明，先读）
- `<文件路径 1>`（<用途>）
- `<文件路径 2>`（<用途>）

## 范围 / 链路
- 内部：<系统/模块清单，含端口/协议>
- 外部：<中间件/基础设施清单>

## 执行门禁（硬性）
1. 委托 `<agent-name>` Agent（read-only），回传格式：`结论 / 证据 / 改动建议 / 阻塞项`
2. <步骤化动作，禁跳步>
3. 输出矩阵：`<列1 | 列2 | 列3>`

## 输出模板
| 列1 | 列2 | 列3 |
|---|---|---|

## 验收口径
- 通过 = <可度量条件 A> 且 <条件 B>
- 不通过 = <任一反例>

## 安全约束
- 只读分析，不修改任何文件。
- 写操作先触发 `NotifyUser` 审批，获确认后方可执行。

---

<!-- ============ 附录（评审参考，复制模板本体时忽略） ============ -->

## 绑定语义表（Skill 三类，杜绝混淆）

| Skill 类型 | 绑定 Agent | 执行体 | 示例 |
|---|---|---|---|
| 只读分析类 | `.trae/agents/xxx.md`（必填） | 项目 read-only Agent | aiot-test-coverage / aiot-security-audit / aiot-schema-validator / aiot-system-boundary |
| 运维执行类 | 无（`—`） | 内置 subagent + 命令 | aiot-remote-dev |
| 编排/产品/文档类 | 无（`—`） | 直接执行 | vibe-ops-master / product-agent / project-doc-agent |

## 落地门禁（新 Skill 上线 6 项硬检查）

| # | 门禁 | 判据 | 度量方式 |
|---|---|---|---|
| 1 | Frontmatter 完整 | name 唯一 + description 含触发词 | 人工审 |
| 2 | 事实源真实 | 每个引用文件存在，无悬空引用 | `test -f` 全真 |
| 3 | 验收可度量 | 含「通过=…/不通过=…」 | grep `验收口径` |
| 4 | 绑定一致 | SKILL.md 绑定段 ↔ SKILL_SCOPE 绑定列对齐 | 人工审 |
| 5 | 白名单同步 | 已入 SKILL_SCOPE + `REQUIRED_SKILLS` | `check_trae_baseline.sh` |
| 6 | 巡检通过 | 退出码 0，0 个 MISS | `bash scripts/check_trae_baseline.sh` |

## 工程规范（硬性）

1. Frontmatter 必填 `name`（唯一 kebab-case）+ `description`（含触发词）。
2. 事实源先读再分析，每条结论 `cite` 文件路径 + 行号；悬空引用 = 硬伤。
3. 执行门禁步骤化、禁跳步；委托 Agent 回传统一四段式 `结论 / 证据 / 改动建议 / 阻塞项`。
4. 验收口径可度量（`通过 = ... / 不通过 = ...`），禁止「应该/大概」模糊词。
5. Skill 只「声明」不「执行」，与 Agent 接口隔离，为独立成微服务/拆仓预留解耦边界。
