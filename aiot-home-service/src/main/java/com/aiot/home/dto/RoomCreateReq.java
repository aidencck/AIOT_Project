package com.aiot.home.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoomCreateReq {
    @NotBlank(message = "homeId 不能为空")
    @Size(max = 64, message = "homeId 长度不能超过64")
    private String homeId;
    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过64")
    private String name;
}
