# Admin Console API（前端对接）

面向后台管理前端的聚合接口，统一返回家庭、成员、产品、设备、OTA 与运维闭环核心数据。

## 1) 工作台接口
- `GET /api/v1/admin-console/workbench`
- Header: `Authorization: Bearer <token>`
- Query: `homeId`（可选，不传默认使用当前用户首个家庭）

返回示例字段：
- `selectedHomeId`
- `closureScore`
- `homes`
- `members`
- `products`
- `devices`
- `otaTasks`
- `opsOverview`
- `aiEvalReports`
- `closureStages`
- `overview`

说明：
- 该接口是后台首页推荐入口，负责把“家庭/成员/产品/设备/OTA/告警工单/AI治理”聚合为统一工作台。
- 聚合逻辑继续收敛在 `aiot-device-service admin-console`，避免把后台展示逻辑耦合进 `home-service`、`rule-engine` 等业务域服务。

## 2) 总览接口
- `GET /api/v1/admin-console/overview`
- Header: `Authorization: Bearer <token>`
- Query: `homeId`（可选，不传默认使用当前用户首个家庭）

返回示例字段：
- `homeCount`
- `memberCount`
- `productCount`
- `deviceCount`
- `otaTaskCount`
- `todayAlarmCount`
- `pendingWorkOrderCount`
- `oneTimeResolveRate`
- `aiPersistenceMysqlEnabled`
- `aiPersistenceReadMode`
- `aiPersistenceConfiguredReadMode`
- `aiPersistenceMysqlReady`
- `aiPersistenceMysqlCutoverReady`
- `aiPersistenceMysqlCutoverBlockReason`
- `aiPersistenceBackfillManifestExists`
- `aiPersistenceBackfillWrittenTotal`
- `aiPersistenceConsistencyReportExists`
- `aiPersistenceConsistencyPassed`
- `aiPersistenceConsistencyTotalMismatch`
- `aiPersistenceConstraintsReportExists`
- `aiPersistenceConstraintsGatePassed`
- `aiPersistenceConstraintsFailedCount`
- `aiPersistenceMigrationGatePassed`

## 3) 最新闭环数据接口
- `GET /api/v1/admin-console/latest-closure`
- Header: `Authorization: Bearer <token>`
- Query: `homeId`（可选）

返回示例字段：
- `homes`
- `members`
- `products`
- `devices`
- `otaTasks`
- `opsOverview`
- `aiEvalReports`

## 4) AI 评估门禁接口
- `GET /api/v1/admin-console/ai/evals/regression-gate`
- Header: `Authorization: Bearer <token>`
- Query:
  - `sceneTypes`（可选，支持逗号分隔，如 `OFFLINE_FLAP,SHADOW_DIFF`；默认返回 `OFFLINE_FLAP / PROVISION_FAILURE / SHADOW_DIFF`）

返回字段：
- `sceneType`
- `reportType`
- `reportPath`
- `exists`
- `gatePassed`
- `generatedAt`
- `content`

说明：
- 底层聚合 `rule-engine` 的 `GET /api/v1/admin/ai/evals/{sceneType}/regression-gate`
- 若对应报告文件不存在，则返回 `exists=false`
- `gatePassed` 从报告 `content.gatePassed` 提取，便于前端直接展示

## 5) AI 持久化迁移状态
- `GET /api/v1/admin/ai/persistence/status`
- 服务侧：`rule-engine`

