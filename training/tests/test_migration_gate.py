"""Tests for AI persistence migration gate."""

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class MigrationGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module("aiot_training.backfill.migration_gate")

    def test_evaluate_gate_passes_when_backfill_and_consistency_are_clean(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            backfill_path = project_root / "training" / "data" / "backfill"
            report_path = project_root / "training" / "data" / "reports" / "persistence"
            backfill_path.mkdir(parents=True, exist_ok=True)
            report_path.mkdir(parents=True, exist_ok=True)
            (backfill_path / "redis_to_mysql_manifest.json").write_text(
                json.dumps({"sources": [{"written": 5}, {"written": 6}]}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "offline_flap_consistency.json").write_text(
                json.dumps({"totalMismatchCount": 0, "consistent": True}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "mysql_constraints_verification.json").write_text(
                json.dumps(
                    {
                        "gatePassed": True,
                        "constraintChecks": [{"passed": True}],
                        "behaviorChecks": [{"passed": True}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (report_path / "ai_control_plane_redis_drain.json").write_text(
                json.dumps({"controlPlaneRedisDrainCompleted": True}, ensure_ascii=False),
                encoding="utf-8",
            )

            report = self.module.evaluate_gate(project_root, "OFFLINE_FLAP", 0, 5, False)

        self.assertTrue(report["gatePassed"])
        self.assertEqual(11, report["backfillWrittenTotal"])
        self.assertTrue(report["constraintsGatePassed"])
        self.assertEqual(0, report["constraintsFailedCount"])

    def test_evaluate_gate_fails_when_mismatch_exceeds_threshold(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            backfill_path = project_root / "training" / "data" / "backfill"
            report_path = project_root / "training" / "data" / "reports" / "persistence"
            backfill_path.mkdir(parents=True, exist_ok=True)
            report_path.mkdir(parents=True, exist_ok=True)
            (backfill_path / "redis_to_mysql_manifest.json").write_text(
                json.dumps({"sources": [{"written": 9}]}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "offline_flap_consistency.json").write_text(
                json.dumps({"totalMismatchCount": 3, "consistent": False}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "mysql_constraints_verification.json").write_text(
                json.dumps(
                    {
                        "gatePassed": True,
                        "constraintChecks": [{"passed": True}],
                        "behaviorChecks": [{"passed": True}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (report_path / "ai_control_plane_redis_drain.json").write_text(
                json.dumps({"controlPlaneRedisDrainCompleted": True}, ensure_ascii=False),
                encoding="utf-8",
            )

            report = self.module.evaluate_gate(project_root, "OFFLINE_FLAP", 0, 1, False)

        self.assertFalse(report["gatePassed"])
        self.assertEqual(3, report["consistencyTotalMismatch"])

    def test_evaluate_gate_fails_when_constraints_report_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            backfill_path = project_root / "training" / "data" / "backfill"
            report_path = project_root / "training" / "data" / "reports" / "persistence"
            backfill_path.mkdir(parents=True, exist_ok=True)
            report_path.mkdir(parents=True, exist_ok=True)
            (backfill_path / "redis_to_mysql_manifest.json").write_text(
                json.dumps({"sources": [{"written": 5}]}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "offline_flap_consistency.json").write_text(
                json.dumps({"totalMismatchCount": 0, "consistent": True}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "ai_control_plane_redis_drain.json").write_text(
                json.dumps({"controlPlaneRedisDrainCompleted": True}, ensure_ascii=False),
                encoding="utf-8",
            )

            report = self.module.evaluate_gate(project_root, "OFFLINE_FLAP", 0, 1, False)

        self.assertFalse(report["gatePassed"])
        self.assertFalse(report["constraintsReportExists"])
        self.assertFalse(report["checks"]["constraintsReportPresent"])

    def test_evaluate_gate_exposes_failed_constraint_count(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            backfill_path = project_root / "training" / "data" / "backfill"
            report_path = project_root / "training" / "data" / "reports" / "persistence"
            backfill_path.mkdir(parents=True, exist_ok=True)
            report_path.mkdir(parents=True, exist_ok=True)
            (backfill_path / "redis_to_mysql_manifest.json").write_text(
                json.dumps({"sources": [{"written": 5}]}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "offline_flap_consistency.json").write_text(
                json.dumps({"totalMismatchCount": 0, "consistent": True}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "mysql_constraints_verification.json").write_text(
                json.dumps(
                    {
                        "gatePassed": False,
                        "constraintChecks": [{"passed": False}, {"passed": True}],
                        "behaviorChecks": [{"passed": False}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (report_path / "ai_control_plane_redis_drain.json").write_text(
                json.dumps({"controlPlaneRedisDrainCompleted": True}, ensure_ascii=False),
                encoding="utf-8",
            )

            report = self.module.evaluate_gate(project_root, "OFFLINE_FLAP", 0, 1, False)

        self.assertFalse(report["gatePassed"])
        self.assertFalse(report["constraintsGatePassed"])
        self.assertEqual(2, report["constraintsFailedCount"])

    def test_evaluate_gate_fails_when_control_plane_drain_not_completed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            backfill_path = project_root / "training" / "data" / "backfill"
            report_path = project_root / "training" / "data" / "reports" / "persistence"
            backfill_path.mkdir(parents=True, exist_ok=True)
            report_path.mkdir(parents=True, exist_ok=True)
            (backfill_path / "redis_to_mysql_manifest.json").write_text(
                json.dumps({"sources": [{"written": 5}]}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "offline_flap_consistency.json").write_text(
                json.dumps({"totalMismatchCount": 0, "consistent": True}, ensure_ascii=False),
                encoding="utf-8",
            )
            (report_path / "mysql_constraints_verification.json").write_text(
                json.dumps(
                    {
                        "gatePassed": True,
                        "constraintChecks": [{"passed": True}],
                        "behaviorChecks": [{"passed": True}],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (report_path / "ai_control_plane_redis_drain.json").write_text(
                json.dumps({"controlPlaneRedisDrainCompleted": False}, ensure_ascii=False),
                encoding="utf-8",
            )

            report = self.module.evaluate_gate(project_root, "OFFLINE_FLAP", 0, 1, False)

        self.assertFalse(report["gatePassed"])
        self.assertFalse(report["controlPlaneRedisDrainCompleted"])
        self.assertFalse(report["checks"]["controlPlaneRedisDrainCompleted"])


if __name__ == "__main__":
    unittest.main()
