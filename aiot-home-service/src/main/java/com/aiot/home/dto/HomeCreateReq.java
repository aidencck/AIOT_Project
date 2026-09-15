package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HomeCreateReq {
    @Schema(description = "家庭名称", example = "我的家")
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;
    @Schema(description = "家庭位置", example = "上海市浦东新区")
    @Size(max = 255, message = "location 长度不能超过255")
    private String location;
}
