package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoomUpdateReq {
    @Schema(description = "房间名称", example = "客厅")
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;

    @Schema(description = "房间类型（可选）", example = "客厅")
    @Size(max = 64, message = "roomType 长度不能超过64")
    private String roomType;
}
