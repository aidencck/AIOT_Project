package com.aiot.home.utils;

import com.aiot.common.security.RequestUserContext;

/**
 * 当前登录用户信息上下文 (ThreadLocal)
 */
public class UserContext {

    public static void set(UserInfo userInfo) {
        if (userInfo == null) {
            RequestUserContext.remove();
            return;
        }
        RequestUserContext.set(new RequestUserContext.UserInfo(userInfo.getUserId(), userInfo.getPhone()));
    }

    public static UserInfo get() {
        RequestUserContext.UserInfo userInfo = RequestUserContext.get();
        if (userInfo == null) {
            return null;
        }
        return new UserInfo(userInfo.getUserId(), userInfo.getPhone());
    }

    public static void remove() {
        RequestUserContext.remove();
    }

    public static class UserInfo {
        private String userId;
        private String phone;

        public UserInfo(String userId, String phone) {
            this.userId = userId;
            this.phone = phone;
        }

        public String getUserId() { return userId; }
        public String getPhone() { return phone; }
    }
}
