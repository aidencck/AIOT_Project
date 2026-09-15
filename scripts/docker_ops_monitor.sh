#!/usr/bin/env bash
# ======================================================================
# AIoT docker/daemon 层可观测性运维脚本
#  - events    常驻监听 docker events（die/oom/health_status/restart）写 JSONL 留痕
#  - watermark 单次 docker system df 水位检查，超阈值输出 WARN 并退出码 1
# ======================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUDIT_DIR="${SCRIPT_DIR}/.release_state/audit"
WATERMARK_THRESHOLD="${DOCKER_OPS_WATERMARK_THRESHOLD:-80}"

usage() {
  cat <<'EOF'
用法:
  ./scripts/docker_ops_monitor.sh <subcommand>

子命令:
  events      常驻监听 docker events（die / oom / health_status / restart），
              将事件以 JSONL 追加写入 scripts/.release_state/audit/docker-events-<ts>.jsonl
              （每条含 ts / event / container / image / exit_code 字段）
  watermark   单次执行 docker system df --format json，解析镜像/卷/构建缓存占用，
              当 docker 数据占用超过磁盘总容量阈值（默认 80%）时输出 WARN 并退出码 1

选项:
  -h, --help  显示帮助

环境变量:
  DOCKER_OPS_WATERMARK_THRESHOLD  水位阈值百分比（默认 80）

示例:
  ./scripts/docker_ops_monitor.sh events      # 前台常驻监听，Ctrl-C 退出
  ./scripts/docker_ops_monitor.sh watermark   # 单次水位检查
EOF
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: 未检测到 docker，请先启动 Docker 并将 docker 加入 PATH" >&2
    exit 1
  fi
}

cmd_events() {
  local ts outfile
  ts="$(date +%Y%m%d%H%M%S)"
  outfile="${AUDIT_DIR}/docker-events-${ts}.jsonl"
  mkdir -p "${AUDIT_DIR}"

  echo "[INFO] docker events 常驻监听，事件: die / oom / health_status / restart"
  echo "[INFO] 留痕文件: ${outfile}"
  echo "[INFO] 按 Ctrl-C 停止监听"

  # docker events 逐行输出 JSON，交给 python 归一化为目标 JSONL 字段
  docker events \
    --filter 'type=container' \
    --filter 'event=die' \
    --filter 'event=oom' \
    --filter 'event=health_status' \
    --filter 'event=restart' \
    --format '{{json .}}' \
    | python3 -u -c 'import sys, json, datetime
out_path = sys.argv[1]
def iso(ev):
    t = ev.get("time")
    if isinstance(t, (int, float)):
        try:
            return datetime.datetime.fromtimestamp(int(t), tz=datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        except Exception:
            pass
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
with open(out_path, "a", encoding="utf-8") as f:
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            ev = json.loads(line)
        except Exception:
            continue
        actor = ev.get("Actor") or {}
        attrs = actor.get("Attributes") or {}
        rec = {
            "ts": iso(ev),
            "event": ev.get("status") or ev.get("Action") or "",
            "container": attrs.get("name", ""),
            "image": attrs.get("image", ""),
            "exit_code": attrs.get("exitCode", ""),
        }
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        f.flush()
' "$outfile"
}

cmd_watermark() {
  local df_json root_dir
  df_json="$(docker system df --format json)"
  root_dir="$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || true)"
  [[ -z "${root_dir}" ]] && root_dir="/var/lib/docker"

  python3 - "$df_json" "$root_dir" "$WATERMARK_THRESHOLD" <<'PY'
import json, sys, shutil, re

raw = sys.argv[1]
root = sys.argv[2]
threshold = float(sys.argv[3]) / 100.0

# docker system df --format json 对同一字段会同时输出数字与可读字符串（重复键），
# 取第一次出现的数字值作为字节数
def first_value(pairs):
    d = {}
    for k, v in pairs:
        if k not in d:
            d[k] = v
    return d

def parse_size(s):
    s = str(s).strip()
    if not s:
        return 0
    m = re.match(r"^([0-9.]+)\s*([kKmMgGtT]?i?[bB]?)?$", s)
    if not m:
        return 0
    num = float(m.group(1))
    unit = (m.group(2) or "").lower()
    mult = {
        "": 1, "b": 1,
        "k": 1000, "kb": 1000, "kib": 1024,
        "m": 1000 ** 2, "mb": 1000 ** 2, "mib": 1024 ** 2,
        "g": 1000 ** 3, "gb": 1000 ** 3, "gib": 1024 ** 3,
        "t": 1000 ** 4, "tb": 1000 ** 4, "tib": 1024 ** 4,
    }
    return int(num * mult.get(unit, 1))

def as_int(v):
    if isinstance(v, bool):
        return int(v)
    if isinstance(v, (int, float)):
        return int(v)
    if isinstance(v, str):
        return parse_size(v)
    return 0

def human(n):
    size = float(n)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if size < 1000 or unit == "TB":
            return "%.2f %s" % (size, unit)
        size /= 1000
    return str(n)

images_bytes = 0
volumes_bytes = 0
buildcache_bytes = 0

# docker system df --format json 输出 JSONL：每行一个对象，含 Type/Size 字段，
# 按 Type 归并 Images / Local Volumes / Build Cache 三类占用（Containers 不计入）
for line in raw.splitlines():
    line = line.strip()
    if not line:
        continue
    try:
        obj = json.loads(line, object_pairs_hook=first_value)
    except Exception:
        continue
    t = (obj.get("Type") or "").strip()
    size = as_int(obj.get("Size", 0))
    if t == "Images":
        images_bytes += size
    elif t == "Local Volumes":
        volumes_bytes += size
    elif t == "Build Cache":
        buildcache_bytes += size

docker_used = images_bytes + volumes_bytes + buildcache_bytes

print("[watermark] images      = %s" % human(images_bytes))
print("[watermark] volumes     = %s" % human(volumes_bytes))
print("[watermark] build cache = %s" % human(buildcache_bytes))
print("[watermark] docker 占用 = %s" % human(docker_used))

try:
    total, used, _free = shutil.disk_usage(root)
    disk_label = root
except Exception:
    total, used, _free = shutil.disk_usage("/")
    disk_label = "/"

docker_ratio = (docker_used / total) if total else 0
disk_ratio = (used / total) if total else 0

print("[watermark] 磁盘(%s) 总容量 %s，docker 占用占比 %.2f%%，磁盘整体使用率 %.2f%%"
      % (disk_label, human(total), docker_ratio * 100, disk_ratio * 100))

if docker_ratio > threshold:
    print("[WARN] docker 磁盘占用占比 %.2f%% 超过 %.0f%% 水位阈值" % (docker_ratio * 100, threshold * 100), file=sys.stderr)
    sys.exit(1)

print("[OK] docker 磁盘占用占比 %.2f%% 在 %.0f%% 水位阈值以内" % (docker_ratio * 100, threshold * 100))
sys.exit(0)
PY
}

main() {
  local cmd="${1:-}"
  case "$cmd" in
    events)
      require_docker
      cmd_events
      ;;
    watermark)
      require_docker
      cmd_watermark
      ;;
    -h|--help|"")
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知子命令: ${cmd}" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
