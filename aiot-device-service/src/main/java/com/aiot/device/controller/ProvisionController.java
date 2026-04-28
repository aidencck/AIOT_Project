package com.aiot.device.controller;

import com.aiot.device.dto.ProvisionReq;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.device.dto.ProvisionTokenReq;
import com.aiot.device.service.ProvisionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 设备配网接口
 */
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
    @PostMapping("/token")
    public String createProvisionToken(@Valid @RequestBody ProvisionTokenReq req,
                                       @RequestHeader("Authorization") String authorizationHeader) {
        return provisionService.generateProvisionToken(
                req.getProductKey(),
                req.getDeviceName(),
                req.getHomeId(),
                authorizationHeader
        );
    }

    /**
     * 兼容保留：后续建议迁移到 POST /token。
     */
    @Operation(summary = "获取配网 Token（兼容）", description = "兼容历史调用，建议迁移到 POST /api/v1/provision/token。", deprecated = true)
    @GetMapping("/token")
    public String getProvisionTokenCompat(@RequestParam @NotBlank(message = "productKey 不能为空") String productKey,
                                          @RequestParam @NotBlank(message = "deviceName 不能为空") String deviceName,
                                          @RequestParam @NotBlank(message = "homeId 不能为空") String homeId,
                                          @RequestHeader("Authorization") String authorizationHeader) {
        return provisionService.generateProvisionToken(productKey, deviceName, homeId, authorizationHeader);
    }

    /**
     * 设备端通过 Token 换取真实的 DeviceSecret 和 MQTT 接入点
     */
    @Operation(summary = "兑换配网 Token", description = "设备端使用配网 Token 兑换设备凭证与 MQTT 接入点。")
    @PostMapping("/exchange")
    public ProvisionResp exchangeToken(@Valid @RequestBody ProvisionReq req) {
        return provisionService.provisionDevice(req);
    }
}
