"""Tests for AI control-plane Redis drain helpers."""

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class DrainControlPlaneRedisTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module("aiot_training.backfill.drain_control_plane_redis")
        self.Settings = importlib.import_module("aiot_training.common.settings").Settings

    def build_settings(self, project_root: Path):
        return self.Settings(
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

    def test_execute_drain_dry_run_collects_pending_counts(self) -> None:
        class FakeRedis:
            def hgetall(self, key):
                if "mysql-write-outbox" in key:
                    return {"task-1": json.dumps({"taskId": "task-1"})}
                if "case-materialization-outbox" in key:
                    return {"task-2": json.dumps({"taskId": "task-2"}), "task-3": json.dumps({"taskId": "task-3"})}
                return {}

        settings = self.build_settings(Path("/tmp/aiot"))
        with patch.object(self.module, "build_redis_client", return_value=FakeRedis()):
            stats = self.module.execute_drain(
                settings=settings,
                dry_run=True,
                delete_redis=False,
                batch_size=100,
            )

        self.assertEqual([1, 2], [item.redis_pending for item in stats])
        self.assertEqual([1, 2], [item.redis_remaining for item in stats])
        self.assertEqual([0, 0], [item.mysql_written for item in stats])

    def test_write_drain_report_persists_completion_flag(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            settings = self.build_settings(Path(temp_dir))
            report_path = self.module.write_drain_report(
                settings=settings,
                dry_run=False,
                delete_redis=True,
                batch_size=200,
                stats=[
                    self.module.DrainStats("mysqlWriteOutbox", "aiot:ai:mysql-write-outbox", 2, 2, 2, 0),
                    self.module.DrainStats("caseMaterialization", "aiot:ai:case-materialization-outbox", 1, 1, 1, 0),
                ],
            )
            content = json.loads(report_path.read_text(encoding="utf-8"))

        self.assertTrue(content["controlPlaneRedisDrainCompleted"])
        self.assertEqual(2, content["stores"][0]["mysqlWritten"])


if __name__ == "__main__":
    unittest.main()
