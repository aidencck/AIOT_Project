package com.aiot.device.controller;

import com.aiot.device.dto.ProvisionReq;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.device.dto.ProvisionTokenReq;
import com.aiot.device.service.ProvisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 设备配网接口
 */
@Tag(name = "设备配网", description = "设备配网与凭证兑换相关接口")
@RestController
@RequestMapping("/api/v1/provision")
@Validated
public class ProvisionController {

    @Autowired
    private ProvisionService provisionService;

    /**
     * APP 端或云端请求生成配网 Token
     */
    @Operation(summary = "创建配网 Token（推荐）", description = "推荐使用 POST 方式创建一次性配网 Token。")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/token")
    public String createProvisionToken(@Valid @RequestBody ProvisionTokenReq req) {
        return provisionService.generateProvisionToken(
                req.getProductKey(),
                req.getDeviceSn(),
                req.getDeviceName(),
                req.getHomeId()
        );
    }

    /**
     * 兼容保留：后续建议迁移到 POST /token。
     */
    @Operation(summary = "获取配网 Token（兼容）", description = "兼容历史调用，建议迁移到 POST /api/v1/provision/token。", deprecated = true)
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @GetMapping("/token")
    public String getProvisionTokenCompat(@Parameter(description = "产品 Key") @RequestParam @NotBlank(message = "productKey 不能为空") String productKey,
                                          @Parameter(description = "设备序列号，可选") @RequestParam(required = false) String deviceSn,
                                          @Parameter(description = "设备名称，可选") @RequestParam(required = false) String deviceName,
                                          @Parameter(description = "家庭 ID") @RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        return provisionService.generateProvisionToken(productKey, deviceSn, deviceName, homeId);
    }

    /**
     * 设备端通过 Token 换取真实的 DeviceSecret 和 MQTT 接入点
     */
    @Operation(summary = "兑换配网 Token", description = "设备端使用配网 Token 兑换设备凭证与 MQTT 接入点。")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/exchange")
    public ProvisionResp exchangeToken(@Valid @RequestBody ProvisionReq req) {
        return provisionService.provisionDevice(req);
    }
}
