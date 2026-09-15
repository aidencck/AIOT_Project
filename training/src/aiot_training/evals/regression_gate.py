"""Run a lightweight schema regression gate on built SFT datasets."""

from __future__ import annotations

import argparse
import json

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


def main() -> None:
    # 创建命令行参数解析器，用于处理脚本启动时传入的参数
    # 支持通过命令行传入场景参数，示例：python script.py --scene ONLINE_FLAP
    parser = argparse.ArgumentParser(description="对构建好的SFT数据集运行轻量级的模式回归检查，验证数据集是否符合预设的 schema 要求")
    parser.add_argument("--scene", default="OFFLINE_FLAP", help="指定要检查的场景类型，默认值为OFFLINE_FLAP")
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
    baseline_path = (
        settings.project_root / "training" / "configs" / "eval" / f"{args.scene.lower()}_baseline.json"
    )
    baseline = {}
    if baseline_path.exists():
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    total = 0
    passed = 0
    if dataset_path.exists():
        with dataset_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                if not line.strip():
                    continue
                total += 1
                row = json.loads(line)
                output = row.get("output", {})
                if REQUIRED_FIELDS.issubset(output.keys()):
                    passed += 1
    report = {
        "scene": args.scene,
        "total": total,
        "schema_passed": passed,
        "schema_pass_rate": 0 if total == 0 else passed / total,
        "baseline": baseline,
        "gatePassed": (
            total >= baseline.get("min_total_samples", 1)
            and (0 if total == 0 else passed / total)
            >= baseline.get("min_schema_pass_rate", 0.99)
        ),
    }
    reports_dir = settings.project_root / "training" / "data" / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    with (reports_dir / f"{args.scene.lower()}_regression_gate.json").open(
        "w", encoding="utf-8"
    ) as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
