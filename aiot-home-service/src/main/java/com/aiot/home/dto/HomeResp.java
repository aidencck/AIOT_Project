package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class HomeResp {
    @Schema(description = "家庭ID", example = "home_001")
    private String id;
    @Schema(description = "家庭名称", example = "我的家")
    private String name;
    @Schema(description = "家庭位置", example = "上海市浦东新区")
    private String location;
    @Schema(description = "当前用户在该家庭中的角色", example = "1")
    private Integer role;
}
