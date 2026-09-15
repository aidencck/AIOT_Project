# 交付与回滚落地执行单（Team Agent）

## 1. 目标与验收
- 目标：补齐“发布准入 + 单服务回滚 + 演练留痕”最小闭环，支持值班工程师 15 分钟内恢复单服务。
- 验收标准：
  - 已有可执行脚本：发布、回滚、健康验证。
  - 已有回滚工作流：可手动指定服务与目标 tag 触发。
  - 已有 runbook 与演练模板：可直接用于值班和复盘。
  - 已完成一次演练记录：发布失败 -> 回滚 -> 恢复验证。

## 2. Team Agent 分工
| Agent | 负责人 | 职责 | 交付物 |
|---|---|---|---|
| DevOps Agent | SRE-1 | 发布/回滚自动化脚本与工作流 | `scripts/deploy_single_service.sh`、`scripts/rollback_single_service.sh`、`scripts/verify_release_health.sh`、`.github/workflows/rollback.yml` |
| Backend Agent | BE-Lead | 回滚可执行手册与演练模板 | `docs/runbooks/single-service-rollback.md`、`docs/runbooks/release-drill-template.md` |
| QA Agent | QA-1 | 演练编排、验证与留痕归档 | `docs/drills/YYYY-MM-DD-release-rollback-drill.md`（按模板产出） |
| TL Agent | TL | Go/No-Go 决策、风险闸口与复盘闭环 | 复盘结论、后续行动项与 owner/ddl |

## 3. 执行顺序（D1-D5）
1. D1：DevOps Agent 完成脚本与回滚工作流，并在 staging 自测通过。
2. D2：Backend Agent 完成 runbook 与演练模板，TL 评审。
3. D3：QA Agent 准备故障注入场景，执行首次预演。
4. D4：按“失败 -> 回滚 -> 恢复”完成正式演练并留痕。
5. D5：TL 组织复盘，将改进项写入迭代计划并设定截止时间。

## 4. 演练剧本（标准版）
1. 发布目标服务到 `BAD_TAG`（故障版本）。
2. 注入失败条件（如 readiness 失败或 5xx 超阈值）。
3. 触发回滚（工作流或脚本）到 `GOOD_TAG`。
4. 执行健康验证与业务冒烟。
5. 记录演练数据（TTA 告警触发时长、TTR 回滚恢复时长、恢复后稳定性）。

## 5. 操作入口
- 发布：`scripts/deploy_single_service.sh`
- 回滚：`scripts/rollback_single_service.sh`
- 健康验证：`scripts/verify_release_health.sh`
- 工作流：`.github/workflows/rollback.yml`
- Runbook：`docs/runbooks/single-service-rollback.md`
- 模板：`docs/runbooks/release-drill-template.md`

## 6. 风险与闸口
- 生产禁止使用可变 tag 作为回滚目标（如 `main`）；必须使用不可变版本号或 commit sha。
- 回滚前需确认数据库变更兼容性；不可逆变更需升级到应急评审。
- 任一关键验证失败（健康探针/核心 API）不得恢复放量。
