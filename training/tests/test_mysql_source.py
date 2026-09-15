"""Tests for MySQL exporter query construction."""

# pylint: disable=import-error,protected-access

from datetime import datetime
import sys
import unittest
from pathlib import Path
import importlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class MysqlSourceAdapterTest(unittest.TestCase):
    def test_build_query_filters_feedback_by_scene_via_diagnosis_join(self) -> None:
        MysqlSourceAdapter = importlib.import_module("aiot_training.exporters.mysql_source").MysqlSourceAdapter
        adapter = MysqlSourceAdapter("127.0.0.1", 3306, "root", "pwd", "aiot_cloud")

        sql, params = adapter._build_query("ai_feedback_record", "OFFLINE_FLAP", 100, 20)

        self.assertIn("JOIN ai_diagnosis_record d ON f.diagnosis_id = d.diagnosis_id", sql)
        self.assertIn("WHERE d.scene_type = %s", sql)
        self.assertIn("ORDER BY f.id ASC", sql)
        self.assertEqual(["OFFLINE_FLAP", 100, 20], params)

    def test_normalize_row_makes_mysql_values_json_safe(self) -> None:
        module = importlib.import_module("aiot_training.exporters.mysql_source")
        adapter = module.MysqlSourceAdapter("127.0.0.1", 3306, "root", "pwd", "aiot_cloud")

        row = adapter._normalize_row(
            {
                "created_at": datetime(2026, 8, 7, 1, 0, 0),
                "effectiveness_score": module.Decimal("0.95"),
            }
        )

        self.assertEqual(1786064400000, row["created_at"])
        self.assertEqual(0.95, row["effectiveness_score"])


if __name__ == "__main__":
    unittest.main()
