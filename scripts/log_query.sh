#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

LOKI_BASE="${AIOT_LOKI_BASE:-http://localhost:3100}"

usage() {
  cat <<'EOF'
Usage: log_query.sh [options]

优先走 Loki LogQL 查询；Loki 未就绪时自动降级为 docker compose logs + grep。
至少提供 --service / --trace-id / --level / --contains 之一。

  --service <name>     服务名（Loki 标签 service，如 aiot-device-service）
  --trace-id <id>      按 traceId 精确匹配（日志中形如 [<traceId>]）
  --level <LEVEL>      日志级别（ERROR/WARN/INFO/DEBUG）
  --contains <text>    任意子串匹配
  --since <dur>        时间窗，如 15m/1h/24h（默认 15m）
  --limit <n>          最大返回行数（默认 200）
  --fallback           强制走 docker logs 兜底
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少依赖命令: $1" >&2; exit 1; }
}

parse_duration() {
  local d="$1" num="${d%[smhd]}" unit="${d: -1}"
  case "${unit}" in
    s) echo "${num}" ;;
    m) echo $((num * 60)) ;;
    h) echo $((num * 3600)) ;;
    d) echo $((num * 86400)) ;;
    *) echo "无法解析时长: ${d}" >&2; exit 1 ;;
  esac
}

SERVICE=""
TRACE_ID=""
LEVEL=""
CONTAINS=""
SINCE="15m"
LIMIT=200
FORCE_FALLBACK=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --service) SERVICE="$2"; shift 2 ;;
    --trace-id) TRACE_ID="$2"; shift 2 ;;
    --level) LEVEL="$2"; shift 2 ;;
    --contains) CONTAINS="$2"; shift 2 ;;
    --since) SINCE="$2"; shift 2 ;;
    --limit) LIMIT="$2"; shift 2 ;;
    --fallback) FORCE_FALLBACK=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "${SERVICE}" && -z "${TRACE_ID}" && -z "${LEVEL}" && -z "${CONTAINS}" ]]; then
  echo "至少提供一个过滤条件" >&2
  usage
  exit 1
fi

loki_ready() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "${LOKI_BASE}/ready" || true)"
  [[ "${code}" == "200" ]]
}

build_logql() {
  # 流选择器
  local selector="job=\"docker\""
  if [[ -n "${SERVICE}" ]]; then
    selector="${selector},service=\"${SERVICE}\""
  fi
  local query="{${selector}}"
  # 行过滤器（按序叠加）
  if [[ -n "${LEVEL}" ]]; then
    query="${query} |= \"${LEVEL}\""
  fi
  if [[ -n "${TRACE_ID}" ]]; then
    query="${query} |= \"${TRACE_ID}\""
  fi
  if [[ -n "${CONTAINS}" ]]; then
    query="${query} |= \"${CONTAINS}\""
  fi
  echo "${query}"
}

query_loki() {
  local query="$1" start="$2" end="$3" limit="$4"
  curl -sG --max-time 15 "${LOKI_BASE}/loki/api/v1/query_range" \
    --data-urlencode "query=${query}" \
    --data-urlencode "start=${start}" \
    --data-urlencode "end=${end}" \
    --data-urlencode "limit=${limit}" \
    --data-urlencode "direction=backward" \
    | jq -r '.data.result[]?.values[]? | "\(.[0]) \(.[1])"' 2>/dev/null || true
}

fallback_logs() {
  echo "[fallback] 使用 docker compose logs + grep 检索"
  local svc="${SERVICE}"
  [[ -z "${svc}" ]] && svc=""   # 未指定服务则检索全部业务服务
  local pattern=""
  [[ -n "${TRACE_ID}" ]] && pattern="${pattern}${pattern:+|}${TRACE_ID}"
  [[ -n "${CONTAINS}" ]] && pattern="${pattern}${pattern:+|}${CONTAINS}"
  [[ -n "${LEVEL}" ]] && pattern="${pattern}${pattern:+|}${LEVEL}"
  if [[ -n "${svc}" ]]; then
    docker compose logs --no-color --tail 500 "${svc}" 2>/dev/null | grep -E "${pattern}" | tail -n "${LIMIT}" || true
  else
    docker compose logs --no-color --tail 2000 2>/dev/null | grep -E "${pattern}" | tail -n "${LIMIT}" || true
  fi
}

require_cmd curl

if [[ "${FORCE_FALLBACK}" -eq 1 ]] || ! loki_ready; then
  fallback_logs
  exit 0
fi

require_cmd jq

SINCE_SECONDS="$(parse_duration "${SINCE}")"
END="$(date +%s)"
START=$((END - SINCE_SECONDS))
QUERY="$(build_logql)"

echo "LogQL: ${QUERY}"
query_loki "${QUERY}" "${START}" "${END}" "${LIMIT}"
