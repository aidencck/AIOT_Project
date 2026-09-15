"""Backfill AI runtime data from Redis hashes into MySQL tables."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from aiot_training.common.manifest import write_manifest
from aiot_training.common.settings import Settings, load_settings
from aiot_training.exporters.redis_source import RedisSourceAdapter

REDIS_KEYS = {
    "diagnosis": "aiot:ai:diagnosis-records",
    "feedback": "aiot:ai:feedback-records",
    "cases": "aiot:ai:case-records",
}

UPSERT_SQL = {
    "diagnosis": """
        INSERT INTO ai_diagnosis_record
        (diagnosis_id, trace_id, scene_type, device_id, home_id, event_id, context_snapshot, model_name, prompt_version, diagnosis_result, latency_ms, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        trace_id = VALUES(trace_id),
        scene_type = VALUES(scene_type),
        device_id = VALUES(device_id),
        home_id = VALUES(home_id),
        event_id = VALUES(event_id),
        context_snapshot = VALUES(context_snapshot),
        model_name = VALUES(model_name),
        prompt_version = VALUES(prompt_version),
        diagnosis_result = VALUES(diagnosis_result),
        latency_ms = VALUES(latency_ms),
        created_at = VALUES(created_at)
    """,
    "feedback": """
        INSERT INTO ai_feedback_record
        (feedback_id, diagnosis_id, feedback_type, resolution_status, operator_id, resolution_note, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        diagnosis_id = VALUES(diagnosis_id),
        feedback_type = VALUES(feedback_type),
        resolution_status = VALUES(resolution_status),
        operator_id = VALUES(operator_id),
        resolution_note = VALUES(resolution_note),
        created_at = VALUES(created_at)
    """,
    "cases": """
        INSERT INTO ai_case_library
        (case_id, scene_type, symptom, root_cause, resolution, effectiveness_score, source_feedback_id, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        scene_type = VALUES(scene_type),
        symptom = VALUES(symptom),
        root_cause = VALUES(root_cause),
        resolution = VALUES(resolution),
        effectiveness_score = VALUES(effectiveness_score),
        source_feedback_id = VALUES(source_feedback_id),
        created_at = VALUES(created_at)
    """,
}


@dataclass(frozen=True)
class BackfillStats:
    """Per-source backfill counters."""

    name: str
    redis_key: str
    scanned: int
    written: int


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse CLI arguments for Redis-to-MySQL backfill."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default=None)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--batch-size", type=int, default=200)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def normalize_json_document(value: Any) -> str | None:
    """Convert runtime payloads into valid JSON text for MySQL JSON columns."""
    if value is None:
        return None
    if isinstance(value, str):
        if not value.strip():
            return None
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return json.dumps(value, ensure_ascii=False)
        return json.dumps(parsed, ensure_ascii=False)
    return json.dumps(value, ensure_ascii=False)


def normalize_timestamp(value: Any) -> datetime | None:
    """Convert second/millisecond epoch values into a naive UTC datetime."""
    if value is None:
        return None
    try:
        timestamp = int(value)
    except (TypeError, ValueError):
        return None
    seconds = timestamp / 1000 if abs(timestamp) >= 10**11 else timestamp
    return datetime.fromtimestamp(seconds, tz=timezone.utc).replace(tzinfo=None)


def to_diagnosis_params(row: dict[str, Any]) -> tuple[Any, ...]:
    """Map Redis diagnosis JSON to MySQL upsert parameters."""
    return (
        row.get("diagnosisId"),
        row.get("traceId"),
        row.get("sceneType"),
        row.get("deviceId"),
        row.get("homeId"),
        row.get("eventId"),
        normalize_json_document(row.get("contextSnapshot")),
        row.get("modelName"),
        row.get("promptVersion"),
        normalize_json_document(row.get("diagnosisResult")),
        row.get("latencyMs"),
        normalize_timestamp(row.get("createdAt")),
    )


def to_feedback_params(row: dict[str, Any]) -> tuple[Any, ...]:
    """Map Redis feedback JSON to MySQL upsert parameters."""
    return (
        row.get("feedbackId"),
        row.get("diagnosisId"),
        row.get("feedbackType"),
        row.get("resolutionStatus"),
        row.get("operatorId"),
        row.get("resolutionNote"),
        normalize_timestamp(row.get("createdAt")),
    )


def to_case_params(row: dict[str, Any]) -> tuple[Any, ...]:
    """Map Redis case JSON to MySQL upsert parameters."""
    return (
        row.get("caseId"),
        row.get("sceneType"),
        row.get("symptom"),
        row.get("rootCause"),
        row.get("resolution"),
        row.get("effectivenessScore"),
        row.get("sourceFeedbackId"),
        normalize_timestamp(row.get("createdAt")),
    )


def build_params(name: str, rows: list[dict[str, Any]]) -> list[tuple[Any, ...]]:
    """Convert Redis rows into MySQL executemany parameter tuples."""
    mapper = {
        "diagnosis": to_diagnosis_params,
        "feedback": to_feedback_params,
        "cases": to_case_params,
    }[name]
    return [mapper(row) for row in rows]


def build_redis_adapter(settings: Settings) -> RedisSourceAdapter:
    """Create Redis adapter lazily for offline backfill."""
    try:
        import redis  # pylint: disable=import-error,import-outside-toplevel
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "Missing dependency 'redis'. Install training workspace dependencies before running backfill."
        ) from exc

    client = redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password,
        decode_responses=False,
    )
    return RedisSourceAdapter(client)


def get_mysql_connection(settings: Settings):
    """Create a MySQL connection for batch upserts."""
    try:
        import pymysql  # pylint: disable=import-error,import-outside-toplevel
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "Missing dependency 'PyMySQL'. Install training workspace dependencies before running backfill."
        ) from exc

    return pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_database,
        autocommit=False,
    )


def execute_backfill(
    settings: Settings,
    scene: str | None,
    limit: int | None,
    offset: int,
    batch_size: int,
    dry_run: bool,
) -> list[BackfillStats]:
    """Run Redis-to-MySQL backfill and return per-source stats."""
    adapter = build_redis_adapter(settings)
    collected: dict[str, list[dict[str, Any]]] = {}
    stats: list[BackfillStats] = []
    for name, key in REDIS_KEYS.items():
        rows = adapter.export_hash(key, scene=scene, limit=limit, offset=offset)
        collected[name] = rows
        stats.append(BackfillStats(name=name, redis_key=key, scanned=len(rows), written=0))
    if dry_run:
        return stats

    connection = get_mysql_connection(settings)
    try:
        with connection.cursor() as cursor:
            updated_stats: list[BackfillStats] = []
            for stat in stats:
                params = build_params(stat.name, collected[stat.name])
                written = 0
                for start in range(0, len(params), max(batch_size, 1)):
                    batch = params[start:start + max(batch_size, 1)]
                    if not batch:
                        continue
                    cursor.executemany(UPSERT_SQL[stat.name], batch)
                    written += len(batch)
                updated_stats.append(
                    BackfillStats(
                        name=stat.name,
                        redis_key=stat.redis_key,
                        scanned=stat.scanned,
                        written=written,
                    )
                )
            connection.commit()
            return updated_stats
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def write_backfill_manifest(
    settings: Settings,
    scene: str | None,
    limit: int | None,
    offset: int,
    batch_size: int,
    dry_run: bool,
    stats: list[BackfillStats],
) -> Path:
    """Persist a backfill manifest for auditability."""
    output_path = settings.project_root / "training" / "data" / "backfill" / "redis_to_mysql_manifest.json"
    write_manifest(
        output_path,
        {
            "scene": scene,
            "limit": limit,
            "offset": offset,
            "batchSize": batch_size,
            "dryRun": dry_run,
            "sources": [
                {
                    "name": stat.name,
                    "redisKey": stat.redis_key,
                    "scanned": stat.scanned,
                    "written": stat.written,
                }
                for stat in stats
            ],
        },
    )
    return output_path


def main(argv: list[str] | None = None) -> None:
    """CLI entry for Redis-to-MySQL AI data backfill."""
    args = parse_args(argv)
    settings = load_settings()
    stats = execute_backfill(
        settings=settings,
        scene=args.scene,
        limit=args.limit,
        offset=args.offset,
        batch_size=args.batch_size,
        dry_run=args.dry_run,
    )
    write_backfill_manifest(
        settings=settings,
        scene=args.scene,
        limit=args.limit,
        offset=args.offset,
        batch_size=args.batch_size,
        dry_run=args.dry_run,
        stats=stats,
    )


if __name__ == "__main__":
    main()
