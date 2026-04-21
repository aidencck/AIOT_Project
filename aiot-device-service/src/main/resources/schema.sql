-- 产品信息表
CREATE TABLE IF NOT EXISTS `product_info` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `product_key` varchar(64) NOT NULL COMMENT '产品唯一标识',
  `name` varchar(64) NOT NULL COMMENT '产品名称',
  `description` varchar(255) DEFAULT NULL COMMENT '产品描述',
  `node_type` tinyint(1) NOT NULL COMMENT '节点类型: 1-直连, 2-网关, 3-子设备',
  `thing_model_json` json DEFAULT NULL COMMENT '物模型定义',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_key` (`product_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品信息表';

-- 设备信息表
CREATE TABLE IF NOT EXISTS `device_info` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `device_name` varchar(64) NOT NULL COMMENT '设备名称',
  `product_key` varchar(64) NOT NULL COMMENT '所属产品Key',
  `status` tinyint(1) DEFAULT '0' COMMENT '状态: 0-未激活, 1-在线, 2-离线',
  `home_id` varchar(64) DEFAULT NULL COMMENT '所属家庭',
  `room_id` varchar(64) DEFAULT NULL COMMENT '所属房间',
  `gateway_id` varchar(64) DEFAULT NULL COMMENT '父网关ID',
  `firmware_version` varchar(64) DEFAULT NULL COMMENT '固件版本',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最近心跳时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_home_id` (`home_id`),
  KEY `idx_gateway_id` (`gateway_id`),
  KEY `idx_last_heartbeat_time` (`last_heartbeat_time`),
  KEY `idx_home_status_ctime` (`home_id`, `status`, `create_time`),
  KEY `idx_product_status_ctime` (`product_key`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- 设备凭证表
CREATE TABLE IF NOT EXISTS `device_credential` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `device_id` varchar(64) NOT NULL COMMENT '设备ID',
  `auth_type` tinyint(1) DEFAULT '1' COMMENT '认证类型: 1-一机一密',
  `device_secret` varchar(128) NOT NULL COMMENT '设备密钥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备凭证表';
