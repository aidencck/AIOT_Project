#!/usr/bin/env bash
#
# gen_status_snapshot.sh — 生成 AIoT 项目进度快照 status_snapshot.json
# 供 vibe-ops-master 编排 Agent 的 L0 感知层读取，避免每轮人工翻文档判断"现在该做什么"。
# 聚合 4 源：known-risks.md（P0/P1 待处理风险）/ 路线图里程碑 / git 状态 / CI 状态（尽力而为）。
#
# 用法：bash scripts/gen_status_snapshot.sh [--output <path>]
# 默认输出：.trae/status_snapshot.json
# 退出码：0=成功生成；1=关键源缺失（known-risks.md）；2=参数错误
#
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${ROOT}/.trae/status_snapshot.json"

# 解析 --output
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output|-o) OUT="$2"; shift 2 ;;
    *) echo "未知参数: $1（用法: bash scripts/gen_status_snapshot.sh [--output <path>]）" >&2; exit 2 ;;
  esac
done

python3 - "$ROOT" "$OUT" <<'PY'
import json, re, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
out = Path(sys.argv[2])

# ---------- 1. 风险库解析（单一事实源：known-risks.md）----------
risks_path = root / ".trae/knowledge/known-risks.md"
if not risks_path.exists():
    print(f"[ERROR] 关键源缺失: {risks_path}", file=sys.stderr)
    sys.exit(1)

risks_md = risks_path.read_text(encoding="utf-8")
risks = {"P0": [], "P1": []}
watch = []  # 「观察 / 已落地但待验证」——非待处理但需持续盯的半成品
current = "P1"
for line in risks_md.splitlines():
    s = line.strip()
    # 段落标题 → 优先级（高危/高优先级=P0；中低危/中优先级=P1）
    if s.startswith("###") and ("高危" in s or "P0" in s):
        current = "P0"
    elif s.startswith("###") and ("中低危" in s or "中优先" in s or "P1" in s):
        current = "P1"
    elif s.startswith("##") and "优先级" in s:
        current = "P0" if "P0" in s else ("P1" if "P1" in s else current)
    # 表格行：| R1 | 风险 | 证据 | 止损 | 状态 |
    if s.startswith("|"):
        cells = [c.strip() for c in s.strip("|").split("|")]
        if len(cells) >= 5 and re.match(r"^[RS]\d+$", cells[0]):
            rid, desc, status = cells[0], cells[1], cells[-1]
            if status.startswith("待处理"):
                risks[current].append({"id": rid, "desc": desc, "status": status})
            elif status.startswith("观察") or ("待" in status and ("验证" in status or "seed" in status.lower() or "确认" in status)):
                watch.append({"id": rid, "desc": desc, "status": status, "priority": current})
            # 其余（已修复/已缓解/已落地且无待验证）过滤

# ---------- 2. 路线图里程碑（单一事实源：aiot-full-roadmap-plan.md）----------
roadmap_path = root / ".trae/documents/aiot-full-roadmap-plan.md"
quarters = {}
if roadmap_path.exists():
    txt = roadmap_path.read_text(encoding="utf-8")
    for m in re.finditer(r"`(2026Q[34]|2027Q[12])`[:：]\s*([^。\n]+)", txt):
        quarters.setdefault(m.group(1), m.group(2).strip())

now = datetime.now(timezone.utc)
y, mo = now.year, now.month
if y == 2026 and 7 <= mo <= 9:
    cur_q = "2026Q3"
elif y == 2026 and 10 <= mo <= 12:
    cur_q = "2026Q4"
elif y == 2027 and 1 <= mo <= 3:
    cur_q = "2027Q1"
elif y == 2027 and 4 <= mo <= 6:
    cur_q = "2027Q2"
else:
    cur_q = "unknown"
milestone = quarters.get(cur_q, "")
roadmap_milestone = f"{cur_q}: {milestone}" if milestone else cur_q

# ---------- 3. git 状态 ----------
def git(*args):
    r = subprocess.run(["git", "-C", str(root)] + list(args), capture_output=True, text=True)
    return r.stdout.strip()

branch = git("branch", "--show-current")
uncommitted_files = len(git("status", "--porcelain").splitlines())
recent_commit = git("log", "-1", "--format=%h %s")
unmerged_commits = 0
try:
    upstream = git("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
    if upstream and not upstream.startswith("fatal"):
        unmerged_commits = len(git("log", "--oneline", f"{upstream}..HEAD").splitlines())
except Exception:
    pass

# ---------- 4. CI 状态（尽力而为，gh 不可用不阻断）----------
ci_state = {"available": False}
try:
    r = subprocess.run(
        ["gh", "run", "list", "--limit", "1", "--json", "conclusion,status,headBranch"],
        capture_output=True, text=True, timeout=10,
    )
    if r.returncode == 0 and r.stdout.strip():
        runs = json.loads(r.stdout)
        if runs:
            ci_state = {"available": True, "last_run": runs[0]}
except Exception:
    pass

# ---------- 输出 ----------
snapshot = {
    "generated_at": now.isoformat(),
    "branch": branch,
    "roadmap_milestone": roadmap_milestone,
    "p0_risks": risks["P0"],
    "p1_risks": risks["P1"],
    "watch_risks": watch,
    "uncommitted_files": uncommitted_files,
    "unmerged_commits": unmerged_commits,
    "recent_commit": recent_commit,
    "ci_state": ci_state,
}

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(f"[OK] 快照已生成: {out}")
print(f"  分支: {branch} | 里程碑: {roadmap_milestone}")
print(f"  P0 待处理: {len(risks['P0'])} 条 | P1 待处理: {len(risks['P1'])} 条 | 观察/待验证: {len(watch)} 条")
print(f"  未提交文件: {uncommitted_files} | 未合并提交: {unmerged_commits} | CI: {'可用' if ci_state['available'] else '不可用'}")
PY
