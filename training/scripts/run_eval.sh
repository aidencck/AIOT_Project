#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
python3 -m aiot_training.evals.regression_gate --scene "${1:-OFFLINE_FLAP}"
