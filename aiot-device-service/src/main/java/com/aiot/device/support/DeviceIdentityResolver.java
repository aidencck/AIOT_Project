package com.aiot.device.support;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.entity.Device;
import com.aiot.device.repository.DeviceRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DeviceIdentityResolver {

    private final DeviceRepository deviceRepository;

    public DeviceIdentityResolver(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device requireByIdentity(String deviceIdentity, String notFoundMessage) {
        Device device = findByIdentity(deviceIdentity);
        if (device == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, notFoundMessage);
        }
        return device;
    }

    public Device findByIdentity(String deviceIdentity) {
        if (!StringUtils.hasText(deviceIdentity)) {
            return null;
        }
        return deviceRepository.selectByIdentity(deviceIdentity);
    }

    public String resolveGlobalDeviceId(Device device) {
        if (device == null) {
            return null;
        }
        return StringUtils.hasText(device.getGlobalDeviceId()) ? device.getGlobalDeviceId() : device.getId();
    }

    public String resolveAuthIdentity(Device device) {
        if (device == null) {
            return null;
        }
        if (StringUtils.hasText(device.getAuthIdentity())) {
            return device.getAuthIdentity();
        }
        if (StringUtils.hasText(device.getDeviceSn())) {
            return device.getDeviceSn();
        }
        return resolveGlobalDeviceId(device);
    }

    public String resolveAuthIdentity(String explicitAuthIdentity,
                                      String deviceSn,
                                      String globalDeviceId,
                                      String fallbackDeviceId) {
        if (StringUtils.hasText(explicitAuthIdentity)) {
            return explicitAuthIdentity;
        }
        if (StringUtils.hasText(deviceSn)) {
            return deviceSn;
        }
        if (StringUtils.hasText(globalDeviceId)) {
            return globalDeviceId;
        }
        return fallbackDeviceId;
    }
}
