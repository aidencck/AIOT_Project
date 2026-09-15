"""Verify AI persistence MySQL integrity constraints with live inserts."""

from __future__ import annotations

import argparse
import json
import time
from typing import Any

from aiot_training.common.settings import Settings, load_settings

EXPECTED_CONSTRAINTS = {
    "ai_feedback_record": {
        "fk_ai_feedback_record_diagnosis_id": "FOREIGN KEY",
    },
    "ai_case_library": {
        "fk_ai_case_library_source_feedback_id": "FOREIGN KEY",
        "uk_ai_case_library_source_feedback_id": "UNIQUE",
    },
}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    return parser.parse_args(argv)


def get_mysql_connection(settings: Settings):
    import pymysql  # pylint: disable=import-error,import-outside-toplevel

    return pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_database,
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )


def fetch_constraint_rows(connection, database: str) -> list[dict[str, Any]]:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = %s
              AND TABLE_NAME IN ('ai_feedback_record', 'ai_case_library')
            ORDER BY TABLE_NAME, CONSTRAINT_NAME
            """,
            (database,),
        )
        return list(cursor.fetchall())


def verify_constraints(settings: Settings, scene: str) -> dict[str, Any]:
    connection = get_mysql_connection(settings)
    suffix = str(int(time.time()))
    diagnosis_id = f"proof-diag-{suffix}"
    feedback_id = f"proof-fb-{suffix}"
    case_id = f"proof-case-{suffix}"
    report = {
        "database": settings.mysql_database,
        "scene": scene.upper(),
        "constraintChecks": [],
        "behaviorChecks": [],
        "gatePassed": False,
    }
    try:
        constraint_rows = fetch_constraint_rows(connection, settings.mysql_database)
        constraint_map = {
            (row["TABLE_NAME"], row["CONSTRAINT_NAME"]): row["CONSTRAINT_TYPE"]
            for row in constraint_rows
        }
        for table_name, expected in EXPECTED_CONSTRAINTS.items():
            for constraint_name, constraint_type in expected.items():
                actual = constraint_map.get((table_name, constraint_name))
                report["constraintChecks"].append(
                    {
                        "table": table_name,
                        "constraintName": constraint_name,
                        "expectedType": constraint_type,
                        "actualType": actual,
                        "passed": actual == constraint_type,
                    }
                )

        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO ai_diagnosis_record
                (diagnosis_id, trace_id, scene_type, device_id, home_id, event_id,
                 context_snapshot, model_name, prompt_version, diagnosis_result, latency_ms, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, JSON_OBJECT('deviceId', %s), %s, %s, JSON_OBJECT('summary', 'proof'), %s, NOW())
                """,
                (
                    diagnosis_id,
                    "trace-proof",
                    scene.upper(),
                    "device-proof",
                    "home-proof",
                    "event-proof",
                    "device-proof",
                    "proof-model",
                    "proof-v1",
                    1,
                ),
            )
            cursor.execute(
                """
                INSERT INTO ai_feedback_record
                (feedback_id, diagnosis_id, feedback_type, resolution_status, operator_id, resolution_note, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, NOW())
                """,
                (
                    feedback_id,
                    diagnosis_id,
                    "ACCEPTED",
                    "SOLVED",
                    "proof-operator",
                    "proof-note",
                ),
            )
            cursor.execute(
                """
                INSERT INTO ai_case_library
                (case_id, scene_type, symptom, root_cause, resolution, effectiveness_score, source_feedback_id, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
                """,
                (
                    case_id,
                    scene.upper(),
                    "proof symptom",
                    "NETWORK_INSTABILITY",
                    "proof resolution",
                    1.0,
                    feedback_id,
                ),
            )
            cursor.execute(
                "SELECT COUNT(*) AS total FROM ai_diagnosis_record WHERE diagnosis_id = %s",
                (diagnosis_id,),
            )
            diagnosis_count = int(cursor.fetchone()["total"])
            cursor.execute(
                "SELECT COUNT(*) AS total FROM ai_feedback_record WHERE feedback_id = %s",
                (feedback_id,),
            )
            feedback_count = int(cursor.fetchone()["total"])
            cursor.execute(
                "SELECT COUNT(*) AS total FROM ai_case_library WHERE case_id = %s",
                (case_id,),
            )
            case_count = int(cursor.fetchone()["total"])

        report["behaviorChecks"].append(
            {
                "name": "valid_chain_inserted",
                "passed": diagnosis_count == 1 and feedback_count == 1 and case_count == 1,
                "diagnosisCount": diagnosis_count,
                "feedbackCount": feedback_count,
                "caseCount": case_count,
            }
        )

        behavior_specs = [
            (
                "orphan_feedback_rejected",
                """
                INSERT INTO ai_feedback_record
                (feedback_id, diagnosis_id, feedback_type, resolution_status, created_at)
                VALUES (%s, %s, 'ACCEPTED', 'SOLVED', NOW())
                """,
                (f"proof-fb-orphan-{suffix}", f"missing-diag-{suffix}"),
            ),
            (
                "duplicate_case_rejected",
                """
                INSERT INTO ai_case_library
                (case_id, scene_type, symptom, root_cause, resolution, effectiveness_score, source_feedback_id, created_at)
                VALUES (%s, %s, 'dup symptom', 'NETWORK_INSTABILITY', 'dup resolution', %s, %s, NOW())
                """,
                (f"proof-case-dup-{suffix}", scene.upper(), 0.8, feedback_id),
            ),
            (
                "orphan_case_rejected",
                """
                INSERT INTO ai_case_library
                (case_id, scene_type, symptom, root_cause, resolution, effectiveness_score, source_feedback_id, created_at)
                VALUES (%s, %s, 'orphan symptom', 'NETWORK_INSTABILITY', 'orphan resolution', %s, %s, NOW())
                """,
                (f"proof-case-orphan-{suffix}", scene.upper(), 0.5, f"missing-fb-{suffix}"),
            ),
        ]

        for name, sql, params in behavior_specs:
            report["behaviorChecks"].append(execute_expected_failure(connection, name, sql, params))

        report["gatePassed"] = all(item["passed"] for item in report["constraintChecks"]) and all(
            item["passed"] for item in report["behaviorChecks"]
        )
        report_dir = settings.project_root / "training" / "data" / "reports" / "persistence"
        report_dir.mkdir(parents=True, exist_ok=True)
        report_path = report_dir / "mysql_constraints_verification.json"
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        return report
    finally:
        cleanup_proof_rows(connection, diagnosis_id, feedback_id, case_id, suffix)
        connection.close()


