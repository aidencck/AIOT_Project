# Skill 白名单与禁用治理清单（SKILL SCOPE）

## 目标
把「本项目该用哪些 Skill、禁用哪些无关 Skill」固化为仓库级约束，避免同类任务在不同机器/场景下因 Skill 能力漂移导致输出不可预测。白名单是唯一事实源，巡检脚本 `scripts/check_trae_baseline.sh` 据此核对。

## 白名单（本项目必须可用，按用途分组）

### 项目专属（P0 必选，已版本化到 `.trae/skills/`）
| Skill | 用途 | 绑定 Agent | 门禁 |
|---|---|---|---|
| `aiot-remote-dev` | G0–G7 全生命周期运维/发布/回滚/观测 | —（执行依赖内置 `devops-architect`） | Gate 硬门禁 |
| `aiot-security-audit` | 只读安全审计 | security-auditor | 无 Critical/High |
| `aiot-schema-validator` | AI 诊断 schema/枚举校验 | schema-validator | 100% 归一化 |
| `aiot-test-coverage` | 测试覆盖缺口分析 | test-coverage-analyzer | 活跃链路无 P0 缺失 |
| `aiot-system-boundary` | 系统边界/依赖六维分析 | system-boundary-analyzer | 六维齐全 + cite |
| `product-agent` | 需求→PRD→验收→闭环矩阵 | — | 每条可门禁 |
| `project-doc-agent` | 文档体系化 L0–L4/质量评估/Wiki 同步 | — | 质量门禁 F≥3 且 S≥2 |

> 绑定 Agent 列仅指 `.trae/agents/` 下的项目自研 Agent。`devops-architect` 为内置 subagent（非项目资产）；`vibe-ops-master` 为上层编排者，调度全部 7 个 Skill 与 4 个 read-only Agent，不属单个 Skill 的绑定对象。

### 通用方法（P1 可选）
- `architecture-architect`（架构/边界类）
- `project-manager`（拆解/推进类）
- `TRAE-code-review`（代码审查）
- `TRAE-debugger`（运行时调试）

## 禁用清单（本项目约定禁用，未落地全局开关）

> 说明：以下 Skill 均为全局能力，Trae 的 Skill 开关是全局级（`~/.trae/skill-config.json`），无法按项目单独禁用。为避免误伤其他项目（如 `morena-remote-admin` 在 morena 项目仍需使用），本项目采用**约定禁用**：在 AIOT-java 项目中不触发以下 Skill，而非修改全局开关。若确需跨项目全局禁用，走「变更门禁」第 3 条。

| Skill | 禁用原因 |
|---|---|
| `linkedin-content` | 社媒内容，与 AIoT 后端无关 |
| `twitter-automation` | 社媒自动化，与 AIoT 后端无关 |
| `youtube-downloader` | 视频下载，与 AIoT 后端无关 |
| `product-image-md-extractor` | 电商图片抓取，与本项目无关 |
| `TRAE-generate-mini-app` | 小程序生成，非本后端链路 |
| `morena-remote-admin` | 面向 morena-api 项目，非本项目 |

## 变更门禁
1. 新增/禁用 Skill 必须先改本清单，再同步 `.trae/sources/INDEX.md`。
2. 移除「必选 Skill」需提交风险说明并获 TL 批准。
3. 全局禁用动作（如需跨项目生效）：
   ```bash
   # 编辑 ~/.trae/skill-config.json 的 disabledSkills 数组，加入禁用项名称
   # 改动后重开 Trae 会话生效
   ```

## 巡检触发
- 手动：`bash scripts/check_trae_baseline.sh`
- 建议：接入 CI（门禁失败退出码非 0）
