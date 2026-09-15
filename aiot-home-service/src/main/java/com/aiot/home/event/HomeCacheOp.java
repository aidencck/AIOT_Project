package com.aiot.home.event;

/**
 * 家庭成员缓存操作类型，用于事务提交后异步更新 Redis 缓存。
 */
public enum HomeCacheOp {
    UPDATE_USER_ROLE,
    REMOVE_USER_ROLE,
    REMOVE_HOME_MEMBERS
}
