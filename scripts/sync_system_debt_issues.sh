#!/usr/bin/env bash
set -euo pipefail

REPO="aidencck/AIOT_Project"
MILESTONE_TITLE="V1.2 系统优化技术债务治理"
MILESTONE_DESC="围绕服务通信契约、内部鉴权、一致性补偿、容错与可观测性的技术债务治理。"
LABEL_TECH_DEBT="tech-debt"
LABEL_ARCH="architecture"

ensure_milestone() {
  local number
  number="$(gh api "repos/${REPO}/milestones" --jq ".[] | select(.title == \"${MILESTONE_TITLE}\") | .number" | head -n 1 || true)"
  if [[ -z "${number}" ]]; then
    gh api "repos/${REPO}/milestones" \
      -f title="${MILESTONE_TITLE}" \
      -f description="${MILESTONE_DESC}" >/dev/null
  fi
}

ensure_label() {
  local name="$1"
  local color="$2"
  local description="$3"
  gh label create "${name}" -R "${REPO}" --color "${color}" --description "${description}" >/dev/null 2>&1 || true
}

upsert_issue() {
  local title="$1"
  local body_file="$2"

  local existing
  existing="$(gh issue list -R "${REPO}" --state all --limit 200 --json number,title \
    --jq ".[] | select(.title == \"${title}\") | .number" | head -n 1 || true)"

  if [[ -n "${existing}" ]]; then
    gh issue edit "${existing}" -R "${REPO}" \
      --title "${title}" \
      --body-file "${body_file}" \
      --milestone "${MILESTONE_TITLE}" \
      --add-label "${LABEL_TECH_DEBT}" \
      --add-label "${LABEL_ARCH}" >/dev/null
    echo "updated #${existing} ${title}"
  else
    gh issue create -R "${REPO}" \
      --title "${title}" \
      --body-file "${body_file}" \
      --milestone "${MILESTONE_TITLE}" \
      --label "${LABEL_TECH_DEBT}" \
      --label "${LABEL_ARCH}" >/dev/null
    echo "created ${title}"
  fi
}

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

ensure_label "${LABEL_TECH_DEBT}" "B60205" "技术债务治理"
ensure_label "${LABEL_ARCH}" "1D76DB" "架构优化"
ensure_milestone

cat > "${tmpdir}/01.md" <<'EOF'
## 背景
当前服务间调用存在历史契约混用风险（`Result<T>` 与裸类型混用），需要统一。

## 目标
- 所有跨服务 HTTP 返回统一按 `Result<T>` 协议解析并校验 `code/data`。

## 要求
- 排查 `home/auth/device` 间所有 `WebClient/Feign` 调用。
- 禁止裸 `Boolean/String/Object` 直接作为跨服务响应模型。
- 增加契约回归脚本并接入 CI。

## 验收标准
- 契约脚本通过率 100%。
- 不再出现反序列化类型错配问题。

## 交付物
- 代码改造 + 契约清单 + CI 日志截图。
EOF
upsert_issue "[TD-SYS-01] 跨服务响应契约统一治理（Result<T>）" "${tmpdir}/01.md"

cat > "${tmpdir}/02.md" <<'EOF'
## 背景
内部接口需要与用户接口严格隔离，避免 token 误用与越权调用。

## 目标
- `/api/v1/internal/**` 全量启用服务身份鉴权。

## 要求
- 强制 `X-Internal-Token`（后续可演进 mTLS）。
- 网关层与路由策略不暴露 internal 路径给外部。
- 增加错误调用拦截测试（无 token/错误 token）。

## 验收标准
- 无内部令牌访问 internal 接口返回 `401`。
- 正确服务令牌访问通过。

## 交付物
- 鉴权实现 + 配置文档 + 集成测试结果。
EOF
upsert_issue "[TD-SYS-02] 内部接口服务鉴权与边界收口" "${tmpdir}/02.md"

cat > "${tmpdir}/03.md" <<'EOF'
## 背景
`home/room` 删除补偿以同步调用为主，缺乏审计、重试与巡检闭环。

## 目标
- 建立可追踪、可重试的一致性补偿机制。

## 要求
- 补偿日志标准化（traceId/resourceId/result/latency）。
- 增加巡检任务：悬挂 `homeId/roomId` 检测与告警。
- 失败补偿支持重试策略与手动回放。

## 验收标准
- 灰度环境连续 3 天无新增悬挂引用。
- 补偿失败可定位、可重试、可追溯。

## 交付物
- 补偿机制改造代码 + 巡检任务 + 运维操作说明。
EOF
upsert_issue "[TD-SYS-03] 删除补偿链路可审计化与巡检闭环" "${tmpdir}/03.md"

cat > "${tmpdir}/04.md" <<'EOF'
## 背景
同步调用故障时可能引发阻塞与级联失败，需要统一容错策略。

## 目标
- 关键同步调用具备统一 timeout/retry/circuit-breaker 策略。

## 要求
- 统一 WebClient 配置（连接/响应超时）。
- 有限重试（仅幂等场景），禁止无限重试。
- 引入熔断与降级策略（关键链路）。

## 验收标准
- 下游故障注入时系统可控退化，不出现级联雪崩。
- 错误率与延迟指标在可接受范围内。

## 交付物
- 统一容错组件 + 压测/故障注入报告。
EOF
upsert_issue "[TD-SYS-04] 服务通信容错策略统一（Timeout/Retry/熔断）" "${tmpdir}/04.md"

cat > "${tmpdir}/05.md" <<'EOF'
## 背景
通信链路缺少可观测闭环，故障排查依赖人工日志检索。

## 目标
- 建立通信链路可观测体系（日志、指标、告警）。

## 要求
- 统一出入站 trace 透传与日志字段标准。
- 新增调用指标：请求数、错误数、耗时分位。
- 建立告警策略：签名失败激增、补偿失败、内部接口401异常增长。

## 验收标准
- 关键链路均可在监控面板查看趋势与异常。
- MTTR 显著下降（定义与对比口径明确）。

## 交付物
- 指标埋点 + 告警规则 + Dashboard 文档。
EOF
upsert_issue "[TD-SYS-05] 通信链路可观测性与告警体系" "${tmpdir}/05.md"

cat > "${tmpdir}/06.md" <<'EOF'
## 背景
当前 CI 已有脚本守卫，但尚需真实联调环境执行，防止“仅语法通过”。

## 目标
- 将服务通信关键场景纳入 CI 集成联调。

## 要求
- CI 中启动最小依赖（MySQL/Redis/Nacos/核心服务）并执行集成脚本。
- 固化测试种子数据（HOME_ID/DEVICE_ID）。
- 输出失败工件（日志/响应体）便于排障。

## 验收标准
- PR 阶段可自动验证配网并发、webhook 重放、internal 鉴权场景。
- 失败可复现、可定位。

## 交付物
- CI job + seed 数据脚本 + 失败工件归档。
EOF
upsert_issue "[TD-SYS-06] CI 联调门禁（通信链路真实执行）" "${tmpdir}/06.md"

echo "sync done"
