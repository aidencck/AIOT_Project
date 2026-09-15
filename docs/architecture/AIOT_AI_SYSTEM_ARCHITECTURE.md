---
status: current
owner: 架构组
fact_source: 代码（aiot-rule-engine / aiot-common / aiot-device-service / edge / training / aiot-admin-web）
updated_at: 2026-09-14
---

# AIoT AI 体系架构（全维度方案）

> 定位：从「现状盘点」升级为「全维度架构视图」。每维度按 **现状 / 缺口 / 目标** 三栏收敛，量化目标未实测处标注 `[待定标]`，禁止以推测数字充当结论。
> 事实源以代码为准，与 [架构链路地图](../../.trae/knowledge/architecture-map.md) 对齐，漂移以代码为准。

## 0. 阅读导航（维度矩阵）

| 维度 | 回答的问题 | 章节 |
|---|---|---|
| 战略 | 为什么做、做到什么程度算赢 | §1 |
| 组件 | 有哪些模块、边界在哪 | §2 |
| 数据流 | 数据怎么走（正常路径） | §3 |
| 数据模型 | 实体怎么关联 | §4 |
| 一致性 | 重复/重放怎么防 | §5 |
| 可靠性 | 坏了怎么降级 | §6 |
| 性能 | 量化到多少 | §7 |
| 可观测 | 怎么证明做对了 | §8 |
| 安全 | 边界怎么守 | §9 |
| 决策 | 为什么这样选 | §10 |
| 部署 | 跑在哪、边界在哪 | §11 |
| 演进 | 下一步怎么走 | §12 |

## 1. 定位与北极星指标（Why）

**价值主张**：把设备异常从「人工排查」转为「自动诊断 → 处置 → 沉淀」，形成可复用案例库与规则库。

**北极星指标**（候选，按业务闭环收敛）：

| 指标 | 定义 | 现状 | 状态 |
|---|---|---|---|
| 有效处置率 | `SOLVED` 反馈占比 | 无统计 | `[待定标]` |
| 诊断→处置闭环时长 | 诊断产生到规则/工单生效 | 无统计 | `[待定标]` |
| 规则草稿采纳率 | approve / (approve+reject) | 无统计 | `[待定标]` |

**当前阶段定位**：规则兜底 + 人工反馈为主，LLM 增强为辅，训练闭环未自动化。

## 2. 分层与组件地图（What）

| 层 | 模块 | 技术栈 | 职责 |
|---|---|---|---|
| 边缘诊断层 | `edge/aiot-edge-agent` | Python 3（`requirements.txt` 仅 `jsonschema>=4.18.0`，未固定小版本） | 端侧诊断、schema 校验、上报 |
| 契约层 | `edge/contracts` | JSON Schema | 端云共享输出契约 + Prompt + 模型发布清单 |
| 云端诊断层 | `aiot-rule-engine` | Spring Boot（8084） | 诊断编排、反馈、案例库、规则草稿、持久化 |
| AI 公共能力层 | `aiot-common/ai` | Java 库 | LLM 客户端、Schema 校验、Prompt、审计、DTO |
| 上下文供给层 | `aiot-device-service` | Spring Boot（8081） | 运行时上下文（影子/事件历史/在线状态） |
| 管理后台 | `aiot-admin-web` | Vue3 | AI 治理、规则草稿审批 |
| 训练/评估层 | `training` | Python | 数据集构建、QLoRA 微调、链路质量评估 |

诊断场景与兜底基线（`sceneType` 枚举）：

| 场景 | 兜底根因 | 兜底置信度 | 兜底风险 | ruleDraftable |
|---|---|---|---|---|
| `OFFLINE_FLAP` | `NETWORK_INSTABILITY` | 0.66 | HIGH | true |
| `PROVISION_FAILURE` | `PROVISIONING_CONFLICT` | 0.58 | MEDIUM | false |
| `SHADOW_DIFF` | `STATE_SYNC_DRIFT` | 0.61 | MEDIUM | true |

## 3. 数据链路（正常路径）

### 3.1 云端诊断（fallback-first + 异步 refine）
```
normalize → resolveContext → loadCases(3) → buildFallback(即时, source=fallback)
    → persist → publishAudit → submitLlmRefine(异步) → submitDraftGeneration(异步)
```
异步 refine 仅在 `llm.isEnabled()` 且 `isSceneContextMatched()` 时执行，输出经 `AiSchemaValidator` 归一化，null（占位/枚举非法）则保留兜底并记 `schema_rejected`。

### 3.2 边缘 → 云端上报
`DiagnosisReporter` POST `/api/v1/ai/diagnosis/report` → 云端 `ingestEdgeReport()` 回填上下文持久化（`source=edge-agent`）→ 触发规则草稿生成。

### 3.3 反馈 → 案例库 → 回喂
`SOLVED` 才物化案例：`SOLVED+ACCEPTED=1.0` / `SOLVED=0.8` / 其他 `0.5`；失败入队 `AiCaseMaterializationTask` 重试；案例经 `findBySceneType` 回喂下次推理。

