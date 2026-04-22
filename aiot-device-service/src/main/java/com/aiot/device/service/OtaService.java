package com.aiot.device.service;

import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaTaskPageReq;
import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface OtaService {
    String createFirmwarePackage(FirmwarePackageCreateReq req);

    List<FirmwarePackageResp> listFirmwarePackages(String productKey);

    String createUpgradeTask(OtaUpgradeTaskCreateReq req);

    OtaUpgradeTaskResp getUpgradeTask(String taskId);

    List<OtaUpgradeTaskResp> listUpgradeTasks(String homeId);

    IPage<OtaUpgradeTaskResp> pageUpgradeTasks(OtaTaskPageReq req);

    void reportUpgradeResult(String taskId, String deviceId, OtaUpgradeReportReq req);
}
