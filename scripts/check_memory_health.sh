#!/usr/bin/env bash
#
# check_memory_health.sh — Trae 记忆系统健康度巡检
# 5 维指标体系：完整性 / 一致性 / 规模 / 生命周期 / 检索效能
# 用法：bash scripts/check_memory_health.sh [--json] [--ci]
#   --ci：CI 模式，仅校验仓库侧快照（status_snapshot.json），跳过仅存在于开发机的 ~/.trae/memory 检查。
# 退出码：0=通过；1=存在 FAIL 门禁
# 与 check_trae_baseline.sh（.trae 四子系统）互补：本脚本聚焦 ~/.trae/memory 跨会话记忆层。
#
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CURRENT_PROJECT_PATH="${AIOT_PROJECT_PATH:-$ROOT}"

python3 - "$ROOT" "$CURRENT_PROJECT_PATH" "$@" <<'PY'
import json, re, sys
from datetime import datetime, timezone, date
from pathlib import Path

root = Path(sys.argv[1])
current_project = Path(sys.argv[2])
home = Path.home()
mem = home / ".trae" / "memory"
projects_dir = mem / "projects"

now = datetime.now(timezone.utc)
today = now.date()

metrics = {}
issues = []   # FAIL 级
warns = []    # WARN 级
CI_MODE = "--ci" in sys.argv[2:]

# ---------- 工具 ----------
def fail(key, msg): issues.append({"key": key, "msg": msg})
def warn(key, msg): warns.append({"key": key, "msg": msg})

