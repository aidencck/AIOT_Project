package com.aiot.rule.controller;

import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.service.RuleLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI 规则草稿", description = "AI 规则草稿生成接口")
public class AiRuleDraftController {

    private final RuleLifecycleService ruleLifecycleService;

    public AiRuleDraftController(RuleLifecycleService ruleLifecycleService) {
        this.ruleLifecycleService = ruleLifecycleService;
    }

    @Operation(summary = "生成规则草稿", description = "根据自然语言需求生成 AI 规则草稿")
    @ApiResponse(responseCode = "200", description = "成功，返回统一 Result 包装")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/rule-drafts")
    public RuleDraftResponse draftRule(@Valid @RequestBody RuleDraftRequest request) {
        return ruleLifecycleService.draftRule(request);
    }
}
