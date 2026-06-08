# AIOT-java Wiki（本地）

本目录用于维护与代码现状同步的 Wiki 文档，作为主仓库 `README.md` 的延伸说明。  
核心目标是区分“当前已实现能力”和“目标态规划能力”，减少文档与代码漂移。

最后更新：`2026-05-07`

## 当前状态快照（2026-04）

- 架构基线：Maven 多模块微服务，当前主运行面为 `Gateway + Auth + Device + Home`
- 中间件基座：`MySQL + Redis + Nacos + EMQX`（Docker Compose 单节点）
- 统一返回：API 统一为 `Result{code,message,data}`，网关鉴权失败也返回统一 JSON
- 事件可靠性：设备事件流已落地 `Redis Stream + Consumer Group + ACK + pending 回收 + DLQ`
- 发布治理：已补齐单服务发布/回滚/健康验证脚本与回滚工作流，仍需持续演练留档
- 可观测性：服务侧已接入 Actuator + Micrometer + Prometheus 导出，部署层告警体系待补齐

## 统一路线图总览（2026Q3）

### 产品路线（业务价值主线）

| 里程碑 | 状态 | 核心目标 | 关键产出 | 业务验收 |
| --- | --- | --- | --- | --- |
| M1（当前） | `in-progress` | 完成“可运营闭环” | 管理后台 M0、AI-native M0、告警到工单闭环 | 关键告警可自动建单并可追溯 |
| M2 | `planned` | 完成“可复制运营” | 管理后台 M1、AI-native M1、知识反馈闭环 | 人工排障时长显著下降 |
| M3 | `planned` | 完成“半自动运营” | 管理后台 M2、AI-native M2 | 低风险场景人工介入率下降 |

### 技术路线（稳定性交付主线）

| 版本 | 状态 | 核心目标 | 关键产出 | 工程验收 |
| --- | --- | --- | --- | --- |
| v1.1.0 | `in-progress` | 基线可复现 | 压测口径统一、报告模板统一、环境门禁脚本化 | 任一环境 30 分钟内复现实验 |
| v1.2.0 | `planned` | 瓶颈可归因 | E2E 时延指标、刷盘策略升级、入口保护 | 可回答“哪里慢/慢多少/为何慢” |
| v1.3.0 | `planned` | 发布可门禁 | CI 性能回归、阈值阻断、容量模型 | 发布前自动回归并门禁阻断 |

### 统一映射口径（M ↔ v ↔ Sprint）

| 业务里程碑 | 技术版本 | 执行节奏 | 对齐原则 |
| --- | --- | --- | --- |
| M1 | v1.1.0 | Sprint 1~2 | 先保稳定再扩功能，关键链路必须可观测可回滚 |
| M2 | v1.2.0 | Sprint 3~4 | 新能力上线必须附带链路指标与风险预案 |
| M3 | v1.3.0 | Sprint 5~6 | 发布前必须通过功能/稳定性/性能三门禁 |

## 分层导航

### L0 总路线图（推荐先读）

- 全项目路线图（12个月）：[`aiot-full-roadmap-2026Q3-2027Q2.md`](aiot-full-roadmap-2026Q3-2027Q2.md)
  - 说明：作为 `AIOT 产研负责人` 视角的总纲，统一覆盖产品经营、AI-native、平台架构、稳定性交付与组织治理五条主线。
- 架构评审摘要：[`../architecture-deliverables/A04_演进路线与里程碑.md`](../architecture-deliverables/A04_演进路线与里程碑.md)
  - 说明：作为架构交付包中的里程碑评审入口，摘要化展示阶段目标、版本映射和门禁口径。

### L1 项目概览

- 项目状态：[`PROJECT_STATUS`](PROJECT_STATUS)
- 产品定义：[`AIoT_IceMaker_Product_Definition`](AIoT_IceMaker_Product_Definition)
- 用户账户体系：[`product/AIoT_User_Account_System_Plan`](product/AIoT_User_Account_System_Plan)

### L2 当前运行基线（优先阅读）

