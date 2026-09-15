package com.aiot.device.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
@Data
public class CommandReq {
    @Schema(description = "命令名称")
    private String commandName;
    @Schema(description = "命令参数")
    private Object params;
}
