#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/lib/common.sh"
secret::export_defaults

BASE_GATEWAY="${BASE_GATEWAY:-http://127.0.0.1:8080}"
BASE_AUTH="${BASE_AUTH:-http://127.0.0.1:8082}"
BASE_DEVICE_INTERNAL="${BASE_DEVICE_INTERNAL:-http://127.0.0.1:8081}"
BASE_MQTT="${BASE_MQTT:-http://127.0.0.1:8085}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/full_lifecycle_$(date +%Y%m%d_%H%M%S)}"
mkdir -p "${ARTIFACT_DIR}"

for cmd in curl jq python3 docker; do
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

record_json() {
  local name="$1"
  local content="$2"
  printf '%s\n' "${content}" > "${ARTIFACT_DIR}/${name}.json"
}

record_text() {
  local name="$1"
  local content="$2"
  printf '%s\n' "${content}" > "${ARTIFACT_DIR}/${name}.txt"
}

assert_json_code() {
  local body="$1"
  local expected="$2"
  local actual
  actual="$(printf '%s' "${body}" | jq -r '.code // empty')"
  [[ "${actual}" == "${expected}" ]] || fail "unexpected code: expected=${expected}, actual=${actual}, body=${body}"
}

assert_http_code() {
  local actual="$1"
  local expected="$2"
  [[ "${actual}" == "${expected}" ]] || fail "unexpected http code: expected=${expected}, actual=${actual}"
}

assert_jq_equals() {
  local body="$1"
  local expr="$2"
  local expected="$3"
  local actual
  actual="$(printf '%s' "${body}" | jq -r "${expr}")"
  [[ "${actual}" == "${expected}" ]] || fail "assert jq failed: expr=${expr}, expected=${expected}, actual=${actual}, body=${body}"
}

assert_jq_nonempty() {
  local body="$1"
  local expr="$2"
  local actual
  actual="$(printf '%s' "${body}" | jq -r "${expr}")"
  [[ -n "${actual}" && "${actual}" != "null" ]] || fail "assert jq nonempty failed: expr=${expr}, body=${body}"
}

api_gateway() {
  local name="$1"
  local method="$2"
  local path="$3"
  local token="${4:-}"
  local data="${5:-}"
  local outfile="${ARTIFACT_DIR}/${name}.json"
  local headers=("-H" "Content-Type: application/json")
  if [[ -n "${token}" ]]; then
    headers+=("-H" "Authorization: Bearer ${token}")
  fi
  if [[ -n "${data}" ]]; then
    curl -sS -X "${method}" "${BASE_GATEWAY}${path}" "${headers[@]}" -d "${data}" | tee "${outfile}"
  else
    curl -sS -X "${method}" "${BASE_GATEWAY}${path}" "${headers[@]}" | tee "${outfile}"
  fi
}

api_gateway_http() {
  local name="$1"
  local method="$2"
  local path="$3"
  local token="${4:-}"
  local data="${5:-}"
  local body_file="${ARTIFACT_DIR}/${name}.body"
  local code_file="${ARTIFACT_DIR}/${name}.http"
  local headers=("-H" "Content-Type: application/json")
  if [[ -n "${token}" ]]; then
    headers+=("-H" "Authorization: Bearer ${token}")
  fi
  local code
  if [[ -n "${data}" ]]; then
    code="$(curl -sS -o "${body_file}" -w "%{http_code}" -X "${method}" "${BASE_GATEWAY}${path}" "${headers[@]}" -d "${data}")"
  else
    code="$(curl -sS -o "${body_file}" -w "%{http_code}" -X "${method}" "${BASE_GATEWAY}${path}" "${headers[@]}")"
  fi
  printf '%s\n' "${code}" | tee "${code_file}" >/dev/null
  cat "${body_file}"
}

api_auth_http() {
  local name="$1"
  local path="$2"
  local data="$3"
  local extra_header_name="${4:-}"
  local extra_header_value="${5:-}"
  local body_file="${ARTIFACT_DIR}/${name}.body"
  local code_file="${ARTIFACT_DIR}/${name}.http"
  local args=(-sS -o "${body_file}" -w "%{http_code}" -X POST "${BASE_AUTH}${path}" -H "Content-Type: application/json")
  if [[ -n "${extra_header_name}" ]]; then
    args+=(-H "${extra_header_name}: ${extra_header_value}")
  fi
  local code
  code="$(curl "${args[@]}" -d "${data}")"
  printf '%s\n' "${code}" | tee "${code_file}" >/dev/null
  cat "${body_file}"
}

