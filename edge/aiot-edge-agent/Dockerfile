# AIoT 端侧诊断代理镜像（OpenAI-compatible 推理，仅标准库 + jsonschema）
# 构建上下文必须为 edge/，以同时打包 edge/contracts（schema + prompts）。
# 用法：
#   docker build -f edge/aiot-edge-agent/Dockerfile -t aiot-edge-agent edge
#   docker run --rm -i \
#     -e AIOT_LLM_BASE_URL=http://ollama:11434/v1 \
#     -e AIOT_LLM_API_KEY=ollama \
#     -e AIOT_LLM_MODEL=qwen2.5:1.5b \
#     aiot-edge-agent < events.jsonl
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/edge/aiot-edge-agent

WORKDIR /edge

# 仅拷贝依赖清单以利用层缓存
COPY aiot-edge-agent/requirements.txt /edge/aiot-edge-agent/requirements.txt
RUN pip install --no-cache-dir -r /edge/aiot-edge-agent/requirements.txt

# 拷贝代理源码 + 共享契约（diagnosis.schema.json / prompts）
COPY aiot-edge-agent /edge/aiot-edge-agent
COPY contracts /edge/contracts

WORKDIR /edge/aiot-edge-agent
ENTRYPOINT ["python", "-m", "aiot_edge_agent"]
