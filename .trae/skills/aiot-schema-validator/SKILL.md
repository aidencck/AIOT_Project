---
name: "aiot-schema-validator"
description: "校验 AI 诊断输出是否符合 schema 契约（diagnosis.schema.json、model_manifest.schema.json）与 AiSchemaValidator 归一化规则。当用户要求校验 schema 合规、枚举归一化时触发。"
---

# AI 诊断 Schema 校验（Schema Validator）

## 定位
只读校验，绑定项目 Agent [`schema-validator`](../agents/schema-validator.md)。检测 schema 漂移、非标准枚举、缺失字段、类型错误。

## 链路与契约（事实源）
- 链路：`Edge-Agent -> Rule-Engine -> Cloud AI (Ollama qwen2.5:1.5b)`
- 契约：`edge/contracts/diagnosis.schema.json`、`edge/contracts/model_manifest.schema.json`
- 校验实现：`edge/aiot-edge-agent/schema_validator.py`、`training/src/aiot_training/evals/regression_gate.py`
- 归一化：`AiSchemaValidator`（Java）

## 执行门禁（硬性）
1. 委托 `schema-validator` Agent（read-only），回传格式：`结论 / 证据 / 改动建议 / 阻塞项`
2. 输出矩阵：`Field | Expected | Actual | Deviation | Impact`
3. 末尾必须给：pass/fail verdict + 非合规枚举清单 + `rootCauseCategory`/`confidence` 归一化缺口

## 验收口径
- 诊断输出 100% 通过 `AiSchemaValidator`（schema + 枚举归一化）
- `rootCauseCategory` 只允许受控词表内取值；`confidence` 区间 [0,1]
- 任何 schema drift 或非标准枚举 = 不通过，阻断 `source=llm` 链路
