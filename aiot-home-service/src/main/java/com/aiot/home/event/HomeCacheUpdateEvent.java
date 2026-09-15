package com.aiot.home.event;

/**
 * 家庭缓存更新事件，在事务提交后（AFTER_COMMIT）由监听器消费，避免事务内写 Redis。
 */
public record HomeCacheUpdateEvent(
        HomeCacheOp op,
        String homeId,
        String userId,
        Integer role) {
}
