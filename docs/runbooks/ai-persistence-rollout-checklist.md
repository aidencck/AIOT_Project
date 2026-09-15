# AI 持久化灰度切换清单

## 1. 变更前
- [ ] 已确认 `aiot_cloud` Flyway 迁移完成
- [ ] 已确认 Redis、MySQL 连通性正常
- [ ] 已确认 `AIOT_AI_PERSISTENCE_MYSQL_ENABLED=true`
- [ ] 已确认当前 `readMode` 处于 `REDIS` 或 `DUAL`
- [ ] 已冻结非必要变更窗口

## 2. 数据迁移
- [ ] 已执行 `./training/scripts/backfill_redis_to_mysql.sh OFFLINE_FLAP dry-run`
- [ ] 已执行真实回填
- [ ] `training/data/backfill/redis_to_mysql_manifest.json` 已生成
- [ ] `backfillWrittenTotal > 0`

## 3. 一致性与门禁
- [ ] 已执行 `./training/scripts/verify_mysql_export.sh OFFLINE_FLAP true`
- [ ] 已生成 `offline_flap_consistency.json`
- [ ] `consistent=true`
- [ ] `totalMismatchCount=0`
- [ ] 已执行 `./training/scripts/check_migration_gate.sh OFFLINE_FLAP 0 1`
- [ ] `gatePassed=true`

## 4. 灰度观察
- [ ] 已切到 `readMode=DUAL`
- [ ] 观察窗口内 `mysqlReady=true`
- [ ] 观察窗口内 `consistencyPassed=true`
- [ ] 观察窗口内 `migrationGatePassed=true`
- [ ] fallback 指标未持续升高
- [ ] MySQL 读/写失败指标未异常升高

## 5. 最终切换
- [ ] 已完成 DUAL 阶段观察
- [ ] 已切到 `readMode=MYSQL`
- [ ] 切换后 15 分钟内无明显异常
- [ ] Admin Console 显示迁移状态正常

## 6. 回滚预案
- [ ] 已明确回滚命令或配置
- [ ] 已明确触发阈值：
  - `consistencyPassed=false`
  - `migrationGatePassed=false`
  - fallback 异常持续
  - MySQL 读失败率持续升高
- [ ] 已明确回滚负责人和通知链路

## 7. 归档材料
- [ ] Backfill manifest
- [ ] Consistency report
- [ ] Migration gate report
- [ ] 切换前后配置记录
- [ ] 关键监控截图
- [ ] 最终结论与后续问题单
