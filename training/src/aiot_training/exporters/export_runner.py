"""Export diagnosis, feedback, and case records from runtime stores."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from ..common.manifest import sha256_of_rows, write_manifest
from ..common.settings import load_settings
from .base import SourceAdapter
from .mysql_source import MysqlSourceAdapter
from .redis_source import RedisSourceAdapter


REDIS_KEYS = {
    "diagnosis": "aiot:ai:diagnosis-records",
    "feedback": "aiot:ai:feedback-records",
    "cases": "aiot:ai:case-records",
}


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def build_source_adapter(settings, source: str) -> SourceAdapter:
    if source == "mysql":
        return MysqlSourceAdapter(
            host=settings.mysql_host,
            port=settings.mysql_port,
            user=settings.mysql_user,
            password=settings.mysql_password,
            database=settings.mysql_database,
        )
    import redis  # pylint: disable=import-error,import-outside-toplevel

    client = redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password,
        decode_responses=False,
    )
    return RedisSourceAdapter(client)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    parser.add_argument("--source", choices=["redis", "mysql"], default="mysql")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--offset", type=int, default=0)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)

    settings = load_settings()
    adapter = build_source_adapter(settings, args.source)
    raw_dir = settings.project_root / "training" / "data" / "raw"
    manifest = {
        "scene": args.scene,
        "datasetVersion": settings.dataset_version,
        "sourceType": args.source,
        "limit": args.limit,
        "offset": args.offset,
        "sources": [],
    }
    for name, key in REDIS_KEYS.items():
        rows = adapter.export_hash(
            key,
            scene=args.scene,
            limit=args.limit,
            offset=args.offset,
        )
        output_path = raw_dir / name / f"{args.scene.lower()}.jsonl"
        write_jsonl(output_path, rows)
        manifest["sources"].append(
            {
                "name": name,
                "key": key,
                "rowCount": len(rows),
                "sha256": sha256_of_rows(rows),
                "output": str(output_path.relative_to(settings.project_root)),
            }
        )
    write_manifest(raw_dir / "manifests" / f"{args.scene.lower()}_manifest.json", manifest)


if __name__ == "__main__":
    main()
