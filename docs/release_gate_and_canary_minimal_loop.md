# CI/CD 发布门禁 + 灰度自动回滚（最小闭环）

## 目标

建立单服务发布的最小闭环：

1. 发布前门禁（Release Gate）阻断高风险输入  
2. 发布后灰度观察（Canary Window）及时发现异常  
3. 异常自动回滚（Auto Rollback）快速恢复可用性  

---

## 门禁规则（Release Gate）

脚本：`scripts/release_gate_check.sh`

当前门禁项：

1. `docker-compose` 文件可解析，且目标服务存在  
2. 目标服务必须配置 `healthcheck`（无健康探针禁止发布）  
3. 目标镜像 Tag 可拉取（拉取失败直接阻断）  
4. 可选基线健康检查：发布前当前实例可用（默认开启）  

示例：

```bash
./scripts/release_gate_check.sh \
  --service aiot-device-service \
  --tag sha-1a2b3c4
```

---

## 灰度与自动回滚闭环

脚本：`scripts/release_canary_with_rollback.sh`

执行阶段：

1. 先执行 `release_gate_check.sh`  
2. 发布目标 Tag，并做首次健康验证  
3. 进入灰度观察窗口，按周期巡检健康状态  
4. 若任意巡检失败，自动回滚到发布前镜像并再次验证  

示例：

```bash
./scripts/release_canary_with_rollback.sh \
  --service aiot-device-service \
  --tag sha-1a2b3c4 \
  --health-timeout 180 \
  --canary-seconds 120
```

---

## GitHub Actions 集成点

工作流：`.github/workflows/ci-cd.yml`

已在 `deploy` Job 内实现以下最小闭环策略：

1. 每个变更服务按顺序执行单服务灰度发布  
2. 部署 Tag 固定为 `sha-${GITHUB_SHA:0:7}`，避免漂移  
3. 首次健康检查失败或灰度期失败，立即自动回滚  
4. 回滚后再次健康验证，确保恢复完成  

---

## 已知边界（当前版本）

1. 灰度范围是“单实例单服务”级别，不含流量分流（非 Service Mesh）  
2. 回滚依赖“发布前镜像可解析出 tag”，镜像需遵循 `name:tag`  
3. 未引入业务指标门禁（如错误率、P99），当前以健康探针为主  
4. 多服务联动场景按顺序逐个发布，不是事务性整体回滚  
