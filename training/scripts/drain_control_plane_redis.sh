#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
MODE="${1:-dry-run}"
if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
else
  PYTHON_BIN="${AIOT_TRAINING_PYTHON:-python3}"
fi
export PYTHONPATH="$(pwd)/src${PYTHONPATH:+:${PYTHONPATH}}"

ARGS=()
case "${MODE}" in
  dry-run)
    ARGS+=(--dry-run)
    ;;
  apply)
    ;;
  drain)
    ARGS+=(--delete-redis)
    ;;
  *)
    echo "Unsupported mode: ${MODE}. Use dry-run|apply|drain" >&2
    exit 1
    ;;
esac

"${PYTHON_BIN}" -m aiot_training.backfill.drain_control_plane_redis "${ARGS[@]}"
