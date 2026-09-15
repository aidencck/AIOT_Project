package com.aiot.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterReq {
    @Schema(description = "手机号", example = "13800138000")
    @NotBlank(message = "phone 不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "phone 格式不正确")
    private String phone;
    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "password 不能为空")
    @Size(min = 6, max = 64, message = "password 长度需在6-64之间")
    private String password;
    @Schema(description = "昵称", example = "张三")
    @NotBlank(message = "nickname 不能为空")
    @Size(max = 32, message = "nickname 长度不能超过32")
    private String nickname;
}
