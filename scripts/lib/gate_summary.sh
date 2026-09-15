#!/usr/bin/env bash
# ======================================================================
# 统一 Gate 摘要库：让每个发布/回滚/健康门禁输出统一 JSON 摘要
# schema: { gate, status, service, metrics, elapsed_sec, ts, trace_id }
# 用途：vibe-ops-master L4 度量层机读采集（效率指标单调上升闭环），
#       取代散落在各脚本里字段不一的 result/duration_s。
# 依赖：被 aiotctl source 后调用；也可独立 source（需 ROOT_DIR/AIOT_ROOT_DIR）。
# 注意：变量名避开 `status`（zsh 只读保留字），统一用 `st`。
# ======================================================================

# 摘要落盘根目录（单一事实源路径，供 L4 度量层聚合）
_GATE_SUMMARY_DIR="${AIOT_ROOT_DIR:-${ROOT_DIR:-.}}/scripts/.release_state/gates"

# gate_summary::emit <gate> <status> <service> [elapsed_sec] [metrics_json]
#   gate        门禁编号（G4/G5/G6/rollback/...）
#   status      passed | failed | skipped
#   service     服务名（可多服务用逗号分隔）
#   elapsed_sec 耗时（秒，默认 0）
#   metrics_json 附加度量（JSON 对象字符串，默认 {}）
gate_summary::emit() {
  local gate="${1:-unknown}" st="${2:-unknown}" svc="${3:-}"
  local elapsed_sec="${4:-0}" metrics="${5:-{}}"
  local ts trace_id file

  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  trace_id="${gate}-${svc:-nosvc}-$(date -u +%Y%m%d%H%M%S)"
  mkdir -p "${_GATE_SUMMARY_DIR}"
  file="${_GATE_SUMMARY_DIR}/${gate}-$(date -u +%Y%m%d%H%M%S)-${svc:-nosvc}.json"

  python3 - "$file" "$gate" "$st" "$svc" "$elapsed_sec" "$metrics" "$ts" "$trace_id" <<'PY'
import json, sys
file, gate, st, svc, elapsed_sec, metrics, ts, trace_id = sys.argv[1:]
try:
    m = json.loads(metrics) if metrics else {}
except Exception:
    m = {}
rec = {
    "gate": gate,
    "status": st,
    "service": svc,
    "metrics": m,
    "elapsed_sec": float(elapsed_sec or 0),
    "ts": ts,
    "trace_id": trace_id,
}
with open(file, "w", encoding="utf-8") as f:
    json.dump(rec, f, ensure_ascii=False, indent=2)
    f.write("\n")
# 追加一行到 timeline（供时序聚合），失败不影响主流程
timeline = file.rsplit("/", 1)[0] + "/timeline.jsonl"
try:
    with open(timeline, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
except Exception:
    pass
print(json.dumps(rec, ensure_ascii=False))
PY
}
