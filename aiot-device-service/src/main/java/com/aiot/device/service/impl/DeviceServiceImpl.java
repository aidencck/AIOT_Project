package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.DevicePageReq;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.DeviceUpdateReq;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.DeviceCredential;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.DeviceCredentialRepository;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.model.DeviceStatus;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.service.DeviceService;
import com.aiot.common.dto.home.HomeRoomRelationCheckResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DeviceCredentialRepository credentialRepository;

    @Autowired
    private HomePermissionService homePermissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceResp createDevice(DeviceReq req) {
        Product product = resolveProduct(req.getProductKey());
        String roomId = normalizeOptionalId(req.getRoomId());
        String gatewayId = normalizeOptionalId(req.getGatewayId());

        validateHomeRoomRelation(req.getHomeId(), roomId);
        validateGatewayBinding(product, req.getHomeId(), gatewayId, null);
        Device existed = findSingleDevice(req.getProductKey(), req.getDeviceName());
        if (existed != null) {
            return handleExistingDeviceOnCreate(existed, req, roomId, gatewayId);
        }

        Device device = new Device();
        String generatedId = IdWorker.getIdStr();
        device.setId(generatedId);
        device.setGlobalDeviceId(generatedId);
        device.setDeviceName(req.getDeviceName());
        device.setProductKey(req.getProductKey());
        device.setDeviceSn(normalizeOptionalId(req.getDeviceSn()));
        device.setAuthIdentity(resolveAuthIdentity(req.getAuthIdentity(), generatedId));
        device.setStatus(0); // 未激活
        device.setHomeId(req.getHomeId());
        device.setRoomId(roomId);
        device.setGatewayId(gatewayId);
        device.setFirmwareVersion(req.getFirmwareVersion());
        device.setLastHeartbeatTime(LocalDateTime.now());
        deviceRepository.insert(device);

        // 4. Create Credential (一机一密)
        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId(device.getId());
        credential.setAuthType(1); // 1-一机一密
        String deviceSecret = UUID.randomUUID().toString().replace("-", "");
        credential.setDeviceSecret(deviceSecret);
        credentialRepository.insert(credential);

        DeviceResp resp = convertToResp(device);
        resp.setDeviceSecret(deviceSecret);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceResp claimUnboundDevice(DeviceReq req) {
        Product product = resolveProduct(req.getProductKey());
        String roomId = normalizeOptionalId(req.getRoomId());
        String gatewayId = normalizeOptionalId(req.getGatewayId());
        validateHomeRoomRelation(req.getHomeId(), roomId);
        validateGatewayBinding(product, req.getHomeId(), gatewayId, null);

        Device existed = findSingleDevice(req.getProductKey(), req.getDeviceName());
        if (existed == null || StringUtils.hasText(existed.getHomeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "设备不可再次认领");
        }
        return claimExistingDevice(existed, req, roomId, gatewayId);
    }

    @Override
    public DeviceResp getDeviceById(String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        return convertToResp(device);
    }

    @Override
    public List<DeviceResp> listDevicesByHomeId(String homeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getHomeId, homeId);
        List<Device> devices = deviceRepository.selectList(wrapper);
        return devices.stream().map(this::convertToResp).collect(Collectors.toList());
    }

    @Override
    public IPage<DeviceResp> pageDevices(DevicePageReq req) {
        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 20 : req.getPageSize();
        pageSize = Math.min(pageSize, 200);

        Page<Device> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(req.getHomeId()), Device::getHomeId, req.getHomeId())
                .eq(StringUtils.hasText(req.getProductKey()), Device::getProductKey, req.getProductKey())
                .eq(req.getStatus() != null, Device::getStatus, req.getStatus())
                .orderByDesc(Device::getCreateTime);

        IPage<Device> devicePage = deviceRepository.selectPage(page, wrapper);
        return devicePage.convert(this::convertToResp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDevice(String deviceId, DeviceUpdateReq req) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        Product product = resolveProduct(device.getProductKey());

        if (StringUtils.hasText(req.getDeviceName())) {
            device.setDeviceName(req.getDeviceName());
        }
        if (req.getDeviceSn() != null) {
            device.setDeviceSn(normalizeOptionalId(req.getDeviceSn()));
        }
        if (req.getAuthIdentity() != null) {
            device.setAuthIdentity(resolveAuthIdentity(req.getAuthIdentity(), device.getId()));
        }
        if (req.getRoomId() != null) {
            String roomId = normalizeOptionalId(req.getRoomId());
            validateHomeRoomRelation(device.getHomeId(), roomId);
            device.setRoomId(roomId);
        }
        if (req.getGatewayId() != null) {
            String gatewayId = normalizeOptionalId(req.getGatewayId());
            validateGatewayBinding(product, device.getHomeId(), gatewayId, deviceId);
            device.setGatewayId(gatewayId);
        }
        if (req.getFirmwareVersion() != null) {
            device.setFirmwareVersion(req.getFirmwareVersion());
        }

        deviceRepository.updateById(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            return;
        }

        // If it's a gateway, we should probably unbind or delete sub-devices
        LambdaQueryWrapper<Device> subGw = new LambdaQueryWrapper<>();
        subGw.eq(Device::getGatewayId, deviceId);
        List<Device> subDevices = deviceRepository.selectList(subGw);
        if (!subDevices.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "网关下仍存在子设备，禁止删除");
        }

        deviceRepository.deleteById(deviceId);

        LambdaQueryWrapper<DeviceCredential> cw = new LambdaQueryWrapper<>();
        cw.eq(DeviceCredential::getDeviceId, deviceId);
        credentialRepository.delete(cw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceStatus(String deviceId, Integer status) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        validateStatusTransition(device.getStatus(), status);
        device.setStatus(status);
        if (status != null && status == 1) {
            device.setLastHeartbeatTime(LocalDateTime.now());
        }
        deviceRepository.updateById(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touchHeartbeat(String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        device.setLastHeartbeatTime(LocalDateTime.now());
        deviceRepository.updateById(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevicesByHomeId(String homeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getHomeId, homeId);
        List<Device> devices = deviceRepository.selectList(wrapper);
        int affected = 0;
        for (Device device : devices) {
            device.setHomeId(null);
            device.setRoomId(null);
            device.setGatewayId(null);
            deviceRepository.updateById(device);
            affected++;
        }
        log.info("Compensation audit: unbind home done, homeId={}, affectedDevices={}", homeId, affected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevicesByRoomId(String roomId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getRoomId, roomId);
        List<Device> devices = deviceRepository.selectList(wrapper);
        int affected = 0;
        for (Device device : devices) {
            device.setRoomId(null);
            deviceRepository.updateById(device);
            affected++;
        }
        log.info("Compensation audit: unbind room done, roomId={}, affectedDevices={}", roomId, affected);
    }

    private void validateStatusTransition(Integer from, Integer to) {
        DeviceStatus fromStatus = DeviceStatus.fromCode(from);
        DeviceStatus toStatus = DeviceStatus.fromCode(to);
        if (toStatus == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "非法设备状态");
        }
        if (!DeviceStatus.canTransition(fromStatus, toStatus)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED,
                    String.format("非法状态流转: %s(%d) -> %s(%d)，允许流转: 0->1, 1->2, 2->1",
                            fromStatus.getDesc(), fromStatus.getCode(), toStatus.getDesc(), toStatus.getCode()));
        }
    }

    private void validateHomeRoomRelation(String homeId, String roomId) {
        HomeRoomRelationCheckResp relation = homePermissionService.checkHomeRoomRelation(homeId, roomId);
        if (!Boolean.TRUE.equals(relation.getHomeExists())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "家庭不存在");
        }
        if (StringUtils.hasText(roomId) && !Boolean.TRUE.equals(relation.getRoomExists())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "房间不存在");
        }
        if (StringUtils.hasText(roomId) && !Boolean.TRUE.equals(relation.getRoomBelongsToHome())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "房间不属于当前家庭");
        }
    }

    private Device findSingleDevice(String productKey, String deviceName) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getProductKey, productKey)
                .eq(Device::getDeviceName, deviceName);
        List<Device> existedDevices = deviceRepository.selectList(wrapper);
        if (existedDevices.isEmpty()) {
            return null;
        }
        if (existedDevices.size() > 1) {
            throw new BusinessException(ResultCode.FAILED, "设备唯一性被破坏，请联系管理员修复");
        }
        return existedDevices.get(0);
    }

    private DeviceResp handleExistingDeviceOnCreate(Device existed, DeviceReq req, String roomId, String gatewayId) {
        if (req.getHomeId().equals(existed.getHomeId())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备已存在");
        }
        if (!StringUtils.hasText(existed.getHomeId())) {
            return claimExistingDevice(existed, req, roomId, gatewayId);
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "设备已绑定其他家庭，禁止重复认领");
    }

    private DeviceResp claimExistingDevice(Device existed, DeviceReq req, String roomId, String gatewayId) {
        if (!StringUtils.hasText(existed.getGlobalDeviceId())) {
            existed.setGlobalDeviceId(existed.getId());
        }
        existed.setHomeId(req.getHomeId());
        existed.setRoomId(roomId);
        existed.setGatewayId(gatewayId);
        if (req.getDeviceSn() != null) {
            existed.setDeviceSn(normalizeOptionalId(req.getDeviceSn()));
        }
        if (req.getAuthIdentity() != null) {
            existed.setAuthIdentity(resolveAuthIdentity(req.getAuthIdentity(), existed.getId()));
        } else if (!StringUtils.hasText(existed.getAuthIdentity())) {
            existed.setAuthIdentity(existed.getId());
        }
        if (StringUtils.hasText(req.getFirmwareVersion())) {
            existed.setFirmwareVersion(req.getFirmwareVersion());
        }
        deviceRepository.updateById(existed);

        DeviceCredential credential = loadCredential(existed.getId());
        DeviceResp resp = convertToResp(existed);
        resp.setDeviceSecret(credential.getDeviceSecret());
        return resp;
    }

    private DeviceCredential loadCredential(String deviceId) {
        LambdaQueryWrapper<DeviceCredential> cw = new LambdaQueryWrapper<>();
        cw.eq(DeviceCredential::getDeviceId, deviceId);
        DeviceCredential credential = credentialRepository.selectOne(cw);
        if (credential == null) {
            throw new BusinessException(ResultCode.FAILED, "设备凭证不存在，请联系管理员处理");
        }
        return credential;
    }

    private void validateGatewayBinding(Product product, String homeId, String gatewayId, String currentDeviceId) {
        if (product.getNodeType() == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "产品节点类型缺失");
        }
        if (product.getNodeType() != 3) {
            if (StringUtils.hasText(gatewayId)) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "仅子设备允许绑定网关");
            }
            return;
        }
        if (!StringUtils.hasText(gatewayId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "子设备必须绑定网关");
        }
        if (!StringUtils.hasText(homeId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "子设备必须归属家庭");
        }
        if (StringUtils.hasText(currentDeviceId) && currentDeviceId.equals(gatewayId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不能绑定自己作为网关");
        }
        Device gateway = deviceRepository.selectById(gatewayId);
        if (gateway == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "网关设备不存在");
        }
        if (!homeId.equals(gateway.getHomeId())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "网关设备不属于当前家庭");
        }
        Product gatewayProduct = resolveProduct(gateway.getProductKey());
        if (gatewayProduct.getNodeType() == null || gatewayProduct.getNodeType() != 2) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "关联的设备不是网关");
        }
    }

    private Product resolveProduct(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        Product product = productRepository.selectOne(wrapper);
        if (product == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "产品不存在");
        }
        return product;
    }

    private String normalizeOptionalId(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String resolveAuthIdentity(String authIdentity, String fallbackDeviceId) {
        return StringUtils.hasText(authIdentity) ? authIdentity : fallbackDeviceId;
    }

    private DeviceResp convertToResp(Device device) {
        DeviceResp resp = new DeviceResp();
        resp.setId(device.getId());
        resp.setGlobalDeviceId(resolveGlobalDeviceId(device));
        resp.setDeviceName(device.getDeviceName());
        resp.setProductKey(device.getProductKey());
        resp.setDeviceSn(device.getDeviceSn());
        resp.setAuthIdentity(resolveAuthIdentity(device.getAuthIdentity(), device.getId()));
        resp.setStatus(device.getStatus());
        resp.setHomeId(device.getHomeId());
        resp.setRoomId(device.getRoomId());
        resp.setGatewayId(device.getGatewayId());
        resp.setFirmwareVersion(device.getFirmwareVersion());
        resp.setLastHeartbeatTime(device.getLastHeartbeatTime());
        return resp;
    }

    private String resolveGlobalDeviceId(Device device) {
        if (device == null) {
            return null;
        }
        return StringUtils.hasText(device.getGlobalDeviceId()) ? device.getGlobalDeviceId() : device.getId();
    }
}
