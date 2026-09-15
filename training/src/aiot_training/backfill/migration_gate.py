"""Gate AI persistence migration based on backfill, consistency, live constraints, and control-plane drain reports."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from aiot_training.common.settings import load_settings


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """Parse CLI arguments for migration gate evaluation."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--scene", default="OFFLINE_FLAP")
    parser.add_argument("--max-total-mismatch", type=int, default=0)
    parser.add_argument("--min-written-total", type=int, default=1)
    parser.add_argument("--allow-missing-backfill", action="store_true")
    return parser.parse_args(argv)


def read_json(path: Path) -> dict[str, Any]:
    """Read a JSON file or return an empty dict if missing."""
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def count_failed_constraint_checks(constraints_report: dict[str, Any]) -> int:
    """Count failed checks from the live MySQL constraints verification report."""
    failed = 0
    for key in ("constraintChecks", "behaviorChecks"):
        for item in constraints_report.get(key, []):
            if isinstance(item, dict) and not bool(item.get("passed")):
                failed += 1
    return failed


def evaluate_gate(
    project_root: Path,
    scene: str,
    max_total_mismatch: int,
    min_written_total: int,
    allow_missing_backfill: bool,
) -> dict[str, Any]:
    """Evaluate whether migration can progress to a stricter read mode."""
    backfill_path = (
        project_root / "training" / "data" / "backfill" / "redis_to_mysql_manifest.json"
    )
    consistency_path = (
        project_root / "training" / "data" / "reports" / "persistence" / f"{scene.lower()}_consistency.json"
    )
    constraints_path = (
        project_root / "training" / "data" / "reports" / "persistence" / "mysql_constraints_verification.json"
    )
    drain_path = (
        project_root / "training" / "data" / "reports" / "persistence" / "ai_control_plane_redis_drain.json"
    )
    backfill = read_json(backfill_path)
    consistency = read_json(consistency_path)
    constraints = read_json(constraints_path)
    drain = read_json(drain_path)

    backfill_written_total = sum(
        int(item.get("written", 0))
        for item in backfill.get("sources", [])
        if isinstance(item, dict)
    )
    total_mismatch = int(consistency.get("totalMismatchCount", 0)) if consistency else 0
    constraints_failed_count = count_failed_constraint_checks(constraints)
    backfill_exists = backfill_path.exists()
    consistency_exists = consistency_path.exists()
    constraints_exists = constraints_path.exists()
    constraints_gate_passed = bool(constraints.get("gatePassed")) if constraints else False
    drain_exists = drain_path.exists()
    control_plane_redis_drain_completed = bool(drain.get("controlPlaneRedisDrainCompleted")) if drain else False

    checks = {
        "backfillManifestPresent": backfill_exists or allow_missing_backfill,
        "consistencyReportPresent": consistency_exists,
        "constraintsReportPresent": constraints_exists,
        "controlPlaneDrainReportPresent": drain_exists,
        "writtenTotalReached": backfill_written_total >= min_written_total,
        "mismatchWithinThreshold": total_mismatch <= max_total_mismatch,
        "constraintsGatePassed": constraints_gate_passed,
        "controlPlaneRedisDrainCompleted": control_plane_redis_drain_completed,
    }
    gate_passed = all(checks.values())
    report = {
        "scene": scene,
        "gatePassed": gate_passed,
        "checks": checks,
        "thresholds": {
            "maxTotalMismatch": max_total_mismatch,
            "minWrittenTotal": min_written_total,
            "allowMissingBackfill": allow_missing_backfill,
        },
        "backfillManifestPath": str(backfill_path),
        "backfillManifestExists": backfill_exists,
        "backfillWrittenTotal": backfill_written_total,
        "consistencyReportPath": str(consistency_path),
        "consistencyReportExists": consistency_exists,
        "consistencyTotalMismatch": total_mismatch,
        "constraintsReportPath": str(constraints_path),
        "constraintsReportExists": constraints_exists,
        "constraintsGatePassed": constraints_gate_passed,
        "constraintsFailedCount": constraints_failed_count,
        "controlPlaneDrainReportPath": str(drain_path),
        "controlPlaneDrainReportExists": drain_exists,
        "controlPlaneRedisDrainCompleted": control_plane_redis_drain_completed,
    }
    report_dir = project_root / "training" / "data" / "reports" / "persistence"
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path = report_dir / f"{scene.lower()}_migration_gate.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def main(argv: list[str] | None = None) -> None:
    """CLI entry for migration gate evaluation."""
    args = parse_args(argv)
    settings = load_settings()
    report = evaluate_gate(
        project_root=settings.project_root,
        scene=args.scene,
        max_total_mismatch=args.max_total_mismatch,
        min_written_total=args.min_written_total,
        allow_missing_backfill=args.allow_missing_backfill,
    )
    if not report["gatePassed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
