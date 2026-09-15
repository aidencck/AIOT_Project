package com.aiot.rule.service;

import com.aiot.common.api.ResultCode;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class AiPersistenceHistoryQueryService {

    public static final String BUSINESS_LIVE_FLOW_REPORT_TYPE = "BUSINESS_LIVE_FLOW";
    private static final Set<String> SUPPORTED_REPORT_TYPES = Set.of(
            AiControlPlaneDrainService.DRAIN_AUDIT_EVENT_TYPE,
            BUSINESS_LIVE_FLOW_REPORT_TYPE
    );

    private final AiControlPlaneDrainStatusProvider aiControlPlaneDrainStatusProvider;
    private final AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider;

    public AiPersistenceHistoryQueryService(AiControlPlaneDrainStatusProvider aiControlPlaneDrainStatusProvider,
                                            AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider) {
        this.aiControlPlaneDrainStatusProvider = aiControlPlaneDrainStatusProvider;
        this.aiBusinessLiveFlowStatusProvider = aiBusinessLiveFlowStatusProvider;
    }

    public AiPersistenceHistoryDetailResp getDetail(String reportType, Long occurredAt) {
        String normalizedReportType = normalizeReportType(reportType);
        validateOccurredAt(occurredAt);
        if (AiControlPlaneDrainService.DRAIN_AUDIT_EVENT_TYPE.equals(normalizedReportType)) {
            return aiControlPlaneDrainStatusProvider.getHistoryDetail(occurredAt);
        }
        if (BUSINESS_LIVE_FLOW_REPORT_TYPE.equals(normalizedReportType)) {
            return aiBusinessLiveFlowStatusProvider.getHistoryDetail(occurredAt);
        }
        throw new BusinessException(ResultCode.VALIDATE_FAILED, "reportType 不支持: " + reportType);
    }

    private String normalizeReportType(String reportType) {
        String normalized = reportType == null ? "" : reportType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_REPORT_TYPES.contains(normalized)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "reportType 不支持: " + reportType);
        }
        return normalized;
    }

    private void validateOccurredAt(Long occurredAt) {
        if (occurredAt == null || occurredAt <= 0) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "occurredAt 必须为正整数");
        }
    }
}
