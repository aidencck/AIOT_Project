package com.aiot.device.dto;

import lombok.Data;

@Data
public class FirmwarePackageResp {
    private String packageId;
    private String productKey;
    private String version;
    private String downloadUrl;
    private String checksum;
    private String releaseNotes;
    private Integer status;
}
