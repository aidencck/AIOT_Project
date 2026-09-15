# AIOT 全项目完整路线图实施计划

## Summary

- 目标：基于仓库现有的产品路线、AI-native 蓝图、技术版本路线和架构交付包，整理出一份面向 `AIOT 产研负责人` 的 `12 个月全项目双层版路线图`。
- 输出形态：
  - 管理层：一份可用于汇报和对齐的总路线图，强调阶段目标、业务价值、关键里程碑、风险与经营指标。
  - 执行层：一份可用于落地的模块级改造和迭代路线，明确模块、接口、数据对象、验收口径、依赖与节奏。
- 规划范围：覆盖 `产品经营 + AI-native + 技术架构 + 稳定性交付 + 组织治理` 五条主线，不仅限于 AI 专项或技术专项。
- 规划周期：`2026Q3 ~ 2027Q2`，按季度组织阶段目标，再按半年度和季度映射到现有版本与里程碑。

## Current State Analysis

### 现有文档与边界

- `docs/wiki/README.md`
  - 当前已经是本地 Wiki 的总导航，区分了“当前已实现能力”和“目标态规划能力”。
  - 已经同时维护了产品路线、技术路线和 AI-native 执行入口，适合作为完整路线图的主入口索引。
- `docs/product/AIoT_AI_Native_Product_Roadmap.md`
  - 已给出 AI-native 专项的 Premise/Constraints/Boundaries/Endgame、M0~M3 和核心能力定义。
  - 更适合保留为“AI 专项子路线图”，不应直接承载全项目总路线图。
- `docs/product/AIoT_Admin_Backoffice_Roadmap.md`
  - 已覆盖后台产品闭环、角色职责、P0/P1/P2 能力和管理指标。
  - 更适合保留为“后台产品专项子路线图”。
- `docs/wiki/2026Q3-technology-roadmap-v1.1.0.md`
  - 已沉淀为稳定性与性能治理版本路线，边界明确为“性能与架构演进”。
  - 更适合继续作为技术专项版本路线，不应被总路线图吞并。
- `docs/architecture-deliverables/A04_演进路线与里程碑.md`
  - 当前仍是模板化骨架，只有通用阶段结构，尚未承载完整业务/产品/AI/治理的一体化演进内容。
  - 适合作为完整路线图的架构评审映射页或摘要页。
- `docs/PROJECT_STATUS.md`
  - 仍保留较早期的任务分配与阶段描述，包含 Kafka、TDengine 等历史目标项。
  - 可作为“历史背景”，但不适合作为新的路线图主入口。

### 已知项目现状

- 架构现状：Maven 多模块微服务，主运行面为 `Gateway + Auth + Device + Home`，`Rule/Shadow/MQTT Adapter/Data Parser` 为“可运行但未完全产品化”阶段。
- AI-native 现状：已有专项蓝图和交付拆解，但当前代码与文档显示 AI 实现仍主要处于规划和骨架阶段。
- 管理后台现状：已有“最小闭环导向”的路线图和版本计划，但与 AI-native、技术治理仍是分散维护。
- 统一问题：当前缺少一份能够从 `AIOT 产研负责人视角` 将“业务目标、专项路线、技术版本、交付治理、组织机制”统一串起来的总路线图 SSOT。

### 关键冲突与空缺

- 冲突 1：存在多份“路线图”文档，但每份各自聚焦不同子域，缺少统一总纲。
- 冲突 2：`A04_演进路线与里程碑.md` 过于模板化，无法承接当前已有的专项规划内容。
- 空缺 1：缺少一份明确覆盖 `2026Q3 ~ 2027Q2` 的季度级全局路线图。
- 空缺 2：缺少一套从“产研负责人职责”反推的能力域拆分、Owner、依赖、验收与风险矩阵。
- 空缺 3：缺少双层结构，把“汇报口径”和“执行口径”放在同一文档体系中。

## Assumptions & Decisions

- 决策 1：完整路线图将采用“`总纲 + 专项映射`”结构，而不是重写并替代所有已有专项文档。
- 决策 2：完整路线图的主文档落在 `docs/wiki/`，因为现有 Wiki 已是总导航和代码同步说明入口。
- 决策 3：架构交付包中的 `A04_演进路线与里程碑.md` 将作为评审摘要页，链接回 Wiki 主路线图，而不是重复维护完整细节。
- 决策 4：`AIoT_AI_Native_Product_Roadmap.md` 与 `AIoT_Admin_Backoffice_Roadmap.md` 保留为专项详版，只补充与总路线图的对应关系，不重写专项内容。
- 决策 5：时间跨度采用 `12 个月`，拆成四个季度：
  - `2026Q3`：底座收敛与可运营闭环
  - `2026Q4`：AI-native M1 与运营复制能力
  - `2027Q1`：半自动治理与多租户/交付能力
  - `2027Q2`：平台化与行业方案化
