from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    project_root: Path
    redis_host: str
    redis_port: int
    redis_db: int
    redis_password: str | None
    mysql_host: str
    mysql_port: int
    mysql_user: str
    mysql_password: str | None
    mysql_database: str
    dataset_version: str


def load_settings() -> Settings:
    default_project_root = str(Path(__file__).resolve().parents[4])
    project_root = Path(os.getenv("AIOT_PROJECT_ROOT") or default_project_root)
    return Settings(
        project_root=project_root,
        redis_host=os.getenv("AIOT_REDIS_HOST", "127.0.0.1"),
        redis_port=int(os.getenv("AIOT_REDIS_PORT", "6379")),
        redis_db=int(os.getenv("AIOT_REDIS_DB", "0")),
        redis_password=os.getenv("AIOT_REDIS_PASSWORD") or None,
        mysql_host=os.getenv("AIOT_MYSQL_HOST", "127.0.0.1"),
        mysql_port=int(os.getenv("AIOT_MYSQL_PORT", "3306")),
        mysql_user=os.getenv("AIOT_MYSQL_USER") or os.getenv("MYSQL_USER", "root"),
        mysql_password=os.getenv("AIOT_MYSQL_PASSWORD") or os.getenv("MYSQL_PASSWORD") or None,
        mysql_database=os.getenv("AIOT_MYSQL_DATABASE", "aiot_cloud"),
        dataset_version=os.getenv("AIOT_DATASET_VERSION", "offline-flap-sft-dev"),
    )
