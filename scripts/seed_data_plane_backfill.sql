-- =============================================================================
-- 数据面确定性种子 + 回填验证（R3 验收）
--
-- 用途：向空数据面注入确定性 family/room/device 数据，复现 Flyway 回填逻辑，
--       验证 v_device_data_plane_coverage 覆盖率 >= 0.9。
--
-- 回填逻辑来源（镜像，不重复散落）：
--   aiot_home/V1_0_6  房间主数据回填
--   aiot_cloud/V1_1_7 设备房间绑定 + 固件版本回填
--   aiot_cloud/V1_1_8 网关/子设备晋升与绑定
--
-- 幂等：INSERT 用 NOT EXISTS 去重，UPDATE 为幂等赋值，可重复执行。
-- 验收锚点：room_covered/total_devices >= 0.9
--          firmware_covered/total_devices >= 0.9
--          gateway_covered/sub_device_total >= 0.9
-- =============================================================================

-- 1. 直连产品（种子设备主产品，node_type=1）
INSERT INTO aiot_cloud.product_info (id, product_key, name, description, node_type, create_time, update_time, is_deleted)
SELECT 'seed-product-direct', 'SEED_DIRECT', 'Seed Direct Device', '数据面种子：直连设备', 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM aiot_cloud.product_info WHERE product_key = 'SEED_DIRECT' AND is_deleted = 0);

-- 2. 家庭（3 个）
INSERT INTO aiot_home.home_info (id, name, location, create_time, update_time, is_deleted)
SELECT 'seed-home-1', '种子家庭1', 'seed', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM aiot_home.home_info WHERE id = 'seed-home-1');
INSERT INTO aiot_home.home_info (id, name, location, create_time, update_time, is_deleted)
SELECT 'seed-home-2', '种子家庭2', 'seed', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM aiot_home.home_info WHERE id = 'seed-home-2');
INSERT INTO aiot_home.home_info (id, name, location, create_time, update_time, is_deleted)
SELECT 'seed-home-3', '种子家庭3', 'seed', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM aiot_home.home_info WHERE id = 'seed-home-3');

-- 3. 设备（每个家庭 3 台，原始未回填态：room_id/gateway_id/firmware_version 全 NULL）
INSERT INTO aiot_cloud.device_info
    (id, global_device_id, device_name, product_key, device_sn, auth_identity,
     status, home_id, room_id, gateway_id, firmware_version,
     create_time, update_time, version, is_deleted)
SELECT d.id, d.id, d.id, 'SEED_DIRECT', d.id, d.id,
       1, d.home_id, NULL, NULL, NULL,
       NOW(), NOW(), 0, 0
FROM (
    SELECT 'seed-dev-1-1' AS id, 'seed-home-1' AS home_id
    UNION ALL SELECT 'seed-dev-1-2', 'seed-home-1'
    UNION ALL SELECT 'seed-dev-1-3', 'seed-home-1'
    UNION ALL SELECT 'seed-dev-2-1', 'seed-home-2'
    UNION ALL SELECT 'seed-dev-2-2', 'seed-home-2'
    UNION ALL SELECT 'seed-dev-2-3', 'seed-home-2'
    UNION ALL SELECT 'seed-dev-3-1', 'seed-home-3'
    UNION ALL SELECT 'seed-dev-3-2', 'seed-home-3'
    UNION ALL SELECT 'seed-dev-3-3', 'seed-home-3'
) d
WHERE NOT EXISTS (SELECT 1 FROM aiot_cloud.device_info e WHERE e.id = d.id);

-- 4. 回填：房间主数据（镜像 V1_0_6）
INSERT INTO aiot_home.room_info (id, home_id, name, room_type, create_time, update_time, is_deleted)
SELECT CONCAT('room-', h.id), h.id, '默认房间', 'DEFAULT', NOW(), NOW(), 0
FROM aiot_home.home_info h
WHERE h.is_deleted = 0
  AND h.id LIKE 'seed-home-%'
  AND NOT EXISTS (SELECT 1 FROM aiot_home.room_info r WHERE r.home_id = h.id AND r.is_deleted = 0);

-- 5. 回填：设备房间绑定（镜像 V1_1_7 step1）
UPDATE aiot_cloud.device_info d
SET d.room_id = CONCAT('room-', d.home_id)
WHERE d.is_deleted = 0
  AND d.home_id LIKE 'seed-home-%'
  AND (d.room_id IS NULL OR d.room_id = '');

-- 6. 回填：固件版本事实源 + 设备绑定（镜像 V1_1_7 step2/3）
INSERT INTO aiot_cloud.firmware_package (id, package_id, product_key, version, download_url, checksum, release_notes, status, is_deleted)
SELECT CONCAT('firmware-pkg-', p.product_key, '-1.0.0'),
       CONCAT('PKG-', p.product_key, '-1.0.0'),
       p.product_key,
       '1.0.0',
       CONCAT('https://ota.aiot.local/firmware/', p.product_key, '/1.0.0.bin'),
       NULL,
       '数据面种子默认固件版本',
       1,
       0
FROM aiot_cloud.product_info p
WHERE p.is_deleted = 0
  AND p.product_key = 'SEED_DIRECT'
  AND NOT EXISTS (SELECT 1 FROM aiot_cloud.firmware_package f WHERE f.product_key = p.product_key AND f.version = '1.0.0' AND f.is_deleted = 0);

UPDATE aiot_cloud.device_info d
SET d.firmware_version = '1.0.0'
WHERE d.is_deleted = 0
  AND d.id LIKE 'seed-dev-%'
  AND (d.firmware_version IS NULL OR d.firmware_version = '');

-- 7. 回填：网关晋升（镜像 V1_1_8 step4：每家庭 id 最小设备 → 网关）
UPDATE aiot_cloud.device_info d
SET d.product_key = 'PERF_GATEWAY_FIX503'
WHERE d.id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY home_id ORDER BY id) AS rn
        FROM aiot_cloud.device_info
        WHERE is_deleted = 0
          AND home_id LIKE 'seed-home-%'
    ) x
    WHERE rn = 1
);

-- 8. 回填：子设备晋升 + 绑定本家庭网关（镜像 V1_1_8 step5：每家庭 id 次小设备 → 子设备）
UPDATE aiot_cloud.device_info d
SET d.product_key = 'PERF_SUBDEVICE_FIX503',
    d.gateway_id = (
        SELECT gw FROM (
            SELECT home_id, MIN(id) AS gw
            FROM aiot_cloud.device_info
            WHERE is_deleted = 0
              AND product_key = 'PERF_GATEWAY_FIX503'
            GROUP BY home_id
        ) g
        WHERE g.home_id = d.home_id
    )
WHERE d.id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY home_id ORDER BY id) AS rn
        FROM aiot_cloud.device_info
        WHERE is_deleted = 0
          AND home_id LIKE 'seed-home-%'
    ) x
    WHERE rn = 2
);
