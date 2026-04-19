package com.aiot.auth.dto;
import lombok.Data;

@Data
public class EmqxAuthReq {
    private String clientid;
    private String username;
    private String password;
}
