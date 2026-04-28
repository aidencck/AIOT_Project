package com.aiot.device.mapper;

import com.aiot.device.entity.Device;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
    @Update("UPDATE device_info SET status = #{status}, update_time = NOW() WHERE id = #{deviceId} AND is_deleted = 0")
    int updateStatusByDeviceId(@Param("deviceId") String deviceId, @Param("status") Integer status);

    @Update({
            "<script>",
            "UPDATE device_info",
            "SET status = #{status}, update_time = NOW()",
            "WHERE is_deleted = 0",
            "AND id IN",
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int updateStatusByDeviceIds(@Param("deviceIds") List<String> deviceIds, @Param("status") Integer status);
}
