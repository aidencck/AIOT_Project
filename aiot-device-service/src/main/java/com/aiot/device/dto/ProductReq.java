package com.aiot.device.dto;
import lombok.Data;
@Data
public class ProductReq {
    private String name;
    private String description;
    private String nodeType;
    private String thingModelJson;
}
