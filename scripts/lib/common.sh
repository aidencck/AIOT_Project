#!/usr/bin/env bash
# ======================================================================
# AIoT DevOps Toolchain — shared library
# Source from any runner:  source "$(dirname "$0")/lib/common.sh"
# 只提供函数，所有脚本复用。严禁在本文件执行任何副作用命令。
# ======================================================================
set -Eeuo pipefail

# -------- 环境 --------
AIOT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AIOT_ROOT_DIR="$(cd "${AIOT_LIB_DIR}/../.." && pwd)"

export AIOT_ROOT_DIR AIOT_LIB_DIR

# -------- colors（no TTY fallback）--------
if [[ -t 1 ]]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YLW=$'\033[33m'; C_BLU=$'\033[34m'
  C_MAG=$'\033[35m'; C_CYN=$'\033[36m'; C_RST=$'\033[0m'; C_BOLD=$'\033[1m'
else
  C_RED=""; C_GRN=""; C_YLW=""; C_BLU=""; C_MAG=""; C_CYN=""; C_RST=""; C_BOLD=""
fi

# -------- logging --------
log::info()    { printf "${C_CYN}[INFO]${C_RST}  %s\n" "$*"; }
log::ok()      { printf "${C_GRN}[OK]${C_RST}    %s\n" "$*"; }
log::warn()    { printf "${C_YLW}[WARN]${C_RST}  %s\n" "$*" >&2; }
log::error()   { printf "${C_RED}[ERROR]${C_RST} %s\n" "$*" >&2; }
log::fatal()   { log::error "$*"; exit 1; }
log::banner()  { printf "\n${C_BOLD}${C_MAG}========== %s ==========${C_RST}\n" "$*"; }
log::section() { printf "${C_BOLD}${C_BLU}▶ %s${C_RST}\n" "$*"; }

# -------- tool probes (cached) --------
_DC_PROG=()       # ("docker" "compose") 或 ("docker-compose")；数组避免空格词拆分
_DOCKER_PROG=()
_MVN_PROG=()

# 封装成函数避免字符串命令词拆分：调用 comp_exec -f file.yml ...
comp_exec()   { tool::probe; [[ "${_DC_PROG[0]:-}" == "__MISSING__" ]] && { log::fatal "docker compose not found"; exit 1; }; "${_DC_PROG[@]}" "$@"; }
docker_exec() { tool::probe; [[ "${_DOCKER_PROG[0]:-}" == "__MISSING__" ]] && { log::fatal "docker not found"; exit 1; }; "${_DOCKER_PROG[@]}" "$@"; }
mvn_exec()    { tool::probe; [[ "${_MVN_PROG[0]:-}" == "__MISSING__" ]] && { log::fatal "mvn not found"; exit 1; }; "${_MVN_PROG[@]}" "$@"; }

