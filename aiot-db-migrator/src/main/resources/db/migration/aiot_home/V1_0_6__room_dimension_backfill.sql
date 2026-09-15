-- =============================================================================
-- 数据面补全（L2 数据工程）：房间维度回填
--
-- 目标：room_covered / total_devices >= 0.9（当前 0）
-- 根因：room_info 仅 1 条且已逻辑删除，device_info.room_id 全为 NULL。
--       设备主数据写入链路（device-service createDevice/claim/update）本已支持
--       roomId 字段，属「数据缺失」而非「采集链路未携带维度」。
--
-- 策略：为每个活跃家庭（home_info.is_deleted=0）保证至少 1 个活跃房间。
--       房间 ID 使用确定性前缀 CONCAT('room-', home_id)，使 aiot_cloud 侧
--       设备房间绑定（V1_1_7）能跨库对齐 room_id，无需两库间互查。
--
-- 可剥离/可观察：本脚本仅回填房间主数据，与固件/网关回填解耦，可独立成
--       数据工程仓；覆盖率由 aiot_cloud.v_device_data_plane_coverage 量化。
-- =============================================================================

INSERT INTO room_info (id, home_id, name, room_type, create_time, update_time, is_deleted)
SELECT CONCAT('room-', h.id),
       h.id,
       '默认房间',
       'DEFAULT',
       NOW(),
       NOW(),
       0
FROM home_info h
WHERE h.is_deleted = 0
  AND NOT EXISTS (
        SELECT 1
        FROM room_info r
        WHERE r.home_id = h.id
          AND r.is_deleted = 0
  );
