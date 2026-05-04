# 环境与配置

## 运行前置

- JDK：17+
- Maven：3.8+
- Docker 与 Docker Compose：用于本地依赖与容器部署

## 基础服务端口（Compose）

- MySQL：`3306`
- Redis：`6379`
- Nacos：`8848`
- EMQX MQTT：`1883`
- EMQX Dashboard：`18083`
- EMQX WebSocket：`8083`

## 微服务端口（Compose）

| 服务 | 服务内端口 | Compose 对宿主机暴露 |
| --- | --- | --- |
| Gateway | `8080` | 是（`8080:8080`） |
| Device Service | `8081` | 否（仅容器内网络） |
| Auth Service | `8082` | 否（仅容器内网络） |
| Home Service | `8083` | 否（仅容器内网络） |
| Rule Engine | `8084` | 否（仅容器内网络） |
| MQTT Adapter | `8085` | 否（仅容器内网络） |
| Data Parser | `8086` | 否（仅容器内网络） |
| Shadow Service | `8087`（Compose 环境变量覆盖） | 否（仅容器内网络） |

说明：
- `aiot-home-service` 默认 `8083`，与 EMQX WebSocket 在“宿主机端口”上潜在冲突；当前 Compose 未暴露 home 端口，因此不会直接冲突。
- `aiot-shadow-service` 代码默认端口为 `8083`，但 Compose 通过 `SERVER_PORT=8087` 覆盖。

## 常用启动方式

1. 一键部署（拉取 GHCR 镜像）：

```bash
./start_services.sh
```

2. 本地开发（中间件容器 + 本地服务）：

```bash
docker compose up -d
mvn clean install -DskipTests
```

3. 全量 Compose 联调（仅网关和中间件对宿主机开放）：

```bash
docker compose up -d
docker compose ps
```

## 关键环境变量（Compose）

- `IMAGE_TAG`：镜像标签，默认 `main`
- `NACOS_ADDR`：Nacos 地址，默认 `aiot-nacos:8848`
- `MYSQL_HOST`：MySQL 地址，默认 `aiot-mysql:3306`
- `MYSQL_PASSWORD`：MySQL root 密码，默认 `root`
- `REDIS_HOST`：Redis 地址，默认 `aiot-redis`
- `EMQX_HOST`：EMQX 地址，默认 `aiot-emqx`

## 配置维护建议

- 新增服务时同步更新 `docker-compose.yml` 与本页配置说明
- 所有敏感配置通过环境变量注入，不在代码中硬编码
- 端口调整后同步更新 README、Wiki 与压测/排障文档
