package com.aiot.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部令牌常量时间比较工具，避免时序侧信道攻击。
 */
public final class InternalTokenUtils {

    private InternalTokenUtils() {
    }

    public static boolean matches(String configured, String provided) {
        if (configured == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
