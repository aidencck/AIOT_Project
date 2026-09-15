#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${ROOT_DIR}/aiot-admin-web"

if ! command -v npm >/dev/null 2>&1; then
  echo "ERROR: npm not found"
  exit 1
fi

if [[ ! -d "${APP_DIR}" ]]; then
  echo "ERROR: aiot-admin-web module not found"
  exit 1
fi

cd "${APP_DIR}"

if [[ ! -d node_modules ]]; then
  npm install
fi

npm run dev -- --host 0.0.0.0 --port 5173
