#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl dev up 或 aiotctl local up
echo "⚠️  $(basename "$0") → 已合并：" >&2
echo "     ghcr.io镜像:   ./aiotctl dev up" >&2
echo "     本地build镜像: ./aiotctl build all ; ./aiotctl local up --obs" >&2
echo "   本次默认走 dev up (主栈12容器)..." >&2
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
exec ./aiotctl dev up "$@"
