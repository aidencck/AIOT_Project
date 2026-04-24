-- 用户信息表
CREATE TABLE IF NOT EXISTS `user_info` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `password` varchar(128) NOT NULL COMMENT '密码(MD5)',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 家庭信息表
CREATE TABLE IF NOT EXISTS `home_info` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '家庭名称',
  `location` varchar(128) DEFAULT NULL COMMENT '地理位置',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭信息表';

-- 房间信息表
CREATE TABLE IF NOT EXISTS `room_info` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `home_id` varchar(64) NOT NULL COMMENT '家庭ID',
  `name` varchar(64) NOT NULL COMMENT '房间名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_home_id` (`home_id`),
  KEY `idx_home_name` (`home_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间信息表';
CREATE TABLE IF NOT EXISTS `home_member` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `home_id` varchar(64) NOT NULL COMMENT '家庭ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `role` tinyint(1) NOT NULL COMMENT '角色: 1-Owner, 2-Admin, 3-Member',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_home_user` (`home_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭成员关联表';
