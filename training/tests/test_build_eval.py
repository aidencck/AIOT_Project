import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


class BuildEvalTest(unittest.TestCase):
    def test_build_eval_keeps_device_rows_in_same_split(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            project_root = Path(temp_dir)
            data_dir = project_root / "training" / "data" / "gold" / "sft"
            data_dir.mkdir(parents=True, exist_ok=True)
            source_path = data_dir / "offline_flap_sft.jsonl"
            rows = [
                {
                    "instruction": "x",
                    "input": {},
                    "output": {"sceneType": "OFFLINE_FLAP"},
                    "metadata": {
                        "diagnosisId": "diag-1",
                        "deviceId": "dev-1",
                        "feedbackType": "ACCEPTED",
                        "resolutionStatus": "SOLVED",
                    },
                },
                {
                    "instruction": "x",
                    "input": {},
                    "output": {"sceneType": "OFFLINE_FLAP"},
                    "metadata": {
                        "diagnosisId": "diag-2",
                        "deviceId": "dev-1",
                        "feedbackType": "MODIFIED",
                        "resolutionStatus": "SOLVED",
                    },
                },
            ]
            with source_path.open("w", encoding="utf-8") as handle:
                for row in rows:
                    handle.write(json.dumps(row, ensure_ascii=False) + "\n")

            env = os.environ.copy()
            env["PYTHONPATH"] = str(Path.cwd() / "training" / "src")
            env["AIOT_PROJECT_ROOT"] = str(project_root)
            subprocess.run(
                ["python3", "-m", "aiot_training.builders.build_eval", "--scene", "OFFLINE_FLAP"],
                check=True,
                cwd=project_root,
                env=env,
            )

            output_path = project_root / "training" / "data" / "gold" / "eval" / "offline_flap_golden.jsonl"
            train_path = project_root / "training" / "data" / "gold" / "sft" / "offline_flap_sft_train.jsonl"
            eval_rows = [
                json.loads(line)
                for line in output_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            ]
            train_rows = [
                json.loads(line)
                for line in train_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            ]

            combined = eval_rows + train_rows
            self.assertEqual(2, len(combined))
            splits = {row["metadata"]["split"] for row in combined}
            self.assertEqual(1, len(splits))
            only_split = combined[0]["metadata"]["split"]
            self.assertIn(only_split, {"train", "val", "test"})
            if only_split == "train":
                self.assertEqual(2, len(train_rows))
                self.assertEqual([], eval_rows)
            else:
                self.assertEqual(2, len(eval_rows))
                self.assertEqual([], train_rows)


if __name__ == "__main__":
    unittest.main()
