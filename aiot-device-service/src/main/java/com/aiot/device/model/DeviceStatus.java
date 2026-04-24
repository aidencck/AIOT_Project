package com.aiot.device.model;

import java.util.Map;
import java.util.Set;

public enum DeviceStatus {
    INACTIVE(0, "未激活"),
    ONLINE(1, "在线"),
    OFFLINE(2, "离线");

    private final int code;
    private final String desc;

    DeviceStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DeviceStatus fromCode(Integer code) {
        if (code == null) {
            return INACTIVE;
        }
        for (DeviceStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static boolean canTransition(DeviceStatus from, DeviceStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Map<DeviceStatus, Set<DeviceStatus>> allowed = Map.of(
                INACTIVE, Set.of(ONLINE),
                ONLINE, Set.of(OFFLINE),
                OFFLINE, Set.of(ONLINE)
        );
        return allowed.getOrDefault(from, Set.of()).contains(to);
    }
}
