package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaTaskPageReq;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeRecordResp;
import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.dto.PageResp;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.FirmwarePackage;
import com.aiot.device.entity.OtaUpgradeRecord;
import com.aiot.device.entity.OtaUpgradeTask;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.FirmwarePackageRepository;
import com.aiot.device.repository.OtaUpgradeRecordRepository;
import com.aiot.device.repository.OtaUpgradeTaskRepository;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.service.OtaService;
import com.aiot.device.utils.FirmwareVersionUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OtaServiceImpl implements OtaService {

    private final FirmwarePackageRepository firmwarePackageRepository;
    private final OtaUpgradeTaskRepository otaUpgradeTaskRepository;
    private final OtaUpgradeRecordRepository otaUpgradeRecordRepository;
    private final DeviceRepository deviceRepository;
    private final ProductRepository productRepository;

    public OtaServiceImpl(FirmwarePackageRepository firmwarePackageRepository,
                          OtaUpgradeTaskRepository otaUpgradeTaskRepository,
                          OtaUpgradeRecordRepository otaUpgradeRecordRepository,
                          DeviceRepository deviceRepository,
                          ProductRepository productRepository) {
        this.firmwarePackageRepository = firmwarePackageRepository;
        this.otaUpgradeTaskRepository = otaUpgradeTaskRepository;
        this.otaUpgradeRecordRepository = otaUpgradeRecordRepository;
        this.deviceRepository = deviceRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createFirmwarePackage(FirmwarePackageCreateReq req) {
        ensureProductExists(req.getProductKey());
        validateVersionFormat(req.getVersion(), "固件版本格式不合法");
        LambdaQueryWrapper<FirmwarePackage> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(FirmwarePackage::getProductKey, req.getProductKey())
                .eq(FirmwarePackage::getVersion, normalizeVersion(req.getVersion()));
        if (firmwarePackageRepository.selectOne(existsWrapper) != null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "同产品版本固件包已存在");
        }

        FirmwarePackage firmwarePackage = new FirmwarePackage();
        firmwarePackage.setPackageId("FW_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        firmwarePackage.setProductKey(req.getProductKey());
        firmwarePackage.setVersion(normalizeVersion(req.getVersion()));
        firmwarePackage.setDownloadUrl(req.getDownloadUrl());
        firmwarePackage.setChecksum(req.getChecksum());
        firmwarePackage.setReleaseNotes(req.getReleaseNotes());
        firmwarePackage.setStatus(1);
        firmwarePackageRepository.insert(firmwarePackage);
        return firmwarePackage.getPackageId();
    }

    @Override
    public List<FirmwarePackageResp> listFirmwarePackages(String productKey) {
        LambdaQueryWrapper<FirmwarePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(productKey), FirmwarePackage::getProductKey, productKey)
                .orderByDesc(FirmwarePackage::getCreateTime);
        return firmwarePackageRepository.selectList(wrapper).stream().map(this::toFirmwareResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFirmwarePackage(String packageId) {
        FirmwarePackage firmwarePackage = findFirmwarePackage(packageId);
        if (firmwarePackage == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "固件包不存在");
        }
        firmwarePackageRepository.deleteById(firmwarePackage.getId());
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
        validateVersionFormat(firmwarePackage.getVersion(), "固件包版本格式不合法");

        List<Device> devices = validateAndLoadDevices(req.getDeviceIds(), req.getHomeId(), req.getProductKey(), firmwarePackage.getVersion());
        assertNoActiveUpgrade(devices.stream().map(Device::getId).collect(Collectors.toList()));
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
        otaUpgradeTaskRepository.insert(task);

        try {
            for (Device device : devices) {
                OtaUpgradeRecord record = new OtaUpgradeRecord();
                record.setRecordId("OTAR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
                record.setTaskId(task.getTaskId());
                record.setDeviceId(device.getId());
                record.setFromVersion(device.getFirmwareVersion());
                record.setToVersion(firmwarePackage.getVersion());
                record.setStatus(1);
                record.setActiveFlag(1);
                otaUpgradeRecordRepository.insert(record);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备存在进行中的OTA任务");
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
        return otaUpgradeTaskRepository.selectList(wrapper).stream()
                .map(task -> toTaskResp(task, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelUpgradeTask(String taskId) {
        OtaUpgradeTask task = findTask(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "升级任务不存在");
        }
        if (task.getStatus() != null && task.getStatus() == 2) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "任务已完成，无法取消");
        }
        if (task.getStatus() != null && task.getStatus() == 3) {
            return;
        }

        LambdaUpdateWrapper<OtaUpgradeRecord> recordWrapper = new LambdaUpdateWrapper<>();
        recordWrapper.eq(OtaUpgradeRecord::getTaskId, task.getTaskId())
                .eq(OtaUpgradeRecord::getStatus, 1)
                .set(OtaUpgradeRecord::getActiveFlag, null);
        otaUpgradeRecordRepository.update(null, recordWrapper);

        task.setStatus(3);
        otaUpgradeTaskRepository.updateById(task);
    }

    @Override
    public OtaTaskPageResp pageUpgradeTasks(OtaTaskPageReq req) {
        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 20 : req.getPageSize();
        pageSize = Math.min(pageSize, 200);

        Page<OtaUpgradeTask> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<OtaUpgradeTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OtaUpgradeTask::getHomeId, req.getHomeId())
                .eq(StringUtils.hasText(req.getProductKey()), OtaUpgradeTask::getProductKey, req.getProductKey())
                .eq(req.getStatus() != null, OtaUpgradeTask::getStatus, req.getStatus())
                .orderByDesc(OtaUpgradeTask::getCreateTime);
        IPage<OtaUpgradeTask> taskPage = otaUpgradeTaskRepository.selectPage(page, wrapper);
        return OtaTaskPageResp.from(PageResp.from(taskPage.convert(task -> toTaskResp(task, false))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportUpgradeResult(String taskId, String deviceId, OtaUpgradeReportReq req) {
        OtaUpgradeTask task = getTask(taskId);
        Device device = requireDevice(deviceId);
        LambdaQueryWrapper<OtaUpgradeRecord> rw = new LambdaQueryWrapper<>();
        rw.eq(OtaUpgradeRecord::getTaskId, taskId)
                .eq(OtaUpgradeRecord::getDeviceId, device.getId());
        OtaUpgradeRecord record = otaUpgradeRecordRepository.selectOne(rw);
        if (record == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级记录不存在");
        }
        if (record.getStatus() != null && record.getStatus() != 1) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级记录已完成上报");
        }
        validateUpgradeReport(task, record, device, req);

        record.setFromVersion(req.getFromVersion());
        record.setToVersion(req.getToVersion());
        record.setStatus(req.getStatus());
        record.setActiveFlag(null);
        record.setErrorMessage(req.getErrorMessage());
        record.setReportTime(LocalDateTime.now());
        otaUpgradeRecordRepository.updateById(record);

        if (req.getStatus() != null && req.getStatus() == 2) {
            device.setFirmwareVersion(req.getToVersion());
            deviceRepository.updateById(device);
        }
        refreshTaskStatistics(task);
    }

    private void refreshTaskStatistics(OtaUpgradeTask task) {
        LambdaQueryWrapper<OtaUpgradeRecord> rw = new LambdaQueryWrapper<>();
        rw.eq(OtaUpgradeRecord::getTaskId, task.getTaskId());
        List<OtaUpgradeRecord> records = otaUpgradeRecordRepository.selectList(rw);
        int success = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 2).count();
        int failed = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 3).count();
        int pending = (int) records.stream().filter(r -> r.getStatus() != null && r.getStatus() == 1).count();
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setStatus(pending == 0 ? 2 : 1);
        otaUpgradeTaskRepository.updateById(task);
    }

    private OtaUpgradeTask getTask(String taskId) {
        OtaUpgradeTask task = findTask(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级任务不存在");
        }
        return task;
    }

    private OtaUpgradeTask findTask(String taskId) {
        LambdaQueryWrapper<OtaUpgradeTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OtaUpgradeTask::getTaskId, taskId);
        return otaUpgradeTaskRepository.selectOne(wrapper);
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
            List<OtaUpgradeRecordResp> recordResps = otaUpgradeRecordRepository.selectList(rw).stream()
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
        FirmwarePackage firmwarePackage = findFirmwarePackage(packageId);
        if (firmwarePackage == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "固件包不存在");
        }
        return firmwarePackage;
    }

    private FirmwarePackage findFirmwarePackage(String packageId) {
        LambdaQueryWrapper<FirmwarePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FirmwarePackage::getPackageId, packageId);
        return firmwarePackageRepository.selectOne(wrapper);
    }

    private List<Device> validateAndLoadDevices(List<String> deviceIds, String homeId, String productKey, String targetVersion) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级设备列表不能为空");
        }
        List<String> uniqueDeviceIds = new ArrayList<>(new LinkedHashSet<>(deviceIds));
        LinkedHashMap<String, Device> devices = new LinkedHashMap<>();
        for (String deviceId : uniqueDeviceIds) {
            Device device = deviceRepository.selectByIdentity(deviceId);
            if (device == null) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在: " + deviceId);
            }
            if (!homeId.equals(device.getHomeId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "设备不属于指定家庭: " + deviceId);
            }
            if (!productKey.equals(device.getProductKey())) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备产品与任务不匹配: " + deviceId);
            }
            validateVersionFormat(device.getFirmwareVersion(), "设备当前固件版本格式不合法: " + deviceId);
            if (FirmwareVersionUtils.compare(targetVersion, device.getFirmwareVersion()) <= 0) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "目标版本必须高于设备当前版本: " + deviceId);
            }
            devices.putIfAbsent(device.getId(), device);
        }
        return new ArrayList<>(devices.values());
    }

    private Device requireDevice(String deviceIdentity) {
        Device device = deviceRepository.selectByIdentity(deviceIdentity);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        return device;
    }

    private void validateUpgradeReport(OtaUpgradeTask task, OtaUpgradeRecord record, Device device, OtaUpgradeReportReq req) {
        validateVersionFormat(req.getFromVersion(), "升级上报的源版本格式不合法");
        validateVersionFormat(req.getToVersion(), "升级上报的目标版本格式不合法");
        if (!normalizeVersion(task.getTargetVersion()).equals(normalizeVersion(req.getToVersion()))) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级上报目标版本与任务不一致");
        }
        if (!normalizeVersion(record.getToVersion()).equals(normalizeVersion(req.getToVersion()))) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级上报目标版本与升级记录不一致");
        }
        if (!normalizeVersion(record.getFromVersion()).equals(normalizeVersion(req.getFromVersion()))) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级上报源版本与升级记录不一致");
        }
        if (!normalizeVersion(device.getFirmwareVersion()).equals(normalizeVersion(req.getFromVersion()))) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "升级上报源版本与设备当前版本不一致");
        }
    }

    private void assertNoActiveUpgrade(List<String> deviceIds) {
        if (deviceIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<OtaUpgradeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OtaUpgradeRecord::getDeviceId, deviceIds)
                .eq(OtaUpgradeRecord::getActiveFlag, 1);
        List<OtaUpgradeRecord> activeRecords = otaUpgradeRecordRepository.selectList(wrapper);
        if (!activeRecords.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED,
                    "设备存在进行中的OTA任务: " + activeRecords.get(0).getDeviceId());
        }
    }

    private void validateVersionFormat(String version, String message) {
        if (!FirmwareVersionUtils.isValid(version)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, message);
        }
    }

    private String normalizeVersion(String version) {
        return FirmwareVersionUtils.normalize(version);
    }

    private void ensureProductExists(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        if (productRepository.selectOne(wrapper) == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "产品不存在");
        }
    }
}
