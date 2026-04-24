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
import com.aiot.device.mapper.DeviceCredentialMapper;
import com.aiot.device.mapper.DeviceMapper;
import com.aiot.device.mapper.ProductMapper;
import com.aiot.device.model.DeviceStatus;
import com.aiot.device.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    private DeviceMapper deviceMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private DeviceCredentialMapper credentialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceResp createDevice(DeviceReq req) {
        // 1. Validate Product
        LambdaQueryWrapper<Product> pw = new LambdaQueryWrapper<>();
        pw.eq(Product::getProductKey, req.getProductKey());
        Product product = productMapper.selectOne(pw);
        if (product == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "产品不存在");
        }

        // 2. Validate Gateway Topology if it's a sub-device (nodeType == 3)
        if (product.getNodeType() == 3) {
            if (!StringUtils.hasText(req.getGatewayId())) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "子设备必须绑定网关");
            }
            Device gateway = deviceMapper.selectById(req.getGatewayId());
            if (gateway == null) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "网关设备不存在");
            }
            
            // Validate if gateway is indeed a gateway
            LambdaQueryWrapper<Product> gwPw = new LambdaQueryWrapper<>();
            gwPw.eq(Product::getProductKey, gateway.getProductKey());
            Product gatewayProduct = productMapper.selectOne(gwPw);
            if (gatewayProduct == null || gatewayProduct.getNodeType() != 2) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "关联的设备不是网关");
            }
        }

        // 3. Create Device
        Device device = new Device();
        device.setDeviceName(req.getDeviceName());
        device.setProductKey(req.getProductKey());
        device.setStatus(0); // 未激活
        device.setHomeId(req.getHomeId());
        device.setRoomId(req.getRoomId());
        device.setGatewayId(req.getGatewayId());
        device.setFirmwareVersion(req.getFirmwareVersion());
        device.setLastHeartbeatTime(LocalDateTime.now());
        deviceMapper.insert(device);

        // 4. Create Credential (一机一密)
        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId(device.getId());
        credential.setAuthType(1); // 1-一机一密
        String deviceSecret = UUID.randomUUID().toString().replace("-", "");
        credential.setDeviceSecret(deviceSecret);
        credentialMapper.insert(credential);

        DeviceResp resp = convertToResp(device);
        resp.setDeviceSecret(deviceSecret);
        return resp;
    }

    @Override
    public DeviceResp getDeviceById(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        return convertToResp(device);
    }

    @Override
    public List<DeviceResp> listDevicesByHomeId(String homeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getHomeId, homeId);
        List<Device> devices = deviceMapper.selectList(wrapper);
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

        IPage<Device> devicePage = deviceMapper.selectPage(page, wrapper);
        return devicePage.convert(this::convertToResp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDevice(String deviceId, DeviceUpdateReq req) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }

        if (StringUtils.hasText(req.getDeviceName())) {
            device.setDeviceName(req.getDeviceName());
        }
        if (req.getRoomId() != null) {
            device.setRoomId(req.getRoomId());
        }
        if (req.getGatewayId() != null) {
            device.setGatewayId(req.getGatewayId());
        }
        if (req.getFirmwareVersion() != null) {
            device.setFirmwareVersion(req.getFirmwareVersion());
        }

        deviceMapper.updateById(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return;
        }
        
        // Delete device
        deviceMapper.deleteById(deviceId);
        
        // Delete credential
        LambdaQueryWrapper<DeviceCredential> cw = new LambdaQueryWrapper<>();
        cw.eq(DeviceCredential::getDeviceId, deviceId);
        credentialMapper.delete(cw);
        
        // If it's a gateway, we should probably unbind or delete sub-devices
        LambdaQueryWrapper<Device> subGw = new LambdaQueryWrapper<>();
        subGw.eq(Device::getGatewayId, deviceId);
        List<Device> subDevices = deviceMapper.selectList(subGw);
        if (!subDevices.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "网关下仍存在子设备，禁止删除");
        }
    }

    @Override
    public void updateDeviceStatus(String deviceId, Integer status) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        validateStatusTransition(device.getStatus(), status);
        device.setStatus(status);
        if (status != null && status == 1) {
            device.setLastHeartbeatTime(LocalDateTime.now());
        }
        deviceMapper.updateById(device);
    }

    @Override
    public void touchHeartbeat(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备不存在");
        }
        device.setLastHeartbeatTime(LocalDateTime.now());
        deviceMapper.updateById(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevicesByHomeId(String homeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getHomeId, homeId);
        List<Device> devices = deviceMapper.selectList(wrapper);
        int affected = 0;
        for (Device device : devices) {
            device.setHomeId(null);
            device.setRoomId(null);
            deviceMapper.updateById(device);
            affected++;
        }
        log.info("Compensation audit: unbind home done, homeId={}, affectedDevices={}", homeId, affected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindDevicesByRoomId(String roomId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getRoomId, roomId);
        List<Device> devices = deviceMapper.selectList(wrapper);
        int affected = 0;
        for (Device device : devices) {
            device.setRoomId(null);
            deviceMapper.updateById(device);
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

    private DeviceResp convertToResp(Device device) {
        DeviceResp resp = new DeviceResp();
        resp.setId(device.getId());
        resp.setDeviceName(device.getDeviceName());
        resp.setProductKey(device.getProductKey());
        resp.setStatus(device.getStatus());
        resp.setHomeId(device.getHomeId());
        resp.setRoomId(device.getRoomId());
        resp.setGatewayId(device.getGatewayId());
        resp.setFirmwareVersion(device.getFirmwareVersion());
        resp.setLastHeartbeatTime(device.getLastHeartbeatTime());
        return resp;
    }
}
