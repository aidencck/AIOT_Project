"""Build a minimal golden eval set from accepted and solved SFT samples."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from ..common.manifest import sha256_of_rows, write_manifest
from ..common.settings import load_settings


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def split_key(row: dict) -> str:
    metadata = row.get("metadata", {})
    return (
        metadata.get("deviceId")
        or metadata.get("homeId")
        or metadata.get("productKey")
        or metadata.get("diagnosisId")
        or ""
    )


def assign_split(row: dict) -> str:
    digest = hashlib.sha256(split_key(row).encode("utf-8")).hexdigest()
    bucket = int(digest[:8], 16) % 100
    if bucket < 70:
        return "train"
    if bucket < 85:
        return "val"
    return "test"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    args = parser.parse_args()

    settings = load_settings()
    base = settings.project_root / "training" / "data"
    sft_path = base / "gold" / "sft" / f"{args.scene.lower()}_sft.jsonl"
    rows = read_jsonl(sft_path)
    golden_rows = []
    train_rows = []
    split_counts = {"train": 0, "val": 0, "test": 0}
    for row in rows:
        if row.get("metadata", {}).get("resolutionStatus") != "SOLVED":
            continue
        if row.get("metadata", {}).get("feedbackType") not in {"ACCEPTED", "MODIFIED"}:
            continue
        row.setdefault("metadata", {})
        split = assign_split(row)
        row["metadata"]["split"] = split
        split_counts[split] += 1
        if split == "train":
            train_rows.append(row)
        else:
            golden_rows.append(row)

    output_path = base / "gold" / "eval" / f"{args.scene.lower()}_golden.jsonl"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        for row in golden_rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")

    train_output_path = base / "gold" / "sft" / f"{args.scene.lower()}_sft_train.jsonl"
    with train_output_path.open("w", encoding="utf-8") as handle:
        for row in train_rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")

    write_manifest(
        base / "gold" / "eval" / f"{args.scene.lower()}_golden_manifest.json",
        {
            "scene": args.scene,
            "datasetVersion": settings.dataset_version,
            "rowCount": len(golden_rows),
            "trainRowCount": len(train_rows),
            "evalRowCount": len(golden_rows),
            "splitCounts": split_counts,
            "sha256": sha256_of_rows(golden_rows),
            "source": str(sft_path.relative_to(settings.project_root)),
            "trainSource": str(train_output_path.relative_to(settings.project_root)),
        },
    )


if __name__ == "__main__":
    main()
