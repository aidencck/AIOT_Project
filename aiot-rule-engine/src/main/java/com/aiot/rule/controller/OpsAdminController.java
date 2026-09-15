package com.aiot.rule.controller;

import com.aiot.common.api.Result;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.rule.dto.AiEvalReportResponse;
import com.aiot.rule.dto.AiControlPlaneDrainRequest;
import com.aiot.rule.dto.AiControlPlaneDrainResponse;
import com.aiot.rule.dto.AiBusinessLiveFlowStatusView;
import com.aiot.rule.dto.AiPersistenceStatusResponse;
import com.aiot.rule.dto.DashboardOverviewResponse;
import com.aiot.rule.dto.WorkOrderResolveRequest;
import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.model.WorkOrderRecord;
import com.aiot.rule.service.AiEvalReportService;
import com.aiot.rule.service.AiBusinessLiveFlowStatusProvider;
import com.aiot.rule.service.AiControlPlaneDrainService;
import com.aiot.rule.service.AiPersistenceAdminQueryService;
import com.aiot.rule.service.AiPersistenceHistoryQueryService;
import com.aiot.rule.service.AiPersistenceStatusService;
import com.aiot.rule.service.OpsClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
@Tag(name = "运维管理", description = "告警、工单、审计及 AI 持久化运维接口")
public class OpsAdminController {

    private final OpsClosureService opsClosureService;
    private final AiEvalReportService aiEvalReportService;
    private final AiPersistenceStatusService aiPersistenceStatusService;
    private final AiPersistenceAdminQueryService aiPersistenceAdminQueryService;
    private final AiPersistenceHistoryQueryService aiPersistenceHistoryQueryService;
    private final AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider;
    private final AiControlPlaneDrainService aiControlPlaneDrainService;

    public OpsAdminController(OpsClosureService opsClosureService,
                              AiEvalReportService aiEvalReportService,
                              AiPersistenceStatusService aiPersistenceStatusService,
                              AiPersistenceAdminQueryService aiPersistenceAdminQueryService,
                              AiPersistenceHistoryQueryService aiPersistenceHistoryQueryService,
                              AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider,
                              AiControlPlaneDrainService aiControlPlaneDrainService) {
        this.opsClosureService = opsClosureService;
        this.aiEvalReportService = aiEvalReportService;
        this.aiPersistenceStatusService = aiPersistenceStatusService;
        this.aiPersistenceAdminQueryService = aiPersistenceAdminQueryService;
        this.aiPersistenceHistoryQueryService = aiPersistenceHistoryQueryService;
        this.aiBusinessLiveFlowStatusProvider = aiBusinessLiveFlowStatusProvider;
        this.aiControlPlaneDrainService = aiControlPlaneDrainService;
    }

    @Operation(summary = "查询告警列表", description = "按状态、设备ID、设备标识查询告警列表")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/alarms")
    public Result<List<AlarmRecord>> listAlarms(@RequestParam(required = false) @Parameter(description = "告警状态") String status,
                                                @RequestParam(required = false) @Parameter(description = "设备ID") String deviceId,
                                                @RequestParam(required = false) @Parameter(description = "设备标识") String deviceIdentity) {
        return Result.success(opsClosureService.listAlarms(status, resolveDeviceIdentity(deviceIdentity, deviceId)));
    }

    @Operation(summary = "确认告警", description = "对指定告警进行确认处理")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/alarms/{alarmId}/ack")
    public Void acknowledgeAlarm(@PathVariable @NotBlank(message = "alarmId 不能为空") @Parameter(description = "告警ID") String alarmId,
                                 @RequestParam @NotBlank(message = "operator 不能为空") @Parameter(description = "操作人") String operator) {
        opsClosureService.acknowledgeAlarm(alarmId, operator);
        return null;
    }

    @Operation(summary = "解决告警", description = "对指定告警进行解决处理")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/alarms/{alarmId}/resolve")
    public Void resolveAlarm(@PathVariable @NotBlank(message = "alarmId 不能为空") @Parameter(description = "告警ID") String alarmId,
                             @RequestParam @NotBlank(message = "operator 不能为空") @Parameter(description = "操作人") String operator) {
        opsClosureService.resolveAlarm(alarmId, operator);
        return null;
    }

