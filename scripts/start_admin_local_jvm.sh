#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl admin up
echo "⚠️  $(basename "$0") → 已合并到:  ./aiotctl admin up  （单入口）" >&2
echo "   后续体检:  ./aiotctl verify admin" >&2
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
exec bash scripts/start_admin_local_jvm.sh "$@"  # 本文件为legacy runner，aiotctl admin另有compose模式
