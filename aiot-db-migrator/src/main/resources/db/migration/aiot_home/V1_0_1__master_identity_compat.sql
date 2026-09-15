ALTER TABLE `user_info`
    ADD COLUMN `global_user_id` varchar(64) DEFAULT NULL COMMENT '全局用户ID' AFTER `id`;

UPDATE `user_info`
SET `global_user_id` = `id`
WHERE `global_user_id` IS NULL OR `global_user_id` = '';

ALTER TABLE `user_info`
    MODIFY COLUMN `global_user_id` varchar(64) NOT NULL COMMENT '全局用户ID',
    ADD UNIQUE KEY `uk_global_user_id` (`global_user_id`);
