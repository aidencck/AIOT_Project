#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/deploy_env.sh --env <dev|staging|prod> --services <svc1,svc2> --tag <image_tag>

说明:
  1) 组合 compose.base.yml + compose.<env>.yml
  2) 按服务执行 release_canary_with_rollback.sh
  3) 运行时密钥从 compose/env/<env>/runtime.env 或服务器同路径挂载
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_NAME=""
SERVICES=""
IMAGE_TAG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV_NAME="${2:-}"
      shift 2
      ;;
    --services)
      SERVICES="${2:-}"
      shift 2
      ;;
    --tag)
      IMAGE_TAG="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知参数 $1"
      usage
      exit 1
      ;;
  esac
done

if [[ -z "${ENV_NAME}" || -z "${SERVICES}" || -z "${IMAGE_TAG}" ]]; then
  echo "ERROR: --env --services --tag 均为必填"
  usage
  exit 1
fi

COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
IFS=',' read -r -a service_array <<< "${SERVICES}"

for service in "${service_array[@]}"; do
  "${ROOT_DIR}/scripts/release_canary_with_rollback.sh" \
    --service "$(echo "${service}" | xargs)" \
    --tag "${IMAGE_TAG}" \
    --compose-file "${COMPOSE_FILE}"
done

echo "部署完成: env=${ENV_NAME}, services=${SERVICES}, tag=${IMAGE_TAG}"
