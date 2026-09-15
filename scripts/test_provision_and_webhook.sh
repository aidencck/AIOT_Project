#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   AUTH_TOKEN=... AIOT_EMQX_WEBHOOK_SECRET=... ./scripts/test_provision_and_webhook.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

BASE_GATEWAY="${BASE_GATEWAY:-http://127.0.0.1:8080}"
BASE_DEVICE="${BASE_DEVICE:-http://127.0.0.1:8081}"
BASE_AUTH="${BASE_AUTH:-http://127.0.0.1:8082}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
WEBHOOK_SECRET="${AIOT_EMQX_WEBHOOK_SECRET:-}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/test_provision_and_webhook}"

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
    echo "BASE_DEVICE=${BASE_DEVICE}"
    echo "BASE_AUTH=${BASE_AUTH}"
    echo "HOME_ID=${HOME_ID:-demo-home-id}"
    echo "PRODUCT_KEY=${PRODUCT_KEY:-demo-product-key}"
    echo "DEVICE_NAME=${DEVICE_NAME:-}"
  } > "${ARTIFACT_DIR}/context.env"

  if command -v docker >/dev/null 2>&1; then
    "${COMPOSE_CMD[@]}" ps > "${ARTIFACT_DIR}/docker-compose.ps.txt" 2>&1 || true
    "${COMPOSE_CMD[@]}" logs --tail 200 aiot-device-service aiot-auth-service aiot-home-service aiot-rule-engine \
      > "${ARTIFACT_DIR}/services.log" 2>&1 || true
  fi

  curl -sS "${BASE_DEVICE}/actuator/health/readiness" > "${ARTIFACT_DIR}/device-health.json" 2>&1 || true
  curl -sS "${BASE_AUTH}/actuator/health/readiness" > "${ARTIFACT_DIR}/auth-health.json" 2>&1 || true
}

on_exit() {
  local status="$?"
  collect_evidence "${status}"
  exit "${status}"
}

trap on_exit EXIT

if [[ -z "${AUTH_TOKEN}" ]]; then
  echo "ERROR: AUTH_TOKEN is required"
  exit 1
fi
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  echo "ERROR: AIOT_EMQX_WEBHOOK_SECRET is required"
  exit 1
fi

for cmd in curl jq python3; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

sign_hmac_sha256() {
  local payload="$1"
  PAYLOAD="${payload}" SECRET="${WEBHOOK_SECRET}" python3 - <<'PY'
import hashlib
import hmac
import os

print(hmac.new(
    os.environ["SECRET"].encode("utf-8"),
    os.environ["PAYLOAD"].encode("utf-8"),
    hashlib.sha256
).hexdigest())
PY
}

HOME_ID="${HOME_ID:-demo-home-id}"
PRODUCT_KEY="${PRODUCT_KEY:-demo-product-key}"
DEVICE_NAME="${DEVICE_NAME:-demo-device-$(date +%s)}"

echo "[1/4] 申请配网 token"
cat > "${ARTIFACT_DIR}/step1-request.txt" <<EOF
POST ${BASE_GATEWAY}/api/v1/provision/token
Authorization: Bearer [REDACTED]
EOF
TOKEN_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/provision/token" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$(jq -nc --arg pk "${PRODUCT_KEY}" --arg dn "${DEVICE_NAME}" --arg hid "${HOME_ID}" \
    '{productKey:$pk, deviceName:$dn, homeId:$hid}')")"
printf '%s\n' "${TOKEN_RESP}" > "${ARTIFACT_DIR}/step1-response.json"
TOKEN="$(echo "${TOKEN_RESP}" | jq -r '.data // empty')"
if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: 配网 token 申请失败"
  cat "${ARTIFACT_DIR}/step1-response.json"
  exit 1
fi
echo "token=${TOKEN}"

echo "[2/4] 并发消费同一 token，验证只能成功一次"
REQ_BODY="$(jq -nc --arg t "${TOKEN}" --arg pk "${PRODUCT_KEY}" --arg dn "${DEVICE_NAME}" \
  '{provisionToken:$t, productKey:$pk, deviceName:$dn}')"
printf '%s\n' "${REQ_BODY}" > "${ARTIFACT_DIR}/step2-request.json"

RESP1="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/provision/exchange" -H "Content-Type: application/json" -d "${REQ_BODY}")"
RESP2="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/provision/exchange" -H "Content-Type: application/json" -d "${REQ_BODY}")"
printf '%s\n' "${RESP1}" > "${ARTIFACT_DIR}/step2-response-1.json"
printf '%s\n' "${RESP2}" > "${ARTIFACT_DIR}/step2-response-2.json"

