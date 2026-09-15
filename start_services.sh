#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl dev up
# 已合并: ./aiotctl dev up  或  ./aiotctl dev up --verify
echo "⚠️  $(basename "$0") 已合并为工程化单入口: ./aiotctl dev up" >&2
echo "   如需健康验证: ./aiotctl verify main" >&2
echo "   本次为兼容继续执行..." >&2
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"
exec ./aiotctl dev up "$@"
