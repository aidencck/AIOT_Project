#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

START_STACK="${START_STACK:-true}"
STOP_AFTER_VERIFY="${STOP_AFTER_VERIFY:-false}"
AUTO_GENERATE_HISTORY="${AUTO_GENERATE_HISTORY:-true}"
AUTO_GENERATE_HISTORY_SCENE="${AUTO_GENERATE_HISTORY_SCENE:-OFFLINE_FLAP}"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify_admin_local_ai_persistence.sh"
START_SCRIPT="${ROOT_DIR}/scripts/start_admin_local_jvm.sh"
STOP_SCRIPT="${ROOT_DIR}/scripts/stop_admin_local_jvm.sh"

if [[ ! -x "${VERIFY_SCRIPT}" ]]; then
  echo "ERROR: verify script is missing or not executable: ${VERIFY_SCRIPT}"
  exit 1
fi

if [[ "${START_STACK}" == "true" && ! -x "${START_SCRIPT}" ]]; then
  echo "ERROR: start script is missing or not executable: ${START_SCRIPT}"
  exit 1
fi

if [[ "${STOP_AFTER_VERIFY}" == "true" && ! -x "${STOP_SCRIPT}" ]]; then
  echo "ERROR: stop script is missing or not executable: ${STOP_SCRIPT}"
  exit 1
fi

cleanup() {
  if [[ "${STOP_AFTER_VERIFY}" == "true" ]]; then
    echo "Stopping admin local JVM stack because STOP_AFTER_VERIFY=true"
    "${STOP_SCRIPT}" || true
  fi
}

trap cleanup EXIT

if [[ "${START_STACK}" == "true" ]]; then
  echo "[1/2] Start admin local JVM stack"
  "${START_SCRIPT}"
else
  echo "[1/2] Reuse existing admin local JVM stack"
fi

echo "[2/2] Verify admin local AI persistence flow"
AUTO_GENERATE_HISTORY="${AUTO_GENERATE_HISTORY}" \
AUTO_GENERATE_HISTORY_SCENE="${AUTO_GENERATE_HISTORY_SCENE}" \
"${VERIFY_SCRIPT}"

echo "Admin local JVM verification finished."
