#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
python3 -m aiot_training.trainers.qlora_entry --scene "${1:-OFFLINE_FLAP}"
