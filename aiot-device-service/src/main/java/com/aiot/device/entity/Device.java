package com.aiot.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {
    @TableId
    private Long id;
    private String deviceId;
    private String productId;
    private String deviceName;
    private Long tenantId;
    private Integer status;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
