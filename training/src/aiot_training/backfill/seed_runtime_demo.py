"""Seed minimal AI runtime data into Redis/MySQL for migration checks."""

from __future__ import annotations

import argparse
import json

from aiot_training.backfill.redis_to_mysql import REDIS_KEYS
from aiot_training.common.settings import Settings, load_settings

IDENTIFIER_FIELDS = {
    "diagnosis": "diagnosisId",
    "feedback": "feedbackId",
    "cases": "caseId",
}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse CLI arguments for demo runtime data seeding."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    parser.add_argument("--reset-redis", action="store_true")
    parser.add_argument("--reset-mysql", action="store_true")
    return parser.parse_args(argv)


def build_demo_records(scene: str) -> dict[str, list[dict]]:
    """Build a minimal linked diagnosis/feedback/case dataset."""
    normalized_scene = scene.upper()
    diagnosis_id = f"{normalized_scene.lower()}-diag-1"
    feedback_id = f"{normalized_scene.lower()}-fb-1"
    case_id = f"{normalized_scene.lower()}-case-1"
    created_at = 1_786_000_000_000
    return {
        "diagnosis": [
            {
                "diagnosisId": diagnosis_id,
                "traceId": f"{normalized_scene.lower()}-trace-1",
                "sceneType": normalized_scene,
                "deviceId": "dev-seed-1",
                "homeId": "home-seed-1",
                "eventId": "evt-seed-1",
                "contextSnapshot": json.dumps({"productKey": "pk-seed-1", "onlineStatus": "OFFLINE"}, ensure_ascii=False),
                "modelName": "seed-model",
                "promptVersion": "seed-v1",
                "diagnosisResult": json.dumps(
                    {
                        "sceneType": normalized_scene,
                        "summary": "Seeded diagnosis for migration gate",
                        "rootCauseCategory": "NETWORK_INSTABILITY",
                        "confidence": 0.91,
                        "recommendedActions": ["检查网络", "检查供电"],
                    },
                    ensure_ascii=False,
                ),
                "latencyMs": 12,
                "createdAt": created_at,
            }
        ],
        "feedback": [
            {
                "feedbackId": feedback_id,
                "diagnosisId": diagnosis_id,
                "feedbackType": "ACCEPTED",
                "resolutionStatus": "SOLVED",
                "operatorId": "seed-operator",
                "resolutionNote": "Seeded fix confirmed",
                "createdAt": created_at + 1_000,
            }
        ],
        "cases": [
            {
                "caseId": case_id,
                "sceneType": normalized_scene,
                "symptom": "Seeded offline flap symptom",
                "rootCause": "NETWORK_INSTABILITY",
                "resolution": "重启网关并排查供电",
                "effectivenessScore": 1.0,
                "sourceFeedbackId": feedback_id,
                "createdAt": created_at + 2_000,
            }
        ],
    }


def get_redis_client(settings: Settings):
    """Create Redis client lazily for seed operations."""
    import redis  # pylint: disable=import-error,import-outside-toplevel

    return redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password,
        decode_responses=True,
    )


def get_mysql_connection(settings: Settings):
    """Create MySQL connection lazily for optional reset operations."""
    import pymysql  # pylint: disable=import-error,import-outside-toplevel

    return pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password,
        database=settings.mysql_database,
        autocommit=False,
    )


def seed_redis(settings: Settings, records: dict[str, list[dict]], reset_redis: bool) -> None:
    """Write demo runtime data to Redis hashes."""
    client = get_redis_client(settings)
    if reset_redis:
        client.delete(*REDIS_KEYS.values())
    for name, key in REDIS_KEYS.items():
        for row in records[name]:
            identifier = row[IDENTIFIER_FIELDS[name]]
            client.hset(key, identifier, json.dumps(row, ensure_ascii=False))


def reset_mysql(settings: Settings) -> None:
    """Truncate AI persistence tables for deterministic checks."""
    connection = get_mysql_connection(settings)
    try:
        with connection.cursor() as cursor:
            cursor.execute("DELETE FROM ai_case_library")
            cursor.execute("DELETE FROM ai_feedback_record")
            cursor.execute("DELETE FROM ai_diagnosis_record")
        connection.commit()
    finally:
        connection.close()


def main(argv: list[str] | None = None) -> None:
    """CLI entry for seeding demo runtime data."""
    args = parse_args(argv)
    settings = load_settings()
    if args.reset_mysql:
        reset_mysql(settings)
    seed_redis(settings, build_demo_records(args.scene), args.reset_redis)


if __name__ == "__main__":
    main()
