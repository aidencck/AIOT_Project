#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
SCENE="${1:-}"
MODE="${2:-${AIOT_BACKFILL_MODE:-apply}}"
if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
else
  PYTHON_BIN="${AIOT_TRAINING_PYTHON:-python3}"
fi
export PYTHONPATH="$(pwd)/src${PYTHONPATH:+:${PYTHONPATH}}"

ARGS=()
if [[ -n "${SCENE}" ]]; then
  ARGS+=(--scene "${SCENE}")
fi
if [[ "${MODE}" == "dry-run" ]]; then
  ARGS+=(--dry-run)
fi

"${PYTHON_BIN}" -m aiot_training.backfill.redis_to_mysql "${ARGS[@]}"
