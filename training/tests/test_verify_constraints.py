"""Tests for MySQL constraint verification workflow."""

import importlib
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class VerifyConstraintsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = importlib.import_module("aiot_training.backfill.verify_constraints")
        self.settings_module = importlib.import_module("aiot_training.common.settings")

    def test_execute_expected_failure_marks_rejection(self) -> None:
        original_pymysql = sys.modules.get("pymysql")

        class FakeCursor:
            def execute(self, sql, params):
                raise RuntimeError("constraint failed")

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

        class FakeConnection:
            def cursor(self):
                return FakeCursor()

        sys.modules["pymysql"] = types.SimpleNamespace(
            err=types.SimpleNamespace(IntegrityError=RuntimeError)
        )
        try:
            report = self.module.execute_expected_failure(
                FakeConnection(),
                "orphan_feedback_rejected",
                "INSERT ...",
                ("fb-1", "diag-1"),
            )
        finally:
            if original_pymysql is None:
                sys.modules.pop("pymysql", None)
            else:
                sys.modules["pymysql"] = original_pymysql

        self.assertTrue(report["passed"])
        self.assertIn("constraint failed", report["error"])

    def test_verify_constraints_writes_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            settings = self.settings_module.Settings(
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

            class FakeCursor:
                def __init__(self):
                    self.fetchone_calls = 0
                    self.constraint_rows = [
                        {
                            "TABLE_NAME": "ai_feedback_record",
                            "CONSTRAINT_NAME": "fk_ai_feedback_record_diagnosis_id",
                            "CONSTRAINT_TYPE": "FOREIGN KEY",
                        },
                        {
                            "TABLE_NAME": "ai_case_library",
                            "CONSTRAINT_NAME": "fk_ai_case_library_source_feedback_id",
                            "CONSTRAINT_TYPE": "FOREIGN KEY",
                        },
                        {
                            "TABLE_NAME": "ai_case_library",
                            "CONSTRAINT_NAME": "uk_ai_case_library_source_feedback_id",
                            "CONSTRAINT_TYPE": "UNIQUE",
                        },
                    ]

                def execute(self, sql, params=None):
                    self.sql = sql
                    self.params = params

                def fetchall(self):
                    return self.constraint_rows

                def fetchone(self):
                    self.fetchone_calls += 1
                    return {"total": 1}

                def __enter__(self):
                    return self

                def __exit__(self, exc_type, exc, tb):
                    return False

            class FakeConnection:
                def __init__(self):
                    self.cursor_instance = FakeCursor()
                    self.closed = False

                def cursor(self):
                    return self.cursor_instance

                def close(self):
                    self.closed = True

            fake_connection = FakeConnection()
            original_get_mysql_connection = self.module.get_mysql_connection
            original_execute_expected_failure = self.module.execute_expected_failure
            try:
                self.module.get_mysql_connection = lambda _settings: fake_connection
                self.module.execute_expected_failure = lambda *_args, **_kwargs: {
                    "name": "synthetic-check",
                    "passed": True,
                    "error": "constraint rejected",
                }
                report = self.module.verify_constraints(settings, "OFFLINE_FLAP")
            finally:
                self.module.get_mysql_connection = original_get_mysql_connection
                self.module.execute_expected_failure = original_execute_expected_failure

            report_path = project_root / "training" / "data" / "reports" / "persistence" / "mysql_constraints_verification.json"
            self.assertTrue(report["gatePassed"])
            self.assertTrue(report_path.exists())
            loaded = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertTrue(loaded["gatePassed"])
            self.assertTrue(fake_connection.closed)


if __name__ == "__main__":
    unittest.main()
