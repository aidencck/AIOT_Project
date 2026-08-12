ALTER TABLE `device_info`
    ADD COLUMN `global_device_id` varchar(64) DEFAULT NULL COMMENT '全局设备ID' AFTER `id`,
    ADD COLUMN `device_sn` varchar(64) DEFAULT NULL COMMENT '设备序列号' AFTER `product_key`,
    ADD COLUMN `auth_identity` varchar(64) DEFAULT NULL COMMENT '设备认证身份' AFTER `device_sn`;

UPDATE `device_info`
SET `global_device_id` = `id`
WHERE `global_device_id` IS NULL OR `global_device_id` = '';

UPDATE `device_info`
SET `auth_identity` = `id`
WHERE `auth_identity` IS NULL OR `auth_identity` = '';

ALTER TABLE `device_info`
    MODIFY COLUMN `global_device_id` varchar(64) NOT NULL COMMENT '全局设备ID',
    MODIFY COLUMN `auth_identity` varchar(64) NOT NULL COMMENT '设备认证身份',
    ADD UNIQUE KEY `uk_global_device_id` (`global_device_id`),
    ADD UNIQUE KEY `uk_device_sn` (`device_sn`),
    ADD UNIQUE KEY `uk_auth_identity` (`auth_identity`);