tool::probe() {
  # PATH 补齐（IDE启动时PATH不完整）
  case ":$PATH:" in
    *:/usr/local/bin:*|*/usr/local/bin*) ;;
    *) export PATH="/usr/local/bin:/opt/homebrew/bin:${HOME}/.docker/bin:${HOME}/bin:${PATH}" ;;
  esac

  if (( ${#_DOCKER_PROG[@]} == 0 )); then
    if command -v docker >/dev/null 2>&1; then
      _DOCKER_PROG=("docker")
    else
      _DOCKER_PROG=("__MISSING__")
    fi
  fi
  if (( ${#_DC_PROG[@]} == 0 )); then
    if [[ "${_DOCKER_PROG[0]}" != "__MISSING__" ]] && docker compose version >/dev/null 2>&1; then
      _DC_PROG=("docker" "compose")
    elif command -v docker-compose >/dev/null 2>&1; then
      _DC_PROG=("docker-compose")
    else
      _DC_PROG=("__MISSING__")
    fi
  fi
  if (( ${#_MVN_PROG[@]} == 0 )); then
    if [[ -x "${AIOT_ROOT_DIR}/mvnw" ]]; then
      _MVN_PROG=("${AIOT_ROOT_DIR}/mvnw" "-B")
    elif command -v mvn >/dev/null 2>&1; then
      _MVN_PROG=("mvn" "-B")
    else
      _MVN_PROG=("__MISSING__")
    fi
  fi
}
tool::require_docker() {
  tool::probe
  [[ "${_DOCKER_PROG[0]}" != "__MISSING__" ]] || log::fatal "docker 未找到，请先启动 Docker Desktop 并将 /usr/local/bin 加入 PATH"
}
tool::require_compose() {
  tool::require_docker
  [[ "${_DC_PROG[0]}" != "__MISSING__" ]] || log::fatal "docker compose 未找到，请升级到 Docker Desktop ≥ 3.4"
}
tool::require_mvn() {
  tool::probe
  [[ "${_MVN_PROG[0]}" != "__MISSING__" ]] || log::fatal "Maven 未找到：需要 mvnw 或全局 mvn"
}
tool::docker() { docker_exec "$@"; }
tool::dc()     { local mode_wd="$PWD"; local -a _a=(); for arg in "$@"; do _a+=("$arg"); done; comp_exec "${_a[@]}"; }
tool::mvn()    { ( cd "$AIOT_ROOT_DIR" && mvn_exec "$@" ); }

# -------- secrets：环境变量契约（默认值 + 幂等重置）--------
# 关键设计：reset 采用「动态收集环境契约文件中的全部变量名」而非硬编码清单，
# 从 compose/env/*/runtime.env 与 .env.example 中提取 union 后统一 unset，
# 彻底避免「dev 独有变量(如 AIOT_DEVICE_SERVICE_URL)在切到 prod 时残留污染」这类漂移。
secret::reset() {
  local f k
  for f in "${AIOT_ROOT_DIR}"/compose/env/*/runtime.env "${AIOT_ROOT_DIR}"/compose/env/*/.env.example; do
    [[ -f "$f" ]] || continue
    while IFS= read -r k; do
      [[ -n "$k" ]] && unset "$k" 2>/dev/null || true
    done < <(sed -nE 's/^([A-Za-z_][A-Za-z0-9_]*)=.*/\1/p' "$f")
  done
  # 兜底：覆盖「仅以注释形式存在于 .env.example」的工具链自管理变量
  unset AIOT_ENV IMAGE_TAG SPRING_PROFILES_ACTIVE \
        MYSQL_PASSWORD EMQX_API_USER EMQX_API_PASSWORD \
        GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD \
        AIOT_JWT_SECRET AIOT_INTERNAL_TOKEN AIOT_EMQX_WEBHOOK_SECRET \
        AIOT_OPENAPI_PUBLIC_ENABLED AUTH_WEBHOOK_URL OLLAMA_MODEL \
        AI_LLM_ENABLED AI_LLM_BASE_URL AI_LLM_API_KEY AI_LLM_MODEL \
        AI_LLM_NUM_PREDICT AI_LLM_NUM_CTX AI_LLM_TIMEOUT_MS \
        2>/dev/null || true
}
secret::export_defaults() {
  export MYSQL_PASSWORD="${MYSQL_PASSWORD:-root123456}"
  export EMQX_API_USER="${EMQX_API_USER:-admin}"
  export EMQX_API_PASSWORD="${EMQX_API_PASSWORD:-emqxadmin123}"
  export GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
  export GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-grafana123}"
  export AIOT_JWT_SECRET="${AIOT_JWT_SECRET:-jwt-local-dev-strong-secret-2026-32byte}"
  export AIOT_INTERNAL_TOKEN="${AIOT_INTERNAL_TOKEN:-internal-token-local-dev-20260826-24c}"
  export AIOT_EMQX_WEBHOOK_SECRET="${AIOT_EMQX_WEBHOOK_SECRET:-emqx-webhook-local-dev-20260826-24ch}"
  export AIOT_ENV="${AIOT_ENV:-dev}"
  export IMAGE_TAG="${IMAGE_TAG:-main}"
}
# 从 runtime.env / .env 叠加（仅存在时）
secret::load_file() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  log::info "Load env from ${f#${AIOT_ROOT_DIR}/}"
  set -a; # shellcheck disable=SC1090
  source "$f"; set +a
}

# -------- 环境上下文单一事实源 --------
# env::ctx 将 mode 绑定为 (env, workdir, compose files) 三元组：
#   - 消除 compose::files 与 compose::workdir 两处独立 case 的漂移
#   - 消除 AIOT_ENV 与 mode 脱节导致的「prod compose 文件 + dev 密钥」污染
# 调用后可用：_CTX_ENV / _CTX_WORKDIR / _COMPOSE_FILES（bash 无法返回数组，故用全局变量传递）
_COMPOSE_FILES=()
_CTX_ENV=""
_CTX_WORKDIR=""
env::ctx() {
  local mode="$1"
  _COMPOSE_FILES=()
  case "$mode" in
    infra)         _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.dev-infra-only.yml") ;;
    main|dev)      _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.yml") ;;
    main-obs)      _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.yml" "--profile" "observability") ;;
    local)         _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.yml" "-f" "docker-compose.local.yml") ;;
    local-obs)     _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.yml" "-f" "docker-compose.local.yml" "--profile" "observability") ;;
    ci)            _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.yml" "-f" "docker-compose.ci.yml") ;;
    admin-local)   _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}";          _COMPOSE_FILES=("-f" "docker-compose.yml" "-f" "docker-compose.admin-local.yml") ;;
    staging)       _CTX_ENV=staging;  _CTX_WORKDIR="${AIOT_ROOT_DIR}/compose";  _COMPOSE_FILES=("-f" "compose.base.yml" "-f" "compose.staging.yml") ;;
    staging-dev)   _CTX_ENV=dev;      _CTX_WORKDIR="${AIOT_ROOT_DIR}/compose";  _COMPOSE_FILES=("-f" "compose.base.yml" "-f" "compose.dev.yml") ;;
    prod)          _CTX_ENV=prod;     _CTX_WORKDIR="${AIOT_ROOT_DIR}/compose";  _COMPOSE_FILES=("-f" "compose.base.yml" "-f" "compose.prod.yml") ;;
    *) log::fatal "unknown compose mode: $mode" ;;
  esac
}
# 兼容别名：既有 compose::files / compose::workdir 调用点无需改动，统一收敛到 env::ctx
compose::files()   { env::ctx "$1"; }
compose::workdir() { env::ctx "$1"; echo "$_CTX_WORKDIR"; }

