# L4 升级 PR 拆分提交流程

## 使用方式
- 在项目根目录执行。
- 每个 PR 包执行一组 `git add`，然后单独 `git commit`。
- 合并顺序按 `PR-1 -> PR-2 -> PR-3 -> PR-4`。

## PR-1 质量门禁统一
```bash
git add \
  .github/workflows/ci-cd.yml \
  pom.xml \
  aiot-auth-service/pom.xml \
  aiot-device-service/pom.xml \
  aiot-home-service/pom.xml

git commit -m "chore(ci): unify jacoco and coverage gates across modules"
```

## PR-2 观测栈补齐
```bash
git add \
  docker-compose.yml \
  monitoring/prometheus/prometheus.yml \
  monitoring/loki/loki.yml \
  monitoring/promtail/promtail.yml \
  monitoring/tempo/tempo.yml \
  monitoring/grafana/provisioning/datasources/datasources.yml

git commit -m "feat(observability): add loki promtail tempo grafana baseline stack"
```

## PR-3 MQTT/Data-Parser 业务闭环
```bash
git add \
  aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/config/HttpClientConfig.java \
  aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/controller/MqttIngressController.java \
  aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/dto \
  aiot-mqtt-adapter/src/main/java/com/aiot/mqtt/service \
  aiot-mqtt-adapter/src/main/resources/application.yml \
  aiot-data-parser/pom.xml \
  aiot-data-parser/src/main/java/com/aiot/data/DataParserApplication.java \
  aiot-data-parser/src/main/java/com/aiot/data/controller/InternalParserController.java \
  aiot-data-parser/src/main/java/com/aiot/data/dto \
  aiot-data-parser/src/main/java/com/aiot/data/service \
  aiot-data-parser/src/main/resources/application.yml \
  scripts/test_mqtt_data_parser_loop.sh

git commit -m "feat(mqtt-parser): implement ingest parse and stream publish loop"
```

## PR-4 演练与留痕闭环
```bash
git add \
  scripts/inject_fault.sh \
  scripts/run_release_rollback_drill.sh \
  scripts/rollback_single_service.sh \
  docs/runbooks/README.md \
  docs/runbooks/release-drill-template.md \
  docs/runbooks/single-service-rollback.md

git commit -m "feat(sre): add drill orchestration and structured rollback audit"
```

## 任务文档（可选单独提交）
```bash
git add \
  docs/tasks/L4_EXECUTION_PR_PACKAGES.md \
  docs/tasks/L4_PR_STAGING_COMMANDS.md

git commit -m "docs(execution): add L4 rollout PR package and staging guide"
```
