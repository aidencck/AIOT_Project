package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductReq {
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;
    @Size(max = 255, message = "description 长度不能超过255")
    private String description;
    @NotNull(message = "nodeType 不能为空")
    private Integer nodeType;
    @NotBlank(message = "thingModelJson 不能为空")
    private String thingModelJson;
}
