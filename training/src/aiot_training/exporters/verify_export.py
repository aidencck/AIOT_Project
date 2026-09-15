"""Verify exported raw training artifacts and manifests."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from .export_runner import REDIS_KEYS, build_source_adapter
from ..common.manifest import sha256_of_rows
from ..common.settings import load_settings

KEY_FIELDS = {
    "diagnosis": "diagnosis_id",
    "feedback": "feedback_id",
    "cases": "case_id",
}

IGNORED_FIELDS = {"id"}
JSON_TEXT_FIELDS = {"context_snapshot", "diagnosis_result"}

FIELD_ALIASES = {
    "diagnosisId": "diagnosis_id",
    "traceId": "trace_id",
    "sceneType": "scene_type",
    "deviceId": "device_id",
    "homeId": "home_id",
    "eventId": "event_id",
    "contextSnapshot": "context_snapshot",
    "modelName": "model_name",
    "promptVersion": "prompt_version",
    "diagnosisResult": "diagnosis_result",
    "latencyMs": "latency_ms",
    "createdAt": "created_at",
    "feedbackId": "feedback_id",
    "resolutionStatus": "resolution_status",
    "feedbackType": "feedback_type",
    "operatorId": "operator_id",
    "resolutionNote": "resolution_note",
    "caseId": "case_id",
    "rootCause": "root_cause",
    "effectivenessScore": "effectiveness_score",
    "sourceFeedbackId": "source_feedback_id",
}


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse CLI arguments for export verification."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    parser.add_argument("--source", choices=["redis", "mysql"], default="mysql")
    parser.add_argument("--require-non-empty", action="store_true")
    parser.add_argument("--check-consistency", action="store_true")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--offset", type=int, default=0)
    return parser.parse_args(argv)


def read_json(path: Path) -> dict[str, Any]:
    """Read a JSON document from disk."""
    if not path.exists():
        raise FileNotFoundError(f"export manifest not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    """Read a JSONL file from disk."""
    if not path.exists():
        return []
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def normalize_row(row: dict[str, Any]) -> dict[str, Any]:
    """Normalize Redis/MySQL row shapes into a canonical snake_case dict."""
    normalized: dict[str, Any] = {}
    for key, value in row.items():
        if key in IGNORED_FIELDS:
            continue
        normalized_key = FIELD_ALIASES.get(key, key)
        normalized[normalized_key] = normalize_value(normalized_key, value)
    return normalized


def normalize_value(key: str, value: Any) -> Any:
    """Normalize values so Redis/MySQL compare on semantic content, not text formatting."""
    if key in JSON_TEXT_FIELDS and isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return value
    return value


def canonical_row_hash(row: dict[str, Any]) -> str:
    """Build a stable hash for a canonical row."""
    normalized = normalize_row(row)
    return sha256_of_rows([normalized])


def compare_source_outputs(
    scene: str,
    limit: int | None = None,
    offset: int = 0,
) -> dict[str, Any]:
    """Compare Redis and MySQL runtime exports and return a consistency report."""
    settings = load_settings()
    redis_adapter = build_source_adapter(settings, "redis")
    mysql_adapter = build_source_adapter(settings, "mysql")
    per_source: list[dict[str, Any]] = []
    total_mismatch = 0
    for name, key in REDIS_KEYS.items():
        redis_rows = redis_adapter.export_hash(key, scene=scene, limit=limit, offset=offset)
        mysql_rows = mysql_adapter.export_hash(key, scene=scene, limit=limit, offset=offset)
        key_field = KEY_FIELDS[name]
        redis_map = {
            str(normalize_row(row).get(key_field)): normalize_row(row)
            for row in redis_rows
            if normalize_row(row).get(key_field) is not None
        }
        mysql_map = {
            str(normalize_row(row).get(key_field)): normalize_row(row)
            for row in mysql_rows
            if normalize_row(row).get(key_field) is not None
        }
        redis_keys = set(redis_map.keys())
        mysql_keys = set(mysql_map.keys())
        shared_keys = sorted(redis_keys & mysql_keys)
        diff_keys = [
            item_key
            for item_key in shared_keys
            if canonical_row_hash(redis_map[item_key]) != canonical_row_hash(mysql_map[item_key])
        ]
        source_report = {
            "name": name,
            "redisCount": len(redis_rows),
            "mysqlCount": len(mysql_rows),
            "redisOnlyCount": len(redis_keys - mysql_keys),
            "mysqlOnlyCount": len(mysql_keys - redis_keys),
            "fieldMismatchCount": len(diff_keys),
            "redisOnlyKeysPreview": sorted(redis_keys - mysql_keys)[:20],
            "mysqlOnlyKeysPreview": sorted(mysql_keys - redis_keys)[:20],
            "fieldMismatchKeysPreview": diff_keys[:20],
        }
        total_mismatch += (
            source_report["redisOnlyCount"]
            + source_report["mysqlOnlyCount"]
            + source_report["fieldMismatchCount"]
        )
        per_source.append(source_report)
    report = {
        "scene": scene,
        "limit": limit,
        "offset": offset,
        "consistent": total_mismatch == 0,
        "totalMismatchCount": total_mismatch,
        "sources": per_source,
    }
    reports_dir = settings.project_root / "training" / "data" / "reports" / "persistence"
    reports_dir.mkdir(parents=True, exist_ok=True)
    report_path = reports_dir / f"{scene.lower()}_consistency.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def verify_manifest(project_root: Path, scene: str, source: str, require_non_empty: bool) -> dict[str, Any]:
    """Validate export manifest against generated JSONL outputs."""
    raw_dir = project_root / "training" / "data" / "raw"
    manifest_path = raw_dir / "manifests" / f"{scene.lower()}_manifest.json"
    manifest = read_json(manifest_path)
    if manifest.get("sourceType") != source:
        raise AssertionError(f"manifest sourceType mismatch: expected {source}, got {manifest.get('sourceType')}")
    sources = manifest.get("sources", [])
    if len(sources) != 3:
        raise AssertionError(f"expected 3 raw sources, got {len(sources)}")
    total_rows = 0
    for item in sources:
        output_path = project_root / item["output"]
        rows = read_jsonl(output_path)
        actual_count = len(rows)
        if actual_count != item.get("rowCount"):
            raise AssertionError(f"rowCount mismatch for {item['name']}: expected {item.get('rowCount')}, got {actual_count}")
        if sha256_of_rows(rows) != item.get("sha256"):
            raise AssertionError(f"sha256 mismatch for {item['name']}")
        total_rows += actual_count
    if require_non_empty and total_rows <= 0:
        raise AssertionError("expected non-empty export output")
    return manifest


def main(argv: list[str] | None = None) -> None:
    """CLI entry for export artifact verification."""
    args = parse_args(argv)
    settings = load_settings()
    verify_manifest(
        project_root=settings.project_root,
        scene=args.scene,
        source=args.source,
        require_non_empty=args.require_non_empty,
    )
    if args.check_consistency:
        compare_source_outputs(scene=args.scene, limit=args.limit, offset=args.offset)


if __name__ == "__main__":
    main()
