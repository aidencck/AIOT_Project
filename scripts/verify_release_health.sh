#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/verify_release_health.sh -s <service> [选项]

必填参数:
  -s, --service        docker-compose 服务名（例如: aiot-device-service）

可选参数:
  -f, --compose-file   compose 文件路径（默认: docker-compose.yml）
      --timeout        最大等待秒数（默认: 180）
      --interval       轮询间隔秒数（默认: 5）
      --url            自定义 HTTP 健康检查地址（可选）
  -h, --help           显示帮助

说明:
  1) 优先检查容器 Health 状态（healthy / running）
  2) 若可推断或指定 HTTP 地址，则额外检查 HTTP 200
EOF
}

default_health_url() {
  local service="$1"
  case "${service}" in
    aiot-gateway) echo "http://127.0.0.1:8080/actuator/health/readiness" ;;
    aiot-device-service) echo "http://127.0.0.1:8081/actuator/health/readiness" ;;
    aiot-auth-service) echo "http://127.0.0.1:8082/actuator/health/readiness" ;;
    aiot-home-service) echo "http://127.0.0.1:8083/actuator/health/readiness" ;;
    aiot-rule-engine) echo "http://127.0.0.1:8084/actuator/health/readiness" ;;
    aiot-mqtt-adapter) echo "http://127.0.0.1:8085/actuator/health/readiness" ;;
    aiot-data-parser) echo "http://127.0.0.1:8086/actuator/health/readiness" ;;
    aiot-shadow-service) echo "http://127.0.0.1:8087/actuator/health/readiness" ;;
    nacos) echo "http://127.0.0.1:8848/nacos/v1/console/health/readiness" ;;
    *) echo "" ;;
  esac
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
COMPOSE_FILE="docker-compose.yml"
TIMEOUT="180"
INTERVAL="5"
HEALTH_URL=""

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
    --timeout)
      TIMEOUT="${2:-}"
      shift 2
      ;;
    --interval)
      INTERVAL="${2:-}"
      shift 2
      ;;
    --url)
      HEALTH_URL="${2:-}"
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

if ! [[ "${TIMEOUT}" =~ ^[0-9]+$ && "${INTERVAL}" =~ ^[0-9]+$ && "${INTERVAL}" -gt 0 ]]; then
  echo "ERROR: --timeout/--interval 需为正整数，且 interval > 0"
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

if [[ -z "${HEALTH_URL}" ]]; then
  HEALTH_URL="$(default_health_url "${SERVICE}")"
fi

if [[ -n "${HEALTH_URL}" ]] && ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: 需要 curl 执行 HTTP 健康检查"
  exit 1
fi

CID="$("${COMPOSE_CMD[@]}" -f "${COMPOSE_FILE}" ps -q "${SERVICE}" || true)"
if [[ -z "${CID}" ]]; then
  echo "ERROR: 服务未启动或未创建容器: ${SERVICE}"
  exit 1
fi

echo "开始健康验证: ${SERVICE}"
echo "容器 ID: ${CID}"
if [[ -n "${HEALTH_URL}" ]]; then
  echo "HTTP 检查: ${HEALTH_URL}"
else
  echo "HTTP 检查: 未配置，跳过"
fi

start_ts="$(date +%s)"
deadline=$((start_ts + TIMEOUT))

while true; do
  now="$(date +%s)"
  if (( now > deadline )); then
    echo "ERROR: 健康验证超时（${TIMEOUT}s）"
    docker ps --filter "id=${CID}"
    docker logs --tail 120 "${CID}" || true
    exit 1
  fi

  container_state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${CID}" 2>/dev/null || echo "unknown")"
  container_ok="0"
  if [[ "${container_state}" == "healthy" || "${container_state}" == "running" ]]; then
    container_ok="1"
  elif [[ "${container_state}" == "unhealthy" || "${container_state}" == "exited" || "${container_state}" == "dead" ]]; then
    echo "ERROR: 容器状态异常: ${container_state}"
    docker logs --tail 120 "${CID}" || true
    exit 1
  fi

  http_ok="1"
  if [[ -n "${HEALTH_URL}" ]]; then
    if ! curl -fsS "${HEALTH_URL}" >/dev/null 2>&1; then
      http_ok="0"
    fi
  fi

  if [[ "${container_ok}" == "1" && "${http_ok}" == "1" ]]; then
    echo "健康验证通过: ${SERVICE}"
    exit 0
  fi

  sleep "${INTERVAL}"
done
