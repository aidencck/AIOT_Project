package com.aiot.device.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminPageResp<T> {
    private Long total;
    private Integer pageNo;
    private Integer pageSize;
    private List<T> records;
}
