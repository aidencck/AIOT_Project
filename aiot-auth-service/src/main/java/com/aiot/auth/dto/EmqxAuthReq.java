package com.aiot.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmqxAuthReq {
    @Schema(description = "客户端ID", example = "device_001")
    @NotBlank(message = "clientid 不能为空")
    @Size(max = 128, message = "clientid 长度不能超过128")
    // 客户端ID需符合设备标识规范，仅允许字母、数字、下划线和连字符，确保与平台设备台账匹配
    private String clientid;
    
    @Schema(description = "用户名", example = "device_user")
    @NotBlank(message = "username 不能为空")
    @Size(max = 128, message = "username 长度不能超过128")
    // 用户名需与设备绑定的账号体系一致，禁止使用未备案的通用账号
    private String username;
    
    @Schema(description = "密码", example = "secret")
    @NotBlank(message = "password 不能为空")
    @Size(max = 256, message = "password 长度不能超过256")
    // 密码需满足复杂度要求：至少8位，包含大小写字母、数字和特殊字符，不可为明文传输
    private String password;
}
