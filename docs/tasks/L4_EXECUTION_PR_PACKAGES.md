# L4 升级落地 PR 作战手册

## 目标
- 将当前改造拆分为 4 个可并行开发、可串行合并的 PR 包。
- 每个 PR 包都具备：范围边界、负责人建议、验收命令、回滚点。

## 合并顺序（必须）
1. `PR-1 质量门禁统一`
2. `PR-2 观测栈补齐`
3. `PR-3 MQTT/Data-Parser 业务闭环`
4. `PR-4 演练与留痕闭环`

## PR-1 质量门禁统一（平台组）
- 建议分支名：`feat/l4-ci-coverage-unify`
- 目标：统一 JaCoCo 与 CI 覆盖率门禁口径，补齐 `aiot-data-parser` 变更检测。
- 变更文件：
  - `.github/workflows/ci-cd.yml`
  - `pom.xml`
  - `aiot-auth-service/pom.xml`
  - `aiot-device-service/pom.xml`
  - `aiot-home-service/pom.xml`
- 验收命令：
```bash
mvn -B -T 1C clean verify --file pom.xml
```
- DoD：
  - CI `detect-changes` 包含 `aiot-data-parser`。
  - CI 覆盖率阈值与父 POM 一致（单一来源）。
  - 任意变更模块构建后有 `target/site/jacoco/jacoco.xml`。
- 回滚点：
  - 仅回退 `.github/workflows/ci-cd.yml` 与 `pom.xml` 的覆盖率相关改动。

## PR-2 观测栈补齐（DevOps/SRE）
- 建议分支名：`feat/l4-observability-stack`
- 目标：落地 Loki/Promtail/Tempo/Grafana 最小可用观测平台。
- 变更文件：
  - `docker-compose.yml`
  - `monitoring/prometheus/prometheus.yml`
  - `monitoring/loki/loki.yml`
  - `monitoring/promtail/promtail.yml`
  - `monitoring/tempo/tempo.yml`
  - `monitoring/grafana/provisioning/datasources/datasources.yml`
- 验收命令：
```bash
docker compose up -d prometheus loki promtail tempo grafana
docker compose ps
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:3100/ready
curl -fsS http://localhost:3200/ready
```
- DoD：
  - Grafana 能看到 Prometheus/Loki/Tempo 数据源。
  - Prometheus 能加载 alerts 目录规则。
  - Promtail 能采集容器日志并送入 Loki。
- 回滚点：
  - 回退 `docker-compose.yml` 观测服务区块和 `monitoring/*` 新增配置。

## PR-3 MQTT/Data-Parser 业务闭环（设备接入组+解析组）
- 建议分支名：`feat/l4-mqtt-parser-e2e`
- 目标：打通 `mqtt-adapter -> data-parser -> Redis Stream`，保持现有事件总线契约不变。
- 变更文件：
  - `aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/config/HttpClientConfig.java`
  - `aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/controller/MqttIngressController.java`
  - `aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/dto/*`
  - `aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/service/*`
  - `aiot-mqtt-adapter/src/main/resources/application.yml`
  - `aiot-data-parser/src/main/java/com/aiot/data/controller/InternalParserController.java`
  - `aiot-data-parser/src/main/java/com/aiot/data/dto/*`
  - `aiot-data-parser/src/main/java/com/aiot/data/service/*`
  - `aiot-data-parser/src/main/java/com/aiot/data/DataParserApplication.java`
  - `aiot-data-parser/src/main/resources/application.yml`
  - `aiot-data-parser/pom.xml`
  - `docker-compose.yml`
  - `scripts/test_mqtt_data_parser_loop.sh`
- 验收命令：
```bash
mvn -B -pl aiot-mqtt-adapter,aiot-data-parser -am test
bash scripts/test_mqtt_data_parser_loop.sh
```
- DoD：
  - `MqttIngressController` 可接收消息并成功调用 data-parser。
  - data-parser 解析 online/offline 后写入 `aiot:stream:device-event`。
  - 写流字段保持 `eventId/eventType/deviceId/payload`。
- 回滚点：
  - 回退 `aiot-mqtt-adapter` 与 `aiot-data-parser` 新增接口和 compose 对应环境变量。

## PR-4 演练与留痕闭环（SRE/发布组）
- 建议分支名：`feat/l4-drill-audit`
- 目标：提供故障注入、一键演练、结构化留痕与 runbook 单入口。
- 变更文件：
  - `scripts/inject_fault.sh`
  - `scripts/run_release_rollback_drill.sh`
  - `scripts/rollback_single_service.sh`
  - `docs/runbooks/README.md`
  - `docs/runbooks/release-drill-template.md`
  - `docs/runbooks/single-service-rollback.md`
- 验收命令：
```bash
bash -n scripts/inject_fault.sh
bash -n scripts/run_release_rollback_drill.sh
bash -n scripts/rollback_single_service.sh
scripts/run_release_rollback_drill.sh --help
```
- DoD：
  - 可执行完整链路：发布 -> 注入 -> 失败验证 -> 回滚 -> 恢复验证。
  - 输出 JSON/JSONL 结构化留痕，含 `trace_id`、`operator`、`result`、`duration`。
  - runbook 明确入口、步骤、验收标准。
- 回滚点：
  - 仅回退 `scripts/` 新增文件与 `rollback_single_service.sh` 留痕增强段落。

## 分派建议（RACI）
- 平台组：`PR-1` Responsible，架构组 Accountable。
- DevOps/SRE：`PR-2` 与 `PR-4` Responsible，架构组 Accountable。
- 设备接入组：`PR-3` 中 mqtt-adapter 部分 Responsible。
- 数据解析组：`PR-3` 中 data-parser 部分 Responsible。
- QA：全 PR Consulted，重点负责端到端回归和演练验收。

## 发布门禁建议
- 所有 PR 合并前必须通过：
  - Maven 构建与测试
  - CI 覆盖率门禁
  - compose 配置有效性检查
  - 脚本语法检查（`bash -n`）
