package com.aiot.rule.controller;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.rule.dto.RuleApproveRequest;
import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.dto.RuleUpdateRequest;
import com.aiot.rule.service.RuleLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/rules")
@Validated
@Tag(name = "AI 规则", description = "AI 规则草稿与审批接口")
public class AiRuleController {

    private final RuleLifecycleService ruleLifecycleService;

    public AiRuleController(RuleLifecycleService ruleLifecycleService) {
        this.ruleLifecycleService = ruleLifecycleService;
    }

    @Operation(summary = "生成规则草稿", description = "根据自然语言需求生成 AI 规则草稿")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/draft")
    public RuleDraftResponse draftRule(@Valid @RequestBody RuleDraftRequest request) {
        return ruleLifecycleService.draftRule(request);
    }

    @Operation(summary = "审批规则草稿", description = "对指定规则草稿提交审批意见")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "规则不存在")
    @PostMapping("/{ruleId}/approve")
    public RuleDraftResponse approveRule(@PathVariable @NotBlank(message = "ruleId 不能为空") @Parameter(description = "规则ID") String ruleId,
                                         @Valid @RequestBody RuleApproveRequest request) {
        try {
            return ruleLifecycleService.approveRule(ruleId, request);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @Operation(summary = "驳回规则草稿", description = "驳回指定规则草稿并记录驳回原因")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "规则不存在")
    @PostMapping("/{ruleId}/reject")
    public RuleDraftResponse rejectRule(@PathVariable @NotBlank(message = "ruleId 不能为空") @Parameter(description = "规则ID") String ruleId,
                                        @RequestBody(required = false) Map<String, String> body) {
        String rejectReason = body == null ? null : body.get("rejectReason");
        try {
            return ruleLifecycleService.rejectRule(ruleId, rejectReason);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @Operation(summary = "更新规则草稿", description = "更新指定规则草稿的触发条件与动作")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "规则不存在")
    @PutMapping("/{ruleId}")
    public RuleDraftResponse updateRule(@PathVariable @NotBlank(message = "ruleId 不能为空") @Parameter(description = "规则ID") String ruleId,
                                        @Valid @RequestBody RuleUpdateRequest request) {
        try {
            return ruleLifecycleService.updateRule(ruleId, request);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @Operation(summary = "删除规则草稿", description = "删除指定规则草稿，仅 DRAFT 状态可删除")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败或状态不允许")
    @ApiResponse(responseCode = "404", description = "规则不存在")
    @DeleteMapping("/{ruleId}")
    public Void deleteRule(@PathVariable @NotBlank(message = "ruleId 不能为空") @Parameter(description = "规则ID") String ruleId) {
        try {
            ruleLifecycleService.deleteRule(ruleId);
            return null;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, e.getMessage());
        }
    }

    @Operation(summary = "查询规则列表", description = "按状态查询 AI 规则列表，status 为空时返回全部")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping
    public List<RuleDraftResponse> listRules(@RequestParam(required = false) @Parameter(description = "规则状态") String status) {
        return ruleLifecycleService.listRules(status);
    }
}
