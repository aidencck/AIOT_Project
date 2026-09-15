package com.aiot.device.service;

import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaTaskPageReq;
import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;

import java.util.List;

public interface OtaService {
    String createFirmwarePackage(FirmwarePackageCreateReq req);

    List<FirmwarePackageResp> listFirmwarePackages(String productKey);

    void deleteFirmwarePackage(String packageId);

    String createUpgradeTask(OtaUpgradeTaskCreateReq req);

    OtaUpgradeTaskResp getUpgradeTask(String taskId);

    List<OtaUpgradeTaskResp> listUpgradeTasks(String homeId);

    void cancelUpgradeTask(String taskId);

    OtaTaskPageResp pageUpgradeTasks(OtaTaskPageReq req);

    void reportUpgradeResult(String taskId, String deviceId, OtaUpgradeReportReq req);
}
