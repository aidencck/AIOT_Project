#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/lib/common.sh"
secret::export_defaults
secret::load_file "${AIOT_ROOT_DIR}/compose/env/${AIOT_ENV}/runtime.env"
secret::require_secrets

ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/artifacts/full_lifecycle_real_$(date +%Y%m%d_%H%M%S)}"
API_ARTIFACT_DIR="${ARTIFACT_ROOT}/api"
VERIFY_ARTIFACT_DIR="${ARTIFACT_ROOT}/verify"
mkdir -p "${API_ARTIFACT_DIR}" "${VERIFY_ARTIFACT_DIR}"

for cmd in jq docker bash; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

record_text() {
  local name="$1"
  local content="$2"
  printf '%s\n' "${content}" > "${VERIFY_ARTIFACT_DIR}/${name}.txt"
}

record_json() {
  local name="$1"
  local content="$2"
  printf '%s\n' "${content}" > "${VERIFY_ARTIFACT_DIR}/${name}.json"
}

json_file() {
  local name="$1"
  printf '%s/%s.json' "${API_ARTIFACT_DIR}" "${name}"
}

assert_file_exists() {
  local path="$1"
  [[ -f "${path}" ]] || fail "missing file ${path}"
}

assert_json_value() {
  local file="$1"
  local expr="$2"
  local expected="$3"
  local actual
  actual="$(jq -r "${expr}" "${file}")"
  [[ "${actual}" == "${expected}" ]] || fail "json assert failed file=${file} expr=${expr} expected=${expected} actual=${actual}"
}

assert_nonempty() {
  local value="$1"
  local message="$2"
  [[ -n "${value}" && "${value}" != "null" ]] || fail "${message}"
}

mysql_query() {
  local sql="$1"
  docker exec aiot-mysql mysql -uroot -p"${MYSQL_PASSWORD}" -N -e "${sql}"
}

redis_cmd() {
  docker exec aiot-redis redis-cli "$@"
}

log "G0 run full lifecycle API flow"
ARTIFACT_DIR="${API_ARTIFACT_DIR}" "${ROOT_DIR}/scripts/test_full_user_device_lifecycle.sh"

SUMMARY_FILE="${API_ARTIFACT_DIR}/summary.json"
assert_file_exists "${SUMMARY_FILE}"

OWNER_USER_ID="$(jq -r '.ownerUserId' "${SUMMARY_FILE}")"
MEMBER_USER_ID="$(jq -r '.memberUserId' "${SUMMARY_FILE}")"
HOME_ID="$(jq -r '.homeId' "${SUMMARY_FILE}")"
ROOM_ID="$(jq -r '.roomId' "${SUMMARY_FILE}")"
PRODUCT_KEY="$(jq -r '.productKey' "${SUMMARY_FILE}")"
DEVICE_ID="$(jq -r '.deviceId' "${SUMMARY_FILE}")"
GLOBAL_DEVICE_ID="$(jq -r '.globalDeviceId' "${SUMMARY_FILE}")"
AUTH_IDENTITY="$(jq -r '.authIdentity' "${SUMMARY_FILE}")"
STREAM_KEY="$(jq -r '.parserStreamKey' "${SUMMARY_FILE}")"

OWNER_NICKNAME="$(jq -r '.data.nickname' "$(json_file owner_login)")"
MEMBER_NICKNAME="$(jq -r '.data.nickname' "$(json_file member_login)")"
EXCHANGE_SECRET="$(jq -r '.data.deviceSecret' "$(json_file provision_exchange)")"
EXCHANGE_SN="$(jq -r '.data.deviceSn' "$(json_file provision_exchange)")"
GET_DEVICE_FW="$(jq -r '.data.firmwareVersion' "$(json_file get_device_updated)")"
FINAL_CONTEXT_FILE="$(json_file ai_context_final)"
assert_file_exists "${FINAL_CONTEXT_FILE}"

log "G1 verify API artifacts are complete"
assert_json_value "${SUMMARY_FILE}" '.status' "PASS"
assert_json_value "$(json_file get_device_updated)" '.data.deviceId' "${DEVICE_ID}"
assert_json_value "$(json_file get_device_updated)" '.data.globalDeviceId' "${GLOBAL_DEVICE_ID}"
assert_json_value "$(json_file get_device_updated)" '.data.authIdentity' "${AUTH_IDENTITY}"
assert_json_value "$(json_file get_device_updated)" '.data.firmwareVersion' "2.0.0"
assert_json_value "$(json_file get_shadow_after_reported)" '.data.reported.actualTemp' "23.5"
assert_json_value "$(json_file ai_context_online)" '.data.onlineStatus' "online"
assert_json_value "$(json_file ai_context_after_home_delete)" '.data.homeId' "null"
assert_json_value "$(json_file ai_context_after_home_delete)" '.data.roomId' "null"

