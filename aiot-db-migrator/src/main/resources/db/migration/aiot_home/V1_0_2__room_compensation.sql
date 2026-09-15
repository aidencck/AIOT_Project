ALTER TABLE `home_delete_compensation_task`
    ADD COLUMN `target_type` varchar(16) NOT NULL DEFAULT 'HOME' COMMENT '补偿目标类型: HOME/ROOM' AFTER `id`,
    ADD COLUMN `target_id` varchar(64) DEFAULT NULL COMMENT '补偿目标ID' AFTER `target_type`;

UPDATE `home_delete_compensation_task`
SET `target_id` = `home_id`
WHERE `target_id` IS NULL;

ALTER TABLE `home_delete_compensation_task`
    DROP INDEX `uk_home_id`,
    ADD UNIQUE KEY `uk_target` (`target_type`,`target_id`);
