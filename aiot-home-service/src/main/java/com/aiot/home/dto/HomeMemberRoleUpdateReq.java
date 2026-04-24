package com.aiot.home.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HomeMemberRoleUpdateReq {

    @NotNull(message = "role 不能为空")
    @Min(value = 2, message = "role 不能小于2")
    @Max(value = 3, message = "role 不能大于3")
    private Integer role;
}
