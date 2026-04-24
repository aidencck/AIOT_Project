package com.aiot.home.service;

import com.aiot.home.dto.LoginReq;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.dto.RegisterReq;

public interface UserService {
    LoginResp login(LoginReq req);
    void register(RegisterReq req);
}
