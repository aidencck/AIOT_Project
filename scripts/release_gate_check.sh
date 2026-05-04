#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/release_gate_check.sh -s <service> -t <image_tag> [选项]

必填参数:
  -s, --service        docker-compose 服务名（例如: aiot-device-service）
  -t, --tag            目标发布镜像标签（例如: sha-1a2b3c4 / v1.2.3）

可选参数:
  -f, --compose-file   compose 文件路径（默认: docker-compose.yml）
      --skip-baseline  跳过发布前基线健康检查
      --baseline-timeout 基线健康检查超时（秒，默认: 60）
  -h, --help           显示帮助

说明:
  发布门禁检查包含：
  1) compose 文件与服务存在性检查
  2) 服务必须配置 healthcheck
  3) 目标镜像可拉取
  4) 可选：当前运行实例基线健康检查
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
IMAGE_TAG=""
COMPOSE_FILE="docker-compose.yml"
SKIP_BASELINE="0"
BASELINE_TIMEOUT="60"

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
    --skip-baseline)
      SKIP_BASELINE="1"
      shift
      ;;
    --baseline-timeout)
      BASELINE_TIMEOUT="${2:-}"
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

if ! [[ "${BASELINE_TIMEOUT}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: --baseline-timeout 需为正整数秒"
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

service_rendered="$("${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" config 2>/dev/null | sed -n "/^[[:space:]]${SERVICE}:/,/^[^[:space:]]/p")"
if ! grep -q "healthcheck:" <<< "${service_rendered}"; then
  echo "ERROR: ${SERVICE} 缺少 healthcheck 配置，禁止发布"
  exit 1
fi

echo "[Gate] 验证目标镜像可拉取: ${SERVICE}:${IMAGE_TAG}"
IMAGE_TAG="${IMAGE_TAG}" "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" pull "${SERVICE}" >/dev/null

if [[ "${SKIP_BASELINE}" == "1" ]]; then
  echo "[Gate] 已跳过基线健康检查（--skip-baseline）"
  echo "[Gate] 通过"
  exit 0
fi

if "${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" ps -q "${SERVICE}" | grep -q .; then
  echo "[Gate] 执行发布前基线健康检查: ${SERVICE}"
  "${ROOT_DIR}/scripts/verify_release_health.sh" \
    --service "${SERVICE}" \
    --compose-file "${COMPOSE_FILE}" \
    --timeout "${BASELINE_TIMEOUT}" \
    --interval 5
else
  echo "[Gate] 当前未检测到运行中的 ${SERVICE}，跳过基线健康检查"
fi

echo "[Gate] 通过"
