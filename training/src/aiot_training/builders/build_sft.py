"""Build a minimal SFT dataset from exported AI diagnosis records."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from ..common.settings import load_settings
from ..contracts.dataset_models import SftSample


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def load_json_object(payload: object) -> dict:
    if isinstance(payload, dict):
        return payload
    if not isinstance(payload, str) or not payload.strip():
        return {}
    try:
        value = json.loads(payload)
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def get_field(row: dict, *names: str):
    """兼容 camelCase 与 snake_case 字段名，避免 exporter 口径漂移。"""
    for name in names:
        if row.get(name) is not None:
            return row.get(name)
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    args = parser.parse_args()

    settings = load_settings()
    base = settings.project_root / "training" / "data"
    diagnosis_rows = read_jsonl(base / "raw" / "diagnosis" / f"{args.scene.lower()}.jsonl")
    feedback_rows = read_jsonl(base / "raw" / "feedback" / f"{args.scene.lower()}.jsonl")
    feedback_map = {
        get_field(row, "diagnosisId", "diagnosis_id"): row for row in feedback_rows
    }

    output_path = base / "gold" / "sft" / f"{args.scene.lower()}_sft.jsonl"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as handle:
        for row in diagnosis_rows:
            diagnosis_id = get_field(row, "diagnosisId", "diagnosis_id")
            feedback = feedback_map.get(diagnosis_id)
            context_snapshot = load_json_object(
                get_field(row, "contextSnapshot", "context_snapshot")
            )
            diagnosis_result = get_field(row, "diagnosisResult", "diagnosis_result")
            sample = SftSample(
                instruction="请基于设备上下文输出结构化诊断结果",
                input={
                    "sceneType": get_field(row, "sceneType", "scene_type"),
                    "contextSnapshot": get_field(row, "contextSnapshot", "context_snapshot"),
                    "eventId": get_field(row, "eventId", "event_id"),
                },
                output=json.loads(diagnosis_result if isinstance(diagnosis_result, str) else "{}"),
                metadata={
                    "diagnosisId": diagnosis_id,
                    "deviceId": get_field(row, "deviceId", "device_id"),
                    "homeId": get_field(row, "homeId", "home_id"),
                    "productKey": context_snapshot.get("productKey"),
                    "sceneType": get_field(row, "sceneType", "scene_type"),
                    "promptVersion": get_field(row, "promptVersion", "prompt_version"),
                    "feedbackType": (
                        None if feedback is None else get_field(feedback, "feedbackType", "feedback_type")
                    ),
                    "resolutionStatus": (
                        None
                        if feedback is None
                        else get_field(feedback, "resolutionStatus", "resolution_status")
                    ),
                },
            )
            handle.write(json.dumps(sample.to_dict(), ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
