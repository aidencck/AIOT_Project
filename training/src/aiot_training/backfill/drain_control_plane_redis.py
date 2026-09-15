"""Drain legacy AI control-plane Redis hashes into MySQL tables."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from typing import Any

from aiot_training.common.settings import Settings, load_settings

REDIS_KEYS = {
    "mysqlWriteOutbox": "aiot:ai:mysql-write-outbox",
    "caseMaterialization": "aiot:ai:case-materialization-outbox",
}

UPSERT_SQL = {
    "mysqlWriteOutbox": """
        INSERT INTO ai_mysql_write_outbox
        (task_id, entity_type, record_key, payload_json, last_error, failed_at, last_retry_at, retry_count)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        entity_type = VALUES(entity_type),
        record_key = VALUES(record_key),
        payload_json = VALUES(payload_json),
        last_error = VALUES(last_error),
        failed_at = VALUES(failed_at),
        last_retry_at = VALUES(last_retry_at),
        retry_count = VALUES(retry_count)
    """,
    "caseMaterialization": """
        INSERT INTO ai_case_materialization_task
        (task_id, diagnosis_id, feedback_id, feedback_type, resolution_status, resolution_note, operator_id, queued_at, last_retry_at, retry_count, last_error)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        diagnosis_id = VALUES(diagnosis_id),
        feedback_id = VALUES(feedback_id),
        feedback_type = VALUES(feedback_type),
        resolution_status = VALUES(resolution_status),
        resolution_note = VALUES(resolution_note),
        operator_id = VALUES(operator_id),
        queued_at = VALUES(queued_at),
        last_retry_at = VALUES(last_retry_at),
        retry_count = VALUES(retry_count),
        last_error = VALUES(last_error)
    """,
}


@dataclass(frozen=True)
class DrainStats:
    name: str
    redis_key: str
    redis_pending: int
    mysql_written: int
    redis_deleted: int
    redis_remaining: int


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--delete-redis", action="store_true")
    parser.add_argument("--batch-size", type=int, default=200)
    return parser.parse_args(argv)


def build_redis_client(settings: Settings):
    try:
        import redis  # pylint: disable=import-error,import-outside-toplevel
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "Missing dependency 'redis'. Install training workspace dependencies before draining control-plane Redis."
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
            "Missing dependency 'PyMySQL'. Install training workspace dependencies before draining control-plane Redis."
        ) from exc

    return pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_database,
        autocommit=False,
    )


def parse_task_json(payload: str, key_name: str, task_id: str) -> dict[str, Any]:
    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Invalid JSON payload for {key_name}:{task_id}") from exc
    if not isinstance(parsed, dict):
        raise ValueError(f"Unexpected payload type for {key_name}:{task_id}")
    return parsed


def to_mysql_outbox_params(task: dict[str, Any], task_id: str) -> tuple[Any, ...]:
    return (
        task.get("taskId") or task_id,
        task.get("entityType"),
        task.get("recordKey"),
        task.get("payloadJson"),
        task.get("lastError"),
        task.get("failedAt"),
        task.get("lastRetryAt"),
        task.get("retryCount", 0),
    )


def to_case_task_params(task: dict[str, Any], task_id: str) -> tuple[Any, ...]:
    return (
        task.get("taskId") or task_id,
        task.get("diagnosisId"),
        task.get("feedbackId"),
        task.get("feedbackType"),
        task.get("resolutionStatus"),
        task.get("resolutionNote"),
        task.get("operatorId"),
        task.get("queuedAt"),
        task.get("lastRetryAt"),
        task.get("retryCount", 0),
        task.get("lastError"),
    )


def build_params(name: str, entries: dict[str, str]) -> tuple[list[str], list[tuple[Any, ...]]]:
    ids: list[str] = []
    params: list[tuple[Any, ...]] = []
    mapper = {
        "mysqlWriteOutbox": to_mysql_outbox_params,
        "caseMaterialization": to_case_task_params,
    }[name]
    for task_id, payload in entries.items():
        parsed = parse_task_json(payload, name, task_id)
        ids.append(task_id)
        params.append(mapper(parsed, task_id))
    return ids, params


def execute_drain(
    settings: Settings,
    dry_run: bool,
    delete_redis: bool,
    batch_size: int,
) -> list[DrainStats]:
    redis_client = build_redis_client(settings)
    raw_entries = {name: redis_client.hgetall(key) for name, key in REDIS_KEYS.items()}
    if dry_run:
        return [
            DrainStats(
                name=name,
                redis_key=REDIS_KEYS[name],
                redis_pending=len(entries),
                mysql_written=0,
                redis_deleted=0,
                redis_remaining=len(entries),
            )
            for name, entries in raw_entries.items()
        ]

    connection = get_mysql_connection(settings)
    try:
        stats: list[DrainStats] = []
        with connection.cursor() as cursor:
            for name, entries in raw_entries.items():
                task_ids, params = build_params(name, entries)
                written = 0
                for start in range(0, len(params), max(batch_size, 1)):
                    batch = params[start:start + max(batch_size, 1)]
                    if not batch:
                        continue
                    cursor.executemany(UPSERT_SQL[name], batch)
                    written += len(batch)
                stats.append(
                    DrainStats(
                        name=name,
                        redis_key=REDIS_KEYS[name],
                        redis_pending=len(entries),
                        mysql_written=written,
                        redis_deleted=0,
                        redis_remaining=len(entries),
                    )
                )
            connection.commit()
        if delete_redis:
            updated_stats: list[DrainStats] = []
            for stat in stats:
                entries = raw_entries[stat.name]
                deleted = 0
                if entries:
                    deleted = int(redis_client.hdel(stat.redis_key, *entries.keys()))
                remaining = int(redis_client.hlen(stat.redis_key))
                updated_stats.append(
                    DrainStats(
                        name=stat.name,
                        redis_key=stat.redis_key,
                        redis_pending=stat.redis_pending,
                        mysql_written=stat.mysql_written,
                        redis_deleted=deleted,
                        redis_remaining=remaining,
                    )
                )
            return updated_stats
        return stats
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def write_drain_report(
    settings: Settings,
    dry_run: bool,
    delete_redis: bool,
    batch_size: int,
    stats: list[DrainStats],
) -> Any:
    report = {
        "dryRun": dry_run,
        "deleteRedis": delete_redis,
        "batchSize": batch_size,
        "stores": [
            {
                "name": item.name,
                "redisKey": item.redis_key,
                "redisPending": item.redis_pending,
                "mysqlWritten": item.mysql_written,
                "redisDeleted": item.redis_deleted,
                "redisRemaining": item.redis_remaining,
            }
            for item in stats
        ],
    }
    report["controlPlaneRedisDrainCompleted"] = all(item.redis_remaining == 0 for item in stats)
    report_dir = settings.project_root / "training" / "data" / "reports" / "persistence"
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path = report_dir / "ai_control_plane_redis_drain.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report_path


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    settings = load_settings()
    stats = execute_drain(
        settings=settings,
        dry_run=args.dry_run,
        delete_redis=args.delete_redis,
        batch_size=args.batch_size,
    )
    write_drain_report(
        settings=settings,
        dry_run=args.dry_run,
        delete_redis=args.delete_redis,
        batch_size=args.batch_size,
        stats=stats,
    )


if __name__ == "__main__":
    main()
