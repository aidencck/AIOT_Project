package com.aiot.device.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
@Data
@AllArgsConstructor
public class DeviceStatusResp {
    private String deviceId;
    private String status;
    private Object shadowData;
}