log "G2 verify MySQL user domain rows"
USER_ROWS="$(mysql_query "USE aiot_home; SELECT id,global_user_id,nickname,is_deleted FROM user_info WHERE id IN ('${OWNER_USER_ID}','${MEMBER_USER_ID}') ORDER BY id;")"
record_text "mysql_user_rows" "${USER_ROWS}"
printf '%s\n' "${USER_ROWS}" | grep -q "${OWNER_USER_ID}" || fail "owner user row missing"
printf '%s\n' "${USER_ROWS}" | grep -q "${MEMBER_USER_ID}" || fail "member user row missing"
printf '%s\n' "${USER_ROWS}" | grep -q "${OWNER_NICKNAME}" || fail "owner nickname mismatch in mysql"
printf '%s\n' "${USER_ROWS}" | grep -q "${MEMBER_NICKNAME}" || fail "member nickname mismatch in mysql"
printf '%s\n' "${USER_ROWS}" | awk '{print $2}' | grep -v '^NULL$' >/dev/null || fail "global_user_id missing in mysql"

HOME_ROW="$(mysql_query "USE aiot_home; SELECT id,name,location,is_deleted FROM home_info WHERE id='${HOME_ID}';")"
ROOM_ROW="$(mysql_query "USE aiot_home; SELECT id,home_id,name,is_deleted FROM room_info WHERE id='${ROOM_ID}';")"
MEMBER_ROWS="$(mysql_query "USE aiot_home; SELECT home_id,user_id,role,is_deleted FROM home_member WHERE home_id='${HOME_ID}' ORDER BY user_id;")"
TASK_ROWS="$(mysql_query "USE aiot_home; SELECT target_type,target_id,status,retry_count FROM home_delete_compensation_task WHERE home_id='${HOME_ID}' ORDER BY target_type;")"
record_text "mysql_home_row" "${HOME_ROW}"
record_text "mysql_room_row" "${ROOM_ROW}"
record_text "mysql_member_rows" "${MEMBER_ROWS}"
record_text "mysql_compensation_tasks" "${TASK_ROWS}"
printf '%s\n' "${HOME_ROW}" | grep -q $'\t1$' || fail "home row not marked deleted"
printf '%s\n' "${ROOM_ROW}" | grep -q $'\t1$' || fail "room row not marked deleted"
printf '%s\n' "${MEMBER_ROWS}" | grep -q "${OWNER_USER_ID}" || fail "owner membership row missing"
printf '%s\n' "${MEMBER_ROWS}" | grep -q "${MEMBER_USER_ID}" || fail "member membership row missing"
printf '%s\n' "${MEMBER_ROWS}" | grep -q $'\t1\t1$' || fail "owner role/is_deleted mismatch"
printf '%s\n' "${MEMBER_ROWS}" | grep -q $'\t2\t1$' || fail "member role/is_deleted mismatch"
printf '%s\n' "${TASK_ROWS}" | grep -q $'HOME\t'"${HOME_ID}"$'\t3\t0' || fail "home compensation task not successful"
printf '%s\n' "${TASK_ROWS}" | grep -q $'ROOM\t'"${ROOM_ID}"$'\t3\t0' || fail "room compensation task not successful"

log "G3 verify MySQL product and device rows"
PRODUCT_ROW="$(mysql_query "USE aiot_cloud; SELECT product_key,name,node_type,JSON_UNQUOTE(JSON_EXTRACT(thing_model_json,'$.schema')),JSON_UNQUOTE(JSON_EXTRACT(thing_model_json,'$.properties[2].identifier')),JSON_UNQUOTE(JSON_EXTRACT(thing_model_json,'$.properties[2].dataType')),is_deleted FROM product_info WHERE product_key='${PRODUCT_KEY}';")"
DEVICE_ROW="$(mysql_query "USE aiot_cloud; SELECT id,global_device_id,product_key,device_sn,auth_identity,status,COALESCE(home_id,'NULL'),COALESCE(room_id,'NULL'),firmware_version,is_deleted FROM device_info WHERE id='${DEVICE_ID}';")"
CREDENTIAL_ROW="$(mysql_query "USE aiot_cloud; SELECT device_id,device_secret,is_deleted FROM device_credential WHERE device_id='${DEVICE_ID}';")"
record_text "mysql_product_row" "${PRODUCT_ROW}"
record_text "mysql_device_row" "${DEVICE_ROW}"
record_text "mysql_credential_row" "${CREDENTIAL_ROW}"
printf '%s\n' "${PRODUCT_ROW}" | grep -q "${PRODUCT_KEY}" || fail "product row missing"
printf '%s\n' "${PRODUCT_ROW}" | grep -q $'\taiot.device-model/v1\tmode\ttext\t0$' || fail "product standardized model mismatch"
printf '%s\n' "${DEVICE_ROW}" | grep -q "${DEVICE_ID}" || fail "device row missing"
printf '%s\n' "${DEVICE_ROW}" | grep -q "${GLOBAL_DEVICE_ID}" || fail "global_device_id mismatch in mysql"
printf '%s\n' "${DEVICE_ROW}" | grep -q "${AUTH_IDENTITY}" || fail "auth_identity mismatch in mysql"
printf '%s\n' "${DEVICE_ROW}" | grep -q $'\t2\tNULL\tNULL\t2.0.0\t0$' || fail "device final state mismatch in mysql"
printf '%s\n' "${CREDENTIAL_ROW}" | grep -q "${EXCHANGE_SECRET}" || fail "device secret mismatch between API and mysql"

