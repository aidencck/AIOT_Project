#!/bin/bash
set -e

REPO="aidencck/AIOT_Project"

create_label() {
  local name=$1
  local color=$2
  local desc=$3
  gh api -X POST "repos/$REPO/labels" -f name="$name" -f color="$color" -f description="$desc" --silent || true
}

create_milestone() {
  local title=$1
  local desc=$2
  gh api -X POST "repos/$REPO/milestones" -f title="$title" -f description="$desc" --silent || true
}

create_issue() {
  local milestone=$1
  local labels=$2
  local title=$3
  local body=$4
  gh issue create -R "$REPO" -m "$milestone" -l "$labels" -t "$title" -b "$body" >/dev/null
}

echo "Creating labels..."
create_label "sprint:m0-s1" "0e8a16" "M0 Sprint 1"
create_label "sprint:m0-s2" "1d76db" "M0 Sprint 2"
create_label "sprint:m0-s3" "fbca04" "M0 Sprint 3"
create_label "sprint:m0-s4" "d93f0b" "M0 Sprint 4"
create_label "epic:access" "5319e7" "Access and master data"
create_label "epic:alert" "0052cc" "Alert and notification"
create_label "epic:ticket" "c2e0c6" "Ticket and SLA"
create_label "epic:governance" "bfdadc" "Audit and governance"
create_label "priority:p0" "b60205" "Top priority"
create_label "priority:p1" "fbca04" "Important"

echo "Creating milestones..."
create_milestone "M0-S1 W1-W2 基础主数据与权限底座" "组织权限、用户家庭、设备资产主数据打通。"
create_milestone "M0-S2 W3-W4 规则告警与通知联动" "规则监控、告警入池、通知联动闭环。"
create_milestone "M0-S3 W5-W6 工单闭环与SLA" "告警到工单再到结案回写的端到端流程。"
create_milestone "M0-S4 W7-W8 审计治理与经营看板" "审计可追溯与VP经营看板交付。"

echo "Creating issues for Sprint 1..."
create_issue "M0-S1 W1-W2 基础主数据与权限底座" "sprint:m0-s1,epic:access,priority:p0" "[M0-S1] 组织与权限中心最小可用版本" "交付角色、菜单、数据权限（项目/家庭维度）与关键操作二次确认。"
create_issue "M0-S1 W1-W2 基础主数据与权限底座" "sprint:m0-s1,epic:access,priority:p0" "[M0-S1] 用户与家庭中心主数据能力" "交付家庭创建、成员管理、设备绑定解绑，打通用户-家庭-设备关系。"
create_issue "M0-S1 W1-W2 基础主数据与权限底座" "sprint:m0-s1,epic:access,priority:p0" "[M0-S1] 设备与资产中心台账能力" "交付设备台账、在线状态、固件版本、心跳时间查询能力。"

echo "Creating issues for Sprint 2..."
create_issue "M0-S2 W3-W4 规则告警与通知联动" "sprint:m0-s2,epic:alert,priority:p0" "[M0-S2] 告警规则模板能力" "支持阈值、离线、频发、去重、升级规则配置。"
create_issue "M0-S2 W3-W4 规则告警与通知联动" "sprint:m0-s2,epic:alert,priority:p0" "[M0-S2] 告警中心与状态流转" "实现告警入池、级别标注、状态流转与基础检索。"
create_issue "M0-S2 W3-W4 规则告警与通知联动" "sprint:m0-s2,epic:alert,priority:p1" "[M0-S2] 通知通道接入" "接入至少1条通知通道，并支持失败重试与追踪。"

echo "Creating issues for Sprint 3..."
create_issue "M0-S3 W5-W6 工单闭环与SLA" "sprint:m0-s3,epic:ticket,priority:p0" "[M0-S3] 告警自动建单能力" "按告警级别和类型自动创建工单并关联告警。"
create_issue "M0-S3 W5-W6 工单闭环与SLA" "sprint:m0-s3,epic:ticket,priority:p0" "[M0-S3] 工单分派与处理流程" "支持手动分派与规则分派，完成处理记录与结案动作。"
create_issue "M0-S3 W5-W6 工单闭环与SLA" "sprint:m0-s3,epic:ticket,priority:p1" "[M0-S3] SLA 监控与超时预警" "支持响应时长、处理时长、超时预警与统计。"

echo "Creating issues for Sprint 4..."
create_issue "M0-S4 W7-W8 审计治理与经营看板" "sprint:m0-s4,epic:governance,priority:p0" "[M0-S4] 审计与风控中心基础能力" "交付关键操作日志、权限变更日志、异常登录审计。"
create_issue "M0-S4 W7-W8 审计治理与经营看板" "sprint:m0-s4,epic:governance,priority:p0" "[M0-S4] VP经营看板基础版" "交付在线率、告警趋势、SLA达成率、一次解决率指标看板。"
create_issue "M0-S4 W7-W8 审计治理与经营看板" "sprint:m0-s4,epic:governance,priority:p1" "[M0-S4] 发布治理与演练机制" "完成回滚预案、发布演练、运维手册和值班机制。"

echo "Done: Milestones and issues have been created on GitHub."