api_device_internal() {
  local name="$1"
  local path="$2"
  local body
  body="$(curl -sS "${BASE_DEVICE_INTERNAL}${path}" -H "X-Internal-Token: ${AIOT_INTERNAL_TOKEN}")"
  record_json "${name}" "${body}"
  printf '%s' "${body}"
}

api_mqtt_internal() {
  local name="$1"
  local data="$2"
  local body
  body="$(curl -sS -X POST "${BASE_MQTT}/api/v1/mqtt/messages" \
    -H "Content-Type: application/json" \
    -H "X-Internal-Token: ${AIOT_INTERNAL_TOKEN}" \
    -d "${data}")"
  record_json "${name}" "${body}"
  printf '%s' "${body}"
}

sign_hmac_sha256() {
  local payload="$1"
  PAYLOAD="${payload}" SECRET="${AIOT_EMQX_WEBHOOK_SECRET}" python3 - <<'PY'
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

device_password() {
  local clientid="$1"
  local secret="$2"
  CLIENTID="${clientid}" SECRET="${secret}" python3 - <<'PY'
import hashlib
import hmac
import os
print(hmac.new(
    os.environ["SECRET"].encode("utf-8"),
    os.environ["CLIENTID"].encode("utf-8"),
    hashlib.sha256
).hexdigest())
PY
}

wait_until() {
  local attempts="$1"
  local sleep_seconds="$2"
  local check_cmd="$3"
  local i
  for ((i=1; i<=attempts; i++)); do
    if eval "${check_cmd}"; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done
  return 1
}

redis_xlen() {
  local stream_key="$1"
  docker exec aiot-redis redis-cli XLEN "${stream_key}" | tr -d '\r'
}

redis_latest_stream_entry() {
  local stream_key="$1"
  docker exec aiot-redis redis-cli XREVRANGE "${stream_key}" + - COUNT 1
}

log "G0 verify running stack"
"${ROOT_DIR}/aiotctl" verify dev > "${ARTIFACT_DIR}/aiotctl_verify_dev.log"

STAMP="$(date +%s)"
OWNER_PHONE="13910${STAMP: -6}"
MEMBER_PHONE="13920${STAMP: -6}"
PASSWORD="Aa123456!"
OWNER_NICK="owner-${STAMP}"
MEMBER_NICK="member-${STAMP}"
HOME_NAME="LifecycleHome-${STAMP}"
ROOM_NAME="LifecycleRoom-${STAMP}"
PRODUCT_NAME="LifecycleProduct-${STAMP}"
DEVICE_NAME="lifecycle-device-${STAMP}"
DEVICE_SN="SN-${STAMP}"
MANUAL_DEVICE_NAME="manual-device-${STAMP}"
CLIENT_ID="mqtt-client-${STAMP}"
THING_MODEL='{"properties":[{"identifier":"power","name":"Power","dataType":"bool"},{"identifier":"temp","name":"Temp","dataType":"float"}]}'
THING_MODEL_V2='{"properties":[{"identifier":"power","name":"Power","dataType":"bool"},{"identifier":"temp","name":"Temp","dataType":"float"},{"identifier":"mode","name":"Mode","dataType":"text"}]}'

log "1 owner register and login"
OWNER_REGISTER_BODY="$(jq -nc --arg phone "${OWNER_PHONE}" --arg password "${PASSWORD}" --arg nickname "${OWNER_NICK}" '{phone:$phone,password:$password,nickname:$nickname}')"
OWNER_REGISTER_HTTP_BODY="$(api_gateway_http owner_register POST /api/v1/users/register "" "${OWNER_REGISTER_BODY}")"
assert_http_code "$(cat "${ARTIFACT_DIR}/owner_register.http")" "201"
[[ -z "${OWNER_REGISTER_HTTP_BODY}" ]] || fail "owner register should return empty body"

OWNER_LOGIN_BODY="$(jq -nc --arg phone "${OWNER_PHONE}" --arg password "${PASSWORD}" '{phone:$phone,password:$password}')"
OWNER_LOGIN_RESP="$(api_gateway owner_login POST /api/v1/users/login "" "${OWNER_LOGIN_BODY}")"
assert_json_code "${OWNER_LOGIN_RESP}" "200"
assert_jq_nonempty "${OWNER_LOGIN_RESP}" '.data.token'
OWNER_TOKEN="$(printf '%s' "${OWNER_LOGIN_RESP}" | jq -r '.data.token')"
OWNER_USER_ID="$(printf '%s' "${OWNER_LOGIN_RESP}" | jq -r '.data.userId')"

log "2 member register and login"
MEMBER_REGISTER_BODY="$(jq -nc --arg phone "${MEMBER_PHONE}" --arg password "${PASSWORD}" --arg nickname "${MEMBER_NICK}" '{phone:$phone,password:$password,nickname:$nickname}')"
MEMBER_REGISTER_HTTP_BODY="$(api_gateway_http member_register POST /api/v1/users/register "" "${MEMBER_REGISTER_BODY}")"
assert_http_code "$(cat "${ARTIFACT_DIR}/member_register.http")" "201"
[[ -z "${MEMBER_REGISTER_HTTP_BODY}" ]] || fail "member register should return empty body"

MEMBER_LOGIN_BODY="$(jq -nc --arg phone "${MEMBER_PHONE}" --arg password "${PASSWORD}" '{phone:$phone,password:$password}')"
MEMBER_LOGIN_RESP="$(api_gateway member_login POST /api/v1/users/login "" "${MEMBER_LOGIN_BODY}")"
assert_json_code "${MEMBER_LOGIN_RESP}" "200"
MEMBER_TOKEN="$(printf '%s' "${MEMBER_LOGIN_RESP}" | jq -r '.data.token')"
MEMBER_USER_ID="$(printf '%s' "${MEMBER_LOGIN_RESP}" | jq -r '.data.userId')"

log "3 member initial home list should be empty"
MEMBER_HOMES_INITIAL="$(api_gateway member_list_homes_initial GET /api/v1/homes "${MEMBER_TOKEN}")"
assert_json_code "${MEMBER_HOMES_INITIAL}" "200"
assert_jq_equals "${MEMBER_HOMES_INITIAL}" '.data | length' "0"

log "4 owner create home and validate home list"
CREATE_HOME_BODY="$(jq -nc --arg name "${HOME_NAME}" --arg location "Lifecycle Lab" '{name:$name,location:$location}')"
CREATE_HOME_RESP="$(api_gateway create_home POST /api/v1/homes "${OWNER_TOKEN}" "${CREATE_HOME_BODY}")"
assert_json_code "${CREATE_HOME_RESP}" "200"
HOME_ID="$(printf '%s' "${CREATE_HOME_RESP}" | jq -r '.data')"
[[ -n "${HOME_ID}" && "${HOME_ID}" != "null" ]] || fail "home id missing"

OWNER_HOMES_RESP="$(api_gateway owner_list_homes GET /api/v1/homes "${OWNER_TOKEN}")"
assert_json_code "${OWNER_HOMES_RESP}" "200"
assert_jq_equals "${OWNER_HOMES_RESP}" ".data[] | select(.id==\"${HOME_ID}\") | .role" "1"

log "5 owner add member as role3 and validate membership"
ADD_MEMBER_BODY="$(jq -nc --arg userId "${MEMBER_USER_ID}" '{userId:$userId,role:3}')"
ADD_MEMBER_RESP="$(api_gateway add_member POST "/api/v1/homes/${HOME_ID}/members" "${OWNER_TOKEN}" "${ADD_MEMBER_BODY}")"
assert_json_code "${ADD_MEMBER_RESP}" "200"

MEMBER_HOMES_AFTER_JOIN="$(api_gateway member_list_homes_after_join GET /api/v1/homes "${MEMBER_TOKEN}")"
assert_json_code "${MEMBER_HOMES_AFTER_JOIN}" "200"
assert_jq_equals "${MEMBER_HOMES_AFTER_JOIN}" ".data[] | select(.id==\"${HOME_ID}\") | .role" "3"

MEMBERS_RESP="$(api_gateway list_members GET "/api/v1/homes/${HOME_ID}/members" "${OWNER_TOKEN}")"
assert_json_code "${MEMBERS_RESP}" "200"
assert_jq_equals "${MEMBERS_RESP}" ".data[] | select(.userId==\"${MEMBER_USER_ID}\") | .role" "3"

log "6 member role3 create room should fail"
CREATE_ROOM_DENY_BODY="$(jq -nc --arg homeId "${HOME_ID}" --arg name "DeniedRoom-${STAMP}" '{homeId:$homeId,name:$name}')"
api_gateway_http member_create_room_denied POST /api/v1/rooms "${MEMBER_TOKEN}" "${CREATE_ROOM_DENY_BODY}" >/dev/null
assert_http_code "$(cat "${ARTIFACT_DIR}/member_create_room_denied.http")" "403"

log "7 owner promote member to role2 and member creates room"
PROMOTE_BODY='{"role":2}'
PROMOTE_RESP="$(api_gateway promote_member PUT "/api/v1/homes/${HOME_ID}/members/${MEMBER_USER_ID}/role" "${OWNER_TOKEN}" "${PROMOTE_BODY}")"
assert_json_code "${PROMOTE_RESP}" "200"
MEMBERS_AFTER_PROMOTE="$(api_gateway list_members_after_promote GET "/api/v1/homes/${HOME_ID}/members" "${OWNER_TOKEN}")"
assert_jq_equals "${MEMBERS_AFTER_PROMOTE}" ".data[] | select(.userId==\"${MEMBER_USER_ID}\") | .role" "2"

CREATE_ROOM_BODY="$(jq -nc --arg homeId "${HOME_ID}" --arg name "${ROOM_NAME}" '{homeId:$homeId,name:$name}')"
CREATE_ROOM_RESP="$(api_gateway create_room POST /api/v1/rooms "${MEMBER_TOKEN}" "${CREATE_ROOM_BODY}")"
assert_json_code "${CREATE_ROOM_RESP}" "200"
ROOM_ID="$(printf '%s' "${CREATE_ROOM_RESP}" | jq -r '.data')"
[[ -n "${ROOM_ID}" && "${ROOM_ID}" != "null" ]] || fail "room id missing"

ROOM_LIST_RESP="$(api_gateway list_rooms GET "/api/v1/rooms?homeId=${HOME_ID}" "${OWNER_TOKEN}")"
assert_json_code "${ROOM_LIST_RESP}" "200"
assert_jq_equals "${ROOM_LIST_RESP}" ".data[] | select(.id==\"${ROOM_ID}\") | .homeId" "${HOME_ID}"

log "8 create product and update thing model"
CREATE_PRODUCT_BODY="$(jq -nc --arg name "${PRODUCT_NAME}" --arg description "Lifecycle product" --arg tm "${THING_MODEL}" '{name:$name,description:$description,nodeType:1,thingModelJson:$tm}')"
CREATE_PRODUCT_RESP="$(api_gateway create_product POST /api/v1/products "${OWNER_TOKEN}" "${CREATE_PRODUCT_BODY}")"
assert_json_code "${CREATE_PRODUCT_RESP}" "200"
PRODUCT_KEY="$(printf '%s' "${CREATE_PRODUCT_RESP}" | jq -r '.data')"
[[ -n "${PRODUCT_KEY}" && "${PRODUCT_KEY}" != "null" ]] || fail "product key missing"

GET_PRODUCT_RESP="$(api_gateway get_product GET "/api/v1/products/${PRODUCT_KEY}" "${OWNER_TOKEN}")"
assert_json_code "${GET_PRODUCT_RESP}" "200"
assert_jq_equals "${GET_PRODUCT_RESP}" '.data.productKey' "${PRODUCT_KEY}"

UPDATE_TM_RESP="$(api_gateway update_thing_model PUT "/api/v1/products/${PRODUCT_KEY}/thing-model" "${OWNER_TOKEN}" "${THING_MODEL_V2}")"
assert_json_code "${UPDATE_TM_RESP}" "200"
GET_PRODUCT_V2_RESP="$(api_gateway get_product_v2 GET "/api/v1/products/${PRODUCT_KEY}" "${OWNER_TOKEN}")"
assert_jq_equals "${GET_PRODUCT_V2_RESP}" '.data.productKey' "${PRODUCT_KEY}"
assert_jq_equals "${GET_PRODUCT_V2_RESP}" '.data.thingModelJson | fromjson | .schema' "aiot.device-model/v1"
assert_jq_equals "${GET_PRODUCT_V2_RESP}" '.data.thingModelJson | fromjson | .properties[] | select(.identifier=="mode") | .dataType' "text"

log "9 direct manual device create update delete lifecycle"
CREATE_MANUAL_DEVICE_BODY="$(jq -nc --arg name "${MANUAL_DEVICE_NAME}" --arg pk "${PRODUCT_KEY}" --arg homeId "${HOME_ID}" --arg roomId "${ROOM_ID}" --arg fw "1.0.0" '{deviceName:$name,productKey:$pk,homeId:$homeId,roomId:$roomId,firmwareVersion:$fw}')"
CREATE_MANUAL_DEVICE_RESP="$(api_gateway create_manual_device POST /api/v1/devices "${OWNER_TOKEN}" "${CREATE_MANUAL_DEVICE_BODY}")"
assert_json_code "${CREATE_MANUAL_DEVICE_RESP}" "200"
MANUAL_DEVICE_ID="$(printf '%s' "${CREATE_MANUAL_DEVICE_RESP}" | jq -r '.data.deviceId')"
MANUAL_DEVICE_SECRET="$(printf '%s' "${CREATE_MANUAL_DEVICE_RESP}" | jq -r '.data.deviceSecret')"
[[ -n "${MANUAL_DEVICE_ID}" && "${MANUAL_DEVICE_ID}" != "null" ]] || fail "manual device id missing"
[[ -n "${MANUAL_DEVICE_SECRET}" && "${MANUAL_DEVICE_SECRET}" != "null" ]] || fail "manual device secret missing"

UPDATE_MANUAL_DEVICE_BODY="$(jq -nc --arg name "${MANUAL_DEVICE_NAME}-updated" --arg roomId "${ROOM_ID}" --arg fw "1.0.1" '{deviceName:$name,roomId:$roomId,firmwareVersion:$fw}')"
UPDATE_MANUAL_DEVICE_RESP="$(api_gateway update_manual_device PUT "/api/v1/devices/${MANUAL_DEVICE_ID}" "${OWNER_TOKEN}" "${UPDATE_MANUAL_DEVICE_BODY}")"
assert_json_code "${UPDATE_MANUAL_DEVICE_RESP}" "200"
GET_MANUAL_DEVICE_RESP="$(api_gateway get_manual_device GET "/api/v1/devices/${MANUAL_DEVICE_ID}" "${OWNER_TOKEN}")"
assert_json_code "${GET_MANUAL_DEVICE_RESP}" "200"
assert_jq_equals "${GET_MANUAL_DEVICE_RESP}" '.data.firmwareVersion' "1.0.1"

DELETE_MANUAL_DEVICE_RESP="$(api_gateway delete_manual_device DELETE "/api/v1/devices/${MANUAL_DEVICE_ID}" "${OWNER_TOKEN}")"
assert_json_code "${DELETE_MANUAL_DEVICE_RESP}" "200"
api_gateway_http get_deleted_manual_device GET "/api/v1/devices/${MANUAL_DEVICE_ID}" "${OWNER_TOKEN}" >/dev/null
assert_http_code "$(cat "${ARTIFACT_DIR}/get_deleted_manual_device.http")" "404"

log "10 provision token exchange lifecycle"
PROVISION_TOKEN_BODY="$(jq -nc --arg pk "${PRODUCT_KEY}" --arg dn "${DEVICE_NAME}" --arg sn "${DEVICE_SN}" --arg homeId "${HOME_ID}" '{productKey:$pk,deviceName:$dn,deviceSn:$sn,homeId:$homeId}')"
PROVISION_TOKEN_RESP="$(api_gateway create_provision_token POST /api/v1/provision/token "${OWNER_TOKEN}" "${PROVISION_TOKEN_BODY}")"
assert_json_code "${PROVISION_TOKEN_RESP}" "200"
PROVISION_TOKEN="$(printf '%s' "${PROVISION_TOKEN_RESP}" | jq -r '.data')"

EXCHANGE_BODY="$(jq -nc --arg provisionToken "${PROVISION_TOKEN}" --arg productKey "${PRODUCT_KEY}" --arg deviceName "${DEVICE_NAME}" --arg deviceSn "${DEVICE_SN}" '{provisionToken:$provisionToken,productKey:$productKey,deviceName:$deviceName,deviceSn:$deviceSn}')"
EXCHANGE_RESP="$(api_gateway provision_exchange POST /api/v1/provision/exchange "" "${EXCHANGE_BODY}")"
assert_json_code "${EXCHANGE_RESP}" "200"
DEVICE_ID="$(printf '%s' "${EXCHANGE_RESP}" | jq -r '.data.deviceId')"
GLOBAL_DEVICE_ID="$(printf '%s' "${EXCHANGE_RESP}" | jq -r '.data.globalDeviceId')"
AUTH_IDENTITY="$(printf '%s' "${EXCHANGE_RESP}" | jq -r '.data.authIdentity')"
DEVICE_SECRET="$(printf '%s' "${EXCHANGE_RESP}" | jq -r '.data.deviceSecret')"
assert_jq_nonempty "${EXCHANGE_RESP}" '.data.mqttHost'
assert_jq_nonempty "${EXCHANGE_RESP}" '.data.mqttPort'

SECOND_EXCHANGE_BODY="$(api_gateway_http provision_exchange_reuse POST /api/v1/provision/exchange "" "${EXCHANGE_BODY}")"
assert_http_code "$(cat "${ARTIFACT_DIR}/provision_exchange_reuse.http")" "400"

log "11 device credential auth lifecycle"
BAD_AUTH_BODY="$(jq -nc --arg clientid "${CLIENT_ID}" --arg username "${AUTH_IDENTITY}" --arg password "bad-signature" '{clientid:$clientid,username:$username,password:$password}')"
api_auth_http device_auth_bad /api/v1/emqx/auth "${BAD_AUTH_BODY}" >/dev/null
assert_http_code "$(cat "${ARTIFACT_DIR}/device_auth_bad.http")" "401"

DEVICE_PASSWORD="$(device_password "${CLIENT_ID}" "${DEVICE_SECRET}")"
GOOD_AUTH_BODY="$(jq -nc --arg clientid "${CLIENT_ID}" --arg username "${AUTH_IDENTITY}" --arg password "${DEVICE_PASSWORD}" '{clientid:$clientid,username:$username,password:$password}')"
GOOD_AUTH_RESP="$(api_auth_http device_auth_good /api/v1/emqx/auth "${GOOD_AUTH_BODY}")"
assert_http_code "$(cat "${ARTIFACT_DIR}/device_auth_good.http")" "200"
[[ "${GOOD_AUTH_RESP}" == "allow" ]] || fail "device auth expected allow, got=${GOOD_AUTH_RESP}"

log "12 device detail list and update should reflect exchanged data"
GET_DEVICE_RESP="$(api_gateway get_device GET "/api/v1/devices/${DEVICE_ID}" "${OWNER_TOKEN}")"
assert_json_code "${GET_DEVICE_RESP}" "200"
assert_jq_equals "${GET_DEVICE_RESP}" '.data.authIdentity' "${AUTH_IDENTITY}"
assert_jq_equals "${GET_DEVICE_RESP}" '.data.homeId' "${HOME_ID}"

LIST_DEVICES_RESP="$(api_gateway list_devices GET "/api/v1/devices?homeId=${HOME_ID}" "${OWNER_TOKEN}")"
assert_json_code "${LIST_DEVICES_RESP}" "200"
assert_jq_equals "${LIST_DEVICES_RESP}" ".data[] | select(.deviceId==\"${DEVICE_ID}\") | .productKey" "${PRODUCT_KEY}"

UPDATE_DEVICE_BODY="$(jq -nc --arg roomId "${ROOM_ID}" --arg fw "2.0.0" '{roomId:$roomId,firmwareVersion:$fw}')"
UPDATE_DEVICE_RESP="$(api_gateway update_device PUT "/api/v1/devices/${DEVICE_ID}" "${OWNER_TOKEN}" "${UPDATE_DEVICE_BODY}")"
assert_json_code "${UPDATE_DEVICE_RESP}" "200"
GET_DEVICE_UPDATED_RESP="$(api_gateway get_device_updated GET "/api/v1/devices/${DEVICE_ID}" "${OWNER_TOKEN}")"
assert_jq_equals "${GET_DEVICE_UPDATED_RESP}" '.data.roomId' "${ROOM_ID}"
assert_jq_equals "${GET_DEVICE_UPDATED_RESP}" '.data.firmwareVersion' "2.0.0"

log "13 shadow desired reported lifecycle"
GET_SHADOW_INITIAL="$(api_gateway get_shadow_initial GET "/api/v1/devices/${DEVICE_ID}/shadow" "${OWNER_TOKEN}")"
assert_json_code "${GET_SHADOW_INITIAL}" "200"

UPDATE_DESIRED_BODY='{"power":true,"targetTemp":24}'
UPDATE_DESIRED_RESP="$(api_gateway update_shadow_desired POST "/api/v1/devices/${DEVICE_ID}/shadow/desired" "${OWNER_TOKEN}" "${UPDATE_DESIRED_BODY}")"
assert_json_code "${UPDATE_DESIRED_RESP}" "200"
SHADOW_AFTER_DESIRED="$(api_gateway get_shadow_after_desired GET "/api/v1/devices/${DEVICE_ID}/shadow" "${OWNER_TOKEN}")"
assert_jq_equals "${SHADOW_AFTER_DESIRED}" '.data.desired.power' "true"
assert_jq_equals "${SHADOW_AFTER_DESIRED}" '.data.delta.targetTemp' "24"

UPDATE_REPORTED_BODY='{"power":true,"targetTemp":24,"actualTemp":23.5}'
UPDATE_REPORTED_RESP="$(api_gateway update_shadow_reported POST "/api/v1/devices/${DEVICE_ID}/shadow/reported" "${OWNER_TOKEN}" "${UPDATE_REPORTED_BODY}")"
assert_json_code "${UPDATE_REPORTED_RESP}" "200"
SHADOW_AFTER_REPORTED="$(api_gateway get_shadow_after_reported GET "/api/v1/devices/${DEVICE_ID}/shadow" "${OWNER_TOKEN}")"
assert_jq_equals "${SHADOW_AFTER_REPORTED}" '.data.reported.actualTemp' "23.5"
assert_jq_equals "${SHADOW_AFTER_REPORTED}" '.data.delta | length' "0"

log "14 device online webhook should update status via actual auth API"
WEBHOOK_TS="$(date +%s)"
WEBHOOK_PAYLOAD="client.connected.${CLIENT_ID}.${AUTH_IDENTITY}.${WEBHOOK_TS}"
WEBHOOK_SIG="$(sign_hmac_sha256 "${WEBHOOK_PAYLOAD}")"
WEBHOOK_BODY="$(jq -nc --arg action "client.connected" --arg clientid "${CLIENT_ID}" --arg username "${AUTH_IDENTITY}" --argjson timestamp "${WEBHOOK_TS}" '{action:$action,clientid:$clientid,username:$username,timestamp:$timestamp}')"
WEBHOOK_ONLINE_RESP="$(api_auth_http device_webhook_online /api/v1/emqx/webhook "${WEBHOOK_BODY}" "x-emqx-signature" "${WEBHOOK_SIG}")"
assert_http_code "$(cat "${ARTIFACT_DIR}/device_webhook_online.http")" "200"
[[ "${WEBHOOK_ONLINE_RESP}" == "success" ]] || fail "webhook online failed: ${WEBHOOK_ONLINE_RESP}"

wait_until 20 1 "[[ \"\$(api_gateway get_device_after_online GET /api/v1/devices/${DEVICE_ID} ${OWNER_TOKEN} | jq -r '.data.status')\" == \"1\" ]]" || fail "device status did not become online"
AI_CONTEXT_ONLINE="$(api_device_internal ai_context_online "/api/v1/internal/ai/devices/${DEVICE_ID}/context?sceneType=OFFLINE_FLAP")"
assert_json_code "${AI_CONTEXT_ONLINE}" "200"
assert_jq_equals "${AI_CONTEXT_ONLINE}" '.data.homeId' "${HOME_ID}"
assert_jq_equals "${AI_CONTEXT_ONLINE}" '.data.roomId' "${ROOM_ID}"
assert_jq_equals "${AI_CONTEXT_ONLINE}" '.data.onlineStatus' "online"

log "15 mqtt ingress should land to parser and Redis stream"
STREAM_BEFORE="$(redis_xlen "aiot:stream:device-event")"
MQTT_BODY="$(jq -nc --arg msgId "msg-${STAMP}" --arg deviceId "${DEVICE_ID}" --arg topic "devices/${DEVICE_ID}/status" --arg payload '{"status":"online","temperature":23.5}' --argjson ts "$(( $(date +%s) * 1000 ))" '{messageId:$msgId,deviceId:$deviceId,topic:$topic,payload:$payload,timestamp:$ts}')"
MQTT_RESP="$(api_mqtt_internal mqtt_ingress "${MQTT_BODY}")"
assert_json_code "${MQTT_RESP}" "200"
assert_jq_equals "${MQTT_RESP}" '.data.deviceId' "${DEVICE_ID}"
assert_jq_equals "${MQTT_RESP}" '.data.parserEventType' "DEVICE_ONLINE"
PARSER_STREAM_KEY="$(printf '%s' "${MQTT_RESP}" | jq -r '.data.parserStreamKey')"
[[ "${PARSER_STREAM_KEY}" == "aiot:stream:device-event" ]] || fail "unexpected parser stream key ${PARSER_STREAM_KEY}"
STREAM_AFTER="$(redis_xlen "${PARSER_STREAM_KEY}")"
(( STREAM_AFTER > STREAM_BEFORE )) || fail "redis stream length did not increase"
LATEST_STREAM="$(redis_latest_stream_entry "${PARSER_STREAM_KEY}")"
record_text "redis_latest_stream" "${LATEST_STREAM}"
printf '%s' "${LATEST_STREAM}" | grep -q "${DEVICE_ID}" || fail "latest stream does not contain device id"

log "16 offline webhook should update status"
WEBHOOK_TS_OFFLINE="$(date +%s)"
WEBHOOK_PAYLOAD_OFFLINE="client.disconnected.${CLIENT_ID}.${AUTH_IDENTITY}.${WEBHOOK_TS_OFFLINE}"
WEBHOOK_SIG_OFFLINE="$(sign_hmac_sha256 "${WEBHOOK_PAYLOAD_OFFLINE}")"
WEBHOOK_BODY_OFFLINE="$(jq -nc --arg action "client.disconnected" --arg clientid "${CLIENT_ID}" --arg username "${AUTH_IDENTITY}" --argjson timestamp "${WEBHOOK_TS_OFFLINE}" '{action:$action,clientid:$clientid,username:$username,timestamp:$timestamp}')"
WEBHOOK_OFFLINE_RESP="$(api_auth_http device_webhook_offline /api/v1/emqx/webhook "${WEBHOOK_BODY_OFFLINE}" "x-emqx-signature" "${WEBHOOK_SIG_OFFLINE}")"
assert_http_code "$(cat "${ARTIFACT_DIR}/device_webhook_offline.http")" "200"
[[ "${WEBHOOK_OFFLINE_RESP}" == "success" ]] || fail "webhook offline failed"
wait_until 20 1 "[[ \"\$(api_gateway get_device_after_offline GET /api/v1/devices/${DEVICE_ID} ${OWNER_TOKEN} | jq -r '.data.status')\" == \"2\" ]]" || fail "device status did not become offline"

log "17 internal status summary should be queryable"
STATUS_SUMMARY_RESP="$(api_device_internal status_summary "/api/v1/internal/devices/status/summary")"
assert_json_code "${STATUS_SUMMARY_RESP}" "200"
assert_jq_nonempty "${STATUS_SUMMARY_RESP}" '.data.totalCount'

log "18 room delete should unbind roomId via compensation"
DELETE_ROOM_RESP="$(api_gateway delete_room DELETE "/api/v1/rooms/${ROOM_ID}?homeId=${HOME_ID}" "${MEMBER_TOKEN}")"
assert_json_code "${DELETE_ROOM_RESP}" "200"
wait_until 20 1 "[[ \"\$(api_device_internal ai_context_after_room_delete /api/v1/internal/ai/devices/${DEVICE_ID}/context?sceneType=OFFLINE_FLAP | jq -r '.data.roomId')\" == \"null\" ]]" || fail "device roomId not cleared after room delete"

log "19 home delete should unbind homeId and remove home membership"
DELETE_HOME_RESP="$(api_gateway delete_home DELETE "/api/v1/homes/${HOME_ID}" "${OWNER_TOKEN}")"
assert_json_code "${DELETE_HOME_RESP}" "200"
wait_until 20 1 "[[ \"\$(api_gateway owner_homes_after_delete GET /api/v1/homes ${OWNER_TOKEN} | jq -r '.data | map(select(.id==\"${HOME_ID}\")) | length')\" == \"0\" ]]" || fail "home still present in owner list"
wait_until 20 1 "[[ \"\$(api_device_internal ai_context_after_home_delete /api/v1/internal/ai/devices/${DEVICE_ID}/context?sceneType=OFFLINE_FLAP | jq -r '.data.homeId')\" == \"null\" ]]" || fail "device homeId not cleared after home delete"
FINAL_AI_CONTEXT="$(api_device_internal ai_context_final "/api/v1/internal/ai/devices/${DEVICE_ID}/context?sceneType=OFFLINE_FLAP")"
assert_jq_equals "${FINAL_AI_CONTEXT}" '.data.roomId' "null"

log "20 final snapshot"
FINAL_OWNER_HOMES="$(api_gateway final_owner_homes GET /api/v1/homes "${OWNER_TOKEN}")"
FINAL_MEMBER_HOMES="$(api_gateway final_member_homes GET /api/v1/homes "${MEMBER_TOKEN}")"
assert_jq_equals "${FINAL_OWNER_HOMES}" '.data | length' "0"
assert_jq_equals "${FINAL_MEMBER_HOMES}" '.data | length' "0"

jq -nc \
  --arg ownerUserId "${OWNER_USER_ID}" \
  --arg memberUserId "${MEMBER_USER_ID}" \
  --arg homeId "${HOME_ID}" \
  --arg roomId "${ROOM_ID}" \
  --arg productKey "${PRODUCT_KEY}" \
  --arg deviceId "${DEVICE_ID}" \
  --arg globalDeviceId "${GLOBAL_DEVICE_ID}" \
  --arg authIdentity "${AUTH_IDENTITY}" \
  --arg parserStreamKey "${PARSER_STREAM_KEY}" \
  --arg artifactDir "${ARTIFACT_DIR}" \
  '{ownerUserId:$ownerUserId,memberUserId:$memberUserId,homeId:$homeId,roomId:$roomId,productKey:$productKey,deviceId:$deviceId,globalDeviceId:$globalDeviceId,authIdentity:$authIdentity,parserStreamKey:$parserStreamKey,artifactDir:$artifactDir,status:"PASS"}' \
  | tee "${ARTIFACT_DIR}/summary.json"

echo "ALL PASS"
