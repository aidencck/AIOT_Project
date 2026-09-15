#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/verify_entry.sh --stage <build|runtime|integration|release> [选项]

选项:
      --stage         验证阶段
      --modules       Maven 模块列表，逗号分隔
      --services      服务列表，逗号分隔
      --suite         集成脚本套件: service-communication | provision-webhook | shadow-event-flow
      --service       发布验证时的单个服务名
      --compose-file  compose 文件路径（默认: docker-compose.yml）
      --timeout       健康检查最大等待秒数（默认: 180）
      --interval      健康检查轮询间隔秒数（默认: 5）
  -h, --help          显示帮助

示例:
  ./scripts/verify_entry.sh --stage build --modules aiot-device-service,aiot-home-service
  ./scripts/verify_entry.sh --stage runtime --services mysql,redis,aiot-gateway
  ./scripts/verify_entry.sh --stage integration --suite provision-webhook
  ./scripts/verify_entry.sh --stage release --service aiot-device-service
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

STAGE=""
MODULES=""
SERVICES=""
SUITE=""
SERVICE=""
COMPOSE_FILE="docker-compose.yml"
TIMEOUT="180"
INTERVAL="5"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stage)
      STAGE="${2:-}"
      shift 2
      ;;
    --modules)
      MODULES="${2:-}"
      shift 2
      ;;
    --services)
      SERVICES="${2:-}"
      shift 2
      ;;
    --suite)
      SUITE="${2:-}"
      shift 2
      ;;
    --service)
      SERVICE="${2:-}"
      shift 2
      ;;
    --compose-file)
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

if [[ -z "${STAGE}" ]]; then
  echo "ERROR: --stage 为必填参数"
  usage
  exit 1
fi

split_csv() {
  local raw="$1"
  local item=""
  IFS=',' read -r -a items <<< "${raw}"
  for item in "${items[@]}"; do
    item="$(echo "${item}" | xargs)"
    if [[ -n "${item}" ]]; then
      printf '%s\n' "${item}"
    fi
  done
}

run_build() {
  if [[ -n "${MODULES}" ]]; then
    mvn -B -pl "${MODULES}" -am clean verify
  else
    mvn -B clean verify
  fi
}

run_runtime() {
  if [[ -z "${SERVICES}" ]]; then
    echo "ERROR: runtime 阶段需要 --services"
    exit 1
  fi

  local service_name=""
  while IFS= read -r service_name; do
    ./scripts/verify_release_health.sh \
      --service "${service_name}" \
      --compose-file "${COMPOSE_FILE}" \
      --timeout "${TIMEOUT}" \
      --interval "${INTERVAL}"
  done < <(split_csv "${SERVICES}")
}

run_integration() {
  case "${SUITE}" in
    service-communication)
      ./scripts/test_service_communication.sh
      ;;
    provision-webhook)
      ./scripts/test_provision_and_webhook.sh
      ;;
    shadow-event-flow)
      ./scripts/test_shadow_event_flow.sh
      ;;
    *)
      echo "ERROR: 未知集成套件: ${SUITE}"
      exit 1
      ;;
  esac
}

run_release() {
  if [[ -z "${SERVICE}" ]]; then
    echo "ERROR: release 阶段需要 --service"
    exit 1
  fi

  ./scripts/release_gate_check.sh
  ./scripts/verify_release_health.sh \
    --service "${SERVICE}" \
    --compose-file "${COMPOSE_FILE}" \
    --timeout "${TIMEOUT}" \
    --interval "${INTERVAL}"
}

case "${STAGE}" in
  build)
    run_build
    ;;
  runtime)
    run_runtime
    ;;
  integration)
    run_integration
    ;;
  release)
    run_release
    ;;
  *)
    echo "ERROR: 不支持的 stage: ${STAGE}"
    usage
    exit 1
    ;;
esac
