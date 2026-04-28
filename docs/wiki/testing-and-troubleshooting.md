# 测试与排障

## 构建与验证

1. 全量构建验证：

```bash
mvn clean verify
```

2. 跳过测试快速构建：

```bash
mvn clean install -DskipTests
```

3. CI 行为（GitHub Actions）：

- 变更检测：按目录识别受影响服务
- 增量构建：仅对变更服务执行 Maven 构建与镜像构建
- 增量部署：服务器仅 `pull/up` 变更服务

## API 自检入口

- Device Swagger：`http://localhost:8081/swagger-ui.html`
- Auth Swagger：`http://localhost:8082/swagger-ui.html`
- Home Swagger：`http://localhost:8083/swagger-ui.html`（需本地启动家庭服务）

## 常见问题

1. 服务启动失败：Nacos/MySQL/Redis 未就绪  
处理：先执行 `docker compose ps` 与 `docker compose logs <service>`，确认依赖健康检查通过。

2. 家庭服务端口冲突（8083）  
处理：`8083` 同时被 EMQX WebSocket 占用，需修改家庭服务端口或调整 EMQX 端口映射。

3. 设备操作报权限错误  
处理：检查请求头 `Authorization` 是否有效，且用户是否具备目标 `homeId` 的最小角色权限。

4. EMQX Webhook 回调 401  
处理：检查 `x-emqx-signature` 与服务端签名密钥是否一致，核对请求时间戳与签名字段。

5. Compose 拉取镜像失败  
处理：检查 GHCR 访问权限与网络连通性，必要时登录容器仓库后重试。

## 观测建议

- 使用 Actuator 健康检查确认服务状态：`/actuator/health`
- 容器编排健康探针建议使用：`/actuator/health/readiness`
- 存活探针建议使用：`/actuator/health/liveness`
- 指标抓取入口：`/actuator/prometheus`
- 出现跨服务调用异常时，优先检查 Nacos 注册状态与服务名配置
- 每次发布后优先回归：设备创建、配网 token、设备影子读写、家庭删除补偿

## Prometheus 抓取样例

```yaml
scrape_configs:
  - job_name: 'aiot-services'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - aiot-gateway:8080
          - aiot-device-service:8081
          - aiot-auth-service:8082
          - aiot-home-service:8083
```

## 事件可靠性治理（Redis Stream）

- 已启用 pending 回收任务：`aiot.events.pending-reclaim.enabled=true`
- 回收任务默认每 `30s` 扫描一次当前 consumer 的 pending 队列：`fixed-delay-ms=30000`
- 单次最大回收批次：`max-batch-size=64`
- 消费异常消息会写入 DLQ：`aiot:stream:device-event:dlq`，并对源消息执行 ACK 防止长期积压
- 关键指标（Prometheus）：
  - `aiot_stream_pending_recovered_total`
  - `aiot_stream_pending_recovery_failed_total`
