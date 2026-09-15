#!/usr/bin/env bash
# ======================================================================
# GitHub GraphQL 批量查询工具（一次调用聚合多项统计，比 REST 少 N 次往返）。
# owner/name 从 SSOT（scripts/lib/services.json）读取，禁止硬编码。
# 用法：
#   ./scripts/gh_graphql.sh          # 仓库统计：open issues/PRs + 各 milestone 进度
# ======================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh" 2>/dev/null || true

OWNER="${GITHUB_OWNER:-$(github::owner 2>/dev/null || echo 'aidencck')}"
REPO_NAME="${GITHUB_REPO_NAME:-$(github::repo 2>/dev/null | cut -d/ -f2 || echo 'AIOT_Project')}"

QUERY="$(cat <<EOF
query {
  repository(owner: "${OWNER}", name: "${REPO_NAME}") {
    openIssues: issues(states: OPEN) { totalCount }
    openPRs: pullRequests(states: OPEN) { totalCount }
    milestones(first: 20, states: OPEN, orderBy: {field: DUE_DATE, direction: ASC}) {
      nodes {
        title
        openIssues: issues(states: OPEN) { totalCount }
        closedIssues: issues(states: CLOSED) { totalCount }
      }
    }
  }
}
EOF
)"

RAW="$(gh api graphql -f "query=${QUERY}")" || {
  echo "ERROR: GraphQL 请求失败（检查 gh 登录态与网络）" >&2
  exit 1
}
# 仓库不存在/无访问权限时 repository 为 null，提前显式报错而非静默输出空。
if jq -e '.data.repository == null' <<<"$RAW" >/dev/null 2>&1; then
  echo "ERROR: 仓库 ${OWNER}/${REPO_NAME} 不存在或当前账号无访问权限" >&2
  exit 1
fi
jq '.data.repository | {
  repo: "'"${OWNER}/${REPO_NAME}"'",
  open_issues: .openIssues.totalCount,
  open_prs: .openPRs.totalCount,
  milestones: [.milestones.nodes[] | {title, open: .openIssues.totalCount, closed: .closedIssues.totalCount}]
}' <<<"$RAW"
