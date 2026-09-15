#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
SCENE="${1:-OFFLINE_FLAP}"
REQUIRE_NON_EMPTY="${2:-false}"
if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
else
  PYTHON_BIN="${AIOT_TRAINING_PYTHON:-python3}"
fi
export PYTHONPATH="$(pwd)/src${PYTHONPATH:+:${PYTHONPATH}}"

ARGS=(--scene "${SCENE}" --source mysql)
if [[ "${REQUIRE_NON_EMPTY}" == "true" ]]; then
  ARGS+=(--require-non-empty)
fi

"${PYTHON_BIN}" -m aiot_training.exporters.export_runner --scene "${SCENE}" --source mysql
"${PYTHON_BIN}" -m aiot_training.exporters.verify_export "${ARGS[@]}" --check-consistency
