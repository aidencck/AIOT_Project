package com.aiot.common.security;

/**
 * 网关透传用户上下文（ThreadLocal）
 */
public final class RequestUserContext {

    private static final ThreadLocal<UserInfo> USER_CONTEXT = new ThreadLocal<>();

    private RequestUserContext() {
    }

    public static void set(UserInfo userInfo) {
        USER_CONTEXT.set(userInfo);
    }

    public static UserInfo get() {
        return USER_CONTEXT.get();
    }

    public static void remove() {
        USER_CONTEXT.remove();
    }

    public static class UserInfo {
        private final String userId;
        private final String globalUserId;
        private final String phone;

        public UserInfo(String userId, String phone) {
            this(userId, userId, phone);
        }

        public UserInfo(String userId, String globalUserId, String phone) {
            this.userId = userId;
            this.globalUserId = globalUserId;
            this.phone = phone;
        }

        public String getUserId() {
            return userId;
        }

        public String getGlobalUserId() {
            return globalUserId == null || globalUserId.isBlank() ? userId : globalUserId;
        }

        public String getPhone() {
            return phone;
        }
    }
}
