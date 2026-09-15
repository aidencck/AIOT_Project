package com.aiot.common.ai.client;

public interface LlmClient {
    String chatJson(String systemPrompt, String userPrompt);

    boolean isEnabled();

    String getModelName();
}