- 架构现状：[`current-architecture.md`](current-architecture.md)
- 核心中间件：[`core-middlewares.md`](core-middlewares.md)
- 能力矩阵：[`capability-matrix.md`](capability-matrix.md)
- 系统优化路线：[`system-optimization-roadmap.md`](system-optimization-roadmap.md)
- 环境与配置：[`environment-and-config.md`](environment-and-config.md)
- 测试与排障：[`testing-and-troubleshooting.md`](testing-and-troubleshooting.md)
- 监控与告警现状：[`monitoring-and-alerting-status.md`](monitoring-and-alerting-status.md)

### L3 AI-native 与版本路线（执行主入口）

- AI-native 专项总览：[`../product/AIoT_AI_Native_Product_Roadmap`](../product/AIoT_AI_Native_Product_Roadmap.md)
- 管理后台专项总览：[`../product/AIoT_Admin_Backoffice_Roadmap`](../product/AIoT_Admin_Backoffice_Roadmap.md)
- AI-native 总览：[`ai-native-overview.md`](ai-native-overview.md)
- AI-native M0 实施蓝图：[`ai-native-m0-blueprint.md`](ai-native-m0-blueprint.md)
- AI-native 交付拆解：[`ai-native-delivery-backlog.md`](ai-native-delivery-backlog.md)
- 2026Q3 技术路线 v1.1.0：[`2026Q3-technology-roadmap-v1.1.0.md`](2026Q3-technology-roadmap-v1.1.0.md)
- 设备状态性能基线 v1.0.0：[`device-status-performance-baseline-v1.0.0.md`](device-status-performance-baseline-v1.0.0.md)
- 设备状态性能基线 v1.1.0：[`device-status-performance-baseline-v1.1.0.md`](device-status-performance-baseline-v1.1.0.md)
- 历史归档（参考）：[`2026Q3-technology-roadmap-v1.0.0.md`](2026Q3-technology-roadmap-v1.0.0.md)

### L4 历史与专项文档

- 系统架构设计：[`architecture_design`](architecture_design)（总体架构分层、边界与关键流程）
- 骨架与模块规划：[`project_skeleton_plan`](project_skeleton_plan)（模块职责、目录结构与依赖关系）
- 技术选型与基础配置：[`technology_selection`](technology_selection)（中间件选型原则、配置基线与取舍）
- 数据库架构与设计：[`database_architecture`](database_architecture)（核心表模型、约束与演进策略）
- 部署架构与性能评估：[`deployment_and_performance`](deployment_and_performance)（部署拓扑、容量假设与性能评估口径）
- DDD 与 API 契约：[`ddd_and_api_contract`](ddd_and_api_contract)（领域边界、上下文映射与接口契约）
- 通用组件开发指南：[`common_components_guide`](common_components_guide)（通用能力抽象、复用规则与接入方式）
- 全局开发规范：[`development_standards`](development_standards)（编码规范、协作约定、质量门禁）
- MVP 功能设计：[`mvp_features_design`](mvp_features_design)（MVP 范围、核心用户路径与验收标准）
- 迭代与发布计划：[`iteration_and_release_plan`](iteration_and_release_plan)（迭代节奏、发布门禁与回滚策略）

## 文档约定

- 状态标签：`current`（已落地）、`in-progress`（开发中）、`planned`（规划中）
- 事实来源：以仓库代码、`docker-compose.yml`、`pom.xml`、Workflow 与运行脚本为准
- 变更原则：功能改动、发布改动、告警改动都要同步更新对应 Wiki 页面
- 路线原则：产品路线以“业务价值闭环”为主线，技术路线以“稳定性交付门禁”为主线；冲突时先满足稳定性与安全红线
- 校验清单：[`wiki-fact-verification-checklist.md`](wiki-fact-verification-checklist.md)

## 关联文档

- 项目主页：[`Home`](Home)
- 开发规范：[`development_standards`](development_standards)
- 项目状态：[`PROJECT_STATUS`](PROJECT_STATUS)
- 变更记录：仓库 `CHANGELOG.md`（建议在主仓库查看）
