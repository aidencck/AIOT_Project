#!/usr/bin/env bash
set -euo pipefail

# 本地 dry-run GitHub Actions 门禁链（nektos/act）。
# 注意：docker-build（type=gha 缓存）与 deploy（SSH）无法在本地 act 中真实复现，
# act 仅覆盖 detect-changes → build-and-test 等门禁链 job。

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# 前置检查：act 是否已安装
if ! command -v act >/dev/null 2>&1; then
  echo "错误：未检测到 act，无法本地 dry-run CI。" >&2
  echo "安装方式：" >&2
  echo "  - brew install act（macOS Homebrew）" >&2
  echo "  - 或从 https://github.com/nektos/act/releases 下载预编译二进制" >&2
  exit 1
fi

# 读取第一个参数作为 job 名，默认跑 build-and-test
JOB="${1:-build-and-test}"

# GITHUB_TOKEN：docker push 类 job 需真实 token，本地门禁链用占位值即可
if [[ -z "${GITHUB_TOKEN:-}" ]]; then
  GITHUB_TOKEN="local-token"
  echo "警告：docker push 类 job 需真实 token，本地门禁链不受影响（未设置 GITHUB_TOKEN，使用占位 local-token）" >&2
fi

# --bind：绑定本地 docker 已拉取的镜像，避免重复拉取/拷贝
act -j "$JOB" --bind --env "GITHUB_TOKEN=${GITHUB_TOKEN}"
