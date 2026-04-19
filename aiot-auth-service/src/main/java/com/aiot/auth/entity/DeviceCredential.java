package com.aiot.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("device_credential")
public class DeviceCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String deviceSecret;
    private Integer authType;
    @TableLogic
    private Integer isDeleted;
}
