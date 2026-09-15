# AI 持久化迁移手册

## 1. 目标
- 将 AI 诊断、反馈、案例数据从 `Redis-only` 运行态迁移到 `Redis + MySQL`，并最终支持把 `readMode` 切换到 `MYSQL`
- 保证迁移过程可观测、可回滚、可审计

## 2. 适用范围
- `ai_diagnosis_record`
- `ai_feedback_record`
- `ai_case_library`
- 涉及模块：
  - `aiot-rule-engine`
  - `training`
  - `aiot-db-migrator`
  - `aiot-device-service admin-console`

## 3. 前置条件
- [ ] `aiot-db-migrator` 已执行 `aiot_cloud` 的 Flyway 迁移
- [ ] 运行环境可访问 Redis、MySQL
- [ ] `training` 依赖已安装：

```bash
cd /Users/aiden/Projects/AIOT-java/training
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e .
```

- [ ] 迁移脚本会优先使用 `training/.venv/bin/python`，并自动把 `training/src` 注入 `PYTHONPATH`

- [ ] 环境变量已配置：

```bash
export AIOT_MYSQL_HOST=127.0.0.1
export AIOT_MYSQL_PORT=3306
export AIOT_MYSQL_USER=root
export AIOT_MYSQL_PASSWORD=YOUR_PASSWORD
export AIOT_MYSQL_DATABASE=aiot_cloud
export AIOT_REDIS_HOST=127.0.0.1
export AIOT_REDIS_PORT=6379
export AIOT_REDIS_DB=0
export AIOT_PROJECT_ROOT=/Users/aiden/Projects/AIOT-java
```

### 3.1 Admin 最小联调入口
- 若目标是本地验证 `device-service admin-console -> rule-engine ai persistence query/history detail`，优先使用仓库根目录脚本：

```bash
cd /Users/aiden/Projects/AIOT-java
./start_admin_local_stack.sh
```

- 该脚本会自动完成：
  - 打包 `aiot-home-service` / `aiot-rule-engine` / `aiot-device-service`
  - 执行 `aiot_cloud` 与 `aiot_home` 的 Flyway 迁移
  - 使用 `docker-compose.local.yml` + `docker-compose.admin-local.yml` 启动本地镜像
  - 仅拉起 admin 验证所需的 `mysql` / `redis` / `home-service` / `rule-engine` / `device-service`
  - 关闭 `Nacos` 强依赖并对外暴露：
    - `http://127.0.0.1:8081/admin`
    - `http://127.0.0.1:8083`
    - `http://127.0.0.1:8084`
- 说明：
  - 该入口专门用于 admin 最小联调，不依赖 `nacos` / `emqx` 在线可用。
  - `docker-compose.admin-local.yml` 会为 `device-service` 显式打开 `AIOT_SECURITY_ALLOW_DIRECT_USER_JWT=true`，允许本地用 `home-service` 签发的用户 JWT 直连 `device-service` 的 `/api/v1/admin-console/**`；生产默认仍保持关闭，优先要求网关透传 `X-User-Id`。
  - 适合验证：
    - `GET /api/v1/admin-console/ai/persistence/query`
    - `GET /api/v1/admin-console/ai/persistence/history/detail`
    - admin 页面“AI 持久化历史”与“AI 历史详情”面板
  - 栈拉起后，建议立刻执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./scripts/verify_admin_local_ai_persistence.sh
```

  - 该脚本会自动：
    - 等待 `home-service` 与 `device-service` readiness
    - 注册并登录一个本地测试用户，或复用外部注入的 `AUTH_TOKEN`
    - 直连验证 `GET /api/v1/admin-console/ai/persistence/query`
    - 优先从 `recentHistory` 自动抽取一条 `reportType + occurredAt` 再验证 `history/detail`
    - 将请求与响应证据落盘到 `artifacts/admin-local-ai-persistence/`
  - 若 query 成功但没有历史索引，先执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/verify_ai_business_live_flow.sh OFFLINE_FLAP
```

  - 或先执行一次 runtime drain，再重新运行上述验收脚本。

