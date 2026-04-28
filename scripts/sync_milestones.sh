#!/bin/bash

# Ensure we exit on error
set -e

REPO="aidencck/AIOT_Project"

echo "Checking for existing V1.0 milestone..."
# Check if Milestone #7 exists (from our earlier test) and update it, else create new
M1_NUM=$(gh api repos/$REPO/milestones --jq '.[] | select(.title | startswith("V1.0")) | .number' | head -n 1)

if [ -z "$M1_NUM" ]; then
    echo "Creating V1.0 MVP Milestone..."
    M1_NUM=$(gh api repos/$REPO/milestones -f title="V1.0 MVP 与基础闭环" -f description="验证商业逻辑，打通端到端通信闭环，基于 Docker Compose 实现快速私有化部署" --jq '.number')
else
    echo "Updating existing V1.0 Milestone (#$M1_NUM)..."
    M1_NUM=$(gh api -X PATCH repos/$REPO/milestones/$M1_NUM -f title="V1.0 MVP 与基础闭环" -f description="验证商业逻辑，打通端到端通信闭环，基于 Docker Compose 实现快速私有化部署" --jq '.number')
fi

echo "Checking for existing V2.0 milestone..."
M2_NUM=$(gh api repos/$REPO/milestones --jq '.[] | select(.title | startswith("V2.0")) | .number' | head -n 1)
if [ -z "$M2_NUM" ]; then
    echo "Creating V2.0 Milestone..."
    M2_NUM=$(gh api repos/$REPO/milestones -f title="V2.0 高可用微服务演进" -f description="支撑十万级在线设备，消除单点故障，全面拥抱 Kubernetes" --jq '.number')
fi

echo "Checking for existing V3.0 milestone..."
M3_NUM=$(gh api repos/$REPO/milestones --jq '.[] | select(.title | startswith("V3.0")) | .number' | head -n 1)
if [ -z "$M3_NUM" ]; then
    echo "Creating V3.0 Milestone..."
    M3_NUM=$(gh api repos/$REPO/milestones -f title="V3.0 千万级海量并发架构" -f description="支撑千万级设备在线，日均百亿条遥测数据的存储与毫秒级检索" --jq '.number')
fi

echo "Milestones ready: M1=#$M1_NUM, M2=#$M2_NUM, M3=#$M3_NUM"

# Function to create issue if not exists
create_issue() {
    local MILESTONE_NAME=$1
    local TITLE=$2
    local BODY=$3
    
    # Check if issue already exists
    EXISTING=$(gh issue list -R $REPO --search "$TITLE" --json number -q '.[0].number')
    if [ -n "$EXISTING" ]; then
        echo "Issue '$TITLE' already exists (#$EXISTING), skipping."
    else
        echo "Creating Issue: $TITLE"
        gh issue create -R $REPO -m "$MILESTONE_NAME" -t "$TITLE" -b "$BODY" > /dev/null
    fi
}

echo "=============================="
echo "Creating Issues for V1.0 MVP..."
create_issue "V1.0 MVP 与基础闭环" "核心业务 CRUD 与物模型落地" "落地 aiot-device-service 和 aiot-auth-service，完成 MyBatis-Plus 基础表结构设计（设备、产品、物模型）。"
create_issue "V1.0 MVP 与基础闭环" "设备鉴权与 EMQX 状态联动" "实现 HMAC_SHA256 鉴权与 EMQX Webhook 上下线联动。"
create_issue "V1.0 MVP 与基础闭环" "自动化 E2E 测试验证" "Service 层业务逻辑覆盖率达到 70%。利用 Bash 脚本模拟设备 MQTT 接入、心跳、HTTP 下发指令闭环。"
create_issue "V1.0 MVP 与基础闭环" "中间件与服务一键部署" "产出标准的 docker-compose.yml。实现一键拉起 MySQL, Redis, EMQX (单节点), Nacos 和业务微服务。"

echo "=============================="
echo "Creating Issues for V2.0 高可用..."
create_issue "V2.0 高可用微服务演进" "SpringCloud 微服务深度拆分" "拆分 aiot-data-service (负责海量遥测数据接收) 与 aiot-rule-engine (规则引擎)。"
create_issue "V2.0 高可用微服务演进" "MySQL 主从与 Redis 哨兵集群搭建" "MySQL 升级为主从复制 (Master-Slave)，Redis 升级为 Sentinel 哨兵模式。"
create_issue "V2.0 高可用微服务演进" "EMQX 集群与负载均衡代理" "EMQX 升级为 3 节点集群，使用 Haproxy/Nginx 代理。"
create_issue "V2.0 高可用微服务演进" "全链路监控与 10万压测" "引入 Prometheus + Grafana 进行监控。引入 SkyWalking 实现全链路追踪。使用 JMeter-MQTT 插件进行 10 万连接并发压测及混沌测试。"
create_issue "V2.0 高可用微服务演进" "Kubernetes Helm 部署适配" "编写 K8s Helm Charts，实现云原生部署。"

echo "=============================="
echo "Creating Issues for V3.0 海量并发..."
create_issue "V3.0 千万级海量并发架构" "TDengine 时序库引入与冷热分离" "引入 TDengine，将原本存在 MySQL/Redis 的设备遥测流水数据双写或全量迁移至 TDengine。"
create_issue "V3.0 千万级海量并发架构" "ShardingSphere 分库分表" "引入 Apache ShardingSphere，对 iot_device 表根据 product_id 或 tenant_id 进行分库分表。"
create_issue "V3.0 千万级海量并发架构" "Kafka 流量削峰与数据一致性对账" "在 EMQX 规则引擎与后端服务之间插入 Kafka 削峰，编写旁路脚本定时对账确保 0 丢失。"
create_issue "V3.0 千万级海量并发架构" "K8s 自动扩缩容与百万级压测" "实现多可用区 (Multi-AZ) 容灾部署，配置 K8s HPA。进行 100万~1000万模拟设备长连接极限压测。"

echo "✅ All milestones and issues successfully synchronized to remote project!"
