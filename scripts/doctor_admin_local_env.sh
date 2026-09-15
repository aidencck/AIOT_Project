#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

DOCKER_TIMEOUT_SECONDS="${DOCKER_TIMEOUT_SECONDS:-8}"
MIN_FREE_KB_WARN="${MIN_FREE_KB_WARN:-524288}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/admin-local-doctor}"
LOG_FILE="${ARTIFACT_DIR}/doctor.log"

mkdir -p "${ARTIFACT_DIR}"
: > "${LOG_FILE}"

print_check() {
  local level="$1"
  local message="$2"
  echo "[${level}] ${message}" | tee -a "${LOG_FILE}"
}

run_with_timeout() {
  local timeout_seconds="$1"
  shift
  "$@" &
  local pid=$!
  local deadline=$(( $(date +%s) + timeout_seconds ))
  while kill -0 "${pid}" >/dev/null 2>&1; do
    if (( $(date +%s) >= deadline )); then
      kill "${pid}" >/dev/null 2>&1 || true
      wait "${pid}" 2>/dev/null || true
      return 124
    fi
    sleep 1
  done
  wait "${pid}"
}

require_command() {
  local cmd="$1"
  if command -v "${cmd}" >/dev/null 2>&1; then
    print_check "OK" "command available: ${cmd}"
  else
    print_check "ERROR" "missing command: ${cmd}"
    return 1
  fi
}

check_disk() {
  local free_kb
  free_kb="$(df -k "${ROOT_DIR}" | awk 'NR==2 {print $4}')"
  if [[ -n "${free_kb}" && "${free_kb}" -lt "${MIN_FREE_KB_WARN}" ]]; then
    print_check "WARN" "low disk space: ${free_kb} KB available under ${ROOT_DIR}"
  else
    print_check "OK" "disk space looks acceptable: ${free_kb} KB available under ${ROOT_DIR}"
  fi
}

check_env_file() {
  if [[ ! -f "${ROOT_DIR}/.env" ]]; then
    print_check "ERROR" ".env is missing"
    return 1
  fi

  set -a
  source "${ROOT_DIR}/.env"
  set +a

  local required_envs=(
    MYSQL_PASSWORD
    AIOT_JWT_SECRET
    AIOT_INTERNAL_TOKEN
    EMQX_API_USER
    EMQX_API_PASSWORD
  )
  local missing=0
  for key in "${required_envs[@]}"; do
    if [[ -z "${!key:-}" ]]; then
      print_check "ERROR" "env is empty: ${key}"
      missing=1
    else
      print_check "OK" "env is set: ${key}"
    fi
  done
  if [[ "${missing}" != "0" ]]; then
    return 1
  fi
}

check_docker_daemon() {
  if ! command -v docker >/dev/null 2>&1; then
    print_check "ERROR" "docker command not found"
    return 1
  fi

  local output_file="${ARTIFACT_DIR}/docker-info.log"
  : > "${output_file}"
  if run_with_timeout "${DOCKER_TIMEOUT_SECONDS}" bash -lc "docker info > '${output_file}' 2>&1"; then
    print_check "OK" "docker daemon is reachable"
    return 0
  fi

  local status=$?
  if [[ "${status}" == "124" ]]; then
    print_check "ERROR" "docker daemon check timed out after ${DOCKER_TIMEOUT_SECONDS}s"
  else
    print_check "ERROR" "docker daemon is unavailable"
  fi
  if [[ -f "${output_file}" ]]; then
    tail -n 40 "${output_file}" | tee -a "${LOG_FILE}" >/dev/null
  fi
  return 1
}

main() {
  local failed=0

  require_command bash || failed=1
  require_command curl || failed=1
  require_command mvn || failed=1
  require_command docker || failed=1

  check_disk || true
  check_env_file || failed=1
  check_docker_daemon || failed=1

  if [[ "${failed}" != "0" ]]; then
    print_check "SUMMARY" "admin local doctor failed, see ${LOG_FILE}"
    exit 1
  fi
  print_check "SUMMARY" "admin local doctor passed"
}

main
