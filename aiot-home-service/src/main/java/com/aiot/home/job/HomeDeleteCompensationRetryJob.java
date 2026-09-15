package com.aiot.home.job;

import com.aiot.home.service.HomeDeleteCompensationTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HomeDeleteCompensationRetryJob {

    private final HomeDeleteCompensationTaskService taskService;

    @Value("${aiot.compensation.home-delete.batch-size:20}")
    private int batchSize;

    public HomeDeleteCompensationRetryJob(HomeDeleteCompensationTaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${aiot.compensation.home-delete.scan-interval-ms:10000}")
    public void retry() {
        taskService.retryDueTasks(batchSize);
    }
}
