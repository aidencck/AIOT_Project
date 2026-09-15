#!/usr/bin/env bash
# 启用严格错误处理：任何命令失败、未定义变量、管道错误都会导致脚本终止
set -euo pipefail

# 脚本核心定位逻辑：获取项目根目录，确保所有路径基于根目录的绝对路径执行
# 第一性原理：无论从哪里调用脚本，都能正确定位项目根目录，保证路径一致性
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

# 环境变量配置：敏感项不设可预测默认值，缺失即 fail-fast（CI 由 GITHUB_ENV 注入）
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/service-communication/bootstrap}"
BASE_GATEWAY="${BASE_GATEWAY:-http://127.0.0.1:8080}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD is required (no predictable default)}"

# 创建产物目录，确保所有日志和请求响应文件有地方存储
mkdir -p "${ARTIFACT_DIR}"

# 依赖检查：提前验证所有需要的系统命令是否存在，避免执行中途失败
# 使用场景：新手本地执行时能快速发现缺少的工具，提前安装
for cmd in curl jq mvn docker; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: missing command ${cmd}"
    exit 1
  fi
done

# 测试数据生成：使用时间戳保证每次执行的测试数据唯一，避免冲突
# 第一性原理：每次执行都是干净的隔离环境，不会和历史测试数据冲突
PHONE="${PHONE:-1380013$(date +%M%S)}"
PASSWORD="${PASSWORD:-123456}"
NICKNAME="${NICKNAME:-ci-user}"
HOME_NAME="${HOME_NAME:-CI Home}"
PRODUCT_NAME="${PRODUCT_NAME:-CI Product}"
DEVICE_NAME="${DEVICE_NAME:-ci-device-$(date +%s)}"

# 通用等待函数：轮询等待HTTP服务就绪，支持超时配置
# 第一性原理：分布式系统启动顺序不确定，必须主动等待依赖服务就绪再执行后续操作
# 参数1：要等待的URL，参数2：超时时间（默认180秒），参数3：轮询间隔（默认3秒）
wait_for_url() {
  local url="$1"
  local timeout="${2:-180}"
  local interval="${3:-3}"
  local deadline=$(( $(date +%s) + timeout ))

  while (( $(date +%s) <= deadline )); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${interval}"
  done
  echo "ERROR: wait url timeout -> ${url}"
  return 1
}

# Flyway数据库迁移环境变量配置：两个数据库（云端库和家庭库）的连接信息
# 使用场景：本地开发、CI测试中统一配置数据库连接，支持外部覆盖密码等敏感信息
export FLYWAY_AIOT_CLOUD_URL="jdbc:mysql://127.0.0.1:3306/aiot_cloud?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
export FLYWAY_AIOT_CLOUD_USER="root"
export FLYWAY_AIOT_CLOUD_PASSWORD="${MYSQL_PASSWORD}"
export FLYWAY_AIOT_HOME_URL="jdbc:mysql://127.0.0.1:3306/aiot_home?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
export FLYWAY_AIOT_HOME_USER="root"
export FLYWAY_AIOT_HOME_PASSWORD="${MYSQL_PASSWORD}"

# 步骤1：执行Flyway数据库迁移，先校验再执行迁移，日志输出到产物目录便于排查
echo "[1/6] Run Flyway"
mvn -B -pl aiot-db-migrator -P aiot-cloud flyway:validate flyway:migrate > "${ARTIFACT_DIR}/flyway-aiot-cloud.log" 2>&1
mvn -B -pl aiot-db-migrator -P aiot-home flyway:validate flyway:migrate > "${ARTIFACT_DIR}/flyway-aiot-home.log" 2>&1

# 步骤2：等待所有核心服务就绪，包括网关和各个业务服务的健康检查端点
echo "[2/6] Wait gateway and services"
wait_for_url "${BASE_GATEWAY}/actuator/health/readiness" 240 3
wait_for_url "${BASE_HOME:-http://127.0.0.1:18083}/actuator/health/readiness" 240 3
wait_for_url "${BASE_DEVICE:-http://127.0.0.1:18081}/actuator/health/readiness" 240 3
wait_for_url "${BASE_AUTH:-http://127.0.0.1:18082}/actuator/health/readiness" 240 3

# 步骤3：调用用户注册接口，生成唯一测试用户，所有请求和响应都落盘保存
echo "[3/6] Register user"
REGISTER_BODY="$(jq -nc --arg phone "${PHONE}" --arg password "${PASSWORD}" --arg nickname "${NICKNAME}" \
  '{phone:$phone, password:$password, nickname:$nickname}')"
printf '%s\n' "${REGISTER_BODY}" > "${ARTIFACT_DIR}/register-request.json"
REGISTER_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/users/register" \
  -H "Content-Type: application/json" \
  -d "${REGISTER_BODY}")"
printf '%s\n' "${REGISTER_RESP}" > "${ARTIFACT_DIR}/register-response.json"

# 步骤4：用户登录，获取认证令牌，后续所有需要权限的接口都要使用这个令牌
# 第一性原理：接口权限校验依赖有效的令牌，必须提前校验令牌获取成功
echo "[4/6] Login user"
LOGIN_BODY="$(jq -nc --arg phone "${PHONE}" --arg password "${PASSWORD}" \
  '{phone:$phone, password:$password}')"
printf '%s\n' "${LOGIN_BODY}" > "${ARTIFACT_DIR}/login-request.json"
LOGIN_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/users/login" \
  -H "Content-Type: application/json" \
  -d "${LOGIN_BODY}")"