    @Operation(summary = "查询工单列表", description = "按状态、处理人、设备信息查询工单列表")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/work-orders")
    public Result<List<WorkOrderRecord>> listWorkOrders(@RequestParam(required = false) @Parameter(description = "工单状态") String status,
                                                        @RequestParam(required = false) @Parameter(description = "处理人") String assignee,
                                                        @RequestParam(required = false) @Parameter(description = "设备ID") String deviceId,
                                                        @RequestParam(required = false) @Parameter(description = "设备标识") String deviceIdentity) {
        return Result.success(opsClosureService.listWorkOrders(
                status,
                assignee,
                resolveDeviceIdentity(deviceIdentity, deviceId)
        ));
    }

    @Operation(summary = "认领工单", description = "将指定工单分配给处理人")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/work-orders/{workOrderId}/claim")
    public Void claimWorkOrder(@PathVariable @NotBlank(message = "workOrderId 不能为空") @Parameter(description = "工单ID") String workOrderId,
                               @RequestParam @NotBlank(message = "assignee 不能为空") @Parameter(description = "处理人") String assignee) {
        opsClosureService.claimWorkOrder(workOrderId, assignee);
        return null;
    }

    @Operation(summary = "解决工单", description = "对指定工单提交处理结果")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/work-orders/{workOrderId}/resolve")
    public Void resolveWorkOrder(@PathVariable @NotBlank(message = "workOrderId 不能为空") @Parameter(description = "工单ID") String workOrderId,
                                 @Valid @RequestBody WorkOrderResolveRequest request) {
        opsClosureService.resolveWorkOrder(workOrderId, request.getOperator(), request.getResult());
        return null;
    }

    @Operation(summary = "检查工单SLA", description = "检查并标记超出SLA的工单")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/work-orders/sla/check")
    public Integer checkWorkOrderSla() {
        return opsClosureService.checkAndMarkSlaBreached();
    }

    @Operation(summary = "运维概览", description = "获取运维看板概览数据")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/dashboard/overview")
    public DashboardOverviewResponse overview() {
        return opsClosureService.getOverview();
    }

    @Operation(summary = "查询审计记录", description = "查询操作审计记录")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/audits")
    public List<AuditRecord> listAudits(@RequestParam(required = false) Integer limit) {
        return opsClosureService.listAudits(limit);
    }

    @Operation(summary = "AI评估回归门禁报告", description = "按场景类型获取AI评估回归门禁报告")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/ai/evals/{sceneType}/regression-gate")
    public AiEvalReportResponse getRegressionGateReport(@PathVariable @NotBlank(message = "sceneType 不能为空") @Parameter(description = "场景类型") String sceneType) {
        return aiEvalReportService.getRegressionGateReport(sceneType);
    }

    @Operation(summary = "AI持久化状态", description = "获取AI持久化当前状态")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/ai/persistence/status")
    public AiPersistenceStatusResponse getAiPersistenceStatus() {
        return aiPersistenceStatusService.getStatus();
    }

    @Operation(summary = "AI持久化查询", description = "获取AI持久化管理查询信息")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/ai/persistence/query")
    public AiPersistenceAdminQueryResp getAiPersistenceQuery() {
        return aiPersistenceAdminQueryService.getQuery();
    }

    @Operation(summary = "AI持久化历史详情", description = "按报告类型与时间获取AI持久化历史详情")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/ai/persistence/history/detail")
    public AiPersistenceHistoryDetailResp getAiPersistenceHistoryDetail(
            @RequestParam @NotBlank(message = "reportType 不能为空") @Parameter(description = "报告类型") String reportType,
            @RequestParam @NotNull(message = "occurredAt 不能为空") @Positive(message = "occurredAt 必须为正整数") @Parameter(description = "发生时间戳") Long occurredAt) {
        return aiPersistenceHistoryQueryService.getDetail(reportType, occurredAt);
    }

    @Operation(summary = "AI业务实时链路状态", description = "获取AI业务实时链路验证状态")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @GetMapping("/ai/persistence/business-live-flow")
    public AiBusinessLiveFlowStatusView getAiBusinessLiveFlow() {
        return aiBusinessLiveFlowStatusProvider.getStatus();
    }

    @Operation(summary = "AI控制面排空", description = "执行AI控制面Redis排空操作")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/ai/persistence/control-plane/drain")
    public AiControlPlaneDrainResponse drainAiControlPlane(@Valid @RequestBody AiControlPlaneDrainRequest request) {
        return aiControlPlaneDrainService.drain(request);
    }

    private String resolveDeviceIdentity(String deviceIdentity, String legacyDeviceId) {
        return org.springframework.util.StringUtils.hasText(deviceIdentity) ? deviceIdentity : legacyDeviceId;
    }
}
