# AIoT-java 项目文档总纲（Documentation Map）

> 单一事实入口。本文档定义项目文档的「分层模型、目录契约、事实源、Wiki 同步流程与质量门禁」。
> 由 `project-doc-agent`（`.trae/skills/project-doc-agent/SKILL.md`）驱动维护；代码变更触发的文档更新必须遵守本契约。

## 1. 分层模型（L0–L4 + 横切）

按「读者视角 + 生命周期」把文档分为 5 个纵向层，另设 4 类横切类型。

| 层 | 定位 | 回答 | 受众 | 更新节奏 | 代表文档 |
|---|---|---|---|---|---|
| L0 总纲 | 战略/路线总入口 | 为什么做、去哪 | 高管 / 产研负责人 | 季度 | `README.md`、`aiot-full-roadmap`、`A01_架构总纲` |
| L1 概览 | 项目是什么 | 是什么 | 全员 / 新成员 | 里程碑 | `PROJECT_STATUS`、产品定义、账户体系 |
| L2 运行基线 | 现状如何 | 现在什么样 | 研发 / 运维 | 随代码变更 | `current-architecture`、能力矩阵、环境配置、监控告警 |
| L3 路线执行 | 要去哪 / 怎么走 | 接下来怎么做 | 研发 / 项目 | 迭代 | AI-native 路线、技术路线版本、性能基线 |
| L4 历史专项 | 为什么这么设计 | 设计依据 | 深度研发 | 归档 / 低频 | 架构设计、数据库设计、DDD、技术选型 |

横切类型（不参与 L 分层，按用途归档）：

| 类型 | 目录 | 用途 |
|---|---|---|
| 运行手册 | `docs/runbooks/` | 事故处理、发布/回滚 SOP |
| 任务留痕 | `docs/tasks/` | Agent 分工、执行包、门禁命令 |
| 模板 | `docs/templates/` | 复盘 / 需求 / 演练模板 |
| 迭代复盘 | `docs/sprints/` | Sprint 验收、事故 / 演练复盘 |

## 2. 目录契约（单一事实源）

| 目录 | 分层 | 是否进 Wiki | 说明 |
|---|---|---|---|
| 仓库根 `README.md` | L0 | 否 | 项目战略 + 快速开始，主仓库唯一入口 |
| `docs/wiki/` | L0–L3 | 是（manifest 白名单） | Wiki 事实源目录，禁止 `../` 链接 |
| `docs/architecture/` | L4 | 否 | 架构专项设计，repo 侧（含代码导航学习手册） |
| `docs/architecture-deliverables/` | L0/L4 | 否 | A01–A08 架构交付包 |
| `docs/product/` | L1/L3 | 是（部分） | 产品路线 / 迭代计划，部分 wiki-bound |
| `docs/`（根） | L4 | 是（部分） | 架构设计、数据库、DDD、选型、规范等历史专项 |
| `docs/runbooks/` | 横切 | 否 | 运行手册 |
| `docs/tasks/` `docs/templates/` `docs/sprints/` | 横切 | 否 | 任务 / 模板 / 复盘 |
| `.wiki-sync/` | — | 是 | Wiki 镜像仓库（gitlink，远端 `AIOT_Project.wiki.git`） |

## 3. 事实源与漂移治理

- **事实源优先级**：代码 / `docker-compose.yml` / `pom.xml` / GitHub Workflow / 运行脚本 > 文档。漂移以代码为准。
- **状态标签**：`current`（已落地）、`in-progress`（开发中）、`planned`（规划中）。
- **链接规则**：wiki-bound 文档禁止 `../` 父级相对链接；引用非 wiki 内容（代码 / 脚本 / 未同步文档）一律用绝对 URL `https://github.com/aidencck/AIOT_Project/blob/main/<path>`。
- **事实校验**：每篇 wiki 页面须通过 `docs/wiki/wiki-fact-verification-checklist.md`。

## 4. Wiki 同步流程