### 3.2 低磁盘 / 低构建成本旁路
- 启动前建议先执行环境诊断：

```bash
cd /Users/aiden/Projects/AIOT-java
./scripts/doctor_admin_local_env.sh
```

- 该诊断会检查：
  - `bash` / `curl` / `mvn` / `docker` 命令是否存在
  - `.env` 中关键密钥是否齐全
  - 当前磁盘余量是否低于预警阈值
  - Docker daemon 是否可在短超时内响应
- 若诊断失败，优先查看：
  - `artifacts/admin-local-doctor/doctor.log`
  - `artifacts/admin-local-doctor/docker-info.log`

- 若宿主机磁盘紧张，或不希望每次都执行 `docker compose ... up --build` 重建 `home/rule/device` 镜像，可改用：

```bash
cd /Users/aiden/Projects/AIOT-java
./scripts/start_admin_local_jvm.sh
```

- 该入口的策略是：
  - 仅使用 Docker 启动 `mysql` / `redis`
  - `aiot-home-service` / `aiot-rule-engine` / `aiot-device-service` 改为本地 `mvn spring-boot:run`
  - 默认先执行 `aiot_cloud` / `aiot_home` 的 Flyway 迁移，确保旁路不是“只起进程、不做库准备”的半成品
  - 自动关闭 `Nacos` 配置与注册依赖
  - 自动把 `device-service` 切到 `AIOT_SECURITY_ALLOW_DIRECT_USER_JWT=true`
  - 将日志与 PID 分别落盘到 `artifacts/admin-local-jvm/logs/` 与 `artifacts/admin-local-jvm/pids/`
- 适用边界：
  - 这是“低构建成本联调路径”，不是完全无 Docker 路径
  - 若 `mysql` / `redis` 已由外部环境提供，可用 `SKIP_INFRA_START=true` 跳过基础设施启动
  - 若数据库已提前完成迁移，可用 `SKIP_MIGRATIONS=true` 跳过 Flyway
- 启动后同样执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./scripts/verify_admin_local_ai_persistence.sh
```

- 若希望一条命令完成“启动 JVM 旁路 + 执行 query/detail 验收”，可直接执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./scripts/run_admin_local_jvm_verification.sh
```

- 可选控制项：
  - `START_STACK=false`：复用已运行的 JVM 旁路，只执行验收
  - `STOP_AFTER_VERIFY=true`：验收完成后自动调用 `./scripts/stop_admin_local_jvm.sh`
  - `AUTO_GENERATE_HISTORY=true`：当 `query` 里没有 `recentHistory` 时，自动执行 `./training/scripts/verify_ai_business_live_flow.sh OFFLINE_FLAP` 生成一条业务主链历史，再重试 `query/detail`

- 停止本地 JVM 进程：

```bash
cd /Users/aiden/Projects/AIOT-java
./scripts/stop_admin_local_jvm.sh
```

- 若 JVM 旁路启动失败，优先查看：
  - `artifacts/admin-local-jvm/logs/aiot-home-service.log`
  - `artifacts/admin-local-jvm/logs/aiot-rule-engine.log`
  - `artifacts/admin-local-jvm/logs/aiot-device-service.log`
  - `artifacts/admin-local-jvm/logs/flyway-aiot-cloud.log`
  - `artifacts/admin-local-jvm/logs/flyway-aiot-home.log`
- 若自动补历史已触发，附加查看：
  - `artifacts/admin-local-ai-persistence/auto-generate-history-output.log`
- 若启动中途失败且 `CLEAN_ON_FAILURE=true`，脚本会自动停止已拉起的本地 JVM 进程，避免遗留半启动状态。
- `start_admin_local_jvm.sh` 现在会在真正执行 `docker compose up` 前先做 Docker daemon 预检，避免在 Docker Desktop 不可用时先等待端口超时再失败。

