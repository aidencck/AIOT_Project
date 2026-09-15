#!/usr/bin/env bash
# ======================================================================
# 部署前 jar/镜像与项目配置一致性校验（可作为 G4 前置门禁独立运行）
# 对 services.json 中的 8 个业务服务（排除 aiot-admin-web）做 5 维校验：
#   D1 jar 存在 / D2 jar 新鲜度 / D3 配置一致 / D4 Maven 元数据 / D5 镜像一致
# 任一 FAIL 汇总后退出码 = 1。
# ======================================================================
set -Eeuo pipefail

source "$(dirname "$0")/lib/common.sh"

pass_count=0
warn_count=0
fail_count=0

declare -a services=()
env_name=""
strict=false
skip_image=false

usage_note() {
  cat >&2 <<'EOF'
usage: verify_artifact_consistency.sh [--service svc] [--env env] [--strict] [--skip-image]
  --service svc   可重复或逗号分隔；默认全部 8 个服务
  --env env       加载 compose/env/<env>/runtime.env（决定 IMAGE_TAG）
  --strict        IMAGE_TAG 为 git sha 且与 HEAD 不符时，从 WARN 升级为 FAIL
  --skip-image    跳过 D5 镜像一致性校验
EOF
}

while (( $# > 0 )); do
  case "$1" in
    --service)
      shift
      [[ -n "${1:-}" ]] || log::fatal "--service 需要一个服务名"
      services+=("$1")
      ;;
    --env)
      shift
      [[ -n "${1:-}" ]] || log::fatal "--env 需要一个环境名"
      env_name="$1"
      ;;
    --strict) strict=true ;;
    --skip-image) skip_image=true ;;
    -h|--help) usage_note; exit 0 ;;
    *)
      # 兼容 aiotctl 的 `artifact-check [env]` 位置参数写法
      if [[ -z "$env_name" ]]; then
        env_name="$1"
      else
        log::fatal "未知参数: $1"
      fi
      ;;
  esac
  shift
done

# 展开逗号分隔的 --service
if (( ${#services[@]} > 0 )); then
  declare -a expanded=()
  for s in "${services[@]}"; do
    IFS=',' read -r -a parts <<< "$s"
    expanded+=("${parts[@]}")
  done
  services=("${expanded[@]}")
else
  read -r -a services <<< "$(service::list)"
fi

if [[ -n "$env_name" ]]; then
  env::ctx "$env_name"
  compose::load_env
fi
image_tag="${IMAGE_TAG:-main}"

WORK_TMP="$(mktemp -d)"
trap 'rm -rf "$WORK_TMP"' EXIT

# ---- 结果记录 ----
_record() {
  local kind="$1" msg="$2"
  case "$kind" in
    PASS) pass_count=$((pass_count+1)); log::ok "$msg" ;;
    WARN) warn_count=$((warn_count+1)); log::warn "$msg" ;;
    FAIL) fail_count=$((fail_count+1)); log::error "$msg" ;;
  esac
}

