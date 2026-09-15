#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
SCENE="${1:-OFFLINE_FLAP}"
SOURCE="${2:-${AIOT_TRAINING_SOURCE:-mysql}}"
python3 -m aiot_training.exporters.export_runner --scene "${SCENE}" --source "${SOURCE}"
