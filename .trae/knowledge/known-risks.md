# 已知风险与教训（Known Risks & Lessons）

> 沉淀源：项目历史会话 + 数据分布实测 + 复盘。每条含「风险 / 证据 / 止损动作 / 状态」。

## 单一事实源声明（SSOT）
- 本文件是「AIOT 项目风险 / 教训 / 后续动作」的**唯一事实源**。
- Trae 记忆层的 `project_memory.md` 若与本文件冲突，**以本文件为准**。
- 改本文件后，`project_memory.md` 的 Lessons Learned / Next Actions 仅保留指针，不重复罗列细节。

## 安全类风险（2026-09 首轮安全审计，verdict=不通过）

> 来源：`aiot-security-audit` Skill 首轮实战。findings=12（High 5 / Medium 5 / Low 2），无 Critical。

### 高危（High，待处置 P0）
| # | 风险 | 证据 | 止损动作 | 状态 |
|---|---|---|---|---|
| S1 | 测试产物泄露真实凭据：deviceSecret + 用户 JWT + 明文密码入库 | artifacts/**/*.json | 清理产物 + artifacts 入 .gitignore + 轮换 deviceSecret | 已修复（2026-09，删除 artifacts + gitignore） |
| S2 | 硬编码可预测默认密钥（JWT/internal-token/EMQX webhook secret），可伪造 JWT 绕过鉴权 | scripts/lib/common.sh#L119-L121 | 删默认值，缺失即 fail-fast | 已修复（2026-09，secret::require_secrets fail-fast + CI runtime-smoke/services.json 可预测 runtime-smoke-* 清零，改 ephemeral 随机注入） |
| S3 | 管理后台存储型 XSS（动态字段 innerHTML 未转义） | aiot-device-service/.../static/admin/app.js | 统一 escapeHtml() | 已修复（2026-09，6 处 innerHTML 加 escapeHtml） |

### 中低危（Medium/Low，进 backlog）
| # | 风险 | 证据 | 止损动作 | 状态 |
|---|---|---|---|---|
| S4 | k8s values-dev 明文凭据 + JWT secret 长度不足（24<32 字节） | k8s/helm/values-dev.yaml | 改外部 Secret 注入 + 补足长度 | 已修复（2026-09，secrets 全空值 + 外部注入注释 + JWT 提示 ≥32 字节） |
| S5 | 弱默认密码（MySQL root123456 / EMQX emqxadmin123 / Grafana grafana123） | common.sh / docker-compose*.yml | 删默认回退，强制外部注入 | 已修复（2026-09，删 3 处弱默认 + MYSQL/EMQX 入 require_secrets + compose 弱回退改 :? / 空值 + CI db-migration/training 可预测 ci-mysql-password-123456 改 openssl rand 随机） |
| S6 | runtime.env 自引用占位，外部未注入时解析为空 → 空/弱密码直连 DB | compose/env/*/runtime.env | 空值部署门禁拦截 | 已修复（2026-09，secret::reset 保留安全密钥 + load_file 关闭 nounset + require_secrets 门禁） |
| S7 | 本地关闭 JWT 鉴权 / 弱 LLM key | docker-compose.admin-local.yml / docker-compose.local.yml | 仅限本地，严禁入 staging/prod | 已修复（2026-09，LOCAL-ONLY 注释 + service::guard 门禁拦截 JWT 绕过开关泄漏） |

## 高优先级（P0，阻断正确性/时效性）
| # | 风险 | 证据 | 止损动作 | 状态 |
|---|---|---|---|---|
| R1 | AI 推理上下文「空壳化」：缺 recentEvents，小模型收敛到默认安全值（置信度 0.8） | 1.5b 模型证据不足时输出退化 | 数据面门禁：上下文完整度 <4/8 时拒绝调 LLM，保持确定性兜底（`AiDiagnosisService.isSceneContextMatched`/`contextCompletenessScore`）；根因数据维度仍缺，见 R3 | 已缓解 |
| R2 | Redis Stream 积压（9.8w 条），诊断时效失效 | Stream backlog 实测 | 建立消费者积压监控 + 治理：`StreamBacklogMetrics`(PEL 口径修正) + `StreamBacklogMigrationGate`(XCLAIM 迁移) + `StreamRetentionTrimmer`(XTRIM MAXLEN) + Prometheus 告警 | 已落地（2026-09，编译通过） |
| R3 | 数据维度覆盖率 <0.1%（room_id/gateway_id/firmware），AI 缺业务维度 | 覆盖率实测 | L2 数据工程补全：Flyway `V1_1_6/1_1_7/1_1_8` + `V1_0_6` 回填 room/firmware/gateway 维度 | 已落地（2026-09，迁移已应用 + 确定性种子回填验证，room/firmware/gateway 覆盖率均 100% ≥ 0.9） |

## 中优先级（P1，稳定性/性能）
| # | 风险 | 证据 | 止损动作 | 状态 |
|---|---|---|---|---|
| R4 | Ollama 模型加载在受限内存触发 OOM | 模型加载 OOM 复现 | 关非核心容器 / 优化 num_ctx | 已缓解 |
| R5 | 高未激活偏差（status=0 占 93.6%） | 数据分布 | 激活链路治理 | 观察 |

## 教训（Lessons Learned）
1. 小模型在上下文证据不足时输出收敛到默认安全值 → 必须在数据面解决，而非调 prompt。
2. Redis Stream 积压会使 AI 诊断时效性失效 → 消费者积压必须进监控。
3. 数据库变更只走 `aiot-db-migrator` 的 Flyway 脚本，禁止手改。
4. AI 诊断结果必须过 `AiSchemaValidator` 归一化，否则枚举漂移污染案例库。

## 回写规则
- 每轮复盘/事故后更新本文件状态列。
- 新风险先判优先级再入表，禁止无证据的风险条目。