def execute_expected_failure(connection, name: str, sql: str, params: tuple[Any, ...]) -> dict[str, Any]:
    import pymysql  # pylint: disable=import-error,import-outside-toplevel

    try:
        with connection.cursor() as cursor:
            cursor.execute(sql, params)
    except pymysql.err.IntegrityError as exc:  # pragma: no cover - exercised by live DB verification
        return {
            "name": name,
            "passed": True,
            "error": str(exc),
        }
    return {
        "name": name,
        "passed": False,
        "error": "unexpectedly succeeded",
    }


def cleanup_proof_rows(connection, diagnosis_id: str, feedback_id: str, case_id: str, suffix: str) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            DELETE FROM ai_case_library
            WHERE case_id IN (%s, %s, %s)
            """,
            (case_id, f"proof-case-dup-{suffix}", f"proof-case-orphan-{suffix}"),
        )
        cursor.execute(
            """
            DELETE FROM ai_feedback_record
            WHERE feedback_id IN (%s, %s)
            """,
            (feedback_id, f"proof-fb-orphan-{suffix}"),
        )
        cursor.execute(
            "DELETE FROM ai_diagnosis_record WHERE diagnosis_id = %s",
            (diagnosis_id,),
        )


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    settings = load_settings()
    report = verify_constraints(settings, args.scene)
    if not report["gatePassed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
