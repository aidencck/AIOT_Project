-- 房间信息表增加房间类型字段，支持房间更新接口持久化 roomType
ALTER TABLE `room_info`
    ADD COLUMN `room_type` varchar(64) DEFAULT NULL COMMENT '房间类型' AFTER `name`;
