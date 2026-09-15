#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
python3 -m aiot_training.builders.build_sft --scene "${1:-OFFLINE_FLAP}"
python3 -m aiot_training.builders.build_eval --scene "${1:-OFFLINE_FLAP}"