## 4. 执行步骤
### 4.1 DDL 校验
- 执行：

```bash
cd /Users/aiden/Projects/AIOT-java
mvn -B -pl aiot-db-migrator -P aiot-cloud -Denforcer.skip=true -Dflyway.ignoreMigrationPatterns='*:pending' flyway:validate flyway:migrate
```

- 验证 MySQL 中存在：
  - `ai_diagnosis_record`
  - `ai_feedback_record`
  - `ai_case_library`
  - `ai_mysql_write_outbox`
  - `ai_case_materialization_task`
- 验证 `V1_1_1__ai_integrity_constraints.sql` 已生效：
  - `ai_feedback_record.diagnosis_id -> ai_diagnosis_record.diagnosis_id`
  - `ai_case_library.source_feedback_id -> ai_feedback_record.feedback_id`
  - `ai_case_library.source_feedback_id` 唯一约束已存在，防止同一反馈重复沉淀多个案例
- 验证 `V1_1_2__ai_mysql_outbox_tables.sql` 已生效：
  - MySQL 双写失败补偿进入 `ai_mysql_write_outbox`
  - case 沉淀失败补偿进入 `ai_case_materialization_task`
- 推荐直接执行活体验证：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/verify_mysql_constraints.sh OFFLINE_FLAP
```

- 产物：
  - `training/data/reports/persistence/mysql_constraints_verification.json`
- 注意：该迁移会在加外键前先清理历史孤儿数据：
  - 删除引用不存在 `feedback_id` 的案例行
  - 删除引用不存在 `diagnosis_id` 的反馈行

### 4.2 Redis -> MySQL 回填
- 先 dry-run：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/backfill_redis_to_mysql.sh OFFLINE_FLAP dry-run
```

- 再执行真实回填：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/backfill_redis_to_mysql.sh OFFLINE_FLAP
```

- 核查产物：
  - `training/data/backfill/redis_to_mysql_manifest.json`

### 4.3 MySQL 导出与一致性校验
- 执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/verify_mysql_export.sh OFFLINE_FLAP true
```

- 产物：
  - `training/data/raw/manifests/offline_flap_manifest.json`
  - `training/data/reports/persistence/offline_flap_consistency.json`

- 重点检查：
  - `consistent=true`
  - `totalMismatchCount=0`

