# 发布演练模板（Release Drill Template）

## 1. 演练基本信息
- **演练名称**：
- **演练日期**：
- **环境**：staging / prod-like
- **演练范围**：单服务 / 多服务（本模板默认单服务）
- **目标服务**：
- **演练负责人（DRI）**：
- **参与角色**：研发、SRE、QA、产品、客服代表

## 2. 演练目标与触发条件
### 2.1 演练目标
- 验证发布流程在异常场景下可快速回滚。
- 验证监控告警、人工确认与自动化脚本协同有效。
- 验证留痕与复盘链路完整可审计。

### 2.2 触发条件（演练注入）
至少选择一项进行注入：
- [ ] 错误率注入：模拟 5xx 持续高于阈值。
- [ ] 延迟注入：模拟 P95 延迟持续退化。
- [ ] 业务失败注入：核心接口返回异常码。
- [ ] 配置错误注入：错误配置导致依赖连接失败。
- [ ] 安全策略注入：鉴权配置异常触发安全回滚。

## 3. 执行步骤（演练流程）
1. **准备阶段**
   - 确认演练窗口、通知相关方、冻结非必要变更。
   - 准备目标版本与回滚版本，确认脚本可用性。
2. **发布阶段**
   - 按标准流程发布到演练环境（可金丝雀）。
   - 记录发布时间、版本号、实例变化。
   - 演练前基线：验证系统运行负载（吞吐/延迟）与正确性（数据链路一致）——对应 `scripts/verify_drill_load_correctness.sh --phase baseline`。
3. **故障注入阶段**
   - 按选定触发条件注入异常并观察告警触发。
   - 记录告警首次触发时间。
   - 可选脚本：
     ```bash
     /Users/aiden/Projects/AIOT-java/scripts/inject_fault.sh \
       --service SERVICE_NAME \
       --mode stop-container
     ```
4. **回滚阶段**
   - 执行回滚脚本或工作流：
     ```bash
     /Users/aiden/Projects/AIOT-java/scripts/rollback_single_service.sh \
       --service SERVICE_NAME \
       --to-tag TARGET_VERSION
     ```
   - 记录回滚开始与完成时间。
5. **验证阶段**
   - 执行健康检查与业务冒烟。
   - 回滚恢复后：验证系统运行负载与正确性恢复到基线区间——对应 `scripts/verify_drill_load_correctness.sh --phase recovery`。
   - 对比演练前后 SLI/SLO 指标。
6. **收尾阶段**
   - 恢复常规流量策略。
   - 汇总证据、登记问题与改进项。

### 3.1 一键演练（推荐）
```bash
/Users/aiden/Projects/AIOT-java/scripts/run_release_rollback_drill.sh \
  --service SERVICE_NAME \
  --tag DRILL_RELEASE_TAG \
  --fault-mode stop-container \
  --health-timeout 180
# 默认执行「发布前基线 + 回滚恢复后」两段运行负载与正确性验证；
# 可观测栈（Prometheus/Loki/Tempo）不可用时自动软降级跳过，可用 --skip-load-verify 显式跳过。
# 默认冒烟规模：50 用户 × 2 设备 / 并发 50（可经 verify_drill_load_correctness.sh --users/--load-concurrency 调整）；
# 报告 JSON 含 load_verify 结果与 load_compare（baseline/recovery/delta）指标对比。
```

## 4. 验证清单
- [ ] 告警在预期时间内触发（目标：≤5 分钟）。
- [ ] 回滚在目标时长内完成（目标：≤15 分钟）。
- [ ] 服务健康探针恢复正常。
- [ ] 核心业务链路验证通过。
- [ ] 运行负载验证通过（吞吐 rps、延迟 p50/p95/p99 达标）。
- [ ] 正确性验证通过（设备状态落库、规则/影子消费、可观测链路数据一致）。
- [ ] 指标恢复到基线区间（错误率、延迟、吞吐）。
- [ ] 关键日志与监控证据已归档。
- [ ] 演练问题单与改进项已创建并指派 owner。

## 5. 留痕模板
```text
[发布演练记录]
演练编号：
日期/时间：
环境：
目标服务：
发布版本：
回滚版本：
触发条件（注入方式）：
告警触发时间：
回滚开始时间：
回滚完成时间：
恢复确认时间：
参与人：
结果结论（通过/部分通过/失败）：
关键证据链接（监控/日志/流水线）：
问题清单：
改进项（owner + ddl）：
```

## 6. RACI
| 活动 | R(负责执行) | A(最终负责) | C(咨询) | I(被通知) |
|---|---|---|---|---|
| 演练计划制定 | 研发 DRI | TL/EM | SRE、QA、产品 | 全体相关团队 |
| 演练执行与回滚 | 研发 DRI + SRE | TL/EM | QA、DBA | 产品、客服 |
| 结果验证 | QA + 研发 | TL/EM | SRE、产品 | 业务方 |
| 复盘与整改跟踪 | 研发 DRI | TL/EM | SRE、QA | 团队管理层 |

## 7. 验收标准
- **流程可执行**：演练流程无阻塞，关键步骤均可按文档执行完成。
- **指标可达标**：告警触达、回滚时效、业务恢复满足目标阈值。
- **证据可追溯**：发布、回滚、验证全链路证据完整。
- **改进可闭环**：演练问题均形成行动项并有明确 owner 与截止时间。
- **结构化留痕**：`timeline-<trace_id>.jsonl` 与 `report-<trace_id>.json` 均已生成并归档。

## 8. 复盘结论（填写区）
- **总体结论**：
- **做得好的点**：
- **主要风险点**：
- **下次演练优化建议**：
