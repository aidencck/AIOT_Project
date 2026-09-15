package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPersistenceHistoryQueryServiceTest {

    @Test
    void shouldDelegateBusinessHistoryDetailQuery() {
        AiControlPlaneDrainStatusProvider drainStatusProvider = mock(AiControlPlaneDrainStatusProvider.class);
        AiBusinessLiveFlowStatusProvider businessLiveFlowStatusProvider = mock(AiBusinessLiveFlowStatusProvider.class);
        when(businessLiveFlowStatusProvider.getHistoryDetail(2233445566L)).thenReturn(AiPersistenceHistoryDetailResp.builder()
                .reportType(AiPersistenceHistoryQueryService.BUSINESS_LIVE_FLOW_REPORT_TYPE)
                .occurredAt(2233445566L)
                .reportPath("/tmp/history/ai_business_live_flow_2233445566.json")
                .exists(true)
                .scene("OFFLINE_FLAP")
                .success(true)
                .content(Map.of("scene", "OFFLINE_FLAP"))
                .build());
        AiPersistenceHistoryQueryService service = new AiPersistenceHistoryQueryService(
                drainStatusProvider,
                businessLiveFlowStatusProvider
        );

        AiPersistenceHistoryDetailResp response = service.getDetail(" business_live_flow ", 2233445566L);

        verify(businessLiveFlowStatusProvider).getHistoryDetail(2233445566L);
        assertTrue(Boolean.TRUE.equals(response.getExists()));
        assertEquals("OFFLINE_FLAP", response.getScene());
    }

    @Test
    void shouldRejectUnknownHistoryType() {
        AiPersistenceHistoryQueryService service = new AiPersistenceHistoryQueryService(
                mock(AiControlPlaneDrainStatusProvider.class),
                mock(AiBusinessLiveFlowStatusProvider.class)
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDetail("UNKNOWN", 1L));

        assertEquals("reportType 不支持: UNKNOWN", ex.getMessage());
    }

    @Test
    void shouldRejectNonPositiveOccurredAt() {
        AiPersistenceHistoryQueryService service = new AiPersistenceHistoryQueryService(
                mock(AiControlPlaneDrainStatusProvider.class),
                mock(AiBusinessLiveFlowStatusProvider.class)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDetail("BUSINESS_LIVE_FLOW", 0L));

        assertEquals("occurredAt 必须为正整数", ex.getMessage());
    }
}
