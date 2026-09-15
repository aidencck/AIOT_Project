"""基于 edge/contracts/diagnosis.schema.json 的结构化诊断校验。"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    from jsonschema import Draft7Validator

    _HAS_JSONSCHEMA = True
except ImportError:  # pragma: no cover - 兜底分支
    _HAS_JSONSCHEMA = False

CONTRACTS_DIR = Path(__file__).resolve().parents[2] / "contracts"
DEFAULT_SCHEMA_PATH = CONTRACTS_DIR / "diagnosis.schema.json"


def load_schema(path: Optional[Any] = None) -> Dict[str, Any]:
    target = Path(path) if path else Path(os.environ.get("AIOT_SCHEMA_PATH", DEFAULT_SCHEMA_PATH))
    with target.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def validate(
    instance: Dict[str, Any],
    schema: Optional[Dict[str, Any]] = None,
    schema_path: Optional[Any] = None,
) -> Tuple[bool, List[str]]:
    """返回 (是否通过, 错误信息列表)。"""
    if schema is None:
        schema = load_schema(schema_path)
    if _HAS_JSONSCHEMA:
        validator = Draft7Validator(schema)
        errors = sorted(validator.iter_errors(instance), key=lambda e: list(e.absolute_path))
        messages = [err.message for err in errors]
        return not messages, messages
    messages = _fallback_validate(instance, schema)
    return not messages, messages


def _fallback_validate(instance: Any, schema: Dict[str, Any]) -> List[str]:
    """无 jsonschema 时的最小校验子集（draft-07 的 type/required/items/minItems/min/max）。"""
    errors: List[str] = []
    if not isinstance(instance, dict):
        return ["instance must be an object"]

    for required in schema.get("required", []):
        if required not in instance:
            errors.append(f"'{required}' is a required property")

    properties = schema.get("properties", {})
    for name, spec in properties.items():
        if name not in instance or instance[name] is None:
            continue
        value = instance[name]
        value_type = spec.get("type")
        if value_type == "string" and not isinstance(value, str):
            errors.append(f"'{name}' must be a string")
        elif value_type == "number" and not isinstance(value, (int, float)):
            errors.append(f"'{name}' must be a number")
        elif value_type == "boolean" and not isinstance(value, bool):
            errors.append(f"'{name}' must be a boolean")
        elif value_type == "array":
            if not isinstance(value, list):
                errors.append(f"'{name}' must be an array")
            else:
                min_items = spec.get("minItems")
                if min_items is not None and len(value) < min_items:
                    errors.append(f"'{name}' must contain at least {min_items} items")
                item_type = spec.get("items", {}).get("type")
                if item_type == "string" and not all(isinstance(item, str) for item in value):
                    errors.append(f"'{name}' items must be strings")
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            minimum = spec.get("minimum")
            maximum = spec.get("maximum")
            if minimum is not None and value < minimum:
                errors.append(f"'{name}' must be >= {minimum}")
            if maximum is not None and value > maximum:
                errors.append(f"'{name}' must be <= {maximum}")
    return errors
