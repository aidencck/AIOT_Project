---
name: "aiot-remote-dev"
description: "AIoT 全生命周期统一运维入口：本机 ./aiotctl、远端 SSH 直连 /opt/aiot-cloud，覆盖 G0 自检→G7 观测，每 Gate 含硬性验收门禁与可度量指标。当需要开发/构建/测试/迁移/发布/回滚/运维 AIoT 服务时触发。"
---

# AIoT 全生命周期工具（分 Gate 门禁）

## 绑定 Agent / 执行依赖
- 本 Skill 不绑定项目 read-only Agent（区别于 4 个分析 Skill）；执行依赖内置 `devops-architect` subagent 与 `./aiotctl` 单入口命令。
- 写操作（`deploy`/`rollback`/`migrate`）受上层编排者 `vibe-ops-master` 的 `NotifyUser` 审批门禁约束，见「安全约束」。

单入口原则：所有操作收敛到 `./aiotctl`（本机）与 `ssh aiot-prod "cd /opt/aiot-cloud && ./aiotctl ..."`（远端），命令同构。Makefile 仅为快捷转发，无独立逻辑。

## 执行上下文（二选一，命令模板统一）

| 上下文 | 前缀变量 | 触发条件 |
|--------|----------|----------|
| 本机 | `AIOTCTL="./aiotctl"` | 开发/构建/测试/演练 |
| 远端 prod | `AIOTCTL='ssh aiot-prod "cd /opt/aiot-cloud && ./aiotctl"'` | 迁移/发布/回滚/观测 |
| 远端 staging | `AIOTCTL='ssh aiot-staging "cd /opt/aiot-cloud && ./aiotctl"'` | 预发 |

连接前提（一次性）：`~/.ssh/config` 建 `aiot-prod` / `aiot-staging` 别名，密钥 `IdentityFile` 指向私钥；校验 `ssh aiot-prod "cd /opt/aiot-cloud && git rev-parse --short HEAD && docker compose version"`。

## 服务与端口（单一事实源 scripts/lib/services.json）

<!-- AIOT-GEN: port-table -->
| 服务 | 端口 | 服务 | 端口 |
|---|---|---|---|
| aiot-gateway | 8080 | aiot-rule-engine | 8084 |
| aiot-device-service | 8081 | aiot-mqtt-adapter | 8085 |
| aiot-auth-service | 8082 | aiot-data-parser | 8086 |
| aiot-home-service | 8083 | aiot-shadow-service | 8087 |
<!-- AIOT-GEN-END: port-table -->

中间件端口：MySQL 3306 / Redis 6379 / EMQX 1883 / EMQX-API 18083。健康端点统一 `/actuator/health/readiness`。

---

## 全生命周期 Gate 总览

| Gate | 阶段 | 阻断性 | 主命令 | 验收判据 |
|------|------|--------|--------|----------|
| G0 | 环境自检 | 是 | `doctor` | issue=0 |
| G1 | 构建 | 是 | `build all` | jar+image 产出成功 |
| G2 | 测试 | 是 | `test <suite>` | 全通过 |
| G3 | 数据迁移 | 是 | `migrate` + `ai migrate-gate` | Flyway 版本一致 & mismatch=0 |
| G4 | 发布门禁 | 是 | `gate` / `release_gate_check.sh` | 4 项检查全过 |
| G5 | 灰度发布 | 是 | `canary` / `deploy` | 灰度健康 & 零回滚 |
| G6 | 发布后验证 | 是 | `verify` + `release-health` + 性能门禁 | TCP+HTTP+性能阈值全过 |
| G7 | 观测诊断 | 否 | `logs`/`jvm-diagnostics`/`observability` | 日志可查/指标可采集 |

---

## Gate 明细

### G0 环境自检
- 命令：`$AIOTCTL doctor`
- 检查项：命令存在(docker/mvn/curl/jq) / compose 存在 / 密钥长度 / compose 语法(7 模式) / 端口占用
- 验收：输出 `DOCTOR PASSED (0 issues)`，任一 issue → 阻断

### G1 构建
- 命令：`$AIOTCTL build jars`（Maven 打包，skip test）→ `$AIOTCTL build images`（compose 分层 build）
- 验收：jar 与镜像均成功产出；镜像 tag 可被后续 gate 拉取
- 度量：`time $AIOTCTL build all` 记录构建耗时（基线见评估体系）

### G2 测试
- 命令：`$AIOTCTL test communication|e2e|webhook|mqtt|shadow`
- 验收：各套件退出码 0

### G3 数据迁移
- 命令：`scripts/migrate_database.sh --db all`（Flyway）；`$AIOTCTL ai migrate-gate`（迁移门禁）
- 验收：Flyway 版本一致；`--max-total-mismatch 0 --min-written-total 1`

### G4 发布门禁
- 命令：`$AIOTCTL gate <env> <service...>`（内部 `release_gate_check.sh`）
- 4 项检查：compose 服务存在 / 服务含 healthcheck / 目标镜像可拉取 / 基线健康
- 验收：输出 `[Gate] 通过`