# 按当前 _CTX_ENV 幂等加载环境：reset 防污染 → 默认值兜底 → 强绑定 AIOT_ENV → 加载对应 runtime.env
compose::load_env() {
  secret::reset
  secret::export_defaults
  export AIOT_ENV="${_CTX_ENV:-dev}"
  secret::load_file "${AIOT_ROOT_DIR}/compose/env/${AIOT_ENV}/runtime.env"
}
compose::config_test() {
  local mode="$1"
  env::ctx "$mode"
  compose::load_env
  log::section "compose config validation : $mode (env=${_CTX_ENV})"
  ( cd "$_CTX_WORKDIR" && comp_exec "${_COMPOSE_FILES[@]}" config --quiet ) && log::ok "compose $mode config valid"
}
compose::up() {
  local mode="$1" detached="${2:-true}"
  env::ctx "$mode"
  compose::load_env
  local detach_flag=""
  [[ "$detached" == "true" ]] && detach_flag="-d"
  log::banner "docker compose up [mode=${mode} env=${_CTX_ENV}]"
  ( cd "$_CTX_WORKDIR" && comp_exec "${_COMPOSE_FILES[@]}" up $detach_flag --remove-orphans )
}
compose::pull() {
  local mode="$1"
  env::ctx "$mode"
  compose::load_env
  log::section "docker compose pull [mode=${mode} env=${_CTX_ENV}]"
  ( cd "$_CTX_WORKDIR" && comp_exec "${_COMPOSE_FILES[@]}" pull ) || log::warn "pull failed (可能离线)，继续使用已有镜像"
}
compose::down() {
  local mode="$1" volumes="${2:-false}"
  env::ctx "$mode"
  compose::load_env
  local vol_flag=""
  [[ "$volumes" == "true" ]] && vol_flag="-v"
  log::banner "docker compose down [mode=${mode} env=${_CTX_ENV}] volumes=${volumes}"
  ( cd "$_CTX_WORKDIR" && comp_exec "${_COMPOSE_FILES[@]}" down $vol_flag --remove-orphans ) || true
}
compose::down_all() {
  local volumes="${1:-false}"
  for mode in infra main local ci admin-local prod staging staging-dev; do
    compose::down "$mode" "$volumes"
  done
  # prune dangling AIOT-labeled
  tool::docker image prune -f \
    --filter="label=org.opencontainers.image.source=https://github.com/aidencck/AIOT-java" \
    >/dev/null 2>&1 || true
  log::ok "all stacks stopped"
}

# -------- service registry（单一事实源：scripts/lib/services.json）--------
# 所有服务名/端口/健康探针/runtime-smoke 环境变量均从该 JSON 读取，
# 严禁在 CI 流程、rollback 流程、Makefile、aiotctl 中重复硬编码服务清单。
SERVICES_JSON="${AIOT_ROOT_DIR}/scripts/lib/services.json"

service::list() {
  python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
print(" ".join(s["name"] for s in data["services"]))
PY
}

