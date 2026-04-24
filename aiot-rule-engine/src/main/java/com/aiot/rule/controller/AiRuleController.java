package com.aiot.rule.controller;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.rule.dto.RuleApproveRequest;
import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.service.RuleLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/rules")
@Validated
public class AiRuleController {

    private final RuleLifecycleService ruleLifecycleService;

    public AiRuleController(RuleLifecycleService ruleLifecycleService) {
        this.ruleLifecycleService = ruleLifecycleService;
    }

    @PostMapping("/draft")
    public Result<RuleDraftResponse> draftRule(@Valid @RequestBody RuleDraftRequest request) {
        return Result.success(ruleLifecycleService.draftRule(request));
    }

    @PostMapping("/{ruleId}/approve")
    public Result<RuleDraftResponse> approveRule(@PathVariable @NotBlank(message = "ruleId 不能为空") String ruleId,
                                                 @Valid @RequestBody RuleApproveRequest request) {
        try {
            return Result.success(ruleLifecycleService.approveRule(ruleId, request));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }
}
