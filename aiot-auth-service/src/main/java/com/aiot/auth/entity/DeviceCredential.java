package com.aiot.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备凭证表 (aiot-auth-service)
 */
@Data
@TableName("device_credential")
public class DeviceCredential {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String deviceId;

    /**
     * 认证类型：1-一机一密，2-一型一密
     */
    private Integer authType;

    private String deviceSecret;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
