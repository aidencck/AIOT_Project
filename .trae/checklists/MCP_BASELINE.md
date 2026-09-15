# MCP Baseline Checklist

## 目标

- 统一各场景 MCP 能力，降低“同类任务在不同场景结果不一致”的风险。
- 基线必须与「本机实际注册」对齐，不写未启用的 MCP。

## 当前场景（s_AIOT-java）实际可用 MCP

| MCP | 状态 | 在本项目解决的问题 |
|---|---|---|
| `mcp_Memory` | 必选 | 长链路记忆与知识沉淀（8 微服务链路） |
| `mcp_Sequential_Thinking` | 必选 | 多步一致性推理（设备→规则→影子数据流） |
| `mcp_GitHub` | 必选 | PR/Issue/分支/review 闭环，配合 CI 门禁 |
| `mcp_Excel` | 可选 | 设备数据表模型 / 容量规划 / 性能基线台账 |
| `mcp_Firecrawl` | 可选 | 站点抓取、外部资料索引、事实校验（兼外部文档拉取） |
| `integrated_web-dev` | 已注册未用 | 面向 Web 快应用（supabase/stripe/vercel），与本 Java 后端不匹配，当前场景在册但禁用 |

## 缺口与补位

| 原基线项 | 实际状态 | 补位方案 |
|---|---|---|
| `mcp_Docker` | 未启用 | [aiot-remote-dev Skill](~/.trae/skills/aiot-remote-dev/SKILL.md) 的 `ssh` + `aiotctl` 直连，少一层抽象 |
| `mcp_Kubernetes` | 未启用 | 同上前提；k8s 路径走 `k8s/deploy.sh` + `kubectl` 经 ssh 执行 |
| `mcp_Playwright` | 未启用 | `browser_use` / `Playwright` 子代理承担页面验证 |
| `mcp_feishu` | 未启用 | 需要飞书协同时再开启 |

> 结论：Docker/K8s 运维不走 MCP，走 ssh 技能；只有「记忆/推理/GitHub/抓取/表格」用 MCP，页面验证走 browser_use/Playwright 子代理。

## 每周巡检清单

1. 检查 `~/.trae/mcps/` 下当前场景是否包含上表「必选」MCP。
2. 检查每个 MCP 是否存在 `SERVER_METADATA.json` 与 `tools/*.json`。
3. 对缺失项输出补齐清单：`场景 -> 缺失 MCP -> owner -> ddl`。
4. 将巡检结果写入 `docs/tasks/` 或 `docs/sprints/` 对应周文档。

## GitHub 协作边界（mcp_GitHub vs gh CLI）

- **单一事实源**：仓库/owner/源码 URL 唯一维护于 `scripts/lib/services.json` 的 `github` 段，由 `scripts/lib/common.sh` 的 `github::*` 函数读取；CI 的 `service-registry-guard` 已接入 `github::guard` 漂移校验。
- **仓库定位**：`aidencck/AIOT_Project`。本地目录名 `AIOT-java` 仅为工程名，与 GitHub 仓库名不一致，禁止把 `org.opencontainers.image.source` 或播种脚本 `REPO` 写成 `AIOT-java`。
- **mcp_GitHub（agent 对话内）**：负责 PR/Issue/分支/review 的交互式闭环（通过 run_mcp 调用），面向「单次对话内」的协作动作。
- **gh CLI（终端/CI 脚本）**：负责 `scripts/*.sh` / `scripts/*.py` 的批量播种（labels/milestones/issues/projects），面向「可重复、幂等、可入 CI」的任务编排。
- **两者不互相替代**：mcp_GitHub 无法被 shell 脚本调用，gh CLI 无法在 agent 对话内直接调用；正确姿势是两者共享同一 SSOT，禁止各自硬编码仓库名。

## 变更门禁

- 任何移除「必选 MCP」的操作，必须先提交风险说明并获 TL 批准。
- 引入新 MCP 前，需给出用途、边界、失败回退方案，并同步更新本文件与 `.trae/sources/INDEX.md`。
