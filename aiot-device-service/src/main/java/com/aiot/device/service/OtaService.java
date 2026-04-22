package com.aiot.device.service;

import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;

import java.util.List;

public interface OtaService {
    String createFirmwarePackage(FirmwarePackageCreateReq req);

    List<FirmwarePackageResp> listFirmwarePackages(String productKey);

    String createUpgradeTask(OtaUpgradeTaskCreateReq req);

    OtaUpgradeTaskResp getUpgradeTask(String taskId);

    List<OtaUpgradeTaskResp> listUpgradeTasks(String homeId);

    void reportUpgradeResult(String taskId, String deviceId, OtaUpgradeReportReq req);
}
