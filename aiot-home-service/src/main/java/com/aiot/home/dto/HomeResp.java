package com.aiot.home.dto;

import lombok.Data;

@Data
public class HomeResp {
    private String id;
    private String name;
    private String location;
    private Integer role; // 当前用户在该家庭中的角色
}
