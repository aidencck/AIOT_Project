SHELL := /bin/bash

# ======================================================================
# 【项目底层核心设计：单入口架构】
# 第一性原理：所有环境操作统一收敛到 ./aiotctl，彻底消除多脚本带来的一致性问题
# 核心约束：本Makefile仅作为快捷入口，所有业务逻辑零重复，100%复用aiotctl实现
# 复用逻辑：密钥/路径/Maven/Docker等基础能力统一由scripts/lib/common.sh维护，本文件无硬编码
# 使用场景：覆盖本地开发/CI构建/预发部署/生产上线全流程，所有环境操作入口统一
# 初始化要求：首次执行前需确保aiotctl有可执行权限，依赖环境由doctor目标自动校验
# ======================================================================

# 可覆盖环境变量（仅本地开发默认值，生产/预发环境通过外部注入覆盖）
MYSQL_PASSWORD    ?= root123456
EMQX_API_PASSWORD ?= emqxadmin123
GRAFANA_PASSWORD  ?= grafana123
AIOT_JWT_SECRET   ?= jwt-local-dev-strong-secret-2026-32byte
AIOT_INTERNAL     ?= internal-token-local-dev-20260826-24c
AIOT_WEBHOOK      ?= emqx-webhook-local-dev-20260826-24ch
AIOT_ENV          ?= dev
IMAGE_TAG         ?= main

# 统一导出环境变量，全部转发给aiotctl处理，本层不做任何业务解析
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
	verify verify-infra verify-local \
	logs logs-infra logs-local logs-main \
	ps ps-infra ps-local ps-main \
	down clean \
	test-e2e test-comm test-webhook test-mqtt test-shadow \
	drill-rollback drill-canary drill-fault \
	ai-seed ai-verify ai-flow release-health

help: ## 所有目标（一键转发到 aiotctl）
	@echo "AIOT Makefile shortcuts → 全部转发到 ./aiotctl（单入口工程化）"
	@echo "  🔧  开发模式：infra (P0) / local (P1) / main / admin / ci"
	@echo "  🏗   构建：build / build-jars / build-images"
	@echo "  🚀  部署：deploy-prod / deploy-staging <service> <tag>"
	@echo "  🧪  测试：test-comm / test-e2e / test-webhook / test-mqtt / test-shadow"
	@echo "  🩺  体检：doctor  verify-infra  verify-local  release-health"
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
infra-ps:     ## infra容器状态
	@$(AIOTCTL) infra ps
infra-logs:   ## infra日志tail；用 SERVICE=aiot-mysql 指定单容器
	@$(AIOTCTL) infra logs $(SERVICE)
infra-down:   ## 停infra
	@$(AIOTCTL) infra down

# P2级开发模式：启动本地全栈docker-compose，不含自动构建，用于验证镜像可用性
# 适用场景：本地集成测试，需要模拟全容器环境，提前验证镜像兼容性
local: ## [P2] 全栈 docker-compose.local.yml（不含build）
	@$(AIOTCTL) local up
local-build: ## [P2] 全栈：Maven打包 → 本地镜像build → up
	@$(AIOTCTL) build all
	@$(AIOTCTL) local up
local-ps:     @$(AIOTCTL) local ps
local-logs:   @$(AIOTCTL) local logs $(SERVICE)
local-down:   @$(AIOTCTL) local down

# P1级开发模式：启动主业务集群，拉取ghcr.io官方预构建镜像，无需本地打包
# 适用场景：联调测试，需要快速搭建完整核心集群，聚焦业务联调而非本地构建
main:         ## [P1] 主compose 12核心 ghcr.io 镜像
	@$(AIOTCTL) dev up
main-obs:     ## 主compose + observability（全21容器）
	@$(AIOTCTL) observability up
main-ps:      @$(AIOTCTL) dev ps
main-logs:    @$(AIOTCTL) dev logs $(SERVICE)
main-down:    @$(AIOTCTL) dev down

# AI后台专属开发模式：启动AI模块持久化存储，支撑AI功能本地开发
admin:        ## Admin本地模式（AI持久化）
	@$(AIOTCTL) admin up
admin-down:   @$(AIOTCTL) admin down

# CI环境专用模式：端口全部偏移1808x，避免和本地开发端口冲突，支持CI流水线并行执行
ci:           ## CI端口偏移(1808x)
	@$(AIOTCTL) ci up
ci-down:      @$(AIOTCTL) ci down

# -------- 构建流程 --------
build:        ## jars + 本地images
	@$(AIOTCTL) build all
build-jars:   ## 仅Maven打包
	@$(AIOTCTL) build jars
build-images: ## 仅本地镜像分层build
	@$(AIOTCTL) build images

# -------- 部署流程（生产/预发环境统一入口）--------
deploy:               ## 通用: make deploy ENV=prod SERVICES="aiot-gateway aiot-auth-service"
	@$(AIOTCTL) deploy $(ENV) $(SERVICES)
deploy-staging:       ## make deploy-staging SERVICES=aiot-gateway,aiot-auth-service
	@$(AIOTCTL) deploy staging $(SERVICES)
deploy-prod:          ## make deploy-prod
	@$(AIOTCTL) deploy prod $(SERVICES)
staging:              @$(AIOTCTL) deploy staging
staging-down:         @$(AIOTCTL) down staging
prod:                 @$(AIOTCTL) deploy prod
prod-down:            @$(AIOTCTL) down prod

# -------- 验证与观测/停止 --------
verify:           @$(AIOTCTL) verify local
verify-infra:     @$(AIOTCTL) verify infra
verify-local:     @$(AIOTCTL) verify local
release-health:   @$(AIOTCTL) release-health $(SERVICES)

logs:             @$(AIOTCTL) logs main $(SERVICE)
logs-infra:       @$(AIOTCTL) logs infra $(SERVICE)
logs-local:       @$(AIOTCTL) logs local $(SERVICE)
logs-main:        @$(AIOTCTL) logs main $(SERVICE)

ps:               @$(AIOTCTL) ps main
ps-infra:         @$(AIOTCTL) ps infra
ps-local:         @$(AIOTCTL) ps local
ps-main:          @$(AIOTCTL) ps main

down:             ## make down / make down TARGET=infra / TARGET=all --volumes
	@$(AIOTCTL) down $(TARGET) $(VOLUMES)
clean:            ## 全栈停+清卷+prune AIOT标签悬空镜像
	@$(AIOTCTL) down all --volumes

# -------- 测试与混沌演练 --------
test-comm:      @$(AIOTCTL) test communication
test-e2e:       @$(AIOTCTL) test e2e
test-webhook:   @$(AIOTCTL) test webhook
test-mqtt:      @$(AIOTCTL) test mqtt
test-shadow:    @$(AIOTCTL) test shadow

drill-rollback: @$(AIOTCTL) drill rollback
drill-canary:   @$(AIOTCTL) drill canary
drill-fault:    @$(AIOTCTL) drill inject-fault

# -------- AI模块专属能力 --------
ai-seed:        @$(AIOTCTL) ai seed
ai-verify:      @$(AIOTCTL) ai verify
ai-flow:        @$(AIOTCTL) ai flow
