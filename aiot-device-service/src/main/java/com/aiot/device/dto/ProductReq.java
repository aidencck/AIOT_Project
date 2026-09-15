package com.aiot.device.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class ProductReq {
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;
    @Size(max = 255, message = "description 长度不能超过255")
    private String description;
    @NotNull(message = "nodeType 不能为空")
    private Integer nodeType;
    private String thingModelJson;
    private String deviceModelJson;

    @AssertTrue(message = "thingModelJson 或 deviceModelJson 至少传一个")
    public boolean isDeviceModelPresent() {
        return StringUtils.hasText(thingModelJson) || StringUtils.hasText(deviceModelJson);
    }
}
