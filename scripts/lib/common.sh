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
      [[ -n "$k" ]] || continue
      # 安全密钥/密码为外部注入（shell/CI/k8s），非 runtime.env 契约变量；不得 reset，
      # 否则 pass-through 自引用（KEY=${KEY}）会展开为空，被误判为缺失。
      [[ "$k" == "AIOT_JWT_SECRET" || "$k" == "AIOT_INTERNAL_TOKEN" || "$k" == "AIOT_EMQX_WEBHOOK_SECRET" \
        || "$k" == "MYSQL_PASSWORD" || "$k" == "EMQX_API_PASSWORD" || "$k" == "GRAFANA_ADMIN_PASSWORD" ]] && continue
      unset "$k" 2>/dev/null || true
    done < <(sed -nE 's/^([A-Za-z_][A-Za-z0-9_]*)=.*/\1/p' "$f")
  done
  # 兜底：覆盖「仅以注释形式存在于 .env.example」的工具链自管理变量（安全密钥/密码已在上方跳过，此处不再 unset）
  unset AIOT_ENV IMAGE_TAG SPRING_PROFILES_ACTIVE \
        EMQX_API_USER GRAFANA_ADMIN_USER \
        AIOT_OPENAPI_PUBLIC_ENABLED AUTH_WEBHOOK_URL OLLAMA_MODEL \
        AI_LLM_ENABLED AI_LLM_BASE_URL AI_LLM_API_KEY AI_LLM_MODEL \
        AI_LLM_NUM_PREDICT AI_LLM_NUM_CTX AI_LLM_TIMEOUT_MS \
        2>/dev/null || true
}
secret::export_defaults() {
  # 仅非敏感项给默认值；账号用户名非机密，保留默认。
  export EMQX_API_USER="${EMQX_API_USER:-admin}"
  export GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
  # 安全红线：所有密码/密钥（MYSQL/EMQX/GRAFANA 密码、JWT、内部令牌、webhook 密钥）
  # 禁止可预测默认值，缺失由 secret::require_secrets 阻断（fail-fast）。
  export AIOT_ENV="${AIOT_ENV:-dev}"
  export IMAGE_TAG="${IMAGE_TAG:-main}"
}
# 从 runtime.env / .env 叠加（仅存在时）
secret::load_file() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  log::info "Load env from ${f#${AIOT_ROOT_DIR}/}"
  set -a
  # 临时关闭 nounset：runtime.env 内 ${VAR} 自引用占位（密钥/密码由外部注入），
  # 未注入时按空值展开，交由 secret::require_secrets 统一 fail-fast。
  set +u
  # shellcheck disable=SC1090
  source "$f"
  set -u
  set +a
}

