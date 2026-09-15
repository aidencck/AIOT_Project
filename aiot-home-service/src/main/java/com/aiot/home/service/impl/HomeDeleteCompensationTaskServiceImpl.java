package com.aiot.home.service.impl;

import com.aiot.home.entity.HomeDeleteCompensationTask;
import com.aiot.home.repository.HomeDeleteCompensationTaskRepository;
import com.aiot.home.service.HomeDeleteCompensationTaskService;
import com.aiot.home.service.HomeDeviceCompensationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class HomeDeleteCompensationTaskServiceImpl implements HomeDeleteCompensationTaskService {

    static final int STATUS_PENDING = 1;
    static final int STATUS_PROCESSING = 2;
    static final int STATUS_SUCCESS = 3;
    static final int STATUS_FAILED = 4;
    static final int STATUS_DEAD = 5;

    private final HomeDeleteCompensationTaskRepository taskRepository;
    private final HomeDeviceCompensationService compensationService;

    @Value("${aiot.compensation.home-delete.retry-backoff-seconds:30}")
    private long retryBackoffSeconds;

    @Value("${aiot.compensation.home-delete.max-retry-count:10}")
    private int maxRetryCount;

    @Value("${aiot.compensation.home-delete.processing-timeout-seconds:60}")
    private long processingTimeoutSeconds;

    public HomeDeleteCompensationTaskServiceImpl(HomeDeleteCompensationTaskRepository taskRepository,
                                                 HomeDeviceCompensationService compensationService) {
        this.taskRepository = taskRepository;
        this.compensationService = compensationService;
    }

    @Override
    public String createPendingTask(String homeId) {
        return createPendingTask("HOME", homeId, homeId);
    }

    @Override
    public String createPendingRoomTask(String roomId, String homeId) {
        return createPendingTask("ROOM", roomId, homeId);
    }

    private String createPendingTask(String targetType, String targetId, String homeId) {
        HomeDeleteCompensationTask task = new HomeDeleteCompensationTask();
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setHomeId(homeId);
        task.setStatus(STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryTime(LocalDateTime.now());
        task.setTraceId(MDC.get("traceId"));
        try {
            taskRepository.insert(task);
            return task.getId();
        } catch (DuplicateKeyException ex) {
            LambdaQueryWrapper<HomeDeleteCompensationTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(HomeDeleteCompensationTask::getTargetType, targetType)
                    .eq(HomeDeleteCompensationTask::getTargetId, targetId);
            HomeDeleteCompensationTask existed = taskRepository.selectOne(wrapper);
            return existed == null ? null : existed.getId();
        }
    }

    @Override
    public void dispatch(String taskId) {
        if (!claimTask(taskId)) {
            return;
        }
        HomeDeleteCompensationTask task = taskRepository.selectById(taskId);
        if (task == null) {
            return;
        }
        try {
            if ("ROOM".equals(task.getTargetType())) {
                compensationService.unbindDevicesByRoomId(task.getTargetId());
            } else {
                compensationService.unbindDevicesByHomeId(task.getHomeId());
            }
            markSuccess(taskId);
        } catch (Exception ex) {
            log.warn("Home delete compensation failed, taskId={}, targetType={}, targetId={}", taskId, task.getTargetType(), task.getTargetId(), ex);
            markFailed(task, ex);
        }
    }

    @Override
    public void retryDueTasks(int batchSize) {
        LambdaQueryWrapper<HomeDeleteCompensationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(HomeDeleteCompensationTask::getStatus, STATUS_PENDING, STATUS_FAILED, STATUS_PROCESSING)
                .le(HomeDeleteCompensationTask::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(HomeDeleteCompensationTask::getNextRetryTime)
                .last("limit " + Math.max(batchSize, 1));
        List<HomeDeleteCompensationTask> tasks = taskRepository.selectList(wrapper);
        for (HomeDeleteCompensationTask task : tasks) {
            dispatch(task.getId());
        }
    }

    private boolean claimTask(String taskId) {
        LambdaUpdateWrapper<HomeDeleteCompensationTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HomeDeleteCompensationTask::getId, taskId)
                .in(HomeDeleteCompensationTask::getStatus, STATUS_PENDING, STATUS_FAILED, STATUS_PROCESSING)
                .le(HomeDeleteCompensationTask::getNextRetryTime, LocalDateTime.now())
                .set(HomeDeleteCompensationTask::getStatus, STATUS_PROCESSING)
                .set(HomeDeleteCompensationTask::getNextRetryTime, LocalDateTime.now().plusSeconds(processingTimeoutSeconds))
                .set(HomeDeleteCompensationTask::getLastError, null);
        return taskRepository.update(null, wrapper) == 1;
    }

    private void markSuccess(String taskId) {
        LambdaUpdateWrapper<HomeDeleteCompensationTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HomeDeleteCompensationTask::getId, taskId)
                .set(HomeDeleteCompensationTask::getStatus, STATUS_SUCCESS)
                .set(HomeDeleteCompensationTask::getLastError, null);
        taskRepository.update(null, wrapper);
    }

    private void markFailed(HomeDeleteCompensationTask task, Exception ex) {
        HomeDeleteCompensationTask latest = taskRepository.selectById(task.getId());
        int retryCount = latest == null || latest.getRetryCount() == null ? 0 : latest.getRetryCount();
        int nextRetryCount = retryCount + 1;
        LambdaUpdateWrapper<HomeDeleteCompensationTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HomeDeleteCompensationTask::getId, task.getId())
                .set(HomeDeleteCompensationTask::getRetryCount, nextRetryCount)
                .set(HomeDeleteCompensationTask::getLastError, abbreviate(ex.getMessage()));
        if (nextRetryCount > maxRetryCount) {
            wrapper.set(HomeDeleteCompensationTask::getStatus, STATUS_DEAD);
        } else {
            wrapper.set(HomeDeleteCompensationTask::getStatus, STATUS_FAILED)
                    .set(HomeDeleteCompensationTask::getNextRetryTime, LocalDateTime.now().plusSeconds(retryBackoffSeconds));
        }
        taskRepository.update(null, wrapper);
    }

    private String abbreviate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 255 ? message.substring(0, 255) : message;
    }
}
