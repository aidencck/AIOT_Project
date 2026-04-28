package com.aiot.home.dto;

import lombok.Data;

@Data
public class LoginResp {
    private String token;
    private String userId;
    private String nickname;
}
