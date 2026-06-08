#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${ROOT_DIR}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but not found."
  exit 1
fi

COMPOSE_CMD="docker compose"
if ! docker compose version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    echo "Docker Compose is required but not found."
    exit 1
  fi
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven (mvn) is required but not found."
  exit 1
fi

echo "Building jars (skip tests) ..."
mvn -B -T 1C -DskipTests package

echo "Starting stack with local-built images ..."
${COMPOSE_CMD} -f docker-compose.yml -f docker-compose.local.yml up -d --build --remove-orphans
${COMPOSE_CMD} -f docker-compose.yml -f docker-compose.local.yml ps

echo "Waiting for gateway readiness ..."
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:8080/actuator/health/readiness" >/dev/null 2>&1; then
    echo "Gateway is ready."
    exit 0
  fi
  sleep 2
done

echo "Gateway readiness timeout."
exit 1

