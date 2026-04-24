#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   DEVICE_ID=xxx TOKEN="Bearer yyy" ./scripts/test_shadow_event_flow.sh

DEVICE_ID="${DEVICE_ID:-}"
TOKEN="${TOKEN:-}"
BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"

if [[ -z "${DEVICE_ID}" || -z "${TOKEN}" ]]; then
  echo "DEVICE_ID and TOKEN are required."
  echo "Example: DEVICE_ID=1001 TOKEN='Bearer xxx' $0"
  exit 1
fi

echo "[1/4] Read initial shadow"
curl -sS -H "Authorization: ${TOKEN}" \
  "${BASE_URL}/api/v1/devices/${DEVICE_ID}/shadow" | jq '.'

echo "[2/4] Update desired shadow"
curl -sS -X POST \
  -H "Authorization: ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"powerSwitch":1,"targetTemp":24}' \
  "${BASE_URL}/api/v1/devices/${DEVICE_ID}/shadow/desired"
echo

echo "[3/4] Verify delta exists"
curl -sS -H "Authorization: ${TOKEN}" \
  "${BASE_URL}/api/v1/devices/${DEVICE_ID}/shadow" | jq '.delta'

echo "[4/4] Verify optimistic lock conflict (expect HTTP 409)"
HTTP_CODE=$(curl -sS -o /tmp/shadow_conflict.json -w "%{http_code}" -X POST \
  -H "Authorization: ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"powerSwitch":0}' \
  "${BASE_URL}/api/v1/devices/${DEVICE_ID}/shadow/desired?expectedVersion=1")
echo "HTTP_CODE=${HTTP_CODE}"
cat /tmp/shadow_conflict.json | jq '.'

echo "Done."
