#!/bin/bash

REPO="aidencck/AIOT_Project"

echo "开始创建 Milestones (迭代版本)..."

# 尝试创建 Milestones，忽略如果已存在的错误
gh api repos/$REPO/milestones -f title="Sprint 0: 基础设施与架构基建" -f description="搭建高可用微服务骨架，确立全局研发规范，为后续业务代码提供坚实底座。" >/dev/null 2>&1 || echo "Sprint 0 已存在"
gh api repos/$REPO/milestones -f title="Sprint 1: 空间与用户域 MVP" -f description="构建“人”与“空间”的关系，实现家庭、房间及成员权限的流转。" >/dev/null 2>&1 || echo "Sprint 1 已存在"
gh api repos/$REPO/milestones -f title="Sprint 2: 设备拓扑与配网域" -f description="解决设备的注册、绑定以及云端设备影子的同步问题。" >/dev/null 2>&1 || echo "Sprint 2 已存在"
gh api repos/$REPO/milestones -f title="Sprint 3: 智能场景与联动引擎" -f description="实现 AIoT 平台的核心灵魂——规则流转与云边协同。" >/dev/null 2>&1 || echo "Sprint 3 已存在"
gh api repos/$REPO/milestones -f title="Sprint 4: 语音生态与 OpenAPI" -f description="打破信息孤岛，对接主流语音助手并对外开放 API。" >/dev/null 2>&1 || echo "Sprint 4 已存在"

echo "开始创建 Issues (任务)..."

# Sprint 0
gh issue create --repo $REPO --title "[Task-01] 搭建基础微服务模块骨架" --body "包含 \`aiot-home-service\`, \`aiot-device-service\`, \`aiot-scene-service\`。" --milestone "Sprint 0: 基础设施与架构基建" >/dev/null
gh issue create --repo $REPO --title "[Task-02] 封装全局统一异常处理组件" --body "封装 \`BusinessException\` + \`ResultCode\`" --milestone "Sprint 0: 基础设施与架构基建" >/dev/null
gh issue create --repo $REPO --title "[Task-03] 实现全局返回结构包装器" --body "实现 \`GlobalResponseHandler\`" --milestone "Sprint 0: 基础设施与架构基建" >/dev/null
gh issue create --repo $REPO --title "[Task-04] 配置基于 MDC 的 TraceId 日志追踪拦截器" --body "确保全链路日志可查" --milestone "Sprint 0: 基础设施与架构基建" >/dev/null
gh issue create --repo $REPO --title "[Task-05] 数据库规范落地" --body "统一引入 \`is_deleted\` 软删除，禁用物理外键，配置 Mybatis-Plus 插件" --milestone "Sprint 0: 基础设施与架构基建" >/dev/null
gh issue create --repo $REPO --title "[Task-06] 制定并配置 Redis 连接池与缓存规范" --body "配置 \`aiot:{module}:{entity}:{id}\` 缓存键自动前缀策略" --milestone "Sprint 0: 基础设施与架构基建" >/dev/null

# Sprint 1
gh issue create --repo $REPO --title "[Task-11] 用户体系对接与鉴权中心搭建" --body "基于 JWT 或 OAuth2 的用户鉴权" --milestone "Sprint 1: 空间与用户域 MVP" >/dev/null
gh issue create --repo $REPO --title "[Task-12] 家庭实体 (Home) 的 CRUD 接口开发与领域建模" --body "完成家庭实体的基础管理接口" --milestone "Sprint 1: 空间与用户域 MVP" >/dev/null
gh issue create --repo $REPO --title "[Task-13] 房间实体 (Room) 与家庭的从属关系开发" --body "实现房间与家庭的拓扑挂载" --milestone "Sprint 1: 空间与用户域 MVP" >/dev/null
gh issue create --repo $REPO --title "[Task-14] 家庭成员角色体系及 RBAC 权限判定逻辑" --body "支持 Owner, Admin, Member 角色与权限隔离" --milestone "Sprint 1: 空间与用户域 MVP" >/dev/null
gh issue create --repo $REPO --title "[Task-15] 基于 Redis Hash 结构优化家庭拓扑与成员权限的高频查询性能" --body "提高核心权限链路的查询速度" --milestone "Sprint 1: 空间与用户域 MVP" >/dev/null
gh issue create --repo $REPO --title "[Task-16] 编写空间与用户域的核心单元测试与接口文档" --body "输出 Swagger/Apifox 文档" --milestone "Sprint 1: 空间与用户域 MVP" >/dev/null

