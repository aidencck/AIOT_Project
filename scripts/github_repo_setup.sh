#!/usr/bin/env bash
# ======================================================================
# 统一 GitHub 仓库级远程配置入口（幂等）。
# 整合五类仓库设置，替代散落的单功能脚本：
#   1) 分支保护        --branch-protection   (PUT branches/{branch}/protection)
#   2) secret scanning --secret-scanning     (PATCH repos/{repo} security_and_analysis)
#   3) Discussions     --discussions         (PATCH repos/{repo} has_discussions)
#   4) Webhooks        --webhooks            (POST repos/{repo}/hooks)
#   5) 环境审批        --environment         (PUT repos/{repo}/environments/{env})
# 前置：gh 已登录且对目标仓库具备 admin 权限（否则 404/403）。
# 用法：
#   ./scripts/github_repo_setup.sh --all
#   ./scripts/github_repo_setup.sh --secret-scanning --discussions
#   ./scripts/github_repo_setup.sh --status     # 只读巡检，无需 admin，输出当前态势
# 参数（环境变量覆盖）：
#   GITHUB_REPO     目标仓库（默认读 SSOT）
#   GITHUB_OWNER    仓库 owner（默认读 SSOT）
#   GITHUB_BRANCH   分支保护目标分支（默认 main）
#   GITHUB_ENV_NAME 环境名（默认 production）
#   WEBHOOK_URL     Webhook 接收地址（--webhooks 必填，缺省则跳过该项）
#   WEBHOOK_SECRET  Webhook 校验密钥（可选）
# ======================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh" 2>/dev/null || true

REPO="${GITHUB_REPO:-$(github::repo 2>/dev/null || echo 'aidencck/AIOT_Project')}"
BRANCH="${GITHUB_BRANCH:-main}"
OWNER="${GITHUB_OWNER:-$(github::owner 2>/dev/null || echo 'aidencck')}"
ENV_NAME="${GITHUB_ENV_NAME:-production}"

# 只读态势巡检：用 GET 拉取仓库设置，无需 admin，用于「配置→校验→报告 drift」闭环。
# 输出当前值，供 owner 判断是否与期望（分支保护 1 审批、secret scanning 开启等）存在漂移。
repo_status() {
  echo "== GitHub 仓库安全态势（只读，无需 admin）: ${REPO} =="

  # 1. 分支保护（404 = 未配置 或 无 admin 读取权限，两者均按未生效标注）
  local bp
  if bp="$(gh api "repos/${REPO}/branches/${BRANCH}/protection" \
       --jq '"enabled (required_reviews=" + (.required_pull_request_reviews.required_approving_review_count|tostring) + ")"' 2>/dev/null)"; then
    echo "    branch_protection: ${bp}"
  else
    echo "    branch_protection: DISABLED（或当前账号无 admin 读取权限）"
  fi

  # 2. secret scanning + push protection（无 admin 时 security_and_analysis 为 null）
  local ss
  if ss="$(gh api "repos/${REPO}" --jq '
      if .security_and_analysis == null then "secret_scanning: n/a（无 admin 权限）"
      else "secret_scanning=" + (.security_and_analysis.secret_scanning.status|tostring)
        + "  push_protection=" + (.security_and_analysis.secret_scanning_push_protection.status|tostring)
      end' 2>/dev/null)"; then
    echo "    ${ss}"
  else
    echo "    secret_scanning: n/a（读取失败）"
  fi

  # 3. Discussions
  local disc
  if disc="$(gh api "repos/${REPO}" --jq '"has_discussions=" + (.has_discussions|tostring)' 2>/dev/null)"; then
    echo "    ${disc}"
  else
    echo "    has_discussions: n/a"
  fi

  # 4. Webhooks（list 需 admin；404 时抑制原始错误体）
  local hooks
  if hooks="$(gh api "repos/${REPO}/hooks" --jq '.[]?.config.url' 2>/dev/null)"; then
    if [[ -z "$hooks" ]]; then
      echo "    webhooks: none"
    else
      echo "    webhooks:"
      echo "$hooks" | sed 's/^/        - /'
    fi
  else
    echo "    webhooks: n/a（需 admin 读取权限）"
  fi

  # 5. environment 审批（production 环境 + 保护规则）
  local env_info
  if env_info="$(gh api "repos/${REPO}/environments/${ENV_NAME}" --jq '"environment=" + .name + " protection_rules=" + ((.protection_rules // []) | length | tostring)' 2>/dev/null)"; then
    echo "    ${env_info}"
  else
    echo "    environment(${ENV_NAME}): 未配置（需 admin）"
  fi
}

DO_ALL=false; DO_BRANCH=false; DO_SECRET=false; DO_DISCUSSIONS=false; DO_WEBHOOKS=false; DO_ENV=false; DO_STATUS=false
for arg in "$@"; do
  case "$arg" in
    --all) DO_ALL=true ;;
    --branch-protection) DO_BRANCH=true ;;
    --secret-scanning) DO_SECRET=true ;;
    --discussions) DO_DISCUSSIONS=true ;;
    --webhooks) DO_WEBHOOKS=true ;;
    --environment) DO_ENV=true ;;
    --status|--check) DO_STATUS=true ;;
  esac
