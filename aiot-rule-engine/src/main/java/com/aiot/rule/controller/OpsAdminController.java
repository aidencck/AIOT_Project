package com.aiot.rule.controller;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.rule.dto.DashboardOverviewResponse;
import com.aiot.rule.dto.WorkOrderResolveRequest;
import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.model.WorkOrderRecord;
import com.aiot.rule.service.OpsClosureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class OpsAdminController {

    private final OpsClosureService opsClosureService;

    public OpsAdminController(OpsClosureService opsClosureService) {
        this.opsClosureService = opsClosureService;
    }

    @GetMapping("/alarms")
    public List<AlarmRecord> listAlarms(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String deviceId) {
        return opsClosureService.listAlarms(status, deviceId);
    }

    @PostMapping("/alarms/{alarmId}/ack")
    public Void acknowledgeAlarm(@PathVariable @NotBlank(message = "alarmId 不能为空") String alarmId,
                                 @RequestParam @NotBlank(message = "operator 不能为空") String operator) {
        try {
            opsClosureService.acknowledgeAlarm(alarmId, operator);
            return null;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @GetMapping("/work-orders")
    public List<WorkOrderRecord> listWorkOrders(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String assignee) {
        return opsClosureService.listWorkOrders(status, assignee);
    }

    @PostMapping("/work-orders/{workOrderId}/claim")
    public Void claimWorkOrder(@PathVariable @NotBlank(message = "workOrderId 不能为空") String workOrderId,
                               @RequestParam @NotBlank(message = "assignee 不能为空") String assignee) {
        try {
            opsClosureService.claimWorkOrder(workOrderId, assignee);
            return null;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @PostMapping("/work-orders/{workOrderId}/resolve")
    public Void resolveWorkOrder(@PathVariable @NotBlank(message = "workOrderId 不能为空") String workOrderId,
                                 @Valid @RequestBody WorkOrderResolveRequest request) {
        try {
            opsClosureService.resolveWorkOrder(workOrderId, request.getOperator(), request.getResult());
            return null;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @PostMapping("/work-orders/sla/check")
    public Integer checkWorkOrderSla() {
        return opsClosureService.checkAndMarkSlaBreached();
    }

    @GetMapping("/dashboard/overview")
    public DashboardOverviewResponse overview() {
        return opsClosureService.getOverview();
    }

    @GetMapping("/audits")
    public List<AuditRecord> listAudits(@RequestParam(required = false) Integer limit) {
        return opsClosureService.listAudits(limit);
    }
}
