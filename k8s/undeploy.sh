#!/usr/bin/env bash
set -euo pipefail

ENV=${1:-dev}
NAMESPACE="aiot"

case "${ENV}" in
  dev|staging|prod) ;;
  *) echo "[ERROR] Invalid env: ${ENV}"; exit 1 ;;
esac

echo "[WARN] Uninstalling aiot-platform from ${ENV} (namespace: ${NAMESPACE})"
helm uninstall aiot-platform --namespace "${NAMESPACE}" || true

echo "[INFO] Remaining pods:"
kubectl get pods --namespace "${NAMESPACE}" || true
