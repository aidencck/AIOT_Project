package com.aiot.auth.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmqxAuthReq {
    @NotBlank(message = "clientid 不能为空")
    @Size(max = 128, message = "clientid 长度不能超过128")
    private String clientid;
    @NotBlank(message = "username 不能为空")
    @Size(max = 128, message = "username 长度不能超过128")
    private String username;
    @NotBlank(message = "password 不能为空")
    @Size(max = 256, message = "password 长度不能超过256")
    private String password;
}
