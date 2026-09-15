SHELL := /bin/bash

# ======================================================================
# 【项目底层核心设计：单入口架构】
# 第一性原理：所有环境操作统一收敛到 ./aiotctl，彻底消除多脚本带来的一致性问题
# 核心约束：本Makefile仅作为快捷入口，所有业务逻辑零重复，100%复用aiotctl实现
# 复用逻辑：密钥/路径/Maven/Docker等基础能力统一由scripts/lib/common.sh维护，本文件无硬编码
# 使用场景：覆盖本地开发/CI构建/预发部署/生产上线全流程，所有环境操作入口统一
# 初始化要求：首次执行前需确保aiotctl有可执行权限，依赖环境由doctor目标自动校验
# ======================================================================

# 敏感项（密码/密钥）不再提供弱默认值：一律由 compose/env/<env>/runtime.env 或外部注入
# （make MYSQL_PASSWORD=xxx ... / shell export）。缺失时由 aiotctl 的 secret::require_secrets 阻断（fail-fast），
# 杜绝弱密码 / 可预测 JWT 泄漏到 staging/prod。仅非敏感项保留默认值。
AIOT_ENV          ?= dev
IMAGE_TAG         ?= main

# 统一导出环境变量，全部转发给aiotctl处理，本层不做任何业务解析（敏感项 pass-through，默认空）
_EXPORT := MYSQL_PASSWORD=$(MYSQL_PASSWORD) \
	EMQX_API_PASSWORD=$(EMQX_API_PASSWORD) \
	GRAFANA_ADMIN_PASSWORD=$(GRAFANA_PASSWORD) \
	AIOT_JWT_SECRET=$(AIOT_JWT_SECRET) \
	AIOT_INTERNAL_TOKEN=$(AIOT_INTERNAL) \
	AIOT_EMQX_WEBHOOK_SECRET=$(AIOT_WEBHOOK) \
	AIOT_ENV=$(AIOT_ENV) \
	IMAGE_TAG=$(IMAGE_TAG)

AIOTCTL := $(_EXPORT) ./aiotctl

# 所有PHONY目标统一声明，确保无文件冲突，全部作为快捷入口转发
.PHONY: help doctor \
	infra infra-ps infra-logs infra-down \
	local local-build local-ps local-logs local-down \
	main main-obs main-ps main-logs main-down \
	admin admin-down ci ci-down \
	prod prod-down staging staging-down \
	build build-jars build-images \
	deploy deploy-prod deploy-staging \
	migrate gate canary rollback reset \
	verify verify-infra verify-local release-health artifact-check \
	logs logs-infra logs-local logs-main \
	ps ps-infra ps-local ps-main \
	down clean \
	test-e2e test-comm test-webhook test-mqtt test-shadow \
	drill-rollback drill-canary drill-fault \
	ai-seed ai-verify ai-flow ai-mysql-check ai-migrate-gate \
	jvm-diagnostics log-query \
	observability observability-down observability-status observability-verify

help: ## 所有目标（一键转发到 aiotctl）
	@echo "AIOT Makefile shortcuts → 全部转发到 ./aiotctl（单入口工程化）"
	@echo "  🔧  开发模式：infra (P0) / local (P1) / main / admin / ci"
	@echo "  🏗   构建：build / build-jars / build-images"
	@echo "  🚀  部署：deploy-prod / deploy-staging <service> <tag>"
	@echo "  🚦  发布：gate / canary / rollback / migrate / reset"
	@echo "  🧪  测试：test-comm / test-e2e / test-webhook / test-mqtt / test-shadow"
	@echo "  🩺  体检：doctor  verify-infra  verify-local  release-health"
	@echo "  🔭  观测：observability / observability-verify / jvm-diagnostics / log-query"
	@echo "  🪵  日志：logs-infra / logs-local / logs-main [SERVICE=aiot-home-service]"
	@echo "  🛑  停止：down [--volumes] / clean"
	@echo ""
	@echo "  💡 完整CLI：./aiotctl help"

