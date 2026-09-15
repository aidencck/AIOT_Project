#!/usr/bin/env bash
set -euo pipefail

ENV=${1:-dev}
NAMESPACE="aiot"
CHART_DIR="$(cd "$(dirname "$0")/helm" && pwd)"
VALUES_FILE="${CHART_DIR}/values-${ENV}.yaml"

if [ ! -f "${VALUES_FILE}" ]; then
  VALUES_FILE="${CHART_DIR}/values.yaml"
fi

echo "[INFO] Env: ${ENV}, Values: ${VALUES_FILE}"

case "${ENV}" in
  dev|staging|prod) ;;
  *) echo "[ERROR] Invalid env: ${ENV}. Use: dev|staging|prod"; exit 1 ;;
esac

# 云相关字段非空门禁：storageClass / imageRegistry 未覆盖会导致 PVC Pending / ImagePullBackOff。
RENDERED="$(helm template aiot-platform "${CHART_DIR}" --values "${VALUES_FILE}" --namespace "${NAMESPACE}")"
if echo "${RENDERED}" | grep -q 'storageClassName: ""'; then
  echo "[ERROR] global.storageClass is empty. Override it in values-${ENV}.yaml (gp3/managed-premium/standard-rwo/alicloud-disk-essd/cbs)."
  exit 1
fi
if echo "${RENDERED}" | grep -q 'image: ".*/aiot-gateway:'; then
  if [ -z "$(echo "${RENDERED}" | grep -o 'image: "[^/]*/aiot-gateway:' | head -1)" ]; then
    echo "[ERROR] global.imageRegistry is empty. Override it in values-${ENV}.yaml."
    exit 1
  fi
fi

echo "[1/5] Create namespace ${NAMESPACE}"
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "[2/5] Apply monitoring ConfigMaps (from ./monitoring)"
kubectl create configmap prometheus-config --namespace "${NAMESPACE}" \
  --from-file=./monitoring/prometheus/prometheus.yml \
  --from-file=alerts=./monitoring/prometheus/alerts \
  --dry-run=client -o yaml | kubectl apply -f - || true

kubectl create configmap alertmanager-config --namespace "${NAMESPACE}" \
  --from-file=./monitoring/alertmanager/alertmanager.yml \
  --dry-run=client -o yaml | kubectl apply -f - || true

kubectl create configmap loki-config --namespace "${NAMESPACE}" \
  --from-file=./monitoring/loki/loki.yml \
  --dry-run=client -o yaml | kubectl apply -f - || true

kubectl create configmap promtail-config --namespace "${NAMESPACE}" \
  --from-file=./monitoring/promtail/promtail.yml \
  --dry-run=client -o yaml | kubectl apply -f - || true

kubectl create configmap tempo-config --namespace "${NAMESPACE}" \
  --from-file=./monitoring/tempo/tempo.yml \
  --dry-run=client -o yaml | kubectl apply -f - || true

kubectl create configmap grafana-provisioning --namespace "${NAMESPACE}" \
  --from-file=./monitoring/grafana/provisioning \
  --dry-run=client -o yaml | kubectl apply -f - || true

kubectl create configmap mysql-init-sql --namespace "${NAMESPACE}" \
  --from-file=./docker/mysql/init \
  --dry-run=client -o yaml | kubectl apply -f - || true

echo "[3/5] Install/Upgrade Helm release aiot-platform"
helm upgrade --install aiot-platform "${CHART_DIR}" \
  --namespace "${NAMESPACE}" \
  --values "${VALUES_FILE}" \
  --timeout 15m0s \
  --wait

echo "[4/5] Verify workloads"
kubectl get pods --namespace "${NAMESPACE}" -o wide

echo "[5/5] Ingress endpoints"
kubectl get ingress --namespace "${NAMESPACE}"

echo "[DONE] AIoT platform deployed to ${ENV}."