### 4.4 迁移门禁判定
- 执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/check_migration_gate.sh OFFLINE_FLAP 0 1
```

- 产物：
  - `training/data/reports/persistence/offline_flap_migration_gate.json`

- 通过条件：
  - `gatePassed=true`
  - `backfillWrittenTotal >= 1`
  - `consistencyTotalMismatch <= 0`

### 4.5 灰度切换到 DUAL
- 先切配置：

```bash
export AIOT_AI_PERSISTENCE_MYSQL_ENABLED=true
export AIOT_AI_PERSISTENCE_READ_MODE=DUAL
export AIOT_AI_PERSISTENCE_CUTOVER_GUARD_ENABLED=true
```

- 发布 `aiot-rule-engine` 后观察：
  - `/actuator/prometheus` 中 AI persistence 读/写指标
  - `GET /api/v1/admin/ai/persistence/status`
  - `POST /api/v1/admin/ai/persistence/control-plane/drain`
  - `GET /api/v1/admin-console/overview`
  - `training/data/reports/persistence/ai_control_plane_redis_drain.json`

- 重点确认：
  - `mysqlReady=true`
  - `constraintsGatePassed=true`
  - `consistencyPassed=true`
  - `migrationGatePassed=true`
  - `configuredReadMode=DUAL`
  - `readMode=DUAL`
  - `mysqlWriteOutboxStoreMode=MYSQL_TABLE_PRIMARY`
  - `caseMaterializationStoreMode=MYSQL_TABLE_PRIMARY`
  - `mysqlWriteOutboxLegacyRedisPendingCount=0`
  - `caseMaterializationLegacyRedisPendingCount=0`
  - `controlPlaneRedisDrainCompleted=true`
  - `controlPlaneLastDrainAccepted=true`
  - `controlPlaneDrainStatus.report.exists=true`
  - `controlPlaneDrainStatus.report.source` 与执行入口一致（`runtime-admin` 或 training 脚本）
  - `controlPlaneDrainStatus.report.requestedStores` 与本次迁移范围一致
  - `controlPlaneDrainStatus.report.mysqlWriteOutboxRedisRemaining=0`
  - `controlPlaneDrainStatus.report.caseMaterializationRedisRemaining=0`
  - `mysqlWriteOutboxReplayEnabled=true`
  - `caseMaterializationReplayEnabled=true`
  - `mysqlWriteOutboxPendingCount=0`
  - `caseMaterializationPendingCount=0`
  - fallback 指标未持续上升

- 说明：
  - 当 `configuredReadMode=DUAL` 且 `mysqlCutoverReady=true` 时，运行态会按 `MySQL-first -> Redis fallback` 执行灰度主读。
  - 当 `configuredReadMode=DUAL` 但门禁证据不足时，运行态仍保持 `Redis-first -> MySQL fallback`，避免提前把主读流量压到 MySQL。
  - 若 `training/data/reports/persistence/ai_control_plane_redis_drain.json` 尚未生成，`/api/v1/admin/ai/persistence/status` 会显式返回 `controlPlaneDrainStatus.report.exists=false`，可据此判断证据缺失而非误认为“已完成”。

### 4.6 最终切换到 MYSQL
- 满足以下条件后再切：
  - 回填完成
  - 约束活体验证通过
  - 一致性报告通过
  - 迁移门禁通过
  - DUAL 观察窗口内无异常 fallback 抖动

- 切配置：

```bash
export AIOT_AI_PERSISTENCE_READ_MODE=MYSQL
export AIOT_AI_PERSISTENCE_CUTOVER_GUARD_ENABLED=true
```

- 若历史 Redis 补偿任务尚未清空，先执行：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/drain_control_plane_redis.sh dry-run
./training/scripts/drain_control_plane_redis.sh drain
```

- 或在 `rule-engine` admin 面直接执行一次带留痕的 runtime drain：

```bash
curl -X POST http://127.0.0.1:8084/api/v1/admin/ai/persistence/control-plane/drain \
  -H 'Content-Type: application/json' \
  -d '{"operator":"ops-admin","dryRun":false,"batchSize":100,"stores":["MYSQL_WRITE_OUTBOX","CASE_MATERIALIZATION"]}'
```

- 若只想灰度迁移单类控制面任务，可先对单个 store 执行：

```bash
curl -X POST http://127.0.0.1:8084/api/v1/admin/ai/persistence/control-plane/drain \
  -H 'Content-Type: application/json' \
  -d '{"operator":"ops-admin","dryRun":false,"batchSize":50,"stores":["MYSQL_WRITE_OUTBOX"]}'
```

- 发布后重点确认：
  - `/api/v1/admin/ai/persistence/status` 中 `configuredReadMode=MYSQL`
  - `/api/v1/admin/ai/persistence/status` 中 `readMode=MYSQL`
  - `/api/v1/admin/ai/persistence/status` 中 `mysqlCutoverReady=true`
  - `/api/v1/admin/ai/persistence/status` 中 `mysqlCutoverBlockReason` 为空
  - `/api/v1/admin/ai/persistence/status` 中 `mysqlWriteOutboxStoreMode=MYSQL_TABLE_PRIMARY`
  - `/api/v1/admin/ai/persistence/status` 中 `caseMaterializationStoreMode=MYSQL_TABLE_PRIMARY`
  - `/api/v1/admin-console/overview` 中 `aiPersistenceConfiguredReadMode=MYSQL`
  - `/api/v1/admin-console/overview` 中 `aiPersistenceReadMode=MYSQL`

