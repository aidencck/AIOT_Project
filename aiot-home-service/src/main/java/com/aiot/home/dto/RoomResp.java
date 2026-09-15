package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RoomResp {
    @Schema(description = "房间ID", example = "room_001")
    private String id;
    @Schema(description = "家庭ID", example = "home_001")
    private String homeId;
    @Schema(description = "房间名称", example = "客厅")
    private String name;
}
