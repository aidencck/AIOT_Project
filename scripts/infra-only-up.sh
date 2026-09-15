#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl infra up
# 已合并为 aiotctl infra up；保留兼容引用
echo "⚠️  $(basename "$0") → 已合并到 ./aiotctl infra up（单入口工程化）" >&2
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
exec ./aiotctl infra up "$@"
