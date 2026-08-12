package com.aiot.auth.mapper;

import com.aiot.auth.entity.DeviceCredential;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceCredentialMapper extends BaseMapper<DeviceCredential> {

    @Select("""
            SELECT dc.*
            FROM device_credential dc
            INNER JOIN device_info di ON di.id = dc.device_id
            WHERE dc.is_deleted = 0
              AND di.is_deleted = 0
              AND (
                dc.device_id = #{identifier}
                OR di.global_device_id = #{identifier}
                OR di.auth_identity = #{identifier}
                OR di.device_sn = #{identifier}
              )
            LIMIT 1
            """)
    DeviceCredential selectByIdentity(@Param("identifier") String identifier);
}
