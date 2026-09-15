#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
SCENE="${1:-OFFLINE_FLAP}"
MAX_TOTAL_MISMATCH="${2:-0}"
MIN_WRITTEN_TOTAL="${3:-1}"
if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
else
  PYTHON_BIN="${AIOT_TRAINING_PYTHON:-python3}"
fi
export PYTHONPATH="$(pwd)/src${PYTHONPATH:+:${PYTHONPATH}}"

"${PYTHON_BIN}" -m aiot_training.backfill.migration_gate \
  --scene "${SCENE}" \
  --max-total-mismatch "${MAX_TOTAL_MISMATCH}" \
  --min-written-total "${MIN_WRITTEN_TOTAL}"
