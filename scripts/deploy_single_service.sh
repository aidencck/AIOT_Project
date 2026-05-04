#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/deploy_single_service.sh -s <service> -t <image_tag> [选项]

必填参数:
  -s, --service        docker-compose 服务名（例如: aiot-device-service）
  -t, --tag            要发布的镜像标签（例如: main / v1.2.3 / sha-xxxx）

可选参数:
  -f, --compose-file   compose 文件路径（默认: docker-compose.yml）
      --skip-verify    跳过发布后健康验证
      --health-timeout 健康验证超时时间（秒，默认: 180）
  -h, --help           显示帮助

示例:
  ./scripts/deploy_single_service.sh -s aiot-device-service -t v1.2.3
  ./scripts/deploy_single_service.sh -s aiot-gateway -t main --health-timeout 240
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
IMAGE_TAG=""
COMPOSE_FILE="docker-compose.yml"
SKIP_VERIFY="0"
HEALTH_TIMEOUT="180"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE="${2:-}"
      shift 2
      ;;
    -t|--tag)
      IMAGE_TAG="${2:-}"
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

if [[ -z "${SERVICE}" || -z "${IMAGE_TAG}" ]]; then
  echo "ERROR: --service 与 --tag 为必填参数"
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

PREV_CID="$("${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" ps -q "${SERVICE}" || true)"
if [[ -n "${PREV_CID}" ]]; then
  PREV_IMAGE="$(docker inspect --format '{{.Config.Image}}' "${PREV_CID}" 2>/dev/null || true)"
  if [[ -n "${PREV_IMAGE}" ]]; then
    echo "${PREV_IMAGE}" > "${STATE_FILE}"
    echo "已记录当前镜像（用于回滚）: ${PREV_IMAGE}"
  fi
fi

echo "开始发布服务: ${SERVICE}"
echo "目标标签: ${IMAGE_TAG}"

IMAGE_TAG="${IMAGE_TAG}" "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" pull "${SERVICE}"
IMAGE_TAG="${IMAGE_TAG}" "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" up -d --no-deps "${SERVICE}"

echo "发布完成: ${SERVICE} -> tag=${IMAGE_TAG}"

if [[ "${SKIP_VERIFY}" == "1" ]]; then
  echo "已跳过健康验证（--skip-verify）"
  exit 0
fi

"${ROOT_DIR}/scripts/verify_release_health.sh" \
  --service "${SERVICE}" \
  --compose-file "${COMPOSE_FILE}" \
  --timeout "${HEALTH_TIMEOUT}"

echo "发布与健康验证均成功。"
