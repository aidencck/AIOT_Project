import json
import os
import subprocess
import sys
from pathlib import Path

import pytest


SRC_DIR = str(Path(__file__).resolve().parents[1] / "src")

VALID_OUTPUT = {
    "sceneType": "OFFLINE_FLAP",
    "summary": "设备周期性离线",
    "rootCauseCategory": "NETWORK_INSTABILITY",
    "confidence": 0.91,
    "evidence": ["离线事件频发", "信号强度弱"],
    "recommendedActions": ["检查网络", "检查供电"],
    "ruleDraftable": True,
    "riskLevel": "HIGH",
}

INCOMPLETE_OUTPUT = {
    "sceneType": "OFFLINE_FLAP",
    "summary": "设备周期性离线",
}


def write_samples(project_root: Path, rows: list[dict]) -> None:
    sft_dir = project_root / "training" / "data" / "gold" / "sft"
    sft_dir.mkdir(parents=True, exist_ok=True)
    path = sft_dir / "offline_flap_sft.jsonl"
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")


def run_gate(project_root: Path) -> dict:
    env = os.environ.copy()
    env["PYTHONPATH"] = SRC_DIR
    env["AIOT_PROJECT_ROOT"] = str(project_root)
    subprocess.run(
        [sys.executable, "-m", "aiot_training.trainers.qlora_entry", "--scene", "OFFLINE_FLAP"],
        check=True,
        cwd=str(project_root),
        env=env,
    )
    report_path = project_root / "training" / "data" / "reports" / "offline_flap_train_ready.json"
    return json.loads(report_path.read_text(encoding="utf-8"))


def sample(output: dict, metadata: dict | None = None) -> dict:
    return {
        "instruction": "请基于设备上下文输出结构化诊断结果",
        "input": {"sceneType": "OFFLINE_FLAP"},
        "output": output,
        "metadata": metadata or {},
    }


def test_gate_passes_on_clean_dataset(tmp_path: Path) -> None:
    write_samples(tmp_path, [sample(VALID_OUTPUT)])
    report = run_gate(tmp_path)

    assert report["totalSamples"] == 1
    assert report["fieldCompleteSamples"] == 1
    assert report["fieldCompleteRate"] == pytest.approx(1.0)
    assert report["sensitive"]["samplesWithSensitiveFields"] == 0
    assert report["gatePassed"] is True
    assert report["reasons"] == []


def test_gate_fails_on_incomplete_and_sensitive_samples(tmp_path: Path) -> None:
    sensitive_sample = sample(
        VALID_OUTPUT,
        {"deviceSecret": "sk-secret-123", "contactPhone": "13800138000"},
    )
    write_samples(
        tmp_path,
        [
            sample(VALID_OUTPUT),
            sample(INCOMPLETE_OUTPUT),
            sensitive_sample,
        ],
    )
    report = run_gate(tmp_path)

    assert report["totalSamples"] == 3
    assert report["fieldCompleteSamples"] == 2
    assert report["fieldCompleteRate"] == pytest.approx(2 / 3)
    assert report["fieldCoverage"]["evidence"] == 2
    assert report["sensitive"]["samplesWithSensitiveFields"] == 1
    assert report["gatePassed"] is False
    assert report["reasons"]
