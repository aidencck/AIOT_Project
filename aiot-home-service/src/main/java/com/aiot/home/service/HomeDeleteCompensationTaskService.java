package com.aiot.home.service;

public interface HomeDeleteCompensationTaskService {

    String createPendingTask(String homeId);

    String createPendingRoomTask(String roomId, String homeId);

    void dispatch(String taskId);

    void retryDueTasks(int batchSize);
}
