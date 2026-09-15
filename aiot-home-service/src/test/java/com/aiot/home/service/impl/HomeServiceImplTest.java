package com.aiot.home.service.impl;

import com.aiot.home.entity.Home;
import com.aiot.home.entity.HomeMember;
import com.aiot.home.event.HomeCacheOp;
import com.aiot.home.event.HomeCacheUpdateEvent;
import com.aiot.home.event.HomeDeleteCompensationEvent;
import com.aiot.home.repository.HomeMemberRepository;
import com.aiot.home.repository.HomeRepository;
import com.aiot.home.repository.UserRepository;
import com.aiot.home.service.HomeDeleteCompensationTaskService;
import com.aiot.home.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceImplTest {

    @Mock
    private HomeRepository homeRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HomeDeleteCompensationTaskService compensationTaskService;

    @Mock
    private RoomService roomService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private HomeServiceImpl homeService;

    @Test
    void deleteHome_shouldCreateCompensationTaskAndPublishEvents() {
        Home home = new Home();
        home.setId("home-1");
        when(homeRepository.selectById("home-1")).thenReturn(home);
        when(compensationTaskService.createPendingTask("home-1")).thenReturn("task-1");

        homeService.deleteHome("home-1", "user-1");

        verify(roomService).deleteRoomsByHomeId("home-1");
        verify(homeRepository).deleteById("home-1");
        verify(homeMemberRepository).delete(any());
        verify(compensationTaskService).createPendingTask("home-1");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        List<Object> events = captor.getAllValues();

        HomeDeleteCompensationEvent compensationEvent = events.stream()
                .filter(HomeDeleteCompensationEvent.class::isInstance)
                .map(HomeDeleteCompensationEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("task-1", compensationEvent.taskId());

        boolean cacheEventPublished = events.stream()
                .filter(HomeCacheUpdateEvent.class::isInstance)
                .map(HomeCacheUpdateEvent.class::cast)
                .anyMatch(e -> e.op() == HomeCacheOp.REMOVE_HOME_MEMBERS && "home-1".equals(e.homeId()));
        assertTrue(cacheEventPublished, "expected REMOVE_HOME_MEMBERS cache event");
    }
}