# 安全红线校验：必填密钥/密码缺失即 fail-fast（禁止可预测默认值兜底）
# GRAFANA_ADMIN_PASSWORD 不在此列：grafana 仅在 observability profile 启动，缺省由 compose 空值处理。
secret::require_secrets() {
  local name missing=""
  for name in AIOT_JWT_SECRET AIOT_INTERNAL_TOKEN AIOT_EMQX_WEBHOOK_SECRET MYSQL_PASSWORD EMQX_API_PASSWORD; do
    [[ -n "${!name:-}" ]] || missing+=" ${name}"
  done
  [[ -z "$missing" ]] || log::fatal "Missing required secrets:${missing}（请从 compose/env/${AIOT_ENV}/.env.example 复制为 runtime.env 并填值）"
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
  secret::require_secrets
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
    --filter="label=org.opencontainers.image.source=https://github.com/aidencck/AIOT_Project" \
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

# infra 端口单一事实源读取（mysql/redis/emqx/nacos/ollama），禁止在 aiotctl/脚本硬编码
infra::port() {
  # 用法: infra::port <infra_name> [field]   field 默认 port（emqx 支持 api_port）
  local name="$1" field="${2:-port}"
  python3 - "$name" "$field" "${SERVICES_JSON}" <<'PY'
import json, sys
name, field, path = sys.argv[1], sys.argv[2], sys.argv[3]
data = json.load(open(path))
print(data["infra"][name][field])
PY
}

# CI 宿主偏移端口（environments.ci.host_ports），用于 ci 模式健康轮询
env::ci_host_ports() {
  python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
print(" ".join(str(v) for v in data["environments"]["ci"]["host_ports"].values()))
PY
}

# 模式 → TCP 健康轮询端口清单（单进程派生，避免多次 python 启动）
# 用法: topology::ports <mode>  输出换行分隔端口；infra/ci 偏移端口从 SSOT 读取，禁止硬编码
topology::ports() {
  local mode="$1"
  python3 - "$mode" "${SERVICES_JSON}" <<'PY'
import json, sys
mode, path = sys.argv[1], sys.argv[2]
data = json.load(open(path))
infra = data["infra"]
svcs = data["services"]
def sport(name):
    for s in svcs:
        if s["name"] == name:
            return s["port"]
    raise KeyError(name)
if mode == "infra":
    ports = [infra["mysql"]["port"], infra["redis"]["port"], infra["emqx"]["port"], infra["emqx"]["api_port"]]
elif mode == "ci":
    ports = [infra["mysql"]["port"], infra["redis"]["port"], infra["emqx"]["port"], sport("aiot-gateway")]
    ports += list(data["environments"]["ci"]["host_ports"].values())
else:
    ports = [infra["mysql"]["port"], infra["redis"]["port"], infra["emqx"]["port"]]
    ports += [s["port"] for s in svcs]
print("\n".join(str(p) for p in ports))
PY
}

# doctor 端口占用检查清单：mysql/redis/emqx(主+api)/nacos/gateway，单进程派生
topology::doctor_ports() {
  python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
infra = data["infra"]
ports = [infra["mysql"]["port"], infra["redis"]["port"], infra["emqx"]["port"], infra["emqx"]["api_port"], infra["nacos"]["port"]]
for s in data["services"]:
    if s["name"] == "aiot-gateway":
        ports.append(s["port"]); break
print("\n".join(str(p) for p in ports))
PY
}

service::guard() {
  # 漂移检测：rollback.yml / docker-compose.yml / compose.base.yml / k8s values.yaml 与 services.json 是否一致（含端口）
  # 覆盖范围：业务服务清单 + 业务端口 + k8s 端口 + infra 端口。overlay/文档端口由 scripts/gen_topology.py --check 负责。
  python3 - "${AIOT_ROOT_DIR}" <<'PY'
import json, sys, re
from pathlib import Path
root = Path(sys.argv[1])
data = json.load(open(root / "scripts/lib/services.json"))
errors = []

rollback = (root / ".github/workflows/rollback.yml").read_text()
compose = (root / "docker-compose.yml").read_text()
base = (root / "compose/compose.base.yml").read_text()
values = (root / "k8s/helm/values.yaml").read_text()

# S7 门禁：JWT 直传绕过开关仅限本地 overlay（docker-compose.local.yml / admin-local.yml），
# 严禁泄漏到 base/staging/prod compose 或 helm values（否则伪造 JWT 可绕过鉴权直连服务）。
_bypass_guard = [
    root / "docker-compose.yml",
    root / "compose/compose.base.yml",
    root / "compose/compose.staging.yml",
    root / "compose/compose.prod.yml",
    root / "k8s/helm/values.yaml",
    root / "k8s/helm/values-prod.yaml",
]
for _fp in _bypass_guard:
    if _fp.exists() and "AIOT_SECURITY_ALLOW_DIRECT_USER_JWT" in _fp.read_text():
        errors.append(f"JWT bypass flag (AIOT_SECURITY_ALLOW_DIRECT_USER_JWT) leaked into {_fp.name}")

K8S_KEYS = {
    "aiot-gateway": "gateway",
    "aiot-device-service": "deviceService",
    "aiot-auth-service": "authService",
    "aiot-home-service": "homeService",
    "aiot-rule-engine": "ruleEngine",
    "aiot-shadow-service": "shadowService",
    "aiot-mqtt-adapter": "mqttAdapter",
    "aiot-data-parser": "dataParser",
}

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
    # 业务服务必须在 compose.base.yml 配置 deploy.resources.limits（内存/CPU）
    blk = re.search(rf'(?ms)^  {re.escape(name)}:\n(.*?)(?=^  \S|\Z)', base)
    block = blk.group(1) if blk else ""
    if not (re.search(r'(?m)^\s+deploy:\s*$', block)
            and re.search(r'(?m)^\s+limits:\s*$', block)
            and re.search(r'(?m)^\s+memory:', block)
            and re.search(r'(?m)^\s+cpus:', block)):
        errors.append(f"compose.base.yml missing deploy.resources.limits for `{name}`")
    key = K8S_KEYS[name]
    m = re.search(rf'^  {re.escape(key)}:\n(?:(?!^  \S).)*?^    port:\s*(\d+)', values, re.M | re.S)
    if not m:
        errors.append(f"values.yaml missing service port `{key}`")
    elif m.group(1) != str(port):
        errors.append(f"values.yaml port drift for `{key}` (expect {port}, got {m.group(1)})")

# infra 端口：compose 中必须出现主端口映射（mysql/redis/emqx/nacos/ollama）
for k, v in data["infra"].items():
    port = v["port"]
    if f"{port}:{port}" not in compose and f"{port}:" not in compose:
        errors.append(f"docker-compose.yml missing infra `{k}` port {port}")

if errors:
    print("service registry drift detected:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("service registry in sync")
PY
}

# -------- GitHub 协作 SSOT（单一事实源：scripts/lib/services.json）--------
# repo/owner/repo_url/image_source 唯一在此维护；Dockerfile 的 image.source、
# CI 的镜像 prune filter、scripts 播种脚本的 REPO 默认值、以及 mcp_GitHub 仓库定位
# 均以此为准，禁止二次硬编码。
github::repo()         { python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["github"]["repo"])
PY
}
github::owner()        { python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["github"]["owner"])
PY
}
github::repo_url()     { python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["github"]["repo_url"])
PY
}
github::image_source() { python3 - "${SERVICES_JSON}" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["github"]["image_source"])
PY
}

