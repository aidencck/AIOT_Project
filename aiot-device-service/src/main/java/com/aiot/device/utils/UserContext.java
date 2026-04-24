package com.aiot.device.utils;

/**
 * 当前登录用户信息上下文 (ThreadLocal)
 */
public class UserContext {
    private static final ThreadLocal<UserInfo> USER_THREAD_LOCAL = new ThreadLocal<>();

    public static void set(UserInfo userInfo) {
        USER_THREAD_LOCAL.set(userInfo);
    }

    public static UserInfo get() {
        return USER_THREAD_LOCAL.get();
    }

    public static void remove() {
        USER_THREAD_LOCAL.remove();
    }

    public static class UserInfo {
        private final String userId;
        private final String phone;

        public UserInfo(String userId, String phone) {
            this.userId = userId;
            this.phone = phone;
        }

        public String getUserId() {
            return userId;
        }

        public String getPhone() {
            return phone;
        }
    }
}
