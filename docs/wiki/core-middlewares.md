# AIOT-java 核心中间件梳理

## 1. 文档目的

梳理当前仓库已落地并在运行链路中承担关键职责的核心中间件，明确其角色、落地点与配置入口，作为部署、排障和新成员上手的统一参考。

## 2. 当前已落地核心中间件

| 中间件 | 主要角色 | 运行形态 | 默认端口 |
|---|---|---|---|
| MySQL 8.0 | 业务主数据存储（设备、认证、家庭等） | Docker Compose 独立服务 `mysql` | 3306 |
| Redis 7 | 缓存与事件流基础（含 Redis Stream） | Docker Compose 独立服务 `redis` | 6379 |
| Nacos 2.3.2 | 服务注册与发现 | Docker Compose 独立服务 `nacos` | 8848, 9848 |
| EMQX 5.3.0 | 设备 MQTT 接入与连接管理 | Docker Compose 独立服务 `emqx` | 1883, 8083, 18083 |

## 3. 中间件落地证据

### 3.1 Docker Compose 基础设施编排

- `docker-compose.yml` 已定义 `mysql`、`redis`、`nacos`、`emqx` 四个基础中间件服务。
- 上述服务均配置了 `healthcheck`，并被业务服务通过 `depends_on.condition: service_healthy` 依赖。
- 业务服务环境变量显式引用中间件地址，如 `MYSQL_HOST`、`REDIS_HOST`、`NACOS_ADDR`、`EMQX_HOST`。

### 3.2 应用配置对中间件的消费

- `aiot-device-service/src/main/resources/application.yml`：
  - `spring.datasource` 指向 MySQL
  - `spring.data.redis` 指向 Redis
  - `spring.cloud.nacos.discovery` 指向 Nacos
  - `emqx.api.base-url` 指向 EMQX 管理 API
- `aiot-gateway/src/main/resources/application.yml`：
  - `spring.cloud.nacos.discovery.server-addr` 使用 Nacos 做服务发现。

### 3.3 项目文档与技术栈声明

- `README.md` 技术栈明确包含 MySQL、Redis、EMQX、Nacos，并说明项目以 Docker Compose 单节点部署。

## 4. 中间件与业务能力映射

| 业务能力 | 关键中间件 | 说明 |
|---|---|---|
| 服务注册与路由转发 | Nacos | 网关与各微服务通过服务名发现，实现服务间调用。 |
| 设备接入与认证联动 | EMQX + Auth/Device 服务 | 设备通过 MQTT 接入，EMQX 与认证/设备服务形成接入控制与状态更新链路。 |
| 核心业务持久化 | MySQL | 承载设备、家庭、认证等结构化业务数据。 |
| 缓存与事件解耦 | Redis | 用于缓存与事件流（如设备事件流、DLQ）支撑异步处理。 |

## 5. 与“骨架模块”的关系说明

- `aiot-rule-engine`、`aiot-shadow-service`、`aiot-mqtt-adapter`、`aiot-data-parser` 当前处于不同成熟度阶段。
- 即使部分模块仍在演进，底层中间件底座已稳定围绕 MySQL/Redis/Nacos/EMQX 展开，属于当前可运行架构的事实基线。

## 6. 运维核对清单（建议）

- 检查中间件容器健康状态：`mysql`、`redis`、`nacos`、`emqx`。
- 检查业务服务关键环境变量是否完整注入。
- 检查业务服务 `readiness` 是否通过，确认对依赖中间件可达。
- 重点关注 Redis 事件流积压与 EMQX 连接/认证失败告警。
