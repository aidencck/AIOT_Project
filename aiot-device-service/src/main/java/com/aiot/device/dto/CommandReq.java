package com.aiot.device.dto;
import lombok.Data;
@Data
public class CommandReq {
    private String commandName;
    private Object params;
}
