package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBusinessLiveFlowSnapshot {
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
    private List<AiPersistenceHistoryEntry> recentHistory;
}