- 决策 6：路线图必须同时回答两类问题：
  - “管理层为什么现在做、做到哪里算成功”
  - “执行层要改哪些模块、在哪些文档落地、如何验收”
- 假设 1：本次实施以“文档体系重构与路线统一”为目标，不修改业务代码。
- 假设 2：版本命名继续沿用现有 `M1/M2/M3` 与 `v1.1.0/v1.2.0/v1.3.0` 口径，但会扩展到跨季度映射。

## Proposed Changes

### 1. 新增完整路线图主文档

- 文件：`/Users/aiden/Projects/AIOT-java/docs/wiki/aiot-full-roadmap-2026Q3-2027Q2.md`
- What：
  - 新建一份全项目总路线图，作为 `AIOT 产研负责人` 的 SSOT。
  - 采用双层结构：
    - 第一层：管理摘要
    - 第二层：执行拆解
- Why：
  - 当前已有多份专项路线图，但缺少全局总纲。
  - 用户要求的是“把上面的内容进行详细规划，形成一个完整的路线图”，需要单一入口统一承载。
- How：
  - 文档结构固定为：
    - `Premise / Constraints / Boundaries / Endgame`
    - 当前状态判断与核心缺口
    - 负责人职责映射到能力域
    - 12 个月路线总览（季度表）
    - 五条主线：产品经营、AI-native、架构平台、交付治理、组织机制
    - 模块级演进地图（`gateway/auth/device/home/rule/shadow/mqtt-adapter/data-parser` + 拟新增模块）
    - 季度里程碑、阶段验收、北极星指标、风险矩阵、依赖矩阵
    - 与现有专项文档的映射导航
  - 新文档中明确引用现有专项文档，避免内容重复。

### 2. 更新 Wiki 总导航

- 文件：`/Users/aiden/Projects/AIOT-java/docs/wiki/README.md`
- What：
  - 在“统一路线图总览”或“L3 AI-native 与版本路线”附近新增“全项目路线图”入口。
  - 调整导航分组，明确“总路线图”和“专项路线图”的层级关系。
- Why：
  - `docs/wiki/README.md` 已是统一入口，不增加导航会造成新文档难以被发现。
- How：
  - 新增一节，例如“L0 总路线图”或在 L1/L3 中加入：
    - `全项目路线图（12个月）`
    - `AI-native 专项`
    - `管理后台专项`
    - `技术版本路线`
  - 为每类文档补充一句边界说明，避免再次重叠。

### 3. 将 A04 从模板页升级为评审摘要页

- 文件：`/Users/aiden/Projects/AIOT-java/docs/architecture-deliverables/A04_演进路线与里程碑.md`
- What：
  - 把当前模板化内容升级成“完整路线图的架构评审摘要”。
  - 保留其在交付包中的位置，但内容改为摘要和评审口径，不再单独维护一套孤立阶段模板。
- Why：
  - 当前 A04 只有三个通用 Phase，无法反映已经存在的产品、AI-native、技术、治理多线并行事实。
  - 架构交付包要求 A04 可评审、可验收、可与 A06/A07/A08 联动。
- How：
  - 重写为：
    - 路线总览摘要
    - 季度阶段与版本映射
    - 架构变更主线
    - 评审清单与门禁引用
    - 指向 `docs/wiki/aiot-full-roadmap-2026Q3-2027Q2.md` 的链接

### 4. 补充 AI-native 专项与总路线图映射

- 文件：`/Users/aiden/Projects/AIOT-java/docs/product/AIoT_AI_Native_Product_Roadmap.md`
- What：
  - 不重写主体内容，只增加“在全项目路线图中的位置”和季度映射说明。
- Why：
  - 保持 AI-native 作为专项路线图，但让读者知道它属于总路线图中的哪一段。
- How：
  - 增加“关联到全项目路线图”的章节：
    - 对应阶段
    - 依赖模块
    - 对应经营/交付目标

### 5. 补充后台产品专项与总路线图映射

