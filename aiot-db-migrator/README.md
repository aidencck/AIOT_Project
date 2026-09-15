# aiot-db-migrator

`aiot-db-migrator` 是 AIOT-java 的数据库迁移入口模块，负责把 `aiot_cloud` 与 `aiot_home` 的 DDL 纳入 Flyway 流程。

## 目录结构

```text
src/main/resources/db/migration/aiot_cloud
src/main/resources/db/migration/aiot_home
```

## 本地执行

```bash
export FLYWAY_AIOT_CLOUD_URL="jdbc:mysql://127.0.0.1:3306/aiot_cloud?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export FLYWAY_AIOT_CLOUD_USER="root"
export FLYWAY_AIOT_CLOUD_PASSWORD="***"

export FLYWAY_AIOT_HOME_URL="jdbc:mysql://127.0.0.1:3306/aiot_home?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export FLYWAY_AIOT_HOME_USER="root"
export FLYWAY_AIOT_HOME_PASSWORD="***"

mvn -pl aiot-db-migrator -P aiot-cloud flyway:validate flyway:migrate
mvn -pl aiot-db-migrator -P aiot-home flyway:validate flyway:migrate
```

## 基线策略

- `V1_0_0__baseline.sql` 代表仓库当前的结构基线。
- 存量环境首次接入 Flyway 时应先完成备份，再执行基线与校验。
- 存量库首次接入 Flyway 时执行 baseline，将当前 schema 标记为 `1`：

```bash
mvn -pl aiot-db-migrator -P aiot-cloud flyway:baseline -Dflyway.baselineVersion=1
mvn -pl aiot-db-migrator -P aiot-home flyway:baseline -Dflyway.baselineVersion=1
```
