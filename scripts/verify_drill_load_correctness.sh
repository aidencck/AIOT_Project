#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法:
  ./scripts/verify_drill_load_correctness.sh -s <service> --phase <baseline|recovery> --trace-id <id> [选项]

必填参数:
  -s, --service            目标服务名（用于时间线标识）
      --phase              演练阶段：baseline | recovery
      --trace-id           演练 Trace ID

可选参数:
      --users              压测用户数（默认 50）
      --devices-per-user   每用户设备数（默认 2）
      --load-concurrency   负载并发线程数（默认 50）
      --seed-concurrency   种子并发线程数（默认 12）
      --rounds             压测轮次（默认 1）
      --base-gateway       API 网关（默认 http://127.0.0.1:8080）
      --base-prom          Prometheus（默认 http://127.0.0.1:9090）
      --base-loki          Loki（默认 http://127.0.0.1:3100）
      --base-tempo         Tempo（默认 http://127.0.0.1:3200）
      --artifact-dir       报告目录（默认 scripts/.release_state/drill-load）
      --summary-file       写出一份机器可读的指标摘要 JSON（供演练做 baseline/recovery 对比）
      --operator           执行人（默认 $USER）
      --skip-observability-precheck  跳过可观测栈就绪预检
  -h, --help               显示帮助

说明:
  复用 perf_user_device_observability.py 的「负载压测 + 业务正确性 + 可观测性」能力，
  对演练环境做冒烟级运行负载（吞吐/延迟）与正确性（数据链路一致）验证，并以演练时间线
  一致的结构化事件输出结果。
  返回码：0=通过；2=可观测栈不可用已软降级跳过；1=验证失败。
EOF
}

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

SERVICE=""
PHASE="baseline"
TRACE_ID=""
OPERATOR="${USER:-unknown}"

USERS="50"
DEVICES_PER_USER="2"
LOAD_CONCURRENCY="50"
SEED_CONCURRENCY="12"
ROUNDS="1"

BASE_GATEWAY="http://127.0.0.1:8080"
BASE_PROM="http://127.0.0.1:9090"
BASE_LOKI="http://127.0.0.1:3100"
BASE_TEMPO="http://127.0.0.1:3200"

ARTIFACT_DIR="${ROOT_DIR}/scripts/.release_state/drill-load"
SUMMARY_FILE=""
SKIP_OBS_PRECHECK="0"

json_escape() {
  local raw="${1:-}"
  raw="${raw//\\/\\\\}"
  raw="${raw//\"/\\\"}"
  raw="${raw//$'\n'/\\n}"
  printf '%s' "${raw}"
}

emit_event() {
  local level="$1"
  local stage="$2"
  local status="$3"
  local message="$4"
  local ts
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "{\"ts\":\"${ts}\",\"trace_id\":\"$(json_escape "${TRACE_ID}")\",\"service\":\"$(json_escape "${SERVICE}")\",\"action\":\"load_correctness_verify\",\"phase\":\"$(json_escape "${PHASE}")\",\"stage\":\"$(json_escape "${stage}")\",\"level\":\"$(json_escape "${level}")\",\"status\":\"$(json_escape "${status}")\",\"operator\":\"$(json_escape "${OPERATOR}")\",\"message\":\"$(json_escape "${message}")\"}"
}

# 从压测报告抽取关键指标，写出机器可读摘要，供演练 baseline/recovery 对比。
write_summary() {
  local status="$1"
  if [[ -z "${SUMMARY_FILE}" || ! -f "${REPORT_FILE}" ]]; then
    return 0
  fi
  python3 - "${REPORT_FILE}" "${SUMMARY_FILE}" "${PHASE}" "${SERVICE}" "${TRACE_ID}" "${status}" "${USERS}" "${DEVICES_PER_USER}" "${LOAD_CONCURRENCY}" <<'PYEOF'
import json, sys
report_file, summary_file, phase, service, trace_id, status, users, dpu, lc = sys.argv[1:10]
d = json.load(open(report_file))
load = d.get("load", {})
summary = {
    "phase": phase,
    "service": service,
    "trace_id": trace_id,
    "status": status,
    "scenario": d.get("scenario"),
    "users": int(users),
    "devices_per_user": int(dpu),
    "load_concurrency": int(lc),
    "throughput_rps": load.get("throughput_rps", 0),
    "latency_p50_sec": load.get("latency_p50_sec", 0),
    "latency_p95_sec": load.get("latency_p95_sec", 0),
    "latency_p99_sec": load.get("latency_p99_sec", 0),
    "success_ratio": load.get("success_ratio", 0),
    "http_5xx": load.get("http_5xx", 0),
    "report_file": report_file,
}
json.dump(summary, open(summary_file, "w"), ensure_ascii=False, indent=2)
PYEOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--service)
      SERVICE="${2:-}"
      shift 2
      ;;
    --phase)
      PHASE="${2:-}"
      shift 2
      ;;
    --trace-id)
      TRACE_ID="${2:-}"
      shift 2
      ;;
    --operator)
      OPERATOR="${2:-}"
      shift 2
      ;;
    --users)
      USERS="${2:-}"
      shift 2
      ;;
    --devices-per-user)
      DEVICES_PER_USER="${2:-}"
      shift 2
      ;;
    --load-concurrency)
      LOAD_CONCURRENCY="${2:-}"
      shift 2
      ;;
    --seed-concurrency)
      SEED_CONCURRENCY="${2:-}"
      shift 2
      ;;
    --rounds)
      ROUNDS="${2:-}"
      shift 2
      ;;
    --base-gateway)
      BASE_GATEWAY="${2:-}"
      shift 2
      ;;
    --base-prom)
      BASE_PROM="${2:-}"
      shift 2
      ;;
    --base-loki)
      BASE_LOKI="${2:-}"
      shift 2
      ;;
    --base-tempo)
      BASE_TEMPO="${2:-}"
      shift 2
      ;;
    --artifact-dir)
      ARTIFACT_DIR="${2:-}"
      shift 2
      ;;
    --summary-file)
      SUMMARY_FILE="${2:-}"
      shift 2
      ;;
    --skip-observability-precheck)
      SKIP_OBS_PRECHECK="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: 未知参数: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ -z "${SERVICE}" || -z "${TRACE_ID}" ]]; then
  echo "ERROR: --service 与 --trace-id 为必填参数"
  usage
  exit 1
