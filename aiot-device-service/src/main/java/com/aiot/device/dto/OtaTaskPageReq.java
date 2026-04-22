package com.aiot.device.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtaTaskPageReq {
    @NotBlank(message = "homeId 不能为空")
    @Size(max = 64, message = "homeId 长度不能超过64")
    private String homeId;
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;
    private Integer status;
    @Min(value = 1, message = "pageNo 不能小于1")
    private Integer pageNo = 1;
    @Min(value = 1, message = "pageSize 不能小于1")
    @Max(value = 200, message = "pageSize 不能大于200")
    private Integer pageSize = 20;
}
