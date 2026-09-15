-- =============================================================================
-- 数据面四维覆盖与回填（P1）
--
-- 背景：AI 诊断上下文存在“推理空壳”问题——qwen2.5:1.5b 在 room/gateway/firmware
--       等业务维度缺失时，输出收敛到默认安全值（置信度 0.8），判别力丧失。
--       数据面硬门禁（AiDiagnosisService 上下文完整度评分）已强制证据不足时
--       走兜底诊断，本迁移负责“补数据 + 度量覆盖”，倒逼 L2 数据工程补全。
--
-- 关键口径纠正（避免误判缺失）：
--   * gateway_id 仅对子设备(node_type=3)有意义。直连(node_type=1)/网关(node_type=2)
--     设备本身应为 NULL（应用层 validateGatewayBinding 已强制），因此 gateway
--     覆盖率必须按 node_type=3 子设备范围统计，而非全量设备。
--   * home_id 仅对已激活设备(status<>0)有意义。
--   * room_id / firmware_version 为可选采集维度，其缺失属前向治理范畴（边端上报
--     补齐），本迁移仅对“可确定性回填”的 firmware_version 做历史回填。
-- =============================================================================

-- 1. 回填 firmware_version：取最近一次成功(status=2) OTA 升级记录的 to_version
UPDATE device_info d
JOIN (
    SELECT r.device_id, r.to_version
    FROM ota_upgrade_record r
    JOIN (
        SELECT device_id,
               MAX(COALESCE(report_time, create_time)) AS max_time
        FROM ota_upgrade_record
        WHERE status = 2
          AND is_deleted = 0
          AND to_version IS NOT NULL
          AND to_version <> ''
        GROUP BY device_id
    ) latest
      ON r.device_id = latest.device_id
     AND COALESCE(r.report_time, r.create_time) = latest.max_time
    WHERE r.status = 2
      AND r.is_deleted = 0
      AND r.to_version IS NOT NULL
      AND r.to_version <> ''
) ota ON d.id = ota.device_id
SET d.firmware_version = ota.to_version
WHERE (d.firmware_version IS NULL OR d.firmware_version = '')
  AND d.is_deleted = 0;

-- 2. room_id 索引：支撑 room 覆盖查询与按房间解绑（unbindDevicesByRoomId）
ALTER TABLE `device_info`
    ADD INDEX `idx_room_id` (`room_id`);

-- 3. 数据面四维覆盖视图（维度感知口径，非全量一刀切）
CREATE OR REPLACE VIEW `v_device_data_plane_coverage` AS
SELECT
    COUNT(*)                                                                   AS total_devices,
    SUM(CASE WHEN d.status IS NOT NULL AND d.status <> 0 THEN 1 ELSE 0 END)    AS activated_devices,
    -- home_id：已激活设备口径
    SUM(CASE WHEN d.status IS NOT NULL AND d.status <> 0
               AND d.home_id IS NOT NULL AND d.home_id <> '' THEN 1 ELSE 0 END) AS home_covered,
    -- room_id：全量设备口径
    SUM(CASE WHEN d.room_id IS NOT NULL AND d.room_id <> '' THEN 1 ELSE 0 END) AS room_covered,
    -- gateway_id：仅子设备(node_type=3)口径
    SUM(CASE WHEN p.node_type = 3 THEN 1 ELSE 0 END)                           AS sub_device_total,
    SUM(CASE WHEN p.node_type = 3
               AND d.gateway_id IS NOT NULL AND d.gateway_id <> '' THEN 1 ELSE 0 END) AS gateway_covered,
    -- firmware_version：全量设备口径
    SUM(CASE WHEN d.firmware_version IS NOT NULL AND d.firmware_version <> '' THEN 1 ELSE 0 END) AS firmware_covered
FROM device_info d
LEFT JOIN product_info p ON p.product_key = d.product_key
WHERE d.is_deleted = 0;
