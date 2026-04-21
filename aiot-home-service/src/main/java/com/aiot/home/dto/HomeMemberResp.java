package com.aiot.home.dto;

import lombok.Data;

@Data
public class HomeMemberResp {
    private String userId;
    private String nickname;
    private String phone;
    private Integer role;
}
