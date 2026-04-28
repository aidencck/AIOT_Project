package com.aiot.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEvent {
    private String eventId;
    private DeviceEventType eventType;
    private String deviceId;
    private Long timestamp;
    private String source;
    private String traceId;
    private Long version;
    private Object payload;
}
