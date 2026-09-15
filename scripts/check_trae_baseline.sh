#!/usr/bin/env bash
#
# check_trae_baseline.sh — Trae 四子系统基线巡检（白盒进度条版）
# 核对：.trae 目录完整性 / 项目 Skill 白名单 / 必选 MCP 是否注册
# 用法：bash scripts/check_trae_baseline.sh
# 退出码：0=全部通过；1=存在缺失
# 白盒说明：4 个接触点（L4 治理层 / L1 策略层 / L2 执行层 / L3 工具层），
#           每项检查推进全局进度条，实时展示完成度、失败数与接触点小结。
#
set -u

# --ci：CI 模式，跳过依赖本机 ~/.trae 的检查（本机 Skill 副本 / MCP 注册）
CI_MODE=0
for _a in "$@"; do
  [ "$_a" = "--ci" ] && CI_MODE=1
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TRAE_DIR="$ROOT/.trae"
HOME_TRAE="${HOME:-$HOME_DIR}/.trae"

# ---------------- 进度条白盒状态机 ----------------
DONE=0
FAIL=0
TOTAL=0
BAR_DIRTY=0
CP_NUM=""; CP_NAME=""; CP_START_DONE=0; CP_START_FAIL=0

if [ -t 1 ]; then TTY=1; else TTY=0; fi
C_RESET=$'\033[0m'; C_GREEN=$'\033[32m'; C_RED=$'\033[31m'; C_CYAN=$'\033[36m'; C_DIM=$'\033[2m'
if [ "$TTY" -ne 1 ]; then C_RESET=""; C_GREEN=""; C_RED=""; C_CYAN=""; C_DIM=""; fi

BAR_W=30

# 绘制全局进度条（TTY 下原地刷新；管道/CI 下不输出，仅保留明细）
draw_bar() {
  [ "$TTY" -ne 1 ] && return
  local denom=$TOTAL filled pct bar i color fail_color
  [ "$denom" -le 0 ] && denom=1
  pct=$(( DONE * 100 / denom ))
  filled=$(( DONE * BAR_W / denom ))
  bar=""
  for ((i=0; i<BAR_W; i++)); do
    if [ "$i" -lt "$filled" ]; then bar+="█"; else bar+="░"; fi
  done
  color="$C_GREEN"; [ "$FAIL" -gt 0 ] && color="$C_RED"
  fail_color="$C_GREEN"; [ "$FAIL" -gt 0 ] && fail_color="$C_RED"
  printf "\r\033[K%s[%s]%s %3d/%d (%3d%%)  失败:%s%d%s" \
    "$color" "$bar" "$C_RESET" "$DONE" "$denom" "$pct" "$fail_color" "$FAIL" "$C_RESET"
  BAR_DIRTY=1
}

# 明细行：若进度条正在行尾刷新，先换行收尾再输出明细
detail() {
  if [ "$BAR_DIRTY" -eq 1 ]; then printf "\n"; BAR_DIRTY=0; fi
  printf "%s\n" "$1"
}

step_ok()  { DONE=$((DONE+1)); detail "  ${C_GREEN}✓${C_RESET} $1"; draw_bar; }
step_bad() { DONE=$((DONE+1)); FAIL=$((FAIL+1)); detail "  ${C_RED}✗${C_RESET} $1"; draw_bar; }

# 接触点（checkpoint）：进入时记录该接触点起始进度
checkpoint() {
  if [ "$BAR_DIRTY" -eq 1 ]; then printf "\n"; BAR_DIRTY=0; fi
  CP_NUM="$1"; CP_NAME="$2"; CP_START_DONE=$DONE; CP_START_FAIL=$FAIL
  printf "\n${C_CYAN}[%s/4] %s${C_RESET}\n" "$CP_NUM" "$CP_NAME"
}

# 接触点小结：先收尾进度条行，再输出该接触点通过/失败数
checkpoint_done() {
  if [ "$BAR_DIRTY" -eq 1 ]; then printf "\n"; BAR_DIRTY=0; fi
  local d=$(( DONE - CP_START_DONE ))
  local f=$(( FAIL - CP_START_FAIL ))
  local color="$C_DIM"; [ "$f" -gt 0 ] && color="$C_RED"
  printf "%s  ▸ 接触点 %s 完成：通过 %d/%d，失败 %d%s\n" "$color" "$CP_NUM" "$((d-f))" "$d" "$f" "$C_RESET"
}

