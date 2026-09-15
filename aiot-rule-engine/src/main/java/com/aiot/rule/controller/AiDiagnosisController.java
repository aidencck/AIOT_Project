package com.aiot.rule.controller;

import com.aiot.common.dto.ai.AiCaseItem;
import com.aiot.common.dto.ai.AiDiagnosisRequest;
import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.aiot.common.dto.ai.AiFeedbackRequest;
import com.aiot.common.dto.ai.AiFeedbackResponse;
import com.aiot.rule.dto.AiEdgeDiagnosisReportRequest;
import com.aiot.rule.service.AiDiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@Validated
@Tag(name = "AI 诊断", description = "AI 诊断、反馈与案例检索接口")
public class AiDiagnosisController {

    private final AiDiagnosisService aiDiagnosisService;

    public AiDiagnosisController(AiDiagnosisService aiDiagnosisService) {
        this.aiDiagnosisService = aiDiagnosisService;
    }

    @Operation(summary = "AI 诊断", description = "根据设备事件发起 AI 诊断")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/diagnosis")
    public AiDiagnosisResponse diagnose(@Valid @RequestBody AiDiagnosisRequest request) {
        return aiDiagnosisService.diagnose(request);
    }

    @Operation(summary = "接收边缘诊断结果", description = "接收边缘 agent 上报的诊断结果并入库")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/diagnosis/report")
    public AiDiagnosisResponse reportDiagnosis(@Valid @RequestBody AiEdgeDiagnosisReportRequest request) {
        return aiDiagnosisService.ingestEdgeReport(request);
    }

    @Operation(summary = "AI 反馈", description = "提交 AI 诊断结果反馈")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/feedback")
    public AiFeedbackResponse feedback(@Valid @RequestBody AiFeedbackRequest request) {
        return aiDiagnosisService.feedback(request);
    }

    @Operation(summary = "检索诊断案例", description = "按场景类型检索历史诊断案例")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/cases/search")
    public List<AiCaseItem> searchCases(@RequestParam @NotBlank(message = "sceneType 不能为空") @Parameter(description = "场景类型") String sceneType,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(20) @Parameter(description = "返回数量，1-20，默认10") Integer limit) {
        return aiDiagnosisService.searchCases(sceneType, limit);
    }
}
