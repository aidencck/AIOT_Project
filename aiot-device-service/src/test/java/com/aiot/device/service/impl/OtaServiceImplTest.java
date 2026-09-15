package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.OtaTaskPageReq;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtaServiceImplTest {

    private FirmwarePackageRepository firmwarePackageRepository;
    private OtaUpgradeTaskRepository otaUpgradeTaskRepository;
    private OtaUpgradeRecordRepository otaUpgradeRecordRepository;
    private DeviceRepository deviceRepository;
    private ProductRepository productRepository;
    private OtaServiceImpl otaService;

    @BeforeEach
    void setUp() {
        firmwarePackageRepository = mock(FirmwarePackageRepository.class);
        otaUpgradeTaskRepository = mock(OtaUpgradeTaskRepository.class);
        otaUpgradeRecordRepository = mock(OtaUpgradeRecordRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        productRepository = mock(ProductRepository.class);
        otaService = new OtaServiceImpl(
                firmwarePackageRepository,
                otaUpgradeTaskRepository,
                otaUpgradeRecordRepository,
                deviceRepository,
                productRepository);
    }

    @Test
    void createUpgradeTask_shouldRejectWhenTargetVersionNotHigherThanCurrent() {
        when(productRepository.selectOne(any())).thenReturn(product("pk-1"));
        when(firmwarePackageRepository.selectOne(any())).thenReturn(firmware("pkg-1", "pk-1", "1.0.0"));
        when(deviceRepository.selectByIdentity("dev-1")).thenReturn(device("dev-1", "home-1", "pk-1", "1.0.0"));

        OtaUpgradeTaskCreateReq req = new OtaUpgradeTaskCreateReq();
        req.setHomeId("home-1");
        req.setProductKey("pk-1");
        req.setPackageId("pkg-1");
        req.setDeviceIds(List.of("dev-1"));

        BusinessException ex = assertThrows(BusinessException.class, () -> otaService.createUpgradeTask(req));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("目标版本必须高于设备当前版本: dev-1", ex.getMessage());
    }

    @Test
    void createUpgradeTask_shouldRejectWhenDeviceHasActiveTask() {
        when(productRepository.selectOne(any())).thenReturn(product("pk-1"));
        when(firmwarePackageRepository.selectOne(any())).thenReturn(firmware("pkg-1", "pk-1", "2.0.0"));
        when(deviceRepository.selectByIdentity("dev-1")).thenReturn(device("dev-1", "home-1", "pk-1", "1.0.0"));

        OtaUpgradeRecord active = new OtaUpgradeRecord();
        active.setDeviceId("dev-1");
        active.setActiveFlag(1);
        when(otaUpgradeRecordRepository.selectList(any())).thenReturn(List.of(active));

        OtaUpgradeTaskCreateReq req = new OtaUpgradeTaskCreateReq();
        req.setHomeId("home-1");
        req.setProductKey("pk-1");
        req.setPackageId("pkg-1");
        req.setDeviceIds(List.of("dev-1"));

        BusinessException ex = assertThrows(BusinessException.class, () -> otaService.createUpgradeTask(req));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("设备存在进行中的OTA任务: dev-1", ex.getMessage());
    }

    @Test
    void reportUpgradeResult_shouldRejectWhenToVersionMismatchesTask() {
        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setTaskId("task-1");
        task.setTargetVersion("2.0.0");
        when(otaUpgradeTaskRepository.selectOne(any())).thenReturn(task);

        OtaUpgradeRecord record = new OtaUpgradeRecord();
        record.setTaskId("task-1");
        record.setDeviceId("dev-1");
        record.setFromVersion("1.0.0");
        record.setToVersion("2.0.0");
        record.setStatus(1);
        record.setActiveFlag(1);
        when(otaUpgradeRecordRepository.selectOne(any())).thenReturn(record);

        when(deviceRepository.selectByIdentity("dev-sn-1"))
                .thenReturn(device("dev-1", "home-1", "pk-1", "1.0.0"));

        OtaUpgradeReportReq req = new OtaUpgradeReportReq();
        req.setStatus(2);
        req.setFromVersion("1.0.0");
        req.setToVersion("2.1.0");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> otaService.reportUpgradeResult("task-1", "dev-sn-1", req));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("升级上报目标版本与任务不一致", ex.getMessage());
        verify(otaUpgradeRecordRepository).selectOne(any());
    }

    @Test
    void pageUpgradeTasks_shouldReturnUnifiedPageResponse() {
        OtaTaskPageReq req = new OtaTaskPageReq();
        req.setHomeId("home-1");
        req.setPageNo(3);
        req.setPageSize(2);

        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setTaskId("task-1");
        task.setHomeId("home-1");
        task.setProductKey("pk-1");
        task.setStatus(1);

        Page<OtaUpgradeTask> taskPage = new Page<>(3, 2);
        taskPage.setTotal(7);
        taskPage.setRecords(List.of(task));
        when(otaUpgradeTaskRepository.selectPage(any(), any())).thenReturn(taskPage);

        OtaTaskPageResp resp = otaService.pageUpgradeTasks(req);

        assertEquals(7L, resp.getTotal());
        assertEquals(3, resp.getPageNo());
        assertEquals(2, resp.getPageSize());
        assertEquals(1, resp.getRecords().size());
        assertEquals("task-1", resp.getRecords().get(0).getTaskId());
    }

    private Product product(String productKey) {
        Product product = new Product();
        product.setProductKey(productKey);
        return product;
    }

    private FirmwarePackage firmware(String packageId, String productKey, String version) {
        FirmwarePackage firmwarePackage = new FirmwarePackage();
        firmwarePackage.setPackageId(packageId);
        firmwarePackage.setProductKey(productKey);
        firmwarePackage.setVersion(version);
        firmwarePackage.setStatus(1);
        return firmwarePackage;
    }

    private Device device(String deviceId, String homeId, String productKey, String version) {
        Device device = new Device();
        device.setId(deviceId);
        device.setHomeId(homeId);
        device.setProductKey(productKey);
        device.setFirmwareVersion(version);
        return device;
    }
}
