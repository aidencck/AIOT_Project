"""Verify the runtime AI diagnosis -> feedback -> case persistence flow."""

from __future__ import annotations

import argparse
from decimal import Decimal
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from aiot_training.common.settings import Settings, load_settings

HISTORY_LIMIT = 20
BUSINESS_REDIS_KEYS = {
    "diagnosis": "aiot:ai:diagnosis-records",
    "feedback": "aiot:ai:feedback-records",
    "cases": "aiot:ai:case-records",
    "mysqlWriteOutbox": "aiot:ai:mysql-write-outbox",
    "caseMaterialization": "aiot:ai:case-materialization-outbox",
}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    parser.add_argument(
        "--rule-engine-base-url",
        default=os.getenv("AIOT_RULE_ENGINE_BASE_URL", "http://127.0.0.1:8084"),
    )
    parser.add_argument("--device-id", default=f"dev-live-proof-{int(time.time())}")
    parser.add_argument("--event-id", default=f"evt-live-proof-{int(time.time())}")
    parser.add_argument("--feedback-type", default="ACCEPTED")
    parser.add_argument("--resolution-status", default="SOLVED")
    parser.add_argument("--operator-id", default="ops-admin")
    parser.add_argument("--resolution-note", default="live proof solved")
    parser.add_argument("--report-path", default=None)
    parser.add_argument("--skip-clean", action="store_true")
    return parser.parse_args(argv)


def build_redis_client(settings: Settings):
    try:
        import redis  # pylint: disable=import-error,import-outside-toplevel
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "Missing dependency 'redis'. "
            "Install training workspace dependencies before running live proof."
        ) from exc

    return redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password,
        decode_responses=True,
    )