- 文件：`/Users/aiden/Projects/AIOT-java/docs/product/AIoT_Admin_Backoffice_Roadmap.md`
- What：
  - 不重写主路线，仅增加其在 12 个月总路线图中的定位与阶段依赖。
- Why：
  - 管理后台现在是一条单独产品线，需要被纳入总路线图，而不是并列漂浮。
- How：
  - 增加一节，说明：
    - 对应总路线图的哪几个季度
    - 与 AI-native、工单、告警、审计、经营看板之间的衔接

### 6. 可选补充：更新项目状态页的“路线图入口”

- 文件：`/Users/aiden/Projects/AIOT-java/docs/PROJECT_STATUS.md`
- What：
  - 只做轻量更新，增加“当前推荐阅读的路线图入口”。
- Why：
  - 该文档保留了历史任务视图，若不标注，读者可能仍把它误认为最新主路线。
- How：
  - 在开头或结尾增加一段“当前路线图请以 Wiki 总入口和全项目路线图为准”的说明。
- 说明：
  - 该项为可选。如果实施时发现项目希望保留历史原貌，可不修改本文件。

## Implementation Outline

### 步骤 1：整理总纲内容框架

- 从以下现有文档抽取已定事实和口径：
  - `docs/wiki/current-architecture.md`
  - `docs/wiki/capability-matrix.md`
  - `docs/product/AIoT_AI_Native_Product_Roadmap.md`
  - `docs/product/AIoT_Admin_Backoffice_Roadmap.md`
  - `docs/wiki/2026Q3-technology-roadmap-v1.1.0.md`
  - `docs/wiki/ai-native-overview.md`
- 输出统一骨架：
  - 当前现状
  - 职责映射
  - 四季度总路线
  - 五条主线
  - 模块级执行图

### 步骤 2：撰写全项目路线图主文档

- 先形成管理层摘要部分：
  - 战略目标
  - 阶段目标
  - 业务价值
  - 北极星指标
  - 主要风险
- 再形成执行层部分：
  - 模块演进
  - 数据对象/API/治理机制
  - 里程碑与验收
  - 依赖与关键决策

### 步骤 3：更新导航与架构摘要页

- 修改 `docs/wiki/README.md`，加入总路线图入口。
- 修改 `docs/architecture-deliverables/A04_演进路线与里程碑.md`，改成评审摘要与入口映射。

### 步骤 4：补专项映射

- 在 `AIoT_AI_Native_Product_Roadmap.md` 和 `AIoT_Admin_Backoffice_Roadmap.md` 中补“归属总路线图”的说明。
- 保证专项文档与总路线图之间是“父子关系”，不是并列重复。

### 步骤 5：一致性校验

- 校验时间窗口、版本命名、里程碑命名是否统一。
- 校验 `current / in-progress / planned` 状态标签与现有 Wiki 口径不冲突。
- 校验新文档引用路径全部存在且导航可达。

## Verification Steps

- 文档结构验证：
  - 确认新文档包含 `Summary / Current State Analysis / Proposed Changes / Assumptions & Decisions / Verification` 所需规划信息。
  - 确认最终路线图包含“管理层摘要 + 执行层拆解”双层结构。
- 事实一致性验证：
  - 对照 `docs/wiki/current-architecture.md` 与 `docs/wiki/capability-matrix.md`，确认“当前能力”表述不夸大已落地现状。
  - 对照 `docs/wiki/2026Q3-technology-roadmap-v1.1.0.md`，确认技术版本口径与现有版本线一致。
  - 对照 `docs/product/AIoT_AI_Native_Product_Roadmap.md` 与 `docs/product/AIoT_Admin_Backoffice_Roadmap.md`，确认专项路线没有被错误改写。
- 导航验证：
  - `docs/wiki/README.md` 能找到全项目路线图。
  - `A04_演进路线与里程碑.md` 能回链到全项目路线图。
  - 专项路线图能指向总路线图。
- 可执行性验证：
  - 读者仅阅读新总路线图即可回答：
    - 未来 12 个月做什么
    - 为什么按这个顺序做
    - 哪些模块要改
    - 如何验收
    - 与现有专项文档是什么关系

## Expected Final Deliverable Shape

- 一份新的总路线图 SSOT 文档，适合汇报和执行共用。
- 一份更新后的 Wiki 导航，明确总纲与专项边界。
- 一份升级后的 A04 评审摘要页，能进入架构交付包评审流程。
- 两份专项路线图完成归位，形成“总路线图 -> 专项路线图 -> 执行蓝图”的三级文档体系。
