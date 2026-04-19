package com.aiot.device.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId
    private Long id;
    private String productId;
    private String productName;
    private String description;
    private String nodeType;
    private String thingModelJson;
    private Long tenantId;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
