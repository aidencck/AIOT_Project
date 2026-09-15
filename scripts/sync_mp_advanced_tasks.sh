#!/usr/bin/env bash

set -euo pipefail

REPO="aidencck/AIOT_Project"
MILESTONE_TITLE="V1.1 MyBatis-Plus CRUD 进阶开发"
MILESTONE_DESC="围绕 device/home/auth 三域完成 MyBatis-Plus 的分页、局部更新、约束校验、审计与测试闭环。"

ensure_milestone() {
  local number
  number="$(gh api "repos/${REPO}/milestones" --jq ".[] | select(.title == \"${MILESTONE_TITLE}\") | .number" | head -n 1 || true)"
  if [[ -z "${number}" ]]; then
    gh api "repos/${REPO}/milestones" \
      -f title="${MILESTONE_TITLE}" \
      -f description="${MILESTONE_DESC}" >/dev/null
  fi
}

upsert_issue() {
  local title="$1"
  local body_file="$2"

  local existing
  existing="$(gh issue list -R "${REPO}" --state all --limit 200 --json number,title \
    --jq ".[] | select(.title == \"${title}\") | .number" | head -n 1 || true)"

  if [[ -n "${existing}" ]]; then
    gh issue edit "${existing}" -R "${REPO}" \
      --title "${title}" \
      --body-file "${body_file}" \
      --milestone "${MILESTONE_TITLE}" >/dev/null
    echo "updated #${existing} ${title}"
  else
    gh issue create -R "${REPO}" \
      --title "${title}" \
      --body-file "${body_file}" \
      --milestone "${MILESTONE_TITLE}" >/dev/null
    echo "created ${title}"
  fi
}

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

ensure_milestone

cat > "${tmpdir}/01.md" <<'EOF'
## 目标
- 在 `device` 域实现“同家庭设备名唯一”约束，防止脏数据与重复命名。

## 开发要求
- Service 层新增重命名/更新设备名时的唯一性校验。
- 校验维度：`home_id + device_name + is_deleted=0`。
- 新增或优化数据库唯一索引：`uk_home_device_name(home_id, device_name, is_deleted)`。
- 冲突时抛出业务异常，错误码与文案保持统一规范。

## 验收标准
- 同一家庭下重复设备名创建/更新被拒绝。
- 不同家庭允许同名设备。
- 已逻辑删除设备不影响同名新建。

## 测试要求
- 单元测试覆盖：创建重复名、更新重复名、跨家庭同名、软删后重建。
- 集成测试验证数据库唯一索引生效。

## 交付物
- 代码 MR（Service/Mapper/DDL 变更）。
- 测试报告（单测与集成测试结果）。
EOF
upsert_issue "[MP-ADV-01] Device 重命名唯一性校验与索引落地" "${tmpdir}/01.md"

cat > "${tmpdir}/02.md" <<'EOF'
## 目标
- 强化设备分页查询能力，支持可控排序，避免慢查询与非法排序字段注入风险。

## 开发要求
- 分页接口支持排序参数：`sortBy`、`sortOrder`。
- 排序字段白名单仅允许：`createTime`、`status`、`deviceName`。
- 非白名单字段回退默认排序：`createTime desc`。
- `pageSize` 限制上限（建议 `<=200`），并保留现有条件过滤能力。

## 验收标准
- 白名单字段排序生效。
- 非法排序字段不会拼接到 SQL。
- 大分页参数被自动限流到上限。

## 测试要求
- 单元测试覆盖各排序分支与非法参数分支。
- 集成测试检查 SQL 执行结果与分页稳定性。

## 交付物
- 分页 API 参数与实现代码。
- 接口文档更新（请求示例与排序说明）。
EOF
upsert_issue "[MP-ADV-02] Device 分页排序白名单与参数治理" "${tmpdir}/02.md"

cat > "${tmpdir}/03.md" <<'EOF'
## 目标
- 为设备分页与局部更新能力补齐质量闭环，确保回归可控。

## 开发要求
- 为 `pageDevices`、`updateDevice` 增加单元测试。
- 增加接口级集成测试（含异常分支）。
- 将测试纳入 CI 卡点，失败即阻断合并。

## 验收标准
- 关键分支覆盖：正常路径、参数边界、设备不存在、冲突校验。
- CI 中可重复执行并稳定通过。

## 测试要求
- 单测覆盖率满足团队基线（建议 Service 层 >= 70%）。
- 提供 E2E 脚本补充关键链路验证。

## 交付物
- 测试代码与 CI 配置更新。
- 测试执行截图或日志归档。
EOF
upsert_issue "[MP-ADV-03] Device 分页与更新能力测试闭环" "${tmpdir}/03.md"

cat > "${tmpdir}/04.md" <<'EOF'
## 目标
- 在 `home` 域统一分页查询能力，提升大数据量下接口可用性。

## 开发要求
- 为 `home`、`room`、`user` 列表接口提供统一分页参数。
- 查询条件支持可选过滤（按业务场景定义）。
- 保持 Controller 轻量，仅做参数接收与转发。

