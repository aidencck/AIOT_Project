package com.aiot.device.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备实体类
 */
@Data
@TableName("device_info")
public class Device {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String deviceName;

    private String productKey;

    /**
     * 设备状态：0-未激活，1-在线，2-离线
     */
    private Integer status;

    /**
     * 所属家庭 ID
     */
    private String homeId;

    /**
     * 所属房间 ID
     */
    private String roomId;

    /**
     * 网关ID（如果当前设备是子设备，则关联父网关）
     */
    private String gatewayId;

    /**
     * 固件版本
     */
    private String firmwareVersion;

    /**
     * 最近一次心跳时间
     */
    private LocalDateTime lastHeartbeatTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