# ---- D1：jar 存在（排除 -sources / -javadoc）----
_find_jar() {
  local svc="$1" f
  for f in "${AIOT_ROOT_DIR}/${svc}/target"/*.jar; do
    [[ -e "$f" ]] || continue
    case "$f" in
      *-sources.jar|*-javadoc.jar) continue ;;
    esac
    printf '%s\n' "$f"
    return 0
  done
  return 1
}

# ---- D2：jar 新鲜度（jar mtime >= src 最新文件 mtime）----
_freshness() {
  python3 - "$1" "$2" <<'PY'
import os, sys
jar, src = sys.argv[1], sys.argv[2]
jm = int(os.path.getmtime(jar))
best = 0
for dp, dn, fn in os.walk(src):
    for n in fn:
        p = os.path.join(dp, n)
        try:
            t = os.path.getmtime(p)
        except OSError:
            continue
        if t > best:
            best = t
print("PASS" if jm >= best else "FAIL")
PY
}

# ---- D3：配置一致（字节一致 PASS / 规范化一致 WARN / 否则 FAIL）----
_cfg_compare() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, zipfile
jar, entry, src = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    jar_bytes = zipfile.ZipFile(jar).read(entry)
except KeyError:
    print("MISSING")
    sys.exit(0)
with open(src, "rb") as f:
    src_bytes = f.read()
if jar_bytes == src_bytes:
    print("PASS")
    sys.exit(0)
def norm(b):
    out = []
    for raw in b.decode("utf-8", errors="replace").splitlines():
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.lstrip().startswith("#"):
            continue
        out.append(line)
    return "\n".join(out).encode("utf-8")
print("WARN" if norm(jar_bytes) == norm(src_bytes) else "FAIL")
PY
}

# ---- D3b：application-common.yml 位于 BOOT-INF/lib/aiot-common-*.jar 嵌套层，需二次解包 ----
_cfg_compare_common() {
  python3 - "$1" "$2" <<'PY'
import sys, zipfile, io
jar, src = sys.argv[1], sys.argv[2]
zf = zipfile.ZipFile(jar)
nested = next((n for n in zf.namelist() if n.startswith("BOOT-INF/lib/aiot-common-") and n.endswith(".jar")), None)
if nested is None:
    print("MISSING")
    sys.exit(0)
try:
    jar_bytes = zipfile.ZipFile(io.BytesIO(zf.read(nested))).read("application-common.yml")
except KeyError:
    print("MISSING")
    sys.exit(0)
with open(src, "rb") as f:
    src_bytes = f.read()
if jar_bytes == src_bytes:
    print("PASS")
    sys.exit(0)
def norm(b):
    out = []
    for raw in b.decode("utf-8", errors="replace").splitlines():
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.lstrip().startswith("#"):
            continue
        out.append(line)
    return "\n".join(out).encode("utf-8")
print("WARN" if norm(jar_bytes) == norm(src_bytes) else "FAIL")
PY
}

# ---- D4：Maven 元数据（pom.properties vs pom.xml）----
_pom_artifact() {
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for child in root:
    if child.tag.endswith("artifactId"):
        print(child.text.strip() if child.text else "")
        sys.exit(0)
sys.exit(1)
PY
}

_pom_check() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, zipfile
jar, pom_path, expected_artifact = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    data = zipfile.ZipFile(jar).read(pom_path).decode("utf-8")
except KeyError:
    print("FAIL missing pom.properties")
    sys.exit(0)
props = {}
for line in data.splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
g = props.get("groupId")
a = props.get("artifactId")
v = props.get("version")
if g == "com.aiot.cloud" and a == expected_artifact and v == "1.0.0-SNAPSHOT":
    print("PASS")
else:
    print("FAIL group=%s artifact=%s version=%s" % (g, a, v))
PY
}

# ---- D5 辅助：sha256 ----
_jar_entry_sha256() {
  python3 - "$1" "$2" <<'PY'
import sys, zipfile, hashlib
jar, entry = sys.argv[1], sys.argv[2]
try:
    data = zipfile.ZipFile(jar).read(entry)
except KeyError:
    sys.exit(1)
print(hashlib.sha256(data).hexdigest())
PY
}

_file_sha256() {
  python3 - "$1" <<'PY'
import sys, hashlib, pathlib
print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
}

# ---- D5：镜像一致性 ----
_check_image() {
  local svc="$1" jar="$2"
  local image="ghcr.io/aidencck/aiot-java/${svc}:${image_tag}"

  if [[ "$image_tag" =~ ^[0-9a-fA-F]{7,40}$ ]]; then
    local short_head tag_lower head_lower
    short_head="$( (cd "$AIOT_ROOT_DIR" && git rev-parse --short HEAD 2>/dev/null) || true )"
    if [[ -n "$short_head" ]]; then
      tag_lower="$(printf '%s' "$image_tag" | tr '[:upper:]' '[:lower:]')"
      head_lower="$(printf '%s' "$short_head" | tr '[:upper:]' '[:lower:]')"
      if [[ "$tag_lower" != "$head_lower" ]]; then
        if $strict; then
          _record FAIL "D5 git tag=${image_tag} != HEAD=${short_head}"
        else
          _record WARN "D5 git tag=${image_tag} != HEAD=${short_head}"
        fi
      else
        _record PASS "D5 git tag=${image_tag} == HEAD=${short_head}"
      fi
    else
      _record WARN "D5 无法获取 git HEAD，跳过 tag 校验"
    fi
  fi

  tool::probe
  if ! docker_exec image inspect "$image" >/dev/null 2>&1; then
    _record WARN "D5 镜像不存在，跳过: $image"
    return
  fi

  local local_sha cid img_sha
  if ! local_sha="$(_jar_entry_sha256 "$jar" "BOOT-INF/classes/application.yml")"; then
    _record FAIL "D5 本地 jar 缺少 BOOT-INF/classes/application.yml"
    return
  fi

  cid=""
  if cid="$(docker_exec create "$image" 2>/dev/null)"; then
    if docker_exec cp "${cid}:/app/BOOT-INF/classes/application.yml" "${WORK_TMP}/${svc}.app.yml" 2>/dev/null; then
      img_sha="$(_file_sha256 "${WORK_TMP}/${svc}.app.yml")"
      if [[ "$local_sha" == "$img_sha" ]]; then
        _record PASS "D5 镜像与本地 jar 的 application.yml sha256 一致 ($img_sha)"
      else
        _record FAIL "D5 镜像 application.yml sha256=$img_sha != 本地 jar=$local_sha"
      fi
    else
      _record WARN "D5 无法提取镜像内 /app/BOOT-INF/classes/application.yml"
    fi
    docker_exec rm "$cid" >/dev/null 2>&1 || true
  else
    _record WARN "D5 docker create 失败: $image"
  fi
}

# ---- 单服务 5 维校验 ----
_check_service() {
  local svc="$1"
  local svc_dir="${AIOT_ROOT_DIR}/${svc}"
  local jar

  log::section "verify service: ${svc}"

  # D1
  if jar="$(_find_jar "$svc")"; then
    _record PASS "D1 jar 存在: ${jar#${AIOT_ROOT_DIR}/}"
  else
    _record FAIL "D1 jar 缺失: ${svc}/target 下无 *.jar"
    return
  fi

  # D2
  if [[ "$(_freshness "$jar" "${svc_dir}/src")" == "PASS" ]]; then
    _record PASS "D2 jar 新鲜度: jar mtime >= src 最新文件"
  else
    _record FAIL "D2 jar 新鲜度: 产物过期 (jar mtime < src 最新文件)"
  fi

  # D3
  case "$(_cfg_compare "$jar" "BOOT-INF/classes/application.yml" "${svc_dir}/src/main/resources/application.yml")" in
    PASS) _record PASS "D3 application.yml: 与源码字节一致" ;;
    WARN) _record WARN "D3 application.yml: 与源码仅格式差异（空行/注释/行尾空白）" ;;
    MISSING|FAIL) _record FAIL "D3 application.yml: 与源码不一致或缺失" ;;
  esac

  case "$(_cfg_compare_common "$jar" "${AIOT_ROOT_DIR}/aiot-common/src/main/resources/application-common.yml")" in
    PASS) _record PASS "D3 application-common.yml: 与源码字节一致" ;;
    WARN) _record WARN "D3 application-common.yml: 与源码仅格式差异" ;;
    MISSING|FAIL) _record FAIL "D3 application-common.yml: 与源码不一致或缺失" ;;
  esac

  # D4
  local artifact pom_path
  artifact="$(_pom_artifact "${svc_dir}/pom.xml")"
  pom_path="META-INF/maven/com.aiot.cloud/${artifact}/pom.properties"
  case "$(_pom_check "$jar" "$pom_path" "$artifact")" in
    PASS) _record PASS "D4 Maven 元数据: group/artifact/version 一致" ;;
    *)    _record FAIL "D4 Maven 元数据漂移" ;;
  esac

  # D5
  if $skip_image; then
    log::info "D5 镜像一致性: skipped (--skip-image)"
  else
    _check_image "$svc" "$jar"
  fi
}

# ---- 主流程 ----
log::banner "ARTIFACT CONSISTENCY CHECK"
log::info "services=${services[*]} env=${env_name:-<inherit>} strict=$strict skip_image=$skip_image image_tag=$image_tag"

for svc in "${services[@]}"; do
  [[ -n "$svc" ]] || continue
  _check_service "$svc"
done

log::banner "SUMMARY"
log::info "通过=$pass_count 警告=$warn_count 失败=$fail_count"
if (( fail_count > 0 )); then
  log::fatal "artifact consistency check FAILED: $fail_count failure(s)"
fi
log::ok "artifact consistency check passed"
