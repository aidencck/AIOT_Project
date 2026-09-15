"""Tests for export verification workflow."""

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class VerifyExportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module("aiot_training.exporters.verify_export")

    def test_verify_manifest_accepts_consistent_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            raw_dir = project_root / "training" / "data" / "raw"
            (raw_dir / "diagnosis").mkdir(parents=True, exist_ok=True)
            (raw_dir / "feedback").mkdir(parents=True, exist_ok=True)
            (raw_dir / "cases").mkdir(parents=True, exist_ok=True)
            (raw_dir / "manifests").mkdir(parents=True, exist_ok=True)

            outputs = {
                "diagnosis": [{"diagnosisId": "diag-1"}],
                "feedback": [{"feedbackId": "fb-1"}],
                "cases": [{"caseId": "case-1"}],
            }
            sources = []
            for name, rows in outputs.items():
                output_path = raw_dir / name / "offline_flap.jsonl"
                output_path.write_text(
                    "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
                    encoding="utf-8",
                )
                sources.append(
                    {
                        "name": name,
                        "key": f"aiot:ai:{name}-records",
                        "rowCount": len(rows),
                        "sha256": self.module.sha256_of_rows(rows),
                        "output": str(output_path.relative_to(project_root)),
                    }
                )
            (raw_dir / "manifests" / "offline_flap_manifest.json").write_text(
                json.dumps(
                    {
                        "scene": "OFFLINE_FLAP",
                        "sourceType": "mysql",
                        "sources": sources,
                    },
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )

            manifest = self.module.verify_manifest(project_root, "OFFLINE_FLAP", "mysql", True)

        self.assertEqual("mysql", manifest["sourceType"])

    def test_compare_source_outputs_generates_consistency_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            settings_module = importlib.import_module("aiot_training.common.settings")
            settings = settings_module.Settings(
                project_root=project_root,
                redis_host="127.0.0.1",
                redis_port=6379,
                redis_db=0,
                redis_password=None,
                mysql_host="127.0.0.1",
                mysql_port=3306,
                mysql_user="root",
                mysql_password=None,
                mysql_database="aiot_cloud",
                dataset_version="dev",
            )

            class FakeAdapter:
                def __init__(self, rows):
                    self.rows = rows

                def export_hash(self, key, scene=None, limit=None, offset=0):
                    return self.rows[key]

            redis_rows = {
                "aiot:ai:diagnosis-records": [{"diagnosisId": "diag-1", "sceneType": "OFFLINE_FLAP"}],
                "aiot:ai:feedback-records": [{"feedbackId": "fb-1", "diagnosisId": "diag-1"}],
                "aiot:ai:case-records": [{"caseId": "case-1", "sceneType": "OFFLINE_FLAP"}],
            }
            mysql_rows = {
                "aiot:ai:diagnosis-records": [{"diagnosis_id": "diag-1", "scene_type": "OFFLINE_FLAP"}],
                "aiot:ai:feedback-records": [{"feedback_id": "fb-2", "diagnosis_id": "diag-1"}],
                "aiot:ai:case-records": [{"case_id": "case-1", "scene_type": "OFFLINE_FLAP"}],
            }

            original_load_settings = self.module.load_settings
            original_build_source_adapter = self.module.build_source_adapter
            self.module.load_settings = lambda: settings
            self.module.build_source_adapter = lambda _settings, source: FakeAdapter(redis_rows if source == "redis" else mysql_rows)
            try:
                report = self.module.compare_source_outputs("OFFLINE_FLAP")
            finally:
                self.module.load_settings = original_load_settings
                self.module.build_source_adapter = original_build_source_adapter

            report_path = project_root / "training" / "data" / "reports" / "persistence" / "offline_flap_consistency.json"
            self.assertTrue(report_path.exists())
            self.assertFalse(report["consistent"])
            self.assertEqual(2, report["totalMismatchCount"])

    def test_verify_manifest_rejects_mismatched_row_count(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            raw_dir = project_root / "training" / "data" / "raw"
            (raw_dir / "diagnosis").mkdir(parents=True, exist_ok=True)
            (raw_dir / "feedback").mkdir(parents=True, exist_ok=True)
            (raw_dir / "cases").mkdir(parents=True, exist_ok=True)
            (raw_dir / "manifests").mkdir(parents=True, exist_ok=True)
            (raw_dir / "diagnosis" / "offline_flap.jsonl").write_text('{"diagnosisId":"diag-1"}\n', encoding="utf-8")
            (raw_dir / "feedback" / "offline_flap.jsonl").write_text('{"feedbackId":"fb-1"}\n', encoding="utf-8")
            (raw_dir / "cases" / "offline_flap.jsonl").write_text('{"caseId":"case-1"}\n', encoding="utf-8")
            (raw_dir / "manifests" / "offline_flap_manifest.json").write_text(
                json.dumps(
                    {
                        "scene": "OFFLINE_FLAP",
                        "sourceType": "mysql",
                        "sources": [
                            {
                                "name": "diagnosis",
                                "key": "aiot:ai:diagnosis-records",
                                "rowCount": 2,
                                "sha256": self.module.sha256_of_rows([{"diagnosisId": "diag-1"}]),
                                "output": "training/data/raw/diagnosis/offline_flap.jsonl",
                            },
                            {
                                "name": "feedback",
                                "key": "aiot:ai:feedback-records",
                                "rowCount": 1,
                                "sha256": self.module.sha256_of_rows([{"feedbackId": "fb-1"}]),
                                "output": "training/data/raw/feedback/offline_flap.jsonl",
                            },
                            {
                                "name": "cases",
                                "key": "aiot:ai:cases-records",
                                "rowCount": 1,
                                "sha256": self.module.sha256_of_rows([{"caseId": "case-1"}]),
                                "output": "training/data/raw/cases/offline_flap.jsonl",
                            },
                        ],
                    },
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(AssertionError, "rowCount mismatch"):
                self.module.verify_manifest(project_root, "OFFLINE_FLAP", "mysql", False)

    def test_normalize_row_parses_json_text_fields(self) -> None:
        normalized = self.module.normalize_row(
            {
                "id": 99,
                "contextSnapshot": '{"b":2,"a":1}',
                "diagnosisResult": '{"summary":"ok","confidence":0.9}',
            }
        )

        self.assertNotIn("id", normalized)
        self.assertEqual({"a": 1, "b": 2}, normalized["context_snapshot"])
        self.assertEqual({"summary": "ok", "confidence": 0.9}, normalized["diagnosis_result"])


if __name__ == "__main__":
    unittest.main()
