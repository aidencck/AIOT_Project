"""Tests for Redis-to-MySQL backfill helpers."""

# pylint: disable=import-error,unused-argument

from datetime import datetime
import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class RedisToMysqlBackfillTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module("aiot_training.backfill.redis_to_mysql")

    def test_normalize_json_document_keeps_valid_json(self) -> None:
        normalized = self.module.normalize_json_document('{"foo":"bar"}')

        self.assertEqual('{"foo": "bar"}', normalized)

    def test_build_diagnosis_params_normalizes_payloads(self) -> None:
        params = self.module.to_diagnosis_params(
            {
                "diagnosisId": "diag-1",
                "traceId": "trace-1",
                "sceneType": "OFFLINE_FLAP",
                "deviceId": "dev-1",
                "homeId": "home-1",
                "eventId": "evt-1",
                "contextSnapshot": '{"productKey":"pk-1"}',
                "modelName": "model-a",
                "promptVersion": "v1",
                "diagnosisResult": {"summary": "ok"},
                "latencyMs": 15,
                "createdAt": "1000",
            }
        )

        self.assertEqual("diag-1", params[0])
        self.assertEqual('{"productKey": "pk-1"}', params[6])
        self.assertEqual('{"summary": "ok"}', params[9])
        self.assertEqual(datetime(1970, 1, 1, 0, 16, 40), params[11])

    def test_execute_backfill_dry_run_collects_stats_without_mysql(self) -> None:
        Settings = importlib.import_module("aiot_training.common.settings").Settings
        settings = Settings(
            project_root=Path("/tmp/aiot"),
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
            def export_hash(self, key, scene=None, limit=None, offset=0):
                if "diagnosis" in key:
                    return [{"diagnosisId": "diag-1"}]
                if "feedback" in key:
                    return [{"feedbackId": "fb-1"}]
                return []

        with patch.object(self.module, "build_redis_adapter", return_value=FakeAdapter()):
            stats = self.module.execute_backfill(
                settings=settings,
                scene="OFFLINE_FLAP",
                limit=None,
                offset=0,
                batch_size=100,
                dry_run=True,
            )

        self.assertEqual([1, 1, 0], [item.scanned for item in stats])
        self.assertEqual([0, 0, 0], [item.written for item in stats])

    def test_write_backfill_manifest_persists_stats(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            Settings = importlib.import_module("aiot_training.common.settings").Settings
            settings = Settings(
                project_root=Path(temp_dir),
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
            path = self.module.write_backfill_manifest(
                settings=settings,
                scene="OFFLINE_FLAP",
                limit=10,
                offset=2,
                batch_size=50,
                dry_run=False,
                stats=[
                    self.module.BackfillStats("diagnosis", "aiot:ai:diagnosis-records", 10, 10),
                    self.module.BackfillStats("feedback", "aiot:ai:feedback-records", 8, 8),
                ],
            )
            content = json.loads(path.read_text(encoding="utf-8"))

        self.assertEqual("OFFLINE_FLAP", content["scene"])
        self.assertEqual(2, len(content["sources"]))
        self.assertEqual(10, content["sources"][0]["written"])


if __name__ == "__main__":
    unittest.main()