fi

if [[ "${PHASE}" != "baseline" && "${PHASE}" != "recovery" ]]; then
  echo "ERROR: --phase 仅支持 baseline | recovery"
  usage
  exit 1
fi

for v in USERS DEVICES_PER_USER LOAD_CONCURRENCY SEED_CONCURRENCY ROUNDS; do
  if ! [[ "${!v}" =~ ^[0-9]+$ ]]; then
    echo "ERROR: ${v} 必须为正整数"
    exit 1
  fi
done

if ! command -v python3 >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: 需要 python3 与 curl"
  exit 1
fi

emit_event "INFO" "init" "running" "开始运行负载与正确性冒烟验证"

# 可观测栈是负载正确性校验的硬前提：Prometheus/Loki/Tempo 任一不可用将导致压测脚本
# 在指标采集阶段异常，故预检就绪性，缺失时给出明确失败信号而非让压测中途崩溃。
if [[ "${SKIP_OBS_PRECHECK}" != "1" ]]; then
  emit_event "INFO" "observability_precheck" "running" "预检可观测栈就绪性"
  obs_endpoints="prometheus|${BASE_PROM}/-/healthy loki|${BASE_LOKI}/ready tempo|${BASE_TEMPO}/ready"
  for entry in ${obs_endpoints}; do
    name="${entry%%|*}"
    url="${entry#*|}"
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "${url}" || true)"
    if [[ "${code}" != "200" ]]; then
      emit_event "WARN" "observability_precheck" "skipped" "${name} 不可用 (${url}, http=${code})，软降级跳过负载与正确性验证"
      echo "WARN: 可观测栈未就绪（${name} -> ${url}, http=${code}），软降级跳过负载/正确性验证"
      exit 2
    fi
  done
  emit_event "INFO" "observability_precheck" "success" "可观测栈就绪"
fi

SCENARIO="${SERVICE}-${PHASE}-${TRACE_ID}"
mkdir -p "${ARTIFACT_DIR}"

emit_event "INFO" "pressure" "running" "执行负载压测与业务/可观测链路校验"

set +e
python3 scripts/perf_user_device_observability.py \
  --users "${USERS}" \
  --devices-per-user "${DEVICES_PER_USER}" \
  --seed-concurrency "${SEED_CONCURRENCY}" \
  --load-concurrency "${LOAD_CONCURRENCY}" \
  --rounds "${ROUNDS}" \
  --base-gateway "${BASE_GATEWAY}" \
  --base-prom "${BASE_PROM}" \
  --base-loki "${BASE_LOKI}" \
  --base-tempo "${BASE_TEMPO}" \
  --scenario "${SCENARIO}" \
  --artifact-dir "${ARTIFACT_DIR}"
PRESSURE_RC=$?
set -e

REPORT_FILE="${ARTIFACT_DIR}/${SCENARIO}/report.json"
if [[ ! -f "${REPORT_FILE}" ]]; then
  emit_event "ERROR" "pressure" "failed" "压测报告缺失: ${REPORT_FILE}"
  echo "ERROR: 压测报告未生成: ${REPORT_FILE}"
  exit 1
fi

REPORT_STATUS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["status"])' "${REPORT_FILE}" 2>/dev/null || echo "unknown")"
THROUGHPUT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["load"]["throughput_rps"])' "${REPORT_FILE}" 2>/dev/null || echo "0")"
SUCCESS_RATIO="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["load"]["success_ratio"])' "${REPORT_FILE}" 2>/dev/null || echo "0")"
P95="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["load"]["latency_p95_sec"])' "${REPORT_FILE}" 2>/dev/null || echo "0")"
FAILED_CHECKS="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(",".join(k for k,v in d["checks"].items() if not v))' "${REPORT_FILE}" 2>/dev/null || echo "unknown")"

if [[ "${PRESSURE_RC}" -eq 0 && "${REPORT_STATUS}" == "passed" ]]; then
  write_summary "passed"
  emit_event "INFO" "pressure" "success" "负载与正确性验证通过: rps=${THROUGHPUT}, p95=${P95}s, success_ratio=${SUCCESS_RATIO}"
  echo "负载与正确性验证通过: phase=${PHASE}, rps=${THROUGHPUT}, p95=${P95}s, success_ratio=${SUCCESS_RATIO}"
  exit 0
fi

write_summary "failed"
emit_event "ERROR" "pressure" "failed" "负载与正确性验证失败: rc=${PRESSURE_RC}, status=${REPORT_STATUS}, failed_checks=${FAILED_CHECKS}, rps=${THROUGHPUT}, p95=${P95}s, success_ratio=${SUCCESS_RATIO}"
echo "ERROR: 负载与正确性验证失败: phase=${PHASE}, rc=${PRESSURE_RC}, status=${REPORT_STATUS}, failed_checks=${FAILED_CHECKS}"
echo "报告: ${REPORT_FILE}"
exit 1
