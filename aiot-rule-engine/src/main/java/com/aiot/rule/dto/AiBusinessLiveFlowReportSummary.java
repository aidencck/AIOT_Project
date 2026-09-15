package com.aiot.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBusinessLiveFlowReportSummary {
    private Boolean exists;
    private String path;
    private Long verifiedAt;
    private String scene;
    private Boolean success;
    private String deviceId;
    private String globalDeviceId;
    private String authIdentity;
    private String deviceSn;
    private String eventId;
    private String diagnosisId;
    private String feedbackId;
    private String caseId;
    private Integer mysqlWriteOutboxCount;
    private Integer caseMaterializationTaskCount;
    private Boolean diagnosisMirrorExists;
    private Boolean feedbackMirrorExists;
    private Boolean caseMirrorExists;
}
