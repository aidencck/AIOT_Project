#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/rollback_single_service.sh -s <service> [选项]

必填参数:
  -s, --service        docker-compose 服务名（例如: aiot-device-service）

可选参数:
  -t, --to-tag         回滚目标镜像标签（例如: v1.2.2）
  -f, --compose-file   compose 文件路径（默认: docker-compose.yml）
      --local          本地镜像模式（跳过 registry pull，改用本地镜像 tag 匹配）
      --skip-verify    跳过回滚后健康验证
      --health-timeout 健康验证超时时间（秒，默认: 180）
      --trace-id       留痕 Trace ID（默认自动生成）
      --operator       操作人（默认: $USER）
      --output-dir     留痕输出目录（默认: scripts/.release_state/audit）
  -h, --help           显示帮助

说明:
  若不传 --to-tag，脚本会尝试读取 scripts/.release_state/<service>.previous_image
  并自动提取 tag 作为回滚版本。

示例:
  ./scripts/rollback_single_service.sh -s aiot-device-service -t v1.2.2
  ./scripts/rollback_single_service.sh -s aiot-device-service
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

# 复用共享库的密钥默认值，确保 docker compose up 注入 AIOT_JWT_SECRET 等（否则服务启动失败）
source "${ROOT_DIR}/scripts/lib/common.sh"
secret::export_defaults

SERVICE=""
ROLLBACK_TAG=""
COMPOSE_FILE="docker-compose.yml"
LOCAL="0"
SKIP_VERIFY="0"
HEALTH_TIMEOUT="180"
TRACE_ID=""
OPERATOR="${USER:-unknown}"
OUTPUT_DIR=""

START_EPOCH="$(date +%s)"
AUDIT_FILE=""
SUMMARY_FILE=""
ROLLBACK_STATUS="failed"
CURRENT_IMAGE=""
PREV_IMAGE=""

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
  line="{\"ts\":\"${ts}\",\"trace_id\":\"$(json_escape "${TRACE_ID}")\",\"service\":\"$(json_escape "${SERVICE}")\",\"action\":\"rollback\",\"stage\":\"$(json_escape "${stage}")\",\"level\":\"$(json_escape "${level}")\",\"status\":\"$(json_escape "${status}")\",\"operator\":\"$(json_escape "${OPERATOR}")\",\"target_tag\":\"$(json_escape "${ROLLBACK_TAG}")\",\"message\":\"$(json_escape "${message}")\"}"
  echo "${line}"
  if [[ -n "${AUDIT_FILE}" ]]; then
    echo "${line}" >> "${AUDIT_FILE}"
  fi
}

finalize_trace() {
  local rc="$?"
  local end_epoch
  end_epoch="$(date +%s)"
  local duration_s=$((end_epoch - START_EPOCH))
  if [[ "${rc}" -eq 0 ]]; then
    ROLLBACK_STATUS="success"
  fi

  if [[ -n "${TRACE_ID}" ]]; then
    local finish_ts
    finish_ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    local summary
    summary="{\"ts\":\"${finish_ts}\",\"trace_id\":\"$(json_escape "${TRACE_ID}")\",\"service\":\"$(json_escape "${SERVICE}")\",\"action\":\"rollback\",\"result\":\"${ROLLBACK_STATUS}\",\"duration_s\":${duration_s},\"operator\":\"$(json_escape "${OPERATOR}")\",\"target_tag\":\"$(json_escape "${ROLLBACK_TAG}")\",\"previous_image\":\"$(json_escape "${PREV_IMAGE}")\",\"current_image\":\"$(json_escape "${CURRENT_IMAGE}")\",\"audit_file\":\"$(json_escape "${AUDIT_FILE}")\"}"
    if [[ -n "${SUMMARY_FILE}" ]]; then
      echo "${summary}" > "${SUMMARY_FILE}"
    fi
    if [[ "${rc}" -eq 0 ]]; then
      emit_event "INFO" "finalize" "success" "回滚流程完成，留痕已输出"
    else
      emit_event "ERROR" "finalize" "failed" "回滚流程失败，留痕已输出"
    fi
    echo "结构化留痕(JSONL): ${AUDIT_FILE}"
    echo "结构化摘要(JSON): ${SUMMARY_FILE}"
  fi
}

trap finalize_trace EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE="${2:-}"
      shift 2
      ;;
    -t|--to-tag)
      ROLLBACK_TAG="${2:-}"
      shift 2
      ;;
    -f|--compose-file)
      COMPOSE_FILE="${2:-}"
      shift 2
      ;;
    --local)
      LOCAL="1"
      shift
      ;;
    --skip-verify)
      SKIP_VERIFY="1"
      shift
      ;;
    --health-timeout)
      HEALTH_TIMEOUT="${2:-}"
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
    --output-dir)
      OUTPUT_DIR="${2:-}"
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
  TRACE_ID="rbk-${SERVICE}-$(date +%Y%m%d%H%M%S)"
fi

