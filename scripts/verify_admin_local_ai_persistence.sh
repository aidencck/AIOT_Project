#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/admin-local-ai-persistence}"
BASE_HOME_SERVICE="${BASE_HOME_SERVICE:-http://127.0.0.1:8083}"
BASE_DEVICE_SERVICE="${BASE_DEVICE_SERVICE:-http://127.0.0.1:8081}"
PHONE="${PHONE:-1380015$(date +%M%S)}"
PASSWORD="${PASSWORD:-123456}"
NICKNAME="${NICKNAME:-admin-local-user}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
REPORT_TYPE="${REPORT_TYPE:-}"
OCCURRED_AT="${OCCURRED_AT:-}"
REQUIRE_DETAIL="${REQUIRE_DETAIL:-true}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"
AUTO_GENERATE_HISTORY="${AUTO_GENERATE_HISTORY:-false}"
AUTO_GENERATE_HISTORY_SCENE="${AUTO_GENERATE_HISTORY_SCENE:-OFFLINE_FLAP}"
LIVE_FLOW_SCRIPT="${ROOT_DIR}/training/scripts/verify_ai_business_live_flow.sh"

mkdir -p "${ARTIFACT_DIR}"

for cmd in curl jq; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

wait_for_url() {
  local url="$1"
  local timeout_seconds="${2:-120}"
  local deadline=$(( $(date +%s) + timeout_seconds ))

  while (( $(date +%s) <= deadline )); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "ERROR: readiness timeout -> ${url}"
  return 1
}

write_json() {
  local path="$1"
  local body="$2"
  printf '%s\n' "${body}" > "${path}"
}

result_code() {
  jq -r '.code // empty'
}

result_message() {
  jq -r '.message // empty'
}

ensure_success_result() {
  local response_body="$1"
  local action="$2"
  local response_path="$3"
  local code
  local message

  code="$(printf '%s' "${response_body}" | result_code)"
  if [[ "${code}" != "200" ]]; then
    message="$(printf '%s' "${response_body}" | result_message)"
    echo "ERROR: ${action} failed, code=${code:-empty}, message=${message:-empty}, response=${response_path}"
    exit 1
  fi
}

generate_history_if_needed() {
  if [[ "${AUTO_GENERATE_HISTORY}" != "true" ]]; then
    return 1
  fi
  if [[ ! -x "${LIVE_FLOW_SCRIPT}" ]]; then
    echo "ERROR: live flow script is missing or not executable: ${LIVE_FLOW_SCRIPT}"
    exit 1
  fi

  echo "[4/6] Generate business live flow history automatically"
  LIVE_FLOW_OUTPUT="$("${LIVE_FLOW_SCRIPT}" "${AUTO_GENERATE_HISTORY_SCENE}" 2>&1)"
  write_json "${ARTIFACT_DIR}/auto-generate-history-output.log" "${LIVE_FLOW_OUTPUT}"
  return 0
}

