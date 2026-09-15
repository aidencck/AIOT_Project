package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HomeUpdateReq {
    @Schema(description = "家庭名称（可选）", example = "我的家")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;

    @Schema(description = "家庭位置（可选）", example = "上海")
    @Size(max = 128, message = "location 长度不能超过128")
    private String location;
}
