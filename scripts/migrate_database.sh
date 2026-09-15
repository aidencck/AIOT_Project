#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/migrate_database.sh [选项]

选项:
  --db <cloud|home|all>   目标库（默认: all）
  --mode <migrate|info>    执行动作（默认: migrate）
  --target <version>       目标版本（可选，仅 migrate 生效）
  --image <tag>            Flyway 镜像（默认: flyway/flyway:10.17.3-alpine）
  --network <name>         MySQL 所在 docker 网络（默认: aiot-net）
  -h, --help               显示帮助

环境变量:
  MYSQL_PASSWORD           数据库密码（aiot_app / root 同密）
  MYSQL_HOST               MySQL 主机名（默认: aiot-mysql）
  MYSQL_PORT               MySQL 端口（默认: 3306）
  MYSQL_USER               迁移账号（默认: aiot_app）

说明:
  - migrate 幂等，无待执行脚本时为空操作
  - 与 aiot-db-migrator 的 Maven 配置对齐:
      flyway_schema_history_{db}，其余开关统一由
      aiot-db-migrator/flyway-migration.conf 提供
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

DB="all"
MODE="migrate"
TARGET=""
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:10.17.3-alpine}"
NETWORK="aiot-net"
MYSQL_HOST="${MYSQL_HOST:-aiot-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-aiot_app}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)
      DB="${2:-}"
      shift 2
      ;;
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --target)
      TARGET="${2:-}"
      shift 2
      ;;
    --image)
      FLYWAY_IMAGE="${2:-}"
      shift 2
      ;;
    --network)
      NETWORK="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知参数: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "${DB}" != "cloud" && "${DB}" != "home" && "${DB}" != "all" ]]; then
  echo "ERROR: --db 仅支持 cloud|home|all" >&2
  exit 1
fi

if [[ "${MODE}" != "migrate" && "${MODE}" != "info" ]]; then
  echo "ERROR: --mode 仅支持 migrate|info" >&2
  exit 1
fi

if [[ -n "${TARGET}" && "${MODE}" != "migrate" ]]; then
  echo "ERROR: --target 仅在 --mode migrate 下生效" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: 未检测到 docker" >&2
  exit 1
fi

load_password() {
  if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
    return
  fi
  # 服务器上 compose 通过 .env 注入 MYSQL_PASSWORD，此处回退加载
  if [[ -f "${ROOT_DIR}/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${ROOT_DIR}/.env"
    set +a
  fi
  if [[ -z "${MYSQL_PASSWORD:-}" ]]; then
    echo "ERROR: 缺少 MYSQL_PASSWORD（请设置环境变量或在 ${ROOT_DIR}/.env 提供）" >&2
    exit 1
  fi
}

db_name() {
  case "$1" in
    cloud) echo "aiot_cloud" ;;
    home) echo "aiot_home" ;;
  esac
}

run_flyway() {
  local db="$1"
  local db_full
  db_full="$(db_name "${db}")"
  local table="flyway_schema_history_${db_full}"
  local migration_dir="${ROOT_DIR}/aiot-db-migrator/src/main/resources/db/migration/${db_full}"
  local url="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${db_full}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"

  if [[ ! -d "${migration_dir}" ]]; then
    echo "ERROR: 迁移目录不存在: ${migration_dir}" >&2
    exit 1
  fi

  local -a cmd=(
    docker run --rm --network "${NETWORK}"
    -v "${migration_dir}:/flyway/sql:ro"
    -v "${ROOT_DIR}/aiot-db-migrator/flyway-migration.conf:/flyway/conf/flyway.conf:ro"
    -e "FLYWAY_URL=${url}"
    -e "FLYWAY_USER=${MYSQL_USER}"
    -e "FLYWAY_PASSWORD=${MYSQL_PASSWORD}"
    -e "FLYWAY_TABLE=${table}"
    -e "FLYWAY_LOCATIONS=filesystem:/flyway/sql"
  )

  if [[ -n "${TARGET}" ]]; then
    cmd+=(-e "FLYWAY_TARGET=${TARGET}")
  fi

  cmd+=("${FLYWAY_IMAGE}" "${MODE}")

  echo "[Migrate:${db}] 执行 ${MODE}"
  "${cmd[@]}"
}

main() {
  load_password

  local dbs=()
  if [[ "${DB}" == "all" ]]; then
    dbs=(cloud home)
  else
    dbs=("${DB}")
  fi

  for db in "${dbs[@]}"; do
    run_flyway "${db}"
  done

  echo "[Migrate] 完成"
}

main
