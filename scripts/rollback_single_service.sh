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
      --skip-verify    跳过回滚后健康验证
      --health-timeout 健康验证超时时间（秒，默认: 180）
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

SERVICE=""
ROLLBACK_TAG=""
COMPOSE_FILE="docker-compose.yml"
SKIP_VERIFY="0"
HEALTH_TIMEOUT="180"

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
    --skip-verify)
      SKIP_VERIFY="1"
      shift
      ;;
    --health-timeout)
      HEALTH_TIMEOUT="${2:-}"
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

if ! "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" config --services | grep -Fxq "${SERVICE}"; then
  echo "ERROR: compose 中不存在服务: ${SERVICE}"
  exit 1
fi

STATE_DIR="${ROOT_DIR}/scripts/.release_state"
mkdir -p "${STATE_DIR}"
STATE_FILE="${STATE_DIR}/${SERVICE}.previous_image"

if [[ -z "${ROLLBACK_TAG}" ]]; then
  if [[ ! -f "${STATE_FILE}" ]]; then
    echo "ERROR: 未提供 --to-tag 且找不到历史镜像记录: ${STATE_FILE}"
    exit 1
  fi
  PREV_IMAGE="$(cat "${STATE_FILE}")"
  if [[ "${PREV_IMAGE}" == *":"* ]]; then
    ROLLBACK_TAG="${PREV_IMAGE##*:}"
  else
    echo "ERROR: 历史镜像记录无法提取 tag: ${PREV_IMAGE}"
    echo "请显式传入 --to-tag <tag>"
    exit 1
  fi
fi

CURRENT_CID="$("${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" ps -q "${SERVICE}" || true)"
if [[ -n "${CURRENT_CID}" ]]; then
  CURRENT_IMAGE="$(docker inspect --format '{{.Config.Image}}' "${CURRENT_CID}" 2>/dev/null || true)"
  if [[ -n "${CURRENT_IMAGE}" ]]; then
    echo "${CURRENT_IMAGE}" > "${STATE_FILE}"
    echo "已记录当前镜像（用于反向回滚）: ${CURRENT_IMAGE}"
  fi
fi

echo "开始回滚服务: ${SERVICE}"
echo "目标标签: ${ROLLBACK_TAG}"

IMAGE_TAG="${ROLLBACK_TAG}" "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" pull "${SERVICE}"
IMAGE_TAG="${ROLLBACK_TAG}" "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" up -d --no-deps "${SERVICE}"

echo "回滚完成: ${SERVICE} -> tag=${ROLLBACK_TAG}"

if [[ "${SKIP_VERIFY}" == "1" ]]; then
  echo "已跳过健康验证（--skip-verify）"
  exit 0
fi

"${ROOT_DIR}/scripts/verify_release_health.sh" \
  --service "${SERVICE}" \
  --compose-file "${COMPOSE_FILE}" \
  --timeout "${HEALTH_TIMEOUT}"

echo "回滚与健康验证均成功。"
