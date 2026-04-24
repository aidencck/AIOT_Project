package com.aiot.device.mapper;

import com.aiot.device.entity.Device;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
    @Update("UPDATE device_info SET status = #{status}, update_time = NOW() WHERE id = #{deviceId} AND is_deleted = 0")
    int updateStatusByDeviceId(@Param("deviceId") String deviceId, @Param("status") Integer status);
}
