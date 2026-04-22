package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaTaskPageReq;
import com.aiot.device.dto.OtaUpgradeRecordResp;
import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.FirmwarePackage;
import com.aiot.device.entity.OtaUpgradeRecord;
import com.aiot.device.entity.OtaUpgradeTask;
import com.aiot.device.entity.Product;
import com.aiot.device.mapper.DeviceMapper;
import com.aiot.device.mapper.FirmwarePackageMapper;
import com.aiot.device.mapper.OtaUpgradeRecordMapper;
import com.aiot.device.mapper.OtaUpgradeTaskMapper;
import com.aiot.device.mapper.ProductMapper;
import com.aiot.device.service.OtaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OtaServiceImpl implements OtaService {

    private final FirmwarePackageMapper firmwarePackageMapper;
    private final OtaUpgradeTaskMapper otaUpgradeTaskMapper;
    private final OtaUpgradeRecordMapper otaUpgradeRecordMapper;
    private final DeviceMapper deviceMapper;
    private final ProductMapper productMapper;

    public OtaServiceImpl(FirmwarePackageMapper firmwarePackageMapper,
                          OtaUpgradeTaskMapper otaUpgradeTaskMapper,
                          OtaUpgradeRecordMapper otaUpgradeRecordMapper,
                          DeviceMapper deviceMapper,
                          ProductMapper productMapper) {
        this.firmwarePackageMapper = firmwarePackageMapper;
        this.otaUpgradeTaskMapper = otaUpgradeTaskMapper;
        this.otaUpgradeRecordMapper = otaUpgradeRecordMapper;
        this.deviceMapper = deviceMapper;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createFirmwarePackage(FirmwarePackageCreateReq req) {
        ensureProductExists(req.getProductKey());
        LambdaQueryWrapper<FirmwarePackage> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(FirmwarePackage::getProductKey, req.getProductKey())
                .eq(FirmwarePackage::getVersion, req.getVersion());
        if (firmwarePackageMapper.selectOne(existsWrapper) != null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "同产品版本固件包已存在");
        }

        FirmwarePackage firmwarePackage = new FirmwarePackage();
        firmwarePackage.setPackageId("FW_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        firmwarePackage.setProductKey(req.getProductKey());
        firmwarePackage.setVersion(req.getVersion());
        firmwarePackage.setDownloadUrl(req.getDownloadUrl());
        firmwarePackage.setChecksum(req.getChecksum());
        firmwarePackage.setReleaseNotes(req.getReleaseNotes());
        firmwarePackage.setStatus(1);
        firmwarePackageMapper.insert(firmwarePackage);
        return firmwarePackage.getPackageId();
    }

    @Override
    public List<FirmwarePackageResp> listFirmwarePackages(String productKey) {
        LambdaQueryWrapper<FirmwarePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(productKey), FirmwarePackage::getProductKey, productKey)
                .orderByDesc(FirmwarePackage::getCreateTime);
        return firmwarePackageMapper.selectList(wrapper).stream().map(this::toFirmwareResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUpgradeTask(OtaUpgradeTaskCreateReq req) {
        ensureProductExists(req.getProductKey());
        FirmwarePackage firmwarePackage = getFirmwarePackage(req.getPackageId());
        if (!req.getProductKey().equals(firmwarePackage.getProductKey())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "固件包与产品不匹配");
        }
        if (firmwarePackage.getStatus() == null || firmwarePackage.getStatus() != 1) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "固件包不可用");
        }

        List<Device> devices = validateAndLoadDevices(req.getDeviceIds(), req.getHomeId(), req.getProductKey());
        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setTaskId("OTA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        task.setHomeId(req.getHomeId());
        task.setProductKey(req.getProductKey());
        task.setPackageId(req.getPackageId());
        task.setTargetVersion(firmwarePackage.getVersion());
        task.setStatus(1);
        task.setTotalCount(devices.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        otaUpgradeTaskMapper.insert(task);

        for (Device device : devices) {
            OtaUpgradeRecord record = new OtaUpgradeRecord();
            record.setRecordId("OTAR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            record.setTaskId(task.getTaskId());
            record.setDeviceId(device.getId());
            record.setFromVersion(device.getFirmwareVersion());
            record.setToVersion(firmwarePackage.getVersion());
            record.setStatus(1);
            otaUpgradeRecordMapper.insert(record);
        }
        return task.getTaskId();
    }

    @Override
    public OtaUpgradeTaskResp getUpgradeTask(String taskId) {
        OtaUpgradeTask task = getTask(taskId);
        return toTaskResp(task, true);
    }

    @Override
    public List<OtaUpgradeTaskResp> listUpgradeTasks(String homeId) {
        LambdaQueryWrapper<OtaUpgradeTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(homeId), OtaUpgradeTask::getHomeId, homeId)
                .orderByDesc(OtaUpgradeTask::getCreateTime);
        return otaUpgradeTaskMapper.selectList(wrapper).stream()
                .map(task -> toTaskResp(task, false))
                .collect(Collectors.toList());
    }

    @Override
    public IPage<OtaUpgradeTaskResp> pageUpgradeTasks(OtaTaskPageReq req) {
        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 20 : req.getPageSize();
        pageSize = Math.min(pageSize, 200);

        Page<OtaUpgradeTask> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<OtaUpgradeTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OtaUpgradeTask::getHomeId, req.getHomeId())
                .eq(StringUtils.hasText(req.getProductKey()), OtaUpgradeTask::getProductKey, req.getProductKey())
                .eq(req.getStatus() != null, OtaUpgradeTask::getStatus, req.getStatus())
                .orderByDesc(OtaUpgradeTask::getCreateTime);
        IPage<OtaUpgradeTask> taskPage = otaUpgradeTaskMapper.selectPage(page, wrapper);
        return taskPage.convert(task -> toTaskResp(task, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportUpgradeResult(String taskId, String deviceId, OtaUpgradeReportReq req) {
        OtaUpgradeTask task = getTask(taskId);
        LambdaQueryWrapper<OtaUpgradeRecord> rw = new LambdaQueryWrapper<>();
        rw.eq(OtaUpgradeRecord::getTaskId, taskId)
                .eq(OtaUpgradeRecord::getDeviceId, deviceId);
        OtaUpgradeRecord record = otaUpgradeRecordMapper.selectOne(rw);
        if (record == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级记录不存在");
        }
        if (record.getStatus() != null && record.getStatus() != 1) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级记录已完成上报");
        }

        record.setFromVersion(req.getFromVersion());
        record.setToVersion(req.getToVersion());
        record.setStatus(req.getStatus());
        record.setErrorMessage(req.getErrorMessage());
        record.setReportTime(LocalDateTime.now());
        otaUpgradeRecordMapper.updateById(record);

        if (req.getStatus() != null && req.getStatus() == 2) {
            Device device = deviceMapper.selectById(deviceId);
            if (device != null) {
                device.setFirmwareVersion(req.getToVersion());
                deviceMapper.updateById(device);
            }
        }
        refreshTaskStatistics(task);
    }

    private void refreshTaskStatistics(OtaUpgradeTask task) {
        LambdaQueryWrapper<OtaUpgradeRecord> rw = new LambdaQueryWrapper<>();
        rw.eq(OtaUpgradeRecord::getTaskId, task.getTaskId());
        List<OtaUpgradeRecord> records = otaUpgradeRecordMapper.selectList(rw);
        int success = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 2).count();
        int failed = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 3).count();
        int pending = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 1).count();
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setStatus(pending == 0 ? 2 : 1);
        otaUpgradeTaskMapper.updateById(task);
    }

    private OtaUpgradeTask getTask(String taskId) {
        LambdaQueryWrapper<OtaUpgradeTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OtaUpgradeTask::getTaskId, taskId);
        OtaUpgradeTask task = otaUpgradeTaskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级任务不存在");
        }
        return task;
    }

    private OtaUpgradeTaskResp toTaskResp(OtaUpgradeTask task, boolean withRecords) {
        OtaUpgradeTaskResp resp = new OtaUpgradeTaskResp();
        resp.setTaskId(task.getTaskId());
        resp.setHomeId(task.getHomeId());
        resp.setProductKey(task.getProductKey());
        resp.setPackageId(task.getPackageId());
        resp.setTargetVersion(task.getTargetVersion());
        resp.setStatus(task.getStatus());
        resp.setTotalCount(task.getTotalCount());
        resp.setSuccessCount(task.getSuccessCount());
        resp.setFailedCount(task.getFailedCount());
        resp.setCreateTime(task.getCreateTime());

        if (withRecords) {
            LambdaQueryWrapper<OtaUpgradeRecord> rw = new LambdaQueryWrapper<>();
            rw.eq(OtaUpgradeRecord::getTaskId, task.getTaskId())
                    .orderByAsc(OtaUpgradeRecord::getCreateTime);
            List<OtaUpgradeRecordResp> recordResps = otaUpgradeRecordMapper.selectList(rw).stream()
                    .map(this::toRecordResp)
                    .collect(Collectors.toList());
            resp.setRecords(recordResps);
        } else {
            resp.setRecords(new ArrayList<>());
        }
        return resp;
    }

    private OtaUpgradeRecordResp toRecordResp(OtaUpgradeRecord record) {
        OtaUpgradeRecordResp resp = new OtaUpgradeRecordResp();
        resp.setRecordId(record.getRecordId());
        resp.setDeviceId(record.getDeviceId());
        resp.setFromVersion(record.getFromVersion());
        resp.setToVersion(record.getToVersion());
        resp.setStatus(record.getStatus());
        resp.setErrorMessage(record.getErrorMessage());
        resp.setReportTime(record.getReportTime());
        return resp;
    }

    private FirmwarePackageResp toFirmwareResp(FirmwarePackage firmwarePackage) {
        FirmwarePackageResp resp = new FirmwarePackageResp();
        resp.setPackageId(firmwarePackage.getPackageId());
        resp.setProductKey(firmwarePackage.getProductKey());
        resp.setVersion(firmwarePackage.getVersion());
        resp.setDownloadUrl(firmwarePackage.getDownloadUrl());
        resp.setChecksum(firmwarePackage.getChecksum());
        resp.setReleaseNotes(firmwarePackage.getReleaseNotes());
        resp.setStatus(firmwarePackage.getStatus());
        return resp;
    }

    private FirmwarePackage getFirmwarePackage(String packageId) {
        LambdaQueryWrapper<FirmwarePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FirmwarePackage::getPackageId, packageId);
        FirmwarePackage firmwarePackage = firmwarePackageMapper.selectOne(wrapper);
        if (firmwarePackage == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "固件包不存在");
        }
        return firmwarePackage;
    }

    private List<Device> validateAndLoadDevices(List<String> deviceIds, String homeId, String productKey) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级设备列表不能为空");
        }
        List<Device> devices = new ArrayList<>();
        for (String deviceId : deviceIds) {
            Device device = deviceMapper.selectById(deviceId);
            if (device == null) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在: " + deviceId);
            }
            if (!homeId.equals(device.getHomeId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "设备不属于指定家庭: " + deviceId);
            }
            if (!productKey.equals(device.getProductKey())) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备产品与任务不匹配: " + deviceId);
            }
            devices.add(device);
        }
        return devices;
    }

    private void ensureProductExists(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        if (productMapper.selectOne(wrapper) == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "产品不存在");
        }
    }
}