printf '%s\n' "${LOGIN_RESP}" > "${ARTIFACT_DIR}/login-response.json"
AUTH_TOKEN="$(echo "${LOGIN_RESP}" | jq -r '.data.token // empty')"
if [[ -z "${AUTH_TOKEN}" ]]; then
  echo "ERROR: login failed"
  exit 1
fi

# 步骤5：创建家庭，家庭是IoT设备的归属单元，必须先创建家庭才能添加设备
echo "[5/6] Create home"
HOME_BODY="$(jq -nc --arg name "${HOME_NAME}" --arg location "CI Lab" '{name:$name, location:$location}')"
printf '%s\n' "${HOME_BODY}" > "${ARTIFACT_DIR}/create-home-request.json"
HOME_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/homes" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${HOME_BODY}")"
printf '%s\n' "${HOME_RESP}" > "${ARTIFACT_DIR}/create-home-response.json"
HOME_ID="$(echo "${HOME_RESP}" | jq -r '.data // empty')"
if [[ -z "${HOME_ID}" ]]; then
  echo "ERROR: create home failed"
  exit 1
fi

# 步骤6：创建设备产品（设备的模板），然后完成设备的开户和凭证交换，最终生成可用的设备ID
# 第一性原理：IoT设备的接入流程是标准化的：产品定义->家庭创建->设备开户->凭证交换->设备上线
echo "[6/6] Create product and provision device"

# 幂等处理：先查询同名产品复用 productKey，避免 schema 迁移后 product_info 清空导致重复创建时 key 后缀累积
PRODUCT_LIST_RESP="$(curl -sS -X GET "${BASE_GATEWAY}/api/v1/products" \
  -H "Authorization: Bearer ${AUTH_TOKEN}")"
printf '%s\n' "${PRODUCT_LIST_RESP}" > "${ARTIFACT_DIR}/list-products-response.json"
PRODUCT_KEY="$(echo "${PRODUCT_LIST_RESP}" | jq -r --arg name "${PRODUCT_NAME}" \
  '[.data[]? | select(.name == $name) | .productKey][0] // empty')"

if [[ -z "${PRODUCT_KEY}" ]]; then
  PRODUCT_BODY="$(jq -nc --arg name "${PRODUCT_NAME}" --arg description "CI product" \
    '{name:$name, description:$description, nodeType:1, thingModelJson:"{}"}')"
  printf '%s\n' "${PRODUCT_BODY}" > "${ARTIFACT_DIR}/create-product-request.json"
  PRODUCT_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/products" \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PRODUCT_BODY}")"
  printf '%s\n' "${PRODUCT_RESP}" > "${ARTIFACT_DIR}/create-product-response.json"
  PRODUCT_KEY="$(echo "${PRODUCT_RESP}" | jq -r '.data // empty')"
fi

if [[ -z "${PRODUCT_KEY}" ]]; then
  echo "ERROR: create product failed"
  exit 1
fi

# 申请设备开户令牌，需要关联产品、设备名称和归属的家庭ID
PROVISION_BODY="$(jq -nc --arg pk "${PRODUCT_KEY}" --arg dn "${DEVICE_NAME}" --arg hid "${HOME_ID}" \
  '{productKey:$pk, deviceName:$dn, homeId:$hid}')"
printf '%s\n' "${PROVISION_BODY}" > "${ARTIFACT_DIR}/provision-token-request.json"
PROVISION_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/provision/token" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${PROVISION_BODY}")"
printf '%s\n' "${PROVISION_RESP}" > "${ARTIFACT_DIR}/provision-token-response.json"
PROVISION_TOKEN="$(echo "${PROVISION_RESP}" | jq -r '.data // empty')"
if [[ -z "${PROVISION_TOKEN}" ]]; then
  echo "ERROR: provision token failed"
  exit 1
fi

# 用开户令牌交换正式的设备凭证，获取设备ID，完成设备的接入准备
EXCHANGE_BODY="$(jq -nc --arg t "${PROVISION_TOKEN}" --arg pk "${PRODUCT_KEY}" --arg dn "${DEVICE_NAME}" \
  '{provisionToken:$t, productKey:$pk, deviceName:$dn}')"
printf '%s\n' "${EXCHANGE_BODY}" > "${ARTIFACT_DIR}/exchange-request.json"
EXCHANGE_RESP="$(curl -sS -X POST "${BASE_GATEWAY}/api/v1/provision/exchange" \
  -H "Content-Type: application/json" \
  -d "${EXCHANGE_BODY}")"
printf '%s\n' "${EXCHANGE_RESP}" > "${ARTIFACT_DIR}/exchange-response.json"
DEVICE_ID="$(echo "${EXCHANGE_RESP}" | jq -r '.data.deviceId // empty')"
if [[ -z "${DEVICE_ID}" ]]; then
  echo "ERROR: provision exchange failed"
  exit 1
fi

# 生成核心配置摘要文件，把整个流程生成的关键信息汇总，供后续测试流程使用
SEED_SUMMARY="$(jq -nc \
  --arg authToken "${AUTH_TOKEN}" \
  --arg homeId "${HOME_ID}" \
  --arg deviceId "${DEVICE_ID}" \
  --arg productKey "${PRODUCT_KEY}" \
  --arg phone "${PHONE}" \
  '{authToken:$authToken, homeId:$homeId, deviceId:$deviceId, productKey:$productKey, phone:$phone}')"
printf '%s\n' "${SEED_SUMMARY}" > "${ARTIFACT_DIR}/seed-summary.json"
echo "Bootstrap completed."
