#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   AUTH_TOKEN=... AIOT_INTERNAL_TOKEN=... HOME_ID=... DEVICE_ID=... ./scripts/test_service_communication.sh

BASE_HOME="${BASE_HOME:-http://127.0.0.1:8083}"
BASE_DEVICE="${BASE_DEVICE:-http://127.0.0.1:8081}"
BASE_AUTH="${BASE_AUTH:-http://127.0.0.1:8082}"

AUTH_TOKEN="${AUTH_TOKEN:-}"
INTERNAL_TOKEN="${AIOT_INTERNAL_TOKEN:-}"
HOME_ID="${HOME_ID:-}"
DEVICE_ID="${DEVICE_ID:-}"

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
RESP="$(curl -sS "${BASE_HOME}/api/v1/homes/${HOME_ID}/permission/check?minRole=3" \
  -H "Authorization: Bearer ${AUTH_TOKEN}")"
CODE="$(echo "${RESP}" | jq -r '.code // empty')"
if [[ "${CODE}" != "200" ]]; then
  echo "ERROR: permission/check 未返回标准 Result 成功结构: ${RESP}"
  exit 1
fi
echo "PASS: permission/check Result 契约正常"

echo "[2/4] 内部状态接口无内部令牌应被拒绝"
HTTP_UNAUTH="$(curl -sS -o /tmp/internal_unauth.out -w "%{http_code}" \
  -X POST "${BASE_DEVICE}/api/v1/internal/devices/${DEVICE_ID}/status?status=1")"
if [[ "${HTTP_UNAUTH}" != "401" ]]; then
  echo "ERROR: internal 接口未拒绝无令牌调用"
  cat /tmp/internal_unauth.out
  exit 1
fi
echo "PASS: internal 接口拒绝无令牌调用"

echo "[3/4] 内部状态接口携带服务令牌应可进入业务层"
HTTP_INTERNAL="$(curl -sS -o /tmp/internal_auth.out -w "%{http_code}" \
  -X POST "${BASE_DEVICE}/api/v1/internal/devices/${DEVICE_ID}/status?status=1" \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}")"
if [[ "${HTTP_INTERNAL}" != "200" ]]; then
  echo "ERROR: internal 接口服务令牌调用失败"
  cat /tmp/internal_auth.out
  exit 1
fi
echo "PASS: internal 接口服务令牌校验通过"

echo "[4/4] webhook 入口错误签名应被拒绝"
WEBHOOK_BODY="$(jq -nc \
  --arg action "client.connected" \
  --arg clientid "it-client-comm" \
  --arg username "${DEVICE_ID}" \
  --argjson ts "$(date +%s)" \
  '{action:$action, clientid:$clientid, username:$username, timestamp:$ts}')"
HTTP_BAD_SIGN="$(curl -sS -o /tmp/webhook_bad_sign.out -w "%{http_code}" \
  -X POST "${BASE_AUTH}/api/v1/emqx/webhook" \
  -H "Content-Type: application/json" \
  -H "x-emqx-signature: bad-signature" \
  -d "${WEBHOOK_BODY}")"
if [[ "${HTTP_BAD_SIGN}" != "401" ]]; then
  echo "ERROR: webhook 错签名未被拦截"
  cat /tmp/webhook_bad_sign.out
  exit 1
fi
echo "PASS: webhook 错签名被拦截"

echo "ALL PASS"
