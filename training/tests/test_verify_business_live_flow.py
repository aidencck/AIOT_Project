"""Tests for AI business live flow report indexing helpers."""

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class VerifyBusinessLiveFlowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module(
            "aiot_training.live_proof.verify_business_flow"
        )

    def test_update_history_index_keeps_recent_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            report_path = Path(temp_dir) / "ai_business_live_flow.json"
            index_path = self.module.resolve_history_index_path(report_path)

            self.module.update_history_index(
                index_path,
                {
                    "reportType": "BUSINESS_LIVE_FLOW",
                    "occurredAt": 200,
                    "reportPath": str(Path(temp_dir) / "history" / "report-200.json"),
                    "operator": "ops-admin",
                    "scene": "OFFLINE_FLAP",
                    "success": True,
                    "accepted": None,
                    "dryRun": None,
                    "message": "newest",
                },
            )
            self.module.update_history_index(
                index_path,
                {
                    "reportType": "BUSINESS_LIVE_FLOW",
                    "occurredAt": 100,
                    "reportPath": str(Path(temp_dir) / "history" / "report-100.json"),
                    "operator": "ops-admin",
                    "scene": "OFFLINE_FLAP",
                    "success": False,
                    "accepted": None,
                    "dryRun": None,
                    "message": "older",
                },
            )

            payload = json.loads(index_path.read_text(encoding="utf-8"))

        self.assertEqual("BUSINESS_LIVE_FLOW", payload["reportType"])
        self.assertEqual(2, len(payload["entries"]))
        self.assertEqual("report-100.json", Path(payload["entries"][0]["reportPath"]).name)
        self.assertEqual("report-200.json", Path(payload["entries"][1]["reportPath"]).name)

    def test_resolve_archive_report_path_uses_history_folder(self) -> None:
        report_path = Path("/tmp/reports/ai_business_live_flow.json")

        archive_path = self.module.resolve_archive_report_path(report_path, 2233445566)

        self.assertEqual(
            Path("/tmp/reports/history/ai_business_live_flow_2233445566.json"),
            archive_path,
        )


if __name__ == "__main__":
    unittest.main()
