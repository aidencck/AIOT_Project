package com.aiot.device.utils;

import org.springframework.util.StringUtils;

public final class FirmwareVersionUtils {

    private FirmwareVersionUtils() {
    }

    public static boolean isValid(String version) {
        if (!StringUtils.hasText(version)) {
            return false;
        }
        String normalized = normalize(version);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String[] parts = normalized.split("\\.");
        for (String part : parts) {
            if (!part.matches("\\d+")) {
                return false;
            }
        }
        return true;
    }

    public static int compare(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (!isValid(normalizedLeft) || !isValid(normalizedRight)) {
            throw new IllegalArgumentException("版本号格式不合法");
        }
        String[] leftParts = normalizedLeft.split("\\.");
        String[] rightParts = normalizedRight.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    public static String normalize(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }
        String trimmed = version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }
}
