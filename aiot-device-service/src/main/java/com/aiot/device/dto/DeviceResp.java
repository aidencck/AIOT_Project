package com.aiot.device.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
@Data
@AllArgsConstructor
public class DeviceResp {
    private String deviceId;
    private String deviceSecret;
}