# github::guard：仓库标识漂移检测。拦截两类历史硬伤：
#   ① org.opencontainers.image.source 指向非 SSOT 仓库；
#   ② 出现任何「非 SSOT 仓库」的 github.com/<owner>/<repo> 引用（含 AIOT-java 历史 bug 模式）。
# 只扫描主仓 tracked 文件（git grep），构建产物 target/ 与 .wiki-sync 独立镜像仓不在主仓 tracked 内。
github::guard() {
  python3 - "${AIOT_ROOT_DIR}" <<'PY'
import json, re, subprocess, sys
from pathlib import Path
root = Path(sys.argv[1])
g = json.load(open(root / "scripts/lib/services.json"))["github"]
repo_name = g["repo"].split("/")[-1]
allowed = {repo_name, repo_name + ".wiki"}
errors = []

def grep(pattern):
    p = subprocess.run(["git", "-C", str(root), "grep", "-n", "--", pattern],
                       text=True, capture_output=True)
    return p.stdout

# ① image.source 必须与 SSOT 一致
for line in grep("org.opencontainers.image.source").splitlines():
    m = re.search(r"org\.opencontainers\.image\.source=([A-Za-z0-9:./_-]+)", line)
    if m and m.group(1) != g["image_source"]:
        errors.append(f"image.source drift: {line}")

# ② 任何 github.com/<owner>/<repo> 引用必须是 SSOT 仓库（或其 wiki）
seen = set()
for line in grep("github.com/" + g["owner"] + "/").splitlines():
    for m in re.finditer(r"github\.com/" + re.escape(g["owner"]) + r"/([A-Za-z0-9_.-]+)", line):
        seen.add(m.group(1))
for r in sorted(seen - allowed):
    errors.append(f"unknown repo `{r}` (expected one of {sorted(allowed)})")

# ③ SSOT 自洽（repo_url/image_source 必须由 repo 派生）
expected = f"https://github.com/{g['repo']}"
if g["repo_url"] != expected or g["image_source"] != expected:
    errors.append(f"SSOT github.* URL mismatch: {g}")

if errors:
    print("github registry drift detected:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("github registry in sync")
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
# 批量端口扫描：单 python 进程并发检查所有端口，消除「N 端口 × 串行超时」的最坏延迟。
# 用法: verify::tcp_scan <timeout> <port> [port...]
# 输出 "port=ok|fail"（每行）；退出码 0=全部可达，1=存在不可达。
verify::tcp_scan() {
  local timeout="${1:-3}"; shift
  python3 - "$timeout" "$@" <<'PY' 2>/dev/null
import socket, sys, concurrent.futures
timeout = int(sys.argv[1]); ports = sys.argv[2:]
def check(p):
    s = socket.socket(); s.settimeout(timeout)
    ok = False
    try:
        s.connect(("127.0.0.1", int(p))); ok = True
    except Exception:
        ok = False
    finally:
        try: s.close()
        except Exception: pass
    return p, ok
fails = 0
workers = min(16, max(1, len(ports)))
with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as ex:
    for p, ok in ex.map(check, ports):
        print(f"{p}={'ok' if ok else 'fail'}")
        if not ok: fails += 1
sys.exit(1 if fails else 0)
PY
}
verify::health_poll() {
  # verify::health_poll mode deadline_sec ports...
  local mode="$1"; shift
  local deadline="$1"; shift
  local -a ports=("$@")
  local end=$((SECONDS + deadline))
  while (( SECONDS < end )); do
    if verify::tcp_scan 3 "${ports[@]}" >/dev/null; then return 0; fi
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
