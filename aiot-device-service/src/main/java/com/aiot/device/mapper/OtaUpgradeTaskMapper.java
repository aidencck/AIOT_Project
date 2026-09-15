package com.aiot.device.mapper;

import com.aiot.device.entity.OtaUpgradeTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OtaUpgradeTaskMapper extends BaseMapper<OtaUpgradeTask> {

    @Select("""
            SELECT *
            FROM ota_upgrade_task
            WHERE is_deleted = 0
              AND task_id = #{taskId}
            LIMIT 1
            """)
    OtaUpgradeTask selectByTaskId(@Param("taskId") String taskId);
}
