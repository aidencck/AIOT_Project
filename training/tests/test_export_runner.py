"""Tests for exporter argument defaults."""

# pylint: disable=import-error

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
import importlib


class ExportRunnerArgsTest(unittest.TestCase):
    def test_parse_args_defaults_to_mysql_source(self) -> None:
        parse_args = importlib.import_module("aiot_training.exporters.export_runner").parse_args
        args = parse_args([])

        self.assertEqual("OFFLINE_FLAP", args.scene)
        self.assertEqual("mysql", args.source)
        self.assertIsNone(args.limit)
        self.assertEqual(0, args.offset)


if __name__ == "__main__":
    unittest.main()
