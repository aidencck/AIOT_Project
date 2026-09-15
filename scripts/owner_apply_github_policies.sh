#!/usr/bin/env bash
# ======================================================================
# Owner 一键收口：以仓库 admin 身份执行全部远端 GitHub 策略 + 自动验收。
# 把「分支保护 / secret push-protection / environment 审批 / discussions / webhook」
# 五类远端门禁一次性落地，并回读态势确认结果，避免人工逐项点 UI。
#
# 用法（需仓库 owner/admin 的 gh 会话）：
#   bash scripts/owner_apply_github_policies.sh
#
# 前置：gh auth login 使用具备 admin 权限的账号（owner aidencck）。
# 验收口径：脚本末尾 --status 中 branch_protection/secret_scanning/environment
#           不再为 DISABLED/n/a，且退出码为 0。
# ======================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETUP="$ROOT/scripts/github_repo_setup.sh"

echo "== 前置检查：gh 登录身份 =="
gh auth status 2>&1 | sed 's/^/    /' || echo "    （gh 未登录或 token 缺失，远端策略将无法生效）"

echo
echo "== [1/2] 执行远端策略（五类门禁）=="
"$SETUP" --all
apply_rc=$?

echo
echo "== [2/2] 验收：只读态势巡检 =="
"$SETUP" --status

echo
if [[ "$apply_rc" -eq 0 ]]; then
  echo "== 收口完成：所有远端策略已生效 =="
else
  echo "== 收口未完成：${apply_rc} 项失败（多为非 admin 账号导致 404），请用仓库 owner 的 gh 会话重跑本脚本 =="
fi
exit "$apply_rc"