返回字段：
- `mysqlEnabled`
- `readMode`
- `configuredReadMode`
- `mysqlReady`
- `mysqlCutoverReady`
- `mysqlCutoverBlockReason`
- `backfillManifestExists`
- `backfillManifestPath`
- `backfillSourceCount`
- `backfillScannedTotal`
- `backfillWrittenTotal`
- `consistencyReportExists`
- `consistencyPassed`
- `consistencyTotalMismatch`
- `constraintsReportExists`
- `constraintsGatePassed`
- `constraintsFailedCount`
- `migrationGateReportExists`
- `migrationGatePassed`
- `mysqlWriteOutboxStoreMode`
- `mysqlWriteOutboxLegacyRedisPendingCount`
- `mysqlWriteOutboxReplayEnabled`
- `mysqlWriteOutboxReplayBatchSize`
- `mysqlWriteOutboxReplayFixedDelayMs`
- `mysqlWriteOutboxPendingCount`
- `mysqlWriteOutboxOldestAgeSeconds`
- `caseMaterializationStoreMode`
- `caseMaterializationLegacyRedisPendingCount`
- `caseMaterializationReplayEnabled`
- `caseMaterializationReplayBatchSize`
- `caseMaterializationReplayFixedDelayMs`
- `caseMaterializationPendingCount`
- `caseMaterializationOldestAgeSeconds`
- `controlPlaneRedisDrainCompleted`
- `controlPlaneDrainStatus`
- `controlPlaneLastDrainAt`
- `controlPlaneLastDrainOperator`
- `controlPlaneLastDrainDryRun`
- `controlPlaneLastDrainAccepted`
- `controlPlaneLastDrainMessage`

`controlPlaneDrainStatus` 结构：
- `lastExecution.executedAt`
- `lastExecution.operator`
- `lastExecution.dryRun`
- `lastExecution.accepted`
- `lastExecution.message`
- `report.exists`
- `report.path`
- `report.source`
- `report.executedAt`
- `report.operator`
- `report.dryRun`
- `report.accepted`
- `report.batchSize`
- `report.requestedStores`
- `report.controlPlaneRedisDrainCompleted`
- `report.message`
- `report.mysqlWriteOutboxRedisPending`
- `report.mysqlWriteOutboxMysqlWritten`
- `report.mysqlWriteOutboxRedisRemaining`
- `report.caseMaterializationRedisPending`
- `report.caseMaterializationMysqlWritten`
- `report.caseMaterializationRedisRemaining`

首页 `/api/v1/admin-console/overview` 同步透传以下摘要字段：
- `aiPersistenceControlPlaneDrainReportExists`
- `aiPersistenceControlPlaneDrainReportPath`
- `aiPersistenceControlPlaneDrainReportSource`
- `aiPersistenceControlPlaneDrainReportExecutedAt`
- `aiPersistenceControlPlaneDrainReportOperator`
- `aiPersistenceControlPlaneDrainReportDryRun`
- `aiPersistenceControlPlaneDrainReportAccepted`
- `aiPersistenceControlPlaneDrainReportBatchSize`
- `aiPersistenceControlPlaneDrainReportRequestedStores`
- `aiPersistenceControlPlaneDrainReportMessage`
- `aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending`
- `aiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten`
- `aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining`
- `aiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending`
- `aiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten`
- `aiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining`

## 6) AI 控制面 Redis Drain
- `POST /api/v1/admin/ai/persistence/control-plane/drain`
- 服务侧：`rule-engine`
- 请求体：
  - `operator`
  - `dryRun`
  - `batchSize`
  - `stores`
- 返回字段：
  - `accepted`
  - `dryRun`
  - `batchSize`
  - `requestedStores`
  - `mysqlWriteOutboxStoreMode`
  - `caseMaterializationStoreMode`
  - `mysqlWriteOutboxLegacyRedisBefore`
  - `caseMaterializationLegacyRedisBefore`
  - `mysqlWriteOutboxMigrated`
  - `caseMaterializationMigrated`
  - `mysqlWriteOutboxLegacyRedisAfter`
  - `caseMaterializationLegacyRedisAfter`
  - `controlPlaneRedisDrainCompleted`
  - `reportPath`
  - `message`
  - `executedAt`
  - `operator`

