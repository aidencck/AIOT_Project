#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8082}"
WEBHOOK_PATH="${WEBHOOK_PATH:-/api/v1/emqx/webhook}"
SECRET="${AIOT_EMQX_WEBHOOK_SECRET:-}"

TOTAL_REQUESTS="${1:-2000}"
CONCURRENCY="${2:-50}"
DEVICE_POOL_SIZE="${3:-500}"
REPORT_FILE="${4:-/tmp/device_status_perf_report.txt}"

if [[ -z "${SECRET}" ]]; then
  echo "ERROR: AIOT_EMQX_WEBHOOK_SECRET is required."
  echo "Usage: AIOT_EMQX_WEBHOOK_SECRET=xxx $0 [total_requests] [concurrency] [device_pool_size] [report_file]"
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl not found."
  exit 1
fi

if ! command -v xxd >/dev/null 2>&1; then
  echo "ERROR: xxd not found."
  exit 1
fi

TARGET_URL="${BASE_URL%/}${WEBHOOK_PATH}"
TMP_DIR="$(mktemp -d)"
START_EPOCH="$(date +%s)"

echo "Start perf test: url=${TARGET_URL}, total=${TOTAL_REQUESTS}, concurrency=${CONCURRENCY}, device_pool=${DEVICE_POOL_SIZE}"

run_once() {
  local i="$1"
  local action="client.connected"
  if (( i % 2 == 0 )); then
    action="client.disconnected"
  fi
  local ts
  ts="$(date +%s)"
  local device_id
  device_id="perf-device-$(( i % DEVICE_POOL_SIZE ))"
  local client_id
  client_id="perf-client-$i"
  local payload
  payload="${action}.${client_id}.${device_id}.${ts}"
  local signature
  signature="$(printf "%s" "${payload}" | openssl dgst -sha256 -hmac "${SECRET}" -binary | xxd -p -c 256)"
  local body
  body="$(printf '{"action":"%s","clientid":"%s","username":"%s","timestamp":%s}' "${action}" "${client_id}" "${device_id}" "${ts}")"

  local result
  result="$(
    curl -sS -o /dev/null -w "%{http_code} %{time_total}" \
      -X POST "${TARGET_URL}" \
      -H "Content-Type: application/json" \
      -H "x-emqx-signature: ${signature}" \
      --data "${body}" || echo "000 0"
  )"
  echo "${result}" >> "${TMP_DIR}/results.txt"
}

export -f run_once
export TARGET_URL SECRET DEVICE_POOL_SIZE TMP_DIR

seq 1 "${TOTAL_REQUESTS}" | xargs -I{} -P "${CONCURRENCY}" bash -c 'run_once "$@"' _ {}

END_EPOCH="$(date +%s)"
DURATION_SEC="$(( END_EPOCH - START_EPOCH ))"
if (( DURATION_SEC <= 0 )); then
  DURATION_SEC=1
fi

TOTAL_DONE="$(wc -l < "${TMP_DIR}/results.txt" | tr -d ' ')"
SUCCESS_2XX="$(awk '$1 ~ /^2/ {count++} END {print count+0}' "${TMP_DIR}/results.txt")"
HTTP_401="$(awk '$1 == "401" {count++} END {print count+0}' "${TMP_DIR}/results.txt")"
HTTP_5XX="$(awk '$1 ~ /^5/ {count++} END {print count+0}' "${TMP_DIR}/results.txt")"
FAILED_NET="$(awk '$1 == "000" {count++} END {print count+0}' "${TMP_DIR}/results.txt")"
QPS=$(( TOTAL_DONE / DURATION_SEC ))

awk '{print $2}' "${TMP_DIR}/results.txt" | sort -n > "${TMP_DIR}/latencies_sorted.txt"

calc_percentile() {
  local p="$1"
  awk -v p="${p}" '
    { a[NR] = $1 }
    END {
      if (NR == 0) {
        print "0"
        exit
      }
      rank = int((p / 100) * (NR - 1)) + 1
      if (rank < 1) rank = 1
      if (rank > NR) rank = NR
      printf "%.6f", a[rank]
    }' "${TMP_DIR}/latencies_sorted.txt"
}

LAT_AVG_SEC="$(awk '{sum += $1} END {if (NR == 0) print "0"; else printf "%.6f", sum / NR}' "${TMP_DIR}/latencies_sorted.txt")"
LAT_P50_SEC="$(calc_percentile 50)"
LAT_P95_SEC="$(calc_percentile 95)"
LAT_P99_SEC="$(calc_percentile 99)"
STATUS_BREAKDOWN="$(awk '{count[$1]++} END {for (code in count) printf "%s=%d\n", code, count[code]}' "${TMP_DIR}/results.txt" | sort)"

{
  echo "=== Device Status Webhook Perf Report ==="
  echo "target_url=${TARGET_URL}"
  echo "total_requests=${TOTAL_REQUESTS}"
  echo "concurrency=${CONCURRENCY}"
  echo "device_pool_size=${DEVICE_POOL_SIZE}"
  echo "duration_sec=${DURATION_SEC}"
  echo "throughput_qps=${QPS}"
  echo "total_done=${TOTAL_DONE}"
  echo "success_2xx=${SUCCESS_2XX}"
  echo "http_401=${HTTP_401}"
  echo "http_5xx=${HTTP_5XX}"
  echo "network_failed_000=${FAILED_NET}"
  echo "latency_avg_sec=${LAT_AVG_SEC}"
  echo "latency_p50_sec=${LAT_P50_SEC}"
  echo "latency_p95_sec=${LAT_P95_SEC}"
  echo "latency_p99_sec=${LAT_P99_SEC}"
  echo "status_breakdown_start"
  echo "${STATUS_BREAKDOWN}"
  echo "status_breakdown_end"
  echo "timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
} | tee "${REPORT_FILE}"

rm -rf "${TMP_DIR}"

echo "Perf test completed. Report: ${REPORT_FILE}"
