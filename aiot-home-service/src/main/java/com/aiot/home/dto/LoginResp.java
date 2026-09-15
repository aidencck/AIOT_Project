package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginResp {
    @Schema(description = "登录令牌", example = "eyJhbGciOiJIUzI1NiJ9.xxx")
    private String token;
    @Schema(description = "用户ID", example = "user_001")
    private String userId;
    @Schema(description = "全局用户ID", example = "global_user_001")
    private String globalUserId;
    @Schema(description = "昵称", example = "张三")
    private String nickname;
}
