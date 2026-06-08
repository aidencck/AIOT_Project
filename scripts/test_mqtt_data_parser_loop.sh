#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   AIOT_INTERNAL_TOKEN=xxx DEVICE_ID=d-100 ./scripts/test_mqtt_data_parser_loop.sh

BASE_MQTT="${BASE_MQTT:-http://127.0.0.1:8085}"
DEVICE_ID="${DEVICE_ID:-it-device-$(date +%s)}"
INTERNAL_TOKEN="${AIOT_INTERNAL_TOKEN:-}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
STREAM_KEY="${AIOT_EVENTS_DEVICE_STATUS_STREAM:-aiot:stream:device-event}"

if [[ -z "${INTERNAL_TOKEN}" ]]; then
  echo "ERROR: AIOT_INTERNAL_TOKEN is required"
  exit 1
fi

for cmd in curl jq; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

echo "[1/3] 通过 mqtt-adapter 上报在线状态消息"
REQ_BODY="$(jq -nc \
  --arg msgId "msg-$(date +%s)" \
  --arg deviceId "${DEVICE_ID}" \
  --arg topic "devices/${DEVICE_ID}/status" \
  --arg payload '{"status":"online"}' \
  --argjson ts "$(($(date +%s)*1000))" \
  '{messageId:$msgId, deviceId:$deviceId, topic:$topic, payload:$payload, timestamp:$ts}')"

RESP="$(curl -sS -X POST "${BASE_MQTT}/api/v1/mqtt/messages" \
  -H "Content-Type: application/json" \
  -H "X-Internal-Token: ${INTERNAL_TOKEN}" \
  -d "${REQ_BODY}")"
echo "${RESP}" | jq '.'

CODE="$(echo "${RESP}" | jq -r '.code // empty')"
EVENT_TYPE="$(echo "${RESP}" | jq -r '.data.parserEventType // empty')"
if [[ "${CODE}" != "200" || "${EVENT_TYPE}" != "DEVICE_ONLINE" ]]; then
  echo "ERROR: mqtt-adapter -> data-parser 闭环失败"
  exit 1
fi
echo "PASS: 闭环调用成功，事件类型=${EVENT_TYPE}"

echo "[2/3] 校验保持 Redis Stream 契约字段"
STREAM_RETURNED="$(echo "${RESP}" | jq -r '.data.parserStreamKey // empty')"
if [[ "${STREAM_RETURNED}" != "${STREAM_KEY}" ]]; then
  echo "ERROR: stream key 不匹配，expect=${STREAM_KEY}, actual=${STREAM_RETURNED}"
  exit 1
fi
echo "PASS: stream key 契约一致"

echo "[3/3] 可选：读取 Redis 最新消息确认 eventType"
if command -v redis-cli >/dev/null 2>&1; then
  RAW="$(redis-cli -h "${REDIS_HOST}" XREVRANGE "${STREAM_KEY}" + - COUNT 1)"
  echo "${RAW}"
  if ! echo "${RAW}" | grep -q "DEVICE_ONLINE"; then
    echo "ERROR: Redis Stream 最新消息未包含 DEVICE_ONLINE"
    exit 1
  fi
  echo "PASS: Redis Stream 消息写入成功"
else
  echo "SKIP: redis-cli 不存在，跳过 Redis 内容校验"
fi

echo "ALL PASS"
