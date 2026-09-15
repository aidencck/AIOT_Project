#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${ROOT_DIR}/artifacts/jvm-diagnostics"
mkdir -p "${OUT_DIR}"

usage() {
  cat <<'EOF'
Usage: jvm_diagnostics.sh --pid <pid> | --container <name> --action <action> [options]

Actions:
  thread-dump      打印全部线程栈
  class-histogram  按类统计对象数量
  gcutil           GC 利用率采样
  heap-dump        导出堆转储 .hprof

Options:
  --samples <n>     gcutil 采样次数（默认 5）
  --interval-ms <n> gcutil 采样间隔毫秒（默认 1000）

能力边界（重要）：
  --pid        目标为本地 JDK 进程（start_admin_local_jvm.sh 拉起），全工具可用。
  --container  目标为 docker 容器。运行时镜像已升级为 eclipse-temurin:17-jdk，
               含 jcmd/jstat/jmap（位于 /opt/java/openjdk/bin），全工具可用；
               thread-dump 仍用 kill -3 触发，heap-dump 在容器内生成后 docker cp 到 OUT_DIR。
EOF
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "缺少依赖命令: $1" >&2; exit 1; }
}

ACTION=""
PID=""
CONTAINER=""
SAMPLES=5
INTERVAL_MS=1000

while [[ $# -gt 0 ]]; do
  case "$1" in
    --action) ACTION="$2"; shift 2 ;;
    --pid) PID="$2"; shift 2 ;;
    --container) CONTAINER="$2"; shift 2 ;;
    --samples) SAMPLES="$2"; shift 2 ;;
    --interval-ms) INTERVAL_MS="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

[[ -z "${ACTION}" ]] && { echo "缺少 --action" >&2; usage; exit 1; }
[[ -n "${PID}" && -n "${CONTAINER}" ]] && { echo "--pid 与 --container 二选一" >&2; exit 1; }
[[ -z "${PID}" && -z "${CONTAINER}" ]] && { echo "需提供 --pid 或 --container" >&2; usage; exit 1; }

TS="$(date +%Y%m%d-%H%M%S)"

if [[ -n "${CONTAINER}" ]]; then
  docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}" || { echo "容器未运行: ${CONTAINER}" >&2; exit 1; }
  TARGET="${CONTAINER}"
else
  kill -0 "${PID}" 2>/dev/null || { echo "进程不存在: ${PID}" >&2; exit 1; }
  TARGET="${PID}"
fi

case "${ACTION}" in
  thread-dump)
    OUT="${OUT_DIR}/thread-dump-${TARGET}-${TS}.txt"
    if [[ -n "${CONTAINER}" ]]; then
      echo "触发 SIGQUIT 线程转储到容器 stdout，等待 2s 后采集 docker logs..."
      docker exec "${CONTAINER}" sh -c 'kill -3 1'
      sleep 2
      docker logs --tail 4000 "${CONTAINER}" > "${OUT}" 2>&1
    else
      require_cmd jstack
      jstack "${PID}" > "${OUT}" 2>&1
    fi
    echo "wrote ${OUT}"
    ;;
  class-histogram)
    OUT="${OUT_DIR}/class-histogram-${TARGET}-${TS}.txt"
    if [[ -n "${CONTAINER}" ]]; then
      docker exec "${CONTAINER}" jcmd 1 GC.class_histogram > "${OUT}" 2>&1
    else
      require_cmd jcmd
      jcmd "${PID}" GC.class_histogram > "${OUT}" 2>&1
    fi
    echo "wrote ${OUT}"
    ;;
  gcutil)
    OUT="${OUT_DIR}/gcutil-${TARGET}-${TS}.txt"
    if [[ -n "${CONTAINER}" ]]; then
      docker exec "${CONTAINER}" jstat -gcutil 1 "${INTERVAL_MS}" "${SAMPLES}" > "${OUT}" 2>&1
    else
      require_cmd jstat
      jstat -gcutil "${PID}" "${INTERVAL_MS}" "${SAMPLES}" > "${OUT}" 2>&1
    fi
    echo "wrote ${OUT}"
    ;;
  heap-dump)
    OUT="${OUT_DIR}/heap-dump-${TARGET}-${TS}.hprof"
    if [[ -n "${CONTAINER}" ]]; then
      NAME="heap-dump-${TARGET}-${TS}.hprof"
      docker exec "${CONTAINER}" jcmd 1 GC.heap_dump "/tmp/${NAME}"
      docker cp "${CONTAINER}:/tmp/${NAME}" "${OUT}"
    else
      require_cmd jcmd
      jcmd "${PID}" GC.heap_dump "${OUT}" > /dev/null 2>&1
    fi
    echo "wrote ${OUT}"
    ;;
  *)
    echo "未知 action: ${ACTION}" >&2; usage; exit 1 ;;
esac