- 说明：
  - 当 `AIOT_AI_PERSISTENCE_CUTOVER_GUARD_ENABLED=true` 且 `readMode=MYSQL` 时，若 `consistency / constraints / migration gate / pending queues` 任一不满足，运行态会自动回落到 `REDIS`。
  - 如需强制跳过保护，仅用于应急排障：

```bash
export AIOT_AI_PERSISTENCE_ALLOW_UNSAFE_MYSQL_READ=true
```

### 4.7 Runtime Drain Apply 活体验证
- 适用场景：
  - 需要证明 `POST /api/v1/admin/ai/persistence/control-plane/drain` 在 `MYSQL_TABLE_PRIMARY` 下会把 Redis legacy task 真实迁移到 MySQL
  - 需要隔离 replay scheduler，避免后台自动迁移污染证据

- 先清空控制面任务表与 Redis legacy key：

```bash
docker exec aiot-mysql mysql -uroot -p${MYSQL_PASSWORD} -Daiot_cloud -e \
  "DELETE FROM ai_mysql_write_outbox; DELETE FROM ai_case_materialization_task;"
redis-cli -h 127.0.0.1 -p 6379 DEL aiot:ai:mysql-write-outbox aiot:ai:case-materialization-outbox
```

- 用 MySQL primary + replay 关闭模式启动 `aiot-rule-engine`：

```bash
export AIOT_AI_PERSISTENCE_MYSQL_ENABLED=true
export AIOT_AI_PERSISTENCE_READ_MODE=REDIS
export AIOT_AI_PERSISTENCE_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/aiot_cloud?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export AIOT_AI_PERSISTENCE_MYSQL_USER=root
export AIOT_AI_PERSISTENCE_MYSQL_PASSWORD="${MYSQL_PASSWORD}"
export AIOT_AI_PERSISTENCE_MYSQL_OUTBOX_REPLAY_ENABLED=false
export AIOT_AI_CASE_MATERIALIZATION_REPLAY_ENABLED=false
export REDIS_HOST=127.0.0.1
```

- 注入两类 Spring `GenericJackson2JsonRedisSerializer` 兼容的 legacy task：

```bash
cd /Users/aiden/Projects/AIOT-java
./training/scripts/seed_control_plane_legacy_tasks.sh
```

- 先看 apply 前状态：

```bash
curl http://127.0.0.1:8084/api/v1/admin/ai/persistence/status
```

- 重点确认：
  - `mysqlWriteOutboxStoreMode=MYSQL_TABLE_PRIMARY`
  - `caseMaterializationStoreMode=MYSQL_TABLE_PRIMARY`
  - `mysqlWriteOutboxLegacyRedisPendingCount=1`
  - `caseMaterializationLegacyRedisPendingCount=1`
  - `mysqlWriteOutboxReplayEnabled=false`
  - `caseMaterializationReplayEnabled=false`

- 执行真实 apply：

```bash
curl -X POST http://127.0.0.1:8084/api/v1/admin/ai/persistence/control-plane/drain \
  -H 'Content-Type: application/json' \
  -d '{"operator":"ops-admin","dryRun":false,"batchSize":10,"stores":["MYSQL_WRITE_OUTBOX","CASE_MATERIALIZATION"]}'
```

- 通过证据：
  - 返回体中：
    - `accepted=true`
    - `mysqlWriteOutboxLegacyRedisBefore=1`
    - `caseMaterializationLegacyRedisBefore=1`
    - `mysqlWriteOutboxMigrated=1`
    - `caseMaterializationMigrated=1`
    - `mysqlWriteOutboxLegacyRedisAfter=0`
    - `caseMaterializationLegacyRedisAfter=0`
    - `controlPlaneRedisDrainCompleted=true`
  - `GET /api/v1/admin/ai/persistence/status` 中：
    - `controlPlaneDrainStatus.lastExecution.dryRun=false`
    - `controlPlaneDrainStatus.lastExecution.accepted=true`
    - `controlPlaneDrainStatus.report.source=runtime-admin`
    - `controlPlaneDrainStatus.report.mysqlWriteOutboxMysqlWritten=1`
    - `controlPlaneDrainStatus.report.caseMaterializationMysqlWritten=1`
    - `controlPlaneDrainStatus.report.mysqlWriteOutboxRedisRemaining=0`
    - `controlPlaneDrainStatus.report.caseMaterializationRedisRemaining=0`
  - Redis 中：
    - `HLEN aiot:ai:mysql-write-outbox = 0`
    - `HLEN aiot:ai:case-materialization-outbox = 0`
  - MySQL 中：
    - `ai_mysql_write_outbox` 存在 `outbox-task-apply`
    - `ai_case_materialization_task` 存在 `case-task-apply`

