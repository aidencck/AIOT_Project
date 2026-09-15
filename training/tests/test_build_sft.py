import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


class BuildSftTest(unittest.TestCase):
    def test_build_sft_enriches_device_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            raw_dir = project_root / "training" / "data" / "raw"
            diagnosis_dir = raw_dir / "diagnosis"
            feedback_dir = raw_dir / "feedback"
            diagnosis_dir.mkdir(parents=True, exist_ok=True)
            feedback_dir.mkdir(parents=True, exist_ok=True)

            diagnosis_rows = [
                {
                    "diagnosisId": "diag-1",
                    "sceneType": "OFFLINE_FLAP",
                    "deviceId": "dev-1",
                    "homeId": "home-1",
                    "eventId": "evt-1",
                    "promptVersion": "v1",
                    "contextSnapshot": json.dumps({"productKey": "pk-1"}, ensure_ascii=False),
                    "diagnosisResult": json.dumps({"sceneType": "OFFLINE_FLAP", "summary": "ok"}, ensure_ascii=False),
                }
            ]
            feedback_rows = [
                {
                    "diagnosisId": "diag-1",
                    "feedbackType": "ACCEPTED",
                    "resolutionStatus": "SOLVED",
                }
            ]
            (diagnosis_dir / "offline_flap.jsonl").write_text(
                "\n".join(json.dumps(row, ensure_ascii=False) for row in diagnosis_rows) + "\n",
                encoding="utf-8",
            )
            (feedback_dir / "offline_flap.jsonl").write_text(
                "\n".join(json.dumps(row, ensure_ascii=False) for row in feedback_rows) + "\n",
                encoding="utf-8",
            )

            env = os.environ.copy()
            env["PYTHONPATH"] = str(Path.cwd() / "training" / "src")
            env["AIOT_PROJECT_ROOT"] = str(project_root)
            subprocess.run(
                ["python3", "-m", "aiot_training.builders.build_sft", "--scene", "OFFLINE_FLAP"],
                check=True,
                cwd=project_root,
                env=env,
            )

            output_path = project_root / "training" / "data" / "gold" / "sft" / "offline_flap_sft.jsonl"
            rows = [
                json.loads(line)
                for line in output_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            ]

            self.assertEqual(1, len(rows))
            metadata = rows[0]["metadata"]
            self.assertEqual("dev-1", metadata["deviceId"])
            self.assertEqual("home-1", metadata["homeId"])
            self.assertEqual("pk-1", metadata["productKey"])
            self.assertEqual("OFFLINE_FLAP", metadata["sceneType"])


if __name__ == "__main__":
    unittest.main()
