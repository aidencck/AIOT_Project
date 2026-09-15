-- 同一家庭下活跃房间名唯一（含逻辑删除标记），防止并发创建同名房间
-- 说明：若存量数据存在重复的 (home_id, name, is_deleted)，需先治理再执行本迁移
ALTER TABLE `room_info`
    DROP INDEX `idx_home_name`,
    ADD UNIQUE KEY `uk_home_name` (`home_id`, `name`, `is_deleted`);
