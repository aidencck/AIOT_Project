from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Any


@dataclass
class SftSample:
    instruction: str
    input: dict[str, Any]
    output: dict[str, Any]
    metadata: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
