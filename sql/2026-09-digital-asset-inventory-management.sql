-- Inventory management + marketplace-wide duplicate credential protection.
-- Run before starting a backend version that contains DigitalAsset.contentHash:
-- psql -h localhost -p 5678 -U postgres -d commercehub_db \
--   -f sql/2026-09-digital-asset-inventory-management.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE digital_assets ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE digital_assets ALTER COLUMN content_hash TYPE VARCHAR(64) USING BTRIM(content_hash);

-- ACCOUNT: username trước dấu | là định danh duy nhất, không phân biệt hoa/thường.
-- Vì vậy cùng username nhưng đổi password vẫn không thể được bán lần hai.
UPDATE digital_assets
SET asset_identifier = LEFT(
        LOWER(BTRIM(SPLIT_PART(BTRIM(COALESCE(NULLIF(delivery_content, ''), asset_data)), '|', 1))),
        500
    )
WHERE asset_type = 'ACCOUNT'
  AND BTRIM(COALESCE(NULLIF(delivery_content, ''), asset_data)) <> '';

-- Bảo toàn mọi row lịch sử. Nếu dữ liệu cũ trùng định danh, ưu tiên row SOLD;
-- các bản AVAILABLE dư được chuyển REVOKED để không thể tiếp tục giao cho buyer.
DROP INDEX IF EXISTS uq_digital_assets_content_hash;
UPDATE digital_assets SET content_hash = NULL;

WITH normalized_assets AS (
    SELECT id,
           status,
           ENCODE(DIGEST(
               asset_type || E'\n' || CASE
                   WHEN asset_type = 'ACCOUNT' THEN
                       LOWER(BTRIM(SPLIT_PART(BTRIM(COALESCE(NULLIF(delivery_content, ''), asset_data)), '|', 1)))
                   ELSE BTRIM(COALESCE(NULLIF(delivery_content, ''), asset_data))
               END,
               'sha256'
           ), 'hex') AS fingerprint
    FROM digital_assets
    WHERE BTRIM(COALESCE(NULLIF(delivery_content, ''), asset_data)) <> ''
), ranked_assets AS (
    SELECT id,
           fingerprint,
           ROW_NUMBER() OVER (
               PARTITION BY fingerprint
               ORDER BY CASE status
                            WHEN 'SOLD' THEN 0
                            WHEN 'RESERVED' THEN 1
                            WHEN 'DISPUTED' THEN 2
                            WHEN 'AVAILABLE' THEN 3
                            ELSE 4
                        END,
                        CASE WHEN status = 'AVAILABLE' THEN id END DESC,
                        id
           ) AS fingerprint_order
    FROM normalized_assets
)
UPDATE digital_assets asset
SET content_hash = CASE WHEN ranked.fingerprint_order = 1 THEN ranked.fingerprint ELSE NULL END,
    status = CASE
        WHEN ranked.fingerprint_order > 1 AND asset.status = 'AVAILABLE' THEN 'REVOKED'
        ELSE asset.status
    END,
    updated_at = CASE
        WHEN ranked.fingerprint_order > 1 AND asset.status = 'AVAILABLE' THEN NOW()
        ELSE asset.updated_at
    END
FROM ranked_assets ranked
WHERE asset.id = ranked.id;

-- stock_count là cache; tái đồng bộ theo số row AVAILABLE thực tế sau cleanup.
WITH actual_stock AS (
    SELECT variant.id AS variant_id,
           COUNT(asset.id) FILTER (WHERE asset.status = 'AVAILABLE')::INT AS available_count
    FROM product_variants variant
    JOIN products product ON product.id = variant.product_id
    LEFT JOIN digital_assets asset ON asset.product_variant_id = variant.id
    WHERE product.delivery_type = 'INSTANT'
    GROUP BY variant.id
)
UPDATE product_variants variant
SET stock_count = actual.available_count
FROM actual_stock actual
WHERE variant.id = actual.variant_id
  AND variant.stock_count IS DISTINCT FROM actual.available_count;

CREATE UNIQUE INDEX IF NOT EXISTS uq_digital_assets_content_hash
    ON digital_assets(content_hash);

COMMIT;
