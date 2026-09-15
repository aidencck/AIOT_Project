package com.aiot.device.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
@Data
@AllArgsConstructor
public class DeviceStatusResp {
    @Schema(description = "设备 ID")
    private String deviceId;
    @Schema(description = "设备状态，可选值：0-未激活，1-在线，2-离线")
    private String status;
    @Schema(description = "影子数据")
    private Object shadowData;
}
