#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   AUTH_TOKEN=... AIOT_INTERNAL_TOKEN=... HOME_ID=... DEVICE_ID=... ./scripts/test_service_communication.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

BASE_GATEWAY="${BASE_GATEWAY:-http://127.0.0.1:8080}"
BASE_HOME="${BASE_HOME:-http://127.0.0.1:8083}"
BASE_DEVICE="${BASE_DEVICE:-http://127.0.0.1:8081}"
BASE_AUTH="${BASE_AUTH:-http://127.0.0.1:8082}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/test_service_communication}"

AUTH_TOKEN="${AUTH_TOKEN:-}"
INTERNAL_TOKEN="${AIOT_INTERNAL_TOKEN:-}"
HOME_ID="${HOME_ID:-}"
DEVICE_ID="${DEVICE_ID:-}"

mkdir -p "${ARTIFACT_DIR}"

COMPOSE_CMD=(docker compose)
if ! docker compose version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
  fi
fi

collect_evidence() {
  local status="$1"
  if [[ "${status}" == "0" ]]; then
    return
  fi

  {
    echo "BASE_GATEWAY=${BASE_GATEWAY}"
    echo "BASE_HOME=${BASE_HOME}"
    echo "BASE_DEVICE=${BASE_DEVICE}"
    echo "BASE_AUTH=${BASE_AUTH}"
    echo "HOME_ID=${HOME_ID}"
    echo "DEVICE_ID=${DEVICE_ID}"
  } > "${ARTIFACT_DIR}/context.env"

  if command -v docker >/dev/null 2>&1; then
    "${COMPOSE_CMD[@]}" ps > "${ARTIFACT_DIR}/docker-compose.ps.txt" 2>&1 || true
    "${COMPOSE_CMD[@]}" logs --tail 200 aiot-home-service aiot-device-service aiot-auth-service \
      > "${ARTIFACT_DIR}/services.log" 2>&1 || true
  fi

  curl -sS "${BASE_HOME}/actuator/health/readiness" > "${ARTIFACT_DIR}/home-health.json" 2>&1 || true
  curl -sS "${BASE_DEVICE}/actuator/health/readiness" > "${ARTIFACT_DIR}/device-health.json" 2>&1 || true
  curl -sS "${BASE_AUTH}/actuator/health/readiness" > "${ARTIFACT_DIR}/auth-health.json" 2>&1 || true
}

on_exit() {
  local status="$?"
  collect_evidence "${status}"
  exit "${status}"
}

trap on_exit EXIT

if [[ -z "${AUTH_TOKEN}" || -z "${INTERNAL_TOKEN}" || -z "${HOME_ID}" || -z "${DEVICE_ID}" ]]; then
  echo "ERROR: AUTH_TOKEN / AIOT_INTERNAL_TOKEN / HOME_ID / DEVICE_ID are required"
  exit 1
fi

for cmd in curl jq; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

echo "[1/4] 校验 home 权限接口返回统一 Result 契约"
cat > "${ARTIFACT_DIR}/step1-request.txt" <<EOF
GET ${BASE_GATEWAY}/api/v1/homes/${HOME_ID}/permission/check?minRole=3
Authorization: Bearer [REDACTED]
EOF
RESP="$(curl -sS "${BASE_GATEWAY}/api/v1/homes/${HOME_ID}/permission/check?minRole=3" \
  -H "Authorization: Bearer ${AUTH_TOKEN}")"
printf '%s\n' "${RESP}" > "${ARTIFACT_DIR}/step1-response.json"
CODE="$(echo "${RESP}" | jq -r '.code // empty')"
if [[ "${CODE}" != "200" ]]; then
  echo "ERROR: permission/check 未返回标准 Result 成功结构: ${RESP}"
  exit 1
fi
echo "PASS: permission/check Result 契约正常"

echo "[2/4] 内部状态接口无内部令牌应被拒绝"
cat > "${ARTIFACT_DIR}/step2-request.txt" <<EOF
POST ${BASE_DEVICE}/api/v1/internal/devices/${DEVICE_ID}/status?status=1
X-Internal-Token: [ABSENT]
EOF
HTTP_UNAUTH="$(curl -sS -o "${ARTIFACT_DIR}/step2-response.out" -w "%{http_code}" \
  -X POST "${BASE_DEVICE}/api/v1/internal/devices/${DEVICE_ID}/status?status=1")"
printf '%s\n' "${HTTP_UNAUTH}" > "${ARTIFACT_DIR}/step2-http-code.txt"
if [[ "${HTTP_UNAUTH}" != "401" ]]; then
  echo "ERROR: internal 接口未拒绝无令牌调用"
  cat "${ARTIFACT_DIR}/step2-response.out"
  exit 1
fi
echo "PASS: internal 接口拒绝无令牌调用"

echo "[3/4] 内部状态接口携带服务令牌应可进入业务层"
cat > "${ARTIFACT_DIR}/step3-request.txt" <<EOF
POST ${BASE_DEVICE}/api/v1/internal/devices/${DEVICE_ID}/status?status=1
X-Internal-Token: [REDACTED]
EOF
HTTP_INTERNAL="$(curl -sS -o "${ARTIFACT_DIR}/step3-response.out" -w "%{http_code}" \
  -X POST "${BASE_DEVICE}/api/v1/internal/devices/${DEVICE_ID}/status?status=1" \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}")"
printf '%s\n' "${HTTP_INTERNAL}" > "${ARTIFACT_DIR}/step3-http-code.txt"
if [[ "${HTTP_INTERNAL}" != "200" && "${HTTP_INTERNAL}" != "400" ]]; then
  echo "ERROR: internal 接口服务令牌未进入业务层"
  cat "${ARTIFACT_DIR}/step3-response.out"
  exit 1
fi
echo "PASS: internal 接口服务令牌校验通过，业务层已接收请求"

echo "[4/4] webhook 入口错误签名应被拒绝"
WEBHOOK_BODY="$(jq -nc \
  --arg action "client.connected" \
  --arg clientid "it-client-comm" \
  --arg username "${DEVICE_ID}" \
  --argjson ts "$(date +%s)" \
  '{action:$action, clientid:$clientid, username:$username, timestamp:$ts}')"
printf '%s\n' "${WEBHOOK_BODY}" > "${ARTIFACT_DIR}/step4-request.json"
HTTP_BAD_SIGN="$(curl -sS -o "${ARTIFACT_DIR}/step4-response.out" -w "%{http_code}" \
  -X POST "${BASE_GATEWAY}/api/v1/emqx/webhook" \
  -H "Content-Type: application/json" \
  -H "x-emqx-signature: bad-signature" \
  -d "${WEBHOOK_BODY}")"
printf '%s\n' "${HTTP_BAD_SIGN}" > "${ARTIFACT_DIR}/step4-http-code.txt"
if [[ "${HTTP_BAD_SIGN}" != "401" ]]; then
  echo "ERROR: webhook 错签名未被拦截"
  cat "${ARTIFACT_DIR}/step4-response.out"
  exit 1
fi
echo "PASS: webhook 错签名被拦截"

echo "ALL PASS"