## 7) 说明
- 前端建议先调用 `/overview` 渲染首页指标，再按需调用 `/latest-closure` 填充列表页。
- 首页也可直接调用 `/ai/evals/regression-gate` 渲染 AI 训练/评估门禁卡片。
- 首页 overview 已包含 AI 持久化迁移摘要，适合灰度切换期间看 `configuredReadMode / readMode / mysqlCutoverReady / backfillWrittenTotal / replayEnabled / pendingCount`。
- 若要切换到 `MYSQL` 读模式，建议同时看 `constraintsGatePassed=true`、`consistencyPassed=true`、`migrationGatePassed=true`，并确认 `mysqlCutoverBlockReason` 为空。
- 当 `configuredReadMode=DUAL` 且 `mysqlCutoverReady=true` 时，业务读链路按 `MySQL-first` 灰度；若 `mysqlCutoverReady=false`，则 DUAL 仍保持 `Redis-first`。
- `mysqlWriteOutboxStoreMode` 与 `caseMaterializationStoreMode` 用于确认当前补偿控制面是否已切到 `MYSQL_TABLE_PRIMARY`，而不是仍停留在 `REDIS_HASH_ONLY`。
- `mysqlWriteOutboxLegacyRedisPendingCount`、`caseMaterializationLegacyRedisPendingCount` 与 `controlPlaneRedisDrainCompleted` 用于确认 Redis 历史补偿任务是否已经清空，而不是长期双栈悬挂。
- `controlPlaneLastDrain*` 用于回显最近一次 drain 的执行人、模式、时间与结果，便于把脚本化运维动作纳入运行态留痕。
- `controlPlaneDrainStatus.report.*` 是标准 drain 证据摘要；若报告文件不存在，则返回 `report.exists=false`，其余摘要字段保持为空，不再要求前端通过 audit 间接推断。
- `stores` 支持 `MYSQL_WRITE_OUTBOX`、`CASE_MATERIALIZATION`，为空时默认两类都执行；`batchSize` 会被服务端截断到 `max-batch-size`。
- runtime drain 会覆盖写入 `ai_control_plane_redis_drain.json`，因此 training gate 与 runtime admin 共用同一份控制面 drain 证据。
- 若用户未绑定家庭，接口会返回业务错误提示。
- 统一响应结构为：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": { }
  }
  ```

## 8) 设备分页接口（前端列表页）
- `GET /api/v1/admin-console/devices/page`
- Header: `Authorization: Bearer <token>`
- Query:
  - `homeId`（可选，不传默认首个家庭）
  - `productKey`（可选）
  - `status`（可选，0未激活/1在线/2离线）
  - `pageNo`（可选，默认 1）
  - `pageSize`（可选，默认 20，最大 200）

返回字段：
- `total`
- `pageNo`
- `pageSize`
- `records`（`DeviceResp` 列表）

## 9) OTA任务分页接口（前端列表页）
- `GET /api/v1/admin-console/ota/tasks/page`
- Header: `Authorization: Bearer <token>`
- Query:
  - `homeId`（可选，不传默认首个家庭）
  - `productKey`（可选）
  - `status`（可选，1进行中/2已完成）
  - `pageNo`（可选，默认 1）
  - `pageSize`（可选，默认 20，最大 200）

返回字段：
- `total`
- `pageNo`
- `pageSize`
- `records`（`OtaUpgradeTaskResp` 列表）

## 10) 内嵌管理页面（Spring Boot 静态资源）
- 访问地址：`GET /admin`（重定向到 `/admin/index.html`）
- 页面能力：
  - 闭环工作台（workbench）
  - 首页总览卡片（overview）
  - AI 评估门禁表格（ai/evals/regression-gate）
  - AI 持久化迁移摘要（overview）
  - 设备分页列表（devices/page）
  - OTA任务分页列表（ota/tasks/page）
- 鉴权方式：页面右上角输入 Token（支持仅粘贴 token，页面自动补 `Bearer ` 前缀）