### 4.8 Business Main-Flow Live 验证
- 适用场景：
  - 需要证明 `POST /api/v1/ai/diagnosis -> POST /api/v1/ai/feedback` 主业务链已经按 `MySQL-first + Redis mirror` 落库
  - 需要证明真实业务记录进入 `ai_diagnosis_record / ai_feedback_record / ai_case_library`，同时不产生 MySQL/Redis 补偿残留

- 建议沿用 4.7 的运行态前提：
  - `aiot-rule-engine` 已用 `AIOT_AI_PERSISTENCE_MYSQL_ENABLED=true` 启动
  - `mysqlWriteOutboxStoreMode=MYSQL_TABLE_PRIMARY`
  - `caseMaterializationStoreMode=MYSQL_TABLE_PRIMARY`
  - 若要隔离补偿线程噪音，继续保持：

```bash
export AIOT_AI_PERSISTENCE_MYSQL_OUTBOX_REPLAY_ENABLED=false
export AIOT_AI_CASE_MATERIALIZATION_REPLAY_ENABLED=false
```

- 执行：

```bash
cd /Users/aiden/Projects/AIOT-java
set -a && source ./.env && set +a
export AIOT_MYSQL_PASSWORD="${MYSQL_PASSWORD}"
./training/scripts/verify_ai_business_live_flow.sh OFFLINE_FLAP
```

- 产物：
  - `training/data/reports/persistence/ai_business_live_flow.json`
  - `training/data/reports/persistence/ai_business_live_flow_index.json`
  - `training/data/reports/persistence/history/ai_business_live_flow_<timestamp>.json`
  - `training/data/reports/persistence/ai_control_plane_redis_drain_index.json`
  - `training/data/reports/persistence/history/ai_control_plane_redis_drain_<timestamp>.json`
  - `GET /api/v1/admin/ai/persistence/status` 将同步回显 business live flow 摘要
  - `GET /api/v1/admin/ai/persistence/query` 提供统一的 AI persistence admin query contract，并包含 `controlPlaneDrain.recentHistory` 与 `businessLiveFlow.recentHistory`
  - `GET /api/v1/admin/ai/persistence/business-live-flow` 提供独立的业务活体验证查询接口
  - `GET /api/v1/admin-console/ai/persistence/query` 提供 admin-console 聚合后的统一 query contract
  - `GET /api/v1/admin-console/ai/persistence/business-live-flow` 提供 admin-console 聚合后的独立查询接口
  - `GET /api/v1/admin/ai/persistence/history/detail?reportType=<REPORT_TYPE>&occurredAt=<TIMESTAMP>` 提供按 `reportType + occurredAt` 定位归档详情的后端接口
  - `GET /api/v1/admin-console/ai/persistence/history/detail?reportType=<REPORT_TYPE>&occurredAt=<TIMESTAMP>` 提供 admin-console 聚合后的历史详情接口
  - `device-service` admin-console overview 将展示最近一次业务主链活体验证结果
  - `device-service` admin-console 新增 “AI 持久化历史” 面板，直接展示统一 query contract 返回的最近历史索引
  - `device-service` admin-console 新增 “AI 历史详情” 面板，可从历史表点击“查看详情”加载归档 JSON 与元数据

