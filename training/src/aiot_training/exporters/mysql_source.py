"""MySQL-first source adapter for table-backed AI runtime exports."""

from __future__ import annotations

from datetime import date, datetime, timezone
from decimal import Decimal

from .base import SourceAdapter

TABLE_MAP = {
    "aiot:ai:diagnosis-records": "ai_diagnosis_record",
    "aiot:ai:feedback-records": "ai_feedback_record",
    "aiot:ai:case-records": "ai_case_library",
}

class MysqlSourceAdapter(SourceAdapter):
    def __init__(
        self,
        host: str,
        port: int,
        user: str,
        password: str | None,
        database: str,
    ):
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.database = database

    def export_hash(
        self,
        key: str,
        scene: str | None = None,
        limit: int | None = None,
        offset: int = 0,
    ) -> list[dict]:
        table = TABLE_MAP.get(key)
        if table is None:
            raise ValueError(f"Unsupported MySQL export key: {key}")
        import pymysql  # pylint: disable=import-error,import-outside-toplevel

        connection = pymysql.connect(
            host=self.host,
            port=self.port,
            user=self.user,
            password=self.password,
            database=self.database,
            cursorclass=pymysql.cursors.DictCursor,
        )
        try:
            with connection.cursor() as cursor:
                sql, params = self._build_query(table, scene, limit, offset)
                cursor.execute(sql, params)
                return [self._normalize_row(row) for row in cursor.fetchall()]
        finally:
            connection.close()

    @staticmethod
    def _normalize_value(value):
        if isinstance(value, datetime):
            return int(value.replace(tzinfo=timezone.utc).timestamp() * 1000)
        if isinstance(value, date):
            return value.isoformat()
        if isinstance(value, Decimal):
            return float(value)
        return value

    def _normalize_row(self, row: dict) -> dict:
        return {key: self._normalize_value(value) for key, value in row.items()}

    def _build_query(
        self,
        table: str,
        scene: str | None,
        limit: int | None,
        offset: int,
    ) -> tuple[str, list[object]]:
        params: list[object] = []
        if table == "ai_feedback_record" and scene:
            sql = (
                "SELECT f.* FROM ai_feedback_record f "
                "JOIN ai_diagnosis_record d ON f.diagnosis_id = d.diagnosis_id "
                "WHERE d.scene_type = %s"
            )
            params.append(scene.upper())
            order_by = " ORDER BY f.id ASC"
        else:
            sql = f"SELECT * FROM {table}"
            if scene and table in {"ai_diagnosis_record", "ai_case_library"}:
                sql += " WHERE scene_type = %s"
                params.append(scene.upper())
            order_by = " ORDER BY id ASC"
        sql += order_by
        if limit is not None and limit >= 0:
            sql += " LIMIT %s OFFSET %s"
            params.extend([limit, offset])
        return sql, params
