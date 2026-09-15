---
name: "aiot-security-audit"
description: "对 AIoT 代码库做只读安全审计：硬编码密钥、SQL 注入、MQTT/设备凭据泄漏、XSS、不安全反序列化、.env/application.yml 密钥入库。当用户要求安全审查、漏洞扫描、硬编码密钥检查时触发。"
---

# AIoT 安全审计（Security Audit）

## 定位
只读安全审计，绑定项目 Agent [`security-auditor`](../agents/security-auditor.md)。按 Critical/High/Medium/Low 分级，末尾给 verdict。不修改任何文件。

## 触发条件
- 用户要求「安全审查 / 漏洞扫描 / 硬编码密钥检查 / 上线前安全复核」

## 扫描范围（按优先级）
1. 硬编码密钥：Java/Python/Vue/shell 中的 API key、密码、token、JWT secret
2. SQL 注入：MyBatis `${}` 动态 SQL
3. MQTT / 设备鉴权凭据泄漏、不安全默认值
4. Vue admin XSS、不安全反序列化
5. 已提交的 `.env` / `application.yml` 密钥

## 执行门禁（硬性）
1. 委托 `security-auditor` Agent（read-only）执行扫描，回传格式：`结论 / 证据 / 改动建议 / 阻塞项`
2. **Critical**：立即处置，出修复 PR，不得进入 backlog
3. **High**：需给出修复 PR 或止损方案
4. **Medium/Low**：进入安全 backlog，写入 `.trae/knowledge/known-risks.md`

## 输出模板
| File | Line | Risk | Issue | Fix |
|---|---|---|---|---|
| ... | ... | Critical/High/Medium/Low | ... | ... |

末尾必须给：`findings 总数 / 最高风险 / verdict(通过|有条件通过|不通过)`。

## 验收口径
- 无 Critical 且无 High = 通过
- 仅 Medium/Low = 有条件通过
- 任一 Critical 或 High = 不通过
