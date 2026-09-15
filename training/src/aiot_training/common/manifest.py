"""Dataset manifest helpers."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any


def write_manifest(path: Path, content: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(content, ensure_ascii=False, indent=2), encoding="utf-8")


def sha256_of_rows(rows: list[dict[str, Any]]) -> str:
    encoded = json.dumps(rows, ensure_ascii=False, sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()