### G5 灰度发布 / 回滚
- 命令：`scripts/release_canary_with_rollback.sh --service <svc> --tag <sha> --health-timeout 180 --canary-seconds 120`
- 发布链路（脚本内闭环，禁跳步）：`release_gate_check.sh` → `deploy_single_service.sh` → `verify_release_health.sh` → 失败自动 `rollback_single_service.sh`
- 回滚：`scripts/rollback_single_service.sh --service <svc>`
- 验收：灰度健康通过，未触发自动回滚

### G6 发布后验证
- 命令：`$AIOTCTL verify <env>`（TCP+健康）；`$AIOTCTL release-health <env> <svc>`；`python3 scripts/perf/assert_perf_gate.py <report> ci-smoke`
- 验收：TCP 全端口通 + HTTP readiness 200 + 性能阈值（见评估体系 G6）

### G7 观测诊断
- 命令：`$AIOTCTL logs <mode> [svc]` / `$AIOTCTL jvm-diagnostics <svc>` / `$AIOTCTL observability up|down`
- 验收：非阻断，日志可 tail、JVM 指标可读

---

## 评估体系（度量 + 阈值 + 优化闭环）

### 门禁阈值表（硬门禁，fail 即阻断）

| Gate | 指标 | 阈值 | 度量来源 |
|------|------|------|----------|
| G0 | doctor issue 数 | = 0 | `aiotctl doctor` |
| G2 | 测试套件通过率 | 100% | `aiotctl test` 退出码 |
| G3 | 迁移 mismatch 总数 | ≤ 0 | `ai migrate-gate --max-total-mismatch 0` |
| G4 | 门禁通过率 | 100% | `release_gate_check.sh` |
| G6 | 成功请求比 success_ratio | ≥ 0.995 (ci-smoke) / ≥ 0.999 (nightly) | `assert_perf_gate.py` |
| G6 | HTTP 5xx 数 | = 0 (ci-smoke) | 同上 |
| G6 | 网络失败数 | = 0 | 同上 |
| G6 | P95 延迟 | ≤ 0.200s (ci-smoke) / ≤ 0.300s (nightly) | 同上 |
| G6 | P99 延迟 | ≤ 0.400s (ci-smoke) / ≤ 0.800s (nightly) | 同上 |

> 阈值唯一事实源：`scripts/perf/assert_perf_gate.py` 的 `THRESHOLDS`（profile = `ci-smoke` / `nightly-baseline`）。调阈值只改此文件，不散落。

### 效率指标（趋势度量，用于后期优化）

| 指标 | 采集方式 | 优化目标 |
|------|----------|----------|
| 构建耗时 (G1) | `time ./aiotctl build all` | 下降（并行/缓存/分层复用） |
| 测试耗时 (G2) | `time ./aiotctl test e2e` | 下降 |
| 发布成功率 (G5) | 灰度次数 vs 自动回滚次数 | → 100% |
| 回滚率 (G5) | 回滚次数 / 发布次数 | → 0 |
| 门禁拦截率 (G4) | 门禁 fail 次数 / 执行次数 | 上升（左移，提前拦截缺陷） |
| 发布后回归 (G6) | 性能门禁 fail 次数 | → 0 |

### 优化闭环（每次执行后落地）
1. 每 Gate 输出统一 JSON 摘要（`status` / `metrics` / `elapsed_sec`），参照 `assert_perf_gate.py` 的输出结构。
2. 任一硬门禁 fail → 记录 gate/服务/原因，阻断下游。
3. 效率指标超基线 → 进入优化队列，按「耗时/失败率」排序处置。

---

## 安全约束（必须遵守）
- 密钥/密码不落仓库与本文档；只放 `~/.ssh/` 与远端 `runtime.env`。
- 默认只读：巡检/日志/门禁/状态可直接执行；`deploy`/`rollback`/`migrate` 写操作必须先触发 `NotifyUser` 审批门禁，获用户确认后方可执行（禁止跳过）。
- 迁移前确认 Flyway 版本；发布前确认镜像 tag 已存在仓库。
- 发布按「单服务灰度 + 自动回滚」最小闭环，严禁 `docker compose up -d` 全量覆盖生产。

## Agent 执行约定
- 长链路任务（发布/迁移/回滚）执行前用 `create_goal` 建立目标，完成后 `update_goal(complete)`，被阻断 `update_goal(blocked)`。
- 单命令优先：直接 `RunCommand` 跑 `$AIOTCTL ...`，返回即展示，不落文件。
- 长任务（发布/灰度/构建）用 `blocking:false` + `CheckCommandStatus` 轮询；失败回滚而非重试。
- 结果口径统一：`./aiotctl verify <env>` 的 TCP+健康 + `assert_perf_gate.py` 的 JSON `status=passed` 作为「发布成功」唯一判据。
