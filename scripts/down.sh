#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl down <target> [--volumes]
echo "⚠️  $(basename "$0") → 已合并到 ./aiotctl down [infra|main|local|admin|ci|prod|staging|all] [--volumes]" >&2
echo "   示例：./aiotctl down infra ; ./aiotctl down all --volumes" >&2
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
exec ./aiotctl down "${1:-all}" "${2:-}" "$@"
