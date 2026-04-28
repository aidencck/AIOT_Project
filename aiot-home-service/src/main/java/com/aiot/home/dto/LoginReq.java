package com.aiot.home.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginReq {
    @NotBlank(message = "phone 不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "phone 格式不正确")
    private String phone;
    @NotBlank(message = "password 不能为空")
    @Size(min = 6, max = 64, message = "password 长度需在6-64之间")
    private String password;
}
