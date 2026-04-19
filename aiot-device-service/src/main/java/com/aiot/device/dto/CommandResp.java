package com.aiot.device.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
@Data
@AllArgsConstructor
public class CommandResp {
    private String messageId;
    private String status;
}
