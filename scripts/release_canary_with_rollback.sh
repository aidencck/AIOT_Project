#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/release_canary_with_rollback.sh -s <service> -t <image_tag> [选项]

必填参数:
  -s, --service          docker-compose 服务名（例如: aiot-device-service）
  -t, --tag              目标发布镜像标签（例如: sha-1a2b3c4 / v1.2.3）

可选参数:
  -f, --compose-file     compose 文件路径（默认: docker-compose.yml）
      --health-timeout   单次健康验证超时（秒，默认: 180）
      --health-interval  健康验证轮询间隔（秒，默认: 5）
      --canary-seconds   灰度观察时长（秒，默认: 120）
      --canary-interval  灰度巡检间隔（秒，默认: 15）
      --disable-auto-rollback  关闭自动回滚
  -h, --help             显示帮助

说明:
  执行流程：
  1) 发布门禁检查（复用 release_gate_check.sh）
  2) 执行单服务发布并做首次健康验证
  3) 灰度观察窗口内持续巡检
  4) 失败则自动回滚到前一版本并做回滚后验证
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
IMAGE_TAG=""
COMPOSE_FILE="docker-compose.yml"
HEALTH_TIMEOUT="180"
HEALTH_INTERVAL="5"
CANARY_SECONDS="120"
CANARY_INTERVAL="15"
AUTO_ROLLBACK="1"

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
    --health-timeout)
      HEALTH_TIMEOUT="${2:-}"
      shift 2
      ;;
    --health-interval)
      HEALTH_INTERVAL="${2:-}"
      shift 2
      ;;
    --canary-seconds)
      CANARY_SECONDS="${2:-}"
      shift 2
      ;;
    --canary-interval)
      CANARY_INTERVAL="${2:-}"
      shift 2
      ;;
    --disable-auto-rollback)
      AUTO_ROLLBACK="0"
      shift
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

for v in HEALTH_TIMEOUT HEALTH_INTERVAL CANARY_SECONDS CANARY_INTERVAL; do
  if ! [[ "${!v}" =~ ^[0-9]+$ ]]; then
    echo "ERROR: ${v} 必须是正整数秒"
    exit 1
  fi
done

if (( HEALTH_INTERVAL <= 0 || CANARY_INTERVAL <= 0 )); then
  echo "ERROR: --health-interval 与 --canary-interval 必须大于 0"
  exit 1
fi

run_rollback() {
  echo "[Canary] 开始自动回滚: ${SERVICE}"
  if "${ROOT_DIR}/scripts/rollback_single_service.sh" \
    --service "${SERVICE}" \
    --compose-file "${COMPOSE_FILE}" \
    --health-timeout "${HEALTH_TIMEOUT}"; then
    echo "[Canary] 自动回滚成功: ${SERVICE}"
    return 0
  fi

  echo "[Canary] 自动回滚失败: ${SERVICE}"
  return 1
}

echo "[Canary] 1/4 发布门禁检查"
"${ROOT_DIR}/scripts/release_gate_check.sh" \
  --service "${SERVICE}" \
  --tag "${IMAGE_TAG}" \
  --compose-file "${COMPOSE_FILE}"

echo "[Canary] 2/4 执行发布与首次健康验证"
if ! "${ROOT_DIR}/scripts/deploy_single_service.sh" \
  --service "${SERVICE}" \
  --tag "${IMAGE_TAG}" \
  --compose-file "${COMPOSE_FILE}" \
  --health-timeout "${HEALTH_TIMEOUT}"; then
  echo "[Canary] 首次健康验证失败"
  if [[ "${AUTO_ROLLBACK}" == "1" ]]; then
    run_rollback || true
  fi
  exit 1
fi

echo "[Canary] 3/4 灰度观察窗口开始（${CANARY_SECONDS}s）"
deadline=$(( $(date +%s) + CANARY_SECONDS ))
while (( $(date +%s) < deadline )); do
  if ! "${ROOT_DIR}/scripts/verify_release_health.sh" \
    --service "${SERVICE}" \
    --compose-file "${COMPOSE_FILE}" \
    --timeout "${HEALTH_INTERVAL}" \
    --interval "${HEALTH_INTERVAL}"; then
    echo "[Canary] 灰度巡检失败"
    if [[ "${AUTO_ROLLBACK}" == "1" ]]; then
      run_rollback || true
    fi
    exit 1
  fi
  sleep "${CANARY_INTERVAL}"
done

echo "[Canary] 4/4 灰度通过，发布完成: ${SERVICE} -> ${IMAGE_TAG}"
