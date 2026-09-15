#!/usr/bin/env bash
set -euo pipefail

REDIS_HOST="${AIOT_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${AIOT_REDIS_PORT:-6379}"
WRITE_OUTBOX_KEY="aiot:ai:mysql-write-outbox"
CASE_TASK_KEY="aiot:ai:case-materialization-outbox"

if ! command -v redis-cli >/dev/null 2>&1; then
  echo "redis-cli is required" >&2
  exit 1
fi

if ! command -v ruby >/dev/null 2>&1; then
  echo "ruby is required to generate GenericJackson2JsonRedisSerializer-compatible payloads" >&2
  exit 1
fi

encode_json_scalar() {
  ruby -rjson -e 'print JSON.generate(ARGV[0])' "$1"
}

OUTBOX_TASK_JSON='{"taskId":"outbox-task-apply","entityType":"AI_DIAGNOSIS","recordKey":"diag-apply-1","payloadJson":"diag-apply-payload","lastError":"legacy redis only","failedAt":1754560000000,"lastRetryAt":1754560001000,"retryCount":1}'
CASE_TASK_JSON='{"taskId":"case-task-apply","diagnosisId":"diag-apply-1","feedbackId":"feedback-apply-1","feedbackType":"FALSE_POSITIVE","resolutionStatus":"RESOLVED","resolutionNote":"runtime-apply-verification","operatorId":"ops-admin","queuedAt":1754560002000,"lastRetryAt":1754560003000,"retryCount":2,"lastError":"legacy redis only"}'

OUTBOX_VALUE="$(encode_json_scalar "${OUTBOX_TASK_JSON}")"
CASE_VALUE="$(encode_json_scalar "${CASE_TASK_JSON}")"

redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" DEL "${WRITE_OUTBOX_KEY}" "${CASE_TASK_KEY}" >/dev/null
redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" HSET "${WRITE_OUTBOX_KEY}" outbox-task-apply "${OUTBOX_VALUE}" >/dev/null
redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" HSET "${CASE_TASK_KEY}" case-task-apply "${CASE_VALUE}" >/dev/null

echo "Seeded ${WRITE_OUTBOX_KEY} and ${CASE_TASK_KEY} on ${REDIS_HOST}:${REDIS_PORT}"