refresh_query_and_pick_history() {
  QUERY_RESP="$(curl -sS -X GET "${BASE_DEVICE_SERVICE}/api/v1/admin-console/ai/persistence/query" \
    -H "Authorization: Bearer ${AUTH_TOKEN}")"
  write_json "${ARTIFACT_DIR}/ai-persistence-query-response.json" "${QUERY_RESP}"
  QUERY_CODE="$(printf '%s' "${QUERY_RESP}" | result_code)"
  QUERY_MESSAGE="$(printf '%s' "${QUERY_RESP}" | result_message)"
  if [[ "${QUERY_CODE}" != "200" ]]; then
    if [[ "${QUERY_MESSAGE}" == "缺少用户身份，请通过网关访问" ]]; then
      echo "ERROR: current device-service runtime still requires gateway headers; verify AIOT_SECURITY_ALLOW_DIRECT_USER_JWT and rebuild device-service. response=${ARTIFACT_DIR}/ai-persistence-query-response.json"
      exit 1
    fi
    echo "ERROR: admin query failed, code=${QUERY_CODE:-empty}, message=${QUERY_MESSAGE:-empty}, response=${ARTIFACT_DIR}/ai-persistence-query-response.json"
    exit 1
  fi

  if [[ -z "${REPORT_TYPE}" || -z "${OCCURRED_AT}" ]]; then
    FIRST_HISTORY="$(printf '%s' "${QUERY_RESP}" | jq -c '
      ((.data.businessLiveFlow.recentHistory // []) + (.data.controlPlaneDrain.recentHistory // []))
      | map(select(.reportType != null and .occurredAt != null))
      | sort_by(.occurredAt)
      | reverse
      | .[0] // empty
    ')"
    if [[ -n "${FIRST_HISTORY}" ]]; then
      REPORT_TYPE="$(printf '%s' "${FIRST_HISTORY}" | jq -r '.reportType')"
      OCCURRED_AT="$(printf '%s' "${FIRST_HISTORY}" | jq -r '.occurredAt')"
    fi
  fi
}

echo "[1/6] Wait local readiness"
wait_for_url "${BASE_HOME_SERVICE}/actuator/health/readiness" "${WAIT_TIMEOUT_SECONDS}"
wait_for_url "${BASE_DEVICE_SERVICE}/actuator/health/readiness" "${WAIT_TIMEOUT_SECONDS}"

if [[ -z "${AUTH_TOKEN}" ]]; then
  echo "[2/6] Register or login local user"
  REGISTER_BODY="$(jq -nc --arg phone "${PHONE}" --arg password "${PASSWORD}" --arg nickname "${NICKNAME}" \
    '{phone:$phone, password:$password, nickname:$nickname}')"
  write_json "${ARTIFACT_DIR}/register-request.json" "${REGISTER_BODY}"
  REGISTER_RESP="$(curl -sS -X POST "${BASE_HOME_SERVICE}/api/v1/users/register" \
    -H "Content-Type: application/json" \
    -d "${REGISTER_BODY}")"
  write_json "${ARTIFACT_DIR}/register-response.json" "${REGISTER_RESP}"

  LOGIN_BODY="$(jq -nc --arg phone "${PHONE}" --arg password "${PASSWORD}" \
    '{phone:$phone, password:$password}')"
  write_json "${ARTIFACT_DIR}/login-request.json" "${LOGIN_BODY}"
  LOGIN_RESP="$(curl -sS -X POST "${BASE_HOME_SERVICE}/api/v1/users/login" \
    -H "Content-Type: application/json" \
    -d "${LOGIN_BODY}")"
  write_json "${ARTIFACT_DIR}/login-response.json" "${LOGIN_RESP}"
  ensure_success_result "${LOGIN_RESP}" "user login" "${ARTIFACT_DIR}/login-response.json"
  AUTH_TOKEN="$(printf '%s' "${LOGIN_RESP}" | jq -r '.data.token // empty')"
  if [[ -z "${AUTH_TOKEN}" ]]; then
    echo "ERROR: login succeeded but token is empty, response=${ARTIFACT_DIR}/login-response.json"
    exit 1
  fi
else
  echo "[2/6] Reuse provided AUTH_TOKEN"
fi

echo "[3/6] Verify admin query over direct JWT"
refresh_query_and_pick_history

if [[ -z "${REPORT_TYPE}" || -z "${OCCURRED_AT}" ]]; then
  if generate_history_if_needed; then
    echo "[5/6] Retry admin query after auto-generated history"
    refresh_query_and_pick_history
  fi
fi

if [[ -z "${REPORT_TYPE}" || -z "${OCCURRED_AT}" ]]; then
  if [[ "${REQUIRE_DETAIL}" == "true" ]]; then
    echo "ERROR: admin query succeeded but no recent history entry was found. Run ./training/scripts/verify_ai_business_live_flow.sh OFFLINE_FLAP or runtime drain first, then rerun. response=${ARTIFACT_DIR}/ai-persistence-query-response.json"
    exit 1
  fi

  SUMMARY="$(jq -nc \
    --arg baseHomeService "${BASE_HOME_SERVICE}" \
    --arg baseDeviceService "${BASE_DEVICE_SERVICE}" \
    --arg phone "${PHONE}" \
    --arg queryResponse "${ARTIFACT_DIR}/ai-persistence-query-response.json" \
    '{
      success: true,
      detailSkipped: true,
      baseHomeService: $baseHomeService,
      baseDeviceService: $baseDeviceService,
      phone: $phone,
      queryResponse: $queryResponse
    }')"
  write_json "${ARTIFACT_DIR}/verification-summary.json" "${SUMMARY}"
  echo "[5/6] Skip detail verification because no history exists"
  echo "[6/6] Summary written to ${ARTIFACT_DIR}/verification-summary.json"
  exit 0
fi

echo "[5/6] Verify admin history detail"
DETAIL_RESP="$(curl -sS -G "${BASE_DEVICE_SERVICE}/api/v1/admin-console/ai/persistence/history/detail" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  --data-urlencode "reportType=${REPORT_TYPE}" \
  --data-urlencode "occurredAt=${OCCURRED_AT}")"
write_json "${ARTIFACT_DIR}/ai-persistence-history-detail-response.json" "${DETAIL_RESP}"
ensure_success_result "${DETAIL_RESP}" "admin history detail" "${ARTIFACT_DIR}/ai-persistence-history-detail-response.json"
DETAIL_EXISTS="$(printf '%s' "${DETAIL_RESP}" | jq -r '.data.exists // false')"
if [[ "${DETAIL_EXISTS}" != "true" ]]; then
  echo "ERROR: history detail responded successfully but report does not exist, response=${ARTIFACT_DIR}/ai-persistence-history-detail-response.json"
  exit 1
fi

SUMMARY="$(jq -nc \
  --arg baseHomeService "${BASE_HOME_SERVICE}" \
  --arg baseDeviceService "${BASE_DEVICE_SERVICE}" \
  --arg phone "${PHONE}" \
  --arg reportType "${REPORT_TYPE}" \
  --arg occurredAt "${OCCURRED_AT}" \
  --arg queryResponse "${ARTIFACT_DIR}/ai-persistence-query-response.json" \
  --arg detailResponse "${ARTIFACT_DIR}/ai-persistence-history-detail-response.json" \
  '{
    success: true,
    detailSkipped: false,
    baseHomeService: $baseHomeService,
    baseDeviceService: $baseDeviceService,
    phone: $phone,
    reportType: $reportType,
    occurredAt: $occurredAt,
    queryResponse: $queryResponse,
    detailResponse: $detailResponse
  }')"
write_json "${ARTIFACT_DIR}/verification-summary.json" "${SUMMARY}"

echo "[6/6] Admin local AI persistence verification passed"
echo "Artifacts: ${ARTIFACT_DIR}"
