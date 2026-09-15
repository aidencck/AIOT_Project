-- 设备信息表增加乐观锁版本号，配合 Mybatis-Plus OptimisticLockerInnerInterceptor 防止并发更新丢数据
ALTER TABLE `device_info`
    ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `update_time`;
