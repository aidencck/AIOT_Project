package com.aiot.rule.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Webhook 目标 URL 安全校验组件（独立组件，可随规则执行逻辑拆分为独立微服务复用）。
 * 防止 WEBHOOK_POST 动作被用于 SSRF 攻击（内网扫描、云元数据窃取等）。
 * 默认拒绝（fail-closed）：无法判定安全的地址一律拒绝。
 */
@Slf4j
@Component
public class WebhookUrlValidator {

    /**
     * 云厂商元数据服务地址（AWS/GCP/Aliyun 均使用 169.254.169.254）。
     * 该地址落入 169.254.0.0/16 链路本地段（isLinkLocalAddress 已覆盖），此处显式列出做双重防御。
     */
    private static final String METADATA_IP = "169.254.169.254";

    /**
     * 白名单：逗号分隔的 host 列表（忽略大小写、trim）。命中白名单时跳过私网/环回拦截。
     */
    @Value("${aiot.rule.action.webhook.allowed-hosts:}")
    private String allowedHosts;

    /**
     * 校验 webhook 目标 URL，非法时抛出带中文原因的 IllegalArgumentException。
     */
    public void validate(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("webhook URL 不能为空");
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("webhook URL 非法，无法解析: " + url, e);
        }

        // scheme 仅允许 http/https（大小写不敏感）
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("webhook URL 协议仅允许 http/https: " + url);
        }

        // host 必须非空
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("webhook URL 缺少 host: " + url);
        }

        // 白名单精确命中时跳过私网/环回拦截（scheme 已在上面校验为 http/https）
        if (isAllowedHost(host)) {
            return;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("webhook URL 指向受限地址（内网/环回/链路本地/元数据），拒绝访问: " + url);
                }
            }
        } catch (UnknownHostException e) {
            // DNS 解析失败或 IPv6 字面量无法判定：默认拒绝
            throw new IllegalArgumentException("webhook URL 域名无法解析，拒绝访问: " + url, e);
        }
    }

    private boolean isAllowedHost(String host) {
        if (!StringUtils.hasText(allowedHosts)) {
            return false;
        }
        String normalized = host.trim().toLowerCase();
        for (String candidate : allowedHosts.split(",")) {
            String allowed = candidate.trim();
            if (!allowed.isEmpty() && normalized.equals(allowed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }
        return METADATA_IP.equals(address.getHostAddress());
    }
}
