package com.aiot.device.mapper;

import com.aiot.common.dto.device.DeviceStatusSummaryResp;
import com.aiot.device.entity.Device;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
    @Update("UPDATE device_info SET status = #{status}, update_time = NOW() WHERE id = #{deviceId} AND is_deleted = 0")
    int updateStatusByDeviceId(@Param("deviceId") String deviceId, @Param("status") Integer status);

    @Select("""
            SELECT *
            FROM device_info
            WHERE is_deleted = 0
              AND (
                id = #{identifier}
                OR global_device_id = #{identifier}
                OR auth_identity = #{identifier}
                OR device_sn = #{identifier}
              )
            LIMIT 1
            """)
    Device selectByIdentity(@Param("identifier") String identifier);

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM device_info WHERE is_deleted = 0 AND status = 1) AS onlineCount,
              (SELECT COUNT(*) FROM device_info WHERE is_deleted = 0 AND status = 2) AS offlineCount,
              (SELECT COUNT(*) FROM device_info WHERE is_deleted = 0) AS totalCount
            """)
    DeviceStatusSummaryResp selectStatusSummary();

    @Update({
            "<script>",
            "UPDATE device_info",
            "SET status = #{status}, update_time = NOW()",
            "WHERE is_deleted = 0",
            "AND global_device_id IN",
            "<foreach collection='globalDeviceIds' item='globalDeviceId' open='(' separator=',' close=')'>",
            "#{globalDeviceId}",
            "</foreach>",
            "</script>"
    })
    int updateStatusByGlobalDeviceIds(@Param("globalDeviceIds") List<String> globalDeviceIds, @Param("status") Integer status);
}