# ---------- A. 完整性（Completeness） ----------
snap = root / ".trae" / "status_snapshot.json"
metrics["snapshot_exists"] = snap.exists()
snap_age_min = None
snap_valid = False
if snap.exists():
    try:
        data = json.loads(snap.read_text(encoding="utf-8"))
        gen = data.get("generated_at")
        if gen:
            gen_dt = datetime.fromisoformat(gen)
            snap_age_min = int((now - gen_dt).total_seconds() // 60)
            snap_valid = True
    except Exception:
        snap_valid = False
    metrics["snapshot_age_min"] = snap_age_min
    metrics["snapshot_valid"] = snap_valid
    if not CI_MODE and (snap_age_min is None or snap_age_min > 24 * 60):
        fail("snapshot_stale", f"status_snapshot.json 缺失或过期（age={snap_age_min}min）")
    if CI_MODE and not snap_valid:
        fail("snapshot_invalid", "status_snapshot.json 不是合法 JSON 或缺 generated_at，CI 门禁阻断")
else:
    fail("snapshot_missing", "status_snapshot.json 未落盘，L0 感知退化为手动聚合")

# ---------- B. 一致性（Consistency） ----------
# B1: project_memory 指针化（不含 known-risks 状态细节）
pm_files = list(projects_dir.glob("*/project_memory.md")) if projects_dir.exists() else []
pm_has_state_detail = False
for pm in pm_files:
    txt = pm.read_text(encoding="utf-8", errors="ignore")
    # 状态细节词只应出现在 known-risks.md，不应出现在 project_memory.md
    if re.search(r"待处理|已修复|已缓解|已落地|观察", txt):
        pm_has_state_detail = True
metrics["project_memory_pointerized"] = not pm_has_state_detail
if pm_has_state_detail:
    warn("pm_not_pointerized", "project_memory.md 含状态细节，违反 SSOT「仅留指针」约定")

# B2: 当前项目 project_id 冗余度（应 =1）
# project_id 命名：路径 /Users/aiden/Projects/AIOT-java -> -Users-aiden-Projects-AIOT-java[...]
proj_key = str(current_project).replace("/", "-")
matching_ids = [d.name for d in projects_dir.iterdir() if d.is_dir() and d.name.startswith(proj_key)] if projects_dir.exists() else []
metrics["active_project_ids"] = len(matching_ids)
metrics["active_project_id_names"] = matching_ids
if not CI_MODE:
    if len(matching_ids) > 1:
        warn("project_id_redundant", f"当前项目存在 {len(matching_ids)} 个 project_id，检索命中率下降：{matching_ids}")
    elif len(matching_ids) == 0:
        fail("project_id_missing", f"未找到当前项目的 project_id（prefix={proj_key}）")
    # P2：旧 project_id 归档策略——系统托管目录，仅监控「最近活跃天数」，>60 天才提示可归档，不自动删除
    activity_days = {}
    for pid_name in matching_ids:
        pid_dir = projects_dir / pid_name
        latest = 0.0
        for f in pid_dir.rglob("*"):
            if f.is_file():
                try:
                    latest = max(latest, f.stat().st_mtime)
                except Exception:
                    pass
        activity_days[pid_name] = int((now.timestamp() - latest) // 86400) if latest else None
    metrics["project_id_activity_days"] = activity_days
    for pid_name, days in activity_days.items():
        if days is not None and days > 60:
            warn("project_id_archive_candidate", f"project_id {pid_name} 已 {days} 天无增长，可归档")

# ---------- C. 规模（Scale） ----------
session_files = list(projects_dir.glob("*/*/session_memory_*.jsonl")) if projects_dir.exists() else []
topics_files = list(projects_dir.glob("*/*/topics.md")) if projects_dir.exists() else []
total_bytes = 0
for f in session_files + topics_files:
    try:
        total_bytes += f.stat().st_size
    except Exception:
        pass
metrics["session_file_count"] = len(session_files)
metrics["topics_count"] = len(topics_files)
metrics["memory_size_kb"] = round(total_bytes / 1024, 1)

# ---------- D. 生命周期（Lifecycle） ----------
# D1: 老旧日期目录（目录名为 YYYYMMDD，>90 天）
old_dirs = []
for d in projects_dir.glob("*/*/"):
    if d.is_dir() and re.match(r"^\d{8}$", d.name):
        try:
            d_date = datetime.strptime(d.name, "%Y%m%d").date()
            if (today - d_date).days > 90:
                old_dirs.append(str(d))
        except ValueError:
            pass
metrics["stale_dir_count"] = len(old_dirs)
if old_dirs:
    warn("stale_dirs", f"{len(old_dirs)} 个 >90 天未更新的日期目录（无 TTL 归档）")

# D2: 空 topics（无 session 索引行）
empty_topics = []
for t in topics_files:
    txt = t.read_text(encoding="utf-8", errors="ignore")
    if not re.search(r"session_id:", txt):
        empty_topics.append(str(t))
metrics["empty_topics_count"] = len(empty_topics)

# ---------- E. 检索效能（Retrieval） ----------
# topics.md 中的 session_id 是否能匹配到对应 session_memory_<id>.jsonl
session_id_set = set()
for f in session_files:
    m = re.search(r"session_memory_(.+)\.jsonl$", f.name)
    if m:
        session_id_set.add(m.group(1))
ref_count = 0
hit_count = 0
for t in topics_files:
    txt = t.read_text(encoding="utf-8", errors="ignore")
    for m in re.finditer(r"session_id:\s*([0-9a-f]+)", txt):
        ref_count += 1
        if m.group(1) in session_id_set:
            hit_count += 1
metrics["topic_session_refs"] = ref_count
metrics["topic_session_hits"] = hit_count
metrics["topic_index_hit_rate"] = round(hit_count / ref_count, 3) if ref_count else 1.0
if ref_count and hit_count < ref_count:
    warn("topic_index_gap", f"topics 索引 {ref_count - hit_count}/{ref_count} 个 session_id 无对应 jsonl（漂移/丢失）")

# ---------- 门禁判定 ----------
verdict = "PASS" if not issues else "FAIL"
if verdict == "PASS" and warns:
    verdict = "WARN"

out = {
    "generated_at": now.isoformat(),
    "verdict": verdict,
    "metrics": metrics,
    "fail": issues,
    "warn": warns,
}

want_json = "--json" in sys.argv[2:]
if want_json:
    print(json.dumps(out, ensure_ascii=False, indent=2))
else:
    m = metrics
    print("== Trae 记忆系统健康度巡检 ==")
    if CI_MODE:
        print("  [CI 模式] 仅校验仓库侧快照，跳过本地 ~/.trae/memory 检查")
    print(f"[A 完整性] snapshot_exists={m.get('snapshot_exists')} age_min={m.get('snapshot_age_min')}")
    print(f"[B 一致性] pointerized={m.get('project_memory_pointerized')} active_project_ids={m.get('active_project_ids')} {m.get('active_project_id_names')}")
    print(f"[C 规模]   session={m.get('session_file_count')} topics={m.get('topics_count')} size_kb={m.get('memory_size_kb')}")
    print(f"[D 生命周期] stale_dirs={m.get('stale_dir_count')} empty_topics={m.get('empty_topics_count')}")
    print(f"[E 检索]  refs={m.get('topic_session_refs')} hits={m.get('topic_session_hits')} hit_rate={m.get('topic_index_hit_rate')}")
    print(f"== 结果：{verdict} ==（FAIL {len(issues)} / WARN {len(warns)}）")
    for i in issues:
        print(f"  [FAIL] {i['key']}: {i['msg']}")
    for w in warns:
        print(f"  [WARN] {w['key']}: {w['msg']}")

sys.exit(0 if verdict != "FAIL" else 1)
PY
