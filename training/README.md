# AIoT Training Workspace

This workspace is intentionally decoupled from the Maven reactor.

Goals:
- export diagnosis / feedback / case records from current runtime stores
- build normalized SFT datasets
- run lightweight regression gates
- provide a future finetune entrypoint that can later move to a standalone repo

Current scope:
- MySQL-first exporter for `ai_diagnosis_record`, `ai_feedback_record`, `ai_case_library`
- Redis-compatible exporter for `aiot:ai:diagnosis-records`, `aiot:ai:feedback-records`, `aiot:ai:case-records`
- OFFLINE_FLAP scene-first dataset builder
- placeholder eval and QLoRA training entry

Usage:
- Local setup recommendation: `cd training && python3 -m venv .venv && . .venv/bin/activate && python -m pip install --upgrade pip && python -m pip install -e .`
- Default export path: `make export` or `./scripts/export_raw.sh OFFLINE_FLAP`
- Redis compatibility export: `make export-redis` or `./scripts/export_raw.sh OFFLINE_FLAP redis`
- Required MySQL env: `AIOT_MYSQL_HOST`, `AIOT_MYSQL_PORT`, `AIOT_MYSQL_USER`, `AIOT_MYSQL_PASSWORD`, `AIOT_MYSQL_DATABASE`
- Real MySQL export verification: `make verify-mysql-export` or `./scripts/verify_mysql_export.sh OFFLINE_FLAP true`
- Real MySQL constraint verification: `./scripts/verify_mysql_constraints.sh OFFLINE_FLAP`
- Redis/MySQL consistency check: `make check-redis-mysql-consistency`
- Migration gate: `make migration-gate` or `./scripts/check_migration_gate.sh OFFLINE_FLAP 0 1`
- Redis to MySQL backfill dry-run: `./scripts/backfill_redis_to_mysql.sh OFFLINE_FLAP dry-run`
- Redis to MySQL backfill apply: `make backfill-redis-to-mysql`
- Control-plane Redis drain dry-run: `./scripts/drain_control_plane_redis.sh dry-run`
- Control-plane Redis drain apply and delete legacy Redis tasks: `./scripts/drain_control_plane_redis.sh drain`
- Migration scripts auto-detect `training/.venv/bin/python` and add `training/src` to `PYTHONPATH`

Generated artifacts:
- Raw export manifest: `training/data/raw/manifests/<scene>_manifest.json`
- Backfill manifest: `training/data/backfill/redis_to_mysql_manifest.json`
- Consistency report: `training/data/reports/persistence/<scene>_consistency.json`
- Migration gate report: `training/data/reports/persistence/<scene>_migration_gate.json`
- Constraint verification report: `training/data/reports/persistence/mysql_constraints_verification.json`
- Control-plane Redis drain report: `training/data/reports/persistence/ai_control_plane_redis_drain.json`
