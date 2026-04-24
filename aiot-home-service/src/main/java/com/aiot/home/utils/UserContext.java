package com.aiot.home.utils;

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
        private String userId;
        private String phone;
        private String authorization;

        public UserInfo(String userId, String phone, String authorization) {
            this.userId = userId;
            this.phone = phone;
            this.authorization = authorization;
        }

        public String getUserId() { return userId; }
        public String getPhone() { return phone; }
        public String getAuthorization() { return authorization; }
    }
}
