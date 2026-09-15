package com.aiot.device.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ota_upgrade_task")
public class OtaUpgradeTask {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String taskId;

    private String homeId;

    private String productKey;

    private String packageId;

    private String targetVersion;

    private Integer status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
