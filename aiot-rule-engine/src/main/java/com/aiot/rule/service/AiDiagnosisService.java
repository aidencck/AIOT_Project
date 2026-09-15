package com.aiot.rule.service;

import com.aiot.common.ai.audit.AiAuditPublisher;
import com.aiot.common.ai.client.LlmClient;
import com.aiot.common.ai.prompt.PromptRegistry;
import com.aiot.common.ai.schema.AiSchemaValidator;
import com.aiot.common.dto.ai.AiCaseItem;
import com.aiot.common.dto.ai.AiDiagnosisRequest;
import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.aiot.common.dto.ai.AiFeedbackRequest;
import com.aiot.common.dto.ai.AiFeedbackResponse;
import com.aiot.common.dto.ai.AiKnowledgeSearchResponse;
import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.rule.client.AiContextProvider;
import com.aiot.rule.client.KnowledgeSearchClient;
import com.aiot.rule.dto.AiEdgeDiagnosisReportRequest;
import com.aiot.rule.model.AiCaseRecord;
import com.aiot.rule.model.AiCaseMaterializationTask;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.model.AiFeedbackRecord;
import com.aiot.rule.repository.AiCaseRecordRepository;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiDiagnosisRecordRepository;
import com.aiot.rule.repository.AiFeedbackRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiDiagnosisService {

    private final AiContextProvider aiContextProvider;
    private final KnowledgeSearchClient knowledgeSearchClient;
    private final LlmClient llmClient;
    private final PromptRegistry promptRegistry;
    private final AiSchemaValidator aiSchemaValidator;
    private final AiAuditPublisher aiAuditPublisher;
    private final AiDiagnosisRecordRepository diagnosisRecordRepository;
    private final AiFeedbackRecordRepository feedbackRecordRepository;
    private final AiCaseRecordRepository caseRecordRepository;
    private final AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository;
    private final AiPersistenceMetrics aiPersistenceMetrics;
    private final ObjectMapper objectMapper;
    private final AiRuleDraftGenerationService aiRuleDraftGenerationService;
    private final Executor executor;
    private final Executor llmExecutor;

    public AiDiagnosisService(AiContextProvider aiContextProvider,
                              KnowledgeSearchClient knowledgeSearchClient,
                              LlmClient llmClient,
                              PromptRegistry promptRegistry,
                              AiSchemaValidator aiSchemaValidator,
                              AiAuditPublisher aiAuditPublisher,
                              AiDiagnosisRecordRepository diagnosisRecordRepository,
                              AiFeedbackRecordRepository feedbackRecordRepository,
                              AiCaseRecordRepository caseRecordRepository,
                              AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository,
                              AiPersistenceMetrics aiPersistenceMetrics,
                              ObjectMapper objectMapper,
                              AiRuleDraftGenerationService aiRuleDraftGenerationService,
                              @Qualifier("applicationTaskExecutor") Executor executor,
                              @Qualifier("aiLlmExecutor") Executor llmExecutor) {
        this.aiContextProvider = aiContextProvider;
        this.knowledgeSearchClient = knowledgeSearchClient;
        this.llmClient = llmClient;
        this.promptRegistry = promptRegistry;
        this.aiSchemaValidator = aiSchemaValidator;
        this.aiAuditPublisher = aiAuditPublisher;
        this.diagnosisRecordRepository = diagnosisRecordRepository;
        this.feedbackRecordRepository = feedbackRecordRepository;
        this.caseRecordRepository = caseRecordRepository;
        this.aiCaseMaterializationTaskRepository = aiCaseMaterializationTaskRepository;
        this.aiPersistenceMetrics = aiPersistenceMetrics;
        this.objectMapper = objectMapper;
        this.aiRuleDraftGenerationService = aiRuleDraftGenerationService;
        this.executor = executor;
        this.llmExecutor = llmExecutor;
    }

    public AiDiagnosisResponse diagnose(AiDiagnosisRequest request) {
        long startedAt = System.currentTimeMillis();
        String traceId = resolveTraceId();
        String sceneType = normalize(request.getSceneType());
        AiRuntimeContextPayload contextPayload = resolveContextPayload(request.getDeviceId(), sceneType);
        AiRuntimeContext context = contextPayload == null ? null : contextPayload.getRuntimeContext();
        List<AiCaseRecord> cases = caseRecordRepository.findBySceneType(sceneType, 3);

        String diagnosisId = UUID.randomUUID().toString();
        String promptVersion = promptRegistry.getDiagnosisPromptVersion(sceneType);

        // 请求线程立即返回确定性兜底，LLM 推理转后台回填，避免 20s+ 同步推理钳制吞吐。
        AiDiagnosisResponse response = buildFallbackDiagnosis(request, context, cases);
        response.setDiagnosisId(diagnosisId);
        response.setPromptVersion(promptVersion);
        // fallback 由确定性规则生成，不标注 LLM 模型名，避免 model_name 列误标。
        response.setSource("fallback");

        long latencyMs = System.currentTimeMillis() - startedAt;
        persistDiagnosis(diagnosisId, traceId, sceneType, request, context, response, latencyMs);
        aiAuditPublisher.publishDiagnosis(
                traceId,
                sceneType,
                llmClient.getModelName(),
                promptVersion,
                llmClient.isEnabled(),
                true,
                latencyMs
        );

        submitLlmRefine(request, sceneType, traceId, diagnosisId, startedAt, promptVersion, context, contextPayload, cases);
        submitDraftGeneration(request, response);
        return response;
    }

    private void submitLlmRefine(AiDiagnosisRequest request, String sceneType, String traceId,
                                 String diagnosisId, long startedAt, String promptVersion,
                                 AiRuntimeContext context, AiRuntimeContextPayload contextPayload,
                                 List<AiCaseRecord> cases) {
        if (!llmClient.isEnabled() || context == null || !isSceneContextMatched(sceneType, context)) {
            return;
        }
        try {
            llmExecutor.execute(() -> refineWithLlm(request, sceneType, traceId, diagnosisId,
                    startedAt, promptVersion, context, contextPayload, cases));
            aiPersistenceMetrics.recordLlmRefine("submitted");
        } catch (RejectedExecutionException ex) {
            aiPersistenceMetrics.recordLlmRefine("rejected");
            log.warn("LLM 异步推理队列已满, 保持兜底结果, diagnosisId={}", diagnosisId, ex);
        }
    }

    private void refineWithLlm(AiDiagnosisRequest request, String sceneType, String traceId,
                               String diagnosisId, long startedAt, String promptVersion,
                               AiRuntimeContext context, AiRuntimeContextPayload contextPayload,
                               List<AiCaseRecord> cases) {
        try {
            AiKnowledgeSearchResponse knowledge = knowledgeSearchClient.search(contextPayload, sceneType, request.getDeviceId());
            String llmOutput = llmClient.chatJson(
                    promptRegistry.getDiagnosisPrompt(sceneType),
                    buildUserPrompt(request, context, cases, knowledge)
            );
            AiDiagnosisResponse refined = aiSchemaValidator.validateDiagnosisJson(llmOutput);
            if (refined == null) {
                aiPersistenceMetrics.recordLlmRefine("schema_rejected");
                log.warn("LLM 异步回填被 schema 校验拒绝, 保持兜底, diagnosisId={}", diagnosisId);
                return;
            }
            refined.setDiagnosisId(diagnosisId);
            refined.setPromptVersion(promptVersion);
            refined.setModelName(llmClient.getModelName());
            refined.setSource("llm");
            long totalLatency = System.currentTimeMillis() - startedAt;
            persistDiagnosis(diagnosisId, traceId, sceneType, request, context, refined, totalLatency);
            aiAuditPublisher.publishDiagnosis(
                    traceId,
                    sceneType,
                    llmClient.getModelName(),
                    promptVersion,
                    true,
                    false,
                    totalLatency
            );
            aiPersistenceMetrics.recordLlmRefine("success");
        } catch (Exception ex) {
            log.warn("LLM 异步推理回填失败, diagnosisId={}", diagnosisId, ex);
            aiPersistenceMetrics.recordLlmRefine("exception");
        }
    }

    private void persistDiagnosis(String diagnosisId, String traceId, String sceneType,
                                  AiDiagnosisRequest request, AiRuntimeContext context,
                                  AiDiagnosisResponse response, long latencyMs) {
        diagnosisRecordRepository.save(AiDiagnosisRecord.builder()
                .diagnosisId(diagnosisId)
                .traceId(traceId)
                .sceneType(sceneType)
                .deviceId(request.getDeviceId())
                .homeId(context == null ? null : context.getHomeId())
                .eventId(request.getEventId())
                .contextSnapshot(toJson(context))
                .modelName(response.getModelName())
                .promptVersion(response.getPromptVersion())
                .diagnosisResult(toJson(response))
                .latencyMs(latencyMs)
                .createdAt(System.currentTimeMillis())
                .build());
    }

    private void submitDraftGeneration(AiDiagnosisRequest request, AiDiagnosisResponse response) {
        executor.execute(() -> {
            try {
                aiRuleDraftGenerationService.generateDraftIfEligible(request, response);
            } catch (Exception e) {
                log.warn("规则草案生成失败, deviceId={}", request.getDeviceId(), e);
            }
        });
    }

    private AiRuntimeContextPayload resolveContextPayload(String deviceId, String sceneType) {
        try {
            return aiContextProvider.getRuntimeContext(deviceId, sceneType);
        } catch (Exception ex) {
            log.warn("获取设备运行时上下文失败, 使用兜底诊断, deviceId={}", deviceId, ex);
            return null;
        }
    }

    public AiDiagnosisResponse ingestEdgeReport(AiEdgeDiagnosisReportRequest request) {
        long startedAt = System.currentTimeMillis();
        String traceId = resolveTraceId();
        AiDiagnosisResponse diagnosis = request.getDiagnosis();
        if (diagnosis == null) {
            diagnosis = new AiDiagnosisResponse();
        }
        String diagnosisId = StringUtils.hasText(diagnosis.getDiagnosisId())
                ? diagnosis.getDiagnosisId()
                : UUID.randomUUID().toString();
        String sceneType = normalize(StringUtils.hasText(request.getSceneType())
                ? request.getSceneType()
                : diagnosis.getSceneType());
        if (!StringUtils.hasText(diagnosis.getSource())) {
            diagnosis.setSource("edge-agent");
        }
        diagnosis.setDiagnosisId(diagnosisId);

        String homeId = null;
        String contextSnapshot = null;
        try {
            AiRuntimeContextPayload payload = aiContextProvider.getRuntimeContext(request.getDeviceId(), sceneType);
            if (payload != null) {
                AiRuntimeContext runtimeContext = payload.getRuntimeContext();
                homeId = runtimeContext == null ? null : runtimeContext.getHomeId();
                contextSnapshot = toJson(runtimeContext);
            }
        } catch (Exception ex) {
            log.warn("回填边缘诊断上报上下文失败, deviceId={}", request.getDeviceId(), ex);
        }

        diagnosisRecordRepository.save(AiDiagnosisRecord.builder()
                .diagnosisId(diagnosisId)
                .traceId(traceId)
                .sceneType(sceneType)
                .deviceId(request.getDeviceId())
                .homeId(homeId)
                .eventId(request.getEventId())
                .contextSnapshot(contextSnapshot)
                .modelName(diagnosis.getModelName())
                .promptVersion(diagnosis.getPromptVersion())
                .diagnosisResult(toJson(diagnosis))
                .latencyMs(System.currentTimeMillis() - startedAt)
                .createdAt(System.currentTimeMillis())
                .build());

        boolean llmEnabled = "llm".equalsIgnoreCase(diagnosis.getSource());
        aiAuditPublisher.publishDiagnosis(
                traceId,
                sceneType,
                diagnosis.getModelName(),
                diagnosis.getPromptVersion(),
                llmEnabled,
                !llmEnabled,
                System.currentTimeMillis() - startedAt
        );

        AiDiagnosisRequest draftRequest = new AiDiagnosisRequest();
        draftRequest.setDeviceId(request.getDeviceId());
        draftRequest.setSceneType(sceneType);
        draftRequest.setEventId(request.getEventId());
        final AiDiagnosisResponse finalDiagnosis = diagnosis;
        executor.execute(() -> {
            try {
                aiRuleDraftGenerationService.generateDraftIfEligible(draftRequest, finalDiagnosis);
            } catch (Exception e) {
                log.warn("规则草案生成失败, deviceId={}", request.getDeviceId(), e);
            }
        });
        return diagnosis;
    }

    public AiFeedbackResponse feedback(AiFeedbackRequest request) {
        AiDiagnosisRecord diagnosisRecord = diagnosisRecordRepository.findById(request.getDiagnosisId());
        if (diagnosisRecord == null) {
            return AiFeedbackResponse.builder()
                    .accepted(Boolean.FALSE)
                    .diagnosisId(request.getDiagnosisId())
                    .feedbackSaved(Boolean.FALSE)
                    .caseRequested(Boolean.FALSE)
                    .caseSaved(Boolean.FALSE)
                    .caseStatus("DIAGNOSIS_NOT_FOUND")
                    .message("diagnosis record not found")
                    .build();
        }
        String feedbackId = UUID.randomUUID().toString();
        feedbackRecordRepository.save(AiFeedbackRecord.builder()
                .feedbackId(feedbackId)
                .diagnosisId(request.getDiagnosisId())
                .feedbackType(normalize(request.getFeedbackType()))
                .resolutionStatus(normalize(request.getResolutionStatus()))
                .operatorId(request.getOperatorId())
                .resolutionNote(request.getResolutionNote())
                .createdAt(System.currentTimeMillis())
                .build());
        AiFeedbackResponse.AiFeedbackResponseBuilder responseBuilder = AiFeedbackResponse.builder()
                .accepted(Boolean.TRUE)
                .diagnosisId(request.getDiagnosisId())
                .feedbackId(feedbackId)
                .feedbackSaved(Boolean.TRUE);
        if (!"SOLVED".equalsIgnoreCase(request.getResolutionStatus())) {
            return responseBuilder
                    .caseRequested(Boolean.FALSE)
                    .caseSaved(Boolean.FALSE)
                    .caseStatus("NOT_REQUESTED")
                    .message("case materialization skipped because resolution status is not SOLVED")
                    .build();
        }
        return materializeCase(diagnosisRecord, request, feedbackId, responseBuilder, false, null);
    }

    public boolean replayCaseMaterialization(AiCaseMaterializationTask task) {
        if (task == null || !StringUtils.hasText(task.getDiagnosisId()) || !StringUtils.hasText(task.getFeedbackId())) {
            return false;
        }
        AiDiagnosisRecord diagnosisRecord = diagnosisRecordRepository.findById(task.getDiagnosisId());
        if (diagnosisRecord == null) {
            requeueCaseTask(task, "diagnosis record not found during replay");
            return false;
        }
        AiFeedbackRequest request = new AiFeedbackRequest();
        request.setDiagnosisId(task.getDiagnosisId());
        request.setFeedbackType(task.getFeedbackType());
        request.setResolutionStatus(task.getResolutionStatus());
        request.setResolutionNote(task.getResolutionNote());
        request.setOperatorId(task.getOperatorId());
        AiFeedbackResponse response = materializeCase(
                diagnosisRecord,
                request,
                task.getFeedbackId(),
                AiFeedbackResponse.builder()
                        .accepted(Boolean.TRUE)
                        .diagnosisId(task.getDiagnosisId())
                        .feedbackId(task.getFeedbackId())
                        .feedbackSaved(Boolean.TRUE),
                true,
                task
        );
        return Boolean.TRUE.equals(response.getCaseSaved());
    }

    public List<AiCaseItem> searchCases(String sceneType, int limit) {
        return caseRecordRepository.findBySceneType(normalize(sceneType), limit).stream()
                .map(item -> AiCaseItem.builder()
                        .caseId(item.getCaseId())
                        .sceneType(item.getSceneType())
                        .symptom(item.getSymptom())
                        .rootCause(item.getRootCause())
                        .resolution(item.getResolution())
                        .effectivenessScore(item.getEffectivenessScore())
                        .sourceFeedbackId(item.getSourceFeedbackId())
                        .createdAt(item.getCreatedAt() == null ? null : String.valueOf(item.getCreatedAt()))
                        .build())
                .collect(Collectors.toList());
    }

    private String buildUserPrompt(AiDiagnosisRequest request,
                                   AiRuntimeContext context,
                                   List<AiCaseRecord> cases,
                                   AiKnowledgeSearchResponse knowledge) {
        return "场景=" + normalize(request.getSceneType())
                + "\n设备上下文=" + toJson(context)
                + "\n历史案例=" + toJson(cases)
                + "\n物模型知识片段=" + toJson(knowledge == null ? List.of() : knowledge.getChunks())
                + "\n请只输出 JSON，不要输出 markdown。";
    }

    private AiDiagnosisResponse buildFallbackDiagnosis(AiDiagnosisRequest request,
                                                       AiRuntimeContext context,
                                                       List<AiCaseRecord> cases) {
        String sceneType = normalize(request.getSceneType());
        List<String> evidence = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        if (context != null) {
            evidence.add("deviceId=" + context.getDeviceId());
            if (StringUtils.hasText(context.getOnlineStatus())) {
                evidence.add("onlineStatus=" + context.getOnlineStatus());
            }
            if (StringUtils.hasText(context.getFirmwareVersion())) {
                evidence.add("firmwareVersion=" + context.getFirmwareVersion());
            }
        } else {
            evidence.add("未获取到设备上下文，使用保守诊断");
        }
        if (!cases.isEmpty()) {
            actions.add("参考历史案例: " + cases.get(0).getResolution());
        }
        switch (sceneType) {
            case "PROVISION_FAILURE" -> {
                evidence.add("检测到配网失败场景");
                actions.add("检查家庭绑定、设备命名冲突和配网 token 是否过期");
                actions.add("确认设备与产品型号关系正确");
                return AiDiagnosisResponse.builder()
                        .sceneType(sceneType)
                        .summary("设备处于配网失败场景，优先排查 token、家庭归属和设备资产状态")
                        .rootCauseCategory("PROVISIONING_CONFLICT")
                        .confidence(0.58D)
                        .evidence(evidence)
                        .recommendedActions(actions)
                        .ruleDraftable(Boolean.FALSE)
                        .riskLevel("MEDIUM")
                        .build();
            }
            case "SHADOW_DIFF" -> {
                evidence.add("检测到影子 desired/reported 存在差异");
                actions.add("检查设备离线、指令 ACK 和属性上报链路");
                actions.add("必要时创建状态对账规则");
                return AiDiagnosisResponse.builder()
                        .sceneType(sceneType)
                        .summary("设备影子期望态与实报态不一致，疑似控制回执或状态同步异常")
                        .rootCauseCategory("STATE_SYNC_DRIFT")
                        .confidence(0.61D)
                        .evidence(evidence)
                        .recommendedActions(actions)
                        .ruleDraftable(Boolean.TRUE)
                        .riskLevel("MEDIUM")
                        .build();
            }
            default -> {
                evidence.add("检测到设备离线/抖动场景");
                actions.add("检查供电、网络覆盖和网关链路稳定性");
                actions.add("建议生成频发离线规则草案");
                return AiDiagnosisResponse.builder()
                        .sceneType("OFFLINE_FLAP")
                        .summary("设备存在高频离线或上下线抖动，优先排查网络与供电稳定性")
                        .rootCauseCategory("NETWORK_INSTABILITY")
                        .confidence(0.66D)
                        .evidence(evidence)
                        .recommendedActions(actions)
                        .ruleDraftable(Boolean.TRUE)
                        .riskLevel("HIGH")
                        .build();
            }
        }
    }

    private AiDiagnosisResponse fromDiagnosisJson(String json) {
        try {
            return objectMapper.readValue(json, AiDiagnosisResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private AiFeedbackResponse materializeCase(AiDiagnosisRecord diagnosisRecord,
                                               AiFeedbackRequest request,
                                               String feedbackId,
                                               AiFeedbackResponse.AiFeedbackResponseBuilder responseBuilder,
                                               boolean replay,
                                               AiCaseMaterializationTask replayTask) {
        AiDiagnosisResponse diagnosisResponse = fromDiagnosisJson(diagnosisRecord.getDiagnosisResult());
        if (diagnosisResponse == null) {
            if (replayTask != null) {
                requeueCaseTask(replayTask, "diagnosis result cannot be parsed for case materialization");
            }
            aiPersistenceMetrics.recordCaseMaterialization("skipped_invalid_diagnosis");
            return responseBuilder
                    .caseRequested(Boolean.TRUE)
                    .caseSaved(Boolean.FALSE)
                    .caseStatus("SKIPPED_DIAGNOSIS_INVALID")
                    .message("diagnosis result cannot be parsed for case materialization")
                    .build();
        }
        String caseId = UUID.randomUUID().toString();
        try {
            caseRecordRepository.save(AiCaseRecord.builder()
                    .caseId(caseId)
                    .sceneType(diagnosisRecord.getSceneType())
                    .symptom(diagnosisResponse.getSummary())
                    .rootCause(diagnosisResponse.getRootCauseCategory())
                    .resolution(resolveCaseResolution(request, diagnosisResponse))
                    .effectivenessScore(resolveEffectivenessScore(request.getFeedbackType(), request.getResolutionStatus()))
                    .sourceFeedbackId(feedbackId)
                    .createdAt(System.currentTimeMillis())
                    .build());
            if (replayTask != null) {
                aiCaseMaterializationTaskRepository.delete(replayTask.getTaskId());
                aiPersistenceMetrics.recordCaseMaterialization("replayed");
            } else {
                aiPersistenceMetrics.recordCaseMaterialization("created");
            }
            return responseBuilder
                    .caseRequested(Boolean.TRUE)
                    .caseSaved(Boolean.TRUE)
                    .caseStatus(replay ? "REPLAYED" : "CREATED")
                    .caseId(caseId)
                    .message(replay ? "case materialization replayed successfully" : "case materialized successfully")
                    .build();
        } catch (Exception ex) {
            AiCaseMaterializationTask task = replayTask == null
                    ? enqueueCaseTask(request, feedbackId)
                    : requeueCaseTask(replayTask, ex.getMessage());
            aiPersistenceMetrics.recordCaseMaterialization(replay ? "retry_failed" : "queued");
            return responseBuilder
                    .caseRequested(Boolean.TRUE)
                    .caseSaved(Boolean.FALSE)
                    .caseStatus("QUEUED_RETRY")
                    .caseTaskId(task == null ? null : task.getTaskId())
                    .message("case materialization queued for retry: " + trimMessage(ex.getMessage()))
                    .build();
        }
    }

    private AiCaseMaterializationTask enqueueCaseTask(AiFeedbackRequest request, String feedbackId) {
        AiCaseMaterializationTask task = AiCaseMaterializationTask.builder()
                .taskId(UUID.randomUUID().toString())
                .diagnosisId(request.getDiagnosisId())
                .feedbackId(feedbackId)
                .feedbackType(normalize(request.getFeedbackType()))
                .resolutionStatus(normalize(request.getResolutionStatus()))
                .resolutionNote(request.getResolutionNote())
                .operatorId(request.getOperatorId())
                .queuedAt(System.currentTimeMillis())
                .lastRetryAt(null)
                .retryCount(0)
                .lastError("case_materialization_failed")
                .build();
        aiCaseMaterializationTaskRepository.save(task);
        return task;
    }

    private AiCaseMaterializationTask requeueCaseTask(AiCaseMaterializationTask task, String errorMessage) {
        AiCaseMaterializationTask updated = task.toBuilder()
                .lastRetryAt(System.currentTimeMillis())
                .retryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1)
                .lastError(trimMessage(errorMessage))
                .build();
        aiCaseMaterializationTaskRepository.save(updated);
        return updated;
    }

    private String resolveCaseResolution(AiFeedbackRequest request, AiDiagnosisResponse diagnosisResponse) {
        if (StringUtils.hasText(request.getResolutionNote())) {
            return request.getResolutionNote();
        }
        List<String> recommendedActions = diagnosisResponse.getRecommendedActions();
        return recommendedActions == null || recommendedActions.isEmpty()
                ? "follow diagnosed recommended actions"
                : String.join("；", recommendedActions);
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown";
        }
        return message.length() > 256 ? message.substring(0, 256) : message;
    }

    private Double resolveEffectivenessScore(String feedbackType, String resolutionStatus) {
        if ("SOLVED".equalsIgnoreCase(resolutionStatus) && "ACCEPTED".equalsIgnoreCase(feedbackType)) {
            return 1.0D;
        }
        if ("SOLVED".equalsIgnoreCase(resolutionStatus)) {
            return 0.8D;
        }
        return 0.5D;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static final int CONTEXT_DIMENSION_COUNT = 8;
    private static final int MIN_CONTEXT_COMPLETENESS_SCORE = 4;

    private boolean isSceneContextMatched(String sceneType, AiRuntimeContext context) {
        if (context == null) {
            return false;
        }
        if ("OFFLINE_FLAP".equals(sceneType)) {
            Integer status = context.getStatus();
            if (status == null || (status != 1 && status != 2)) {
                log.warn("数据面门禁拒绝: OFFLINE_FLAP 在线状态不满足, status={}, deviceId={}, 强制兜底",
                        status, context.getDeviceId());
                return false;
            }
        }
        int completenessScore = contextCompletenessScore(context);
        if (completenessScore < MIN_CONTEXT_COMPLETENESS_SCORE) {
            log.warn("数据面门禁拒绝: 上下文完整度不足 score={}/{} , deviceId={}, 强制兜底",
                    completenessScore, CONTEXT_DIMENSION_COUNT, context.getDeviceId());
            return false;
        }
        return true;
    }

    private int contextCompletenessScore(AiRuntimeContext context) {
        int score = 0;
        if (StringUtils.hasText(context.getHomeId())) {
            score++;
        }
        if (StringUtils.hasText(context.getRoomId())) {
            score++;
        }
        if (StringUtils.hasText(context.getGatewayId())) {
            score++;
        }
        if (StringUtils.hasText(context.getFirmwareVersion())) {
            score++;
        }
        if (StringUtils.hasText(context.getOnlineStatus())) {
            score++;
        }
        if (StringUtils.hasText(context.getLastHeartbeatTime())) {
            score++;
        }
        if (isShadowAvailable(context.getShadowSummary())) {
            score++;
        }
        if (context.getRecentEvents() != null && !context.getRecentEvents().isEmpty()) {
            score++;
        }
        return score;
    }

    private boolean isShadowAvailable(Map<String, Object> shadowSummary) {
        return shadowSummary != null
                && !shadowSummary.isEmpty()
                && Boolean.TRUE.equals(shadowSummary.get("shadowAvailable"));
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