if ! [[ "${HEALTH_TIMEOUT}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: --health-timeout 需为正整数秒"
  exit 1
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

# 本地模式叠加 docker-compose.local.yml，使服务镜像指向本地 aiot-*:<tag>
COMPOSE_FILES=(-f "${COMPOSE_FILE}")
if [[ "${LOCAL}" == "1" ]]; then
  if [[ ! -f "docker-compose.local.yml" ]]; then
    echo "ERROR: compose 文件不存在: docker-compose.local.yml"
    exit 1
  fi
  COMPOSE_FILES+=(-f "docker-compose.local.yml")
fi

services_list="$("${COMPOSE_CMD[@]}" "${COMPOSE_FILES[@]}" config --services 2>/dev/null || true)"
if ! grep -Fxq "${SERVICE}" <<< "${services_list}"; then
  echo "ERROR: compose 中不存在服务: ${SERVICE}"
  exit 1
fi

STATE_DIR="${ROOT_DIR}/scripts/.release_state"
mkdir -p "${STATE_DIR}"
STATE_FILE="${STATE_DIR}/${SERVICE}.previous_image"

if [[ -z "${OUTPUT_DIR}" ]]; then
  OUTPUT_DIR="${STATE_DIR}/audit"
fi
mkdir -p "${OUTPUT_DIR}"
AUDIT_FILE="${OUTPUT_DIR}/rollback-${TRACE_ID}.jsonl"
SUMMARY_FILE="${OUTPUT_DIR}/rollback-${TRACE_ID}.summary.json"

emit_event "INFO" "init" "running" "开始执行回滚流程"

if [[ -z "${ROLLBACK_TAG}" ]]; then
  emit_event "INFO" "resolve_target" "running" "未指定回滚版本，尝试读取历史镜像记录"
  if [[ ! -f "${STATE_FILE}" ]]; then
    emit_event "ERROR" "resolve_target" "failed" "未提供 --to-tag 且找不到历史镜像记录"
    echo "ERROR: 未提供 --to-tag 且找不到历史镜像记录: ${STATE_FILE}"
    exit 1
  fi
  PREV_IMAGE="$(cat "${STATE_FILE}")"
  if [[ "${PREV_IMAGE}" == *":"* ]]; then
    ROLLBACK_TAG="${PREV_IMAGE##*:}"
    emit_event "INFO" "resolve_target" "success" "已从历史镜像记录解析回滚目标"
  else
    emit_event "ERROR" "resolve_target" "failed" "历史镜像记录无法提取 tag"
    echo "ERROR: 历史镜像记录无法提取 tag: ${PREV_IMAGE}"
    echo "请显式传入 --to-tag <tag>"
    exit 1
  fi
fi

CURRENT_CID="$("${COMPOSE_CMD[@]}" "${COMPOSE_FILES[@]}" ps -q "${SERVICE}" || true)"
if [[ -n "${CURRENT_CID}" ]]; then
  CURRENT_IMAGE="$(docker inspect --format '{{.Config.Image}}' "${CURRENT_CID}" 2>/dev/null || true)"
  if [[ -n "${CURRENT_IMAGE}" ]]; then
    echo "${CURRENT_IMAGE}" > "${STATE_FILE}"
    echo "已记录当前镜像（用于反向回滚）: ${CURRENT_IMAGE}"
    emit_event "INFO" "snapshot_current" "success" "已记录当前镜像用于反向回滚"
  fi
fi

emit_event "INFO" "rollback_pull" "running" "开始准备目标镜像"
echo "开始回滚服务: ${SERVICE}"
echo "目标标签: ${ROLLBACK_TAG}"

if [[ "${LOCAL}" == "1" ]]; then
  # 本地模式：跳过 registry pull，校验/准备本地镜像 tag，然后直接 up
  local_image="${SERVICE}:${ROLLBACK_TAG}"
  if ! docker image inspect "${local_image}" >/dev/null 2>&1; then
    base_image="${SERVICE}:local"
    if docker image inspect "${base_image}" >/dev/null 2>&1; then
      echo "本地镜像 ${local_image} 不存在，从 ${base_image} 复制 tag"
      docker tag "${base_image}" "${local_image}"
    else
      echo "ERROR: 本地镜像不存在: ${local_image} 且无 ${base_image} 兜底"
      exit 1
    fi
  fi
  emit_event "INFO" "rollback_pull" "success" "本地目标镜像已就绪"
  emit_event "INFO" "rollback_up" "running" "开始重建目标服务容器"
  AIOT_IMAGE_TAG="${ROLLBACK_TAG}" "${COMPOSE_CMD[@]}" "${COMPOSE_FILES[@]}" up -d --no-deps "${SERVICE}"
  emit_event "INFO" "rollback_up" "success" "服务容器已重建"
else
  IMAGE_TAG="${ROLLBACK_TAG}" "${COMPOSE_CMD[@]}" "${COMPOSE_FILES[@]}" pull "${SERVICE}"
  emit_event "INFO" "rollback_pull" "success" "目标镜像拉取完成"
  emit_event "INFO" "rollback_up" "running" "开始重建目标服务容器"
  IMAGE_TAG="${ROLLBACK_TAG}" "${COMPOSE_CMD[@]}" "${COMPOSE_FILES[@]}" up -d --no-deps "${SERVICE}"
  emit_event "INFO" "rollback_up" "success" "服务容器已重建"
fi

echo "回滚完成: ${SERVICE} -> tag=${ROLLBACK_TAG}"

if [[ "${SKIP_VERIFY}" == "1" ]]; then
  emit_event "WARN" "verify" "skipped" "已跳过回滚后健康验证"
  ROLLBACK_STATUS="success"
  echo "已跳过健康验证（--skip-verify）"
  exit 0
fi

emit_event "INFO" "verify" "running" "开始执行回滚后健康验证"
"${ROOT_DIR}/scripts/verify_release_health.sh" \
  --service "${SERVICE}" \
  --compose-file "${COMPOSE_FILE}" \
  --timeout "${HEALTH_TIMEOUT}"
emit_event "INFO" "verify" "success" "回滚后健康验证通过"
ROLLBACK_STATUS="success"

echo "回滚与健康验证均成功。"
