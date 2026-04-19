package com.aiot.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device_credential")
public class DeviceCredential {
    @TableId
    private Long id;
    private String deviceId;
    private String deviceSecret;
    private Integer authType;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
