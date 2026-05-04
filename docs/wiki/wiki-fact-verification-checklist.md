# Wiki 事实校验清单（current）

最后更新：`2026-04-30`

## 使用时机

- 端口、服务编排、依赖关系发生变化时
- 指标、告警、发布回滚流程发生变化时
- 每次版本发布前（作为文档门禁）

## L1 架构事实校验

- 核对模块清单：`pom.xml` 的 `<modules>`
- 核对运行拓扑：`docker-compose.yml` 的 `services`
- 核对服务职责：各服务 `controller/service/config` 目录是否有新增核心能力
- 核对状态标签：`current/in-progress/planned` 是否仍匹配代码现实

## L2 配置与端口校验

- 核对服务端口：各服务 `application.yml` 的 `server.port`
- 核对 Compose 覆盖：`SERVER_PORT` 环境变量是否覆盖默认端口
- 核对对外暴露：`docker-compose.yml` 的 `ports` 是否与 Wiki 一致
- 核对跨服务 URL：`*_SERVICE_URL` 配置是否与实际服务名和端口一致

## L3 监控与告警校验

- 核对抓取目标：`monitoring/prometheus/prometheus.yml`
- 核对告警表达式：`monitoring/prometheus/alerts/*.yml`
- 核对指标存在性：代码中的 `Counter/Gauge/Timer` 名称与规则引用一致
- 核对排障文档：`testing-and-troubleshooting.md` 示例是否过时

## L4 发布与回滚校验

- 核对 CI/CD：`.github/workflows/ci-cd.yml`
- 核对回滚流程：`.github/workflows/rollback.yml` 与 `docs/runbooks/*`
- 核对脚本：`scripts/deploy_single_service.sh`、`rollback_single_service.sh`、`verify_release_health.sh`

## 结果输出模板

- 事实一致项：列出页面和证据
- 偏差项：列出“页面 -> 失配事实 -> 证据文件 -> 修复动作”
- 风险项：列出“当前无法验证”的项与前置条件

## 最小执行策略

- P0：端口、拓扑、接口鉴权、告警规则引用错误（必须立即修）
- P1：能力描述偏差、缺少入口导航（本迭代修）
- P2：表达优化与结构重排（按版本规划）
