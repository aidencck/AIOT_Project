package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.rule.client.AiContextProvider;
import com.aiot.rule.dto.AiBusinessLiveFlowReportSummary;
import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.WorkOrderRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class DeviceIdentityCompatService {

    private static final String DEFAULT_SCENE_TYPE = "OFFLINE_FLAP";

    private final AiContextProvider aiContextProvider;

    public DeviceIdentityCompatService(AiContextProvider aiContextProvider) {
        this.aiContextProvider = aiContextProvider;
    }

    public AlarmRecord enrichAlarmRecord(AlarmRecord alarm) {
        if (alarm == null || hasFullIdentity(alarm.getGlobalDeviceId(), alarm.getAuthIdentity(), alarm.getDeviceSn())) {
            return alarm;
        }
        DeviceIdentitySnapshot snapshot = resolveSnapshot(
                alarm.getDeviceId(),
                alarm.getGlobalDeviceId(),
                alarm.getAuthIdentity(),
                alarm.getDeviceSn()
        );
        alarm.setGlobalDeviceId(snapshot.globalDeviceId());
        alarm.setAuthIdentity(snapshot.authIdentity());
        alarm.setDeviceSn(snapshot.deviceSn());
        return alarm;
    }

    public WorkOrderRecord enrichWorkOrderRecord(WorkOrderRecord workOrder) {
        if (workOrder == null || hasFullIdentity(workOrder.getGlobalDeviceId(), workOrder.getAuthIdentity(), workOrder.getDeviceSn())) {
            return workOrder;
        }
        DeviceIdentitySnapshot snapshot = resolveSnapshot(
                workOrder.getDeviceId(),
                workOrder.getGlobalDeviceId(),
                workOrder.getAuthIdentity(),
                workOrder.getDeviceSn()
        );
        workOrder.setGlobalDeviceId(snapshot.globalDeviceId());
        workOrder.setAuthIdentity(snapshot.authIdentity());
        workOrder.setDeviceSn(snapshot.deviceSn());
        return workOrder;
    }

    public AiBusinessLiveFlowReportSummary enrichBusinessLiveFlow(AiBusinessLiveFlowReportSummary report) {
        if (report == null) {
            return null;
        }
        DeviceIdentitySnapshot snapshot = resolveSnapshot(
                report.getDeviceId(),
                report.getGlobalDeviceId(),
                report.getAuthIdentity(),
                report.getDeviceSn()
        );
        report.setGlobalDeviceId(snapshot.globalDeviceId());
        report.setAuthIdentity(snapshot.authIdentity());
        report.setDeviceSn(snapshot.deviceSn());
        return report;
    }

    public boolean matches(String keyword,
                           String deviceId,
                           String globalDeviceId,
                           String authIdentity,
                           String deviceSn) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim();
        return equalsIgnoreCase(normalized, globalDeviceId)
                || equalsIgnoreCase(normalized, authIdentity)
                || equalsIgnoreCase(normalized, deviceSn)
                || equalsIgnoreCase(normalized, deviceId);
    }

    public DeviceIdentitySnapshot resolveSnapshot(String fallbackDeviceId,
                                                  String existingGlobalDeviceId,
                                                  String existingAuthIdentity,
                                                  String existingDeviceSn) {
        String normalizedDeviceId = normalize(fallbackDeviceId);
        String globalDeviceId = normalize(existingGlobalDeviceId);
        String authIdentity = normalize(existingAuthIdentity);
        String deviceSn = normalize(existingDeviceSn);
        if (hasFullIdentity(globalDeviceId, authIdentity, deviceSn)) {
            return finalizeSnapshot(normalizedDeviceId, globalDeviceId, authIdentity, deviceSn);
        }
        if (!StringUtils.hasText(normalizedDeviceId)) {
            return finalizeSnapshot(normalizedDeviceId, globalDeviceId, authIdentity, deviceSn);
        }
        try {
            AiRuntimeContextPayload payload = aiContextProvider.getRuntimeContext(normalizedDeviceId, DEFAULT_SCENE_TYPE);
            AiRuntimeContext context = payload == null ? null : payload.getRuntimeContext();
            if (context != null) {
                globalDeviceId = firstNonBlank(globalDeviceId, normalize(context.getGlobalDeviceId()), normalize(context.getDeviceId()));
                authIdentity = firstNonBlank(authIdentity, normalize(context.getAuthIdentity()));
                deviceSn = firstNonBlank(deviceSn, normalize(context.getDeviceSn()));
            }
        } catch (Exception ex) {
            log.warn("Resolve device identity snapshot failed, deviceId={}", normalizedDeviceId, ex);
        }
        return finalizeSnapshot(normalizedDeviceId, globalDeviceId, authIdentity, deviceSn);
    }

    private DeviceIdentitySnapshot finalizeSnapshot(String deviceId,
                                                    String globalDeviceId,
                                                    String authIdentity,
                                                    String deviceSn) {
        String resolvedGlobalDeviceId = firstNonBlank(globalDeviceId, deviceId);
        String resolvedAuthIdentity = firstNonBlank(authIdentity, deviceSn);
        return new DeviceIdentitySnapshot(
                deviceId,
                resolvedGlobalDeviceId,
                resolvedAuthIdentity,
                normalize(deviceSn)
        );
    }

    private boolean hasFullIdentity(String globalDeviceId, String authIdentity, String deviceSn) {
        return StringUtils.hasText(globalDeviceId)
                && StringUtils.hasText(authIdentity)
                && StringUtils.hasText(deviceSn);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right.trim());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record DeviceIdentitySnapshot(String deviceId,
                                         String globalDeviceId,
                                         String authIdentity,
                                         String deviceSn) {
    }
}