### 3.4 规则草稿沉淀
`ruleDraftable=true` → 生成 `DRAFT` 规则（`OFFLINE_FLAP→DEVICE_OFFLINE` / `SHADOW_DIFF→SHADOW_DESIRED_UPDATED` / `PROVISION_FAILURE→DEVICE_PROVISION_FAILED`，均 `ALARM_CREATE`）；`deviceId+eventType` 已有 DRAFT 则跳过。

## 4. 数据模型与实体关系（ER）

逻辑关系（字段以 `AiDiagnosisService` 实际使用为准）：

| 关系 | 基数 | 关联键 | 现状 |
|---|---|---|---|
| 诊断 → 反馈 | 1 : 0..1 | `diagnosisId` | 反馈可重复提交，无幂等 |
| 诊断 → 案例 | 1 : 0..1 | `diagnosisId`（物化） | `SOLVED` 才物化 |
| 物化任务 → 案例 | 1 : 0..1 | 任务重试 | 有 `retryCount`，无幂等键 |
| 诊断 → 规则草稿 | 1 : 0..1 | `deviceId+eventType` 去重 | 已去重 ✅ |

**缺口**：诊断记录无唯一约束，`eventId` 仅存储未作键（见 §5）。

## 5. 一致性 / 幂等设计

| 链路 | 现状 | 判定 |
|---|---|---|
| 规则执行幂等 | `idempotency-ttl-seconds=1800`，覆盖 `max-delivery-count(10)×min-idle-ms(60s)=600s` 重投窗口 | ✅ 已闭环 |
| AI 诊断落库幂等 | `traceId=MDC/随机UUID`，`eventId` 存而未用，重复上报/边缘+云端双写产生多记录 | ❌ 缺口 |
| 边缘上报幂等 | `diagnosisId` 取请求值或随机 UUID，无 `eventId` 查重 | ❌ 缺口 |
| 反馈幂等 | 重复反馈可重复触发案例物化 | ❌ 缺口 |
| 案例物化幂等 | 任务重试存在，但无业务幂等键 | ⚠️ 部分 |

**缺口根因**：`resolveTraceId()` 未从 `eventId` 派生，无法用 `deviceId+eventId+sceneType` 做唯一约束。

## 6. 故障模式与降级矩阵（FMEA）

| 故障 | 影响 | 现有降级 | 缺口 |
|---|---|---|---|
| Ollama OOM | refine 失败 | `concurrency=1` 防 OOM | 无 refine 失败计数/告警 |
| LLM 超时（20s） | 回填失败 | 保留兜底 | 无超时率 SLI |
| KnowledgeSearch 超时 | refine 失败 | try-catch 保留兜底 | 无降级审计 |
| device-service 上下文不可用 | 诊断上下文缺失 | 空壳上下文 | 无熔断/降级开关 |
| 异步回填线程池满/宕机 | refine 结果丢失 | 兜底已持久化 | 无回填丢失率统计 |
| 边缘 reporter 上报失败 | 静默丢诊断 | 不抛异常 | 无重试/本地缓冲 |
| Redis 不可用 | 落库失败 | 无 | 无降级写 MySQL |
| MySQL outbox 积压 | 双写不一致 | `cutover-guard` + `replay(30s/32)` | 无积压告警 |

## 7. 量化 NFR / SLO 骨架

| 维度 | 现状（config 实测） | 目标 | 状态 |
|---|---|---|---|
| LLM 超时 | `timeout-ms=20000` | p99 < `[待定标]` | 有值无目标 |
| 兜底时延 | 同步即时返回 | p99 < `[待定标]` | 未压测 |
| LLM 并发 | `concurrency=1` | 支撑 QPS `[待定标]` | 有值无容量模型 |
| 回填队列 | `queue-capacity=64` 满即丢弃 | 丢弃率 < `[待定标]` | 无丢弃率统计 |
| 可用性 | LLM 关闭时兜底可用 | 兜底 99.9% `[待定标]` | 未验证 |
| 推理参数 | `temperature=0.2` / `num-predict=512` / `num-ctx=2048` | — | 已配置 |

## 8. 可观察性 / SLI 闭环

**已有机制**：

| 机制 | 位置 | 频率 |
|---|---|---|
| 运维工单 SLA 检查 | `SlaSchedulerService` → `checkAndMarkSlaBreached` | 60s |
| 诊断审计发布 | `AiAuditPublisher` | 每次诊断 |
| 持久化一致性/迁移门禁报告 | `training/data/reports/persistence/*` | 离线 |

**缺失的 AI 业务 SLI**：诊断准确率、refine 触发率、schema 拒绝率、案例命中率、规则采纳率、丢诊断率。当前有「门禁」无「指标」，未形成闭环。

## 9. 安全 / 合规边界

**已有**：

| 机制 | 位置 | 说明 |
|---|---|---|
| Webhook SSRF 防护 | `aiot.rule.action.webhook.allowed-hosts` | 目标 host 白名单，命中跳过私网/环回拦截 |
| Webhook 超时 | `timeout-ms=2000` | 防挂起 |

