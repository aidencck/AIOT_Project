# 发布回滚闭环门禁清单（Release/Rollback Loop Gate）

## 目标

把「本地验证 → 构建 → CI 门禁 → 远程发布 → 灰度/回滚 → 巡检复盘」串成一条可重复、可留痕的闭环，全程在 Trae 内完成，不切换终端/SSH 客户端/CI 面板。

## 闭环总览（5 阶段，前段不过禁入后段）

| 阶段      | 入口命令                                            | 执行载体（Skill/MCP/Agent）                          | 成功判据                                                              | 失败动作            |
| ------- | ----------------------------------------------- | ---------------------------------------------- | ----------------------------------------------------------------- | --------------- |
| ① 本地验证  | `./aiotctl infra up` + `./aiotctl test <suite>` | 主线程 RunCommand                                 | `verify infra` 全端口就绪 + 测试全绿                                       | 修复重跑，禁入②        |
| ② 构建    | `./aiotctl build jars`                          | 主线程 RunCommand                                 | `mvn clean package -DskipTests` 0 失败                              | 修编译/依赖，禁入③      |
| ③ CI 门禁 | `git push` → GitHub Actions                     | mcp\_GitHub + mcp\_Sequential\_Thinking        | gitleaks/trivy/layering/coverage/communication-guard 全绿 + 镜像 push | 修门禁项后重推         |
| ④ 远程发布  | ssh → migrate → canary+rollback                 | aiot-remote-dev Skill + devops-architect Agent | 灰度窗口内健康持续通过                                                       | 自动回滚 + 留档       |
| ⑤ 巡检复盘  | `release-health` + 留痕                           | aiot-remote-dev Skill + mcp\_Memory            | `verify prod` 通过 + 证据归档                                           | 写 runbook + 复盘卡 |

## ① 本地验证（发布前必过）

```bash
./aiotctl infra up                 # P0: 仅 4 个中间件容器，IDE 直跑 JVM
./aiotctl test communication       # 服务通信联调
./aiotctl test webhook             # 配网 + EMQX webhook
./aiotctl test mqtt                # MQTT 数据解析闭环
./aiotctl test shadow              # 影子设备事件流
./aiotctl verify infra             # 判据：全端口就绪
```

## ② 构建

```bash
./aiotctl build jars               # Maven 打包（跳过测试，并行 1C）
```

## ③ CI 门禁（合并主分支触发，无需本地执行）

- 触发：push `main` → [.github/workflows/ci-cd.yml](../../.github/workflows/ci-cd.yml)
- 门禁链（15 job，全绿才推镜像/发布）：
  1. `service-registry-guard`（服务注册一致性 + 拓扑端口）
  2. `build-and-test`：`gitleaks`（密钥）→ `check_layering`（分层）→ `trivy`（漏洞）→ `weak-secret gate`（弱凭据）→ `mvn verify`（构建+单测）→ `coverage gate`（行/分支覆盖率）
  3. `python-tests`（edge agent + device simulator + training）
  4. `db-migration-validate`（Flyway validate + migrate）
  5. `training-consistency-gate`（迁移一致性 + 约束校验 + backfill）
  6. `communication-guard`（shellcheck + 内部 token 配置）
  7. `observability-smoke`（actuator/prometheus 配置基线）
  8. `observability-runtime-smoke`（运行时 readiness 冒烟）
  9. `dependency-reachability-guard`（compose 依赖可达性）
  10. `artifact-consistency-gate`（jar vs source 一致性）
  11. `docker-build`（ghcr.io 镜像构建 + push）
  12. `frontend-build`（vue-tsc + vitest + vite build）
  13. `service-communication-e2e`（服务通信 + 配网 webhook）
- 判据：上述全绿且镜像 `sha-<full>` 已推送到 registry。

## ④ 远程发布（ssh 直连 /opt/aiot-cloud，见 aiot-remote-dev Skill）

发布前必须：③ 门禁已过、镜像 tag 已存在、且已触发 NotifyUser 审批门禁获用户确认。

> 长链路（发布/迁移/回滚）执行前用 `create_goal` 建立目标追踪，完成后 `update_goal(complete)`，被阻断 `update_goal(blocked)`。

```bash
ssh aiot-prod "cd /opt/aiot-cloud && bash scripts/migrate_database.sh --db all"          # 迁移先行
ssh aiot-prod "cd /opt/aiot-cloud && bash scripts/release_canary_with_rollback.sh \
  --service <svc> --tag sha-<SHA> --health-timeout 180 --canary-seconds 120"              # 单服务灰度+自动回滚
```

脚本内闭环（勿手工跳步）：`release_gate_check.sh` → `deploy_single_service.sh`（首次健康）→ 灰度巡检 `verify_release_health.sh` → 失败自动 `rollback_single_service.sh`。

## ⑤ 巡检复盘

```bash
ssh aiot-prod "cd /opt/aiot-cloud && ./aiotctl release-health prod <svcs...>"             # 发布后健康门禁
ssh aiot-prod "cd /opt/aiot-cloud && ./aiotctl verify prod"                                # 判据：全端口就绪
```

留痕：证据写入 `docs/tasks/`（任务分工）与 `docs/runbooks/`（演练/回滚记录），结论经 mcp\_Memory 沉淀。

## 服务清单（端口单一事实源：scripts/lib/services.json）

<!-- AIOT-GEN: port-table -->
| 服务                  | 端口   | 服务                  | 端口   |
| ------------------- | ---- | ------------------- | ---- |
| aiot-gateway         | 8080  | aiot-rule-engine     | 8084  |
| aiot-device-service  | 8081  | aiot-mqtt-adapter    | 8085  |
| aiot-auth-service    | 8082  | aiot-data-parser     | 8086  |
| aiot-home-service    | 8083  | aiot-shadow-service  | 8087  |
<!-- AIOT-GEN-END: port-table -->

## 演练命令（验证闭环本身可用）

```bash
./aiotctl drill rollback          # 发布→回滚→恢复演练（默认含运行负载+正确性验证）
./aiotctl drill canary            # 灰度演练
./aiotctl drill inject-fault      # 故障注入演练
./aiotctl drill load-verify       # 独立运行负载+正确性验证（对应 verify_drill_load_correctness.sh）
```

> `drill rollback` 在「发布后」与「回滚恢复后」各执行一次运行负载（吞吐/延迟）+ 正确性（数据链路一致）冒烟验证，
> 复用 `scripts/perf_user_device_observability.py`。可观测栈（Prometheus/Loki/Tempo）不可用时自动软降级为 `degraded` 跳过，
> 也可显式 `--skip-load-verify` 跳过。默认冒烟规模 50 用户 × 2 设备 / 并发 50，报告含 `load_compare`（baseline/recovery/delta）对比。

