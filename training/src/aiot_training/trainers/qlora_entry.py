"""Pre-training dataset gate for the future QLoRA integration.

Reads the built SFT dataset, verifies each sample's output carries the
required diagnosis fields, and reports dataset readiness before training.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from ..common.settings import load_settings


REQUIRED_FIELDS = {
    "sceneType",
    "summary",
    "rootCauseCategory",
    "confidence",
    "evidence",
    "recommendedActions",
    "ruleDraftable",
    "riskLevel",
}

SENSITIVE_KEYS = {
    "devicesecret",
    "device_secret",
    "token",
    "access_token",
    "accesstoken",
    "refresh_token",
    "refreshtoken",
    "secret",
    "password",
    "passwd",
    "apikey",
    "api_key",
    "accesskey",
    "access_key",
    "phone",
    "phonenumber",
    "phone_number",
    "mobile",
    "手机号",
}

PHONE_PATTERN = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")

MIN_TOTAL_SAMPLES = 1
MIN_FIELD_COMPLETE_RATE = 1.0
MAX_EXAMPLES = 20


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    rows: list[dict] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                rows.append({"_parseError": True, "raw": line.strip()})
    return rows


def as_dict(payload: object) -> dict:
    if isinstance(payload, dict):
        return payload
    if not isinstance(payload, str) or not payload.strip():
        return {}
    try:
        value = json.loads(payload)
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def find_sensitive_hits(obj: object, path: str = "") -> list[dict]:
    hits: list[dict] = []
    if isinstance(obj, dict):
        for key, value in obj.items():
            child_path = f"{path}.{key}" if path else str(key)
            if str(key).lower() in SENSITIVE_KEYS:
                hits.append({"field": child_path, "reason": f"敏感字段名: {key}"})
            hits.extend(find_sensitive_hits(value, child_path))
    elif isinstance(obj, list):
        for index, value in enumerate(obj):
            hits.extend(find_sensitive_hits(value, f"{path}[{index}]"))
    elif isinstance(obj, str) and PHONE_PATTERN.search(obj):
        hits.append({"field": path, "reason": "疑似手机号"})
    return hits


def build_train_ready_report(rows: list[dict], scene: str) -> dict:
    total = len(rows)
    field_coverage = {field: 0 for field in sorted(REQUIRED_FIELDS)}
    field_complete = 0
    missing_field_samples: list[dict] = []
    sensitive_samples: list[dict] = []

    for index, row in enumerate(rows):
        if row.get("_parseError"):
            missing_field_samples.append(
                {"sampleIndex": index, "missingFields": ["<json parse error>"]}
            )
            continue

        output = as_dict(row.get("output"))
        missing = [field for field in REQUIRED_FIELDS if field not in output]
        for field in REQUIRED_FIELDS:
            if field in output:
                field_coverage[field] += 1
        if missing:
            missing_field_samples.append({"sampleIndex": index, "missingFields": missing})
        else:
            field_complete += 1

        hits = find_sensitive_hits(row)
        if hits:
            sensitive_samples.append({"sampleIndex": index, "hits": hits})

    field_complete_rate = 0 if total == 0 else field_complete / total

    reasons: list[str] = []
    if total < MIN_TOTAL_SAMPLES:
        reasons.append(f"样本数 {total} 少于最小值 {MIN_TOTAL_SAMPLES}")
    if field_complete_rate < MIN_FIELD_COMPLETE_RATE:
        reasons.append(
            f"字段完整率 {field_complete_rate:.4f} 低于阈值 {MIN_FIELD_COMPLETE_RATE}"
        )
    if sensitive_samples:
        reasons.append(f"发现 {len(sensitive_samples)} 条样本包含敏感字段")

    return {
        "scene": scene,
        "totalSamples": total,
        "fieldCompleteSamples": field_complete,
        "fieldCompleteRate": field_complete_rate,
        "fieldCoverage": field_coverage,
        "missingFieldSamples": missing_field_samples[:MAX_EXAMPLES],
        "sensitive": {
            "samplesWithSensitiveFields": len(sensitive_samples),
            "samples": sensitive_samples[:MAX_EXAMPLES],
        },
        "gatePassed": not reasons,
        "reasons": reasons,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    args = parser.parse_args()

    settings = load_settings()
    dataset_path = (
        settings.project_root
        / "training"
        / "data"
        / "gold"
        / "sft"
        / f"{args.scene.lower()}_sft.jsonl"
    )
    rows = read_jsonl(dataset_path)
    report = build_train_ready_report(rows, args.scene)
    report["datasetPath"] = str(dataset_path.relative_to(settings.project_root))

    reports_dir = settings.project_root / "training" / "data" / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    report_path = reports_dir / f"{args.scene.lower()}_train_ready.json"
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
