package com.aiot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "DevicePageResp", description = "设备分页响应")
public class DevicePageResp extends PageResp<DeviceResp> {

    public static DevicePageResp from(PageResp<DeviceResp> pageResp) {
        DevicePageResp resp = new DevicePageResp();
        resp.setTotal(pageResp.getTotal());
        resp.setPageNo(pageResp.getPageNo());
        resp.setPageSize(pageResp.getPageSize());
        resp.setRecords(pageResp.getRecords());
        return resp;
    }
}
