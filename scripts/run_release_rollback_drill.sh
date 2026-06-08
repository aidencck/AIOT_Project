#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/run_release_rollback_drill.sh -s <service> -t <release_tag> [选项]

必填参数:
  -s, --service            docker-compose 服务名（例如: aiot-device-service）
  -t, --tag                演练发布镜像标签（例如: drill-bad-20260504）

可选参数:
  -r, --rollback-tag       回滚目标标签（默认自动读取 previous_image）
  -f, --compose-file       compose 文件路径（默认: docker-compose.yml）
  -m, --fault-mode         故障注入模式（stop-container | kill-container，默认: stop-container）
      --health-timeout     健康检查超时秒数（默认: 180）
      --verify-timeout     注入后故障验证超时秒数（默认: 30）
      --trace-id           演练 Trace ID（默认自动生成）
      --operator           执行人（默认: $USER）
  -h, --help               显示帮助

说明:
  一键完成「发布 -> 故障注入 -> 故障验证 -> 回滚 -> 恢复验证」全链路演练，
  并输出结构化报告到 scripts/.release_state/drill。
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
RELEASE_TAG=""
ROLLBACK_TAG=""
COMPOSE_FILE="docker-compose.yml"
FAULT_MODE="stop-container"
HEALTH_TIMEOUT="180"
VERIFY_TIMEOUT="30"
TRACE_ID=""
OPERATOR="${USER:-unknown}"

START_EPOCH="$(date +%s)"
STATE_DIR="${ROOT_DIR}/scripts/.release_state"
DRILL_DIR="${STATE_DIR}/drill"
mkdir -p "${DRILL_DIR}"
REPORT_FILE=""
TIMELINE_FILE=""
DRILL_RESULT="failed"

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
  local line
  line="{\"ts\":\"${ts}\",\"trace_id\":\"$(json_escape "${TRACE_ID}")\",\"service\":\"$(json_escape "${SERVICE}")\",\"action\":\"release_rollback_drill\",\"stage\":\"$(json_escape "${stage}")\",\"level\":\"$(json_escape "${level}")\",\"status\":\"$(json_escape "${status}")\",\"operator\":\"$(json_escape "${OPERATOR}")\",\"release_tag\":\"$(json_escape "${RELEASE_TAG}")\",\"rollback_tag\":\"$(json_escape "${ROLLBACK_TAG}")\",\"message\":\"$(json_escape "${message}")\"}"
  echo "${line}"
  if [[ -n "${TIMELINE_FILE}" ]]; then
    echo "${line}" >> "${TIMELINE_FILE}"
  fi
}

finalize_report() {
  local rc="$?"
  local end_epoch
  end_epoch="$(date +%s)"
  local duration_s=$((end_epoch - START_EPOCH))
  if [[ "${rc}" -eq 0 ]]; then
    DRILL_RESULT="success"
  fi
  local finish_ts
  finish_ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  local summary
  summary="{\"ts\":\"${finish_ts}\",\"trace_id\":\"$(json_escape "${TRACE_ID}")\",\"service\":\"$(json_escape "${SERVICE}")\",\"operator\":\"$(json_escape "${OPERATOR}")\",\"release_tag\":\"$(json_escape "${RELEASE_TAG}")\",\"rollback_tag\":\"$(json_escape "${ROLLBACK_TAG}")\",\"fault_mode\":\"$(json_escape "${FAULT_MODE}")\",\"result\":\"${DRILL_RESULT}\",\"duration_s\":${duration_s},\"timeline_file\":\"$(json_escape "${TIMELINE_FILE}")\"}"
  if [[ -n "${REPORT_FILE}" ]]; then
    echo "${summary}" > "${REPORT_FILE}"
  fi
  if [[ "${rc}" -eq 0 ]]; then
    emit_event "INFO" "finalize" "success" "演练完成并通过"
  else
    emit_event "ERROR" "finalize" "failed" "演练失败，请根据时间线排查"
  fi
  echo "演练报告(JSON): ${REPORT_FILE}"
  echo "演练时间线(JSONL): ${TIMELINE_FILE}"
}

