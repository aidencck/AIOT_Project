package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FirmwarePackageCreateReq {

    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;

    @NotBlank(message = "version 不能为空")
    @Size(max = 64, message = "version 长度不能超过64")
    private String version;

    @NotBlank(message = "downloadUrl 不能为空")
    @Size(max = 255, message = "downloadUrl 长度不能超过255")
    private String downloadUrl;

    @Size(max = 128, message = "checksum 长度不能超过128")
    private String checksum;

    @Size(max = 255, message = "releaseNotes 长度不能超过255")
    private String releaseNotes;
}
