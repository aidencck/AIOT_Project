#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_DIR="${PID_DIR:-${ROOT_DIR}/artifacts/admin-local-jvm/pids}"

if [[ ! -d "${PID_DIR}" ]]; then
  echo "No PID directory found: ${PID_DIR}"
  exit 0
fi

stop_pid_file() {
  local pid_file="$1"
  local name
  local pid

  name="$(basename "${pid_file}" .pid)"
  pid="$(cat "${pid_file}")"

  if [[ -z "${pid}" ]]; then
    rm -f "${pid_file}"
    return 0
  fi

  if ! kill -0 "${pid}" >/dev/null 2>&1; then
    echo "${name} is not running"
    rm -f "${pid_file}"
    return 0
  fi

  echo "Stopping ${name} (${pid})"
  kill "${pid}" >/dev/null 2>&1 || true
  for _ in $(seq 1 20); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      rm -f "${pid_file}"
      return 0
    fi
    sleep 1
  done

  echo "Force killing ${name} (${pid})"
  kill -9 "${pid}" >/dev/null 2>&1 || true
  rm -f "${pid_file}"
}

for pid_file in \
  "${PID_DIR}/aiot-device-service.pid" \
  "${PID_DIR}/aiot-rule-engine.pid" \
  "${PID_DIR}/aiot-home-service.pid"
do
  if [[ -f "${pid_file}" ]]; then
    stop_pid_file "${pid_file}"
  fi
done

echo "Admin local JVM processes stopped."