trap finalize_report EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE="${2:-}"
      shift 2
      ;;
    -t|--tag)
      RELEASE_TAG="${2:-}"
      shift 2
      ;;
    -r|--rollback-tag)
      ROLLBACK_TAG="${2:-}"
      shift 2
      ;;
    -f|--compose-file)
      COMPOSE_FILE="${2:-}"
      shift 2
      ;;
    -m|--fault-mode)
      FAULT_MODE="${2:-}"
      shift 2
      ;;
    --health-timeout)
      HEALTH_TIMEOUT="${2:-}"
      shift 2
      ;;
    --verify-timeout)
      VERIFY_TIMEOUT="${2:-}"
      shift 2
      ;;
    --trace-id)
      TRACE_ID="${2:-}"
      shift 2
      ;;
    --operator)
      OPERATOR="${2:-}"
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

if [[ -z "${SERVICE}" || -z "${RELEASE_TAG}" ]]; then
  echo "ERROR: --service 与 --tag 为必填参数"
  usage
  exit 1
fi

for v in HEALTH_TIMEOUT VERIFY_TIMEOUT; do
  if ! [[ "${!v}" =~ ^[0-9]+$ ]]; then
    echo "ERROR: ${v} 必须为正整数秒"
    exit 1
  fi
done

if [[ -z "${TRACE_ID}" ]]; then
  TRACE_ID="drill-${SERVICE}-$(date +%Y%m%d%H%M%S)"
fi
REPORT_FILE="${DRILL_DIR}/report-${TRACE_ID}.json"
TIMELINE_FILE="${DRILL_DIR}/timeline-${TRACE_ID}.jsonl"

emit_event "INFO" "init" "running" "开始执行发布回滚一键演练"

emit_event "INFO" "gate_check" "running" "执行发布门禁检查"
"${ROOT_DIR}/scripts/release_gate_check.sh" \
  --service "${SERVICE}" \
  --tag "${RELEASE_TAG}" \
  --compose-file "${COMPOSE_FILE}"
emit_event "INFO" "gate_check" "success" "发布门禁检查通过"

emit_event "INFO" "deploy" "running" "执行演练版本发布并验证健康"
"${ROOT_DIR}/scripts/deploy_single_service.sh" \
  --service "${SERVICE}" \
  --tag "${RELEASE_TAG}" \
  --compose-file "${COMPOSE_FILE}" \
  --health-timeout "${HEALTH_TIMEOUT}"
emit_event "INFO" "deploy" "success" "演练版本发布成功"

emit_event "WARN" "fault_injection" "running" "开始执行故障注入"
"${ROOT_DIR}/scripts/inject_fault.sh" \
  --service "${SERVICE}" \
  --compose-file "${COMPOSE_FILE}" \
  --mode "${FAULT_MODE}" \
  --trace-id "${TRACE_ID}"
emit_event "WARN" "fault_injection" "success" "故障注入完成"

emit_event "INFO" "failure_verify" "running" "验证故障注入是否生效（预期失败）"
if "${ROOT_DIR}/scripts/verify_release_health.sh" \
  --service "${SERVICE}" \
  --compose-file "${COMPOSE_FILE}" \
  --timeout "${VERIFY_TIMEOUT}" \
  --interval 3; then
  emit_event "ERROR" "failure_verify" "failed" "故障验证未失败，注入可能未生效"
  echo "ERROR: 故障验证意外通过，演练终止"
  exit 1
fi
emit_event "INFO" "failure_verify" "success" "故障验证符合预期（健康检查失败）"

emit_event "INFO" "rollback" "running" "开始执行回滚"
ROLLBACK_ARGS=(
  --service "${SERVICE}"
  --compose-file "${COMPOSE_FILE}"
  --health-timeout "${HEALTH_TIMEOUT}"
  --trace-id "${TRACE_ID}"
  --operator "${OPERATOR}"
)
if [[ -n "${ROLLBACK_TAG}" ]]; then
  ROLLBACK_ARGS+=(--to-tag "${ROLLBACK_TAG}")
fi
"${ROOT_DIR}/scripts/rollback_single_service.sh" "${ROLLBACK_ARGS[@]}"
emit_event "INFO" "rollback" "success" "回滚执行完成"

emit_event "INFO" "recovery_verify" "running" "验证回滚后恢复状态"
"${ROOT_DIR}/scripts/verify_release_health.sh" \
  --service "${SERVICE}" \
  --compose-file "${COMPOSE_FILE}" \
  --timeout "${HEALTH_TIMEOUT}"
emit_event "INFO" "recovery_verify" "success" "回滚后健康检查通过"

DRILL_RESULT="success"
echo "发布回滚一键演练通过: service=${SERVICE}, trace_id=${TRACE_ID}"