service::port() {
  local name="$1"
  python3 - "$name" "${SERVICES_JSON}" <<'PY'
import json, sys
name, path = sys.argv[1], sys.argv[2]
data = json.load(open(path))
for s in data["services"]:
    if s["name"] == name:
        print(s["port"]); break
else:
    sys.exit(1)
PY
}

service::health_url() {
  local name="$1"
  local port
  port="$(service::port "$name")" || return 1
  echo "http://127.0.0.1:${port}/actuator/health/readiness"
}

service::needs_infra() {
  local name="$1"
  python3 - "$name" "${SERVICES_JSON}" <<'PY'
import json, sys
name, path = sys.argv[1], sys.argv[2]
data = json.load(open(path))
for s in data["services"]:
    if s["name"] == name:
        print("true" if s.get("needs_infra") else "false"); break
else:
    sys.exit(1)
PY
}

service::runtime_env() {
  # 输出 "K=V K2=V2 ..."（供 mvn spring-boot:run 前作为 env 前缀）
  local name="$1"
  python3 - "$name" "${SERVICES_JSON}" <<'PY'
import json, sys
name, path = sys.argv[1], sys.argv[2]
data = json.load(open(path))
for s in data["services"]:
    if s["name"] == name:
        print(" ".join(f"{k}={v}" for k, v in (s.get("runtime_env") or {}).items())); break
else:
    sys.exit(1)
PY
}

service::guard() {
  # 漂移检测：rollback.yml / docker-compose.yml / compose.base.yml 与 services.json 是否一致（含端口）
  python3 - "${AIOT_ROOT_DIR}" <<'PY'
import json, sys
from pathlib import Path
root = Path(sys.argv[1])
data = json.load(open(root / "scripts/lib/services.json"))
errors = []

rollback = (root / ".github/workflows/rollback.yml").read_text()
compose = (root / "docker-compose.yml").read_text()
base = (root / "compose/compose.base.yml").read_text()

for s in data["services"]:
    name, port = s["name"], s["port"]
    if f"- {name}" not in rollback:
        errors.append(f"rollback.yml missing option `- {name}`")
    if f"{name}:" not in compose:
        errors.append(f"docker-compose.yml missing service `{name}`")
    if f"SERVER_PORT: {port}" not in compose:
        errors.append(f"docker-compose.yml port drift for `{name}` (expect SERVER_PORT: {port})")
    if f"{name}:" not in base:
        errors.append(f"compose.base.yml missing service `{name}`")
    if f"SERVER_PORT: {port}" not in base:
        errors.append(f"compose.base.yml port drift for `{name}` (expect SERVER_PORT: {port})")

if errors:
    print("service registry drift detected:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("service registry in sync")
PY
}

# -------- build / verify helpers --------
build::jars() {
  log::banner "maven build (skip tests, parallel 1C)"
  tool::mvn clean package -DskipTests -T 1C "$@"
}
build::images() {
  local mode="${1:-local}"
  env::ctx "$mode"
  compose::load_env
  log::banner "docker compose build [mode=${mode} env=${_CTX_ENV}]"
  ( cd "$_CTX_WORKDIR" && comp_exec "${_COMPOSE_FILES[@]}" build --parallel )
}
verify::tcp() {
  local host="${1:-127.0.0.1}" port="$2" timeout="${3:-5}"
  python3 - "$host" "$port" "$timeout" <<'PY' 2>/dev/null
import socket, sys
s=socket.socket(); s.settimeout(int(sys.argv[3]))
try:
    s.connect((sys.argv[1], int(sys.argv[2]))); sys.exit(0)
except Exception: sys.exit(1)
finally:
    try: s.close()
    except: pass
PY
}
verify::health_poll() {
  # verify::health_poll mode deadline_sec ports...
  local mode="$1"; shift
  local deadline="$1"; shift
  local -a ports=("$@")
  local end=$((SECONDS + deadline))
  while (( SECONDS < end )); do
    local fail=0
    for p in "${ports[@]}"; do
      if ! verify::tcp 127.0.0.1 "$p" 3; then fail=$((fail+1)); fi
    done
    if (( fail == 0 )); then return 0; fi
    sleep 3; printf "."
  done
  echo
  return 1
}

# -------- args helpers --------
args::has_flag() {
  local needle="$1"; shift
  local a
  for a in "$@"; do [[ "$a" == "$needle" ]] && return 0; done
  return 1
}

# --- ensure sourced, not run directly ---
return 0 2>/dev/null || true
log::warn "common.sh is a library, do not execute directly"
exit 2