done
if [[ "$DO_ALL" == true ]]; then
  DO_BRANCH=true; DO_SECRET=true; DO_DISCUSSIONS=true; DO_WEBHOOKS=true; DO_ENV=true
fi
if [[ "$DO_STATUS" == true ]]; then
  repo_status
  exit 0
fi
if [[ "$DO_BRANCH$DO_SECRET$DO_DISCUSSIONS$DO_WEBHOOKS$DO_ENV" == "falsefalsefalsefalsefalse" ]]; then
  echo "用法: $0 [--all | --branch-protection | --secret-scanning | --discussions | --webhooks | --environment | --status]"
  exit 1
fi

failures=0

# ---- 1. 分支保护 ----
if [[ "$DO_BRANCH" == true ]]; then
  echo "== [1/5] 分支保护: ${REPO}@${BRANCH} =="
  # required_status_checks 固定为 build-and-test（ci-cd.yml 核心质量门禁 job）+ service-registry-guard（无 if 条件、始终运行的兜底 job）。
  # 不选 docker-build：其带 `if: has_changes == 'true'`，变更集为空时会被 skip，导致 required status check 永久 pending 阻断合并。
  if gh api --method PUT "repos/${REPO}/branches/${BRANCH}/protection" --input - >/dev/null 2>&1 <<'EOF'
{
  "required_status_checks": {"strict": true, "contexts": ["build-and-test", "service-registry-guard"]},
  "enforce_admins": true,
  "required_pull_request_reviews": {"required_approving_review_count":1,"dismiss_stale_reviews":true,"require_code_owner_reviews":true},
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
  then
    echo "    OK"
  else
    echo "    FAILED（需 admin 权限）"; failures=$((failures+1))
  fi
fi

# ---- 2. secret scanning + push protection ----
if [[ "$DO_SECRET" == true ]]; then
  echo "== [2/5] secret scanning + push protection: ${REPO} =="
  if out="$(gh api "repos/${REPO}" --method PATCH \
       --field security_and_analysis.secret_scanning.status=enabled \
       --field security_and_analysis.secret_scanning_push_protection.status=enabled \
       --jq '"secret_scanning=" + .security_and_analysis.secret_scanning.status + " push_protection=" + .security_and_analysis.secret_scanning_push_protection.status' 2>/dev/null)"; then
    echo "    ${out}"
  else
    echo "    FAILED（需 admin 权限）"; failures=$((failures+1))
  fi
fi

# ---- 3. Discussions ----
if [[ "$DO_DISCUSSIONS" == true ]]; then
  echo "== [3/5] Discussions: ${REPO} =="
  if out="$(gh api "repos/${REPO}" --method PATCH --field has_discussions=true \
       --jq '"has_discussions=" + (.has_discussions|tostring)' 2>/dev/null)"; then
    echo "    ${out}"
  else
    echo "    FAILED（需 admin 权限）"; failures=$((failures+1))
  fi
fi

# ---- 4. Webhooks（可选，需 WEBHOOK_URL）----
if [[ "$DO_WEBHOOKS" == true ]]; then
  echo "== [4/5] Webhooks: ${REPO} =="
  if [[ -z "${WEBHOOK_URL:-}" ]]; then
    echo "    SKIP：未设置 WEBHOOK_URL（项目当前无接收 GitHub webhook 的服务）"
  elif gh api "repos/${REPO}/hooks" --jq '.[]?.config.url' 2>/dev/null | grep -Fxq "${WEBHOOK_URL}"; then
    echo "    OK（webhook 已存在，跳过，幂等）"
  else
    if out="$(gh api "repos/${REPO}/hooks" \
         -f name=web \
         -f "config.url=${WEBHOOK_URL}" \
         -f config.content_type=json \
         -f "config.secret=${WEBHOOK_SECRET:-}" \
         -f 'events=["push","pull_request"]' \
         --jq '"hook_id=" + (.id|tostring)' 2>/dev/null)"; then
      echo "    ${out}"
    else
      echo "    FAILED（需 admin 权限）"; failures=$((failures+1))
    fi
  fi
fi

# ---- 5. production environment 审批 ----
if [[ "$DO_ENV" == true ]]; then
  echo "== [5/5] environment 审批: ${REPO}/${ENV_NAME} =="
  if ! owner_id="$(gh api "users/${OWNER}" --jq '.id' 2>/dev/null)"; then
    echo "    FAILED（无法解析 owner id: ${OWNER}）"; failures=$((failures+1))
  elif gh api --method PUT "repos/${REPO}/environments/${ENV_NAME}" --input - >/dev/null 2>&1 <<EOF
{
  "wait_timer": 0,
  "reviewers": [{"type": "User", "id": ${owner_id}}]
}
EOF
  then
    echo "    OK（审批人: ${OWNER}）"
  else
    echo "    FAILED（需 admin 权限）"; failures=$((failures+1))
  fi
fi

echo "== 完成：失败 ${failures} 项 =="
exit "${failures}"