**缺口**：设备上下文含 `globalDeviceId / authIdentity / 房间位置`，跨服务经 `InternalAiContextController` 传输的鉴权与脱敏未定义；AI 输出的 `ALARM_CREATE` 无告警风暴护栏；出海数据驻留/跨境合规未覆盖。

## 10. 关键设计决策（ADR，含备选方案 + 拒绝理由）

**ADR-1 fallback-first + 异步 refine**
- 决策：请求线程即时返回兜底，LLM 转后台回填。
- 备选：同步 LLM（拒绝：20s 超时钳制吞吐）；纯 LLM 无兜底（拒绝：模型不稳定时无可用结果）。
- 后果：结果可能短暂非最优，需承受异步回填丢失。

**ADR-2 端云共享 schema + 双端校验**
- 决策：`diagnosis.schema.json` 与 DTO 对齐，端 jsonschema / 云 `AiSchemaValidator`。
- 备选：仅云端校验（拒绝：边缘离线无法本地校验）；仅端侧校验（拒绝：云端 ingest 缺防线）。

**ADR-3 小模型输出宽松归一化**
- 决策：反序列化前收敛强类型，占位命中拒绝为 null。
- 备选：严格 schema 拒绝（拒绝：1.5b 输出多样性高，严格拒绝率过高）；人工兜底（拒绝：不可规模化）。

**ADR-4 上下文完整度门禁（≥4/8）**
- 决策：低完整度拒绝调 LLM，保持兜底。
- 备选：无条件调 LLM（拒绝：低质量上下文污染推理）。
- 后果：`status=0` 高占比下 refine 触发率受限（见 §12）。

**ADR-5 Redis 优先 + MySQL 可选**
- 决策：默认 Redis，MySQL 经 Outbox + cutover-guard 切流。
- 备选：纯 MySQL（拒绝：高频事件写放大）；纯 Redis（拒绝：无法满足审计/持久化）。

**ADR-6 LLM 并发度=1**
- 决策：`concurrency=1`、队列 64、满即丢弃。
- 备选：高并发（拒绝：CPU 推理 llama-server OOM）。

## 11. 部署拓扑 / 网络边界

| 组件 | 端口 | 说明 |
|---|---|---|
| `aiot-rule-engine` | 8084 | AI 诊断编排 + 规则引擎 |
| `aiot-device-service` | 8081 | 设备上下文供给（含内部 AI context 接口） |
| MQTT（device-service 内） | 1883 | 设备接入 |
| Ollama | Docker 注入 | `qwen2.5:1.5b`，CPU 推理 |
| Redis / MySQL | — | 诊断/案例存储（Redis 默认，MySQL 可选） |

**网络边界**：rule-engine → device-service 为点对点 HTTP（`AiContextProvider` → `InternalAiContextController`），无网关/消息总线解耦，是未来拆仓/独立微服务的耦合点。

## 12. 演进路线（量化锚点 + 验收门禁）

| 优先级 | 动作 | 验收门禁 |
|---|---|---|
| P0 | AI 诊断落库幂等（`deviceId+eventId+sceneType` 唯一约束） | 重复上报不产生多记录，`eventId` 升级为唯一键 |
| P0 | 故障降级矩阵落地（LLM 失败计数、熔断开关） | 每种故障有降级 + 告警 |
| P1 | AI 业务 SLI 闭环（准确率/refine 触发率/schema 拒绝率/丢诊断率） | 指标可观测、可告警 |
| P1 | 量化 NFR 定标（p99 时延、QPS、丢诊断率） | 压测报告产出真实数字，替换 `[待定标]` |
| P2 | 上下文覆盖修复（缓解 `status=0` 93.6% 偏差） | 完整度 ≥4/8 占比提升，refine 触发率上升 |
| P2 | 安全边界（PII 脱敏、告警风暴护栏、出海合规） | 跨服务上下文脱敏、动作护栏生效 |

## 13. 事实来源

- `aiot-rule-engine/src/main/resources/application.yml`（LLM / persistence / 幂等 / webhook / sla 配置）
- `aiot-rule-engine/src/main/java/com/aiot/rule/service/AiDiagnosisService.java`
- `aiot-rule-engine/src/main/java/com/aiot/rule/service/SlaSchedulerService.java`
- `aiot-common/src/main/java/com/aiot/common/ai/schema/AiSchemaValidator.java`
- `aiot-common/src/main/java/com/aiot/common/ai/prompt/PromptRegistry.java`
- `aiot-common/src/main/java/com/aiot/common/ai/client/OpenAiCompatibleLlmClient.java`
- `aiot-device-service/src/main/java/com/aiot/device/service/impl/AiContextFacadeImpl.java`
- `edge/aiot-edge-agent/aiot_edge_agent/{agent,llm_client,reporter,schema_validator}.py`
- `edge/contracts/{diagnosis.schema.json,model_manifest.schema.json}`
- `training/`
