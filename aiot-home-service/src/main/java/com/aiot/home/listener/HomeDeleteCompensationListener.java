package com.aiot.home.listener;

import com.aiot.home.event.HomeDeleteCompensationEvent;
import com.aiot.home.service.HomeDeleteCompensationTaskService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HomeDeleteCompensationListener {

    private final HomeDeleteCompensationTaskService taskService;

    public HomeDeleteCompensationListener(HomeDeleteCompensationTaskService taskService) {
        this.taskService = taskService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHomeDeleted(HomeDeleteCompensationEvent event) {
        taskService.dispatch(event.taskId());
    }
}
