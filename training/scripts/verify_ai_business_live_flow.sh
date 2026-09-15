#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
SCENE="${1:-OFFLINE_FLAP}"
shift || true

if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
else
  PYTHON_BIN="${AIOT_TRAINING_PYTHON:-python3}"
fi
export PYTHONPATH="$(pwd)/src${PYTHONPATH:+:${PYTHONPATH}}"

"${PYTHON_BIN}" -m aiot_training.live_proof.verify_business_flow --scene "${SCENE}" "$@"