## 验收标准
- 三类列表接口均支持分页并返回统一分页结构。
- 原有接口兼容策略明确（保留或迁移说明）。

## 测试要求
- 覆盖空数据、单页、多页、越界页码场景。
- 验证逻辑删除数据不会被默认查询返回。

## 交付物
- 分页接口实现与 API 文档。
- 回归测试结果记录。
EOF
upsert_issue "[MP-ADV-04] Home 域统一分页查询能力" "${tmpdir}/04.md"

cat > "${tmpdir}/05.md" <<'EOF'
## 目标
- 在 `home` 域补齐局部更新能力，降低全量更新引发的数据覆盖风险。

## 开发要求
- 支持家庭信息与房间信息的 PATCH/局部更新语义。
- 仅更新显式传入字段，禁止无意清空。
- 统一业务异常与返回结构，遵循全局规范。

## 验收标准
- 局部字段更新不影响未传入字段。
- 非法输入与目标不存在时返回标准业务异常。

## 测试要求
- 覆盖字段部分更新、空字段、并发更新基础场景。
- 覆盖权限不足场景（结合现有鉴权切面）。

## 交付物
- 更新 DTO、Service 实现、接口文档。
- 测试报告与风险说明。
EOF
upsert_issue "[MP-ADV-05] Home 域局部更新能力补齐" "${tmpdir}/05.md"

cat > "${tmpdir}/06.md" <<'EOF'
## 目标
- 强化 `home_member` 关系模型的唯一性与角色边界，避免越权和重复成员数据。

## 开发要求
- 严格执行唯一约束：`(home_id, user_id, is_deleted)` 维度不可重复有效成员。
- 补齐角色边界校验：Owner/Admin/Member 的可执行操作边界明确。
- 对“踢人/转让/降级”等关键动作增加业务规则校验。

## 验收标准
- 重复成员添加被拒绝。
- 低权限用户无法执行高权限动作。
- 关键角色变更具备可追踪日志。

## 测试要求
- 覆盖角色矩阵测试（至少 Owner/Admin/Member 交叉场景）。
- 覆盖重复添加与软删后重加场景。

## 交付物
- 规则实现代码与权限矩阵文档。
- 集成测试结果。
EOF
upsert_issue "[MP-ADV-06] HomeMember 唯一性与角色边界校验" "${tmpdir}/06.md"

cat > "${tmpdir}/07.md" <<'EOF'
## 目标
- 在 `auth` 域提供凭证审计查询能力，满足排障与安全追踪需要。

## 开发要求
- 提供凭证查询接口（分页 + 条件过滤）。
- 审计字段至少包含：设备ID、凭证类型、创建时间、更新时间、状态。
- 默认脱敏返回敏感字段，防止密钥泄漏。

## 验收标准
- 可按设备ID/时间窗口查询凭证记录。
- 查询结果分页稳定，敏感字段按规则脱敏。

## 测试要求
- 覆盖脱敏、分页、过滤、多条件组合场景。
- 安全测试验证不存在明文泄漏。

## 交付物
- 查询 API 与审计视图定义。
- 安全测试记录。
EOF
upsert_issue "[MP-ADV-07] Auth 凭证审计查询接口" "${tmpdir}/07.md"

cat > "${tmpdir}/08.md" <<'EOF'
## 目标
- 建立设备凭证轮换（Rotate Secret）能力，并形成完整审计闭环。

## 开发要求
- 实现凭证轮换流程：生成新密钥、原密钥失效策略、生效窗口控制。
- 轮换操作必须记录审计日志（操作者、时间、设备、结果）。
- 与设备侧鉴权逻辑保持兼容，避免大面积离线。

## 验收标准
- 单设备轮换成功且历史密钥按策略失效。
- 异常回滚路径明确，可恢复。
- 审计日志完整可检索。

## 测试要求
- 覆盖成功轮换、重复轮换、并发轮换、回滚场景。
- 进行灰度验证，确保在线设备稳定。

## 交付物
- 轮换服务实现、审计日志实现、运维操作手册。
- 灰度与回归测试报告。
EOF
upsert_issue "[MP-ADV-08] Auth 凭证轮换与审计闭环" "${tmpdir}/08.md"

cat > "${tmpdir}/09.md" <<'EOF'
## 目标
- 将 MyBatis-Plus 进阶改造涉及的 DDL 统一纳入版本化管理，保障发布可回滚。

## 开发要求
- 引入 Flyway/Liquibase 管理索引与唯一约束变更脚本。
- 规范脚本命名、执行顺序、回滚说明。
- 在 CI/CD 中增加数据库迁移校验步骤。

## 验收标准
- 新环境可一键迁移到目标版本。
- 旧环境增量迁移成功且不影响线上数据。
- 回滚策略文档可执行。

## 测试要求
- 在本地/测试环境完成迁移演练与回滚演练。
- 输出迁移执行日志和风险清单。

## 交付物
- 迁移脚本、回滚文档、流水线配置更新。
EOF
upsert_issue "[MP-ADV-09] DDL 版本化治理（Flyway/Liquibase）" "${tmpdir}/09.md"

echo "sync done"
