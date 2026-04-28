#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "Checking layering rules..."

violations="$(grep -R --line-number --include='*.java' \
  -E 'import com\.aiot\.(auth|home|device|rule)\.mapper\.' \
  aiot-*/src/main/java/*/aiot/*/controller \
  aiot-*/src/main/java/*/aiot/*/service 2>/dev/null || true)"

if [[ -n "${violations}" ]]; then
  echo "Layering check failed: controller/service must not import mapper package."
  echo "${violations}"
  exit 1
fi

echo "Layering check passed."