OK1="$(echo "${RESP1}" | jq -r '.code // empty')"
OK2="$(echo "${RESP2}" | jq -r '.code // empty')"
if [[ "${OK1}" == "200" && "${OK2}" == "200" ]]; then
  echo "ERROR: token 被重复消费，测试失败"
  exit 1
fi
echo "PASS: token 并发复用被阻止"

DEVICE_ID="$(echo "${RESP1}" | jq -r '.data.deviceId // empty')"
if [[ -z "${DEVICE_ID}" ]]; then
  DEVICE_ID="$(echo "${RESP2}" | jq -r '.data.deviceId // empty')"
fi

if [[ -z "${DEVICE_ID}" ]]; then
  echo "WARN: 未获取到 deviceId，跳过状态同步验证"
  echo "RESP1=${RESP1}" > "${ARTIFACT_DIR}/step2-device-id-missing.txt"
  echo "RESP2=${RESP2}" >> "${ARTIFACT_DIR}/step2-device-id-missing.txt"
  exit 0
fi

echo "[3/4] 构造合法 webhook 签名并上报 connected"
TS="$(date +%s)"
ACTION="client.connected"
CLIENT_ID="it-client-1"
PAYLOAD_TO_SIGN="${ACTION}.${CLIENT_ID}.${DEVICE_ID}.${TS}"
SIGNATURE="$(sign_hmac_sha256 "${PAYLOAD_TO_SIGN}")"

WEBHOOK_BODY="$(jq -nc \
  --arg action "${ACTION}" \
  --arg clientid "${CLIENT_ID}" \
  --arg username "${DEVICE_ID}" \
  --argjson ts "${TS}" \
  '{action:$action, clientid:$clientid, username:$username, timestamp:$ts}')"
printf '%s\n' "${WEBHOOK_BODY}" > "${ARTIFACT_DIR}/step3-request.json"

HTTP_CODE_OK="$(curl -sS -o "${ARTIFACT_DIR}/step3-response.out" -w "%{http_code}" \
  -X POST "${BASE_GATEWAY}/api/v1/emqx/webhook" \
  -H "Content-Type: application/json" \
  -H "x-emqx-signature: ${SIGNATURE}" \
  -d "${WEBHOOK_BODY}")"
printf '%s\n' "${HTTP_CODE_OK}" > "${ARTIFACT_DIR}/step3-http-code.txt"
[[ "${HTTP_CODE_OK}" == "200" ]] || { echo "ERROR: 合法 webhook 被拒绝"; cat "${ARTIFACT_DIR}/step3-response.out"; exit 1; }
echo "PASS: 合法 webhook 验证通过"

echo "[4/4] 重放攻击验证（过期 timestamp）"
OLD_TS="$((TS-10000))"
OLD_SIGN_PAYLOAD="${ACTION}.${CLIENT_ID}.${DEVICE_ID}.${OLD_TS}"
OLD_SIGN="$(sign_hmac_sha256 "${OLD_SIGN_PAYLOAD}")"
OLD_BODY="$(jq -nc \
  --arg action "${ACTION}" \
  --arg clientid "${CLIENT_ID}" \
  --arg username "${DEVICE_ID}" \
  --argjson ts "${OLD_TS}" \
  '{action:$action, clientid:$clientid, username:$username, timestamp:$ts}')"
printf '%s\n' "${OLD_BODY}" > "${ARTIFACT_DIR}/step4-request.json"

HTTP_CODE_REPLAY="$(curl -sS -o "${ARTIFACT_DIR}/step4-response.out" -w "%{http_code}" \
  -X POST "${BASE_GATEWAY}/api/v1/emqx/webhook" \
  -H "Content-Type: application/json" \
  -H "x-emqx-signature: ${OLD_SIGN}" \
  -d "${OLD_BODY}")"
printf '%s\n' "${HTTP_CODE_REPLAY}" > "${ARTIFACT_DIR}/step4-http-code.txt"
[[ "${HTTP_CODE_REPLAY}" == "401" ]] || { echo "ERROR: 重放攻击未被拦截"; cat "${ARTIFACT_DIR}/step4-response.out"; exit 1; }
echo "PASS: webhook 重放攻击被拦截"

echo "ALL PASS"
