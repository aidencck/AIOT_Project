package com.aiot.rule.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SlaSchedulerService {

    private final OpsClosureService opsClosureService;

    @Value("${aiot.ops.sla-check.enabled:true}")
    private boolean slaCheckEnabled;

    public SlaSchedulerService(OpsClosureService opsClosureService) {
        this.opsClosureService = opsClosureService;
    }

    @Scheduled(fixedDelayString = "${aiot.ops.sla-check.fixed-delay-ms:60000}")
    public void scheduledCheck() {
        if (!slaCheckEnabled) {
            return;
        }
        Integer changed = opsClosureService.checkAndMarkSlaBreached();
        if (changed != null && changed > 0) {
            log.info("SLA scheduled check done, breachedCount={}", changed);
        }
    }
}