# -------- 基础体检 --------
# 初始化核心校验：检查所有依赖命令、密钥格式、端口占用、配置语法，是所有操作的前置保障
doctor: ## 环境自检：命令/密钥/端口/语法
	@$(AIOTCTL) doctor

# -------- 开发模式（按优先级分层，匹配不同开发场景）--------
# P0级开发模式：仅启动MySQL/EMQX/Redis/RabbitMQ4个基础中间件，业务服务由IDE本地启动
# 适用场景：日常迭代开发，需要断点调试、热重载，最大化开发效率
infra: ## [P0] 仅4个infra容器，IDE直跑8个JVM（断点+热重载）
	@$(AIOTCTL) infra up
infra-ps: ## infra容器状态
	@$(AIOTCTL) infra ps
infra-logs: ## infra日志tail；用 SERVICE=aiot-mysql 指定单容器
	@$(AIOTCTL) infra logs $(SERVICE)
infra-down: ## 停infra
	@$(AIOTCTL) infra down

# P2级开发模式：启动本地全栈docker-compose，不含自动构建，用于验证镜像可用性
# 适用场景：本地集成测试，需要模拟全容器环境，提前验证镜像兼容性
local: ## [P2] 全栈 docker-compose.local.yml（不含build）
	@$(AIOTCTL) local up
local-build: ## [P2] 全栈：Maven打包 → 本地镜像build → up
	@$(AIOTCTL) build all
	@$(AIOTCTL) local up
local-ps: ## 本地栈容器状态
	@$(AIOTCTL) local ps
local-logs: ## 本地栈日志；用 SERVICE= 指定单容器
	@$(AIOTCTL) local logs $(SERVICE)
local-down: ## 停本地栈
	@$(AIOTCTL) local down

# P1级开发模式：启动主业务集群，拉取ghcr.io官方预构建镜像，无需本地打包
# 适用场景：联调测试，需要快速搭建完整核心集群，聚焦业务联调而非本地构建
main: ## [P1] 主compose 12核心 ghcr.io 镜像
	@$(AIOTCTL) dev up
main-obs: ## 主compose + observability（全21容器）
	@$(AIOTCTL) observability up
main-ps: ## 主集群容器状态
	@$(AIOTCTL) dev ps
main-logs: ## 主集群日志；用 SERVICE= 指定单容器
	@$(AIOTCTL) dev logs $(SERVICE)
main-down: ## 停主集群
	@$(AIOTCTL) dev down

# AI后台专属开发模式：启动AI模块持久化存储，支撑AI功能本地开发
admin: ## Admin本地模式（AI持久化）
	@$(AIOTCTL) admin up
admin-down: ## 停Admin本地模式
	@$(AIOTCTL) admin down

# CI环境专用模式：端口全部偏移1808x，避免和本地开发端口冲突，支持CI流水线并行执行
ci: ## CI端口偏移(1808x)
	@$(AIOTCTL) ci up
ci-down: ## 停CI模式
	@$(AIOTCTL) ci down

# -------- 构建流程 --------
build: ## jars + 本地images
	@$(AIOTCTL) build all
build-jars: ## 仅Maven打包
	@$(AIOTCTL) build jars
build-images: ## 仅本地镜像分层build
	@$(AIOTCTL) build images

# -------- 部署流程（生产/预发环境统一入口）--------
deploy: ## 通用: make deploy ENV=prod SERVICES="aiot-gateway aiot-auth-service"
	@$(AIOTCTL) deploy $(ENV) $(SERVICES)
deploy-staging: ## make deploy-staging SERVICES="aiot-gateway aiot-auth-service"
	@$(AIOTCTL) deploy staging $(SERVICES)
deploy-prod: ## make deploy-prod SERVICES="aiot-gateway aiot-auth-service"
	@$(AIOTCTL) deploy prod $(SERVICES)

migrate: ## Flyway迁移: make migrate ARGS="--db all"
	@$(AIOTCTL) migrate $(ARGS)
gate: ## 发布门禁: make gate ENV=prod SERVICES="aiot-gateway aiot-auth-service"
	@$(AIOTCTL) gate $(ENV) $(SERVICES)
