#!/usr/bin/env bash
set -euo pipefail

# 同步主仓库文档 -> GitHub Wiki (.wiki-sync) 并推送
# 用法:
#   ./scripts/sync_wiki.sh --check   仅校验漂移与非法链接，不写入不推送
#   ./scripts/sync_wiki.sh           镜像 + 提交 + 推送到 GitHub Wiki
#
# 事实源: 主仓库 docs/wiki、docs/product、docs(部分 L4/状态)、根产品定义
# 规则:   wiki 页面禁用 `../` 父级相对链接；引用非 wiki 内容用绝对 GitHub URL

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIKI="$ROOT/.wiki-sync"
MODE="${1:-sync}"

# 单一事实源映射: <主仓库相对路径>|<wiki 相对路径>
# 新增/删除 wiki 页面必须同步更新此清单，并同步 _Sidebar.md / Home.md 导航。
# wiki-native 文件（Home.md / _Sidebar.md）不在此清单，由 project-doc-agent 维护。
MANIFEST=$(cat <<'EOF'
docs/wiki/2026Q3-technology-roadmap-v1.0.0.md|2026Q3-technology-roadmap-v1.0.0.md
docs/wiki/2026Q3-technology-roadmap-v1.1.0.md|2026Q3-technology-roadmap-v1.1.0.md
docs/wiki/ai-native-delivery-backlog.md|ai-native-delivery-backlog.md
docs/wiki/ai-native-m0-blueprint.md|ai-native-m0-blueprint.md
docs/wiki/ai-native-overview.md|ai-native-overview.md
docs/wiki/capability-matrix.md|capability-matrix.md
docs/wiki/core-middlewares.md|core-middlewares.md
docs/wiki/current-architecture.md|current-architecture.md
docs/wiki/device-status-performance-baseline-v1.0.0.md|device-status-performance-baseline-v1.0.0.md
docs/wiki/device-status-performance-baseline-v1.1.0.md|device-status-performance-baseline-v1.1.0.md
docs/wiki/environment-and-config.md|environment-and-config.md
docs/wiki/monitoring-and-alerting-status.md|monitoring-and-alerting-status.md
docs/wiki/system-optimization-roadmap.md|system-optimization-roadmap.md
docs/wiki/testing-and-troubleshooting.md|testing-and-troubleshooting.md
docs/wiki/wiki-fact-verification-checklist.md|wiki-fact-verification-checklist.md
docs/wiki/aiot-full-roadmap-2026Q3-2027Q2.md|aiot-full-roadmap-2026Q3-2027Q2.md
docs/wiki/cloud-native-maturity-and-tenant-region-analysis.md|cloud-native-maturity-and-tenant-region-analysis.md
docs/PROJECT_STATUS.md|PROJECT_STATUS.md
docs/architecture_design.md|architecture_design.md
docs/common_components_guide.md|common_components_guide.md
docs/database_architecture.md|database_architecture.md
docs/ddd_and_api_contract.md|ddd_and_api_contract.md
docs/deployment_and_performance.md|deployment_and_performance.md
docs/development_standards.md|development_standards.md
docs/iteration_and_release_plan.md|iteration_and_release_plan.md
docs/mvp_features_design.md|mvp_features_design.md
docs/project_skeleton_plan.md|project_skeleton_plan.md
docs/technology_selection.md|technology_selection.md
AIoT_IceMaker_Product_Definition.md|AIoT_IceMaker_Product_Definition.md
docs/product/AIoT_AI_Native_Product_Roadmap.md|product/AIoT_AI_Native_Product_Roadmap.md
docs/product/AIoT_Admin_Backoffice_Roadmap.md|product/AIoT_Admin_Backoffice_Roadmap.md
docs/product/AIoT_Admin_Backoffice_Version_Iteration_Plan.md|product/AIoT_Admin_Backoffice_Version_Iteration_Plan.md
docs/product/AIoT_User_Account_System_Plan.md|product/AIoT_User_Account_System_Plan.md
EOF
)

# 剥离文件头部 YAML frontmatter（首个 --- 到次个 ---），返回正文。
# GitHub Wiki 不渲染 frontmatter，镜像时剥离，避免页面显示原始 YAML。
strip_frontmatter() {
  awk 'NR==1 && /^---$/ {infm=1; next}
       infm==1 && /^---$/ {infm=2; next}
       infm==1 {next}
       {print}' "$1"
}

check() {
  local rc=0
  echo "== 漂移校验 =="
  while IFS='|' read -r src dst; do
    [ -z "$src" ] && continue
    if [ ! -f "$ROOT/$src" ]; then
      echo "[MISS] 源缺失: $src"; rc=1; continue
    fi
    local bad
    bad="$(grep -nE '\]\((\.\./|file:///)' "$ROOT/$src" || true)"
    if [ -n "$bad" ]; then
      echo "[LINK-DRIFT] $src 含非法链接（父级相对或 file:// 绝对路径）:"
      echo "$bad"; rc=1
    fi
    if [ ! -f "$WIKI/$dst" ]; then
      echo "[NEW] $dst (wiki 缺失)"
    elif ! cmp -s <(strip_frontmatter "$ROOT/$src") "$WIKI/$dst"; then
      echo "[DIFF] $dst (源与 wiki 不一致)"
    fi
  done <<< "$MANIFEST"
  [ "$rc" -eq 0 ] && echo "== 校验通过 ==" || echo "== 校验未通过 =="
  return "$rc"
}

sync() {
  local changed=0
  while IFS='|' read -r src dst; do
    [ -z "$src" ] && continue
    mkdir -p "$(dirname "$WIKI/$dst")"
    if [ ! -f "$WIKI/$dst" ] || ! cmp -s <(strip_frontmatter "$ROOT/$src") "$WIKI/$dst"; then
      strip_frontmatter "$ROOT/$src" > "$WIKI/$dst"
      echo "[COPY] $src -> $dst"
      changed=1
    fi
  done <<< "$MANIFEST"

  if [ "$changed" -eq 0 ]; then
    echo "== 无变更，跳过提交 =="
    return 0
  fi

  git -C "$WIKI" add -A
  git -C "$WIKI" commit -m "docs(wiki): sync documentation structure from main repo" || true
  git -C "$WIKI" push origin master
  echo "== 已推送到 GitHub Wiki（aidencck/AIOT_Project.wiki）=="
}

case "$MODE" in
  --check) check ;;
  *) check && sync ;;
esac
