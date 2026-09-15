package com.aiot.home.listener;

import com.aiot.home.event.HomeCacheUpdateEvent;
import com.aiot.home.service.HomeCacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HomeCacheUpdateListener {

    private final HomeCacheManager cacheManager;

    public HomeCacheUpdateListener(HomeCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCacheUpdate(HomeCacheUpdateEvent event) {
        switch (event.op()) {
            case UPDATE_USER_ROLE ->
                    cacheManager.updateUserRoleCache(event.homeId(), event.userId(), event.role());
            case REMOVE_USER_ROLE ->
                    cacheManager.removeUserRoleCache(event.homeId(), event.userId());
            case REMOVE_HOME_MEMBERS ->
                    cacheManager.removeHomeMembersCache(event.homeId());
        }
    }
}
