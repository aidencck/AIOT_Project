package com.aiot.home.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭成员关联表
 */
@Data
@TableName("home_member")
public class HomeMember {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String homeId;

    private String userId;

    /**
     * 角色：1-Owner(所有者), 2-Admin(管理员), 3-Member(普通成员)
     */
    private Integer role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
