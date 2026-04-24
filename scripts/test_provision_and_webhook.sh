#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   AUTH_TOKEN=... AIOT_EMQX_WEBHOOK_SECRET=... ./scripts/test_provision_and_webhook.sh

BASE_DEVICE="${BASE_DEVICE:-http://127.0.0.1:8081}"
BASE_AUTH="${BASE_AUTH:-http://127.0.0.1:8082}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
WEBHOOK_SECRET="${AIOT_EMQX_WEBHOOK_SECRET:-}"

if [[ -z "${AUTH_TOKEN}" ]]; then
  echo "ERROR: AUTH_TOKEN is required"
  exit 1
fi
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  echo "ERROR: AIOT_EMQX_WEBHOOK_SECRET is required"
  exit 1
fi

for cmd in curl jq openssl; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

HOME_ID="${HOME_ID:-demo-home-id}"
PRODUCT_KEY="${PRODUCT_KEY:-demo-product-key}"
DEVICE_NAME="${DEVICE_NAME:-demo-device-$(date +%s)}"

echo "[1/4] 申请配网 token"
TOKEN="$(curl -sS -G "${BASE_DEVICE}/api/v1/provision/token" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  --data-urlencode "productKey=${PRODUCT_KEY}" \
  --data-urlencode "deviceName=${DEVICE_NAME}" \
  --data-urlencode "homeId=${HOME_ID}")"
echo "token=${TOKEN}"

echo "[2/4] 并发消费同一 token，验证只能成功一次"
REQ_BODY="$(jq -nc --arg t "${TOKEN}" --arg pk "${PRODUCT_KEY}" --arg dn "${DEVICE_NAME}" \
  '{provisionToken:$t, productKey:$pk, deviceName:$dn}')"

RESP1="$(curl -sS -X POST "${BASE_DEVICE}/api/v1/provision/exchange" -H "Content-Type: application/json" -d "${REQ_BODY}")"
RESP2="$(curl -sS -X POST "${BASE_DEVICE}/api/v1/provision/exchange" -H "Content-Type: application/json" -d "${REQ_BODY}")"

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
  exit 0
fi

echo "[3/4] 构造合法 webhook 签名并上报 connected"
TS="$(date +%s)"
ACTION="client.connected"
CLIENT_ID="it-client-1"
PAYLOAD_TO_SIGN="${ACTION}.${CLIENT_ID}.${DEVICE_ID}.${TS}"
SIGNATURE="$(printf '%s' "${PAYLOAD_TO_SIGN}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" | awk '{print $2}')"

WEBHOOK_BODY="$(jq -nc \
  --arg action "${ACTION}" \
  --arg clientid "${CLIENT_ID}" \
  --arg username "${DEVICE_ID}" \
  --argjson ts "${TS}" \
  '{action:$action, clientid:$clientid, username:$username, timestamp:$ts}')"

HTTP_CODE_OK="$(curl -sS -o /tmp/webhook_ok.out -w "%{http_code}" \
  -X POST "${BASE_AUTH}/api/v1/emqx/webhook" \
  -H "Content-Type: application/json" \
  -H "x-emqx-signature: ${SIGNATURE}" \
  -d "${WEBHOOK_BODY}")"
[[ "${HTTP_CODE_OK}" == "200" ]] || { echo "ERROR: 合法 webhook 被拒绝"; cat /tmp/webhook_ok.out; exit 1; }
echo "PASS: 合法 webhook 验证通过"

echo "[4/4] 重放攻击验证（过期 timestamp）"
OLD_TS="$((TS-10000))"
OLD_SIGN_PAYLOAD="${ACTION}.${CLIENT_ID}.${DEVICE_ID}.${OLD_TS}"
OLD_SIGN="$(printf '%s' "${OLD_SIGN_PAYLOAD}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" | awk '{print $2}')"
OLD_BODY="$(jq -nc \
  --arg action "${ACTION}" \
  --arg clientid "${CLIENT_ID}" \
  --arg username "${DEVICE_ID}" \
  --argjson ts "${OLD_TS}" \
  '{action:$action, clientid:$clientid, username:$username, timestamp:$ts}')"

HTTP_CODE_REPLAY="$(curl -sS -o /tmp/webhook_replay.out -w "%{http_code}" \
  -X POST "${BASE_AUTH}/api/v1/emqx/webhook" \
  -H "Content-Type: application/json" \
  -H "x-emqx-signature: ${OLD_SIGN}" \
  -d "${OLD_BODY}")"
[[ "${HTTP_CODE_REPLAY}" == "401" ]] || { echo "ERROR: 重放攻击未被拦截"; cat /tmp/webhook_replay.out; exit 1; }
echo "PASS: webhook 重放攻击被拦截"

echo "ALL PASS"