1. 新增 / 变更 wiki-bound 文档 → 同步更新 `scripts/sync_wiki.sh` 的 manifest 白名单。
2. `./scripts/sync_wiki.sh --check` 校验：源缺失（MISS）、非法 `../` 链接（LINK-DRIFT）、源与 wiki 差异（DIFF）。
3. `./scripts/sync_wiki.sh` 镜像到 `.wiki-sync/`、提交并推送到 `aidencck/AIOT_Project.wiki.git`。
4. 同步更新 `.wiki-sync/_Sidebar.md` 与 `Home.md` 导航。

## 5. 文档内容质量评估模型（DocQA）

> 内容质量是文档的实质，结构分层只是外壳。以下五维评分卡是 `project-doc-agent` 评估 / 补齐的唯一入口，禁止用散落检查项替代。

### 5.1 五维评分卡

| 维度 | 权重 | 证据源 | 达标线 |
|---|---|---|---|
| 事实一致性 F | 30% | 代码 / compose / pom / workflow / 脚本 | ≥ 3 |
| 结构完整性 S | 25% | 层级必备章节清单（见 5.2） | ≥ 2 |
| 时效性 T | 15% | `updated_at` vs 事实源最后变更 | ≥ 3 |
| 口径一致性 C | 15% | 跨文档术语 / 数字 / 状态 | ≥ 3 |
| 可执行性 A | 15% | 命令 / 门禁 / 验收指标 | ≥ 3 |

### 5.2 层级必备章节清单（判「结构完整性 S」）

| 层 / 类型 | 必备要素（缺一即扣 S 分） |
|---|---|
| L0 总纲 | Premise / Constraints / Boundaries / Endgame / 当前状态与核心缺口 |
| L1 概览 | 定位 / 范围 / 当前状态 / 入口导航 |
| L2 运行基线 | 当前架构 / 依赖版本 / 容量与已知缺陷 / 监控告警现状 |
| L3 路线执行 | 目标 / 里程碑(M) / 验收门禁 / 执行节奏 |
| L4 历史专项 | 背景 / 设计决策(ADR) / 取舍 / 演进 |
| 横切 runbook | 触发条件 / 前置检查 / 步骤 / 回滚 / 验收 |

### 5.3 五档补齐决策

| 档 | 缺口 | 判定信号 | 补齐动作 | 补完门禁 |
|---|---|---|---|---|
| D1 | 事实漂移（Fatal） | F < 3 | 以代码为准改写，标注 `fact_source` + 漂移根因 | 交叉验证通过 |
| D2 | 结构缺失（Major） | S < 2 | 按 5.2 必备要素补齐实质章节 | 必备要素逐条落地 |
| D3 | 口径冲突（Major） | C < 3 | 收敛到单一事实源定义，统一改写 | 跨文档无残留冲突 |
| D4 | 时效过期（Minor） | T < 3 | 刷新数据 / 状态 / `updated_at` | 数据可追溯 |
| D5 | 不可执行（Minor） | A < 3 | 补命令 / 门禁 / 验收指标 | 结论可复现 |

## 6. 质量门禁（硬性）

- **内容门禁**：任一文档 F < 3 或 S < 2 时，禁止同步，必须先补齐。
- `--check` 无 MISS、无 LINK-DRIFT 方可推送。
- 文档新增 / 删除必须同步本目录契约与 `.trae/sources/INDEX.md`。
- 功能 / 发布 / 告警 / 架构变更 → 同一次提交内更新对应文档（禁止文档与代码漂移跨提交）。

## 7. 入口导航

- 项目主页：根目录 [`README.md`](../README.md)
- 路线总入口：[`docs/wiki/README.md`](wiki/README.md)
- 架构链路地图：[`.trae/knowledge/architecture-map.md`](../.trae/knowledge/architecture-map.md)
- 来源索引：[`.trae/sources/INDEX.md`](../.trae/sources/INDEX.md)
- 变更记录：根目录 [`CHANGELOG.md`](../CHANGELOG.md)
