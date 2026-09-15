#!/usr/bin/env python3
"""AI 链路质量评估：多场景诊断 -> schema 通过率 -> 反馈案例沉淀 -> 四方对账。

量化维度：
  - source 命中率（llm / fallback 占比）
  - schema 通过率
  - 置信度分布（min / avg / max / 直方图）
  - riskLevel 分布
  - 端到端延迟（min / avg / p50 / p95 / max）
  - 案例沉淀（feedback SOLVED -> case 落库）
  - 持久化对账（MySQL + Redis 镜像 + outbox 清空）
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import time
import urllib.error
import urllib.request
from collections import Counter
from typing import Any


SCENES = ["OFFLINE_FLAP", "PROVISION_FAILURE", "SHADOW_DIFF"]

# rootCauseCategory 受控词表（与 AiSchemaValidator.normalizeRootCause 标准枚举一致）
CONTROLLED_ROOT_CAUSE_CATEGORIES = {
    "NETWORK_INSTABILITY",
    "PERFORMANCE_DEGRADATION",
    "CONFIGURATION_ERROR",
    "STATE_SYNC_DRIFT",
    "PROVISIONING_CONFLICT",
}


def request_json(url: str, payload: dict[str, Any] | None = None) -> tuple[dict[str, Any], float]:
    body = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method="POST" if payload else "GET")
    started = time.time()
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            content = response.read().decode("utf-8")
        elapsed = time.time() - started
        return json.loads(content), elapsed
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} calling {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Failed to call {url}: {exc.reason}") from exc


def unwrap(document: dict[str, Any]) -> dict[str, Any]:
    if "data" in document and isinstance(document["data"], dict):
        return document["data"]
    return document


def query_mysql(sql: str) -> list[dict[str, Any]]:
    import pymysql

    connection = pymysql.connect(
        host=os.getenv("AIOT_MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("AIOT_MYSQL_PORT", "3306")),
        user=os.getenv("AIOT_MYSQL_USER", "root"),
        password=os.getenv("AIOT_MYSQL_PASSWORD", ""),
        database=os.getenv("AIOT_MYSQL_DATABASE", "aiot_cloud"),
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(sql)
            return cursor.fetchall()
    finally:
        connection.close()


def redis_hlen(key: str) -> int:
    try:
        import redis

        client = redis.Redis(
            host=os.getenv("AIOT_REDIS_HOST", "127.0.0.1"),
            port=int(os.getenv("AIOT_REDIS_PORT", "6379")),
            db=int(os.getenv("AIOT_REDIS_DB", "0")),
            decode_responses=True,
        )
        return int(client.hlen(key))
    except Exception:
        return -1


def pct(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(q * len(ordered)))
    return ordered[index]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8084")
    parser.add_argument("--device-id", default="gdev-984102-63010-u0990-d09")
    parser.add_argument("--iterations", type=int, default=5)
    parser.add_argument("--feedback", action="store_true", help="为每个诊断提交 SOLVED 反馈以触发案例沉淀")
    parser.add_argument("--report-path", default=None)
    args = parser.parse_args()

    base = args.base_url
    results: list[dict[str, Any]] = []

    for scene in SCENES:
        for i in range(args.iterations):
            event_id = f"evt-q{scene.lower()}-{i}"
            payload = {"deviceId": args.device_id, "sceneType": scene, "eventId": event_id}
            raw, elapsed = request_json(f"{base}/api/v1/ai/diagnosis", payload)
            data = unwrap(raw)
            source = data.get("source")
            confidence = data.get("confidence")
            risk_level = data.get("riskLevel")
            results.append(
                {
                    "scene": scene,
                    "iteration": i,
                    "source": source,
                    "confidence": confidence,
                    "riskLevel": risk_level,
                    "rootCauseCategory": data.get("rootCauseCategory"),
                    "modelName": data.get("modelName"),
                    "promptVersion": data.get("promptVersion"),
                    "ruleDraftable": data.get("ruleDraftable"),
                    "diagnosisId": data.get("diagnosisId"),
                    "clientLatencySec": round(elapsed, 3),
                    "httpCode": raw.get("code"),
                }
            )

    # 案例沉淀：对 llm 诊断提交 SOLVED 反馈
    case_results: list[dict[str, Any]] = []
    if args.feedback:
        for item in results:
            if item.get("source") != "llm":
                continue
            feedback_payload = {
                "diagnosisId": item["diagnosisId"],
                "feedbackType": "ACCEPTED",
                "resolutionStatus": "SOLVED",
                "operatorId": "quality-eval",
                "resolutionNote": "质量评估闭环验证",
            }
            try:
                raw, _ = request_json(f"{base}/api/v1/ai/feedback", feedback_payload)
                fb = unwrap(raw)
                case_results.append(
                    {
                        "diagnosisId": item["diagnosisId"],
                        "feedbackSaved": fb.get("feedbackSaved"),
                        "caseSaved": fb.get("caseSaved"),
                        "caseStatus": fb.get("caseStatus"),
                        "caseId": fb.get("caseId"),
                    }
                )
            except Exception as exc:  # noqa: BLE001
                case_results.append({"diagnosisId": item["diagnosisId"], "error": str(exc)})

    # 数据库对账
    db_latency_rows = query_mysql(
        "SELECT scene_type, latency_ms FROM ai_diagnosis_record ORDER BY id DESC LIMIT 100"
    )
    latency_ms = [int(r["latency_ms"]) for r in db_latency_rows if r.get("latency_ms") is not None]

    source_counter = Counter(r["source"] for r in results)
    total = len(results)
    llm_count = source_counter.get("llm", 0)
    fallback_count = source_counter.get("fallback", 0)

    confidence_values = [float(r["confidence"]) for r in results if r.get("confidence") is not None]
    risk_counter = Counter(str(r["riskLevel"]) for r in results)

    # 枚举受控词表治理：rootCauseCategory 必须收敛到标准枚举
    root_cause_counter = Counter(str(r["rootCauseCategory"]) for r in results)
    root_cause_total = sum(1 for r in results if r.get("rootCauseCategory") is not None)
    root_cause_out_of_vocab = sorted({
        str(r["rootCauseCategory"])
        for r in results
        if r.get("rootCauseCategory") is not None
        and str(r["rootCauseCategory"]) not in CONTROLLED_ROOT_CAUSE_CATEGORIES
    })
    root_cause_coverage = (
        round((root_cause_total - len(root_cause_out_of_vocab)) / root_cause_total, 4)
        if root_cause_total else 1.0
    )

    # 直方图
    hist = {"0.0-0.6": 0, "0.6-0.7": 0, "0.7-0.8": 0, "0.8-0.9": 0, "0.9-1.0": 0}
    for c in confidence_values:
        if c < 0.6:
            hist["0.0-0.6"] += 1
        elif c < 0.7:
            hist["0.6-0.7"] += 1
        elif c < 0.8:
            hist["0.7-0.8"] += 1
        elif c < 0.9:
            hist["0.8-0.9"] += 1
        else:
            hist["0.9-1.0"] += 1

    counts = {
        "diagnosis": int(query_mysql("SELECT COUNT(*) AS c FROM ai_diagnosis_record")[0]["c"]),
        "feedback": int(query_mysql("SELECT COUNT(*) AS c FROM ai_feedback_record")[0]["c"]),
        "case": int(query_mysql("SELECT COUNT(*) AS c FROM ai_case_library")[0]["c"]),
        "materializationTask": int(query_mysql("SELECT COUNT(*) AS c FROM ai_case_materialization_task")[0]["c"]),
        "outbox": int(query_mysql("SELECT COUNT(*) AS c FROM ai_mysql_write_outbox")[0]["c"]),
    }

    report = {
        "evaluatedAt": int(time.time() * 1000),
        "deviceId": args.device_id,
        "scenes": SCENES,
        "iterationsPerScene": args.iterations,
        "totalDiagnoses": total,
        "source": {
            "llm": llm_count,
            "fallback": fallback_count,
            "llmHitRate": round(llm_count / total, 4) if total else 0.0,
            "fallbackRatio": round(fallback_count / total, 4) if total else 0.0,
            "schemaPassRate": round(llm_count / total, 4) if total else 0.0,
        },
        "confidence": {
            "min": round(min(confidence_values), 4) if confidence_values else None,
            "avg": round(statistics.mean(confidence_values), 4) if confidence_values else None,
            "max": round(max(confidence_values), 4) if confidence_values else None,
            "histogram": hist,
        },
        "riskLevel": dict(risk_counter),
        "enumGovernance": {
            "controlledRootCauseVocab": sorted(CONTROLLED_ROOT_CAUSE_CATEGORIES),
            "rootCauseCategoryDistribution": dict(root_cause_counter),
            "rootCauseCategoryCoverageRate": root_cause_coverage,
            "outOfVocabValues": root_cause_out_of_vocab,
            "gatePassed": root_cause_coverage >= 1.0,
        },
        "latencyMs": {
            "min": min(latency_ms) if latency_ms else None,
            "avg": round(statistics.mean(latency_ms), 1) if latency_ms else None,
            "p50": pct([float(x) for x in latency_ms], 0.50) if latency_ms else None,
            "p95": pct([float(x) for x in latency_ms], 0.95) if latency_ms else None,
            "max": max(latency_ms) if latency_ms else None,
        },
        "persistenceCounts": counts,
        "caseMaterialization": {
            "attempted": len(case_results),
            "succeeded": sum(1 for c in case_results if c.get("caseSaved") is True),
            "statuses": Counter(c.get("caseStatus") for c in case_results),
        },
        "perScene": {
            scene: {
                "total": sum(1 for r in results if r["scene"] == scene),
                "llm": sum(1 for r in results if r["scene"] == scene and r["source"] == "llm"),
                "fallback": sum(1 for r in results if r["scene"] == scene and r["source"] == "fallback"),
            }
            for scene in SCENES
        },
        "details": results,
    }

    print(json.dumps(report, ensure_ascii=False, indent=2))

    if args.report_path:
        from pathlib import Path

        path = Path(args.report_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n[report] {path}")


if __name__ == "__main__":
    main()
