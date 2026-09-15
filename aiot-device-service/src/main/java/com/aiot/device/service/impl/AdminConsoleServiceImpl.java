package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.dto.ai.AiBusinessLiveFlowSnapshot;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.AdminAiEvalReportResp;
import com.aiot.device.dto.AdminAiBusinessLiveFlowResp;
import com.aiot.device.dto.AdminClosureStageResp;
import com.aiot.device.dto.AdminDevicePageReq;
import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;
import com.aiot.device.dto.AdminOtaTaskPageReq;
import com.aiot.device.dto.AdminWorkbenchResp;
import com.aiot.device.dto.DevicePageReq;
import com.aiot.device.dto.DevicePageResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaTaskPageReq;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.service.AdminConsoleRemoteQueryFacade;
import com.aiot.device.service.AdminConsoleService;
import com.aiot.device.service.AiPersistenceAdminFacade;
import com.aiot.device.service.DeviceService;
import com.aiot.device.service.OtaService;
import com.aiot.device.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminConsoleServiceImpl implements AdminConsoleService {

    private static final List<String> DEFAULT_AI_EVAL_SCENES = List.of(
            "OFFLINE_FLAP",
            "PROVISION_FAILURE",
            "SHADOW_DIFF"
    );

    private final ProductService productService;
    private final DeviceService deviceService;
    private final OtaService otaService;
    private final AiPersistenceAdminFacade aiPersistenceAdminFacade;
    private final AdminConsoleRemoteQueryFacade adminConsoleRemoteQueryFacade;

    public AdminConsoleServiceImpl(ProductService productService,
                                   DeviceService deviceService,
                                   OtaService otaService,
                                   AiPersistenceAdminFacade aiPersistenceAdminFacade,
                                   AdminConsoleRemoteQueryFacade adminConsoleRemoteQueryFacade) {
        this.productService = productService;
        this.deviceService = deviceService;
        this.otaService = otaService;
        this.aiPersistenceAdminFacade = aiPersistenceAdminFacade;
        this.adminConsoleRemoteQueryFacade = adminConsoleRemoteQueryFacade;
    }

    @Override
    public AdminWorkbenchResp getWorkbench(String homeId, String authorizationHeader) {
        AdminConsoleAggregateData aggregateData = loadAggregateData(homeId, authorizationHeader);
        AdminConsoleOverviewResp overview = buildOverview(aggregateData);
        List<AdminClosureStageResp> closureStages = buildClosureStages(aggregateData, overview);
        return AdminWorkbenchResp.builder()
                .selectedHomeId(aggregateData.selectedHomeId())
                .closureScore(calculateClosureScore(closureStages))
                .homes(aggregateData.homes())
                .members(aggregateData.members())
                .products(aggregateData.products())
                .devices(aggregateData.devices())
                .otaTasks(aggregateData.otaTasks())
                .opsOverview(aggregateData.opsOverview())
                .aiEvalReports(aggregateData.aiEvalReports())
                .closureStages(closureStages)
                .overview(overview)
                .build();
    }

    @Override
    public AdminConsoleOverviewResp getOverview(String homeId, String authorizationHeader) {
        return buildOverview(loadAggregateData(homeId, authorizationHeader));
    }

    @Override
    public AdminLatestClosureResp getLatestClosure(String homeId, String authorizationHeader) {
        return buildLatestClosure(loadAggregateData(homeId, authorizationHeader));
    }

    @Override
    public DevicePageResp pageDevices(AdminDevicePageReq req, String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        List<Map<String, Object>> homes = adminConsoleRemoteQueryFacade.loadHomes(authorizationHeader);
        String selectedHomeId = pickHomeId(req.getHomeId(), homes);
        if (!StringUtils.hasText(selectedHomeId)) {
            return emptyDevicePage(req);
        }

        DevicePageReq deviceReq = new DevicePageReq();
        deviceReq.setHomeId(selectedHomeId);
        deviceReq.setProductKey(req.getProductKey());
        deviceReq.setStatus(req.getStatus());
        deviceReq.setPageNo(req.getPageNo());
        deviceReq.setPageSize(req.getPageSize());
        return deviceService.pageDevices(deviceReq);
    }

    @Override
    public OtaTaskPageResp pageOtaTasks(AdminOtaTaskPageReq req, String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        List<Map<String, Object>> homes = adminConsoleRemoteQueryFacade.loadHomes(authorizationHeader);
        String selectedHomeId = pickHomeId(req.getHomeId(), homes);
        if (!StringUtils.hasText(selectedHomeId)) {
            return emptyOtaTaskPage(req);
        }

        OtaTaskPageReq otaReq = new OtaTaskPageReq();
        otaReq.setHomeId(selectedHomeId);
        otaReq.setProductKey(req.getProductKey());
        otaReq.setStatus(req.getStatus());
        otaReq.setPageNo(req.getPageNo());
        otaReq.setPageSize(req.getPageSize());
        return otaService.pageUpgradeTasks(otaReq);
    }

    @Override
    public List<AdminAiEvalReportResp> listAiEvalReports(List<String> sceneTypes, String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        List<String> scenes = normalizeSceneTypes(sceneTypes);
        List<AdminAiEvalReportResp> reports = new ArrayList<>();
        for (String sceneType : scenes) {
            reports.add(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate(sceneType));
        }
        return reports;
    }

    @Override
    public AdminAiBusinessLiveFlowResp getAiBusinessLiveFlow(String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        AiBusinessLiveFlowSnapshot report = aiPersistenceAdminFacade.getBusinessLiveFlow();
        return AdminAiBusinessLiveFlowResp.builder()
                .reportExists(report == null ? Boolean.FALSE : report.getReportExists())
                .reportPath(report == null ? null : report.getReportPath())
                .verifiedAt(report == null ? null : report.getVerifiedAt())
                .scene(report == null ? null : report.getScene())
                .success(report == null ? null : report.getSuccess())
                .deviceId(report == null ? null : report.getDeviceId())
                .globalDeviceId(report == null ? null : report.getGlobalDeviceId())
                .authIdentity(report == null ? null : report.getAuthIdentity())
                .deviceSn(report == null ? null : report.getDeviceSn())
                .eventId(report == null ? null : report.getEventId())
                .diagnosisId(report == null ? null : report.getDiagnosisId())
                .feedbackId(report == null ? null : report.getFeedbackId())
                .caseId(report == null ? null : report.getCaseId())
                .mysqlWriteOutboxCount(report == null ? null : report.getMysqlWriteOutboxCount())
                .caseMaterializationTaskCount(report == null ? null : report.getCaseMaterializationTaskCount())
                .diagnosisMirrorExists(report == null ? null : report.getDiagnosisMirrorExists())
                .feedbackMirrorExists(report == null ? null : report.getFeedbackMirrorExists())
                .caseMirrorExists(report == null ? null : report.getCaseMirrorExists())
                .build();
    }

    @Override
    public AiPersistenceAdminQueryResp getAiPersistenceQuery(String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        return aiPersistenceAdminFacade.query();
    }

    @Override
    public AiPersistenceHistoryDetailResp getAiPersistenceHistoryDetail(String reportType,
                                                                       Long occurredAt,
                                                                       String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        ensureAiPersistenceHistoryDetailQuery(reportType, occurredAt);
        return aiPersistenceAdminFacade.getHistoryDetail(reportType, occurredAt);
    }

    private AdminConsoleAggregateData loadAggregateData(String requestedHomeId, String authorizationHeader) {
        ensureAuthorizationHeader(authorizationHeader);
        List<Map<String, Object>> homes = adminConsoleRemoteQueryFacade.loadHomes(authorizationHeader);
        String selectedHomeId = pickHomeId(requestedHomeId, homes);
        boolean hasHome = StringUtils.hasText(selectedHomeId);
        List<Map<String, Object>> members = hasHome
                ? adminConsoleRemoteQueryFacade.loadHomeMembers(selectedHomeId, authorizationHeader)
                : List.of();
        List<ProductResp> products = productService.listProducts();
        List<DeviceResp> devices = hasHome ? deviceService.listDevicesByHomeId(selectedHomeId) : List.of();
        List<OtaUpgradeTaskResp> otaTasks = hasHome ? otaService.listUpgradeTasks(selectedHomeId) : List.of();
        Map<String, Object> opsOverview = adminConsoleRemoteQueryFacade.loadOpsOverview();
        List<AdminAiEvalReportResp> aiEvalReports = listAiEvalReports(DEFAULT_AI_EVAL_SCENES, authorizationHeader);
        return new AdminConsoleAggregateData(
                homes,
                selectedHomeId,
                members,
                products,
                devices,
                otaTasks,
                opsOverview,
                aiEvalReports
        );
    }

    private AdminConsoleOverviewResp buildOverview(AdminConsoleAggregateData aggregateData) {
        List<Map<String, Object>> homes = aggregateData.homes();
        List<Map<String, Object>> members = aggregateData.members();
        List<ProductResp> products = aggregateData.products();
        List<DeviceResp> devices = aggregateData.devices();
        List<OtaUpgradeTaskResp> otaTasks = aggregateData.otaTasks();
        Map<String, Object> ops = aggregateData.opsOverview();
        List<AdminAiEvalReportResp> aiEvalReports = aggregateData.aiEvalReports();

        return AdminConsoleOverviewResp.builder()
                .homeCount(homes.size())
                .memberCount(members.size())
                .productCount(products.size())
                .deviceCount(devices.size())
                .otaTaskCount(otaTasks.size())
                .todayAlarmCount(toInteger(ops.get("todayAlarmCount")))
                .pendingWorkOrderCount(toInteger(ops.get("pendingWorkOrderCount")))
                .oneTimeResolveRate(toDouble(ops.get("oneTimeResolveRate")))
                .aiEvalReadyCount((int) aiEvalReports.stream().filter(item -> Boolean.TRUE.equals(item.getExists())).count())
                .aiEvalPassedCount((int) aiEvalReports.stream().filter(item -> Boolean.TRUE.equals(item.getGatePassed())).count())
                .aiEvalLastGeneratedAt(resolveLatestGeneratedAt(aiEvalReports))
                .aiPersistenceMysqlEnabled(toBoolean(ops.get("aiPersistenceMysqlEnabled")))
                .aiPersistenceReadMode(toStringValue(ops.get("aiPersistenceReadMode")))
                .aiPersistenceConfiguredReadMode(toStringValue(ops.get("aiPersistenceConfiguredReadMode")))
                .aiPersistenceMysqlReady(toBoolean(ops.get("aiPersistenceMysqlReady")))
                .aiPersistenceMysqlCutoverReady(toBoolean(ops.get("aiPersistenceMysqlCutoverReady")))
                .aiPersistenceMysqlCutoverBlockReason(toStringValue(ops.get("aiPersistenceMysqlCutoverBlockReason")))
                .aiPersistenceBackfillManifestExists(toBoolean(ops.get("aiPersistenceBackfillManifestExists")))
                .aiPersistenceBackfillWrittenTotal(toInteger(ops.get("aiPersistenceBackfillWrittenTotal")))
                .aiPersistenceConsistencyReportExists(toBoolean(ops.get("aiPersistenceConsistencyReportExists")))
                .aiPersistenceConsistencyPassed(toBoolean(ops.get("aiPersistenceConsistencyPassed")))
                .aiPersistenceConsistencyTotalMismatch(toInteger(ops.get("aiPersistenceConsistencyTotalMismatch")))
                .aiPersistenceConstraintsReportExists(toBoolean(ops.get("aiPersistenceConstraintsReportExists")))
                .aiPersistenceConstraintsGatePassed(toBoolean(ops.get("aiPersistenceConstraintsGatePassed")))
                .aiPersistenceConstraintsFailedCount(toInteger(ops.get("aiPersistenceConstraintsFailedCount")))
                .aiPersistenceMigrationGatePassed(toBoolean(ops.get("aiPersistenceMigrationGatePassed")))
                .aiPersistenceMysqlWriteOutboxStoreMode(toStringValue(ops.get("aiPersistenceMysqlWriteOutboxStoreMode")))
                .aiPersistenceMysqlWriteOutboxLegacyRedisPendingCount(toInteger(ops.get("aiPersistenceMysqlWriteOutboxLegacyRedisPendingCount")))
                .aiPersistenceMysqlWriteOutboxReplayEnabled(toBoolean(ops.get("aiPersistenceMysqlWriteOutboxReplayEnabled")))
                .aiPersistenceMysqlWriteOutboxReplayBatchSize(toInteger(ops.get("aiPersistenceMysqlWriteOutboxReplayBatchSize")))
                .aiPersistenceMysqlWriteOutboxReplayFixedDelayMs(toLong(ops.get("aiPersistenceMysqlWriteOutboxReplayFixedDelayMs")))
                .aiPersistenceMysqlWriteOutboxPendingCount(toInteger(ops.get("aiPersistenceMysqlWriteOutboxPendingCount")))
                .aiPersistenceMysqlWriteOutboxOldestAgeSeconds(toLong(ops.get("aiPersistenceMysqlWriteOutboxOldestAgeSeconds")))
                .aiPersistenceCaseMaterializationStoreMode(toStringValue(ops.get("aiPersistenceCaseMaterializationStoreMode")))
                .aiPersistenceCaseMaterializationLegacyRedisPendingCount(toInteger(ops.get("aiPersistenceCaseMaterializationLegacyRedisPendingCount")))
                .aiPersistenceCaseMaterializationReplayEnabled(toBoolean(ops.get("aiPersistenceCaseMaterializationReplayEnabled")))
                .aiPersistenceCaseMaterializationReplayBatchSize(toInteger(ops.get("aiPersistenceCaseMaterializationReplayBatchSize")))
                .aiPersistenceCaseMaterializationReplayFixedDelayMs(toLong(ops.get("aiPersistenceCaseMaterializationReplayFixedDelayMs")))
                .aiCaseMaterializationPendingCount(toInteger(ops.get("aiCaseMaterializationPendingCount")))
                .aiCaseMaterializationOldestAgeSeconds(toLong(ops.get("aiCaseMaterializationOldestAgeSeconds")))
                .aiPersistenceControlPlaneRedisDrainCompleted(toBoolean(ops.get("aiPersistenceControlPlaneRedisDrainCompleted")))
                .aiPersistenceBusinessLiveFlowReportExists(toBoolean(ops.get("aiPersistenceBusinessLiveFlowReportExists")))
                .aiPersistenceBusinessLiveFlowReportPath(toStringValue(ops.get("aiPersistenceBusinessLiveFlowReportPath")))
                .aiPersistenceBusinessLiveFlowVerifiedAt(toLong(ops.get("aiPersistenceBusinessLiveFlowVerifiedAt")))
                .aiPersistenceBusinessLiveFlowScene(toStringValue(ops.get("aiPersistenceBusinessLiveFlowScene")))
                .aiPersistenceBusinessLiveFlowSuccess(toBoolean(ops.get("aiPersistenceBusinessLiveFlowSuccess")))
                .aiPersistenceBusinessLiveFlowDeviceId(toStringValue(ops.get("aiPersistenceBusinessLiveFlowDeviceId")))
                .aiPersistenceBusinessLiveFlowGlobalDeviceId(toStringValue(ops.get("aiPersistenceBusinessLiveFlowGlobalDeviceId")))
                .aiPersistenceBusinessLiveFlowAuthIdentity(toStringValue(ops.get("aiPersistenceBusinessLiveFlowAuthIdentity")))
                .aiPersistenceBusinessLiveFlowDeviceSn(toStringValue(ops.get("aiPersistenceBusinessLiveFlowDeviceSn")))
                .aiPersistenceBusinessLiveFlowEventId(toStringValue(ops.get("aiPersistenceBusinessLiveFlowEventId")))
                .aiPersistenceBusinessLiveFlowDiagnosisId(toStringValue(ops.get("aiPersistenceBusinessLiveFlowDiagnosisId")))
                .aiPersistenceBusinessLiveFlowFeedbackId(toStringValue(ops.get("aiPersistenceBusinessLiveFlowFeedbackId")))
                .aiPersistenceBusinessLiveFlowCaseId(toStringValue(ops.get("aiPersistenceBusinessLiveFlowCaseId")))
                .aiPersistenceBusinessLiveFlowMysqlWriteOutboxCount(toInteger(ops.get("aiPersistenceBusinessLiveFlowMysqlWriteOutboxCount")))
                .aiPersistenceBusinessLiveFlowCaseMaterializationTaskCount(toInteger(ops.get("aiPersistenceBusinessLiveFlowCaseMaterializationTaskCount")))
                .aiPersistenceBusinessLiveFlowDiagnosisMirrorExists(toBoolean(ops.get("aiPersistenceBusinessLiveFlowDiagnosisMirrorExists")))
                .aiPersistenceBusinessLiveFlowFeedbackMirrorExists(toBoolean(ops.get("aiPersistenceBusinessLiveFlowFeedbackMirrorExists")))
                .aiPersistenceBusinessLiveFlowCaseMirrorExists(toBoolean(ops.get("aiPersistenceBusinessLiveFlowCaseMirrorExists")))
                .aiPersistenceControlPlaneLastDrainAt(toLong(ops.get("aiPersistenceControlPlaneLastDrainAt")))
                .aiPersistenceControlPlaneLastDrainOperator(toStringValue(ops.get("aiPersistenceControlPlaneLastDrainOperator")))
                .aiPersistenceControlPlaneLastDrainDryRun(toBoolean(ops.get("aiPersistenceControlPlaneLastDrainDryRun")))
                .aiPersistenceControlPlaneLastDrainAccepted(toBoolean(ops.get("aiPersistenceControlPlaneLastDrainAccepted")))
                .aiPersistenceControlPlaneLastDrainMessage(toStringValue(ops.get("aiPersistenceControlPlaneLastDrainMessage")))
                .aiPersistenceControlPlaneDrainReportExists(toBoolean(ops.get("aiPersistenceControlPlaneDrainReportExists")))
                .aiPersistenceControlPlaneDrainReportPath(toStringValue(ops.get("aiPersistenceControlPlaneDrainReportPath")))
                .aiPersistenceControlPlaneDrainReportSource(toStringValue(ops.get("aiPersistenceControlPlaneDrainReportSource")))
                .aiPersistenceControlPlaneDrainReportExecutedAt(toLong(ops.get("aiPersistenceControlPlaneDrainReportExecutedAt")))
                .aiPersistenceControlPlaneDrainReportOperator(toStringValue(ops.get("aiPersistenceControlPlaneDrainReportOperator")))
                .aiPersistenceControlPlaneDrainReportDryRun(toBoolean(ops.get("aiPersistenceControlPlaneDrainReportDryRun")))
                .aiPersistenceControlPlaneDrainReportAccepted(toBoolean(ops.get("aiPersistenceControlPlaneDrainReportAccepted")))
                .aiPersistenceControlPlaneDrainReportBatchSize(toInteger(ops.get("aiPersistenceControlPlaneDrainReportBatchSize")))
                .aiPersistenceControlPlaneDrainReportRequestedStores(toStringValue(ops.get("aiPersistenceControlPlaneDrainReportRequestedStores")))
                .aiPersistenceControlPlaneDrainReportMessage(toStringValue(ops.get("aiPersistenceControlPlaneDrainReportMessage")))
                .aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending(toInteger(ops.get("aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending")))
                .aiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten(toInteger(ops.get("aiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten")))
                .aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining(toInteger(ops.get("aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining")))
                .aiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending(toInteger(ops.get("aiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending")))
                .aiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten(toInteger(ops.get("aiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten")))
                .aiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining(toInteger(ops.get("aiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining")))
                .build();
    }

    private AdminLatestClosureResp buildLatestClosure(AdminConsoleAggregateData aggregateData) {
        return AdminLatestClosureResp.builder()
                .selectedHomeId(aggregateData.selectedHomeId())
                .homes(aggregateData.homes())
                .members(aggregateData.members())
                .products(aggregateData.products())
                .devices(aggregateData.devices())
                .otaTasks(aggregateData.otaTasks())
                .opsOverview(aggregateData.opsOverview())
                .aiEvalReports(aggregateData.aiEvalReports())
                .build();
    }

    private List<AdminClosureStageResp> buildClosureStages(AdminConsoleAggregateData aggregateData,
                                                           AdminConsoleOverviewResp overview) {
        Map<String, Object> selectedHome = findHomeById(aggregateData.homes(), aggregateData.selectedHomeId());
        String homeName = selectedHome == null ? aggregateData.selectedHomeId() : String.valueOf(
                firstNonNull(selectedHome.get("name"), selectedHome.get("homeName"), aggregateData.selectedHomeId())
        );
        List<AdminClosureStageResp> stages = new ArrayList<>();
        stages.add(buildRequiredStage(
                "HOME_BINDING",
                "家庭绑定",
                !aggregateData.homes().isEmpty(),
                "已选家庭: " + homeName,
                "先完成用户与家庭绑定，后台聚合才有业务视角"
        ));
        stages.add(buildRequiredStage(
                "MEMBER_COLLABORATION",
                "成员协同",
                !aggregateData.members().isEmpty(),
                "家庭成员数: " + aggregateData.members().size(),
                "至少需要一名成员参与家庭协同与权限流转"
        ));
        stages.add(buildRequiredStage(
                "PRODUCT_MODEL",
                "产品建模",
                !aggregateData.products().isEmpty(),
                "产品数: " + aggregateData.products().size(),
                "先补齐产品、物模型和固件基线，再接入设备"
        ));
        stages.add(buildRequiredStage(
                "DEVICE_ACCESS",
                "设备接入",
                !aggregateData.devices().isEmpty(),
                "设备数: " + aggregateData.devices().size(),
                "需要有设备台账与在线状态，才能进入运维闭环"
        ));
        stages.add(buildOptionalStage(
                "OTA_OPERATION",
                "OTA运营",
                !aggregateData.otaTasks().isEmpty(),
                "OTA任务数: " + aggregateData.otaTasks().size(),
                "当前无 OTA 任务，后续可按版本治理需要补充"
        ));
        stages.add(buildRequiredStage(
                "OPS_CLOSURE",
                "告警工单",
                hasOpsSignal(overview),
                "今日告警: " + overview.getTodayAlarmCount() + ", 待处理工单: " + overview.getPendingWorkOrderCount(),
                "需要把规则触发、工单处理、SLA 回写纳入统一运营入口"
        ));
        stages.add(buildRequiredStage(
                "AI_GUARDRAIL",
                "AI门禁",
                safeInt(overview.getAiEvalReadyCount()) > 0,
                "AI报告就绪: " + overview.getAiEvalReadyCount() + ", 通过: " + overview.getAiEvalPassedCount(),
                "至少保持一套可复跑的 AI 评测门禁，避免后台运营链路失真"
        ));
        stages.add(buildRequiredStage(
                "PERSISTENCE_GOVERNANCE",
                "持久化治理",
                Boolean.TRUE.equals(overview.getAiPersistenceMysqlCutoverReady()),
                buildPersistenceSummary(overview),
                resolvePersistenceHint(overview)
        ));
        return stages;
    }

    private AdminClosureStageResp buildRequiredStage(String key,
                                                     String name,
                                                     boolean ready,
                                                     String summary,
                                                     String actionHint) {
        return AdminClosureStageResp.builder()
                .stageKey(key)
                .stageName(name)
                .status(ready ? "READY" : "ATTENTION")
                .summary(summary)
                .actionHint(actionHint)
                .build();
    }

    private AdminClosureStageResp buildOptionalStage(String key,
                                                     String name,
                                                     boolean ready,
                                                     String summary,
                                                     String actionHint) {
        return AdminClosureStageResp.builder()
                .stageKey(key)
                .stageName(name)
                .status(ready ? "READY" : "OPTIONAL")
                .summary(summary)
                .actionHint(actionHint)
                .build();
    }

    private Integer calculateClosureScore(List<AdminClosureStageResp> stages) {
        int required = 0;
        int ready = 0;
        for (AdminClosureStageResp stage : stages) {
            if (!"OPTIONAL".equals(stage.getStatus())) {
                required++;
                if ("READY".equals(stage.getStatus())) {
                    ready++;
                }
            }
        }
        if (required == 0) {
            return 0;
        }
        return (int) Math.round(ready * 100D / required);
    }

    private boolean hasOpsSignal(AdminConsoleOverviewResp overview) {
        return safeInt(overview.getTodayAlarmCount()) > 0
                || safeInt(overview.getPendingWorkOrderCount()) > 0
                || safeDouble(overview.getOneTimeResolveRate()) > 0D;
    }

    private String buildPersistenceSummary(AdminConsoleOverviewResp overview) {
        return "生效模式: " + defaultText(overview.getAiPersistenceReadMode())
                + ", 配置模式: " + defaultText(overview.getAiPersistenceConfiguredReadMode())
                + ", 切读就绪: " + booleanText(overview.getAiPersistenceMysqlCutoverReady());
    }

    private String resolvePersistenceHint(AdminConsoleOverviewResp overview) {
        if (StringUtils.hasText(overview.getAiPersistenceMysqlCutoverBlockReason())) {
            return overview.getAiPersistenceMysqlCutoverBlockReason();
        }
        return "需要同时满足一致性、约束、迁移门禁后再切到 MySQL 主读";
    }

    private Map<String, Object> findHomeById(List<Map<String, Object>> homes, String homeId) {
        for (Map<String, Object> home : homes) {
            Object id = home.get("id");
            if (id != null && homeId.equals(String.valueOf(id))) {
                return home;
            }
        }
        return null;
    }

    private String pickHomeId(String requestedHomeId, List<Map<String, Object>> homes) {
        if (StringUtils.hasText(requestedHomeId)) {
            return requestedHomeId;
        }
        if (homes == null || homes.isEmpty()) {
            // 运营账号可能未绑定家庭：降级为空数据，而非让后台聚合链路 400
            return null;
        }
        Object id = homes.get(0).get("id");
        if (id == null || !StringUtils.hasText(String.valueOf(id))) {
            throw new BusinessException(ResultCode.FAILED, "家庭数据异常，缺少 homeId");
        }
        return String.valueOf(id);
    }

    private DevicePageResp emptyDevicePage(AdminDevicePageReq req) {
        DevicePageResp resp = new DevicePageResp();
        resp.setTotal(0L);
        resp.setPageNo(req.getPageNo());
        resp.setPageSize(req.getPageSize());
        resp.setRecords(List.of());
        return resp;
    }

    private OtaTaskPageResp emptyOtaTaskPage(AdminOtaTaskPageReq req) {
        OtaTaskPageResp resp = new OtaTaskPageResp();
        resp.setTotal(0L);
        resp.setPageNo(req.getPageNo());
        resp.setPageSize(req.getPageSize());
        resp.setRecords(List.of());
        return resp;
    }

    private void ensureAuthorizationHeader(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少有效的 Authorization Header");
        }
    }

    private void ensureAiPersistenceHistoryDetailQuery(String reportType, Long occurredAt) {
        if (!StringUtils.hasText(reportType)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "reportType 不能为空");
        }
        if (occurredAt == null || occurredAt <= 0) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "occurredAt 必须为正整数");
        }
    }

    private List<String> normalizeSceneTypes(List<String> sceneTypes) {
        if (sceneTypes == null || sceneTypes.isEmpty()) {
            return DEFAULT_AI_EVAL_SCENES;
        }
        return sceneTypes.stream()
                .filter(StringUtils::hasText)
                .flatMap(item -> Arrays.stream(item.split(",")))
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase())
                .distinct()
                .collect(Collectors.toList());
    }

    private Long resolveLatestGeneratedAt(List<AdminAiEvalReportResp> aiEvalReports) {
        Long latest = null;
        for (AdminAiEvalReportResp item : aiEvalReports) {
            Long generatedAt = item.getGeneratedAt();
            if (generatedAt == null) {
                continue;
            }
            if (latest == null || generatedAt > latest) {
                latest = generatedAt;
            }
        }
        return latest;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0D;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String booleanText(Boolean value) {
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private record AdminConsoleAggregateData(
            List<Map<String, Object>> homes,
            String selectedHomeId,
            List<Map<String, Object>> members,
            List<ProductResp> products,
            List<DeviceResp> devices,
            List<OtaUpgradeTaskResp> otaTasks,
            Map<String, Object> opsOverview,
            List<AdminAiEvalReportResp> aiEvalReports
    ) {
        private AdminConsoleAggregateData {
            homes = homes == null ? List.of() : List.copyOf(homes);
            members = members == null ? List.of() : List.copyOf(members);
            products = products == null ? List.of() : List.copyOf(products);
            devices = devices == null ? List.of() : List.copyOf(devices);
            otaTasks = otaTasks == null ? List.of() : List.copyOf(otaTasks);
            opsOverview = opsOverview == null ? Map.of() : new LinkedHashMap<>(opsOverview);
            aiEvalReports = aiEvalReports == null ? List.of() : List.copyOf(aiEvalReports);
        }
    }
}
