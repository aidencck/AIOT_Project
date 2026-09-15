package com.aiot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "OtaTaskPageResp", description = "OTA 任务分页响应")
public class OtaTaskPageResp extends PageResp<OtaUpgradeTaskResp> {

    public static OtaTaskPageResp from(PageResp<OtaUpgradeTaskResp> pageResp) {
        OtaTaskPageResp resp = new OtaTaskPageResp();
        resp.setTotal(pageResp.getTotal());
        resp.setPageNo(pageResp.getPageNo());
        resp.setPageSize(pageResp.getPageSize());
        resp.setRecords(pageResp.getRecords());
        return resp;
    }
}
