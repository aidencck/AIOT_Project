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

- Gateway：`8080`
- Device Service：`8081`
- Auth Service：`8082`

说明：`aiot-home-service` 默认端口为 `8083`，与 EMQX WebSocket 端口冲突。若在同机本地运行家庭服务，请调整其端口或停用 EMQX WebSocket 映射。

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
- 端口调整后同步更新 README 与 API 文档入口
