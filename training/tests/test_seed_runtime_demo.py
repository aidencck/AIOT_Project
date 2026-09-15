"""Tests for demo runtime seed data."""

import importlib
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, call, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class SeedRuntimeDemoTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module("aiot_training.backfill.seed_runtime_demo")

    def test_build_demo_records_keeps_linked_relationships(self) -> None:
        records = self.module.build_demo_records("offline_flap")

        diagnosis = records["diagnosis"][0]
        feedback = records["feedback"][0]
        case_item = records["cases"][0]

        self.assertEqual("OFFLINE_FLAP", diagnosis["sceneType"])
        self.assertEqual(diagnosis["diagnosisId"], feedback["diagnosisId"])
        self.assertEqual(feedback["feedbackId"], case_item["sourceFeedbackId"])

    def test_reset_mysql_deletes_child_tables_before_parent(self) -> None:
        cursor = MagicMock()
        connection = MagicMock()
        connection.cursor.return_value.__enter__.return_value = cursor

        with patch.object(self.module, "get_mysql_connection", return_value=connection):
            self.module.reset_mysql(MagicMock())

        cursor.execute.assert_has_calls(
            [
                call("DELETE FROM ai_case_library"),
                call("DELETE FROM ai_feedback_record"),
                call("DELETE FROM ai_diagnosis_record"),
            ]
        )
        connection.commit.assert_called_once()
        connection.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
