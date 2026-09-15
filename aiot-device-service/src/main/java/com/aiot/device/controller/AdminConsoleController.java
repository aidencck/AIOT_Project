package com.aiot.device.controller;

import com.aiot.device.dto.AdminAiEvalReportResp;
import com.aiot.device.dto.AdminAiBusinessLiveFlowResp;
import com.aiot.device.dto.AdminWorkbenchResp;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;
import com.aiot.device.dto.AdminDevicePageReq;
import com.aiot.device.dto.AdminOtaTaskPageReq;
import com.aiot.device.dto.DevicePageResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.service.AdminConsoleService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理控制台", description = "管理后台统计与运维相关接口")
@RestController
@Validated
@RequestMapping("/api/v1/admin-console")
public class AdminConsoleController {

    private final AdminConsoleService adminConsoleService;

    public AdminConsoleController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    @Operation(summary = "获取管理工作台", description = "获取后台首页所需的闭环工作台聚合数据")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/workbench")
    public AdminWorkbenchResp workbench(@Parameter(description = "家庭 ID，可选") @RequestParam(required = false) String homeId,
                                        @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getWorkbench(homeId, authorizationHeader);
    }

    @Operation(summary = "获取控制台概览", description = "获取管理控制台总览统计信息")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/overview")
    public AdminConsoleOverviewResp overview(@Parameter(description = "家庭 ID，可选") @RequestParam(required = false) String homeId,
                                             @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getOverview(homeId, authorizationHeader);
    }

    @Operation(summary = "获取最新闭环数据", description = "获取管理控制台最新闭环数据")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/latest-closure")
    public AdminLatestClosureResp latestClosure(@Parameter(description = "家庭 ID，可选") @RequestParam(required = false) String homeId,
                                                @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getLatestClosure(homeId, authorizationHeader);
    }

    @Operation(summary = "分页查询设备", description = "管理控制台分页查询设备")
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(schema = @Schema(implementation = DevicePageResp.class)))
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @GetMapping("/devices/page")
    public DevicePageResp pageDevices(@Valid AdminDevicePageReq req,
                                      @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.pageDevices(req, authorizationHeader);
    }

    @Operation(summary = "分页查询 OTA 任务", description = "管理控制台分页查询 OTA 任务")
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(schema = @Schema(implementation = OtaTaskPageResp.class)))
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @GetMapping("/ota/tasks/page")
    public OtaTaskPageResp pageOtaTasks(@Valid AdminOtaTaskPageReq req,
                                        @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.pageOtaTasks(req, authorizationHeader);
    }

    @Operation(summary = "查询 AI 评测报告", description = "查询 AI 评测报告列表，可按场景类型过滤")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/ai/evals/regression-gate")
    public List<AdminAiEvalReportResp> listAiEvalReports(@Parameter(description = "场景类型列表，可选") @RequestParam(required = false) List<String> sceneTypes,
                                                         @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.listAiEvalReports(sceneTypes, authorizationHeader);
    }

    @Operation(summary = "获取 AI 业务实时链路", description = "获取 AI 业务实时链路数据")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/ai/persistence/business-live-flow")
    public AdminAiBusinessLiveFlowResp getAiBusinessLiveFlow(@RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getAiBusinessLiveFlow(authorizationHeader);
    }

    @Operation(summary = "获取 AI 持久化查询", description = "获取 AI 持久化查询数据")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/ai/persistence/query")
    public AiPersistenceAdminQueryResp getAiPersistenceQuery(@RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getAiPersistenceQuery(authorizationHeader);
    }

    @Operation(summary = "获取 AI 持久化历史详情", description = "根据报告类型与发生时间获取 AI 持久化历史详情")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @GetMapping("/ai/persistence/history/detail")
    public AiPersistenceHistoryDetailResp getAiPersistenceHistoryDetail(@Parameter(description = "报告类型") @RequestParam @NotBlank(message = "reportType 不能为空") String reportType,
                                                                        @Parameter(description = "发生时间戳，正整数") @RequestParam @NotNull(message = "occurredAt 不能为空") @Positive(message = "occurredAt 必须为正整数") Long occurredAt,
                                                                        @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getAiPersistenceHistoryDetail(reportType, occurredAt, authorizationHeader);
    }
}