canary: ## 灰度发布: make canary ENV=prod SERVICE=aiot-gateway TAG=<sha>
	@$(AIOTCTL) canary $(ENV) $(SERVICE) $(TAG)
rollback: ## 回滚: make rollback ENV=prod SERVICE=aiot-gateway
	@$(AIOTCTL) rollback $(ENV) $(SERVICE)
reset: ## 重置: make reset MODE=fresh ARGS="--yes"
	@$(AIOTCTL) reset $(MODE) $(ARGS)

staging: ## 部署staging全栈
	@$(AIOTCTL) deploy staging
staging-down: ## 停staging
	@$(AIOTCTL) down staging
prod: ## 部署prod全栈
	@$(AIOTCTL) deploy prod
prod-down: ## 停prod
	@$(AIOTCTL) down prod

# -------- 验证与观测/停止 --------
verify: ## 验证local栈TCP+健康
	@$(AIOTCTL) verify local
verify-infra: ## 验证infra栈
	@$(AIOTCTL) verify infra
verify-local: ## 验证local栈
	@$(AIOTCTL) verify local
release-health: ## 发布后健康门禁: make release-health SERVICES="aiot-gateway"
	@$(AIOTCTL) release-health $(SERVICES)

artifact-check: ## 部署前jar/镜像一致性校验
	@$(AIOTCTL) artifact-check $(ARGS)

observability: ## 观测栈up: make observability
	@$(AIOTCTL) observability up
observability-down: ## 观测栈down
	@$(AIOTCTL) observability down
observability-status: ## 观测栈状态
	@$(AIOTCTL) observability status
observability-verify: ## 观测栈健康验证
	@$(AIOTCTL) observability verify
jvm-diagnostics: ## JVM诊断: make jvm-diagnostics SERVICE=aiot-home-service ACTION=thread-dump
	@$(AIOTCTL) jvm-diagnostics $(SERVICE) $(ACTION)
log-query: ## 结构化日志检索: make log-query ARGS="--service aiot-gateway"
	@$(AIOTCTL) log-query $(ARGS)

logs: ## 主集群日志
	@$(AIOTCTL) logs main $(SERVICE)
logs-infra: ## infra日志
	@$(AIOTCTL) logs infra $(SERVICE)
logs-local: ## local日志
	@$(AIOTCTL) logs local $(SERVICE)
logs-main: ## 主集群日志
	@$(AIOTCTL) logs main $(SERVICE)

ps: ## 主集群容器列表
	@$(AIOTCTL) ps main
ps-infra: ## infra容器列表
	@$(AIOTCTL) ps infra
ps-local: ## local容器列表
	@$(AIOTCTL) ps local
ps-main: ## 主集群容器列表
	@$(AIOTCTL) ps main

down: ## make down TARGET=infra|all [--volumes]
	@$(AIOTCTL) down $(TARGET) $(VOLUMES)
clean: ## 全栈停+清卷+prune AIOT标签悬空镜像
	@$(AIOTCTL) down all --volumes

# -------- 测试与混沌演练 --------
test-comm: ## 服务通信测试
	@$(AIOTCTL) test communication
test-e2e: ## E2E测试
	@$(AIOTCTL) test e2e
test-webhook: ## Webhook测试
	@$(AIOTCTL) test webhook
test-mqtt: ## MQTT测试
	@$(AIOTCTL) test mqtt
test-shadow: ## 影子设备测试
	@$(AIOTCTL) test shadow

drill-rollback: ## 回滚演练
	@$(AIOTCTL) drill rollback
drill-canary: ## 灰度演练
	@$(AIOTCTL) drill canary
drill-fault: ## 故障注入演练
	@$(AIOTCTL) drill inject-fault

# -------- AI模块专属能力 --------
ai-seed: ## AI种子数据
	@$(AIOTCTL) ai seed
ai-verify: ## AI持久化验证
	@$(AIOTCTL) ai verify
ai-flow: ## AI业务链路验证
	@$(AIOTCTL) ai flow
ai-mysql-check: ## AI MySQL约束校验
	@$(AIOTCTL) ai mysql-check
ai-migrate-gate: ## AI迁移门禁
	@$(AIOTCTL) ai migrate-gate