- 通过证据：
  - 返回体中：
    - `success=true`
    - 存在 `diagnosisId`
    - 存在 `feedbackId`
    - 存在 `caseId`
  - report 中：
    - `statusBefore.mysqlEnabled=true`
    - `statusBefore.mysqlReady=true`
    - `statusBefore.mysqlWriteOutboxStoreMode=MYSQL_TABLE_PRIMARY`
    - `statusBefore.caseMaterializationStoreMode=MYSQL_TABLE_PRIMARY`
    - `responses.feedback.feedbackSaved=true`
    - `responses.feedback.caseSaved=true`
    - `responses.feedback.caseStatus=CREATED`
    - `mysql.diagnosisRow` 非空
    - `mysql.feedbackRow` 非空
    - `mysql.caseRow` 非空
    - `mysql.mysqlWriteOutboxCount=0`
    - `mysql.caseMaterializationTaskCount=0`
    - `redis.diagnosisMirrorExists=true`
    - `redis.feedbackMirrorExists=true`
    - `redis.caseMirrorExists=true`
    - `redis.mysqlWriteOutboxHlen=0`
    - `redis.caseMaterializationHlen=0`

- 说明：
  - 该验证会先清空三张业务主表、两张补偿表与对应 Redis hash，确保本次证据不受历史数据污染。
  - `diagnosis` 接口允许在 `device-service` / `data-parser` 暂不可用时降级为空上下文 + 空知识检索，因此该 live proof 仍可独立验证 AI persistence 主写路径。
  - history detail 查询仅接受 `reportType + occurredAt` 作为定位键，前端不直接传任意文件路径；这保证了后续把归档从本地文件切到对象存储或数据库时，对外契约无需重写。
  - 当前支持的 `reportType` 至少包括：
    - `BUSINESS_LIVE_FLOW`
    - `AI_CONTROL_PLANE_REDIS_DRAIN`
  - admin-console 验收动作：
    - 先调用 `/api/v1/admin-console/ai/persistence/query` 拿到 `businessLiveFlow.recentHistory` 与 `controlPlaneDrain.recentHistory`
    - 再用返回条目的 `reportType + occurredAt` 点击“查看详情”
    - 验收 `exists=true`、`reportPath` 非空、详情元数据与归档 JSON 正常展示
  - 归档保留策略：
    - `control-plane drain` 默认保留最近 20 条，可通过 `AIOT_AI_PERSISTENCE_CONTROL_PLANE_DRAIN_HISTORY_LIMIT` 调整
    - `business live flow` 当前固定保留最近 20 条，由 `training/src/aiot_training/live_proof/verify_business_flow.py` 中的 `HISTORY_LIMIT` 控制
    - 超出保留上限后会同时裁剪 `*_index.json` 并删除 `history/` 目录中的旧归档文件，因此 `history/` 只能作为近期审计缓存，不能视作永久留存目录

## 5. 回滚步骤
### 5.1 快速回滚
- 若切换到 `MYSQL` 后出现异常，立即回退：

```bash
export AIOT_AI_PERSISTENCE_READ_MODE=REDIS
```

- 重启或滚动发布 `aiot-rule-engine`

### 5.2 回滚触发条件
- `migrationGatePassed=false`
- `consistencyPassed=false`
- `consistencyTotalMismatch > 0`
- `aiot.ai.persistence.read.fallback.total` 持续升高
- MySQL 读失败或 p95 时延异常放大

## 6. 观测入口
- `rule-engine`：
  - `GET /api/v1/admin/ai/persistence/status`
  - `/actuator/prometheus`
- `device-service admin-console`：
  - `GET /api/v1/admin-console/overview`
  - `/admin/index.html`

## 7. 交付证据
- [ ] Backfill manifest
- [ ] Consistency report
- [ ] Migration gate report
- [ ] Outbox/task MySQL 表存在性与插入验证记录
- [ ] Control-plane Redis drain report
- [ ] 切换前后配置截图或发布记录
- [ ] 关键监控截图
- [ ] 回滚路径验证记录
