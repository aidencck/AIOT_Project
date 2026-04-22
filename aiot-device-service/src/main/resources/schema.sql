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

-- 固件包表
CREATE TABLE IF NOT EXISTS `firmware_package` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `package_id` varchar(64) NOT NULL COMMENT '固件包唯一ID',
  `product_key` varchar(64) NOT NULL COMMENT '产品Key',
  `version` varchar(64) NOT NULL COMMENT '固件版本',
  `download_url` varchar(255) NOT NULL COMMENT '下载地址',
  `checksum` varchar(128) DEFAULT NULL COMMENT '校验值',
  `release_notes` varchar(255) DEFAULT NULL COMMENT '发布说明',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态: 1-可用, 2-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_id` (`package_id`),
  UNIQUE KEY `uk_product_version` (`product_key`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='固件包表';

-- OTA升级任务表
CREATE TABLE IF NOT EXISTS `ota_upgrade_task` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `task_id` varchar(64) NOT NULL COMMENT '任务唯一ID',
  `home_id` varchar(64) NOT NULL COMMENT '家庭ID',
  `product_key` varchar(64) NOT NULL COMMENT '产品Key',
  `package_id` varchar(64) NOT NULL COMMENT '固件包ID',
  `target_version` varchar(64) NOT NULL COMMENT '目标版本',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '任务状态: 1-进行中, 2-完成, 3-已暂停',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '设备总数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功数',
  `failed_count` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_home_ctime` (`home_id`, `create_time`),
  KEY `idx_product_ctime` (`product_key`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA升级任务表';

-- OTA升级记录表
CREATE TABLE IF NOT EXISTS `ota_upgrade_record` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `record_id` varchar(64) NOT NULL COMMENT '记录唯一ID',
  `task_id` varchar(64) NOT NULL COMMENT '任务ID',
  `device_id` varchar(64) NOT NULL COMMENT '设备ID',
  `from_version` varchar(64) DEFAULT NULL COMMENT '升级前版本',
  `to_version` varchar(64) NOT NULL COMMENT '目标版本',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态: 1-待升级, 2-成功, 3-失败',
  `error_message` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `report_time` datetime DEFAULT NULL COMMENT '设备上报时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_id` (`record_id`),
  UNIQUE KEY `uk_task_device` (`task_id`, `device_id`),
  KEY `idx_device_ctime` (`device_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA升级记录表';
