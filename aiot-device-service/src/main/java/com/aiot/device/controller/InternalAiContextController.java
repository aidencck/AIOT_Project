package com.aiot.device.controller;

import com.aiot.common.api.Result;
import com.aiot.common.dto.ai.AiDeviceContextResp;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.device.service.AiContextFacade;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v1/internal/ai/devices")
@Validated
public class InternalAiContextController {

    private final AiContextFacade aiContextFacade;

    public InternalAiContextController(AiContextFacade aiContextFacade) {
        this.aiContextFacade = aiContextFacade;
    }

    @GetMapping("/{deviceId}/context")
    public Result<AiDeviceContextResp> getContext(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                                                  @RequestParam(defaultValue = "OFFLINE_FLAP") String sceneType) {
        return Result.success(aiContextFacade.buildDeviceContext(deviceId, sceneType));
    }

    @GetMapping("/{deviceId}/runtime-context")
    public Result<AiRuntimeContextPayload> getRuntimeContext(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                                                             @RequestParam(defaultValue = "OFFLINE_FLAP") String sceneType) {
        return Result.success(aiContextFacade.buildRuntimeContext(deviceId, sceneType));
    }
}
