#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/inject_fault.sh -s <service> [选项]

必填参数:
  -s, --service          docker-compose 服务名（例如: aiot-device-service）

可选参数:
  -f, --compose-file     compose 文件路径（默认: docker-compose.yml）
  -m, --mode             注入模式（stop-container | kill-container，默认: stop-container）
      --trace-id         注入链路 ID（默认自动生成）
  -h, --help             显示帮助

说明:
  该脚本用于发布演练中的故障注入，默认会停止目标服务容器，
  以触发健康检查失败和回滚流程。
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
COMPOSE_FILE="docker-compose.yml"
MODE="stop-container"
TRACE_ID=""

json_escape() {
  local raw="${1:-}"
  raw="${raw//\\/\\\\}"
  raw="${raw//\"/\\\"}"
  raw="${raw//$'\n'/\\n}"
  printf '%s' "${raw}"
}

emit_event() {
  local level="$1"
  local stage="$2"
  local status="$3"
  local message="$4"
  local ts
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "{\"ts\":\"${ts}\",\"trace_id\":\"$(json_escape "${TRACE_ID}")\",\"service\":\"$(json_escape "${SERVICE}")\",\"action\":\"fault_injection\",\"mode\":\"$(json_escape "${MODE}")\",\"stage\":\"$(json_escape "${stage}")\",\"level\":\"$(json_escape "${level}")\",\"status\":\"$(json_escape "${status}")\",\"message\":\"$(json_escape "${message}")\"}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE="${2:-}"
      shift 2
      ;;
    -f|--compose-file)
      COMPOSE_FILE="${2:-}"
      shift 2
      ;;
    -m|--mode)
      MODE="${2:-}"
      shift 2
      ;;
    --trace-id)
      TRACE_ID="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知参数: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ -z "${SERVICE}" ]]; then
  echo "ERROR: --service 为必填参数"
  usage
  exit 1
fi

if [[ -z "${TRACE_ID}" ]]; then
  TRACE_ID="fi-${SERVICE}-$(date +%Y%m%d%H%M%S)"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: 未检测到 docker"
  exit 1
fi

COMPOSE_CMD=(docker compose)
if ! docker compose version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
  else
    echo "ERROR: 未检测到 docker compose / docker-compose"
    exit 1
  fi
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "ERROR: compose 文件不存在: ${COMPOSE_FILE}"
  exit 1
fi

services_list="$("${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" config --services 2>/dev/null || true)"
if ! grep -Fxq "${SERVICE}" <<< "${services_list}"; then
  echo "ERROR: compose 中不存在服务: ${SERVICE}"
  exit 1
fi

CID="$("${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" ps -q "${SERVICE}" || true)"
if [[ -z "${CID}" ]]; then
  emit_event "ERROR" "resolve_container" "failed" "服务未启动或未创建容器"
  echo "ERROR: 服务未启动或未创建容器: ${SERVICE}"
  exit 1
fi

emit_event "INFO" "resolve_container" "success" "已定位目标容器"

case "${MODE}" in
  stop-container)
    emit_event "WARN" "inject" "running" "执行 stop-container 注入"
    docker stop "${CID}" >/dev/null
    emit_event "WARN" "inject" "success" "目标容器已停止"
    ;;
  kill-container)
    emit_event "WARN" "inject" "running" "执行 kill-container 注入"
    docker kill "${CID}" >/dev/null
    emit_event "WARN" "inject" "success" "目标容器已强制终止"
    ;;
  *)
    emit_event "ERROR" "inject" "failed" "不支持的注入模式"
    echo "ERROR: 不支持的注入模式: ${MODE}"
    echo "支持模式: stop-container, kill-container"
    exit 1
    ;;
esac

echo "故障注入完成: service=${SERVICE}, mode=${MODE}, trace_id=${TRACE_ID}"
