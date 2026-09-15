package com.aiot.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAiBusinessLiveFlowResp {
    private Boolean reportExists;
    private String reportPath;
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
