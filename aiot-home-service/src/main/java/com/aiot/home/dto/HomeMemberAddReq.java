package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class HomeMemberAddReq {

    @Schema(description = "用户ID", example = "user_001")
    @NotBlank(message = "userId 不能为空")
    private String userId;

    @Schema(description = "角色（2-管理员，3-成员）", example = "3")
    @NotNull(message = "role 不能为空")
    @Min(value = 2, message = "role 不能小于2")
    @Max(value = 3, message = "role 不能大于3")
    private Integer role;
}