def get_mysql_connection(settings: Settings):
    try:
        import pymysql  # pylint: disable=import-error,import-outside-toplevel
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "Missing dependency 'PyMySQL'. "
            "Install training workspace dependencies before running live proof."
        ) from exc

    return pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_database,
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def request_json(method: str, url: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    body = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            content = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} calling {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Failed to call {url}: {exc.reason}") from exc
    return json.loads(content)


def unwrap_result(document: dict[str, Any]) -> dict[str, Any]:
    if "data" in document and isinstance(document["data"], dict):
        return document["data"]
    return document


def cleanup_runtime_state(settings: Settings) -> None:
    redis_client = build_redis_client(settings)
    redis_client.delete(*BUSINESS_REDIS_KEYS.values())

    connection = get_mysql_connection(settings)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET FOREIGN_KEY_CHECKS=0")
            cursor.execute("DELETE FROM ai_case_library")
            cursor.execute("DELETE FROM ai_feedback_record")
            cursor.execute("DELETE FROM ai_diagnosis_record")
            cursor.execute("DELETE FROM ai_mysql_write_outbox")
            cursor.execute("DELETE FROM ai_case_materialization_task")
            cursor.execute("SET FOREIGN_KEY_CHECKS=1")
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def fetch_mysql_snapshot(
    settings: Settings,
    diagnosis_id: str,
    feedback_id: str,
    case_id: str,
) -> dict[str, Any]:
    connection = get_mysql_connection(settings)
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT diagnosis_id, scene_type, device_id, event_id, prompt_version, model_name
                FROM ai_diagnosis_record
                WHERE diagnosis_id = %s
                """,
                (diagnosis_id,),
            )
            diagnosis_row = cursor.fetchone()
            cursor.execute(
                """
                SELECT feedback_id, diagnosis_id, feedback_type, resolution_status,
                       operator_id, resolution_note
                FROM ai_feedback_record
                WHERE feedback_id = %s
                """,
                (feedback_id,),
            )
            feedback_row = cursor.fetchone()
            cursor.execute(
                """
                SELECT case_id, scene_type, source_feedback_id, effectiveness_score, resolution
                FROM ai_case_library
                WHERE case_id = %s
                """,
                (case_id,),
            )
            case_row = cursor.fetchone()
            cursor.execute("SELECT COUNT(*) AS count FROM ai_mysql_write_outbox")
            outbox_count = int(cursor.fetchone()["count"])
            cursor.execute("SELECT COUNT(*) AS count FROM ai_case_materialization_task")
            task_count = int(cursor.fetchone()["count"])
        return {
            "diagnosisRow": diagnosis_row,
            "feedbackRow": feedback_row,
            "caseRow": case_row,
            "mysqlWriteOutboxCount": outbox_count,
            "caseMaterializationTaskCount": task_count,
        }
    finally:
        connection.close()


def fetch_redis_snapshot(
    settings: Settings,
    diagnosis_id: str,
    feedback_id: str,
    case_id: str,
) -> dict[str, Any]:
    redis_client = build_redis_client(settings)
    return {
        "diagnosisHlen": int(redis_client.hlen(BUSINESS_REDIS_KEYS["diagnosis"])),
        "feedbackHlen": int(redis_client.hlen(BUSINESS_REDIS_KEYS["feedback"])),
        "caseHlen": int(redis_client.hlen(BUSINESS_REDIS_KEYS["cases"])),
        "mysqlWriteOutboxHlen": int(
            redis_client.hlen(BUSINESS_REDIS_KEYS["mysqlWriteOutbox"])
        ),
        "caseMaterializationHlen": int(
            redis_client.hlen(BUSINESS_REDIS_KEYS["caseMaterialization"])
        ),
        "diagnosisMirrorExists": redis_client.hexists(
            BUSINESS_REDIS_KEYS["diagnosis"], diagnosis_id
        ),
        "feedbackMirrorExists": redis_client.hexists(
            BUSINESS_REDIS_KEYS["feedback"], feedback_id
        ),
        "caseMirrorExists": redis_client.hexists(BUSINESS_REDIS_KEYS["cases"], case_id),
    }


def resolve_report_path(settings: Settings, report_path: str | None) -> Path:
    if report_path:
        return Path(report_path)
    return (
        settings.project_root
        / "training"
        / "data"
        / "reports"
        / "persistence"
        / "ai_business_live_flow.json"
    )


def resolve_history_index_path(report_path: Path) -> Path:
    return report_path.with_name("ai_business_live_flow_index.json")


def resolve_archive_report_path(report_path: Path, verified_at: int) -> Path:
    history_dir = report_path.parent / "history"
    return history_dir / f"{report_path.stem}_{verified_at}.json"


def make_json_safe(value: Any) -> Any:
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, dict):
        return {key: make_json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [make_json_safe(item) for item in value]
    return value


def read_json_file(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def update_history_index(index_path: Path, entry: dict[str, Any]) -> None:
    payload = read_json_file(index_path)
    existing = payload.get("entries")
    history: list[dict[str, Any]] = []
    if isinstance(existing, list):
        history.extend(item for item in existing if isinstance(item, dict))
    history = [item for item in history if item.get("reportPath") != entry.get("reportPath")]
    history.insert(0, entry)
    stale_entries = history[HISTORY_LIMIT:]
    payload = {
        "reportType": "BUSINESS_LIVE_FLOW",
        "updatedAt": int(time.time() * 1000),
        "entries": history[:HISTORY_LIMIT],
    }
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    prune_stale_history_files(stale_entries)


def prune_stale_history_files(entries: list[dict[str, Any]]) -> None:
    for item in entries:
        report_path = item.get("reportPath")
        if not report_path:
            continue
        path = Path(str(report_path))
        if path.exists():
            path.unlink()


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    settings = load_settings()
    if not args.skip_clean:
        cleanup_runtime_state(settings)

    status_before = unwrap_result(
        request_json("GET", f"{args.rule_engine_base_url}/api/v1/admin/ai/persistence/status")
    )

    diagnosis_response = unwrap_result(
        request_json(
            "POST",
            f"{args.rule_engine_base_url}/api/v1/ai/diagnosis",
            {
                "deviceId": args.device_id,
                "sceneType": args.scene,
                "eventId": args.event_id,
            },
        )
    )
    diagnosis_id = diagnosis_response["diagnosisId"]

    feedback_response = unwrap_result(
        request_json(
            "POST",
            f"{args.rule_engine_base_url}/api/v1/ai/feedback",
            {
                "diagnosisId": diagnosis_id,
                "feedbackType": args.feedback_type,
                "resolutionStatus": args.resolution_status,
                "operatorId": args.operator_id,
                "resolutionNote": args.resolution_note,
            },
        )
    )

    case_id = feedback_response.get("caseId")
    feedback_id = feedback_response.get("feedbackId")
    status_after = unwrap_result(
        request_json("GET", f"{args.rule_engine_base_url}/api/v1/admin/ai/persistence/status")
    )
    mysql_snapshot = fetch_mysql_snapshot(settings, diagnosis_id, feedback_id, case_id)
    redis_snapshot = fetch_redis_snapshot(settings, diagnosis_id, feedback_id, case_id)

    success = all(
        [
            status_before.get("mysqlEnabled") is True,
            status_before.get("mysqlReady") is True,
            status_before.get("mysqlWriteOutboxStoreMode") == "MYSQL_TABLE_PRIMARY",
            status_before.get("caseMaterializationStoreMode") == "MYSQL_TABLE_PRIMARY",
            diagnosis_response.get("diagnosisId") == diagnosis_id,
            feedback_response.get("feedbackSaved") is True,
            feedback_response.get("caseSaved") is True,
            feedback_response.get("caseStatus") == "CREATED",
            mysql_snapshot["diagnosisRow"] is not None,
            mysql_snapshot["feedbackRow"] is not None,
            mysql_snapshot["caseRow"] is not None,
            mysql_snapshot["mysqlWriteOutboxCount"] == 0,
            mysql_snapshot["caseMaterializationTaskCount"] == 0,
            redis_snapshot["diagnosisMirrorExists"] is True,
            redis_snapshot["feedbackMirrorExists"] is True,
            redis_snapshot["caseMirrorExists"] is True,
            redis_snapshot["mysqlWriteOutboxHlen"] == 0,
            redis_snapshot["caseMaterializationHlen"] == 0,
            status_after.get("mysqlWriteOutboxPendingCount") == 0,
            status_after.get("caseMaterializationPendingCount") == 0,
        ]
    )

    report = {
        "verifiedAt": int(time.time() * 1000),
        "scene": args.scene,
        "ruleEngineBaseUrl": args.rule_engine_base_url,
        "cleanedBeforeRun": not args.skip_clean,
        "success": success,
        "requests": {
            "diagnosis": {
                "deviceId": args.device_id,
                "sceneType": args.scene,
                "eventId": args.event_id,
            },
            "feedback": {
                "feedbackType": args.feedback_type,
                "resolutionStatus": args.resolution_status,
                "operatorId": args.operator_id,
                "resolutionNote": args.resolution_note,
            },
        },
        "responses": {
            "diagnosis": diagnosis_response,
            "feedback": feedback_response,
        },
        "statusBefore": status_before,
        "statusAfter": status_after,
        "mysql": mysql_snapshot,
        "redis": redis_snapshot,
    }
    safe_report = make_json_safe(report)

    report_path = resolve_report_path(settings, args.report_path)
    archive_report_path = resolve_archive_report_path(report_path, int(safe_report["verifiedAt"]))
    history_index_path = resolve_history_index_path(report_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    archive_report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(safe_report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    archive_report_path.write_text(
        json.dumps(safe_report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    update_history_index(
        history_index_path,
        {
            "reportType": "BUSINESS_LIVE_FLOW",
            "occurredAt": safe_report["verifiedAt"],
            "reportPath": str(archive_report_path),
            "operator": args.operator_id,
            "scene": args.scene,
            "success": success,
            "accepted": None,
            "dryRun": None,
            "message": feedback_response.get("message"),
        },
    )

    if not success:
        raise SystemExit(
            f"AI business live flow verification failed. See report: {report_path}"
        )

    print(
        json.dumps(
            {
                "success": True,
                "reportPath": str(report_path),
                "archiveReportPath": str(archive_report_path),
                "historyIndexPath": str(history_index_path),
                "diagnosisId": diagnosis_id,
                "feedbackId": feedback_id,
                "caseId": case_id,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
