# Runbook 入口与验收说明

## 1. 入口导航
- 单服务回滚手册：`/Users/aiden/Projects/AIOT-java/docs/runbooks/single-service-rollback.md`
- 发布演练模板：`/Users/aiden/Projects/AIOT-java/docs/runbooks/release-drill-template.md`
- AI 持久化迁移手册：`/Users/aiden/Projects/AIOT-java/docs/runbooks/ai-persistence-migration.md`
- AI 持久化灰度切换清单：`/Users/aiden/Projects/AIOT-java/docs/runbooks/ai-persistence-rollout-checklist.md`
- 一键演练脚本：`/Users/aiden/Projects/AIOT-java/scripts/run_release_rollback_drill.sh`
- 故障注入脚本：`/Users/aiden/Projects/AIOT-java/scripts/inject_fault.sh`
- 回滚脚本（含结构化留痕）：`/Users/aiden/Projects/AIOT-java/scripts/rollback_single_service.sh`

## 2. 推荐执行顺序
1. 先阅读回滚手册确认触发条件和边界。
2. 在演练环境使用一键演练脚本执行完整链路。
3. 演练后检查结构化留痕文件并回填模板。
4. 按验收清单完成签收并沉淀问题单。

## 3. 快速命令
```bash
# 1) 单次故障注入（用于手工演练）
/Users/aiden/Projects/AIOT-java/scripts/inject_fault.sh \
  --service aiot-device-service \
  --mode stop-container

# 2) 一键发布回滚演练（推荐）
/Users/aiden/Projects/AIOT-java/scripts/run_release_rollback_drill.sh \
  --service aiot-device-service \
  --tag drill-20260504 \
  --fault-mode stop-container \
  --health-timeout 180
```

## 4. 结构化留痕输出
- 回滚时间线（JSONL）：`/Users/aiden/Projects/AIOT-java/scripts/.release_state/audit/rollback-<trace_id>.jsonl`
- 回滚摘要（JSON）：`/Users/aiden/Projects/AIOT-java/scripts/.release_state/audit/rollback-<trace_id>.summary.json`
- 演练时间线（JSONL）：`/Users/aiden/Projects/AIOT-java/scripts/.release_state/drill/timeline-<trace_id>.jsonl`
- 演练报告（JSON）：`/Users/aiden/Projects/AIOT-java/scripts/.release_state/drill/report-<trace_id>.json`

## 5. 验收说明（DoD）
- [ ] 演练命令成功执行，且包含完整链路：发布、注入、失败验证、回滚、恢复验证。
- [ ] 故障注入阶段触发健康检查失败，失败结果与注入模式一致。
- [ ] 回滚结果为成功，服务健康检查在阈值内恢复。
- [ ] 结构化留痕文件齐全，`trace_id` 可贯穿回滚与演练时间线。
- [ ] runbook 留痕模板已回填关键证据链接（监控、日志、流水线）。
- [ ] 至少形成 1 条可执行改进项（owner + ddl）并进入迭代跟踪。
