package com.aiot.device.dto;

import lombok.Data;

@Data
public class ProductResp {
    private String id;
    private String productKey;
    private String name;
    private String description;
    private Integer nodeType;
    private String thingModelJson;
}
