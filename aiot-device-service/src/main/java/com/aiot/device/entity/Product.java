package com.aiot.device.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品实体类
 */
@Data
@TableName("product_info")
public class Product {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String productKey;

    private String name;

    private String description;

    /**
     * 节点类型：1-直连设备，2-网关，3-网关子设备
     */
    private Integer nodeType;

    /**
     * 物模型 JSON 定义 (Properties, Events, Services)
     */
    private String thingModelJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