log "G4 verify Redis shadow keys match API data"
DESIRED_HASH="$(redis_cmd HGETALL "aiot:device:shadow:desired:${DEVICE_ID}")"
REPORTED_HASH="$(redis_cmd HGETALL "aiot:device:shadow:reported:${DEVICE_ID}")"
META_HASH="$(redis_cmd HGETALL "aiot:device:shadow:meta:${DEVICE_ID}")"
VERSION_VALUE="$(redis_cmd GET "aiot:device:shadow:version:${DEVICE_ID}")"
record_text "redis_shadow_desired" "${DESIRED_HASH}"
record_text "redis_shadow_reported" "${REPORTED_HASH}"
record_text "redis_shadow_meta" "${META_HASH}"
record_text "redis_shadow_version" "${VERSION_VALUE}"
printf '%s\n' "${DESIRED_HASH}" | grep -q $'power\ntrue' || fail "desired shadow power missing in redis"
printf '%s\n' "${DESIRED_HASH}" | grep -q $'targetTemp\n24' || fail "desired shadow targetTemp missing in redis"
printf '%s\n' "${REPORTED_HASH}" | grep -q $'actualTemp\n23.5' || fail "reported shadow actualTemp missing in redis"
printf '%s\n' "${META_HASH}" | grep -q $'lastUpdatedType\nreported' || fail "shadow meta lastUpdatedType mismatch"
[[ "${VERSION_VALUE}" == "2" ]] || fail "shadow version mismatch expected=2 actual=${VERSION_VALUE}"

log "G5 verify Redis stream has real online/offline traffic for same device"
STREAM_TAIL="$(redis_cmd XREVRANGE "${STREAM_KEY}" + - COUNT 20)"
record_text "redis_stream_tail" "${STREAM_TAIL}"
printf '%s\n' "${STREAM_TAIL}" | grep -q "${DEVICE_ID}" || fail "stream tail missing device id"
printf '%s\n' "${STREAM_TAIL}" | grep -q "DEVICE_ONLINE" || fail "stream tail missing DEVICE_ONLINE"
printf '%s\n' "${STREAM_TAIL}" | grep -q "DEVICE_OFFLINE" || fail "stream tail missing DEVICE_OFFLINE"
printf '%s\n' "${STREAM_TAIL}" | grep -q "${AUTH_IDENTITY}" || fail "stream tail missing auth identity"

log "G6 verify cross-source closure between API and persisted state"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.deviceId' "${GLOBAL_DEVICE_ID}"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.globalDeviceId' "${GLOBAL_DEVICE_ID}"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.authIdentity' "${AUTH_IDENTITY}"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.deviceSn' "${EXCHANGE_SN}"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.productKey' "${PRODUCT_KEY}"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.status' "2"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.firmwareVersion' "${GET_DEVICE_FW}"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.shadowSummary.reported.actualTemp' "23.5"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.shadowSummary.desired.targetTemp' "24"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.homeId' "null"
assert_json_value "${FINAL_CONTEXT_FILE}" '.data.roomId' "null"

jq -nc \
  --arg apiArtifactDir "${API_ARTIFACT_DIR}" \
  --arg verifyArtifactDir "${VERIFY_ARTIFACT_DIR}" \
  --arg ownerUserId "${OWNER_USER_ID}" \
  --arg memberUserId "${MEMBER_USER_ID}" \
  --arg homeId "${HOME_ID}" \
  --arg roomId "${ROOM_ID}" \
  --arg productKey "${PRODUCT_KEY}" \
  --arg deviceId "${DEVICE_ID}" \
  --arg globalDeviceId "${GLOBAL_DEVICE_ID}" \
  --arg authIdentity "${AUTH_IDENTITY}" \
  --arg streamKey "${STREAM_KEY}" \
  '{status:"PASS",apiArtifactDir:$apiArtifactDir,verifyArtifactDir:$verifyArtifactDir,ownerUserId:$ownerUserId,memberUserId:$memberUserId,homeId:$homeId,roomId:$roomId,productKey:$productKey,deviceId:$deviceId,globalDeviceId:$globalDeviceId,authIdentity:$authIdentity,streamKey:$streamKey}' \
  | tee "${ARTIFACT_ROOT}/summary.json"

echo "ALL PASS"
