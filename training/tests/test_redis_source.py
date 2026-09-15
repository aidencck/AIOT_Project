"""Tests for Redis source adapter scene filtering."""

# pylint: disable=import-error

import importlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))


class FakeRedisClient:
    def __init__(self, hashes):
        self.hashes = hashes

    def hgetall(self, key):
        return self.hashes.get(key, {})


class RedisSourceAdapterTest(unittest.TestCase):
    def test_feedback_scene_filter_joins_diagnosis_scene(self) -> None:
        module = importlib.import_module("aiot_training.exporters.redis_source")
        client = FakeRedisClient(
            {
                module.DIAGNOSIS_KEY: {
                    "diag-1": json.dumps({"diagnosisId": "diag-1", "sceneType": "OFFLINE_FLAP"})
                },
                module.FEEDBACK_KEY: {
                    "fb-1": json.dumps({"feedbackId": "fb-1", "diagnosisId": "diag-1"})
                },
            }
        )
        adapter = module.RedisSourceAdapter(client)

        rows = adapter.export_hash(module.FEEDBACK_KEY, scene="OFFLINE_FLAP")

        self.assertEqual(1, len(rows))
        self.assertEqual("fb-1", rows[0]["feedbackId"])


if __name__ == "__main__":
    unittest.main()
