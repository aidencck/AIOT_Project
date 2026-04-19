package com.aiot.auth.dto;
import lombok.Data;

@Data
public class EmqxWebhookReq {
    private String action;
    private String clientid;
    private String username;
    private Long timestamp;
}
