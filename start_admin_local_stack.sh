#!/usr/bin/env bash
# LEGACY COMPAT WRAPPER → FORWARD TO ./aiotctl
# 本文件已合并到 aiotctl admin，保留仅为兼容旧文档/习惯
echo "⚠️  $(basename "$0") 已合并为工程化单入口: ./aiotctl admin up" >&2
echo "   请更新脚本引用；本次为兼容继续执行一次..." >&2
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"
exec ./aiotctl admin up "$@"
