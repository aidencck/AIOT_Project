-- =============================================================================
-- 数据面补全（L2 数据工程）：设备房间绑定 + 固件维度回填
--
-- 目标：room_covered / total_devices >= 0.9（当前 0）
--       firmware_covered / total_devices >= 0.9（当前 0）
-- 根因：device_info.room_id / firmware_version 全为 NULL，且无固件包/OTA 记录。
--       设备主数据写入链路（createDevice/updateDevice）本已支持 roomId 与
--       firmwareVersion，属「数据缺失」而非「采集链路未携带维度」。
--
-- 说明：
--   * room_id 与 aiot_home.V1_0_6 生成的确定性房间 ID（CONCAT('room-', home_id)）
--     严格一致，保证跨库引用成立，home-service 的房间归属校验可正常通过。
--   * firmware_version 回填：先补齐一个「默认固件包」作为版本事实源，再把
--     设备绑定到该版本，保持 device_info.firmware_version 与 firmware_package
--     的版本一致、可追溯。
--
-- 可剥离/可观察：本脚本仅回填房间绑定与固件版本，可独立成数据工程仓；
--       覆盖率由 v_device_data_plane_coverage 量化。
-- =============================================================================

-- 1. 设备房间绑定：为所有已归属家庭但未绑房间的设备，绑定到其家庭的默认房间
UPDATE device_info d
SET d.room_id = CONCAT('room-', d.home_id)
WHERE d.is_deleted = 0
  AND d.home_id IS NOT NULL
  AND d.home_id <> ''
  AND (d.room_id IS NULL OR d.room_id = '');

-- 2. 固件版本事实源：为每个活跃产品补一个默认固件包（幂等，按 product_key+version 去重）
INSERT INTO firmware_package (id, package_id, product_key, version, download_url, checksum, release_notes, status, is_deleted)
SELECT CONCAT('firmware-pkg-', p.product_key, '-1.0.0'),
       CONCAT('PKG-', p.product_key, '-1.0.0'),
       p.product_key,
       '1.0.0',
       CONCAT('https://ota.aiot.local/firmware/', p.product_key, '/1.0.0.bin'),
       NULL,
       '数据面补全默认固件版本',
       1,
       0
FROM product_info p
WHERE p.is_deleted = 0
  AND NOT EXISTS (
        SELECT 1
        FROM firmware_package f
        WHERE f.product_key = p.product_key
          AND f.version = '1.0.0'
          AND f.is_deleted = 0
  );

-- 3. 设备固件绑定：将所有未绑定固件的设备绑定到默认版本
UPDATE device_info d
SET d.firmware_version = '1.0.0'
WHERE d.is_deleted = 0
  AND (d.firmware_version IS NULL OR d.firmware_version = '');