# Sprint 2
gh issue create --repo $REPO --title "[Task-21] 物模型定义核心数据表设计与解析逻辑" --body "包含 Property, Event, Service 模型定义" --milestone "Sprint 2: 设备拓扑与配网域" >/dev/null
gh issue create --repo $REPO --title "[Task-22] MQTT Broker 基础集群搭建及设备端连接鉴权服务开发" --body "支持 EMQX 接入及 ACL 鉴权" --milestone "Sprint 2: 设备拓扑与配网域" >/dev/null
gh issue create --repo $REPO --title "[Task-23] 网关与子设备绑定拓扑逻辑 (Device Topology) 开发" --body "边缘网关与子设备关联挂载" --milestone "Sprint 2: 设备拓扑与配网域" >/dev/null
gh issue create --repo $REPO --title "[Task-24] 设备影子 (Device Shadow) 机制开发" --body "Redis 读写同步与上下行指令队列设计" --milestone "Sprint 2: 设备拓扑与配网域" >/dev/null
gh issue create --repo $REPO --title "[Task-25] 设备在线/离线状态心跳监测与 Webhook 告警推送" --body "实时跟踪设备连通性状态" --milestone "Sprint 2: 设备拓扑与配网域" >/dev/null
gh issue create --repo $REPO --title "[Task-26] 边缘网关一键配网与 Token 换取安全交互流程联调" --body "保障设备安全接入" --milestone "Sprint 2: 设备拓扑与配网域" >/dev/null

# Sprint 3
gh issue create --repo $REPO --title "[Task-31] 场景领域模型建立及数据库表设计" --body "Scene, Trigger, Condition, Action" --milestone "Sprint 3: 智能场景与联动引擎" >/dev/null
gh issue create --repo $REPO --title "[Task-32] 手动场景（一键执行 / Tap-to-Run）核心逻辑开发" --body "支持场景主动触发执行" --milestone "Sprint 3: 智能场景与联动引擎" >/dev/null
gh issue create --repo $REPO --title "[Task-33] 云端自动化规则引擎开发" --body "基于类 SQL 解析或特定规则匹配引擎" --milestone "Sprint 3: 智能场景与联动引擎" >/dev/null
gh issue create --repo $REPO --title "[Task-34] 云边协同判定逻辑" --body "计算场景涉及的设备是否同属一个本地网关" --milestone "Sprint 3: 智能场景与联动引擎" >/dev/null
gh issue create --repo $REPO --title "[Task-35] 本地规则 JSON 生成与 MQTT 局域网下发同步机制" --body "保障场景能在断网环境下本地执行" --milestone "Sprint 3: 智能场景与联动引擎" >/dev/null
gh issue create --repo $REPO --title "[Task-36] 场景执行日志异步入库与 TraceId 追踪" --body "完整记录执行链路，便于排查" --milestone "Sprint 3: 智能场景与联动引擎" >/dev/null

# Sprint 4
gh issue create --repo $REPO --title "[Task-41] 搭建云云对接标准的 OAuth2.0 授权服务器 (授权码模式)" --body "为语音生态对接提供授权支持" --milestone "Sprint 4: 语音生态与 OpenAPI" >/dev/null
gh issue create --repo $REPO --title "[Task-42] 第三方语音助手意图解析适配器开发" --body "适配 Amazon Alexa、小度等主流语音助手" --milestone "Sprint 4: 语音生态与 OpenAPI" >/dev/null
gh issue create --repo $REPO --title "[Task-43] 核心设备控制接口暴露至 OpenAPI 网关" --body "供第三方应用调用控制" --milestone "Sprint 4: 语音生态与 OpenAPI" >/dev/null
gh issue create --repo $REPO --title "[Task-44] OpenAPI 接口的限流 (Rate Limiting) 与 AK/SK 鉴权" --body "保障开放平台安全性与稳定性" --milestone "Sprint 4: 语音生态与 OpenAPI" >/dev/null

echo "所有 Sprints 与 Tasks 已成功推送到 GitHub 项目管理！"
