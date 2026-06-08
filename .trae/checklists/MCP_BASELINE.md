# MCP Baseline Checklist

## 目标

- 统一各场景 MCP 能力，降低“同类任务在不同场景结果不一致”的风险。

## 必选 MCP（所有研发场景）

| MCP | 是否必选 | 说明 |
|---|---|---|
| `integrated_browser` | 是 | Web 验证、页面交互、可视化检查 |
| `mcp_Docker` | 是 | 容器状态、日志、Compose 操作 |
| `mcp_Kubernetes` | 是 | 集群排障、资源巡检、发布验证 |
| `mcp_Playwright` | 是 | UI 自动化、接口与页面联动验证 |
| `mcp_Memory` | 是 | 长链路任务记忆与知识沉淀 |
| `mcp_Sequential_Thinking` | 是 | 复杂问题分步推理 |

## 可选 MCP（按业务开启）

| MCP | 是否必选 | 使用条件 |
|---|---|---|
| `mcp_GitHub` | 否 | 需要远程仓库自动化（Issue/PR/分支） |
| `mcp_feishu` | 否 | 需要飞书协同与文档/消息自动化 |
| `mcp_Firecrawl` | 否 | 需要站点抓取、外部资料索引 |

## 每周巡检清单

1. 检查 `~/.trae/mcps/` 下每个活跃场景是否包含“必选 MCP”。
2. 检查每个 MCP 是否存在 `SERVER_METADATA.json` 与 `tools/*.json`。
3. 对缺失项输出补齐清单：`场景 -> 缺失 MCP -> owner -> ddl`。
4. 将巡检结果写入 `docs/tasks/` 或 `docs/sprints/` 对应周文档。

## 变更门禁

- 任何移除“必选 MCP”的操作，必须先提交风险说明并获 TL 批准。
- 引入新 MCP 前，需给出用途、边界、失败回退方案。
