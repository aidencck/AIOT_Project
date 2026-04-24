#!/bin/bash
set -euo pipefail

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

IMAGE_TAG="${IMAGE_TAG:-main}"
export IMAGE_TAG

echo "Deploying with Docker Compose only (IMAGE_TAG=${IMAGE_TAG})..."
${COMPOSE_CMD} pull
${COMPOSE_CMD} up -d --remove-orphans
${COMPOSE_CMD} ps

echo "Deployment finished. Services are running in Docker containers."
