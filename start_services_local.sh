#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl build all ; ./aiotctl local up
# 已合并: ./aiotctl build all ; ./aiotctl local up
echo "⚠️  $(basename "$0") 已合并为工程化单入口:" >&2
echo "     ./aiotctl build all  →  Maven + Docker分层build" >&2
echo "     ./aiotctl local up   →  本地镜像up + TCP轮询健康" >&2
echo "   本次为兼容继续执行..." >&2
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"
./aiotctl build all
exec ./aiotctl local up "$@"
