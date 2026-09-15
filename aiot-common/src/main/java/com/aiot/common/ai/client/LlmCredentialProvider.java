package com.aiot.common.ai.client;

/**
 * LLM 凭证解析器（登录层抽象）。
 *
 * <p>将凭证获取从静态配置字段中解耦，为后续接入 STS 动态凭证 / Vault 轮换预留可剥离边界：
 * <ul>
 *   <li>{@link StaticLlmCredentialProvider}：直接读取配置中的固定 API Key（当前默认，L0 静态凭证）。</li>
 *   <li>动态实现：按需从凭证中心拉取并缓存短时效凭证，实现轮换（L1 STS / L2 IAM）。</li>
 * </ul>
 *
 * <p>实现方只需定义一个 {@code LlmCredentialProvider} Bean，即可通过
 * {@code @ConditionalOnMissingBean} 无缝替换默认静态凭证，无需改动客户端调用代码。
 */
public interface LlmCredentialProvider {

    /**
     * 解析当前请求应使用的 API Key。
     * 动态实现可在每次调用时刷新凭证，支持密钥轮换与短时效 STS Token。
     *
     * @return API Key，返回 {@code null} 或空串表示凭证不可用
     */
    String resolveApiKey();
}
