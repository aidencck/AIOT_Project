package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class HomeMemberResp {
    @Schema(description = "用户ID", example = "user_001")
    private String userId;
    @Schema(description = "昵称", example = "张三")
    private String nickname;
    @Schema(description = "手机号", example = "13800138000")
    private String phone;
    @Schema(description = "角色（1-拥有者，2-管理员，3-成员）", example = "3")
    private Integer role;
}
