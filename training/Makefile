PYTHON ?= python3
SOURCE ?= mysql

.PHONY: export export-redis verify-mysql-export check-redis-mysql-consistency migration-gate backfill-redis-to-mysql build-eval train

export:
	$(PYTHON) -m aiot_training.exporters.export_runner --scene OFFLINE_FLAP --source $(SOURCE)

export-redis:
	$(PYTHON) -m aiot_training.exporters.export_runner --scene OFFLINE_FLAP --source redis

verify-mysql-export:
	$(PYTHON) -m aiot_training.exporters.export_runner --scene OFFLINE_FLAP --source mysql
	$(PYTHON) -m aiot_training.exporters.verify_export --scene OFFLINE_FLAP --source mysql --require-non-empty --check-consistency

check-redis-mysql-consistency:
	$(PYTHON) -m aiot_training.exporters.verify_export --scene OFFLINE_FLAP --source mysql --check-consistency

migration-gate:
	$(PYTHON) -m aiot_training.backfill.migration_gate --scene OFFLINE_FLAP --max-total-mismatch 0 --min-written-total 1

backfill-redis-to-mysql:
	$(PYTHON) -m aiot_training.backfill.redis_to_mysql

build-eval:
	$(PYTHON) -m aiot_training.builders.build_sft --scene OFFLINE_FLAP
	$(PYTHON) -m aiot_training.builders.build_eval --scene OFFLINE_FLAP
	$(PYTHON) -m aiot_training.evals.regression_gate --scene OFFLINE_FLAP

train:
	$(PYTHON) -m aiot_training.trainers.qlora_entry --scene OFFLINE_FLAP
