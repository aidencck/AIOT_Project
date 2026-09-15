-- =============================================================================
-- 数据面补全（L2 数据工程）：网关-子设备层级回填（可剥离）
--
-- 目标：建立真实可用的「网关 -> 子设备」拓扑，消除 gateway_covered=0
--       （当前子设备数为 0，产品仅 1 个且 node_type=1 直连）。
-- 根因：无网关产品(node_type=2)/子设备产品(node_type=3)，无任何网关绑定，
--       属「数据缺失」而非「采集链路未携带维度」（device-service 的
--       validateGatewayBinding 已实现网关绑定的完整校验）。
--
-- 策略（有界、确定性、幂等）：
--   1. 创建网关产品与子设备产品；
--   2. 每个家庭取 id 最小的一台设备晋升为「网关」；
--   3. 每个家庭取 id 次小的一台设备作为「子设备」，并绑定到本家庭网关；
--   其余设备保持直连（node_type=1）不变，避免大规模改写身份维度。
--
-- 可剥离/可观察：本脚本独立成单一迁移，仅影响网关/子设备主数据与绑定关系；
--       覆盖率由 v_device_data_plane_coverage 的 gateway_covered 量化。
-- =============================================================================

-- 1. 网关产品（node_type=2）
INSERT INTO product_info (id, product_key, name, description, node_type, create_time, update_time, is_deleted)
SELECT 'gateway-product-fix503', 'PERF_GATEWAY_FIX503', 'Perf Gateway', '数据面补全：网关节点', 2, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM product_info
    WHERE product_key = 'PERF_GATEWAY_FIX503' AND is_deleted = 0
);

-- 2. 子设备产品（node_type=3）
INSERT INTO product_info (id, product_key, name, description, node_type, create_time, update_time, is_deleted)
SELECT 'subdevice-product-fix503', 'PERF_SUBDEVICE_FIX503', 'Perf Subdevice', '数据面补全：网关子设备', 3, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM product_info
    WHERE product_key = 'PERF_SUBDEVICE_FIX503' AND is_deleted = 0
);

-- 3. 为新增的两个产品补齐默认固件包，保持与 V1_1_7 的版本事实一致
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
  AND p.product_key IN ('PERF_GATEWAY_FIX503', 'PERF_SUBDEVICE_FIX503')
  AND NOT EXISTS (
        SELECT 1
        FROM firmware_package f
        WHERE f.product_key = p.product_key
          AND f.version = '1.0.0'
          AND f.is_deleted = 0
  );

-- 4. 每个家庭晋升 1 台设备为网关（该家庭 id 最小的设备）
UPDATE device_info d
SET d.product_key = 'PERF_GATEWAY_FIX503'
WHERE d.product_key <> 'PERF_GATEWAY_FIX503'
  AND d.id IN (
        SELECT id FROM (
            SELECT id,
                   ROW_NUMBER() OVER (PARTITION BY home_id ORDER BY id) AS rn
            FROM device_info
            WHERE is_deleted = 0
              AND home_id IS NOT NULL
              AND home_id <> ''
        ) x
        WHERE rn = 1
  );

-- 5. 每个家庭再取 1 台设备作为子设备（该家庭 id 次小的设备），绑定到本家庭网关
UPDATE device_info d
SET d.product_key = 'PERF_SUBDEVICE_FIX503',
    d.gateway_id = (
        SELECT gw_id FROM (
            SELECT home_id, MIN(id) AS gw_id
            FROM device_info
            WHERE is_deleted = 0
              AND product_key = 'PERF_GATEWAY_FIX503'
            GROUP BY home_id
        ) g
        WHERE g.home_id = d.home_id
    )
WHERE d.product_key <> 'PERF_SUBDEVICE_FIX503'
  AND d.id IN (
        SELECT id FROM (
            SELECT id,
                   ROW_NUMBER() OVER (PARTITION BY home_id ORDER BY id) AS rn
            FROM device_info
            WHERE is_deleted = 0
              AND home_id IS NOT NULL
              AND home_id <> ''
        ) x
        WHERE rn = 2
  );
