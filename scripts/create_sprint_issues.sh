#!/bin/bash

REPO="aidencck/AIOT_Project"
MILESTONE="Sprint 1: Vibe Coding 协作基础设施"

# Create Labels if they don't exist
gh api -X POST repos/$REPO/labels -f name="vibe-coding" -f color="0e8a16" -f description="Vibe coding collaboration tasks" --silent || true
gh api -X POST repos/$REPO/labels -f name="documentation" -f color="0075ca" -f description="Improvements or additions to documentation" --silent || true
gh api -X POST repos/$REPO/labels -f name="enhancement" -f color="a2eeef" -f description="New feature or request" --silent || true
gh api -X POST repos/$REPO/labels -f name="infrastructure" -f color="5319e7" -f description="CI/CD and setup" --silent || true

echo "Creating issues..."

# Epic 1
gh issue create -R $REPO -m "$MILESTONE" -l "vibe-coding,enhancement" \
  -t "[Epic 1] 统一 AI 上下文与“规则工程” (Rule Engineering)" \
  -b "目标：确保所有开发者使用的 AI 共享相同的背景知识和业务规范，防止代码风格割裂。

**子任务：**
- [ ] Task 1.1: 创建全局 \`.traerules\` / \`.cursorrules\` 配置文件，写入基础编码规范（异常、Result、日志等）。
- [ ] Task 1.2: 整理并提取《AIoT后端全局开发规范》核心规则，注入 AI 上下文。
- [ ] Task 1.3: 建立业务术语表 (Ubiquitous Language) Markdown 文档，对齐核心实体命名。"

# Epic 2
gh issue create -R $REPO -m "$MILESTONE" -l "vibe-coding,documentation" \
  -t "[Epic 2] 架构与设计文档先行 (Architecture & Isolation)" \
  -b "目标：强制大功能开发前的架构设计，避免 AI 生成不可维护的“意大利面条代码”，并从物理层面划分 AI 修改边界。

**子任务：**
- [ ] Task 2.1: 制定架构设计文档模板（必须包含 Premise, Constraints, Boundaries, Endgame）。
- [ ] Task 2.2: 编写基于 Mermaid 的可视化作图规范，并添加到 AI 规则中。
- [ ] Task 2.3: 划分当前 AIoT 项目的限界上下文（如设备服务、用户服务等），明确开发人员和 AI 的修改边界。"

# Epic 3
gh issue create -R $REPO -m "$MILESTONE" -l "vibe-coding,infrastructure" \
  -t "[Epic 3] 契约驱动开发与测试闭环 (Contract & Test Driven)" \
  -b "目标：接口优先以支持并行 Vibe 开发，并建立强制性的 CI 门禁以拦截不合格的 AI 生成代码。

**子任务：**
- [ ] Task 3.1: 确立 API 契约优先 (API First) 工作流，选择并配置接口定义工具（如 Swagger/OpenAPI）。
- [ ] Task 3.2: 制定 AI 单元测试与集成测试生成规范（规定覆盖率、Mock 方式等）。
- [ ] Task 3.3: 配置 CI/CD 门禁流水线（如 GitHub Actions），强制执行测试与 Linter 检查。"

# Epic 4
gh issue create -R $REPO -m "$MILESTONE" -l "vibe-coding,documentation" \
  -t "[Epic 4] 改变代码审查 (Code Review) 焦点" \
  -b "目标：适应极速代码产出，将 CR 重点从语法转移到业务逻辑与一致性，防止分支偏离。

**子任务：**
- [ ] Task 4.1: 更新代码审查 Checklist，将重点转移至业务逻辑、并发安全和缓存一致性。
- [ ] Task 4.2: 确立并记录“高频小步提交 (Small PRs)”的分支与合并规范。"

echo "Sprint 1 issues created successfully!"
