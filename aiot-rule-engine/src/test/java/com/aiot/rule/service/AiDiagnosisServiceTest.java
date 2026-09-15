package com.aiot.rule.service;

import com.aiot.common.ai.audit.AiAuditPublisher;
import com.aiot.common.ai.client.LlmClient;
import com.aiot.common.ai.prompt.PromptRegistry;
import com.aiot.common.ai.schema.AiSchemaValidator;
import com.aiot.common.dto.ai.AiDiagnosisRequest;
import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.aiot.common.dto.ai.AiFeedbackRequest;
import com.aiot.common.dto.ai.AiFeedbackResponse;
import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.rule.client.AiContextProvider;
import com.aiot.rule.client.KnowledgeSearchClient;
import com.aiot.rule.dto.AiEdgeDiagnosisReportRequest;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiCaseRecordRepository;
import com.aiot.rule.repository.AiDiagnosisRecordRepository;
import com.aiot.rule.repository.AiFeedbackRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDiagnosisServiceTest {

    @Test
    void shouldUseFallbackDiagnosisWhenLlmDisabled() {
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(new ObjectMapper());
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        when(llmClient.isEnabled()).thenReturn(false);
        when(llmClient.getModelName()).thenReturn("stub-model");
        when(caseRecordRepository.findBySceneType("OFFLINE_FLAP", 3)).thenReturn(Collections.emptyList());
        doNothing().when(diagnosisRecordRepository).save(any());
        doNothing().when(auditPublisher).publishDiagnosis(any(), any(), any(), any(), any(Boolean.class), any(Boolean.class), any(Long.class));

        AiDiagnosisService aiDiagnosisService = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                new ObjectMapper(),
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );

        AiDiagnosisRequest request = new AiDiagnosisRequest();
        request.setDeviceId("dev-1");
        request.setSceneType("OFFLINE_FLAP");

        AiDiagnosisResponse response = aiDiagnosisService.diagnose(request);

        assertEquals("OFFLINE_FLAP", response.getSceneType());
        assertTrue(Boolean.TRUE.equals(response.getRuleDraftable()));
        assertEquals("fallback", response.getSource());
    }

    @Test
    void shouldReturnCaseCreatedWhenSolvedFeedbackSaved() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(objectMapper);
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        AiDiagnosisResponse diagnosis = AiDiagnosisResponse.builder()
                .summary("summary")
                .rootCauseCategory("NETWORK_INSTABILITY")
                .recommendedActions(java.util.List.of("action-1"))
                .build();
        when(diagnosisRecordRepository.findById("diag-1")).thenReturn(AiDiagnosisRecord.builder()
                .diagnosisId("diag-1")
                .sceneType("OFFLINE_FLAP")
                .diagnosisResult(objectMapper.writeValueAsString(diagnosis))
                .build());
        doNothing().when(feedbackRecordRepository).save(any());
        doNothing().when(caseRecordRepository).save(any());

        AiDiagnosisService service = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                objectMapper,
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );
        AiFeedbackRequest request = new AiFeedbackRequest();
        request.setDiagnosisId("diag-1");
        request.setFeedbackType("ACCEPTED");
        request.setResolutionStatus("SOLVED");
        request.setResolutionNote("fixed");

        AiFeedbackResponse response = service.feedback(request);

        assertTrue(Boolean.TRUE.equals(response.getAccepted()));
        assertTrue(Boolean.TRUE.equals(response.getFeedbackSaved()));
        assertTrue(Boolean.TRUE.equals(response.getCaseRequested()));
        assertTrue(Boolean.TRUE.equals(response.getCaseSaved()));
        assertEquals("CREATED", response.getCaseStatus());
        verify(caseTaskRepository, never()).save(any());
    }

    @Test
    void shouldQueueCaseTaskWhenCaseSaveFails() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(objectMapper);
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        AiDiagnosisResponse diagnosis = AiDiagnosisResponse.builder()
                .summary("summary")
                .rootCauseCategory("NETWORK_INSTABILITY")
                .recommendedActions(java.util.List.of("action-1"))
                .build();
        when(diagnosisRecordRepository.findById("diag-2")).thenReturn(AiDiagnosisRecord.builder()
                .diagnosisId("diag-2")
                .sceneType("OFFLINE_FLAP")
                .diagnosisResult(objectMapper.writeValueAsString(diagnosis))
                .build());
        doNothing().when(feedbackRecordRepository).save(any());
        org.mockito.Mockito.doThrow(new RuntimeException("redis unavailable")).when(caseRecordRepository).save(any());

        AiDiagnosisService service = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                objectMapper,
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );
        AiFeedbackRequest request = new AiFeedbackRequest();
        request.setDiagnosisId("diag-2");
        request.setFeedbackType("ACCEPTED");
        request.setResolutionStatus("SOLVED");

        AiFeedbackResponse response = service.feedback(request);

        assertTrue(Boolean.TRUE.equals(response.getAccepted()));
        assertFalse(Boolean.TRUE.equals(response.getCaseSaved()));
        assertEquals("QUEUED_RETRY", response.getCaseStatus());
        assertTrue(response.getCaseTaskId() != null && !response.getCaseTaskId().isBlank());
        verify(caseTaskRepository).save(any());
    }

    @Test
    void shouldReturnFallbackDiagnosisWhenRuntimeContextFails() {
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(new ObjectMapper());
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        when(contextProvider.getRuntimeContext("dev-1", "OFFLINE_FLAP"))
                .thenThrow(new RuntimeException("context service unavailable"));
        when(llmClient.isEnabled()).thenReturn(false);
        when(llmClient.getModelName()).thenReturn("stub-model");
        when(caseRecordRepository.findBySceneType("OFFLINE_FLAP", 3)).thenReturn(Collections.emptyList());
        doNothing().when(diagnosisRecordRepository).save(any());
        doNothing().when(auditPublisher).publishDiagnosis(any(), any(), any(), any(), any(Boolean.class), any(Boolean.class), any(Long.class));

        AiDiagnosisService service = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                new ObjectMapper(),
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );

        AiDiagnosisRequest request = new AiDiagnosisRequest();
        request.setDeviceId("dev-1");
        request.setSceneType("OFFLINE_FLAP");

        AiDiagnosisResponse response = service.diagnose(request);

        assertEquals("OFFLINE_FLAP", response.getSceneType());
        assertEquals("fallback", response.getSource());
        verify(diagnosisRecordRepository).save(any());
    }

    @Test
    void shouldPersistEdgeDiagnosisReport() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(objectMapper);
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        AiDiagnosisService service = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                objectMapper,
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );

        AiDiagnosisResponse diagnosis = AiDiagnosisResponse.builder()
                .diagnosisId("edge-diag-1")
                .sceneType("OFFLINE_FLAP")
                .summary("offline")
                .ruleDraftable(Boolean.TRUE)
                .build();
        AiEdgeDiagnosisReportRequest request = new AiEdgeDiagnosisReportRequest();
        request.setDeviceId("dev-9");
        request.setSceneType("OFFLINE_FLAP");
        request.setEventId("evt-1");
        request.setDiagnosis(diagnosis);

        AiDiagnosisResponse response = service.ingestEdgeReport(request);

        assertEquals("edge-diag-1", response.getDiagnosisId());
        assertEquals("edge-agent", response.getSource());

        ArgumentCaptor<AiDiagnosisRecord> recordCaptor = ArgumentCaptor.forClass(AiDiagnosisRecord.class);
        verify(diagnosisRecordRepository).save(recordCaptor.capture());
        AiDiagnosisRecord record = recordCaptor.getValue();
        assertEquals("edge-diag-1", record.getDiagnosisId());
        assertEquals("OFFLINE_FLAP", record.getSceneType());
        assertEquals("dev-9", record.getDeviceId());
        assertEquals("evt-1", record.getEventId());
        assertTrue(record.getDiagnosisResult().contains("\"source\":\"edge-agent\""));
        assertNotNull(record.getLatencyMs());
        verify(auditPublisher).publishDiagnosis(any(), any(), any(), any(), any(Boolean.class), any(Boolean.class), any(Long.class));
    }

    @Test
    void shouldUseFallbackWhenOfflineFlapDeviceStatusUnactivated() {
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(new ObjectMapper());
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        AiRuntimeContext runtimeContext = AiRuntimeContext.builder()
                .deviceId("dev-1")
                .status(0)
                .onlineStatus("unactivated")
                .build();
        AiRuntimeContextPayload payload = AiRuntimeContextPayload.builder()
                .runtimeContext(runtimeContext)
                .build();
        when(contextProvider.getRuntimeContext("dev-1", "OFFLINE_FLAP")).thenReturn(payload);
        when(llmClient.isEnabled()).thenReturn(true);
        when(llmClient.getModelName()).thenReturn("stub-model");
        when(caseRecordRepository.findBySceneType("OFFLINE_FLAP", 3)).thenReturn(Collections.emptyList());
        doNothing().when(diagnosisRecordRepository).save(any());
        doNothing().when(auditPublisher).publishDiagnosis(any(), any(), any(), any(), any(Boolean.class), any(Boolean.class), any(Long.class));

        AiDiagnosisService service = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                new ObjectMapper(),
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );

        AiDiagnosisRequest request = new AiDiagnosisRequest();
        request.setDeviceId("dev-1");
        request.setSceneType("OFFLINE_FLAP");

        AiDiagnosisResponse response = service.diagnose(request);

        assertEquals("fallback", response.getSource());
        assertTrue(response.getEvidence().contains("onlineStatus=unactivated"));
        verify(llmClient, never()).chatJson(any(), any());
    }

    @Test
    void shouldBackfillHomeIdAndContextSnapshotForEdgeReport() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiContextProvider contextProvider = mock(AiContextProvider.class);
        KnowledgeSearchClient knowledgeSearchClient = mock(KnowledgeSearchClient.class);
        LlmClient llmClient = mock(LlmClient.class);
        PromptRegistry promptRegistry = new PromptRegistry();
        AiSchemaValidator validator = new AiSchemaValidator(objectMapper);
        AiAuditPublisher auditPublisher = mock(AiAuditPublisher.class);
        AiDiagnosisRecordRepository diagnosisRecordRepository = mock(AiDiagnosisRecordRepository.class);
        AiFeedbackRecordRepository feedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        AiCaseRecordRepository caseRecordRepository = mock(AiCaseRecordRepository.class);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        AiRuntimeContext runtimeContext = AiRuntimeContext.builder()
                .deviceId("dev-9")
                .homeId("home-9")
                .status(1)
                .onlineStatus("online")
                .build();
        AiRuntimeContextPayload payload = AiRuntimeContextPayload.builder()
                .runtimeContext(runtimeContext)
                .build();
        when(contextProvider.getRuntimeContext("dev-9", "OFFLINE_FLAP")).thenReturn(payload);

        AiDiagnosisService service = new AiDiagnosisService(
                contextProvider,
                knowledgeSearchClient,
                llmClient,
                promptRegistry,
                validator,
                auditPublisher,
                diagnosisRecordRepository,
                feedbackRecordRepository,
                caseRecordRepository,
                caseTaskRepository,
                metrics,
                objectMapper,
                mock(AiRuleDraftGenerationService.class),
                mock(Executor.class),
                mock(Executor.class)
        );

        AiDiagnosisResponse diagnosis = AiDiagnosisResponse.builder()
                .diagnosisId("edge-diag-1")
                .sceneType("OFFLINE_FLAP")
                .summary("offline")
                .ruleDraftable(Boolean.TRUE)
                .build();
        AiEdgeDiagnosisReportRequest request = new AiEdgeDiagnosisReportRequest();
        request.setDeviceId("dev-9");
        request.setSceneType("OFFLINE_FLAP");
        request.setEventId("evt-1");
        request.setDiagnosis(diagnosis);

        AiDiagnosisResponse response = service.ingestEdgeReport(request);

        assertEquals("edge-diag-1", response.getDiagnosisId());

        ArgumentCaptor<AiDiagnosisRecord> recordCaptor = ArgumentCaptor.forClass(AiDiagnosisRecord.class);
        verify(diagnosisRecordRepository).save(recordCaptor.capture());
        AiDiagnosisRecord record = recordCaptor.getValue();
        assertEquals("home-9", record.getHomeId());
        assertTrue(record.getContextSnapshot().contains("\"homeId\":\"home-9\""));
        assertNotNull(record.getLatencyMs());
    }
}