# ---------------- 检查项清单（先定义，再算总数） ----------------
REQUIRED_DIRS=(agents checklists documents skills sources templates knowledge)

REQUIRED_FILES=(
  ".trae/sources/INDEX.md"
  ".trae/templates/AGENT_TASK_CARD.md"
  ".trae/templates/RETROSPECTIVE_TEMPLATE.md"
  ".trae/templates/SKILL_TEMPLATE.md"
  ".trae/checklists/MCP_BASELINE.md"
  ".trae/checklists/RELEASE_ROLLBACK_LOOP.md"
  ".trae/checklists/SKILL_SCOPE.md"
  ".trae/knowledge/architecture-map.md"
  ".trae/knowledge/known-risks.md"
)

REQUIRED_SKILLS=(aiot-remote-dev aiot-security-audit aiot-schema-validator aiot-test-coverage aiot-system-boundary product-agent project-doc-agent)

REQUIRED_AGENTS=(test-coverage-analyzer schema-validator security-auditor system-boundary-analyzer vibe-ops-master)

REQUIRED_MCPS=(mcp_Memory mcp_Sequential_Thinking mcp_GitHub)

# 总数 = 目录7 + 文件9 + Skill7 + remote-dev副本1 + Agent(存在+契约+cite)5*3 + 场景目录1 + MCP 3*3
# CI 模式：remote-dev 副本 / 场景目录 / MCP 为本机依赖，不计入
if [ "$CI_MODE" -eq 1 ]; then
  TOTAL=$(( ${#REQUIRED_DIRS[@]} + ${#REQUIRED_FILES[@]} + ${#REQUIRED_SKILLS[@]} + ${#REQUIRED_AGENTS[@]}*3 ))
else
  TOTAL=$(( ${#REQUIRED_DIRS[@]} + ${#REQUIRED_FILES[@]} + ${#REQUIRED_SKILLS[@]} + 1 + ${#REQUIRED_AGENTS[@]}*3 + 1 + ${#REQUIRED_MCPS[@]}*3 ))
fi

# ---------------- 头部 ----------------
echo "== Trae 四子系统基线巡检（白盒进度条） =="
echo "项目根目录: $ROOT"
printf "检查项总数: %d | 接触点: 4 | 展示: %s\n\n" "$TOTAL" "$([ "$TTY" -eq 1 ] && echo "实时进度条" || echo "管道明细模式")"

# ---------------- [1/4] L4 治理层 ----------------
checkpoint 1 "L4 治理层（.trae 目录 + 文件完整性）"
for d in "${REQUIRED_DIRS[@]}"; do
  if [ -d "$TRAE_DIR/$d" ]; then step_ok "目录 .trae/$d"; else step_bad "目录 .trae/$d"; fi
done
for f in "${REQUIRED_FILES[@]}"; do
  if [ -f "$ROOT/$f" ]; then step_ok "文件 $f"; else step_bad "文件 $f"; fi
done
checkpoint_done

# ---------------- [2/4] L1 策略层 ----------------
checkpoint 2 "L1 策略层（项目 Skill 白名单）"
for s in "${REQUIRED_SKILLS[@]}"; do
  if [ -f "$TRAE_DIR/skills/$s/SKILL.md" ]; then step_ok "Skill $s"; else step_bad "Skill $s"; fi
done
# aiot-remote-dev 双份维护校验：仓库版为 SSOT，本机版为同步副本（见 .trae/sources/INDEX.md）
if [ "$CI_MODE" -eq 1 ]; then
  :  # CI 模式：跳过本机副本校验（runner 无 ~/.trae/skills）
else
  LOCAL_REMOTE_DEV="$HOME_TRAE/skills/aiot-remote-dev/SKILL.md"
  REPO_REMOTE_DEV="$TRAE_DIR/skills/aiot-remote-dev/SKILL.md"
  if [ -f "$LOCAL_REMOTE_DEV" ]; then
    if diff -q "$LOCAL_REMOTE_DEV" "$REPO_REMOTE_DEV" >/dev/null 2>&1; then
      step_ok "Skill aiot-remote-dev 本机副本与 SSOT 一致"
    else
      step_bad "Skill aiot-remote-dev 本机副本与 SSOT 漂移"
    fi
  else
    step_bad "Skill aiot-remote-dev 本机副本缺失 (~/.trae/skills)"
  fi
fi
checkpoint_done

# ---------------- [3/4] L2 执行层 ----------------
checkpoint 3 "L2 执行层（项目 Agent：存在 + 契约 + cite）"
for a in "${REQUIRED_AGENTS[@]}"; do
  if [ -f "$TRAE_DIR/agents/$a.md" ]; then step_ok "Agent $a"; else step_bad "Agent $a"; fi
done
# 契约 lint：每个 Agent 必须含回传/验收契约 + cite 证据要求
for a in "${REQUIRED_AGENTS[@]}"; do
  af="$TRAE_DIR/agents/$a.md"
  if grep -qE 'Output format|验收口径' "$af" 2>/dev/null; then
    step_ok "Agent $a 契约（回传/验收）"
  else
    step_bad "Agent $a 缺契约（Output format/验收口径）"
  fi
  if grep -qi 'cite' "$af" 2>/dev/null; then
    step_ok "Agent $a cite 证据要求"
  else
    step_bad "Agent $a 缺 cite 证据要求"
  fi
done
checkpoint_done

# ---------------- [4/4] L3 工具层 ----------------
checkpoint 4 "L3 工具层（必选 MCP 注册）"
if [ "$CI_MODE" -eq 1 ]; then
  :  # CI 模式：跳过 MCP 注册校验（runner 无 ~/.trae/mcps）
else
  SCENE_DIR="$(ls -d "$HOME_TRAE"/mcps/s_AIOT-java-* 2>/dev/null | head -1)"
  if [ -z "$SCENE_DIR" ]; then
    step_bad "场景目录 ~/.trae/mcps/s_AIOT-java-* 未找到"
    for m in "${REQUIRED_MCPS[@]}"; do
      step_bad "MCP $m（场景目录缺失）"
      step_bad "MCP $m / SERVER_METADATA.json"
      step_bad "MCP $m / tools/*.json"
    done
  else
    step_ok "场景目录 $(basename "$SCENE_DIR")"
    for m in "${REQUIRED_MCPS[@]}"; do
      mdir=""
      if [ -d "$SCENE_DIR/solo_agent/$m" ]; then
        mdir="$SCENE_DIR/solo_agent/$m"
      elif [ -d "$SCENE_DIR/$m" ]; then
        mdir="$SCENE_DIR/$m"
      fi
      if [ -z "$mdir" ]; then
        step_bad "MCP $m（目录缺失）"
        step_bad "MCP $m / SERVER_METADATA.json"
        step_bad "MCP $m / tools/*.json"
      else
        step_ok "MCP $m"
        # 元信息与工具清单必须存在（对齐 MCP_BASELINE.md 每周巡检第 2 条）
        if [ -f "$mdir/SERVER_METADATA.json" ]; then step_ok "MCP $m / SERVER_METADATA.json"; else step_bad "MCP $m / SERVER_METADATA.json"; fi
        if ls "$mdir/tools/"*.json >/dev/null 2>&1; then step_ok "MCP $m / tools/*.json"; else step_bad "MCP $m / tools/*.json"; fi
      fi
    done
  fi
fi
checkpoint_done

# ---------------- 收尾 ----------------
if [ "$BAR_DIRTY" -eq 1 ]; then printf "\n"; BAR_DIRTY=0; fi
echo ""
if [ "$FAIL" -eq 0 ]; then
  printf "%s== 结果：BASELINE PASSED（%d/%d 项通过，0 失败）==%s\n" "$C_GREEN" "$DONE" "$TOTAL" "$C_RESET"
  exit 0
else
  printf "%s== 结果：BASELINE FAILED（%d 项失败）==%s\n" "$C_RED" "$FAIL" "$C_RESET"
  exit 1
fi
